package com.rkant.bhajanapp.ui;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.rkant.bhajanapp.utils.AudioRepository;
import com.rkant.bhajanapp.utils.BatteryHelper;
import com.rkant.bhajanapp.utils.DownloadHelper;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.PlaybackManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudioListActivity extends AppCompatActivity implements AudioAdapter.OnTrackAction {

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

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            String trackId = DownloadHelper.trackIdForDownload(AudioListActivity.this, downloadId);
            if (trackId == null) return;

            for (AudioTrack t : tracks) {
                if (t.id.equals(trackId)) {
                    if (DownloadHelper.isDownloaded(AudioListActivity.this, trackId)) {
                        File f = DownloadHelper.getLocalFile(AudioListActivity.this, trackId);
                        t.isDownloaded = true;
                        t.downloadState = AudioTrack.STATE_DOWNLOADED;
                        if (f != null) t.localPath = f.getAbsolutePath();
                        Toast.makeText(AudioListActivity.this, "Downloaded: " + t.title, Toast.LENGTH_SHORT).show();
                    } else {
                        t.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
                        Toast.makeText(AudioListActivity.this, "Download failed", Toast.LENGTH_SHORT).show();
                    }
                    adapter.notifyDataSetChanged();
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_list);

        requestNotificationPermission();
        BatteryHelper.showGuideIfNeeded(this);

        RecyclerView rv = findViewById(R.id.rv_audio_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AudioAdapter(tracks, this, this);
        rv.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadAudios);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadAudios());

        setupMiniPlayer();
        loadAudios();

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
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
        AudioRepository.loadTracks(new AudioRepository.Callback() {
            @Override
            public void onLoaded(List<AudioTrack> loaded) {
                runOnUiThread(() -> {
                    for (AudioTrack t : loaded) {
                        if (DownloadHelper.isDownloaded(AudioListActivity.this, t.id)) {
                            File f = DownloadHelper.getLocalFile(AudioListActivity.this, t.id);
                            t.isDownloaded = true;
                            t.downloadState = AudioTrack.STATE_DOWNLOADED;
                            if (f != null) t.localPath = f.getAbsolutePath();
                        }
                    }
                    tracks.clear();
                    tracks.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    swipeRefresh.setRefreshing(false);
                    if (tracks.isEmpty())
                        Toast.makeText(AudioListActivity.this, "No audio found", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(AudioListActivity.this, "Failed to load audio list", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ----- AudioAdapter.OnTrackAction -----
    @Override
    public void onPlayClick(AudioTrack track, int position) {
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);

        String currentId = pm.getCurrentMediaId();
        if (track.id != null && track.id.equals(currentId)) {
            startActivity(new Intent(this, MusicPlayerActivity.class));
            return;
        }
        pm.playList(tracks, position);
        startActivity(new Intent(this, MusicPlayerActivity.class));
    }

    @Override
    public void onDownloadClick(AudioTrack track) {
        if (track.isDownloaded) {
            Helper.showConfirm(this, "Delete download",
                    "Remove \"" + track.title + "\" from offline storage?", "Delete", true, () -> {
                        DownloadHelper.delete(this, track.id);
                        track.isDownloaded = false;
                        track.localPath = null;
                        track.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    });
        } else {
            track.downloadState = AudioTrack.STATE_DOWNLOADING;
            adapter.notifyDataSetChanged();
            DownloadHelper.enqueue(this, track);
            Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFavClick(AudioTrack track) {
        AudioPreferences.toggleFav(this, track.id);
        adapter.notifyDataSetChanged();
    }
}