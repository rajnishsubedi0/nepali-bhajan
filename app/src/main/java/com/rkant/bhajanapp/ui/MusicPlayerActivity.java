package com.rkant.bhajanapp.ui;

import android.animation.ValueAnimator;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.Player;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.utils.AudioDatabase;
import com.rkant.bhajanapp.utils.AudioRepository;
import com.rkant.bhajanapp.utils.DownloadHelper;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.PlaybackManager;
import com.rkant.bhajanapp.utils.SheetBus;

import java.util.List;

public class MusicPlayerActivity extends AppCompatActivity implements SheetDragLayout.DragCallback {

    public static volatile boolean isSheetOpen = false;

    private SheetDragLayout sheetRoot;
    private boolean closing = false;

    private TextView tvTitle, tvCurrentTime, tvTotalTime, tvSpeed, tvSleep;
    private SeekBar seekBar;
    private ImageView btnPlayPause, btnShuffle, btnRepeat, btnFav, btnDownload;

    private boolean isTracking = false;
    private String currentMediaId = null;
    private AudioTrack currentTrack = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable sleepTick = new Runnable() {
        @Override
        public void run() {
            updateSleepTimerLabel();
            uiHandler.postDelayed(this, 10000);
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            loadCurrentTrack();
        }
    };

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            if (title != null && !title.isEmpty()) tvTitle.setText(title);

            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

            if (mediaId != null && !mediaId.equals(currentMediaId)) {
                currentMediaId = mediaId;
                Helper.addAudioRecent(MusicPlayerActivity.this, mediaId);
                updateFavButton();
                loadCurrentTrack();
            }
        }

        @Override
        public void onProgress(long position, long duration) {
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
            updateRepeatIcon(repeatMode);
        }

        @Override
        public void onTimerChanged(long remainingMs) {
            updateSleepTimerLabel();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        sheetRoot = findViewById(R.id.sheet_root);
        sheetRoot.setDragCallback(this);

        tvTitle = findViewById(R.id.tv_title);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvSpeed = findViewById(R.id.tv_speed);
        tvSleep = findViewById(R.id.tv_sleep);

        seekBar = findViewById(R.id.seek_bar);

        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnFav = findViewById(R.id.btn_fav);
        btnDownload = findViewById(R.id.btn_download);

        ImageView btnPrev = findViewById(R.id.btn_prev);
        ImageView btnNext = findViewById(R.id.btn_next);

        btnPlayPause.setOnClickListener(v -> PlaybackManager.getInstance().playPause());
        btnPrev.setOnClickListener(v -> PlaybackManager.getInstance().prev());
        btnNext.setOnClickListener(v -> PlaybackManager.getInstance().next());
        btnShuffle.setOnClickListener(v -> PlaybackManager.getInstance().toggleShuffle());
        btnRepeat.setOnClickListener(v -> PlaybackManager.getInstance().cycleRepeat());

        tvSpeed.setOnClickListener(v -> cyclePlaybackSpeed());
        tvSleep.setOnClickListener(v -> showSleepTimerDialog());

        btnFav.setOnClickListener(v -> {
            if (currentMediaId == null) return;

            AudioDatabase db = AudioDatabase.getInstance(this);
            boolean isCurrentlyFav = db.isFavourite(currentMediaId);

            if (isCurrentlyFav) {
                Helper.showConfirm(this, "Remove from Favourites",
                        "Remove this bhajan from favourites?",
                        "Remove", true, () -> {
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

        btnDownload.setOnClickListener(v -> {
            if (currentTrack == null) {
                Toast.makeText(this, "Track information not available yet", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentTrack.isDownloaded) {
                Helper.showConfirm(this, "Delete Download",
                        "Delete \"" + currentTrack.title + "\" from your device?",
                        "Delete", true, () -> {
                            PlaybackManager pm = PlaybackManager.getInstance();
                            if (currentTrack.id.equals(pm.getCurrentMediaId())) {
                                pm.stopAndClear();
                            }

                            DownloadHelper.delete(this, currentTrack.id);

                            currentTrack.isDownloaded = false;
                            currentTrack.localPath = null;
                            currentTrack.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;

                            AudioDatabase.getInstance(this).markNotDownloaded(currentTrack.id);

                            updateDownloadButton();
                            Toast.makeText(this, "Download deleted", Toast.LENGTH_SHORT).show();
                        });
            } else if (currentTrack.downloadState == AudioTrack.STATE_DOWNLOADING) {
                Toast.makeText(this, "Already downloading…", Toast.LENGTH_SHORT).show();
            } else {
                currentTrack.downloadState = AudioTrack.STATE_DOWNLOADING;
                updateDownloadButton();
                DownloadHelper.enqueue(this, currentTrack);
                Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) PlaybackManager.getInstance().seekTo(progress);
                tvCurrentTime.setText(Helper.formatTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                isTracking = false;
            }
        });

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    // ═══════════════════════════════════════════════════════════
    // DRAG TO DISMISS (bottom-sheet behavior)
    // ═══════════════════════════════════════════════════════════

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    public void onSheetDrag(float fraction) {
        if (closing) return;

        float height = Math.max(1, sheetRoot.getHeight());
        sheetRoot.setTranslationY(fraction * height);

        float scale = 1f - 0.06f * fraction;
        sheetRoot.setPivotX(sheetRoot.getWidth() / 2f);
        sheetRoot.setPivotY(0f);
        sheetRoot.setScaleX(scale);
        sheetRoot.setScaleY(scale);

        SheetBus.emit(1f - fraction);
    }

    @Override
    public void onSheetRelease(float fraction, float velocityY) {
        if (closing) return;

        if (fraction > 0.35f || velocityY > 1500f) {
            performClose();
        } else {
            springBack();
        }
    }

    private void springBack() {
        float from = sheetRoot.getTranslationY();
        float height = Math.max(1, sheetRoot.getHeight());

        ValueAnimator va = ValueAnimator.ofFloat(from, 0f);
        va.setDuration(220);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            sheetRoot.setTranslationY(v);

            float f = clamp01(v / height);
            float scale = 1f - 0.06f * f;
            sheetRoot.setScaleX(scale);
            sheetRoot.setScaleY(scale);

            SheetBus.emit(1f - f);
        });
        va.start();
    }

    private void performClose() {
        if (closing) return;
        closing = true;
        sheetRoot.setDragCallback(null);

        float from = sheetRoot.getTranslationY();
        float to = Math.max(1, sheetRoot.getHeight());

        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(230);
        va.setInterpolator(new AccelerateInterpolator());
        va.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            sheetRoot.setTranslationY(v);

            float f = clamp01(v / to);
            float scale = 1f - 0.06f * f;
            sheetRoot.setScaleX(scale);
            sheetRoot.setScaleY(scale);

            SheetBus.emit(1f - f);
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finish();
                overridePendingTransition(0, 0);
            }
        });
        va.start();
    }

    @Override
    public void onBackPressed() {
        if (!closing) {
            performClose();
            return;
        }
        super.onBackPressed();
    }

    // ═══════════════════════════════════════════════════════════
    // CURRENT TRACK (download button)
    // ═══════════════════════════════════════════════════════════

    private void loadCurrentTrack() {
        final String id = currentMediaId;
        if (id == null) {
            btnDownload.setVisibility(View.GONE);
            return;
        }

        AudioRepository.loadTracks(this, new AudioRepository.LoadCallback() {
            @Override
            public void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork) {
                runOnUiThread(() -> {
                    if (!id.equals(currentMediaId)) return;

                    for (AudioTrack t : tracks) {
                        if (t.id.equals(id)) {
                            currentTrack = t;
                            updateDownloadButton();
                            return;
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void updateDownloadButton() {
        if (currentTrack == null) {
            btnDownload.setVisibility(View.GONE);
            return;
        }

        btnDownload.setVisibility(View.VISIBLE);

        if (currentTrack.isDownloaded) {
            btnDownload.setImageResource(R.drawable.ic_check);
            btnDownload.setColorFilter(getColor(R.color.accent));
        } else if (currentTrack.downloadState == AudioTrack.STATE_DOWNLOADING) {
            btnDownload.setImageResource(R.drawable.ic_download);
            btnDownload.setColorFilter(getColor(R.color.accent));
        } else {
            btnDownload.setImageResource(R.drawable.ic_download);
            btnDownload.setColorFilter(getColor(R.color.text_secondary));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SPEED / SLEEP / REPEAT / FAV
    // ═══════════════════════════════════════════════════════════

    private void cyclePlaybackSpeed() {
        float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

        float current = PlaybackManager.getInstance().getPlaybackSpeed();
        int index = 0;

        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - current) < 0.01f) {
                index = i;
                break;
            }
        }

        float next = speeds[(index + 1) % speeds.length];
        PlaybackManager.getInstance().setPlaybackSpeed(next);

        updateSpeedLabel();
        Toast.makeText(this, "Speed: " + speedLabel(next), Toast.LENGTH_SHORT).show();
    }

    private void updateSpeedLabel() {
        tvSpeed.setText(speedLabel(PlaybackManager.getInstance().getPlaybackSpeed()));
    }

    private String speedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.001f) {
            return Math.round(speed) + "x";
        }
        return speed + "x";
    }

    private void showSleepTimerDialog() {
        String[] options = {"Off", "5 minutes", "15 minutes", "30 minutes", "45 minutes", "60 minutes"};

        new AlertDialog.Builder(this)
                .setTitle("Sleep Timer")
                .setItems(options, (dialog, which) -> {
                    PlaybackManager pm = PlaybackManager.getInstance();

                    if (which == 0) {
                        pm.cancelSleepTimer();
                        Toast.makeText(this, "Sleep timer off", Toast.LENGTH_SHORT).show();
                    } else {
                        int minutes;
                        if (which == 1) minutes = 5;
                        else if (which == 2) minutes = 15;
                        else if (which == 3) minutes = 30;
                        else if (which == 4) minutes = 45;
                        else minutes = 60;

                        pm.setSleepTimer(minutes * 60000L);
                        Toast.makeText(this, "Sleep timer: " + minutes + " minutes", Toast.LENGTH_SHORT).show();
                    }

                    updateSleepTimerLabel();
                })
                .show();
    }

    private void updateSleepTimerLabel() {
        long remaining = PlaybackManager.getInstance().getSleepTimerRemaining();

        if (remaining <= 0) {
            tvSleep.setText("Sleep");
            tvSleep.setTextColor(getColor(R.color.text_secondary));
        } else {
            long minutes = (remaining + 59999) / 60000;
            if (minutes <= 0) minutes = 1;

            tvSleep.setText(minutes + "m");
            tvSleep.setTextColor(getColor(R.color.accent));
        }
    }

    private void updateRepeatIcon(int repeatMode) {
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            btnRepeat.setColorFilter(getColor(R.color.accent));
        } else if (repeatMode == Player.REPEAT_MODE_ALL) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setColorFilter(getColor(R.color.accent));
        } else {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setColorFilter(getColor(R.color.text_secondary));
        }
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
        isSheetOpen = true;

        PlaybackManager.getInstance().connect(this);
        PlaybackManager.getInstance().addListener(playbackListener);

        currentMediaId = PlaybackManager.getInstance().getCurrentMediaId();

        updateFavButton();
        updateSpeedLabel();
        updateSleepTimerLabel();
        loadCurrentTrack();

        uiHandler.removeCallbacks(sleepTick);
        uiHandler.postDelayed(sleepTick, 10000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isSheetOpen = false;

        PlaybackManager.getInstance().removeListener(playbackListener);
        uiHandler.removeCallbacks(sleepTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
    }
}