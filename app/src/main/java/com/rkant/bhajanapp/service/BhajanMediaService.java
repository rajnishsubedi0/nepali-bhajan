package com.rkant.bhajanapp.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.ui.MusicPlayerActivity;

public class BhajanMediaService extends MediaSessionService {

    private ExoPlayer player;
    private MediaSession session;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private long resumePosition = C.TIME_UNSET;
    private MediaItem resumeItem = null;
    private boolean waitingForNetwork = false;

    @Override
    public void onCreate() {
        super.onCreate();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_NETWORK); // keep CPU + network awake while playing

        Intent openIntent = new Intent(this, MusicPlayerActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        session = new MediaSession.Builder(this, player).setSessionActivity(pi).build();

        DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider.Builder(this).build();
        provider.setSmallIcon(R.drawable.ic_music_note);
        setMediaNotificationProvider(provider);

        setupPlayerListeners();
        setupNetworkMonitor();
        player.setWakeMode(C.WAKE_MODE_NETWORK);
    }

    private boolean isNetworkAvailable() {
        try {
            if (connectivityManager == null) return false;
            Network active = connectivityManager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            return false;
        }
    }

    /** Saves the current track + position and marks that we are waiting for internet. */
    private void enterWaitingForNetworkMode() {
        if (waitingForNetwork) return;
        if (player == null || player.getCurrentMediaItem() == null) return;
        waitingForNetwork = true;
        resumePosition = player.getCurrentPosition();
        resumeItem = player.getCurrentMediaItem();
    }

    /** Called when internet comes back. Restarts the saved track from where it stopped. */
    private void resumePlayback() {
        if (!waitingForNetwork) return;
        // If ExoPlayer already recovered on its own, do nothing.
        if (player.isPlaying() || player.getPlaybackState() == Player.STATE_BUFFERING) {
            waitingForNetwork = false;
            return;
        }
        if (resumeItem == null) return;

        waitingForNetwork = false;
        player.setMediaItem(resumeItem);
        player.prepare();
        if (resumePosition != C.TIME_UNSET && resumePosition > 0) player.seekTo(resumePosition);
        player.play();
    }

    private void setupPlayerListeners() {
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                // Playback failed. If there is no internet, wait for it to return.
                if (!isNetworkAvailable()) enterWaitingForNetworkMode();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_IDLE) {
                    if (!isNetworkAvailable() && player.getPlayWhenReady()) {
                        enterWaitingForNetworkMode();
                    }
                    // NOTE: We intentionally do NOT call stopSelf() here.
                    // That was the old bug that killed the notification.
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    // Playback is healthy again; clear the saved resume state.
                    waitingForNetwork = false;
                    resumeItem = null;
                    resumePosition = C.TIME_UNSET;
                }
            }
        });
    }

    private void setupNetworkMonitor() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                mainHandler.post(() -> resumePlayback());
            }

            @Override
            public void onLost(@NonNull Network network) {
                mainHandler.post(() -> {
                    if (player != null && (player.isPlaying() || player.getPlayWhenReady())) {
                        enterWaitingForNetworkMode();
                    }
                });
            }
        };

        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(req, networkCallback);
    }

    /**
     * Keeps the notification/foreground alive while we are waiting for the network,
     * so the system does not remove it.
     */
    @Override
    public void onUpdateNotification(MediaSession session, boolean startInForegroundRequired) {
        boolean keepForeground = startInForegroundRequired || waitingForNetwork || player.isPlaying();
        super.onUpdateNotification(session, keepForeground);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player p = session.getPlayer();
        boolean active = waitingForNetwork || (p != null && p.getPlayWhenReady() && p.getMediaItemCount() > 0);
        if (!active) stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (Exception ignored) {}
        }
        if (session != null) session.release();
        if (player != null) player.release();
        super.onDestroy();
    }
}