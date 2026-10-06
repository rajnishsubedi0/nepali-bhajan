package com.rkant.bhajanapp.utils;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.service.BhajanMediaService;

import java.util.ArrayList;
import java.util.List;

public class PlaybackManager {

    /** Simple callback interface so Activities don't depend on Media3 API details. */
    public interface Listener {
        void onStateChanged(boolean isPlaying, String title, String mediaId);
        void onProgress(long position, long duration);
        default void onModesChanged(boolean shuffle, int repeatMode) {}
    }

    private static PlaybackManager instance;
    private MediaController controller;
    private ListenableFuture<MediaController> future;
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) { notifyState(); }
        @Override public void onPlaybackStateChanged(int state) { notifyState(); }
        @Override public void onMediaItemTransition(MediaItem item, int reason) { notifyState(); }
        @Override public void onMediaMetadataChanged(MediaMetadata meta) { notifyState(); }
        @Override public void onShuffleModeEnabledChanged(boolean b) { notifyModes(); }
        @Override public void onRepeatModeChanged(int mode) { notifyModes(); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            notifyProgress();
            if (controller != null && controller.isPlaying()) handler.postDelayed(this, 500);
        }
    };

    public static synchronized PlaybackManager getInstance() {
        if (instance == null) instance = new PlaybackManager();
        return instance;
    }

    /** Idempotent. Safe to call from any Activity. */
    public void connect(Context context) {
        if (controller != null) return;
        Context appCtx = context.getApplicationContext();
        SessionToken token = new SessionToken(appCtx, new ComponentName(appCtx, BhajanMediaService.class));
        future = new MediaController.Builder(appCtx, token).buildAsync();
        future.addListener(() -> {
            try {
                controller = future.get();
                controller.addListener(playerListener);
                notifyState();
                notifyProgress();
                notifyModes();
                startTickerIfPlaying();
            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(appCtx));
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        notifyState(); notifyProgress(); notifyModes();
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    // ----- Controls -----
    public void playPause() {
        if (controller == null) return;
        if (controller.isPlaying()) controller.pause(); else controller.play();
    }

    public void next() { if (controller != null) controller.seekToNext(); }
    public void prev() { if (controller != null) controller.seekToPrevious(); }
    public void seekTo(long pos) { if (controller != null) controller.seekTo(pos); }

    public void toggleShuffle() {
        if (controller != null) controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
    }

    public void cycleRepeat() {
        if (controller == null) return;
        int next = (controller.getRepeatMode() + 1) % 3; // OFF -> ONE -> ALL -> OFF
        controller.setRepeatMode(next);
    }

    /** Loads the whole list as a playlist and starts at the given index. */
    public void playList(List<AudioTrack> tracks, int startIndex) {
        if (controller == null || tracks == null || tracks.isEmpty()) return;
        List<MediaItem> items = new ArrayList<>();
        for (AudioTrack t : tracks) {
            items.add(new MediaItem.Builder()
                    .setUri(t.playableUrl())
                    .setMediaId(t.id)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(t.title).build())
                    .build());
        }
        int idx = Math.max(0, Math.min(startIndex, items.size() - 1));
        controller.setMediaItems(items, idx, 0);
        controller.prepare();
        controller.play();
    }

    /** Stops playback and clears the queue (used by the Stop button). */
    public void stopAndClear() {
        if (controller != null) {
            controller.stop();
            controller.clearMediaItems();
        }
        notifyState();
    }

    // ----- Getters -----
    public MediaController getController() { return controller; }
    public boolean isPlaying() { return controller != null && controller.isPlaying(); }
    public boolean isShuffle() { return controller != null && controller.getShuffleModeEnabled(); }
    public int getRepeatMode() { return controller == null ? Player.REPEAT_MODE_OFF : controller.getRepeatMode(); }

    public String getCurrentMediaId() {
        if (controller == null || controller.getCurrentMediaItem() == null) return null;
        return controller.getCurrentMediaItem().mediaId;
    }

    // ----- Notification helpers -----
    private void notifyState() {
        if (controller == null) return;
        boolean playing = controller.isPlaying();
        String title = "", id = "";
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null) {
            id = item.mediaId != null ? item.mediaId : "";
            if (item.mediaMetadata.title != null) title = item.mediaMetadata.title.toString();
        }
        for (Listener l : new ArrayList<>(listeners)) l.onStateChanged(playing, title, id);
        startTickerIfPlaying();
    }

    private void notifyProgress() {
        if (controller == null) return;
        long pos = controller.getCurrentPosition();
        long dur = controller.getDuration();
        for (Listener l : new ArrayList<>(listeners)) l.onProgress(pos, dur);
    }

    private void notifyModes() {
        if (controller == null) return;
        boolean sh = controller.getShuffleModeEnabled();
        int rp = controller.getRepeatMode();
        for (Listener l : new ArrayList<>(listeners)) l.onModesChanged(sh, rp);
    }

    private void startTickerIfPlaying() {
        handler.removeCallbacks(ticker);
        if (controller != null && controller.isPlaying()) handler.post(ticker);
    }
}