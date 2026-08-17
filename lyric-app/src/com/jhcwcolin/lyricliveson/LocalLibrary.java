package com.jhcwcolin.lyricliveson;

import android.content.Context;

import java.util.List;
import java.util.Map;

/** Local fallback library used when the shared host app is not installed. */
public class LocalLibrary implements ILibrary {
    private final MusicDatabase db;

    public LocalLibrary(Context c) {
        db = new MusicDatabase(c.getApplicationContext());
    }

    @Override public boolean isShared() { return false; }

    @Override public List<Track> allTracks() { return db.allTracks(); }

    @Override public Map<String, String> allLrc() { return db.allLrc(); }

    @Override public boolean addTrack(Track t) { return db.addTrack(t); }

    @Override public boolean addLrc(String uri, String matchKey, String content) {
        return db.addLrc(uri, matchKey, content);
    }

    @Override public boolean hasTrackUri(String uri) { return db.hasTrackUri(uri); }

    @Override public void deleteTrack(long id) { db.deleteTrack(id); }

    @Override public void deleteAll() { db.deleteAll(); }
}
