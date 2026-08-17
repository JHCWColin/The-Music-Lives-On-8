package com.jhcwcolin.lyricliveson;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/** Foreground playback service using a single MediaPlayer with speed control. */
public class PlaybackService extends Service {

    public static final String ACTION_PLAY_PAUSE = "com.jhcwcolin.lyricliveson.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.jhcwcolin.lyricliveson.NEXT";
    public static final String ACTION_PREV = "com.jhcwcolin.lyricliveson.PREV";
    public static final String ACTION_STOP = "com.jhcwcolin.lyricliveson.STOP";

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIF_ID = 1;

    private MediaPlayer player;
    private final List<Track> queue = new ArrayList<Track>();
    private int index = -1;
    private float speed = 1.0f;
    private boolean prepared = false;
    private int consecutiveErrors = 0;
    private PowerManager.WakeLock wakelock;
    private AppPrefs prefs;

    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
        speed = prefs.speed();
        if (speed < 0.5f || speed > 2.0f) speed = 1.0f;
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PLAY_PAUSE.equals(action)) playPause();
        else if (ACTION_NEXT.equals(action)) next();
        else if (ACTION_PREV.equals(action)) prev();
        else if (ACTION_STOP.equals(action)) stopPlayback();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
        releaseWakelock();
    }

    // --- public API for the UI ---

    public List<Track> getQueue() {
        return queue;
    }

    public int getIndex() {
        return index;
    }

    public Track getCurrentTrack() {
        if (index >= 0 && index < queue.size()) return queue.get(index);
        return null;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public int getPosition() {
        if (player != null && prepared) {
            try { return player.getCurrentPosition(); } catch (Exception e) {}
        }
        return 0;
    }

    public int getDuration() {
        if (player != null && prepared) {
            try { return player.getDuration(); } catch (Exception e) {}
        }
        return 0;
    }

    public float getSpeed() {
        return speed;
    }

    public void playQueue(List<Track> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) return;
        queue.clear();
        queue.addAll(tracks);
        if (startIndex < 0 || startIndex >= queue.size()) startIndex = 0;
        consecutiveErrors = 0;
        play(startIndex);
    }

    public void playPause() {
        if (player == null || !prepared) return;
        if (player.isPlaying()) {
            player.pause();
            releaseWakelock();
        } else {
            player.start();
            acquireWakelock();
        }
        updateNotification();
    }

    public void next() {
        consecutiveErrors = 0;
        advance(1);
    }

    public void prev() {
        consecutiveErrors = 0;
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        advance(-1);
    }

    public void seekTo(int ms) {
        if (player != null && prepared) {
            try { player.seekTo(ms); } catch (Exception e) {}
        }
    }

    public void setSpeed(float f) {
        if (f < 0.5f) f = 0.5f;
        if (f > 2.0f) f = 2.0f;
        speed = f;
        prefs.setSpeed(f);
        if (player != null && prepared) {
            applySpeed();
        }
    }

    public void stopPlayback() {
        releasePlayer();
        index = -1;
        stopForeground(true);
        stopSelf();
    }

    // --- internals ---

    private void advance(int delta) {
        if (queue.isEmpty()) return;
        int n = index + delta;
        if (n < 0) n = queue.size() - 1;
        if (n >= queue.size()) n = 0;
        play(n);
    }

    private void play(int i) {
        index = i;
        Track t = queue.get(i);
        releasePlayer();
        prepared = false;
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(this, Uri.parse(t.uri));
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    consecutiveErrors = 0;
                    applySpeed();
                    mp.start();
                    acquireWakelock();
                    updateNotification();
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    advance(1);
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    prepared = false;
                    consecutiveErrors++;
                    if (consecutiveErrors >= queue.size()) {
                        stopPlayback();
                    } else {
                        advance(1);
                    }
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
        }
        startForeground(NOTIF_ID, buildNotification(t, false));
        updateNotification();
    }

    private void applySpeed() {
        try {
            PlaybackParams p = new PlaybackParams();
            p.setSpeed(speed);
            p.setPitch(1.0f);
            player.setPlaybackParams(p);
        } catch (Exception e) {
            // some codecs/devices do not support changing speed
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); } catch (Exception e) {}
            try { player.release(); } catch (Exception e) {}
            player = null;
        }
        prepared = false;
        releaseWakelock();
    }

    private void acquireWakelock() {
        if (wakelock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lyricliveson:playback");
            }
        }
        if (wakelock != null && !wakelock.isHeld()) wakelock.acquire();
    }

    private void releaseWakelock() {
        if (wakelock != null && wakelock.isHeld()) wakelock.release();
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
    }

    private void updateNotification() {
        Track t = getCurrentTrack();
        if (t == null) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(t, isPlaying()));
    }

    private Notification buildNotification(Track t, boolean playing) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = t != null ? t.title : getString(R.string.app_name);
        String text = t != null ? t.artistLabel() : getString(R.string.no_track);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setShowWhen(false);

        if (t != null) {
            int playIcon = playing ? R.drawable.ic_pause : R.drawable.ic_play;
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_prev),
                    getString(R.string.prev_track), servicePending(ACTION_PREV)).build());
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, playIcon),
                    playing ? getString(R.string.pause) : getString(R.string.play),
                    servicePending(ACTION_PLAY_PAUSE)).build());
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_next),
                    getString(R.string.next_track), servicePending(ACTION_NEXT)).build());
        }
        return b.build();
    }

    private PendingIntent servicePending(String action) {
        Intent i = new Intent(this, PlaybackService.class);
        i.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
