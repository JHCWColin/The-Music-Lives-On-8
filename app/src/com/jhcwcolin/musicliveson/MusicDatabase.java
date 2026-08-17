package com.jhcwcolin.musicliveson;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SQLite-backed permanent library (tracks + lyrics), one per app. */
public class MusicDatabase extends SQLiteOpenHelper {

    public MusicDatabase(Context c) {
        super(c, "library.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracks("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uri TEXT UNIQUE,"
                + "title TEXT,"
                + "artist TEXT,"
                + "album TEXT,"
                + "duration INTEGER,"
                + "cover TEXT,"
                + "match_key TEXT)");
        db.execSQL("CREATE TABLE lrcs("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uri TEXT UNIQUE,"
                + "match_key TEXT,"
                + "content TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // no schema changes needed between these builds
    }

    public boolean addTrack(Track t) {
        ContentValues v = new ContentValues();
        v.put("uri", t.uri);
        v.put("title", t.title);
        v.put("artist", t.artist);
        v.put("album", t.album);
        v.put("duration", t.durationMs);
        v.put("cover", t.coverPath);
        v.put("match_key", t.matchKey);
        long r = getWritableDatabase().insertWithOnConflict(
                "tracks", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return r != -1;
    }

    public boolean addLrc(String uri, String matchKey, String content) {
        ContentValues v = new ContentValues();
        v.put("uri", uri);
        v.put("match_key", matchKey);
        v.put("content", content);
        long r = getWritableDatabase().insertWithOnConflict(
                "lrcs", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return r != -1;
    }

    public boolean hasTrackUri(String uri) {
        Cursor c = getReadableDatabase().query(
                "tracks", new String[]{"_id"}, "uri=?",
                new String[]{uri}, null, null, null);
        boolean has = c.moveToFirst();
        c.close();
        return has;
    }

    public List<Track> allTracks() {
        List<Track> list = new ArrayList<Track>();
        Cursor c = getReadableDatabase().query(
                "tracks", null, null, null, null, null, "title COLLATE NOCASE ASC");
        try {
            while (c.moveToNext()) {
                Track t = new Track();
                t.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                t.uri = c.getString(c.getColumnIndexOrThrow("uri"));
                t.title = c.getString(c.getColumnIndexOrThrow("title"));
                t.artist = c.getString(c.getColumnIndexOrThrow("artist"));
                t.album = c.getString(c.getColumnIndexOrThrow("album"));
                t.durationMs = c.getLong(c.getColumnIndexOrThrow("duration"));
                t.coverPath = c.getString(c.getColumnIndexOrThrow("cover"));
                t.matchKey = c.getString(c.getColumnIndexOrThrow("match_key"));
                list.add(t);
            }
        } finally {
            c.close();
        }
        return list;
    }

    public Map<String, String> allLrc() {
        Map<String, String> map = new HashMap<String, String>();
        Cursor c = getReadableDatabase().query("lrcs", null, null, null, null, null, null);
        try {
            while (c.moveToNext()) {
                String key = c.getString(c.getColumnIndexOrThrow("match_key"));
                String content = c.getString(c.getColumnIndexOrThrow("content"));
                if (key != null && content != null) map.put(key, content);
            }
        } finally {
            c.close();
        }
        return map;
    }

    public void deleteTrack(long id) {
        getWritableDatabase().delete("tracks", "_id=?",
                new String[]{String.valueOf(id)});
    }

    public void deleteAll() {
        getWritableDatabase().delete("tracks", null, null);
        getWritableDatabase().delete("lrcs", null, null);
    }
}
