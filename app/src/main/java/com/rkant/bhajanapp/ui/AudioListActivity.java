
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
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import com.rkant.bhajanapp.adapter.BhajanCategoryAdapter;
import com.rkant.bhajanapp.adapter.PlaylistAdapter;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.utils.AudioDatabase;
import com.rkant.bhajanapp.utils.AudioRepository;
import com.rkant.bhajanapp.utils.BatteryHelper;
import com.rkant.bhajanapp.utils.DownloadHelper;
import com.rkant.bhajanapp.utils.BottomNav;
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
    private static final int FILTER_ARCHIVE = 5;

    private static final float MINI_PLAYER_SWIPE_THRESHOLD = 350f;
    private static final float MINI_PLAYER_SWIPE_ALPHA_RANGE = 600f;

    private RecyclerView recyclerView;
    private AudioAdapter audioAdapter;
    private PlaylistAdapter playlistAdapter;
    private BhajanCategoryAdapter audioCategoryAdapter;
    private final List<AudioTrack> allTracks = new ArrayList<>();
    private final List<AudioTrack> displayedTracks = new ArrayList<>();

    private SwipeRefreshLayout swipeRefresh;
    private int currentFilter = FILTER_ALL;
    private String selectedPlaylist = null;

    private boolean showSubCollectionList = false;
    private String selectedSubCollection = null;

    private boolean isSyncing = false;
    private boolean currentlyPlaying = false;

    private TextView filterAll, filterFav, filterDownload, filterRecent, filterPlaylist, filterArchive;
    private String audioQuery = "";

    private TextView tvAudioActiveView;
    private View fabAdd;

    private View miniPlayer;
    private TextView miniTitle;
    private ProgressBar miniProgress;
    private ImageView miniPlay;

    private View contentView;
    private View dimOverlay;
    private ValueAnimator blurAnim;
    private float sheetBlur = 0f;
    private float downX = 0;
    private boolean isSwiping = false;

    private View audioTitleGroup, audioSearchInline;
    private EditText etAudioSearch;
    private boolean searchOpen = false;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasOffline = false;

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            currentlyPlaying = isPlaying;
            updateMiniPlayer(isPlaying, title, mediaId);
            if (audioAdapter != null) audioAdapter.setPlaybackState(mediaId, isPlaying);
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
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_list);
        BottomNav.setup(this, BottomNav.AUDIO);
        requestNotificationPermission();
        BatteryHelper.showGuideIfNeeded(this);

        recyclerView = findViewById(R.id.rv_audio_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        audioAdapter = new AudioAdapter(displayedTracks, this, this);
        playlistAdapter = new PlaylistAdapter(this, this);

        audioCategoryAdapter = new BhajanCategoryAdapter(this, new BhajanCategoryAdapter.OnCategoryAction() {
            @Override
            public void onCategoryClick(String name) {
                showSubCollectionList = false;
                selectedSubCollection = BhajanCategoryAdapter.ALL_ITEM.equals(name) ? null : name;
                refreshDisplayedList();
            }

            @Override
            public void onCategoryDelete(String name) {
                final boolean fav = currentFilter == FILTER_FAV;
                Helper.showConfirm(AudioListActivity.this, "Delete Collection",
                        "Delete collection \u201C" + name + "\u201D? The tracks inside it will not be deleted.",
                        "Delete", true, () -> {
                            if (fav) Helper.deleteAudioFavCategory(AudioListActivity.this, name);
                            else Helper.deleteAudioArchiveCategory(AudioListActivity.this, name);
                            Toast.makeText(AudioListActivity.this, "Collection deleted", Toast.LENGTH_SHORT).show();
                            refreshDisplayedList();
                        });
            }
        });
        audioCategoryAdapter.setNoun("track", "tracks");

        recyclerView.setAdapter(audioAdapter);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> loadAudios(true));

        contentView = findViewById(R.id.audio_content);
        dimOverlay = findViewById(R.id.dim_overlay);

        tvAudioActiveView = findViewById(R.id.tv_audio_active_view);
        fabAdd = findViewById(R.id.fab_audio_add);

        filterAll = findViewById(R.id.filter_all);
        filterFav = findViewById(R.id.filter_fav);
        filterDownload = findViewById(R.id.filter_download);
        filterRecent = findViewById(R.id.filter_recent);
        filterPlaylist = findViewById(R.id.filter_playlist);
        filterArchive = findViewById(R.id.filter_archive);

        filterAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        filterFav.setOnClickListener(v -> setFilter(FILTER_FAV));
        filterDownload.setOnClickListener(v -> setFilter(FILTER_DOWNLOADS));
        filterRecent.setOnClickListener(v -> setFilter(FILTER_RECENT));
        filterPlaylist.setOnClickListener(v -> setFilter(FILTER_PLAYLISTS));
        filterArchive.setOnClickListener(v -> setFilter(FILTER_ARCHIVE));

        audioTitleGroup = findViewById(R.id.audio_title_group);
        audioSearchInline = findViewById(R.id.audio_search_inline);
        etAudioSearch = findViewById(R.id.et_audio_search);
        View btnAudioSearchClear = findViewById(R.id.btn_audio_search_clear);

        findViewById(R.id.btn_audio_search_toggle).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.btn_audio_overflow).setOnClickListener(v -> showOverflowMenu());
        fabAdd.setOnClickListener(v -> handleFabClick());

        etAudioSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                audioQuery = s.toString().trim().toLowerCase();
                btnAudioSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                refreshDisplayedList();
            }
            public void afterTextChanged(Editable s) {}
        });
        etAudioSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                etAudioSearch.clearFocus();
                return true;
            }
            return false;
        });
        btnAudioSearchClear.setOnClickListener(v -> {
            etAudioSearch.setText("");
            etAudioSearch.clearFocus();
            hideKeyboard();
        });

        setupMiniPlayer();
        setupNetworkMonitor();
        updateFilterIcons();
        updateFabVisibility();
        loadAudios(false);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    private void setFilter(int filter) {
        currentFilter = filter;
        selectedPlaylist = null;
        selectedSubCollection = null;
        showSubCollectionList = (filter == FILTER_FAV || filter == FILTER_ARCHIVE);
        updateFilterIcons();
        refreshDisplayedList();
    }

    private void updateFilterIcons() {
        styleFilterIcon(filterAll, currentFilter == FILTER_ALL, false, R.drawable.ic_all);
        styleFilterIcon(filterFav, currentFilter == FILTER_FAV, true, R.drawable.ic_heart_outline);
        styleFilterIcon(filterDownload, currentFilter == FILTER_DOWNLOADS, false, R.drawable.ic_download);
        styleFilterIcon(filterRecent, currentFilter == FILTER_RECENT, false, R.drawable.ic_history);
        styleFilterIcon(filterPlaylist, currentFilter == FILTER_PLAYLISTS, false, R.drawable.ic_playlist);
        styleFilterIcon(filterArchive, currentFilter == FILTER_ARCHIVE, false, R.drawable.ic_archive);
    }

    private void styleFilterIcon(TextView v, boolean selected, boolean isFav, int defaultIcon) {
        int icon = (selected && isFav) ? R.drawable.ic_heart_filled : defaultIcon;
        int color = getColor(selected ? R.color.on_accent : R.color.text_secondary);
        v.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_pill);
        v.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        v.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        v.setTextColor(color);
    }

    // ═══════════════════════════════════════════════════════════
    // SEARCH TOGGLE
    // ═══════════════════════════════════════════════════════════

    private void toggleSearch() {
        if (searchOpen) closeSearch();
        else openSearch();
    }

    private void openSearch() {
        searchOpen = true;
        audioTitleGroup.animate()
                .translationX(-audioTitleGroup.getWidth())
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> audioTitleGroup.setVisibility(View.INVISIBLE))
                .start();
        audioSearchInline.setVisibility(View.VISIBLE);
        audioSearchInline.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(200)
                .start();
        etAudioSearch.requestFocus();
        etAudioSearch.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etAudioSearch, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void closeSearch() {
        searchOpen = false;
        etAudioSearch.setText("");
        etAudioSearch.clearFocus();
        hideKeyboard();
        audioSearchInline.animate()
                .translationX(200)
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> audioSearchInline.setVisibility(View.INVISIBLE))
                .start();
        audioTitleGroup.setVisibility(View.VISIBLE);
        audioTitleGroup.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(200)
                .start();
        refreshDisplayedList();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etAudioSearch.getWindowToken(), 0);
    }

    // ═══════════════════════════════════════════════════════════
    // DATA LOADING
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

            @Override public void onError(String message) {}

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
            public void onLost(@NonNull Network network) { wasOffline = true; }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try { connectivityManager.registerNetworkCallback(request, networkCallback); }
        catch (Exception e) { e.printStackTrace(); }
        wasOffline = !AudioRepository.isNetworkAvailable(this);
    }

    // ═══════════════════════════════════════════════════════════
    // REFRESH
    // ═══════════════════════════════════════════════════════════

    private void refreshDisplayedList() {
        updateActiveViewLabel();
        updateFabVisibility();

        if (currentFilter == FILTER_PLAYLISTS && selectedPlaylist == null) {
            if (recyclerView.getAdapter() != playlistAdapter) recyclerView.setAdapter(playlistAdapter);
            refreshPlaylists();
            return;
        }
        if ((currentFilter == FILTER_FAV || currentFilter == FILTER_ARCHIVE) && showSubCollectionList) {
            showAudioCollectionList(currentFilter == FILTER_FAV);
            return;
        }

        if (recyclerView.getAdapter() != audioAdapter) recyclerView.setAdapter(audioAdapter);
        displayedTracks.clear();

        boolean searching = !audioQuery.isEmpty();
        boolean includeArchived = Helper.isAudioArchivedInListEnabled(this)
                || (searching && Helper.isAudioArchivedSearchable(this));
        int recentLimit = Helper.getAudioRecentLimit(this);

        if (currentFilter == FILTER_ARCHIVE) {
            if (selectedSubCollection == null) {
                for (AudioTrack t : allTracks) if (Helper.isAudioArchived(this, t.id)) displayedTracks.add(t);
            } else {
                List<String> ids = Helper.getAudioArchiveCategories(this).get(selectedSubCollection);
                if (ids == null) { selectedSubCollection = null; showSubCollectionList = true; refreshDisplayedList(); return; }
                for (String id : ids) {
                    for (AudioTrack t : allTracks) {
                        if (t.id.equals(id) && Helper.isAudioArchived(this, t.id)) { displayedTracks.add(t); break; }
                    }
                }
            }
        } else if (currentFilter == FILTER_FAV) {
            if (selectedSubCollection == null) {
                for (AudioTrack t : allTracks) if (t.isFavourite) displayedTracks.add(t);
            } else {
                List<String> ids = Helper.getAudioFavCategories(this).get(selectedSubCollection);
                if (ids == null) { selectedSubCollection = null; showSubCollectionList = true; refreshDisplayedList(); return; }
                for (String id : ids) {
                    for (AudioTrack t : allTracks) {
                        if (t.id.equals(id) && t.isFavourite) { displayedTracks.add(t); break; }
                    }
                }
            }
        } else if (currentFilter == FILTER_DOWNLOADS) {
            for (AudioTrack t : allTracks) if (t.isDownloaded) displayedTracks.add(t);
        } else if (currentFilter == FILTER_RECENT) {
            List<String> recentIds = Helper.getAudioRecentIds(this);
            if (recentIds.size() > recentLimit) recentIds = recentIds.subList(0, recentLimit);
            for (String id : recentIds) {
                for (AudioTrack t : allTracks) {
                    if (t.id.equals(id)) { displayedTracks.add(t); break; }
                }
            }
        } else if (currentFilter == FILTER_PLAYLISTS && selectedPlaylist != null) {
            Map<String, List<String>> playlists = Helper.getPlaylists(this);
            List<String> ids = playlists.get(selectedPlaylist);
            if (ids == null) { selectedPlaylist = null; refreshDisplayedList(); return; }
            for (String id : ids) {
                for (AudioTrack t : allTracks) {
                    if (t.id.equals(id)) { displayedTracks.add(t); break; }
                }
            }
        } else {
            displayedTracks.addAll(allTracks);
        }

        if (currentFilter == FILTER_ALL && !includeArchived) {
            java.util.Iterator<AudioTrack> it = displayedTracks.iterator();
            while (it.hasNext()) {
                AudioTrack t = it.next();
                if (Helper.isAudioArchived(this, t.id)) it.remove();
            }
        }

        if (searching) {
            java.util.Iterator<AudioTrack> it = displayedTracks.iterator();
            while (it.hasNext()) {
                AudioTrack t = it.next();
                if (t.title == null || !t.title.toLowerCase().contains(audioQuery)) it.remove();
            }
        }

        audioAdapter.notifyDataSetChanged();
    }

    private void showAudioCollectionList(boolean fav) {
        if (recyclerView.getAdapter() != audioCategoryAdapter) recyclerView.setAdapter(audioCategoryAdapter);

        Map<String, List<String>> map = fav
                ? Helper.getAudioFavCategories(this)
                : Helper.getAudioArchiveCategories(this);
        String allName = fav ? "All Favourites" : "All Archived";
        int allCount = 0;
        for (AudioTrack t : allTracks) {
            if (fav ? t.isFavourite : Helper.isAudioArchived(this, t.id)) allCount++;
        }
        audioCategoryAdapter.setAllItem(allName, allCount);

        List<String> names = new ArrayList<>(map.keySet());
        Map<String, Integer> counts = new HashMap<>();
        for (String n : names) {
            List<String> ids = map.get(n);
            int c = 0;
            for (String id : ids) {
                if (fav) {
                    for (AudioTrack t : allTracks) {
                        if (t.id.equals(id) && t.isFavourite) { c++; break; }
                    }
                } else {
                    if (Helper.isAudioArchived(this, id)) c++;
                }
            }
            counts.put(n, c);
        }
        audioCategoryAdapter.update(names, counts);
    }

    private void refreshPlaylists() {
        Map<String, List<String>> playlists = Helper.getPlaylists(this);
        List<String> names = new ArrayList<>(playlists.keySet());
        Map<String, Integer> counts = new HashMap<>();
        for (String name : names) counts.put(name, playlists.get(name).size());
        playlistAdapter.update(names, counts);
    }

    private void updateActiveViewLabel() {
        String label;
        if (currentFilter == FILTER_FAV) label = "Favourites";
        else if (currentFilter == FILTER_DOWNLOADS) label = "Downloads";
        else if (currentFilter == FILTER_RECENT) label = "Recent";
        else if (currentFilter == FILTER_PLAYLISTS) label = selectedPlaylist != null ? selectedPlaylist : "Playlists";
        else if (currentFilter == FILTER_ARCHIVE) label = "Archived";
        else label = "All";
        if (selectedSubCollection != null) label = selectedSubCollection;
        tvAudioActiveView.setText(label);
    }

    // ═══════════════════════════════════════════════════════════
    // FAB
    // ═══════════════════════════════════════════════════════════

    private void updateFabVisibility() {
        boolean show = (currentFilter == FILTER_PLAYLISTS && selectedPlaylist == null)
                || (currentFilter == FILTER_FAV && showSubCollectionList)
                || (currentFilter == FILTER_ARCHIVE && showSubCollectionList);
        fabAdd.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void handleFabClick() {
        if (currentFilter == FILTER_FAV) showCreateAudioCollectionDialog(null, true);
        else if (currentFilter == FILTER_ARCHIVE) showCreateAudioCollectionDialog(null, false);
        else showCreatePlaylistDialog(null);
    }

    // ═══════════════════════════════════════════════════════════
    // OVERFLOW MENU
    // ═══════════════════════════════════════════════════════════

    private void showOverflowMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_audio_overflow, null);

        int allCount = allTracks.size();
        int favCount = 0, dlCount = 0;
        for (AudioTrack t : allTracks) {
            if (t.isFavourite) favCount++;
            if (t.isDownloaded) dlCount++;
        }
        int recentCount = Math.min(Helper.getAudioRecentIds(this).size(), Helper.getAudioRecentLimit(this));
        int playlistsCount = Helper.getPlaylists(this).size();
        int archivedCount = Helper.audioArchivedCount(this);

        ((TextView) v.findViewById(R.id.count_a_all)).setText(allCount + "");
        ((TextView) v.findViewById(R.id.count_a_fav)).setText(favCount + "");
        ((TextView) v.findViewById(R.id.count_a_download)).setText(dlCount + "");
        ((TextView) v.findViewById(R.id.count_a_recent)).setText(recentCount + "");
        ((TextView) v.findViewById(R.id.count_a_playlist)).setText(playlistsCount + "");
        ((TextView) v.findViewById(R.id.count_a_archived)).setText(archivedCount + "");

        v.findViewById(R.id.check_a_all).setVisibility(currentFilter == FILTER_ALL ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_a_fav).setVisibility(currentFilter == FILTER_FAV ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_a_download).setVisibility(currentFilter == FILTER_DOWNLOADS ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_a_recent).setVisibility(currentFilter == FILTER_RECENT ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_a_playlist).setVisibility(currentFilter == FILTER_PLAYLISTS ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_a_archived).setVisibility(currentFilter == FILTER_ARCHIVE ? View.VISIBLE : View.GONE);

        v.findViewById(R.id.menu_a_all).setOnClickListener(x -> { dialog.dismiss(); setFilter(FILTER_ALL); });
        v.findViewById(R.id.menu_a_fav).setOnClickListener(x -> { dialog.dismiss(); setFilter(FILTER_FAV); });
        v.findViewById(R.id.menu_a_download).setOnClickListener(x -> { dialog.dismiss(); setFilter(FILTER_DOWNLOADS); });
        v.findViewById(R.id.menu_a_recent).setOnClickListener(x -> { dialog.dismiss(); setFilter(FILTER_RECENT); });
        v.findViewById(R.id.menu_a_playlist).setOnClickListener(x -> { dialog.dismiss(); setFilter(FILTER_PLAYLISTS); });
        v.findViewById(R.id.menu_a_archived).setOnClickListener(x -> { dialog.dismiss(); setFilter(FILTER_ARCHIVE); });

        View manageToggle = v.findViewById(R.id.menu_a_manage_toggle);
        View manageOptions = v.findViewById(R.id.manage_options_container);
        TextView manageLabel = v.findViewById(R.id.tv_manage_label);
        ImageView manageChevron = v.findViewById(R.id.iv_manage_chevron);
        manageToggle.setOnClickListener(x -> {
            boolean isVisible = manageOptions.getVisibility() == View.VISIBLE;
            if (isVisible) {
                manageOptions.setVisibility(View.GONE);
                manageLabel.setText("Cleanup Tools");
                manageChevron.setRotation(0f);
            } else {
                manageOptions.setVisibility(View.VISIBLE);
                manageLabel.setText("Hide Options");
                manageChevron.setRotation(90f);
            }
        });

        v.findViewById(R.id.menu_a_clear_recent).setOnClickListener(x -> {
            dialog.dismiss();
            Helper.showConfirm(this, "Clear listening history", "Remove all recently played tracks?", "Clear", true, () -> {
                Helper.clearAudioRecents(this);
                refreshDisplayedList();
                Toast.makeText(this, "Listening history cleared", Toast.LENGTH_SHORT).show();
            });
        });
        v.findViewById(R.id.menu_a_clear_favs).setOnClickListener(x -> {
            dialog.dismiss();
            Helper.showConfirm(this, "Clear favourites", "Remove all tracks from your favourite list?", "Clear", true, () -> {
                for (AudioTrack t : allTracks) t.isFavourite = false;
                AudioDatabase.getInstance(this).clearAllFavourites();
                Helper.clearAudioFavCategoryContents(this);
                refreshDisplayedList();
                Toast.makeText(this, "Favourites cleared", Toast.LENGTH_SHORT).show();
            });
        });
        v.findViewById(R.id.menu_a_unarchive_all).setOnClickListener(x -> {
            dialog.dismiss();
            Helper.showConfirm(this, "Unarchive all", "Restore all archived tracks?", "Restore", true, () -> {
                Helper.clearAudioArchived(this);
                refreshDisplayedList();
                Toast.makeText(this, "All tracks unarchived", Toast.LENGTH_SHORT).show();
            });
        });

        v.findViewById(R.id.btn_close).setOnClickListener(x -> dialog.dismiss());
        dialog.setContentView(v);
        dialog.show();
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
        ImageView miniStop = findViewById(R.id.mini_stop);

        miniPlayer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    isSwiping = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveDx = event.getRawX() - downX;
                    if (Math.abs(moveDx) > 10) {
                        isSwiping = true;
                        miniPlayer.setTranslationX(moveDx);
                        miniPlayer.setAlpha(1 - Math.min(1, Math.abs(moveDx) / MINI_PLAYER_SWIPE_ALPHA_RANGE));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwiping) {
                        float upDx = event.getRawX() - downX;
                        if (Math.abs(upDx) > MINI_PLAYER_SWIPE_THRESHOLD) {
                            stopAndHideMiniPlayer(upDx > 0 ? 700 : -700);
                        } else {
                            miniPlayer.animate().translationX(0).alpha(1).setDuration(200).start();
                        }
                    } else {
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
            if (miniPlayer.getVisibility() != View.GONE) miniPlayer.setVisibility(View.GONE);
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
        if (miniPlayer == null) return;
        float density = getResources().getDisplayMetrics().density;
        int bottomMargin = (int) (80 * density);
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
    // TRACK OPTIONS
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
        TextView sheetArchive = v.findViewById(R.id.sheet_archive);
        TextView sheetPlaylist = v.findViewById(R.id.sheet_playlist);
        TextView sheetDownload = v.findViewById(R.id.sheet_download);
        TextView sheetRemoveRecent = v.findViewById(R.id.sheet_remove_recent);
        TextView sheetRemovePlaylist = v.findViewById(R.id.sheet_remove_playlist);
        View sheetClose = v.findViewById(R.id.sheet_close);

        sheetTitle.setText(track.title);
        sheetDuration.setText("Duration: " + (track.durationSec > 0 ? Helper.formatTime(track.durationSec * 1000L) : "--:--"));
        sheetSource.setText(track.isDownloaded ? "Saved on this device" : "Streams from online");

        if (track.isDownloaded) {
            sheetStatus.setText("Downloaded");
            sheetStatus.setTextColor(getColor(R.color.accent));
        } else if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
            sheetStatus.setText("Downloading\u2026");
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
            sheetFav.setTextColor(getColor(R.color.accent));
        } else {
            sheetFav.setText("Add to Favourites");
            sheetFav.setTextColor(getColor(R.color.text_primary));
        }
        sheetFav.setOnClickListener(x -> {
            dialog.dismiss();
            if (track.isFavourite) confirmRemoveFavourite(track);
            else showAddToAudioFavCollectionDialog(track);
        });

        boolean archived = Helper.isAudioArchived(this, track.id);
        if (archived) {
            sheetArchive.setText("Unarchive");
            sheetArchive.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.showConfirm(this, "Unarchive track",
                        "Restore \u201C" + track.title + "\u201D back to the main list?",
                        "Unarchive", false, () -> {
                            Helper.unAudioArchive(this, track.id);
                            Toast.makeText(this, "Track unarchived", Toast.LENGTH_SHORT).show();
                            if (currentFilter == FILTER_ARCHIVE && !showSubCollectionList) removeTrackAnimated(track);
                            else refreshDisplayedList();
                        });
            });
        } else {
            sheetArchive.setText("Archive");
            sheetArchive.setOnClickListener(x -> { dialog.dismiss(); showAddToAudioArchiveCollectionDialog(track); });
        }

        sheetPlaylist.setOnClickListener(x -> { dialog.dismiss(); showAddToPlaylistDialog(track); });

        if (track.isDownloaded) {
            sheetDownload.setText("Delete Download");
            sheetDownload.setTextColor(getColor(R.color.accent));
        } else if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
            sheetDownload.setText("Downloading\u2026");
            sheetDownload.setTextColor(getColor(R.color.text_secondary));
        } else {
            sheetDownload.setText("Download");
            sheetDownload.setTextColor(getColor(R.color.text_primary));
        }
        sheetDownload.setOnClickListener(x -> { dialog.dismiss(); handleDownloadAction(track); });

        if (Helper.getAudioRecentIds(this).contains(track.id)) {
            sheetRemoveRecent.setVisibility(View.VISIBLE);
            sheetRemoveRecent.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeAudioRecent(this, track.id);
                Toast.makeText(this, "Removed from recent", Toast.LENGTH_SHORT).show();
                if (currentFilter == FILTER_RECENT) removeTrackAnimated(track);
                else refreshDisplayedList();
            });
        }

        if (selectedPlaylist != null) {
            sheetRemovePlaylist.setVisibility(View.VISIBLE);
            sheetRemovePlaylist.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeFromPlaylist(this, selectedPlaylist, track.id);
                Toast.makeText(this, "Removed from playlist", Toast.LENGTH_SHORT).show();
                removeTrackAnimated(track);
            });
        }

        sheetClose.setOnClickListener(x -> dialog.dismiss());
        dialog.setContentView(v);
        dialog.show();
    }

    private void removeTrackAnimated(AudioTrack track) {
        int pos = audioAdapter.positionOf(track);
        if (pos >= 0) {
            audioAdapter.removeItemAt(pos);
        } else {
            refreshDisplayedList();
        }
    }

    private void confirmRemoveFavourite(AudioTrack track) {
        Helper.showConfirm(this, "Remove from Favourites",
                "Remove \u201C" + track.title + "\u201D from your favourite list?",
                "Remove", true, () -> {
                    track.isFavourite = false;
                    AudioDatabase.getInstance(this).setFavourite(track.id, false);
                    Helper.removeAudioFavFromAllCategories(this, track.id);
                    Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                    if (currentFilter == FILTER_FAV && !showSubCollectionList) removeTrackAnimated(track);
                    else refreshDisplayedList();
                });
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
                Toast.makeText(this, "Already downloading\u2026", Toast.LENGTH_SHORT).show();
                return;
            }
            track.downloadState = AudioTrack.STATE_DOWNLOADING;
            refreshDisplayedList();
            DownloadHelper.enqueue(this, track);
            Toast.makeText(this, "Downloading\u2026", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DIALOGS
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
                    if (currentFilter == FILTER_PLAYLISTS && selectedPlaylist == null) refreshPlaylists();
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

    private void showCreateAudioCollectionDialog(AudioTrack trackToAdd, boolean fav) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_category_name, null);
        EditText etName = view.findViewById(R.id.et_category_name);
        TextView tvDialogTitle = view.findViewById(R.id.tv_dialog_title);
        String title = fav ? "New Favourite Collection" : "New Archive Collection";
        tvDialogTitle.setText(title);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = Helper.sanitizeCategoryName(etName.getText().toString());
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (fav) {
                        Helper.createAudioFavCategory(this, name);
                        if (trackToAdd != null) {
                            Helper.addToAudioFavCategory(this, name, trackToAdd.id);
                            trackToAdd.isFavourite = true;
                            Toast.makeText(this, "Added to favourites \u00B7 \u201C" + name + "\u201D", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show();
                    } else {
                        Helper.createAudioArchiveCategory(this, name);
                        if (trackToAdd != null) {
                            Helper.addToAudioArchiveCategory(this, name, trackToAdd.id);
                            Toast.makeText(this, "Archived \u00B7 \u201C" + name + "\u201D", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show();
                    }
                    refreshDisplayedList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddToAudioFavCollectionDialog(AudioTrack track) {
        if (track == null) return;
        Map<String, List<String>> favCats = Helper.getAudioFavCategories(this);
        List<String> names = new ArrayList<>();
        names.add("All Favourites (default)");
        names.addAll(favCats.keySet());
        names.add("+ Create new collection");
        new AlertDialog.Builder(this)
                .setTitle("Add to Favourites")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        if (!track.isFavourite) {
                            track.isFavourite = true;
                            AudioDatabase.getInstance(this).setFavourite(track.id, true);
                        }
                        Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show();
                        refreshDisplayedList();
                    } else if (which == names.size() - 1) {
                        showCreateAudioCollectionDialog(track, true);
                    } else {
                        Helper.addToAudioFavCategory(this, names.get(which), track.id);
                        track.isFavourite = true;
                        Toast.makeText(this, "Added to favourites \u00B7 \u201C" + names.get(which) + "\u201D",
                                Toast.LENGTH_SHORT).show();
                        refreshDisplayedList();
                    }
                })
                .show();
    }

    private void showAddToAudioArchiveCollectionDialog(AudioTrack track) {
        if (track == null) return;
        Map<String, List<String>> archiveCats = Helper.getAudioArchiveCategories(this);
        List<String> names = new ArrayList<>();
        names.add("All Archived (default)");
        names.addAll(archiveCats.keySet());
        names.add("+ Create new collection");
        new AlertDialog.Builder(this)
                .setTitle("Archive Track")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        Helper.ensureAudioArchived(this, track.id);
                        Toast.makeText(this, "Track archived", Toast.LENGTH_SHORT).show();
                        refreshDisplayedList();
                    } else if (which == names.size() - 1) {
                        showCreateAudioCollectionDialog(track, false);
                    } else {
                        Helper.addToAudioArchiveCategory(this, names.get(which), track.id);
                        Toast.makeText(this, "Archived \u00B7 \u201C" + names.get(which) + "\u201D",
                                Toast.LENGTH_SHORT).show();
                        refreshDisplayedList();
                    }
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════
    // ADAPTER CALLBACKS
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onPlaylistClick(String name) { selectedPlaylist = name; refreshDisplayedList(); }

    @Override
    public void onPlaylistDelete(String name) {
        Helper.showConfirm(this, "Delete Playlist", "Delete playlist \"" + name + "\"?", "Delete", true, () -> {
            Helper.deletePlaylist(this, name);
            refreshPlaylists();
            Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRowClick(AudioTrack track, int position) {
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);
        if (track.id != null && track.id.equals(pm.getCurrentMediaId())) { openPlayer(); return; }
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
    public void onOptionsClick(AudioTrack track, int position) { showTrackOptions(track); }

    // ═══════════════════════════════════════════════════════════
    // SHEET BLUR
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

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onStart() {
        super.onStart();
        SheetBus.setListener(this);
        if (MusicPlayerActivity.isSheetOpen) applySheetBlur(1f);
        PlaybackManager pm = PlaybackManager.getInstance();
        pm.connect(this);
        pm.addListener(playbackListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDisplayedList();
        PlaybackManager pm = PlaybackManager.getInstance();
        String currentId = pm.getCurrentMediaId();
        if (currentId != null && !currentId.isEmpty() && miniPlayer.getVisibility() == View.GONE) {
            updateMiniPlayer(pm.isPlaying(), "Bhajan Audio", currentId);
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
        if (searchOpen) { closeSearch(); return; }
        if (selectedPlaylist != null) {
            selectedPlaylist = null;
            currentFilter = FILTER_PLAYLISTS;
            updateFilterIcons();
            refreshDisplayedList();
            return;
        }
        if ((currentFilter == FILTER_FAV || currentFilter == FILTER_ARCHIVE) && !showSubCollectionList) {
            showSubCollectionList = true;
            selectedSubCollection = null;
            refreshDisplayedList();
            return;
        }
        if (currentFilter != FILTER_ALL) {
            setFilter(FILTER_ALL);
            return;
        }
        super.onBackPressed();
    }
}