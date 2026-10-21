package com.rkant.bhajanapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.BhajanAdapter;
import com.rkant.bhajanapp.adapter.BhajanCategoryAdapter;
import com.rkant.bhajanapp.adapter.CategoryAdapter;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.utils.BhajanCategories;
import com.rkant.bhajanapp.utils.BhajanRepo;
import com.rkant.bhajanapp.utils.BottomNav;
import com.rkant.bhajanapp.utils.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibraryActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY = "CATEGORY";
    public static final String EXTRA_TAB = "TAB";
    public static final String EXTRA_FOCUS_SEARCH = "FOCUS_SEARCH";
    private View recentStripContainer, recentScroll, recentEmpty;
    private LinearLayout recentStrip;
    private ValueAnimator stripAnimator;

    public static final int TAB_ALL = 0;
    public static final int TAB_FAV = 1;
    public static final int TAB_COLLECTIONS = 2;
    public static final int TAB_RECENT = 3;

    private static final int KIND_GENERAL = 0;
    private static final int KIND_FAV = 1;
    private static final int KIND_ARCHIVE = 2;

    private ValueAnimator chipsAnimator;


    private final List<String> cats = Arrays.asList("All", "Krishna", "Shiva", "Hari", "Ram", "Others");

    private List<Bhajan> all = new ArrayList<>();
    private String category = "All";
    private int tab = TAB_ALL;
    private String selectedCollection = null;
    private String selectedSubCollection = null;
    private boolean showSubCollectionList = false;
    private boolean showingArchived = false;
    private boolean searchOpen = false;

    private BhajanAdapter adapter;
    private BhajanCategoryAdapter bhajanCategoryAdapter;
    private CategoryAdapter categoryAdapter;
    private RecyclerView rvList, rvCategories;
    private View root, btnSearchClear, emptyState, listContainer;
    private View titleGroup, searchInline;
    private EditText etSearch;
    private TextView tvCount, tvEmptyTitle, tvEmptySub, btnEmptyAction, tvActiveView;

    private ImageView fabAdd;

    public static Intent intent(android.content.Context ctx, String category, int tab, boolean focusSearch) {
        Intent in = new Intent(ctx, LibraryActivity.class);
        in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        in.putExtra(EXTRA_CATEGORY, category);
        in.putExtra(EXTRA_TAB, tab);
        in.putExtra(EXTRA_FOCUS_SEARCH, focusSearch);
        return in;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        BottomNav.setup(this, BottomNav.LIBRARY);

        root = findViewById(R.id.root);
        etSearch = findViewById(R.id.et_search);
        btnSearchClear = findViewById(R.id.btn_search_clear);
        tvCount = findViewById(R.id.tv_count);
        tvActiveView = findViewById(R.id.tv_active_view);
        emptyState = findViewById(R.id.empty_state);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySub = findViewById(R.id.tv_empty_sub);
        btnEmptyAction = findViewById(R.id.btn_empty_action);
        rvList = findViewById(R.id.recyclerView);
        listContainer = findViewById(R.id.list_container);
        titleGroup = findViewById(R.id.title_group);
        searchInline = findViewById(R.id.search_inline);
        recentStripContainer = findViewById(R.id.recent_strip_container);
        recentScroll = findViewById(R.id.recent_scroll);
        recentEmpty = findViewById(R.id.recent_empty);
        recentStrip = findViewById(R.id.recent_strip);
        fabAdd = findViewById(R.id.fab_add);
        recentStripContainer = findViewById(R.id.recent_strip_container);
        recentScroll = findViewById(R.id.recent_scroll);
        recentEmpty = findViewById(R.id.recent_empty);
        recentStrip = findViewById(R.id.recent_strip);

        root.requestFocus();

        findViewById(R.id.btn_search_toggle).setOnClickListener(v -> toggleSearch());
        btnSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
            hideKeyboard();
        });
        findViewById(R.id.btn_overflow).setOnClickListener(v -> showOverflowMenu());
        fabAdd.setOnClickListener(v -> handleFabClick());

        adapter = new BhajanAdapter(new ArrayList<>(), this);

        // ── Favourite changed: animate removal when a favourite disappears in the Favourites view ──
        adapter.setOnFavChanged((b, position, nowFav) -> {
            if (!nowFav && tab == TAB_FAV) {
                int p = position;
                if (p < 0) p = adapter.positionOf(b);
                if (p >= 0) {
                    adapter.removeItemAt(p);
                    refreshMetadata();
                } else {
                    refresh();
                }
            } else {
                refresh();
            }
        });

        // ── Archived: adapter already animated+removed the row unless "show archived in list" is on ──
        adapter.setOnArchiveChanged((b, position) -> {
            if (Helper.isArchivedInListEnabled(this)) {
                refresh();          // item stays, just restyled
            } else {
                refreshMetadata();  // item already removed by the adapter
            }
        });

        adapter.setOnBhajanOptions((bhajan, position) -> showBhajanOptions(bhajan));

        bhajanCategoryAdapter = new BhajanCategoryAdapter(this, new BhajanCategoryAdapter.OnCategoryAction() {
            @Override
            public void onCategoryClick(String name) {
                if (tab == TAB_COLLECTIONS) {
                    selectedCollection = name;
                } else {
                    showSubCollectionList = false;
                    selectedSubCollection = BhajanCategoryAdapter.ALL_ITEM.equals(name) ? null : name;
                }
                refreshAnimated();
            }

            @Override
            public void onCategoryDelete(String name) {
                final int kind = tab == TAB_FAV ? KIND_FAV : (showingArchived ? KIND_ARCHIVE : KIND_GENERAL);
                Helper.showConfirm(LibraryActivity.this, "Delete Collection",
                        "Delete collection \u201C" + name + "\u201D? The bhajans inside it will not be deleted.",
                        "Delete", true, () -> {
                            if (kind == KIND_FAV) Helper.deleteFavCategory(LibraryActivity.this, name);
                            else if (kind == KIND_ARCHIVE) Helper.deleteArchiveCategory(LibraryActivity.this, name);
                            else Helper.deleteBhajanCategory(LibraryActivity.this, name);
                            Toast.makeText(LibraryActivity.this, "Collection deleted", Toast.LENGTH_SHORT).show();
                            refresh();
                        });
            }
        });

        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);
        rvCategories = findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(cats, this, c -> {
            boolean changed = !c.equals(category);
            category = c;
            if (changed) refresh();
            scrollToTopSmooth();
        });
        rvCategories.setAdapter(categoryAdapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                btnSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                refresh();
            }
            public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                etSearch.clearFocus();
                root.requestFocus();
                return true;
            }
            return false;
        });

        btnEmptyAction.setOnClickListener(v -> {
            tab = TAB_ALL;
            showingArchived = false;
            selectedCollection = null;
            selectedSubCollection = null;
            showSubCollectionList = false;
            category = "All";
            categoryAdapter.setSelected("All");
            refreshAnimated();
        });

        if (savedInstanceState == null) {
            applyIntent(getIntent());
        } else {
            category = savedInstanceState.getString("state_category", "All");
            tab = savedInstanceState.getInt("state_tab", TAB_ALL);
            selectedCollection = savedInstanceState.getString("state_selected_collection");
            selectedSubCollection = savedInstanceState.getString("state_selected_sub");
            showSubCollectionList = savedInstanceState.getBoolean("state_show_sub", false);
            showingArchived = savedInstanceState.getBoolean("state_showing_archived", false);
            categoryAdapter.setSelected(category);
        }

        BhajanRepo.load(this, list -> {
            if (isFinishing() || isDestroyed()) return;
            all = list;
            refresh();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("state_category", category);
        outState.putInt("state_tab", tab);
        outState.putString("state_selected_collection", selectedCollection);
        outState.putString("state_selected_sub", selectedSubCollection);
        outState.putBoolean("state_show_sub", showSubCollectionList);
        outState.putBoolean("state_showing_archived", showingArchived);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void applyIntent(Intent in) {
        if (in == null) return;
        if (in.hasExtra(EXTRA_CATEGORY)) {
            String c = in.getStringExtra(EXTRA_CATEGORY);
            category = (c == null || !cats.contains(c)) ? "All" : c;
            categoryAdapter.setSelected(category);
        }
        if (in.hasExtra(EXTRA_TAB)) {
            int requestedTab = in.getIntExtra(EXTRA_TAB, TAB_ALL);
            if (requestedTab == 1) tab = TAB_ALL;
            else if (requestedTab == 2) tab = TAB_FAV;
            else if (requestedTab == 3) { tab = TAB_ALL; showingArchived = true; }
            else if (requestedTab == 4) tab = TAB_COLLECTIONS;
            else tab = TAB_ALL;

            selectedCollection = null;
            selectedSubCollection = null;
            showSubCollectionList = (tab == TAB_FAV || showingArchived);
        }
        if (in.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            openSearch();
        }
        in.removeExtra(EXTRA_CATEGORY);
        in.removeExtra(EXTRA_TAB);
        in.removeExtra(EXTRA_FOCUS_SEARCH);
        refresh();
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
        titleGroup.animate()
                .translationX(-titleGroup.getWidth())
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> titleGroup.setVisibility(View.INVISIBLE))
                .start();
        searchInline.setVisibility(View.VISIBLE);
        searchInline.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(200)
                .start();
        etSearch.requestFocus();
        etSearch.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void closeSearch() {
        searchOpen = false;
        etSearch.setText("");
        etSearch.clearFocus();
        hideKeyboard();
        searchInline.animate()
                .translationX(200)
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> searchInline.setVisibility(View.INVISIBLE))
                .start();
        titleGroup.setVisibility(View.VISIBLE);
        titleGroup.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(200)
                .start();
        refresh();
    }

    // ═══════════════════════════════════════════════════════════
    // FAB
    // ═══════════════════════════════════════════════════════════
    private void handleFabClick() {
        if (showingArchived) showCreateCollectionDialog(null, KIND_ARCHIVE);
        else if (tab == TAB_FAV) showCreateCollectionDialog(null, KIND_FAV);
        else if (tab == TAB_COLLECTIONS) showCreateCollectionDialog(null, KIND_GENERAL);
    }

    private void updateFabVisibility() {
        boolean showFab = false;
        if (showingArchived && showSubCollectionList) showFab = true;
        else if (tab == TAB_FAV && showSubCollectionList) showFab = true;
        else if (tab == TAB_COLLECTIONS && selectedCollection == null) showFab = true;
        fabAdd.setVisibility(showFab ? View.VISIBLE : View.GONE);
    }

    // ═══════════════════════════════════════════════════════════
    // OVERFLOW MENU
    // ═══════════════════════════════════════════════════════════
    private void showOverflowMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_library_overflow, null);

        int allCount = all.size();
        int recentCount = Helper.getRecentIds(this).size();
        int favCount = Helper.favCount(this);
        int collectionsCount = Helper.getBhajanCategories(this).size();
        int archivedCount = Helper.archivedCount(this);

        ((TextView) v.findViewById(R.id.count_all)).setText(allCount + "");
        ((TextView) v.findViewById(R.id.count_recent)).setText(recentCount + "");
        ((TextView) v.findViewById(R.id.count_fav)).setText(favCount + "");
        ((TextView) v.findViewById(R.id.count_collections)).setText(collectionsCount + "");
        ((TextView) v.findViewById(R.id.count_archived)).setText(archivedCount + "");

        v.findViewById(R.id.check_all).setVisibility(tab == TAB_ALL && !showingArchived ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_recent).setVisibility(tab == TAB_RECENT ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_fav).setVisibility(tab == TAB_FAV ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_collections).setVisibility(tab == TAB_COLLECTIONS ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.check_archived).setVisibility(showingArchived ? View.VISIBLE : View.GONE);

        v.findViewById(R.id.menu_all).setOnClickListener(x -> { dialog.dismiss(); switchView(TAB_ALL, false); });
        v.findViewById(R.id.menu_recent).setOnClickListener(x -> { dialog.dismiss(); switchView(TAB_RECENT, false); });
        v.findViewById(R.id.menu_fav).setOnClickListener(x -> { dialog.dismiss(); switchView(TAB_FAV, false); });
        v.findViewById(R.id.menu_collections).setOnClickListener(x -> { dialog.dismiss(); switchView(TAB_COLLECTIONS, false); });
        v.findViewById(R.id.menu_archived).setOnClickListener(x -> { dialog.dismiss(); switchView(TAB_ALL, true); });

        View manageToggle = v.findViewById(R.id.menu_manage_toggle);
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

        v.findViewById(R.id.menu_clear_recent).setOnClickListener(x -> {
            dialog.dismiss();
            Helper.showConfirm(this, "Clear reading history", "Remove all recently opened bhajans?", "Clear", true, () -> {
                Helper.clearRecents(this);
                refresh();
                Toast.makeText(this, "Reading history cleared", Toast.LENGTH_SHORT).show();
            });
        });

        v.findViewById(R.id.menu_clear_favs).setOnClickListener(x -> {
            dialog.dismiss();
            Helper.showConfirm(this, "Clear favourites", "Remove all bhajans from your favourite list?", "Clear", true, () -> {
                Helper.clearFavs(this);
                refresh();
                Toast.makeText(this, "Favourites cleared", Toast.LENGTH_SHORT).show();
            });
        });

        v.findViewById(R.id.menu_unarchive_all).setOnClickListener(x -> {
            dialog.dismiss();
            Helper.showConfirm(this, "Unarchive all", "Restore all archived bhajans?", "Restore", true, () -> {
                Helper.clearArchived(this);
                refresh();
                Toast.makeText(this, "All bhajans unarchived", Toast.LENGTH_SHORT).show();
            });
        });

        v.findViewById(R.id.btn_close).setOnClickListener(x -> dialog.dismiss());
        dialog.setContentView(v);
        dialog.show();
    }

    private void switchView(int newTab, boolean archived) {
        tab = newTab;
        showingArchived = archived;
        selectedCollection = null;
        selectedSubCollection = null;
        showSubCollectionList = (newTab == TAB_FAV || archived);
        refreshAnimated();
    }

    // ═══════════════════════════════════════════════════════════
    // REFRESH (with bug fix + optional fade transition)
    // ═══════════════════════════════════════════════════════════
    private void refresh() { refreshInternal(false); }
    private void refreshAnimated() { refreshInternal(true); }

    private void refreshInternal(boolean animate) {
        if (adapter == null) return;

        updateActiveViewLabel();
        updateFabVisibility();

        String q = etSearch.getText().toString().trim().toLowerCase();
        boolean searching = !q.isEmpty();

        boolean chipsVisible = (tab == TAB_ALL && !showingArchived);
        boolean stripVisible = (tab == TAB_ALL && !showingArchived && !searching);

        if (stripVisible) populateRecentStripCards();
        animateChips(chipsVisible);
        animateStrip(stripVisible);


        if (tab == TAB_COLLECTIONS && selectedCollection == null) { showCollectionList(KIND_GENERAL); return; }
        if (tab == TAB_FAV && showSubCollectionList) { showCollectionList(KIND_FAV); return; }
        if (showingArchived && showSubCollectionList) { showCollectionList(KIND_ARCHIVE); return; }



        if (rvList.getAdapter() != adapter) rvList.setAdapter(adapter);

        boolean includeArchived = Helper.isArchivedInListEnabled(this)
                || (searching && Helper.isArchivedSearchable(this));

        // ── Build base list by view ──
        List<Bhajan> base = new ArrayList<>();
        if (showingArchived) {
            if (selectedSubCollection == null) {
                for (Bhajan b : all) if (Helper.isArchived(this, b.id)) base.add(b);
            } else {
                List<String> ids = Helper.getArchiveCategories(this).get(selectedSubCollection);
                if (ids == null) { selectedSubCollection = null; showSubCollectionList = true; refresh(); return; }
                for (String id : ids) { Bhajan b = BhajanRepo.find(all, id); if (b != null && Helper.isArchived(this, b.id)) base.add(b); }
            }
        } else if (tab == TAB_FAV) {
            if (selectedSubCollection == null) {
                // Most recently added favourites first
                for (String id : Helper.getFavIds(this)) {
                    Bhajan b = BhajanRepo.find(all, id);
                    if (b != null) base.add(b);
                }
            } else {
                List<String> ids = Helper.getFavCategories(this).get(selectedSubCollection);
                if (ids == null) { selectedSubCollection = null; showSubCollectionList = true; refresh(); return; }
                for (String id : ids) { Bhajan b = BhajanRepo.find(all, id); if (b != null && Helper.isFav(this, b.id)) base.add(b); }
            }
        } else if (tab == TAB_COLLECTIONS && selectedCollection != null) {
            List<String> ids = Helper.getBhajanCategories(this).get(selectedCollection);
            if (ids == null) { selectedCollection = null; refresh(); return; }
            for (String id : ids) { Bhajan b = BhajanRepo.find(all, id); if (b != null) base.add(b); }
        } else if (tab == TAB_RECENT) {
            int recentLimit = Helper.getLibraryRecentLimit(this);
            List<String> recentIds = Helper.getRecentIds(this);
            int count = 0;
            for (String id : recentIds) {
                if (count >= recentLimit) break;
                Bhajan b = BhajanRepo.find(all, id);
                if (b != null) { base.add(b); count++; }
            }
        } else {
            for (Bhajan b : all) {
                if (!Helper.isArchived(this, b.id) || includeArchived) base.add(b);
            }
        }

        // Deity filter applies ONLY in the All view
        boolean applyDeityFilter = (tab == TAB_ALL && !showingArchived);

        List<Bhajan> out = new ArrayList<>();
        for (Bhajan b : base) {
            if (applyDeityFilter && !BhajanRepo.inCategory(b, category)) continue;
            if (!BhajanRepo.matches(b, q)) continue;
            out.add(b);
        }

        boolean swipeEnabled = (tab == TAB_ALL || tab == TAB_RECENT) && !showingArchived;
        boolean empty = out.isEmpty() && !all.isEmpty();

        final List<Bhajan> finalOut = out;
        final String query = q;
        Runnable applyUpdate = () -> {
            adapter.update(finalOut);
            adapter.setSwipeArchiveEnabled(swipeEnabled);
            if (animate) rvList.scrollToPosition(0);   // view change → start at top
            String noun = finalOut.size() == 1 ? "bhajan" : "bhajans";
            tvCount.setText(finalOut.size() + " " + noun);
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvList.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
            if (empty) setEmptyTexts(query);
        };

        if (animate) {
            listContainer.animate().cancel();
            listContainer.animate()
                    .alpha(0f)
                    .setDuration(140)
                    .withEndAction(() -> {
                        applyUpdate.run();
                        listContainer.animate().alpha(1f).setDuration(220).start();
                    })
                    .start();
        } else {
            applyUpdate.run();
        }
    }
    /** Lightweight refresh after an animated removal (adapter list already updated). */
    private void refreshMetadata() {
        int count = adapter.getItemCount();
        String noun = count == 1 ? "bhajan" : "bhajans";
        tvCount.setText(count + " " + noun);
        String q = etSearch.getText().toString().trim().toLowerCase();
        boolean showRecentCards = (tab == TAB_ALL && !showingArchived && q.isEmpty());
        updateRecentStrip(showRecentCards);
        boolean empty = count == 0 && !all.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvList.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) setEmptyTexts(q);
    }

    private void populateRecentStripCards() {
        recentStrip.removeAllViews();
        List<String> recentIds = Helper.getRecentIds(this);
        int limit = Math.min(recentIds.size(), 6);
        int shown = 0;
        LayoutInflater inflater = LayoutInflater.from(this);

        for (String id : recentIds) {
            if (shown >= limit) break;
            Bhajan b = BhajanRepo.find(all, id);
            if (b == null) continue;

            View card = inflater.inflate(R.layout.item_recent_card, recentStrip, false);
            ((TextView) card.findViewById(R.id.tv_recent_title)).setText(b.titleNepali);
            ((TextView) card.findViewById(R.id.tv_recent_type)).setText(b.type != null ? b.type : "");
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, DetailActivity.class);
                i.putExtra("ID", b.id);
                i.putExtra("TITLE", b.titleNepali);
                startActivity(i);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
            recentStrip.addView(card);
            shown++;
        }

        if (shown == 0) {
            recentScroll.setVisibility(View.GONE);
            recentEmpty.setVisibility(View.VISIBLE);
        } else {
            recentScroll.setVisibility(View.VISIBLE);
            recentEmpty.setVisibility(View.GONE);
        }
    }
    private void setEmptyTexts(String q) {
        btnEmptyAction.setVisibility(View.GONE);
        if (q != null && !q.isEmpty()) {
            tvEmptyTitle.setText("No bhajans found");
            tvEmptySub.setText("Try a different search.");
        } else if (showingArchived) {
            tvEmptyTitle.setText("No archived bhajans");
            tvEmptySub.setText("Swipe a bhajan left or right in All or Recent to archive it.");
        } else if (tab == TAB_FAV) {
            if (selectedSubCollection != null) {
                tvEmptyTitle.setText("No bhajans here yet");
                tvEmptySub.setText("Long press any bhajan and choose \u201CAdd to Favourites\u201D.");
            } else {
                tvEmptyTitle.setText("No favourites yet");
                tvEmptySub.setText("Tap the heart on any bhajan to save it here.");
            }
        } else if (tab == TAB_COLLECTIONS) {
            tvEmptyTitle.setText("No bhajans here yet");
            tvEmptySub.setText("Long press any bhajan and choose \u201CAdd to Collection\u201D.");
            btnEmptyAction.setVisibility(View.VISIBLE);
            btnEmptyAction.setText("Browse all bhajans");
        } else if (tab == TAB_RECENT) {
            tvEmptyTitle.setText("Nothing opened yet");
            tvEmptySub.setText("Bhajans you read will show up here.");
        } else {
            tvEmptyTitle.setText("No bhajans here");
            tvEmptySub.setText("Everything might be archived.");
        }
    }

    private void updateActiveViewLabel() {
        String label;
        if (showingArchived) label = "Archived";
        else if (tab == TAB_FAV) label = "Favourites";
        else if (tab == TAB_COLLECTIONS) label = "Collections";
        else if (tab == TAB_RECENT) label = "Recent";
        else label = "All";
        if (selectedCollection != null) label = selectedCollection;
        if (selectedSubCollection != null) label = selectedSubCollection;
        tvActiveView.setText(label);
    }

    private void updateRecentStrip(boolean showCards) {
        recentStripContainer.setVisibility(showCards ? View.VISIBLE : View.GONE);
        if (!showCards) return;

        recentStrip.removeAllViews();
        List<String> recentIds = Helper.getRecentIds(this);
        int limit = Math.min(recentIds.size(), 6);
        int shown = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String id : recentIds) {
            if (shown >= limit) break;
            Bhajan b = BhajanRepo.find(all, id);
            if (b == null) continue;
            View card = inflater.inflate(R.layout.item_recent_card, recentStrip, false);
            ((TextView) card.findViewById(R.id.tv_recent_title)).setText(b.titleNepali);
            ((TextView) card.findViewById(R.id.tv_recent_type)).setText(b.type != null ? b.type : "");
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, DetailActivity.class);
                i.putExtra("ID", b.id);
                i.putExtra("TITLE", b.titleNepali);
                startActivity(i);
            });
            recentStrip.addView(card);
            shown++;
        }
        if (shown == 0) {
            recentScroll.setVisibility(View.GONE);
            recentEmpty.setVisibility(View.VISIBLE);
        } else {
            recentScroll.setVisibility(View.VISIBLE);
            recentEmpty.setVisibility(View.GONE);
        }
    }

    private void showCollectionList(int kind) {
        if (rvList.getAdapter() != bhajanCategoryAdapter) rvList.setAdapter(bhajanCategoryAdapter);
        adapter.setSwipeArchiveEnabled(false);
        recentStripContainer.setVisibility(View.GONE);
        updateFabVisibility();

        Map<String, List<String>> collectionMap;
        String allName = null;
        int allCount = 0;

        if (kind == KIND_FAV) {
            collectionMap = Helper.getFavCategories(this);
            allName = "All Favourites";
            allCount = Helper.favCount(this);
        } else if (kind == KIND_ARCHIVE) {
            collectionMap = Helper.getArchiveCategories(this);
            allName = "All Archived";
            allCount = Helper.archivedCount(this);
        } else {
            collectionMap = Helper.getBhajanCategories(this);
        }

        bhajanCategoryAdapter.setAllItem(allName, allCount);
        List<String> names = new ArrayList<>(collectionMap.keySet());
        Map<String, Integer> counts = new HashMap<>();
        for (String n : names) {
            List<String> ids = collectionMap.get(n);
            int c = 0;
            for (String id : ids) {
                if (kind == KIND_FAV) { if (Helper.isFav(this, id)) c++; }
                else if (kind == KIND_ARCHIVE) { if (Helper.isArchived(this, id)) c++; }
                else c++;
            }
            counts.put(n, c);
        }
        bhajanCategoryAdapter.update(names, counts);
        tvCount.setText(names.size() + (names.size() == 1 ? " collection" : " collections"));

        boolean empty = names.isEmpty() && allName == null;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvList.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            tvEmptyTitle.setText("No collections yet");
            tvEmptySub.setText("Tap the + button to create your first collection.");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LONG PRESS OPTIONS
    // ═══════════════════════════════════════════════════════════
    private void showBhajanOptions(Bhajan b) {
        if (b == null) return;
        boolean fav = Helper.isFav(this, b.id);
        boolean archived = Helper.isArchived(this, b.id);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_bhajan_options, null);

        TextView sheetTitle = v.findViewById(R.id.sheet_title);
        TextView sheetSub = v.findViewById(R.id.sheet_sub);
        TextView sheetCategory = v.findViewById(R.id.sheet_category);
        TextView sheetFav = v.findViewById(R.id.sheet_fav);
        TextView sheetArchive = v.findViewById(R.id.sheet_archive);
        TextView sheetRemoveRecent = v.findViewById(R.id.sheet_remove_recent);
        TextView sheetRemoveCollection = v.findViewById(R.id.sheet_remove_collection);
        View sheetClose = v.findViewById(R.id.sheet_close);

        sheetTitle.setText(b.titleNepali);
        StringBuilder sub = new StringBuilder();
        if (b.type != null && !b.type.isEmpty()) sub.append(b.type);
        if (b.category != null && !b.category.isEmpty()) {
            if (sub.length() > 0) sub.append("  \u00B7  ");
            sub.append(BhajanCategories.nepaliFor(b.category));
        }
        if (sub.length() > 0) { sheetSub.setText(sub.toString()); sheetSub.setVisibility(View.VISIBLE); }
        else sheetSub.setVisibility(View.GONE);

        sheetCategory.setOnClickListener(x -> { dialog.dismiss(); showAddToCollectionDialog(b); });

        if (fav) {
            sheetFav.setText("Remove from Favourites");
            sheetFav.setTextColor(getColor(R.color.accent));
            sheetFav.setOnClickListener(x -> { dialog.dismiss(); confirmRemoveFavourite(b); });
        } else {
            sheetFav.setText("Add to Favourites");
            sheetFav.setTextColor(getColor(R.color.text_primary));
            sheetFav.setOnClickListener(x -> { dialog.dismiss(); showAddToFavCollectionDialog(b); });
        }

        if (archived) {
            sheetArchive.setText("Unarchive");
            sheetArchive.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.showConfirm(this, "Unarchive bhajan",
                        "Restore \u201C" + b.titleNepali + "\u201D back to the main list?",
                        "Unarchive", false, () -> {
                            Helper.unarchive(this, b.id);
                            Toast.makeText(this, "Bhajan unarchived", Toast.LENGTH_SHORT).show();
                            refresh();
                        });
            });
        } else {
            sheetArchive.setText("Archive");
            sheetArchive.setOnClickListener(x -> { dialog.dismiss(); showAddToArchiveCollectionDialog(b); });
        }

        if (Helper.getRecentIds(this).contains(b.id)) {
            sheetRemoveRecent.setVisibility(View.VISIBLE);
            sheetRemoveRecent.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeRecent(this, b.id);
                Toast.makeText(this, "Removed from recent", Toast.LENGTH_SHORT).show();
                if (tab == TAB_RECENT) {
                    int pos = adapter.positionOf(b);
                    if (pos >= 0) { adapter.removeItemAt(pos); refreshMetadata(); }
                    else refresh();
                } else {
                    refresh();
                }
            });
        }

        if (tab == TAB_COLLECTIONS && selectedCollection != null) {
            final String collName = selectedCollection;
            sheetRemoveCollection.setVisibility(View.VISIBLE);
            sheetRemoveCollection.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeFromBhajanCategory(this, collName, b.id);
                Toast.makeText(this, "Removed from \u201C" + collName + "\u201D", Toast.LENGTH_SHORT).show();
                int pos = adapter.positionOf(b);
                if (pos >= 0) { adapter.removeItemAt(pos); refreshMetadata(); }
                else refresh();
            });
        }

        sheetClose.setOnClickListener(x -> dialog.dismiss());
        dialog.setContentView(v);
        dialog.show();
    }

    private void confirmRemoveFavourite(Bhajan b) {
        Helper.showConfirm(this, "Remove from Favourites",
                "Remove \u201C" + b.titleNepali + "\u201D from your favourite list?",
                "Remove", true, () -> {
                    Helper.toggleFav(this, b.id);
                    Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                    if (tab == TAB_FAV) {
                        int pos = adapter.positionOf(b);
                        if (pos >= 0) { adapter.removeItemAt(pos); refreshMetadata(); }
                        else refresh();
                    } else {
                        refresh();
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════
    // COLLECTION DIALOGS
    // ═══════════════════════════════════════════════════════════
    private void showAddToCollectionDialog(Bhajan b) {
        if (b == null) return;
        Map<String, List<String>> userCats = Helper.getBhajanCategories(this);
        List<String> names = new ArrayList<>(userCats.keySet());
        if (names.isEmpty()) { showCreateCollectionDialog(b, KIND_GENERAL); return; }
        names.add("+ Create new collection");
        new AlertDialog.Builder(this)
                .setTitle("Add to Collection")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == names.size() - 1) showCreateCollectionDialog(b, KIND_GENERAL);
                    else {
                        Helper.addToBhajanCategory(this, names.get(which), b.id);
                        Toast.makeText(this, "Added to \u201C" + names.get(which) + "\u201D", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showAddToFavCollectionDialog(Bhajan b) {
        if (b == null) return;
        Map<String, List<String>> favCats = Helper.getFavCategories(this);
        List<String> names = new ArrayList<>();
        names.add("All Favourites (default)");
        names.addAll(favCats.keySet());
        names.add("+ Create new collection");
        new AlertDialog.Builder(this)
                .setTitle("Add to Favourites")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        Helper.ensureFav(this, b.id);
                        Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if (which == names.size() - 1) {
                        showCreateCollectionDialog(b, KIND_FAV);
                    } else {
                        Helper.addToFavCategory(this, names.get(which), b.id);
                        Toast.makeText(this, "Added to favourites \u00B7 \u201C" + names.get(which) + "\u201D",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .show();
    }

    private void showAddToArchiveCollectionDialog(Bhajan b) {
        if (b == null) return;
        Map<String, List<String>> archiveCats = Helper.getArchiveCategories(this);
        List<String> names = new ArrayList<>();
        names.add("All Archived (default)");
        names.addAll(archiveCats.keySet());
        names.add("+ Create new collection");
        new AlertDialog.Builder(this)
                .setTitle("Archive Bhajan")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        Helper.ensureArchived(this, b.id);
                        Toast.makeText(this, "Bhajan archived", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if (which == names.size() - 1) {
                        showCreateCollectionDialog(b, KIND_ARCHIVE);
                    } else {
                        Helper.addToArchiveCategory(this, names.get(which), b.id);
                        Toast.makeText(this, "Archived \u00B7 \u201C" + names.get(which) + "\u201D",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .show();
    }

    private void showCreateCollectionDialog(Bhajan bhajanToAdd, int kind) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_category_name, null);
        EditText etName = view.findViewById(R.id.et_category_name);
        TextView tvDialogTitle = view.findViewById(R.id.tv_dialog_title);

        String title = kind == KIND_FAV ? "New Favourite Collection"
                : kind == KIND_ARCHIVE ? "New Archive Collection" : "New Collection";
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
                    if (kind == KIND_FAV) {
                        Helper.createFavCategory(this, name);
                        if (bhajanToAdd != null) {
                            Helper.addToFavCategory(this, name, bhajanToAdd.id);
                            Toast.makeText(this, "Added to favourites \u00B7 \u201C" + name + "\u201D", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show();
                    } else if (kind == KIND_ARCHIVE) {
                        Helper.createArchiveCategory(this, name);
                        if (bhajanToAdd != null) {
                            Helper.addToArchiveCategory(this, name, bhajanToAdd.id);
                            Toast.makeText(this, "Archived \u00B7 \u201C" + name + "\u201D", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show();
                    } else {
                        Helper.createBhajanCategory(this, name);
                        if (bhajanToAdd != null) {
                            Helper.addToBhajanCategory(this, name, bhajanToAdd.id);
                            Toast.makeText(this, "Added to \u201C" + name + "\u201D", Toast.LENGTH_SHORT).show();
                        } else Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (searchOpen) { closeSearch(); return; }
        if (showingArchived) { showingArchived = false; selectedSubCollection = null; showSubCollectionList = false; refresh(); return; }
        if (tab == TAB_COLLECTIONS && selectedCollection != null) { selectedCollection = null; refresh(); return; }
        if (tab == TAB_FAV && !showSubCollectionList) { showSubCollectionList = true; selectedSubCollection = null; refresh(); return; }
        if (!"All".equals(category) || tab != TAB_ALL) {
            category = "All"; categoryAdapter.setSelected("All"); tab = TAB_ALL;
            selectedCollection = null; selectedSubCollection = null; showSubCollectionList = false;
            refresh(); return;
        }
        super.onBackPressed();
    }
    // ── Smooth scroll to top ──────────────────────────────────────
    private void scrollToTopSmooth() {
        if (rvList == null) return;
        RecyclerView.LayoutManager lm = rvList.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return;
        LinearLayoutManager llm = (LinearLayoutManager) lm;
        int firstVisible = llm.findFirstVisibleItemPosition();
        if (firstVisible <= 0) return;                    // already at top
        if (firstVisible > 20) {
            // Very far down: jump close to the top, then smooth-scroll the last bit
            rvList.scrollToPosition(8);
            rvList.post(() -> rvList.smoothScrollToPosition(0));
        } else {
            rvList.smoothScrollToPosition(0);
        }
    }

    // ── Smooth show / hide for deity chips ────────────────────────
    private void animateChips(boolean show) {
        if (chipsAnimator != null && chipsAnimator.isRunning()) chipsAnimator.cancel();
        if (show) {
            if (rvCategories.getVisibility() == View.VISIBLE) return;
            chipsAnimator = buildExpandAnimator(rvCategories);
        } else {
            if (rvCategories.getVisibility() != View.VISIBLE) { rvCategories.setVisibility(View.GONE); return; }
            chipsAnimator = buildCollapseAnimator(rvCategories);
        }
        chipsAnimator.start();
    }


    private void animateStrip(boolean show) {
        if (stripAnimator != null && stripAnimator.isRunning()) stripAnimator.cancel();

        if (show) {
            if (recentStripContainer.getVisibility() == View.VISIBLE) return;
            stripAnimator = buildExpandAnimator(recentStripContainer);
        } else {
            if (recentStripContainer.getVisibility() != View.VISIBLE) {
                recentStripContainer.setVisibility(View.GONE);
                return;
            }
            stripAnimator = buildCollapseAnimator(recentStripContainer);
        }
        stripAnimator.start();
    }

    private ValueAnimator buildExpandAnimator(final View v) {
        int parentWidth = ((View) v.getParent()).getWidth();
        v.measure(
                View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);

        ValueAnimator anim = ValueAnimator.ofInt(0, targetHeight);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            v.getLayoutParams().height = (int) a.getAnimatedValue();
            v.setAlpha(a.getAnimatedFraction());
            v.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setAlpha(1f);
                v.requestLayout();
            }
        });
        return anim;
    }
    private ValueAnimator buildCollapseAnimator(final View v) {
        final int startHeight = v.getHeight();

        ValueAnimator anim = ValueAnimator.ofInt(startHeight, 0);
        anim.setDuration(220);
        anim.setInterpolator(new AccelerateInterpolator());
        anim.addUpdateListener(a -> {
            v.getLayoutParams().height = (int) a.getAnimatedValue();
            v.setAlpha(1f - a.getAnimatedFraction());
            v.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(View.GONE);
                v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setAlpha(1f);
                v.requestLayout();
            }
        });
        return anim;
    }
}