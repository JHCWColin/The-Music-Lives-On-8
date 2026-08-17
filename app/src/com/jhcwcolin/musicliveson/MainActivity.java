package com.jhcwcolin.musicliveson;

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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    private MusicDatabase db;
    private AppPrefs prefs;
    private PlaybackService svc;
    private boolean bound = false;

    private final Handler ui = new Handler();
    private Runnable tick;

    // header / nav
    private View root;
    private TextView headerTitle, headerByline;
    private TextView navLibrary, navPlayer, navSettings;
    private TextView currentNav;
    private FrameLayout container;
    private View pageLibrary, pagePlayer, pageSettings;

    // library
    private ListView trackList;
    private TrackAdapter adapter;
    private TextView emptyView;
    private List<Track> tracks = new ArrayList<Track>();

    // player
    private ImageView cover;
    private TextView trackTitle, trackArtist;
    private TextView lyricCurrent, lyricNext;
    private TextView timeCurrent, timeTotal, speedValue;
    private SeekBar progressBar, speedBar;
    private Button btnPrev, btnPlayPause, btnNext, btnResetSpeed, btnStop;

    // settings
    private RadioGroup lyricLinesGroup;
    private RadioButton rbSingle, rbDouble;
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
    private boolean userSpeedDragging = false;

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
        db = new MusicDatabase(this);
        prefs = new AppPrefs(this);
        setContentView(R.layout.activity_main);

        bindViews();
        setupPages();
        loadLibrary();
        lrcMap = db.allLrc();
        requestNotifPermissionIfNeeded();
        applyTheme();
        selectPage(navLibrary, pageLibrary);

        Intent si = new Intent(this, PlaybackService.class);
        startService(si);
        bindService(si, connection, Context.BIND_AUTO_CREATE);
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
        headerTitle = (TextView) findViewById(R.id.header_title);
        headerByline = (TextView) findViewById(R.id.header_byline);
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
        lyricCurrent = (TextView) pagePlayer.findViewById(R.id.lyric_current);
        lyricNext = (TextView) pagePlayer.findViewById(R.id.lyric_next);
        timeCurrent = (TextView) pagePlayer.findViewById(R.id.time_current);
        timeTotal = (TextView) pagePlayer.findViewById(R.id.time_total);
        progressBar = (SeekBar) pagePlayer.findViewById(R.id.progress);
        speedBar = (SeekBar) pagePlayer.findViewById(R.id.speed_bar);
        speedValue = (TextView) pagePlayer.findViewById(R.id.speed_value);
        btnPrev = (Button) pagePlayer.findViewById(R.id.btn_prev);
        btnPlayPause = (Button) pagePlayer.findViewById(R.id.btn_play_pause);
        btnNext = (Button) pagePlayer.findViewById(R.id.btn_next);
        btnResetSpeed = (Button) pagePlayer.findViewById(R.id.btn_reset_speed);
        btnStop = (Button) pagePlayer.findViewById(R.id.btn_stop);

        lyricLinesGroup = (RadioGroup) pageSettings.findViewById(R.id.lyric_lines_group);
        rbSingle = (RadioButton) pageSettings.findViewById(R.id.rb_single);
        rbDouble = (RadioButton) pageSettings.findViewById(R.id.rb_double);
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

        speedBar.setProgress(speedToIndex(prefs.speed()));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float sp = indexToSpeed(progress);
                speedValue.setText(formatSpeed(sp));
                if (fromUser && svc != null) svc.setSpeed(sp);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSpeedDragging = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSpeedDragging = false;
                if (svc != null) svc.setSpeed(indexToSpeed(sb.getProgress()));
            }
        });

        btnResetSpeed.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int idx = speedToIndex(1.0f);
                speedBar.setProgress(idx);
                if (svc != null) svc.setSpeed(1.0f);
            }
        });

        // settings
        if (prefs.lyricLines() <= 1) rbSingle.setChecked(true);
        else rbDouble.setChecked(true);
        lyricLinesGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                prefs.setLyricLines(checkedId == R.id.rb_single ? 1 : 2);
            }
        });

        einkSwitch.setChecked(prefs.eink());
        einkSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setEink(checked);
                applyTheme();
            }
        });

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
        tracks = db.allTracks();
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
            lyricCurrent.setText(R.string.no_lyrics);
            lyricNext.setText("");
            lyricNext.setVisibility(View.VISIBLE);
        }
        updateSpeedDisplay();
    }

    private void updateCover(Track t) {
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
        Bitmap b = CoverLoader.decodeSampled(path, 512);
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
            String content = (lrcMap != null) ? lrcMap.get(key) : null;
            currentLrc = LrcParser.parse(content);
        }
        if (currentLrc == null || currentLrc.lines.isEmpty()) {
            lyricCurrent.setText(R.string.no_lyrics);
            lyricNext.setText("");
            lyricNext.setVisibility(View.VISIBLE);
            return;
        }
        int idx = findLine(currentLrc, pos);
        String cur = currentLrc.lines.get(idx).text;
        String next = (idx + 1 < currentLrc.lines.size())
                ? currentLrc.lines.get(idx + 1).text : "";
        if (prefs.lyricLines() <= 1) {
            lyricCurrent.setText(cur);
            lyricNext.setVisibility(View.GONE);
        } else {
            lyricCurrent.setText(cur);
            lyricNext.setText(next);
            lyricNext.setVisibility(View.VISIBLE);
        }
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

    private void updateSpeedDisplay() {
        if (svc == null) return;
        float sp = svc.getSpeed();
        speedValue.setText(formatSpeed(sp));
        if (!userSpeedDragging) speedBar.setProgress(speedToIndex(sp));
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
                    FileImporter.importFiles(MainActivity.this, d, db);
                } else if (req == REQ_IMPORT_FOLDER) {
                    FileImporter.importFolder(MainActivity.this, d, db);
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        lrcMap = db.allLrc();
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
                        db.deleteTrack(t.id);
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
                        db.deleteAll();
                        deleteRecursive(new File(getFilesDir(), "covers"));
                        if (svc != null) svc.stopPlayback();
                        lrcMap = db.allLrc();
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
