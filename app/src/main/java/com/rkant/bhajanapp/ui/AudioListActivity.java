package com.rkant.bhajanapp.ui;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.rkant.bhajanapp.utils.SheetBus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioListActivity extends AppCompatActivity
        implements AudioAdapter.OnTrackAction, PlaylistAdapter.OnPlaylistAction, SheetBus.Listener {

    private static final int REQ_NOTIFICATION = 1001;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_FAV = 1;
    private static final int FILTER_DOWNLOADS = 2;
    private static final int FILTER_RECENT = 3;
    private static final int FILTER_PLAYLISTS = 4;

    private RecyclerView recyclerView;
    private AudioAdapter audioAdapter;
    private PlaylistAdapter playlistAdapter;

    private final List<AudioTrack> allTracks = new ArrayList<>();
    private final List<AudioTrack> displayedTracks = new ArrayList<>();

    private SwipeRefreshLayout swipeRefresh;

    private int currentFilter = FILTER_ALL;
    private String selectedPlaylist = null;
    private boolean isSyncing = false;
    private boolean currentlyPlaying = false;

    private ImageView filterAll, filterFav, filterDownload, filterRecent, filterPlaylist;
    private View bottomBar, btnClearRecent, btnNewPlaylist;
    private TextView tvAudioCount;

    private View miniPlayer;
    private TextView miniTitle;
    private ProgressBar miniProgress;
    private ImageView miniPlay;

    private View contentView;
    private View dimOverlay;
    private ValueAnimator blurAnim;
    private float sheetBlur = 0f;

    // Swipe to dismiss mini player
    private float downX = 0;
    private boolean isSwiping = false;

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

        contentView = findViewById(R.id.audio_content);
        dimOverlay = findViewById(R.id.dim_overlay);

        bottomBar = findViewById(R.id.bottom_bar);
        tvAudioCount = findViewById(R.id.tv_audio_count);
        btnClearRecent = findViewById(R.id.btn_clear_recent);
        btnNewPlaylist = findViewById(R.id.btn_new_playlist);

        filterAll = findViewById(R.id.filter_all);
        filterFav = findViewById(R.id.filter_fav);
        filterDownload = findViewById(R.id.filter_download);
        filterRecent = findViewById(R.id.filter_recent);
        filterPlaylist = findViewById(R.id.filter_playlist);

        filterAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        filterFav.setOnClickListener(v -> setFilter(FILTER_FAV));
        filterDownload.setOnClickListener(v -> setFilter(FILTER_DOWNLOADS));
        filterRecent.setOnClickListener(v -> setFilter(FILTER_RECENT));
        filterPlaylist.setOnClickListener(v -> setFilter(FILTER_PLAYLISTS));

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

        btnNewPlaylist.setOnClickListener(v -> showCreatePlaylistDialog(null));

        setupMiniPlayer();
        setupNetworkMonitor();
        updateFilterIcons();
        updateBottomBar();

        loadAudios(false);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    // ═══════════════════════════════════════════════════════════
    // FILTERS (5 tabs)
    // ═══════════════════════════════════════════════════════════

    private void setFilter(int filter) {
        currentFilter = (currentFilter == filter) ? FILTER_ALL : filter;
        selectedPlaylist = null;
        updateFilterIcons();
        refreshDisplayedList();
    }

    private void updateFilterIcons() {
        styleFilterIcon(filterAll, currentFilter == FILTER_ALL, false, R.drawable.ic_all);
        styleFilterIcon(filterFav, currentFilter == FILTER_FAV, true, R.drawable.ic_heart_outline);
        styleFilterIcon(filterDownload, currentFilter == FILTER_DOWNLOADS, false, R.drawable.ic_download);
        styleFilterIcon(filterRecent, currentFilter == FILTER_RECENT, false, R.drawable.ic_history);
        styleFilterIcon(filterPlaylist, currentFilter == FILTER_PLAYLISTS, false, R.drawable.ic_playlist);
    }

    private void styleFilterIcon(ImageView v, boolean selected, boolean isFav, int defaultIcon) {
        if (selected) {
            // Changed from bg_icon_selected to bg_tab_selected for a rounded rectangle look
            v.setBackgroundResource(R.drawable.bg_tab_selected);
            if (isFav) {
                v.setImageResource(R.drawable.ic_heart_filled);
                v.setColorFilter(getColor(R.color.red));
            } else {
                v.setColorFilter(getColor(R.color.accent));
            }
        } else {
            v.setBackgroundResource(android.R.color.transparent);
            if (isFav) v.setImageResource(R.drawable.ic_heart_outline);
            else v.setImageResource(defaultIcon);
            v.setColorFilter(getColor(R.color.text_secondary));
        }
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
                    if (showSpinner) Toast.makeText(AudioListActivity.this, message, Toast.LENGTH_SHORT).show();
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
                    if (fromNetwork) Toast.makeText(AudioListActivity.this, "Audio list updated", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {}

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
                        if (!isFinishing() && !isSyncing) syncFromNetwork();
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
    // LISTS
    // ═══════════════════════════════════════════════════════════

    private void refreshDisplayedList() {
        if (currentFilter == FILTER_PLAYLISTS && selectedPlaylist == null) {
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

        if (currentFilter == FILTER_FAV) {
            for (AudioTrack t : allTracks) if (t.isFavourite) displayedTracks.add(t);
        } else if (currentFilter == FILTER_DOWNLOADS) {
            for (AudioTrack t : allTracks) if (t.isDownloaded) displayedTracks.add(t);
        } else if (currentFilter == FILTER_RECENT) {
            List<String> recentIds = Helper.getAudioRecentIds(this);
            if (recentIds.size() > 10) recentIds = recentIds.subList(0, 10);
            for (String id : recentIds) {
                for (AudioTrack t : allTracks) {
                    if (t.id.equals(id)) { displayedTracks.add(t); break; }
                }
            }
        } else if (currentFilter == FILTER_PLAYLISTS && selectedPlaylist != null) {
            Map<String, List<String>> playlists = Helper.getPlaylists(this);
            List<String> ids = playlists.get(selectedPlaylist);
            if (ids == null) {
                selectedPlaylist = null;
                refreshDisplayedList();
                return;
            }
            for (String id : ids) {
                for (AudioTrack t : allTracks) {
                    if (t.id.equals(id)) { displayedTracks.add(t); break; }
                }
            }
        } else {
            displayedTracks.addAll(allTracks);
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

    private void updateBottomBar() {
        boolean recentView = currentFilter == FILTER_RECENT;
        boolean playlistView = currentFilter == FILTER_PLAYLISTS && selectedPlaylist == null;
        boolean downloadView = currentFilter == FILTER_DOWNLOADS;
        boolean show = recentView || playlistView || downloadView;

        bottomBar.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            if (playlistView) {
                int count = Helper.getPlaylists(this).size();
                tvAudioCount.setText(count + (count == 1 ? " playlist" : " playlists"));
            } else {
                tvAudioCount.setText(displayedTracks.size() + (displayedTracks.size() == 1 ? " track" : " tracks"));
            }
        }

        btnClearRecent.setVisibility(recentView ? View.VISIBLE : View.GONE);
        btnNewPlaylist.setVisibility(playlistView ? View.VISIBLE : View.GONE);

        updateMiniMargin();
    }

    // ═══════════════════════════════════════════════════════════
    // FLOATING MINI PLAYER (Swipe + Stop + Persistence)
    // ═══════════════════════════════════════════════════════════

    private void setupMiniPlayer() {
        miniPlayer = findViewById(R.id.mini_player);
        miniTitle = findViewById(R.id.mini_title);
        miniProgress = findViewById(R.id.mini_progress);
        miniPlay = findViewById(R.id.mini_play);

        ImageView miniPrev = findViewById(R.id.mini_prev);
        ImageView miniNext = findViewById(R.id.mini_next);
        ImageView miniStop = findViewById(R.id.mini_stop);

        // Handle both swipe and tap in one place to prevent conflicts
        miniPlayer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    isSwiping = false;
                    return true; // Consume DOWN to block OnClickListener from firing later

                case MotionEvent.ACTION_MOVE:
                    float moveDx = event.getRawX() - downX;
                    if (Math.abs(moveDx) > 10) {
                        isSwiping = true;
                        miniPlayer.setTranslationX(moveDx);
                        miniPlayer.setAlpha(1 - Math.min(1, Math.abs(moveDx) / 300f));
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwiping) {
                        float upDx = event.getRawX() - downX;
                        if (Math.abs(upDx) > 150) {
                            stopAndHideMiniPlayer(upDx > 0 ? 500 : -500);
                        } else {
                            miniPlayer.animate().translationX(0).alpha(1).setDuration(200).start();
                        }
                    } else {
                        // It was a clean tap, not a swipe
                        openPlayer();
                    }
                    downX = 0;
                    isSwiping = false;
                    return true;
            }
            return true;
        });

        miniPlay.setOnClickListener(v -> PlaybackManager.getInstance().playPause());
        miniPrev.setOnClickListener(v -> PlaybackManager.getInstance().prev());
        miniNext.setOnClickListener(v -> PlaybackManager.getInstance().next());
        miniStop.setOnClickListener(v -> stopAndHideMiniPlayer(0));
    }

    private void updateMiniPlayer(boolean isPlaying, String title, String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) {
            if (miniPlayer.getVisibility() != View.GONE) {
                miniPlayer.setVisibility(View.GONE);
            }
            return;
        }

        if (miniPlayer.getVisibility() != View.VISIBLE) {
            miniPlayer.setVisibility(View.VISIBLE);
            miniPlayer.setTranslationX(0);
            miniPlayer.setAlpha(1);
        }

        miniTitle.setText(title != null && !title.isEmpty() ? title : "Bhajan Audio");
        miniPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        updateMiniMargin();
    }

    private void stopAndHideMiniPlayer(float endX) {
        miniPlayer.animate().translationX(endX).alpha(0).setDuration(200).withEndAction(() -> {
            PlaybackManager.getInstance().stopAndClear();
            miniPlayer.setTranslationX(0);
            miniPlayer.setAlpha(1);
            miniPlayer.setVisibility(View.GONE);
        }).start();
    }

    private void updateMiniMargin() {
        if (miniPlayer == null || bottomBar == null) return;
        float density = getResources().getDisplayMetrics().density;
        boolean barVisible = bottomBar.getVisibility() == View.VISIBLE;
        int bottomMargin = (int) ((barVisible ? 70 : 14) * density);
        int sideMargin = (int) (14 * density);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) miniPlayer.getLayoutParams();
        lp.bottomMargin = bottomMargin;
        lp.leftMargin = sideMargin;
        lp.rightMargin = sideMargin;
        miniPlayer.setLayoutParams(lp);
    }

    private void openPlayer() {
        animateSheetBlur(1f);
        startActivity(new Intent(this, MusicPlayerActivity.class));
    }

    // ═══════════════════════════════════════════════════════════
    // TRACK OPTIONS BOTTOM SHEET
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
        sheetDuration.setText("Duration: " + (track.durationSec > 0 ? Helper.formatTime(track.durationSec * 1000L) : "--:--"));
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
            if (isCurrent) pm.playPause();
            else {
                int pos = displayedTracks.indexOf(track);
                if (pos >= 0) { pm.connect(this); pm.playList(displayedTracks, pos); }
            }
        });

        if (track.isFavourite) {
            sheetFav.setText("Remove from Favourites");
            sheetFav.setTextColor(getColor(R.color.red));
        } else {
            sheetFav.setText("Add to Favourites");
            sheetFav.setTextColor(getColor(R.color.text_primary));
        }
        sheetFav.setOnClickListener(x -> { dialog.dismiss(); toggleFavourite(track); });

        sheetPlaylist.setOnClickListener(x -> { dialog.dismiss(); showAddToPlaylistDialog(track); });

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
        sheetDownload.setOnClickListener(x -> { dialog.dismiss(); handleDownloadAction(track); });

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

    private void toggleFavourite(AudioTrack track) {
        if (track.isFavourite) {
            Helper.showConfirm(this, "Remove from Favourites", "Remove \"" + track.title + "\" from favourites?", "Remove", true, () -> {
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
            Helper.showConfirm(this, "Delete Download", "Delete \"" + track.title + "\" from your device?", "Delete", true, () -> {
                PlaybackManager pm = PlaybackManager.getInstance();
                if (track.id.equals(pm.getCurrentMediaId())) pm.stopAndClear();
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
                    if (currentFilter == FILTER_PLAYLISTS && selectedPlaylist == null) refreshPlaylists();
                    updateBottomBar();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddToPlaylistDialog(AudioTrack track) {
        if (track == null) return;
        Map<String, List<String>> playlists = Helper.getPlaylists(this);
        List<String> names = new ArrayList<>(playlists.keySet());
        if (names.isEmpty()) { showCreatePlaylistDialog(track); return; }
        names.add("+ Create new playlist");

        new AlertDialog.Builder(this)
                .setTitle("Add to Playlist")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == names.size() - 1) showCreatePlaylistDialog(track);
                    else {
                        Helper.addToPlaylist(this, names.get(which), track.id);
                        Toast.makeText(this, "Added to \"" + names.get(which) + "\"", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onPlaylistClick(String name) {
        selectedPlaylist = name;
        refreshDisplayedList();
    }

    @Override
    public void onPlaylistDelete(String name) {
        Helper.showConfirm(this, "Delete Playlist", "Delete playlist \"" + name + "\"?", "Delete", true, () -> {
            Helper.deletePlaylist(this, name);
            refreshPlaylists();
            updateBottomBar();
            Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRowClick(AudioTrack track, int position) {
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);
        if (track.id != null && track.id.equals(pm.getCurrentMediaId())) {
            openPlayer();
            return;
        }
        pm.playList(displayedTracks, position);
        openPlayer();
    }

    @Override
    public void onPlayPauseClick(AudioTrack track, int position) {
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);
        if (track.id != null && track.id.equals(pm.getCurrentMediaId())) pm.playPause();
        else pm.playList(displayedTracks, position);
    }

    @Override
    public void onOptionsClick(AudioTrack track, int position) {
        showTrackOptions(track);
    }

    // ═══════════════════════════════════════════════════════════
    // PLAYER SHEET BLUR
    // ═══════════════════════════════════════════════════════════

    private void applySheetBlur(float p) {
        p = Math.max(0f, Math.min(1f, p));
        sheetBlur = p;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (p <= 0.02f) contentView.setRenderEffect(null);
            else {
                float radius = Math.min(90f, 35f * p * getResources().getDisplayMetrics().density);
                contentView.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            }
        }
        dimOverlay.setAlpha(0.45f * p);
    }

    private void animateSheetBlur(float target) {
        if (blurAnim != null) blurAnim.cancel();
        blurAnim = ValueAnimator.ofFloat(sheetBlur, target);
        blurAnim.setDuration(260);
        blurAnim.addUpdateListener(a -> applySheetBlur((float) a.getAnimatedValue()));
        blurAnim.start();
    }

    @Override
    public void onSheetOpenProgress(float openProgress) {
        if (blurAnim != null) blurAnim.cancel();
        applySheetBlur(openProgress);
    }

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE & SMART BACK BUTTON
    // ═══════════════════════════════════════════════════════════

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SheetBus.setListener(this);
        if (MusicPlayerActivity.isSheetOpen) applySheetBlur(1f);

        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);
        // addListener automatically triggers onStateChanged with current state,
        // ensuring the mini player persists when returning from MainActivity
        pm.addListener(playbackListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDisplayedList();

        PlaybackManager pm = PlaybackManager.getInstance();
        String currentId = pm.getCurrentMediaId();
        if (currentId != null && !currentId.isEmpty() && miniPlayer.getVisibility() == View.GONE) {
            updateMiniPlayer(true, "Bhajan Audio", currentId);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SheetBus.setListener(null);
        PlaybackManager.getInstance().removeListener(playbackListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        // Smart back button: step out of playlist view first
        if (selectedPlaylist != null) {
            selectedPlaylist = null;
            currentFilter = FILTER_PLAYLISTS;
            updateFilterIcons();
            refreshDisplayedList();
            return;
        }

        // Smart back button: return to "All" tab first, then exit
        if (currentFilter != FILTER_ALL) {
            currentFilter = FILTER_ALL;
            updateFilterIcons();
            refreshDisplayedList();
            return;
        }

        super.onBackPressed();
    }
}