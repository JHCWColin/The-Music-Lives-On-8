package com.jhcwcolin.lyricliveson;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads/writes the shared library hosted by "The Music Lives On 8" through its
 * ContentProvider. Playback and covers go through the provider's openFile so
 * this app does not need its own URI grants for shared tracks.
 */
public class SharedLibrary implements ILibrary {

    public static final String AUTHORITY = "com.jhcwcolin.musicliveson.provider";
    private static final Uri TRACKS = Uri.parse("content://" + AUTHORITY + "/tracks");
    private static final Uri LRC = Uri.parse("content://" + AUTHORITY + "/lrc");
    private static final Uri CLEAR = Uri.parse("content://" + AUTHORITY + "/clear");

    private final Context ctx;

    public SharedLibrary(Context c) {
        ctx = c.getApplicationContext();
    }

    public static boolean isAvailable(Context c) {
        Context app = c.getApplicationContext();
        try {
            if (app.getPackageManager().resolveContentProvider(AUTHORITY, 0) == null) {
                return false;
            }
            Cursor cur = app.getContentResolver().query(
                    TRACKS, new String[]{"_id"}, null, null, null);
            if (cur != null) cur.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public boolean isShared() { return true; }

    @Override public List<Track> allTracks() {
        List<Track> list = new ArrayList<Track>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    TRACKS, null, null, null, "title COLLATE NOCASE ASC");
        } catch (Exception e) {
            return list;
        }
        if (c == null) return list;
        try {
            while (c.moveToNext()) {
                Track t = new Track();
                t.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                t.uri = c.getString(c.getColumnIndexOrThrow("uri"));
                t.title = c.getString(c.getColumnIndexOrThrow("title"));
                t.artist = c.getString(c.getColumnIndexOrThrow("artist"));
                t.album = c.getString(c.getColumnIndexOrThrow("album"));
                t.durationMs = c.getLong(c.getColumnIndexOrThrow("duration"));
                t.matchKey = c.getString(c.getColumnIndexOrThrow("match_key"));
                list.add(t);
            }
        } finally {
            c.close();
        }
        return list;
    }

    @Override public Map<String, String> allLrc() {
        Map<String, String> map = new HashMap<String, String>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(LRC, null, null, null, null);
        } catch (Exception e) {
            return map;
        }
        if (c == null) return map;
        try {
            while (c.moveToNext()) {
                String k = c.getString(c.getColumnIndexOrThrow("match_key"));
                String v = c.getString(c.getColumnIndexOrThrow("content"));
                if (k != null && v != null) map.put(k, v);
            }
        } finally {
            c.close();
        }
        return map;
    }

    @Override public boolean addTrack(Track t) {
        ContentValues v = new ContentValues();
        v.put("uri", t.uri);
        v.put("title", t.title == null ? "" : t.title);
        v.put("artist", t.artist == null ? "" : t.artist);
        v.put("album", t.album == null ? "" : t.album);
        v.put("duration", t.durationMs);
        v.put("match_key", t.matchKey);
        if (t.grantUri != null) v.put("grant_uri", t.grantUri);
        try {
            Uri created = ctx.getContentResolver().insert(TRACKS, v);
            if (created != null) {
                t.id = Long.parseLong(created.getLastPathSegment());
                return true;
            }
        } catch (Exception e) {
            // fall through
        }
        return false;
    }

    @Override public boolean addLrc(String uri, String matchKey, String content) {
        ContentValues v = new ContentValues();
        v.put("uri", uri);
        v.put("match_key", matchKey);
        v.put("content", content);
        try {
            return ctx.getContentResolver().insert(LRC, v) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public boolean hasTrackUri(String uri) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    TRACKS, new String[]{"_id"}, "uri=?", new String[]{uri}, null);
        } catch (Exception e) {
            return false;
        }
        if (c == null) return false;
        boolean has = c.moveToFirst();
        c.close();
        return has;
    }

    @Override public void deleteTrack(long id) {
        try {
            ctx.getContentResolver().delete(ContentUris.withAppendedId(TRACKS, id), null, null);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override public void deleteAll() {
        try {
            ctx.getContentResolver().delete(CLEAR, null, null);
        } catch (Exception e) {
            // ignore
        }
    }
}
