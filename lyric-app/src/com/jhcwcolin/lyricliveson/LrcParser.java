package com.jhcwcolin.lyricliveson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses .lrc content into time-sorted lyric lines. */
public final class LrcParser {

    public static class Line {
        public long timeMs;
        public String text;
    }

    public static class Result {
        public final List<Line> lines = new ArrayList<Line>();
        public String title;
        public String artist;
    }

    private static final Pattern TIME = Pattern.compile(
            "\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    public static Result parse(String content) {
        Result r = new Result();
        if (content == null) return r;
        String[] raw = content.split("\n");
        for (String s : raw) {
            s = s.replace("\r", "");
            Matcher m = TIME.matcher(s);
            List<Long> times = new ArrayList<Long>();
            int last = 0;
            boolean any = false;
            while (m.find()) {
                any = true;
                long t = Long.parseLong(m.group(1)) * 60000L
                       + Long.parseLong(m.group(2)) * 1000L;
                String frac = m.group(3);
                if (frac != null) {
                    if (frac.length() == 1) t += Long.parseLong(frac) * 100L;
                    else if (frac.length() == 2) t += Long.parseLong(frac) * 10L;
                    else t += Long.parseLong(frac);
                }
                times.add(t);
                last = m.end();
            }
            String text = s.substring(last).trim();
            if (any) {
                for (Long t : times) {
                    Line l = new Line();
                    l.timeMs = t;
                    l.text = text;
                    r.lines.add(l);
                }
            } else if (s.startsWith("[ti:")) {
                r.title = stripTag(s);
            } else if (s.startsWith("[ar:")) {
                r.artist = stripTag(s);
            }
        }
        Collections.sort(r.lines, new Comparator<Line>() {
            @Override public int compare(Line a, Line b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return r;
    }

    private static String stripTag(String s) {
        int i = s.indexOf(']');
        return i >= 0 ? s.substring(i + 1).trim() : s;
    }
}
