package com.jhcwcolin.musicliveson;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight settings store (no text input anywhere). */
public class AppPrefs {
    private final SharedPreferences sp;

    public AppPrefs(Context c) {
        sp = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public int lyricLines() {
        return sp.getInt("lyricLines", 2);
    }

    public void setLyricLines(int n) {
        sp.edit().putInt("lyricLines", n).commit();
    }

    public boolean eink() {
        return sp.getBoolean("eink", false);
    }

    public void setEink(boolean b) {
        sp.edit().putBoolean("eink", b).commit();
    }

    public float speed() {
        return sp.getFloat("speed", 1.0f);
    }

    public void setSpeed(float f) {
        sp.edit().putFloat("speed", f).commit();
    }
}
