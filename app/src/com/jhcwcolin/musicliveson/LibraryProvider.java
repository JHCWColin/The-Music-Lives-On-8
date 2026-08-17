package com.jhcwcolin.musicliveson;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hosts the shared music library for the paired app (same signature).
 * Exposes tracks + lyrics CRUD, and file access for playback / covers so the
 * client app never needs its own URI grants.
 */
public class LibraryProvider extends ContentProvider {

    public static final String AUTHORITY = "com.jhcwcolin.musicliveson.provider";

    private static final int TRACKS = 1;
    private static final int TRACK_BY_ID = 2;
    private static final int LRC = 3;
    private static final int CLEAR = 4;
    private static final int TRACK_FILE = 5;
    private static final int COVER = 6;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "tracks", TRACKS);
        MATCHER.addURI(AUTHORITY, "tracks/#", TRACK_BY_ID);
        MATCHER.addURI(AUTHORITY, "lrc", LRC);
        MATCHER.addURI(AUTHORITY, "clear", CLEAR);
        MATCHER.addURI(AUTHORITY, "track_file/#", TRACK_FILE);
        MATCHER.addURI(AUTHORITY, "cover/#", COVER);
    }

    private MusicDatabase db;

    private static Uri tracksUri() {
        return Uri.parse("content://" + AUTHORITY + "/tracks");
    }

    @Override
    public boolean onCreate() {
        db = new MusicDatabase(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        switch (MATCHER.match(uri)) {
            case TRACKS:
                return db.getReadableDatabase().query("tracks", projection, selection,
                        selectionArgs, null, null,
                        sortOrder != null ? sortOrder : "title COLLATE NOCASE ASC");
            case LRC:
                return db.getReadableDatabase().query("lrcs", projection, selection,
                        selectionArgs, null, null, null);
            case TRACK_BY_ID:
                return db.getReadableDatabase().query("tracks", projection, "_id=?",
                        new String[]{uri.getLastPathSegment()}, null, null, null);
            default:
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case TRACKS: return "vnd.android.cursor.dir/vnd.jhcwcolin.track";
            case LRC: return "vnd.android.cursor.dir/vnd.jhcwcolin.lrc";
            case TRACK_FILE: return "audio/*";
            case COVER: return "image/*";
            default: return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Context c = getContext();
        SQLiteDatabase w = db.getWritableDatabase();
        switch (MATCHER.match(uri)) {
            case TRACKS: {
                String trackUri = values.getAsString("uri");
                if (trackUri == null) return null;
                long existing = db.findTrackId(trackUri);
                if (existing != -1) {
                    return ContentUris.withAppendedId(tracksUri(), existing);
                }
                String fallback = values.getAsString("title");
                if (fallback == null || fallback.length() == 0) fallback = "未知曲目";
                String grantUri = values.getAsString("grant_uri");
                CoverLoader.Meta m = extractWithGrant(c, Uri.parse(trackUri), grantUri, fallback);
                ContentValues v = new ContentValues();
                v.put("uri", trackUri);
                v.put("title", m.title != null ? m.title : fallback);
                v.put("artist", m.artist != null ? m.artist : "");
                v.put("album", m.album != null ? m.album : "");
                v.put("duration", m.durationMs);
                v.put("cover", m.coverPath);
                v.put("match_key", values.getAsString("match_key"));
                v.put("grant_uri", grantUri);
                long id = w.insertWithOnConflict("tracks", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (id == -1) id = db.findTrackId(trackUri);
                notifyChanged();
                return ContentUris.withAppendedId(tracksUri(), id);
            }
            case LRC: {
                String lrcUri = values.getAsString("uri");
                if (lrcUri == null) return null;
                ContentValues v = new ContentValues();
                v.put("uri", lrcUri);
                v.put("match_key", values.getAsString("match_key"));
                v.put("content", values.getAsString("content"));
                long id = w.insertWithOnConflict("lrcs", null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                return ContentUris.withAppendedId(
                        Uri.parse("content://" + AUTHORITY + "/lrc"), id);
            }
            default:
                return null;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase w = db.getWritableDatabase();
        int n;
        switch (MATCHER.match(uri)) {
            case CLEAR:
                n = w.delete("tracks", null, null);
                w.delete("lrcs", null, null);
                break;
            case TRACK_BY_ID:
                n = w.delete("tracks", "_id=?", new String[]{uri.getLastPathSegment()});
                break;
            case TRACKS:
                n = w.delete("tracks", selection, selectionArgs);
                break;
            default:
                return 0;
        }
        notifyChanged();
        return n;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        switch (MATCHER.match(uri)) {
            case TRACK_FILE: {
                long id = Long.parseLong(uri.getLastPathSegment());
                Track t = db.findTrack(id);
                if (t == null) throw new FileNotFoundException();
                try {
                    return c.getContentResolver().openFileDescriptor(Uri.parse(t.uri), "r");
                } catch (SecurityException e) {
                    tryGrant(c, t);
                    return c.getContentResolver().openFileDescriptor(Uri.parse(t.uri), "r");
                }
            }
            case COVER: {
                long id = Long.parseLong(uri.getLastPathSegment());
                Track t = db.findTrack(id);
                if (t == null) throw new FileNotFoundException();
                if (t.coverPath == null) {
                    tryGrant(c, t);
                    try {
                        CoverLoader.Meta m = CoverLoader.readMeta(c, Uri.parse(t.uri), "x");
                        if (m.coverPath != null) {
                            db.updateCover(id, m.coverPath);
                            t.coverPath = m.coverPath;
                        }
                    } catch (Exception e) {
                        // keep null
                    }
                }
                if (t.coverPath == null) throw new FileNotFoundException();
                return ParcelFileDescriptor.open(new File(t.coverPath),
                        ParcelFileDescriptor.MODE_READ_ONLY);
            }
            default:
                throw new FileNotFoundException();
        }
    }

    private CoverLoader.Meta extractWithGrant(Context c, Uri trackUri, String grantUri, String fallback) {
        if (!canOpen(c, trackUri) && grantUri != null) {
            try {
                c.getContentResolver().takePersistableUriPermission(
                        Uri.parse(grantUri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                // grant not persistable yet; extraction will degrade to fallback
            }
        }
        return CoverLoader.readMeta(c, trackUri, fallback);
    }

    private boolean canOpen(Context c, Uri u) {
        try {
            ParcelFileDescriptor pfd = c.getContentResolver().openFileDescriptor(u, "r");
            if (pfd != null) pfd.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void tryGrant(Context c, Track t) {
        String g = (t.grantUri != null) ? t.grantUri : t.uri;
        try {
            c.getContentResolver().takePersistableUriPermission(
                    Uri.parse(g), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            // ignore
        }
    }

    private void notifyChanged() {
        Context c = getContext();
        if (c != null) c.getContentResolver().notifyChange(tracksUri(), null);
    }
}
