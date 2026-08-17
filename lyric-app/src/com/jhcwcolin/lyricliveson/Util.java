package com.jhcwcolin.lyricliveson;

import java.util.Locale;

/** Small string / time helpers shared across the app. */
public final class Util {
    private Util() {}

    private static final String[] AUDIO_EXT = {
        "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "wma",
        "mid", "midi", "amr", "3gp", "mp4", "m4b", "aiff", "aif", "ape",
        "mpc", "ac3", "mka", "webm", "xmf", "mxmf", "rtttl", "rtx", "ota", "imy"
    };
    private static final String[] LYRICS_EXT = { "lrc" };

    public static boolean isAudio(String name) {
        String e = ext(name);
        for (String a : AUDIO_EXT) if (a.equals(e)) return true;
        return false;
    }

    public static boolean isLyrics(String name) {
        String e = ext(name);
        for (String a : LYRICS_EXT) if (a.equals(e)) return true;
        return false;
    }

    public static boolean isImage(String name) {
        String e = ext(name);
        return e.equals("jpg") || e.equals("jpeg") || e.equals("png")
            || e.equals("gif") || e.equals("bmp") || e.equals("webp");
    }

    public static String ext(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.US);
    }

    public static String baseName(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        int s = name.lastIndexOf('/');
        int b = name.lastIndexOf('\\');
        if (b > s) s = b;
        if (i < 0 || i <= s) return name.substring(s + 1);
        return name.substring(s + 1, i);
    }

    public static String matchKey(String name) {
        return baseName(name).toLowerCase(Locale.US).trim();
    }

    public static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
