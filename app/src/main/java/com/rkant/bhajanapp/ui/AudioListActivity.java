package com.rkant.bhajanapp.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.rkant.bhajanapp.utils.BatteryHelper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.AudioAdapter;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.utils.AudioPreferences;
import com.rkant.bhajanapp.utils.M3U8Parser;
import com.rkant.bhajanapp.utils.PlaybackManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class AudioListActivity extends AppCompatActivity implements AudioAdapter.OnTrackAction {

    private static final String M3U8_URL = "https://rajnishsubedi0.github.io/audio-streams/master.m3u8";
    private static final int REQ_NOTIFICATION = 1001;

    private AudioAdapter adapter;
    private final List<AudioTrack> tracks = new ArrayList<>();
    private SwipeRefreshLayout swipeRefresh;

    private View miniPlayer;
    private TextView miniTitle;
    private ProgressBar miniProgress;
    private ImageView miniPlay;

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            updateMiniPlayer(isPlaying, title, mediaId);
            if (adapter != null) adapter.setPlaybackState(mediaId, isPlaying);
        }

        @Override
        public void onProgress(long position, long duration) {
            if (miniPlayer.getVisibility() == View.VISIBLE && duration > 0) {
                miniProgress.setMax((int) duration);
                miniProgress.setProgress((int) position);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_list);

        requestNotificationPermission();

        RecyclerView rv = findViewById(R.id.rv_audio_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AudioAdapter(tracks, this, this);
        rv.setAdapter(adapter);
        BatteryHelper.showGuideIfNeeded(this);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadAudios);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadAudios());

        PlaybackManager.requestBatteryExemption(this);

        setupMiniPlayer();
        loadAudios();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PlaybackManager.getInstance().connect(this);
        PlaybackManager.getInstance().addListener(playbackListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        PlaybackManager.getInstance().removeListener(playbackListener);
    }

    private void setupMiniPlayer() {
        miniPlayer = findViewById(R.id.mini_player);
        miniTitle = findViewById(R.id.mini_title);
        miniProgress = findViewById(R.id.mini_progress);
        miniPlay = findViewById(R.id.mini_play);
        ImageView miniPrev = findViewById(R.id.mini_prev);
        ImageView miniNext = findViewById(R.id.mini_next);

        miniPlayer.setOnClickListener(v ->
                startActivity(new Intent(this, MusicPlayerActivity.class)));
        miniPlay.setOnClickListener(v -> PlaybackManager.getInstance().playPause());
        miniPrev.setOnClickListener(v -> PlaybackManager.getInstance().prev());
        miniNext.setOnClickListener(v -> PlaybackManager.getInstance().next());
    }

    private void updateMiniPlayer(boolean isPlaying, String title, String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) {
            miniPlayer.setVisibility(View.GONE);
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(title != null && !title.isEmpty() ? title : "Bhajan Audio");
        miniPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
    }

    private void loadAudios() {
        swipeRefresh.setRefreshing(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            List<AudioTrack> fetched = M3U8Parser.fetchAndParse(M3U8_URL);
            // attach download state
            for (AudioTrack t : fetched) {
                String local = AudioPreferences.getLocalPath(this, t.id);
                if (local != null) { t.isDownloaded = true; t.localPath = local; }
            }
            runOnUiThread(() -> {
                tracks.clear();
                tracks.addAll(fetched);
                adapter.notifyDataSetChanged();
                swipeRefresh.setRefreshing(false);
                if (tracks.isEmpty())
                    Toast.makeText(this, "No audio found", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ----- AudioAdapter.OnTrackAction -----
    @Override
    public void onPlayClick(AudioTrack track, int position) {
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);

        // FIX: Check if the tapped track is already the currently loaded track
        String currentId = pm.getCurrentMediaId();
        if (track.id != null && track.id.equals(currentId)) {
            // It's already loaded/playing. Just open the UI without resetting the position.
            startActivity(new Intent(this, MusicPlayerActivity.class));
            return;
        }

        // It's a different track. Load the new playlist and start playing.
        pm.playList(tracks, position);
        startActivity(new Intent(this, MusicPlayerActivity.class));
    }

    @Override
    public void onDownloadClick(AudioTrack track) {
        Toast.makeText(this, "Offline download requires MP3 sources. This stream is online-only.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onFavClick(AudioTrack track) {
        AudioPreferences.toggleFav(this, track.id);
        adapter.notifyDataSetChanged();
    }
}