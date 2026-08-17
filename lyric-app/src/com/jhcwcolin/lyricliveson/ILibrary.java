package com.jhcwcolin.lyricliveson;

import java.util.List;
import java.util.Map;

/** Abstraction over the music library: either the local DB or the shared provider. */
public interface ILibrary {
    boolean isShared();
    List<Track> allTracks();
    Map<String, String> allLrc();
    boolean addTrack(Track t);
    boolean addLrc(String uri, String matchKey, String content);
    boolean hasTrackUri(String uri);
    void deleteTrack(long id);
    void deleteAll();
}
