package com.jhcwcolin.lyricliveson;

/** A single music track in the local library. */
public class Track {
    public long id;
    public String uri;       // persisted content:// uri
    public String title;
    public String artist;
    public String album;
    public long durationMs;
    public String coverPath; // cached cover image path, may be null
    public String matchKey;  // base file name (no ext), lowercased

    public String artistLabel() {
        if (artist == null || artist.length() == 0
                || "unknown".equalsIgnoreCase(artist)
                || "<unknown>".equalsIgnoreCase(artist)) {
            return "未知艺术家";
        }
        return artist;
    }

    public String durationLabel() {
        return Util.formatTime(durationMs);
    }
}
