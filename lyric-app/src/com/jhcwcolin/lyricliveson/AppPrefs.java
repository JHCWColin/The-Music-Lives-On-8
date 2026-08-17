package com.jhcwcolin.lyricliveson;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight settings store (no text input anywhere). */
public class AppPrefs {
    private final SharedPreferences sp;

    public AppPrefs(Context c) {
        sp = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    /** 1/2/3 = centered big lyrics with that many lines, 4 = full list mode. */
    public int lyricMode() {
        return sp.getInt("lyricMode", 3);
    }

    public void setLyricMode(int n) {
        if (n < 1) n = 1;
        if (n > 4) n = 4;
        sp.edit().putInt("lyricMode", n).commit();
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
