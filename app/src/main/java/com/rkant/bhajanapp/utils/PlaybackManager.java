package com.rkant.bhajanapp.utils;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.service.BhajanMediaService;

import java.util.ArrayList;
import java.util.List;

public class PlaybackManager {

    public interface Listener {
        default void onStateChanged(boolean isPlaying, String title, String mediaId) {}
        default void onProgress(long position, long duration) {}
        default void onModesChanged(boolean shuffle, int repeatMode) {}
        default void onTimerChanged(long remainingMs) {}
    }

    private interface ControllerAction {
        void run(MediaController controller);
    }

    private static volatile PlaybackManager instance;

    public static PlaybackManager getInstance() {
        if (instance == null) {
            synchronized (PlaybackManager.class) {
                if (instance == null) {
                    instance = new PlaybackManager();
                }
            }
        }
        return instance;
    }

    private Context appContext;
    private MediaController controller;
    private androidx.media3.session.MediaController.Builder builder;
    private com.google.common.util.concurrent.ListenableFuture<MediaController> controllerFuture;

    private final List<Listener> listeners = new ArrayList<>();
    private final List<ControllerAction> pendingActions = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float playbackSpeed = 1.0f;

    private boolean lastPlaying = false;
    private String lastMediaId = null;
    private String lastTitle = null;

    private long sleepEndTime = -1;
    private Runnable sleepRunnable;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            notifyState();
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            notifyState();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            notifyModes();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            notifyModes();
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (controller != null) {
                notifyProgress();
                handler.postDelayed(this, 1000);
            }
        }
    };

    private PlaybackManager() {
    }

    // ═══════════════════════════════════════════════════════════
    // CONNECTION
    // ═══════════════════════════════════════════════════════════

    public void connect(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
            loadPlaybackSpeed();
        }

        if (controllerFuture != null) return;
        if (appContext == null) return;

        SessionToken token = new SessionToken(
                appContext,
                new ComponentName(appContext, BhajanMediaService.class)
        );

        controllerFuture = new MediaController.Builder(appContext, token).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                controller.setPlaybackSpeed(playbackSpeed);

                flushPendingActions();
                startProgressLoop();
                notifyModes();
                notifyState();
            } catch (Exception e) {
                e.printStackTrace();
                controllerFuture = null;
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    private void flushPendingActions() {
        if (controller == null) return;

        List<ControllerAction> copy = new ArrayList<>(pendingActions);
        pendingActions.clear();

        for (ControllerAction action : copy) {
            try {
                action.run(controller);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void startProgressLoop() {
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
    }

    private void runOnController(ControllerAction action) {
        if (controller != null) {
            try {
                action.run(controller);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            pendingActions.add(action);
            if (appContext != null) {
                connect(appContext);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LISTENERS
    // ═══════════════════════════════════════════════════════════

    public void addListener(Listener listener) {
        if (listener == null) return;
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        notifyState();
        notifyModes();
        notifyTimer();
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyState() {
        if (controller == null) return;

        try {
            boolean playing = controller.isPlaying();
            MediaItem item = controller.getCurrentMediaItem();

            String id = item != null ? item.mediaId : null;
            String title = null;

            if (item != null && item.mediaMetadata != null && item.mediaMetadata.title != null) {
                title = item.mediaMetadata.title.toString();
            }

            boolean changed = playing != lastPlaying
                    || !TextUtils.equals(id, lastMediaId)
                    || !TextUtils.equals(title, lastTitle);

            if (changed) {
                lastPlaying = playing;
                lastMediaId = id;
                lastTitle = title;

                for (Listener listener : new ArrayList<>(listeners)) {
                    listener.onStateChanged(playing, title, id);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyDirect(boolean playing, String title, String mediaId) {
        lastPlaying = playing;
        lastMediaId = mediaId;
        lastTitle = title;

        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged(playing, title, mediaId);
        }
    }

    private void notifyProgress() {
        if (controller == null) return;

        try {
            long position = controller.getCurrentPosition();
            long duration = controller.getDuration();

            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onProgress(position, duration);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyModes() {
        if (controller == null) return;

        try {
            boolean shuffle = controller.getShuffleModeEnabled();
            int repeatMode = controller.getRepeatMode();

            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onModesChanged(shuffle, repeatMode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyTimer() {
        long remaining = getSleepTimerRemaining();
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onTimerChanged(remaining);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PLAYBACK
    // ═══════════════════════════════════════════════════════════

    public void playList(List<AudioTrack> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) return;
        if (startIndex < 0 || startIndex >= tracks.size()) return;

        if (appContext != null) {
            AudioTrack selected = tracks.get(startIndex);
            if (selected != null) {
                Helper.addAudioRecent(appContext, selected.id);
            }
        }

        List<MediaItem> items = new ArrayList<>();
        int adjustedIndex = 0;

        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack track = tracks.get(i);
            if (track == null) continue;

            String url = track.playableUrl();
            if (url == null || url.isEmpty()) continue;

            if (i == startIndex) {
                adjustedIndex = items.size();
            }

            MediaItem item = new MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(url)
                    .setMediaMetadata(
                            new MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .build()
                    )
                    .build();

            items.add(item);
        }

        if (items.isEmpty()) return;

        final int finalIndex = Math.max(0, Math.min(adjustedIndex, items.size() - 1));

        runOnController(c -> {
            c.setMediaItems(items);
            c.seekTo(finalIndex, 0);
            c.prepare();
            c.play();
            c.setPlaybackSpeed(playbackSpeed);
        });
    }

    public void playPause() {
        runOnController(c -> {
            if (c.isPlaying()) {
                c.pause();
            } else {
                c.play();
            }
        });
    }

    public void next() {
        runOnController(c -> {
            c.seekToNext();
        });
    }

    public void prev() {
        runOnController(c -> {
            c.seekToPrevious();
        });
    }

    public void seekTo(long position) {
        runOnController(c -> c.seekTo(position));
    }

    public void stopAndClear() {
        cancelSleepTimer();

        runOnController(c -> {
            try {
                c.stop();
                c.setMediaItems(new ArrayList<MediaItem>());
            } catch (Exception ignored) {
            }
        });

        notifyDirect(false, null, null);
    }

    public String getCurrentMediaId() {
        if (controller == null) return lastMediaId;

        try {
            MediaItem item = controller.getCurrentMediaItem();
            return item != null ? item.mediaId : null;
        } catch (Exception e) {
            return lastMediaId;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SHUFFLE / REPEAT
    // ═══════════════════════════════════════════════════════════

    public void toggleShuffle() {
        runOnController(c -> {
            c.setShuffleModeEnabled(!c.getShuffleModeEnabled());
        });
        handler.post(this::notifyModes);
    }

    public void cycleRepeat() {
        int current = controller != null ? controller.getRepeatMode() : Player.REPEAT_MODE_OFF;

        int next;
        if (current == Player.REPEAT_MODE_OFF) {
            next = Player.REPEAT_MODE_ALL;
        } else if (current == Player.REPEAT_MODE_ALL) {
            next = Player.REPEAT_MODE_ONE;
        } else {
            next = Player.REPEAT_MODE_OFF;
        }

        runOnController(c -> c.setRepeatMode(next));
        handler.post(this::notifyModes);
    }

    // ═══════════════════════════════════════════════════════════
    // PLAYBACK SPEED
    // ═══════════════════════════════════════════════════════════

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float speed) {
        if (speed < 0.25f) speed = 0.25f;
        if (speed > 3.0f) speed = 3.0f;

        playbackSpeed = speed;
        savePlaybackSpeed();

        runOnController(c -> c.setPlaybackSpeed(playbackSpeed)); // ✅ FIXED
    }

    private void loadPlaybackSpeed() {
        if (appContext == null) return;

        playbackSpeed = appContext
                .getSharedPreferences("bhajan_playback", Context.MODE_PRIVATE)
                .getFloat("speed", 1.0f);
    }

    private void savePlaybackSpeed() {
        if (appContext == null) return;

        appContext.getSharedPreferences("bhajan_playback", Context.MODE_PRIVATE)
                .edit()
                .putFloat("speed", playbackSpeed)
                .apply();
    }

    // ═══════════════════════════════════════════════════════════
    // SLEEP TIMER
    // ═══════════════════════════════════════════════════════════

    public void setSleepTimer(long milliseconds) {
        cancelSleepTimer();

        if (milliseconds <= 0) return;

        sleepEndTime = System.currentTimeMillis() + milliseconds;

        sleepRunnable = this::stopAndClear;
        handler.postDelayed(sleepRunnable, milliseconds);

        notifyTimer();
    }

    public void cancelSleepTimer() {
        if (sleepRunnable != null) {
            handler.removeCallbacks(sleepRunnable);
            sleepRunnable = null;
        }

        boolean hadTimer = sleepEndTime != -1;
        sleepEndTime = -1;

        if (hadTimer) {
            notifyTimer();
        }
    }

    public long getSleepTimerRemaining() {
        if (sleepEndTime == -1) return -1;

        long remaining = sleepEndTime - System.currentTimeMillis();
        return Math.max(remaining, -1);
    }
}