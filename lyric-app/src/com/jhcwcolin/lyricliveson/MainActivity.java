package com.jhcwcolin.lyricliveson;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_IMPORT_FILE = 1;
    private static final int REQ_IMPORT_FOLDER = 2;
    private static final int LYRIC_SLOTS = 10;

    private ILibrary lib;
    private SharedLibrary sharedLib;
    private AppPrefs prefs;
    private PlaybackService svc;
    private boolean bound = false;

    private final Handler ui = new Handler();
    private Runnable tick;

    // header / nav
    private View root;
    private TextView navLibrary, navPlayer, navSettings;
    private TextView currentNav;
    private FrameLayout container;
    private View pageLibrary, pagePlayer, pageSettings;

    // library
    private ListView trackList;
    private TrackAdapter adapter;
    private TextView emptyView;
    private List<Track> tracks = new ArrayList<Track>();

    // player (Apple Music-style large lyric area, speed lives in Settings only)
    private ImageView cover;
    private TextView trackTitle, trackArtist;
    private TextView lyricPrev, lyricCurrent, lyricNext;
    private TextView timeCurrent, timeTotal;
    private SeekBar progressBar;
    private Button btnPrev, btnPlayPause, btnNext, btnStop;
    private LinearLayout lyricBlock, lyricList;
    private final List<TextView> lyricSlots = new ArrayList<TextView>();
    private int lastListIndex = -1;

    // settings
    private RadioGroup lyricLinesGroup;
    private RadioButton rbSingle, rbDouble, rbTriple, rbList;
    private Switch einkSwitch;
    private SeekBar defaultSpeedBar;
    private TextView defaultSpeedValue;
    private Button btnClear;

    // lyric state
    private Map<String, String> lrcMap;
    private LrcParser.Result currentLrc;
    private String currentLrcKey;
    private String lastCoverPath;

    private boolean userSeeking = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder b = (PlaybackService.LocalBinder) service;
            svc = b.getService();
            bound = true;
            startTicker();
            refreshPlayerPage();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            svc = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SharedLibrary.isAvailable(this)) {
            SharedLibrary sl = new SharedLibrary(this);
            sharedLib = sl;
            lib = sl;
        } else {
            lib = new LocalLibrary(this);
        }
        prefs = new AppPrefs(this);
        setContentView(R.layout.activity_main);

        bindViews();
        setupPages();
        loadLibrary();
        lrcMap = lib.allLrc();
        requestNotifPermissionIfNeeded();
        applyTheme();
        selectPage(navLibrary, pageLibrary);

        Intent si = new Intent(this, PlaybackService.class);
        startService(si);
        bindService(si, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // the host app may have added/removed tracks in the shared library
        if (pageLibrary != null && pageLibrary.getVisibility() == View.VISIBLE) {
            lrcMap = lib.allLrc();
            loadLibrary();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTicker();
        if (bound) {
            unbindService(connection);
            bound = false;
            svc = null;
        }
    }

    // --- view wiring ---

    private void bindViews() {
        root = findViewById(R.id.root);
        navLibrary = (TextView) findViewById(R.id.nav_library);
        navPlayer = (TextView) findViewById(R.id.nav_player);
        navSettings = (TextView) findViewById(R.id.nav_settings);
        container = (FrameLayout) findViewById(R.id.container);

        LayoutInflater inf = LayoutInflater.from(this);
        pageLibrary = inf.inflate(R.layout.page_library, container, false);
        pagePlayer = inf.inflate(R.layout.page_player, container, false);
        pageSettings = inf.inflate(R.layout.page_settings, container, false);
        container.addView(pageLibrary);
        container.addView(pagePlayer);
        container.addView(pageSettings);

        trackList = (ListView) pageLibrary.findViewById(R.id.track_list);
        emptyView = (TextView) pageLibrary.findViewById(R.id.empty_view);
        Button btnImportFile = (Button) pageLibrary.findViewById(R.id.btn_import_file);
        Button btnImportFolder = (Button) pageLibrary.findViewById(R.id.btn_import_folder);

        cover = (ImageView) pagePlayer.findViewById(R.id.cover);
        trackTitle = (TextView) pagePlayer.findViewById(R.id.track_title);
        trackArtist = (TextView) pagePlayer.findViewById(R.id.track_artist);
        lyricPrev = (TextView) pagePlayer.findViewById(R.id.lyric_prev);
        lyricCurrent = (TextView) pagePlayer.findViewById(R.id.lyric_current);
        lyricNext = (TextView) pagePlayer.findViewById(R.id.lyric_next);
        lyricBlock = (LinearLayout) pagePlayer.findViewById(R.id.lyric_block);
        lyricList = (LinearLayout) pagePlayer.findViewById(R.id.lyric_list);
        buildLyricSlots();
        timeCurrent = (TextView) pagePlayer.findViewById(R.id.time_current);
        timeTotal = (TextView) pagePlayer.findViewById(R.id.time_total);
        progressBar = (SeekBar) pagePlayer.findViewById(R.id.progress);
        btnPrev = (Button) pagePlayer.findViewById(R.id.btn_prev);
        btnPlayPause = (Button) pagePlayer.findViewById(R.id.btn_play_pause);
        btnNext = (Button) pagePlayer.findViewById(R.id.btn_next);
        btnStop = (Button) pagePlayer.findViewById(R.id.btn_stop);

        lyricLinesGroup = (RadioGroup) pageSettings.findViewById(R.id.lyric_lines_group);
        rbSingle = (RadioButton) pageSettings.findViewById(R.id.rb_single);
        rbDouble = (RadioButton) pageSettings.findViewById(R.id.rb_double);
        rbTriple = (RadioButton) pageSettings.findViewById(R.id.rb_triple);
        rbList = (RadioButton) pageSettings.findViewById(R.id.rb_list);
        einkSwitch = (Switch) pageSettings.findViewById(R.id.eink_switch);
        defaultSpeedBar = (SeekBar) pageSettings.findViewById(R.id.default_speed_bar);
        defaultSpeedValue = (TextView) pageSettings.findViewById(R.id.default_speed_value);
        btnClear = (Button) pageSettings.findViewById(R.id.btn_clear);

        // wire navigation
        navLibrary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectPage(navLibrary, pageLibrary); }
        });
        navPlayer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectPage(navPlayer, pagePlayer); }
        });
        navSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectPage(navSettings, pageSettings); }
        });

        // wire import buttons
        btnImportFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, REQ_IMPORT_FILE);
            }
        });
        btnImportFolder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                startActivityForResult(i, REQ_IMPORT_FOLDER);
            }
        });
    }

    private void setupPages() {
        // library list
        trackList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (svc != null) {
                    svc.playQueue(new ArrayList<Track>(tracks), pos);
                    selectPage(navPlayer, pagePlayer);
                    refreshPlayerPage();
                }
            }
        });
        trackList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                confirmRemove(tracks.get(pos));
                return true;
            }
        });

        // player controls
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (svc == null) return;
                if (svc.getCurrentTrack() == null) {
                    if (!tracks.isEmpty()) {
                        svc.playQueue(new ArrayList<Track>(tracks), 0);
                    }
                } else {
                    svc.playPause();
                }
                refreshPlayerPage();
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (svc != null) svc.next(); }
        });
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (svc != null) svc.prev(); }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (svc != null) svc.stopPlayback();
                refreshPlayerPage();
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) timeCurrent.setText(Util.formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                if (svc != null) svc.seekTo(sb.getProgress());
            }
        });

        // settings
        int mode = prefs.lyricMode();
        if (mode == 1) rbSingle.setChecked(true);
        else if (mode == 2) rbDouble.setChecked(true);
        else if (mode == 3) rbTriple.setChecked(true);
        else rbList.setChecked(true);
        lyricLinesGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                int n;
                if (checkedId == R.id.rb_single) n = 1;
                else if (checkedId == R.id.rb_double) n = 2;
                else if (checkedId == R.id.rb_triple) n = 3;
                else n = 4;
                prefs.setLyricMode(n);
            }
        });

        einkSwitch.setChecked(prefs.eink());
        einkSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setEink(checked);
                applyTheme();
            }
        });

        // speed is configured only here; the player page shows no speed control
        defaultSpeedBar.setProgress(speedToIndex(prefs.speed()));
        defaultSpeedValue.setText(formatSpeed(prefs.speed()));
        defaultSpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float sp = indexToSpeed(progress);
                defaultSpeedValue.setText(formatSpeed(sp));
                if (fromUser) {
                    prefs.setSpeed(sp);
                    if (svc != null) svc.setSpeed(sp);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
    }

    // --- navigation / theme ---

    private void selectPage(View nav, View page) {
        pageLibrary.setVisibility(page == pageLibrary ? View.VISIBLE : View.GONE);
        pagePlayer.setVisibility(page == pagePlayer ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(page == pageSettings ? View.VISIBLE : View.GONE);
        currentNav = (TextView) nav;
        updateNavColors();
    }

    private void updateNavColors() {
        int sel = prefs.eink() ? 0xFF000000 : getColorRes(R.color.nav_selected);
        int unsel = prefs.eink() ? 0xFF555555 : getColorRes(R.color.nav_unselected);
        navLibrary.setTextColor(navLibrary == currentNav ? sel : unsel);
        navPlayer.setTextColor(navPlayer == currentNav ? sel : unsel);
        navSettings.setTextColor(navSettings == currentNav ? sel : unsel);
        navLibrary.setTypeface(Typeface.DEFAULT, navLibrary == currentNav ? Typeface.BOLD : Typeface.NORMAL);
        navPlayer.setTypeface(Typeface.DEFAULT, navPlayer == currentNav ? Typeface.BOLD : Typeface.NORMAL);
        navSettings.setTypeface(Typeface.DEFAULT, navSettings == currentNav ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void applyTheme() {
        boolean eink = prefs.eink();
        root.setBackgroundColor(eink ? 0xFFFFFFFF : getColorRes(R.color.bg));
        updateNavColors();
    }

    private int getColorRes(int id) {
        return getResources().getColor(id, getTheme());
    }

    // --- library ---

    private void loadLibrary() {
        tracks = lib.allTracks();
        adapter = new TrackAdapter(this, tracks);
        trackList.setAdapter(adapter);
        boolean empty = tracks.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        trackList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // --- ticker / player page refresh ---

    private void startTicker() {
        if (tick == null) {
            tick = new Runnable() {
                @Override public void run() {
                    if (bound && svc != null) refreshPlayerPage();
                    ui.postDelayed(this, 300);
                }
            };
        }
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    private void stopTicker() {
        if (tick != null) ui.removeCallbacks(tick);
    }

    private void refreshPlayerPage() {
        if (svc == null) return;
        Track t = svc.getCurrentTrack();
        if (t != null) {
            trackTitle.setText(t.title);
            trackArtist.setText(t.artistLabel());
            updateCover(t);
            int pos = svc.getPosition();
            int dur = svc.getDuration();
            updateLyrics(t, pos);
            timeTotal.setText(Util.formatTime(dur));
            timeCurrent.setText(Util.formatTime(pos));
            if (!userSeeking && dur > 0) {
                if (progressBar.getMax() != dur) progressBar.setMax(dur);
                progressBar.setProgress(pos);
            }
            btnPlayPause.setText(svc.isPlaying() ? getString(R.string.pause) : getString(R.string.play));
        } else {
            trackTitle.setText(R.string.no_track);
            trackArtist.setText("");
            timeTotal.setText("00:00");
            timeCurrent.setText("00:00");
            progressBar.setProgress(0);
            btnPlayPause.setText(R.string.play);
            updateCover(null);
            currentLrcKey = null;
            currentLrc = null;
            lastListIndex = -1;
            showCenteredBlock();
            lyricPrev.setVisibility(View.VISIBLE);
            lyricPrev.setText("");
            lyricCurrent.setText(R.string.no_lyrics);
            lyricNext.setVisibility(View.VISIBLE);
            lyricNext.setText("");
        }
    }

    private void updateCover(Track t) {
        if (sharedLib != null && t != null) {
            // shared mode: cover is served by the host app's provider
            String key = "provider:" + t.id;
            if (key.equals(lastCoverPath)) return;
            lastCoverPath = key;
            ParcelFileDescriptor pfd = null;
            try {
                Uri cu = Uri.parse("content://" + SharedLibrary.AUTHORITY + "/cover/" + t.id);
                pfd = getContentResolver().openFileDescriptor(cu, "r");
                Bitmap b = (pfd != null) ? CoverLoader.decodeSampledFd(pfd, 128) : null;
                if (b != null) {
                    cover.setImageBitmap(b);
                    cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    return;
                }
            } catch (Exception e) {
                // fall through to placeholder
            } finally {
                if (pfd != null) { try { pfd.close(); } catch (Exception e) {} }
            }
            cover.setImageResource(R.drawable.ic_note);
            cover.setScaleType(ImageView.ScaleType.CENTER);
            return;
        }
        String path = (t != null) ? t.coverPath : null;
        boolean same = (path == null && lastCoverPath == null)
                || (path != null && path.equals(lastCoverPath));
        if (same) return;
        lastCoverPath = path;
        if (path == null) {
            cover.setImageResource(R.drawable.ic_note);
            cover.setScaleType(ImageView.ScaleType.CENTER);
            return;
        }
        Bitmap b = CoverLoader.decodeSampled(path, 128);
        if (b != null) {
            cover.setImageBitmap(b);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            cover.setImageResource(R.drawable.ic_note);
            cover.setScaleType(ImageView.ScaleType.CENTER);
        }
    }

    private void updateLyrics(Track t, int pos) {
        String key = t.matchKey;
        if (key == null) key = "";
        if (!key.equals(currentLrcKey)) {
            currentLrcKey = key;
            lastListIndex = -1;
            String content = (lrcMap != null) ? lrcMap.get(key) : null;
            currentLrc = LrcParser.parse(content);
        }
        int mode = prefs.lyricMode();
        if (mode == 4) {
            updateLyricList(pos);
            return;
        }
        showCenteredBlock();
        if (currentLrc == null || currentLrc.lines.isEmpty()) {
            lyricPrev.setVisibility(View.VISIBLE);
            lyricPrev.setText("");
            lyricCurrent.setText(R.string.no_lyrics);
            lyricNext.setVisibility(View.VISIBLE);
            lyricNext.setText("");
            return;
        }
        List<LrcParser.Line> ls = currentLrc.lines;
        int idx = findLine(currentLrc, pos);
        String cur = ls.get(idx).text;
        String prev = (idx - 1 >= 0) ? ls.get(idx - 1).text : "";
        String next = (idx + 1 < ls.size()) ? ls.get(idx + 1).text : "";

        if (mode == 1) {
            lyricPrev.setVisibility(View.GONE);
            lyricCurrent.setText(cur);
            lyricNext.setVisibility(View.GONE);
        } else if (mode == 2) {
            lyricPrev.setVisibility(View.GONE);
            lyricCurrent.setText(cur);
            lyricNext.setVisibility(View.VISIBLE);
            lyricNext.setText(next);
        } else {
            lyricPrev.setVisibility(View.VISIBLE);
            lyricPrev.setText(prev);
            lyricCurrent.setText(cur);
            lyricNext.setVisibility(View.VISIBLE);
            lyricNext.setText(next);
        }
    }

    private void showCenteredBlock() {
        lyricList.setVisibility(View.GONE);
        lyricBlock.setVisibility(View.VISIBLE);
    }

    private void showListBlock() {
        lyricBlock.setVisibility(View.GONE);
        lyricList.setVisibility(View.VISIBLE);
    }

    private void updateLyricList(int pos) {
        showListBlock();
        if (currentLrc == null || currentLrc.lines.isEmpty()) {
            for (int k = 0; k < lyricSlots.size(); k++) {
                lyricSlots.get(k).setVisibility(k == 0 ? View.VISIBLE : View.GONE);
            }
            lyricSlots.get(0).setText(R.string.no_lyrics);
            return;
        }
        List<LrcParser.Line> ls = currentLrc.lines;
        int idx = findLine(currentLrc, pos);
        if (idx == lastListIndex) return;
        lastListIndex = idx;
        int slot = 0;
        int i = idx;
        while (slot < lyricSlots.size() && i < ls.size()) {
            String txt = ls.get(i).text.trim();
            i++;
            if (txt.length() == 0) continue;
            TextView tv = lyricSlots.get(slot);
            tv.setText(txt);
            float a = 1.0f - slot * 0.10f;
            if (a < 0.05f) a = 0.05f;
            tv.setAlpha(a);
            tv.setVisibility(View.VISIBLE);
            slot++;
        }
        for (int k = slot; k < lyricSlots.size(); k++) {
            lyricSlots.get(k).setVisibility(View.GONE);
        }
    }

    private void buildLyricSlots() {
        lyricSlots.clear();
        for (int i = 0; i < LYRIC_SLOTS; i++) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            tv.setTextColor(getColorRes(R.color.text_primary));
            tv.setLineSpacing(0f, 1.2f);
            tv.setPadding(dp(2), dp(5), dp(2), dp(5));
            tv.setTextSize(i == 0 ? 21 : 16);
            tv.setTypeface(Typeface.DEFAULT, i == 0 ? Typeface.BOLD : Typeface.NORMAL);
            lyricList.addView(tv);
            lyricSlots.add(tv);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int findLine(LrcParser.Result r, int pos) {
        List<LrcParser.Line> ls = r.lines;
        int lo = 0, hi = ls.size() - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ls.get(mid).timeMs <= pos) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private float indexToSpeed(int i) {
        return 0.5f + i * 0.1f;
    }

    private int speedToIndex(float f) {
        int idx = Math.round((f - 0.5f) / 0.1f);
        if (idx < 0) idx = 0;
        if (idx > 15) idx = 15;
        return idx;
    }

    private String formatSpeed(float f) {
        return String.format(Locale.US, "%.1f×", f);
    }

    // --- import ---

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        final int req = requestCode;
        final Intent d = data;
        Toast.makeText(this, "正在导入…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                if (req == REQ_IMPORT_FILE) {
                    FileImporter.importFiles(MainActivity.this, d, lib);
                } else if (req == REQ_IMPORT_FOLDER) {
                    FileImporter.importFolder(MainActivity.this, d, lib);
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        lrcMap = lib.allLrc();
                        loadLibrary();
                        Toast.makeText(MainActivity.this, "导入完成", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // --- removal ---

    private void confirmRemove(final Track t) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_track_title)
                .setMessage(R.string.remove_track_msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        lib.deleteTrack(t.id);
                        deleteCoverFile(t.coverPath);
                        if (svc != null && svc.getCurrentTrack() != null
                                && svc.getCurrentTrack().id == t.id) {
                            svc.stopPlayback();
                        }
                        loadLibrary();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        lib.deleteAll();
                        deleteRecursive(new File(getFilesDir(), "covers"));
                        if (svc != null) svc.stopPlayback();
                        lrcMap = lib.allLrc();
                        loadLibrary();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteCoverFile(String path) {
        if (path == null) return;
        try {
            File f = new File(path);
            if (f.exists()) f.delete();
        } catch (Exception e) {}
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
            }
        }
    }
}
