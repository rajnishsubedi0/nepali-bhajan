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
import androidx.media3.common.ForwardingPlayer;
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

    private ExoPlayer exoPlayer;
    private ForwardingPlayer player;
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

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);

        // FIX: Wrap ExoPlayer to FORCE Next/Previous buttons to always show in the notification
        player = new ForwardingPlayer(exoPlayer) {
            @Override
            public boolean isCommandAvailable(@Player.Command int command) {
                if (command == Player.COMMAND_SEEK_TO_NEXT ||
                        command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                        command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                        command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                    return true; // Always tell the OS these buttons are available
                }
                return super.isCommandAvailable(command);
            }

            @Override
            public void seekToNext() {
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNext();
                } else if (exoPlayer.getMediaItemCount() > 0) {
                    exoPlayer.seekTo(0, 0); // Loop back to the first track
                }
            }

            @Override
            public void seekToPrevious() {
                if (exoPlayer.hasPreviousMediaItem()) {
                    exoPlayer.seekToPrevious();
                } else if (exoPlayer.getMediaItemCount() > 0) {
                    exoPlayer.seekTo(exoPlayer.getMediaItemCount() - 1, 0); // Loop to the last track
                }
            }
        };

        Intent openIntent = new Intent(this, MusicPlayerActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Pass the wrapped 'player' to the MediaSession
        session = new MediaSession.Builder(this, player)
                .setSessionActivity(pi)
                .build();

        DefaultMediaNotificationProvider provider = new DefaultMediaNotificationProvider.Builder(this).build();
        provider.setSmallIcon(R.drawable.ic_music_note);
        setMediaNotificationProvider(provider);

        setupPlayerListeners();
        setupNetworkMonitor();
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

    private void enterWaitingForNetworkMode() {
        if (waitingForNetwork) return;
        if (exoPlayer == null || exoPlayer.getCurrentMediaItem() == null) return;
        waitingForNetwork = true;
        resumePosition = exoPlayer.getCurrentPosition();
        resumeItem = exoPlayer.getCurrentMediaItem();
    }

    private void resumePlayback() {
        if (!waitingForNetwork) return;
        if (exoPlayer.isPlaying() || exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
            waitingForNetwork = false;
            return;
        }
        if (resumeItem == null) return;

        waitingForNetwork = false;
        exoPlayer.setMediaItem(resumeItem);
        exoPlayer.prepare();
        if (resumePosition != C.TIME_UNSET && resumePosition > 0) exoPlayer.seekTo(resumePosition);
        exoPlayer.play();
    }

    private void setupPlayerListeners() {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (!isNetworkAvailable()) enterWaitingForNetworkMode();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_IDLE) {
                    if (!isNetworkAvailable() && exoPlayer.getPlayWhenReady()) {
                        enterWaitingForNetworkMode();
                    }
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
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
                    if (exoPlayer != null && (exoPlayer.isPlaying() || exoPlayer.getPlayWhenReady())) {
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

    @Override
    public void onUpdateNotification(MediaSession session, boolean startInForegroundRequired) {
        boolean keepForeground = startInForegroundRequired || waitingForNetwork || exoPlayer.isPlaying();
        super.onUpdateNotification(session, keepForeground);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        boolean active = waitingForNetwork || (exoPlayer != null && exoPlayer.getPlayWhenReady() && exoPlayer.getMediaItemCount() > 0);
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
        if (exoPlayer != null) exoPlayer.release();
        super.onDestroy();
    }
}