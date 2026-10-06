package com.rkant.bhajanapp.service;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
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

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                true);
        player.setHandleAudioBecomingNoisy(true); // pause when headphones unplugged

        Intent openIntent = new Intent(this, MusicPlayerActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        session = new MediaSession.Builder(this, player)
                .setSessionActivity(pi)
                .build();

        DefaultMediaNotificationProvider provider =
                new DefaultMediaNotificationProvider.Builder(this).build();
        provider.setSmallIcon(R.drawable.ic_music_note);
        setMediaNotificationProvider(provider);

        // Stop the service when the player becomes idle
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_IDLE) stopSelf();
            }
        });
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    // Stop service when the app is swiped away from recents
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player p = session.getPlayer();
        if (p == null || !p.getPlayWhenReady() || p.getMediaItemCount() == 0) stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        session.release();
        player.release();
        super.onDestroy();
    }
}