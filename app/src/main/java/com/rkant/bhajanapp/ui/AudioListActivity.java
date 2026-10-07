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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.AudioAdapter;
import com.rkant.bhajanapp.adapter.PlaylistAdapter;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.utils.AudioDatabase;
import com.rkant.bhajanapp.utils.AudioRepository;
import com.rkant.bhajanapp.utils.BatteryHelper;
import com.rkant.bhajanapp.utils.DownloadHelper;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.PlaybackManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioListActivity extends AppCompatActivity
        implements AudioAdapter.OnTrackAction, PlaylistAdapter.OnPlaylistAction {

    private static final int REQ_NOTIFICATION = 1001;

    private RecyclerView recyclerView;

    private AudioAdapter audioAdapter;
    private PlaylistAdapter playlistAdapter;

    private final List<AudioTrack> allTracks = new ArrayList<>();
    private final List<AudioTrack> displayedTracks = new ArrayList<>();

    private SwipeRefreshLayout swipeRefresh;

    private int currentTab = 0;
    private boolean isSyncing = false;

    private View miniPlayer;
    private TextView miniTitle;
    private ProgressBar miniProgress;
    private ImageView miniPlay;

    private TextView audioTitle;
    private ImageView btnCreatePlaylist;

    private View bottomBar;
    private TextView tvAudioCount;
    private View btnClearRecent;

    private String selectedPlaylist = null;
    private boolean currentlyPlaying = false;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasOffline = false;

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            currentlyPlaying = isPlaying;
            updateMiniPlayer(isPlaying, title, mediaId);
            if (audioAdapter != null) {
                audioAdapter.setPlaybackState(mediaId, isPlaying);
            }
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

            for (AudioTrack track : allTracks) {
                if (track.id.equals(trackId)) {
                    File file = DownloadHelper.getLocalFile(AudioListActivity.this, trackId);

                    if (file != null && file.exists()) {
                        track.isDownloaded = true;
                        track.downloadState = AudioTrack.STATE_DOWNLOADED;
                        track.localPath = file.getAbsolutePath();

                        AudioDatabase.getInstance(AudioListActivity.this)
                                .markDownloaded(trackId, file.getAbsolutePath());

                        Toast.makeText(AudioListActivity.this, "Downloaded: " + track.title, Toast.LENGTH_SHORT).show();
                    } else {
                        track.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
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

        recyclerView = findViewById(R.id.rv_audio_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        audioAdapter = new AudioAdapter(displayedTracks, this, this);
        playlistAdapter = new PlaylistAdapter(this, this);

        recyclerView.setAdapter(audioAdapter);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> loadAudios(true));

        audioTitle = findViewById(R.id.tv_audio_title);
        btnCreatePlaylist = findViewById(R.id.btn_create_playlist);

        bottomBar = findViewById(R.id.bottom_bar);
        tvAudioCount = findViewById(R.id.tv_audio_count);
        btnClearRecent = findViewById(R.id.btn_clear_recent);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadAudios(true));
        findViewById(R.id.btn_download_all).setOnClickListener(v -> confirmDownloadAll());
        btnCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog(null));

        btnClearRecent.setOnClickListener(v -> Helper.showConfirm(
                this,
                "Clear Recent",
                "Remove all recently played audio from the Recent list?",
                "Clear",
                true,
                () -> {
                    Helper.clearAudioRecents(this);
                    refreshDisplayedList();
                    Toast.makeText(this, "Recent cleared", Toast.LENGTH_SHORT).show();
                }
        ));

        setupTabs();
        setupMiniPlayer();
        setupNetworkMonitor();
        updateTopBar();

        loadAudios(false);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    // ═══════════════════════════════════════════════════════════
    // LOAD / SYNC
    // ═══════════════════════════════════════════════════════════

    private void loadAudios(boolean showSpinner) {
        if (showSpinner) swipeRefresh.setRefreshing(true);

        isSyncing = true;

        AudioRepository.loadTracks(this, new AudioRepository.LoadCallback() {
            @Override
            public void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork) {
                runOnUiThread(() -> {
                    allTracks.clear();
                    allTracks.addAll(tracks);
                    refreshDisplayedList();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (showSpinner) {
                        Toast.makeText(AudioListActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    isSyncing = false;
                });
            }
        });
    }

    private void syncFromNetwork() {
        if (isSyncing) return;

        isSyncing = true;

        AudioRepository.syncFromNetwork(this, new AudioRepository.LoadCallback() {
            @Override
            public void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork) {
                runOnUiThread(() -> {
                    allTracks.clear();
                    allTracks.addAll(tracks);
                    refreshDisplayedList();

                    if (fromNetwork) {
                        Toast.makeText(AudioListActivity.this, "Audio list updated", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                // Silent sync
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    isSyncing = false;
                });
            }
        });
    }

    private void setupNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                if (wasOffline) {
                    wasOffline = false;
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

        wasOffline = !AudioRepository.isNetworkAvailable(this);
    }

    // ═══════════════════════════════════════════════════════════
    // TABS / LISTS
    // ═══════════════════════════════════════════════════════════

    private void setupTabs() {
        TabLayout tabs = findViewById(R.id.audio_tabs);

        tabs.addTab(tabs.newTab().setText("All"));
        tabs.addTab(tabs.newTab().setText("Favourites"));
        tabs.addTab(tabs.newTab().setText("Downloads"));
        tabs.addTab(tabs.newTab().setText("Recent"));
        tabs.addTab(tabs.newTab().setText("Playlists"));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                selectedPlaylist = null;
                updateTopBar();
                refreshDisplayedList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void refreshDisplayedList() {
        // Playlist list view
        if (currentTab == 4 && selectedPlaylist == null) {
            if (recyclerView.getAdapter() != playlistAdapter) {
                recyclerView.setAdapter(playlistAdapter);
            }
            refreshPlaylists();
            updateBottomBar();
            return;
        }

        if (recyclerView.getAdapter() != audioAdapter) {
            recyclerView.setAdapter(audioAdapter);
        }

        displayedTracks.clear();

        if (currentTab == 0) {
            displayedTracks.addAll(allTracks);
        } else if (currentTab == 1) {
            for (AudioTrack t : allTracks) {
                if (t.isFavourite) displayedTracks.add(t);
            }
        } else if (currentTab == 2) {
            for (AudioTrack t : allTracks) {
                if (t.isDownloaded) displayedTracks.add(t);
            }
        } else if (currentTab == 3) {
            List<String> recentIds = Helper.getAudioRecentIds(this);
            if (recentIds.size() > 10) recentIds = recentIds.subList(0, 10);

            for (String id : recentIds) {
                for (AudioTrack t : allTracks) {
                    if (t.id.equals(id)) {
                        displayedTracks.add(t);
                        break;
                    }
                }
            }
        } else if (currentTab == 4 && selectedPlaylist != null) {
            Map<String, List<String>> playlists = Helper.getPlaylists(this);
            List<String> ids = playlists.get(selectedPlaylist);

            if (ids == null) {
                selectedPlaylist = null;
                updateTopBar();
                refreshDisplayedList();
                return;
            }

            for (String id : ids) {
                for (AudioTrack t : allTracks) {
                    if (t.id.equals(id)) {
                        displayedTracks.add(t);
                        break;
                    }
                }
            }
        }

        audioAdapter.notifyDataSetChanged();
        updateBottomBar();
    }

    private void refreshPlaylists() {
        Map<String, List<String>> playlists = Helper.getPlaylists(this);
        List<String> names = new ArrayList<>(playlists.keySet());
        Map<String, Integer> counts = new HashMap<>();

        for (String name : names) {
            counts.put(name, playlists.get(name).size());
        }

        playlistAdapter.update(names, counts);
    }

    private void updateTopBar() {
        audioTitle.setText(selectedPlaylist != null && !selectedPlaylist.isEmpty()
                ? selectedPlaylist
                : "Bhajan Audio");

        btnCreatePlaylist.setVisibility(currentTab == 4 && selectedPlaylist == null
                ? View.VISIBLE
                : View.GONE);
    }

    private void updateBottomBar() {
        boolean showBar = currentTab == 3 || (currentTab == 4 && selectedPlaylist != null);

        bottomBar.setVisibility(showBar ? View.VISIBLE : View.GONE);

        if (showBar) {
            tvAudioCount.setText(displayedTracks.size() + (displayedTracks.size() == 1 ? " track" : " tracks"));
            btnClearRecent.setVisibility(currentTab == 3 ? View.VISIBLE : View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // MINI PLAYER
    // ═══════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════
    // TRACK OPTIONS BOTTOM SHEET (long-press popup)
    // ═══════════════════════════════════════════════════════════

    private void showTrackOptions(AudioTrack track) {
        if (track == null) return;

        PlaybackManager pm = PlaybackManager.getInstance();
        boolean isCurrent = track.id != null && track.id.equals(pm.getCurrentMediaId());

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_track_options, null);

        TextView sheetTitle = v.findViewById(R.id.sheet_title);
        TextView sheetDuration = v.findViewById(R.id.sheet_duration);
        TextView sheetSource = v.findViewById(R.id.sheet_source);
        TextView sheetStatus = v.findViewById(R.id.sheet_status);
        TextView sheetPlay = v.findViewById(R.id.sheet_play);
        TextView sheetFav = v.findViewById(R.id.sheet_fav);
        TextView sheetPlaylist = v.findViewById(R.id.sheet_playlist);
        TextView sheetDownload = v.findViewById(R.id.sheet_download);
        TextView sheetRemovePlaylist = v.findViewById(R.id.sheet_remove_playlist);
        View sheetClose = v.findViewById(R.id.sheet_close);

        sheetTitle.setText(track.title);
        sheetDuration.setText("Duration: " + (track.durationSec > 0
                ? Helper.formatTime(track.durationSec * 1000L)
                : "--:--"));
        sheetSource.setText(track.isDownloaded ? "Saved on this device" : "Streams from online");

        if (track.isDownloaded) {
            sheetStatus.setText("Downloaded");
            sheetStatus.setTextColor(getColor(R.color.accent));
        } else if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
            sheetStatus.setText("Downloading…");
            sheetStatus.setTextColor(getColor(R.color.accent));
        } else {
            sheetStatus.setText("Not downloaded");
            sheetStatus.setTextColor(getColor(R.color.text_secondary));
        }

        sheetPlay.setText(isCurrent && currentlyPlaying ? "Pause" : "Play");
        sheetPlay.setOnClickListener(x -> {
            dialog.dismiss();
            if (isCurrent) {
                pm.playPause();
            } else {
                int pos = displayedTracks.indexOf(track);
                if (pos >= 0) {
                    pm.connect(this);
                    pm.playList(displayedTracks, pos);
                }
            }
        });

        if (track.isFavourite) {
            sheetFav.setText("Remove from Favourites");
            sheetFav.setTextColor(getColor(R.color.red));
        } else {
            sheetFav.setText("Add to Favourites");
            sheetFav.setTextColor(getColor(R.color.text_primary));
        }
        sheetFav.setOnClickListener(x -> {
            dialog.dismiss();
            toggleFavourite(track);
        });

        sheetPlaylist.setOnClickListener(x -> {
            dialog.dismiss();
            showAddToPlaylistDialog(track);
        });

        if (track.isDownloaded) {
            sheetDownload.setText("Delete Download");
            sheetDownload.setTextColor(getColor(R.color.red));
        } else if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
            sheetDownload.setText("Downloading…");
            sheetDownload.setTextColor(getColor(R.color.text_secondary));
        } else {
            sheetDownload.setText("Download");
            sheetDownload.setTextColor(getColor(R.color.text_primary));
        }
        sheetDownload.setOnClickListener(x -> {
            dialog.dismiss();
            handleDownloadAction(track);
        });

        if (selectedPlaylist != null) {
            sheetRemovePlaylist.setVisibility(View.VISIBLE);
            sheetRemovePlaylist.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeFromPlaylist(this, selectedPlaylist, track.id);
                refreshDisplayedList();
                Toast.makeText(this, "Removed from playlist", Toast.LENGTH_SHORT).show();
            });
        }

        sheetClose.setOnClickListener(x -> dialog.dismiss());

        dialog.setContentView(v);
        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════
    // SHARED ACTIONS (kept in sync across ALL tabs)
    // ═══════════════════════════════════════════════════════════

    private void toggleFavourite(AudioTrack track) {
        if (track.isFavourite) {
            Helper.showConfirm(this, "Remove from Favourites",
                    "Remove \"" + track.title + "\" from favourites?",
                    "Remove", true, () -> {
                        track.isFavourite = false;
                        AudioDatabase.getInstance(this).toggleFavourite(track.id);
                        refreshDisplayedList();
                        Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                    });
        } else {
            track.isFavourite = true;
            AudioDatabase.getInstance(this).toggleFavourite(track.id);
            refreshDisplayedList();
            Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleDownloadAction(AudioTrack track) {
        if (track.isDownloaded) {
            Helper.showConfirm(this, "Delete Download",
                    "Delete \"" + track.title + "\" from your device?",
                    "Delete", true, () -> {
                        PlaybackManager pm = PlaybackManager.getInstance();
                        if (track.id.equals(pm.getCurrentMediaId())) {
                            pm.stopAndClear();
                        }

                        DownloadHelper.delete(this, track.id);

                        track.isDownloaded = false;
                        track.localPath = null;
                        track.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;

                        AudioDatabase.getInstance(this).markNotDownloaded(track.id);

                        refreshDisplayedList();
                        Toast.makeText(this, "Download deleted", Toast.LENGTH_SHORT).show();
                    });
        } else {
            if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
                Toast.makeText(this, "Already downloading…", Toast.LENGTH_SHORT).show();
                return;
            }

            track.downloadState = AudioTrack.STATE_DOWNLOADING;
            refreshDisplayedList();
            DownloadHelper.enqueue(this, track);
            Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DOWNLOAD ALL
    // ═══════════════════════════════════════════════════════════

    private void confirmDownloadAll() {
        int count = 0;
        for (AudioTrack t : allTracks) {
            if (!t.isDownloaded && t.downloadState != AudioTrack.STATE_DOWNLOADING) count++;
        }

        if (count == 0) {
            Toast.makeText(this, "No new tracks to download", Toast.LENGTH_SHORT).show();
            return;
        }

        int finalCount = count;
        Helper.showConfirm(this, "Download All",
                "Download " + finalCount + " audio tracks?",
                "Download", false, () -> {
                    int started = 0;
                    for (AudioTrack t : allTracks) {
                        if (!t.isDownloaded && t.downloadState != AudioTrack.STATE_DOWNLOADING) {
                            t.downloadState = AudioTrack.STATE_DOWNLOADING;
                            DownloadHelper.enqueue(this, t);
                            started++;
                        }
                    }
                    if (started > 0) {
                        refreshDisplayedList();
                        Toast.makeText(this, "Downloading " + started + " tracks…", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════
    // PLAYLIST DIALOGS
    // ═══════════════════════════════════════════════════════════

    private void showCreatePlaylistDialog(AudioTrack trackToAdd) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_playlist_name, null);
        EditText etName = view.findViewById(R.id.et_playlist_name);

        new AlertDialog.Builder(this)
                .setTitle("New Playlist")
                .setView(view)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = Helper.sanitizePlaylistName(etName.getText().toString());

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Playlist name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Helper.createPlaylist(this, name);

                    if (trackToAdd != null) {
                        Helper.addToPlaylist(this, name, trackToAdd.id);
                        Toast.makeText(this, "Added to \"" + name + "\"", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Playlist created", Toast.LENGTH_SHORT).show();
                    }

                    if (currentTab == 4 && selectedPlaylist == null) {
                        refreshPlaylists();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddToPlaylistDialog(AudioTrack track) {
        if (track == null) return;

        Map<String, List<String>> playlists = Helper.getPlaylists(this);
        List<String> names = new ArrayList<>(playlists.keySet());

        if (names.isEmpty()) {
            showCreatePlaylistDialog(track);
            return;
        }

        names.add("+ Create new playlist");

        new AlertDialog.Builder(this)
                .setTitle("Add to Playlist")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == names.size() - 1) {
                        showCreatePlaylistDialog(track);
                    } else {
                        String selected = names.get(which);
                        Helper.addToPlaylist(this, selected, track.id);
                        Toast.makeText(this, "Added to \"" + selected + "\"", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════
    // PlaylistAdapter.OnPlaylistAction
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onPlaylistClick(String name) {
        selectedPlaylist = name;
        updateTopBar();
        refreshDisplayedList();
    }

    @Override
    public void onPlaylistDelete(String name) {
        Helper.showConfirm(this, "Delete Playlist",
                "Delete playlist \"" + name + "\"?",
                "Delete", true, () -> {
                    Helper.deletePlaylist(this, name);
                    refreshPlaylists();
                    Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                });
    }

    // ═══════════════════════════════════════════════════════════
    // AudioAdapter.OnTrackAction
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onRowClick(AudioTrack track, int position) {
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
    public void onPlayPauseClick(AudioTrack track, int position) {
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);

        String currentId = pm.getCurrentMediaId();

        if (track.id != null && track.id.equals(currentId)) {
            pm.playPause();
        } else {
            pm.playList(displayedTracks, position);
        }
    }

    @Override
    public void onOptionsClick(AudioTrack track, int position) {
        showTrackOptions(track);
    }

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════

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
    protected void onResume() {
        super.onResume();
        // Keeps favourites / downloads / recents in sync when returning from player
        refreshDisplayedList();
    }

    @Override
    protected void onStop() {
        super.onStop();
        PlaybackManager.getInstance().removeListener(playbackListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab == 4 && selectedPlaylist != null) {
            selectedPlaylist = null;
            updateTopBar();
            refreshDisplayedList();
            return;
        }

        super.onBackPressed();
    }
}