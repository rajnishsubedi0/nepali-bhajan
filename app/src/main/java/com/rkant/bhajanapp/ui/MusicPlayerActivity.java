package com.rkant.bhajanapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.Player;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.service.BhajanMediaService;
import com.rkant.bhajanapp.utils.AudioDatabase;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.PlaybackManager;

public class MusicPlayerActivity extends AppCompatActivity {

    private TextView tvTitle, tvCurrentTime, tvTotalTime;
    private SeekBar seekBar;
    private ImageView btnPlayPause, btnShuffle, btnRepeat, btnFav;
    private boolean isTracking = false;
    private long currentDuration = C.TIME_UNSET;
    private String currentMediaId = null;

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            if (title != null && !title.isEmpty()) tvTitle.setText(title);
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

            if (mediaId != null && !mediaId.equals(currentMediaId)) {
                currentMediaId = mediaId;
                updateFavButton();
            }
        }

        @Override
        public void onProgress(long position, long duration) {
            currentDuration = duration;
            boolean seekable = duration > 0 && duration != C.TIME_UNSET;
            tvTotalTime.setText(Helper.formatTime(duration));
            seekBar.setEnabled(seekable);

            if (seekable && !isTracking) {
                seekBar.setMax((int) duration);
                seekBar.setProgress((int) position);
            }
            tvCurrentTime.setText(Helper.formatTime(position));
        }

        @Override
        public void onModesChanged(boolean shuffle, int repeatMode) {
            btnShuffle.setColorFilter(getColor(shuffle ? R.color.accent : R.color.text_secondary));
            btnRepeat.setColorFilter(getColor(repeatMode != Player.REPEAT_MODE_OFF
                    ? R.color.accent : R.color.text_secondary));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        tvTitle = findViewById(R.id.tv_title);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        seekBar = findViewById(R.id.seek_bar);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnFav = findViewById(R.id.btn_fav);
        ImageView btnPrev = findViewById(R.id.btn_prev);
        ImageView btnNext = findViewById(R.id.btn_next);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            PlaybackManager.getInstance().stopAndClear();
            stopService(new Intent(this, BhajanMediaService.class));
            finishAffinity();
        });

        btnPlayPause.setOnClickListener(v -> PlaybackManager.getInstance().playPause());
        btnPrev.setOnClickListener(v -> PlaybackManager.getInstance().prev());
        btnNext.setOnClickListener(v -> PlaybackManager.getInstance().next());
        btnShuffle.setOnClickListener(v -> PlaybackManager.getInstance().toggleShuffle());
        btnRepeat.setOnClickListener(v -> PlaybackManager.getInstance().cycleRepeat());

        btnFav.setOnClickListener(v -> {
            if (currentMediaId == null) return;

            AudioDatabase db = AudioDatabase.getInstance(this);
            boolean isCurrentlyFav = db.isFavourite(currentMediaId);

            if (isCurrentlyFav) {
                Helper.showConfirm(this, "Remove from Favourites",
                        "Remove this bhajan from your favourites list?", "Remove", true, () -> {
                            db.toggleFavourite(currentMediaId);
                            updateFavButton();
                            Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                        });
            } else {
                db.toggleFavourite(currentMediaId);
                updateFavButton();
                Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) PlaybackManager.getInstance().seekTo(progress);
                tvCurrentTime.setText(Helper.formatTime(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { isTracking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { isTracking = false; }
        });
    }

    private void updateFavButton() {
        if (currentMediaId == null) return;
        boolean isFav = AudioDatabase.getInstance(this).isFavourite(currentMediaId);
        if (isFav) {
            btnFav.setImageResource(R.drawable.ic_heart_filled);
            btnFav.setColorFilter(getColor(R.color.red));
        } else {
            btnFav.setImageResource(R.drawable.ic_heart_outline);
            btnFav.setColorFilter(getColor(R.color.text_secondary));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PlaybackManager.getInstance().connect(this);
        PlaybackManager.getInstance().addListener(playbackListener);
        currentMediaId = PlaybackManager.getInstance().getCurrentMediaId();
        updateFavButton();
    }

    @Override
    protected void onStop() {
        super.onStop();
        PlaybackManager.getInstance().removeListener(playbackListener);
    }
}