package com.rkant.bhajanapp.ui;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.AudioAdapter;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.utils.AudioDatabase;
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
    private final List<AudioTrack> allTracks = new ArrayList<>();
    private final List<AudioTrack> displayedTracks = new ArrayList<>();
    private SwipeRefreshLayout swipeRefresh;
    private int currentTab = 0;
    private boolean isSyncing = false;

    private View miniPlayer;
    private TextView miniTitle;
    private ProgressBar miniProgress;
    private ImageView miniPlay;

    // Network monitoring for auto-refresh
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasOffline = false;

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            updateMiniPlayer(isPlaying, title, mediaId);
            if (adapter != null) adapter.setPlaybackState(mediaId, isPlaying);
        }

        @Override
        public void onProgress(long position, long duration) {
            if (miniPlayer != null && miniPlayer.getVisibility() == View.VISIBLE && duration > 0) {
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

            for (AudioTrack t : allTracks) {
                if (t.id.equals(trackId)) {
                    File f = DownloadHelper.getLocalFile(AudioListActivity.this, trackId);
                    if (f != null && f.exists()) {
                        t.isDownloaded = true;
                        t.downloadState = AudioTrack.STATE_DOWNLOADED;
                        t.localPath = f.getAbsolutePath();
                        // Persist to database
                        AudioDatabase.getInstance(AudioListActivity.this)
                                .markDownloaded(trackId, f.getAbsolutePath());
                        Toast.makeText(AudioListActivity.this, "Downloaded: " + t.title, Toast.LENGTH_SHORT).show();
                    } else {
                        t.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
                        Toast.makeText(AudioListActivity.this, "Download failed", Toast.LENGTH_SHORT).show();
                    }
                    refreshDisplayedList();
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
        adapter = new AudioAdapter(displayedTracks, this, this);
        rv.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadAudios);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadAudios());

        setupTabs();
        setupMiniPlayer();
        setupNetworkMonitor();

        // Load from database first (instant, works offline)
        loadAudios();

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * Monitor network changes - auto-refresh when internet comes back
     */
    private void setupNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Internet just became available
                if (wasOffline) {
                    wasOffline = false;
                    // Auto-sync after a short delay to ensure connection is stable
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isFinishing() && !isSyncing) {
                            syncFromNetwork();
                        }
                    }, 1000);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                wasOffline = true;
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Set initial state
        wasOffline = !AudioRepository.isNetworkAvailable(this);
    }

    private void setupTabs() {
        TabLayout tabs = findViewById(R.id.audio_tabs);
        tabs.addTab(tabs.newTab().setText("All"));
        tabs.addTab(tabs.newTab().setText("Favourites"));
        tabs.addTab(tabs.newTab().setText("Downloads"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                refreshDisplayedList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
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

    /**
     * Load tracks from database first, then sync from network if available
     */
    private void loadAudios() {
        swipeRefresh.setRefreshing(true);
        isSyncing = true;

        AudioRepository.loadTracks(this, new AudioRepository.LoadCallback() {
            @Override
            public void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork) {
                runOnUiThread(() -> {
                    allTracks.clear();
                    allTracks.addAll(tracks);
                    refreshDisplayedList();

                    if (fromNetwork) {
                        swipeRefresh.setRefreshing(false);
                        isSyncing = false;
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    isSyncing = false;
                    Toast.makeText(AudioListActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Sync only from network (called when internet reconnects)
     */
    private void syncFromNetwork() {
        isSyncing = true;
        AudioRepository.syncFromNetwork(this, new AudioRepository.LoadCallback() {
            @Override
            public void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork) {
                runOnUiThread(() -> {
                    allTracks.clear();
                    allTracks.addAll(tracks);
                    refreshDisplayedList();
                    isSyncing = false;
                    Toast.makeText(AudioListActivity.this, "Audio list updated", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    isSyncing = false;
                });
            }
        });
    }

    private void refreshDisplayedList() {
        displayedTracks.clear();
        if (currentTab == 0) {
            displayedTracks.addAll(allTracks);
        } else if (currentTab == 1) {
            for (AudioTrack t : allTracks) {
                if (t.isFavourite) {
                    displayedTracks.add(t);
                }
            }
        } else if (currentTab == 2) {
            for (AudioTrack t : allTracks) {
                if (t.isDownloaded) {
                    displayedTracks.add(t);
                }
            }
        }
        adapter.notifyDataSetChanged();
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
        pm.playList(displayedTracks, position);
        startActivity(new Intent(this, MusicPlayerActivity.class));
    }

    @Override
    public void onDownloadClick(AudioTrack track) {
        if (track.isDownloaded) {
            Helper.showConfirm(this, "Delete Download",
                    "Are you sure you want to delete \"" + track.title + "\" from your device?", "Delete", true, () -> {

                        // Stop playback if this track is currently playing
                        PlaybackManager pm = PlaybackManager.getInstance();
                        if (track.id.equals(pm.getCurrentMediaId())) {
                            pm.stopAndClear();
                        }

                        DownloadHelper.delete(this, track.id);
                        track.isDownloaded = false;
                        track.localPath = null;
                        track.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;

                        // Update database
                        AudioDatabase.getInstance(this).markNotDownloaded(track.id);

                        refreshDisplayedList();
                        Toast.makeText(this, "Download deleted", Toast.LENGTH_SHORT).show();
                    });
        } else {
            if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
                Toast.makeText(this, "Already downloading...", Toast.LENGTH_SHORT).show();
                return;
            }
            track.downloadState = AudioTrack.STATE_DOWNLOADING;
            refreshDisplayedList();
            DownloadHelper.enqueue(this, track);
            Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFavClick(AudioTrack track) {
        if (track.isFavourite) {
            Helper.showConfirm(this, "Remove from Favourites",
                    "Remove \"" + track.title + "\" from your favourites list?", "Remove", true, () -> {
                        track.isFavourite = false;
                        // Update database
                        AudioDatabase.getInstance(this).toggleFavourite(track.id);
                        refreshDisplayedList();
                        Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                    });
        } else {
            track.isFavourite = true;
            // Update database
            AudioDatabase.getInstance(this).toggleFavourite(track.id);
            refreshDisplayedList();
            Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show();
        }
    }
}