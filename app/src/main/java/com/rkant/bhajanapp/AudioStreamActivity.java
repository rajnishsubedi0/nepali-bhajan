package com.rkant.bhajanapp;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class AudioStreamActivity extends AppCompatActivity {

    private ExoPlayer player;
    private ImageView btnPlayPause;
    private ProgressBar progressLoading;
    private TextView tvStreamStatus;

    // TODO: Update this URL if your file is named differently (e.g., index.m3u8 or live.m3u8)
    private static final String STREAM_URL = "https://rajnishsubedi0.github.io/audio-streams/master.m3u8";
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_stream);

        ThemeHelper.styleActionBar(getSupportActionBar(), getWindow(), this);

        btnPlayPause = findViewById(R.id.btn_play_pause);
        progressLoading = findViewById(R.id.progress_loading);
        tvStreamStatus = findViewById(R.id.tv_stream_status);
        ImageView btnBack = findViewById(R.id.btn_back_audio);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        initializePlayer();

        btnPlayPause.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            }
        });
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();

        // Set Audio Attributes for proper media focus handling (pauses during calls, etc.)
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(audioAttributes, true);

        MediaItem mediaItem = MediaItem.fromUri(STREAM_URL);
        player.setMediaItem(mediaItem);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        progressLoading.setVisibility(View.VISIBLE);
                        btnPlayPause.setVisibility(View.INVISIBLE);
                        tvStreamStatus.setText("Buffering...");
                        break;
                    case Player.STATE_READY:
                        progressLoading.setVisibility(View.GONE);
                        btnPlayPause.setVisibility(View.VISIBLE);
                        isPlaying = player.getPlayWhenReady();
                        updatePlayPauseIcon();
                        tvStreamStatus.setText(isPlaying ? "Streaming Live..." : "Paused");
                        break;
                    case Player.STATE_ENDED:
                        tvStreamStatus.setText("Stream Ended");
                        break;
                    case Player.STATE_IDLE:
                        tvStreamStatus.setText("Ready to Stream");
                        break;
                }
            }

            @Override
            public void onIsPlayingChanged(boolean currentIsPlaying) {
                isPlaying = currentIsPlaying;
                updatePlayPauseIcon();
                if (currentIsPlaying) {
                    tvStreamStatus.setText("Streaming Live...");
                } else if (player.getPlaybackState() == Player.STATE_READY) {
                    tvStreamStatus.setText("Paused");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                progressLoading.setVisibility(View.GONE);
                btnPlayPause.setVisibility(View.VISIBLE);
                tvStreamStatus.setText("Stream Offline or Error");
                CustomToast.showError(AudioStreamActivity.this, "Failed to load stream");
            }
        });

        player.prepare();
        player.setPlayWhenReady(false); // Do not auto-play, wait for user click
    }

    private void updatePlayPauseIcon() {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer(); // Release player when leaving to save bandwidth/battery
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}