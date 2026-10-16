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
import android.widget.TextView;
import android.widget.Toast;

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

    public static final int TAB_ALL = 0;
    public static final int TAB_RECENT = 1;
    public static final int TAB_FAV = 2;
    public static final int TAB_ARCHIVE = 3;
    public static final int TAB_CATEGORY = 4;

    private static final int KIND_GENERAL = 0;
    private static final int KIND_FAV = 1;
    private static final int KIND_ARCHIVE = 2;

    private final List<String> cats = Arrays.asList("All", "Krishna", "Shiva", "Hari", "Ram", "Others");

    private List<Bhajan> all = new ArrayList<>();
    private String category = "All";
    private int tab = TAB_ALL;

    private String selectedCategory = null;      // opened custom category (Category tab)
    private String selectedSubCategory = null;   // opened custom sub-category (Favourites / Archived tab)
    private boolean showSubCategoryList = false; // Favourites / Archived tab: showing their category list

    private BhajanAdapter adapter;
    private BhajanCategoryAdapter bhajanCategoryAdapter;
    private CategoryAdapter categoryAdapter;
    private RecyclerView rvList;
    private View root, btnSearchClear, emptyState;
    private TextView btnClearAll;
    private EditText etSearch;
    private TextView tvCount, tvEmptyTitle, tvEmptySub;
    private TextView[] segments;

    public static Intent intent(android.content.Context ctx, String category, int tab, boolean focusSearch) {
        Intent in = new Intent(ctx, LibraryActivity.class);
        in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
        btnClearAll = findViewById(R.id.btn_clear_all);
        tvCount = findViewById(R.id.tv_count);
        emptyState = findViewById(R.id.empty_state);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySub = findViewById(R.id.tv_empty_sub);
        rvList = findViewById(R.id.recyclerView);
        root.requestFocus();

        adapter = new BhajanAdapter(new ArrayList<>(), this);
        adapter.setOnFavChanged(now -> refresh());
        // Swipe-to-archive archives directly into "All Archived" — just refresh
        adapter.setOnArchiveChanged(id -> refresh());
        adapter.setOnBhajanOptions((bhajan, position) -> showBhajanOptions(bhajan));

        bhajanCategoryAdapter = new BhajanCategoryAdapter(this, new BhajanCategoryAdapter.OnCategoryAction() {
            @Override
            public void onCategoryClick(String name) {
                if (tab == TAB_CATEGORY) {
                    selectedCategory = name;
                } else {
                    showSubCategoryList = false;
                    selectedSubCategory = BhajanCategoryAdapter.ALL_ITEM.equals(name) ? null : name;
                }
                refresh();
            }

            @Override
            public void onCategoryDelete(String name) {
                final int kind = tab == TAB_FAV ? KIND_FAV : (tab == TAB_ARCHIVE ? KIND_ARCHIVE : KIND_GENERAL);
                Helper.showConfirm(LibraryActivity.this, "Delete Category",
                        "Delete category \u201C" + name + "\u201D? The bhajans inside it will not be deleted.",
                        "Delete", true, () -> {
                            if (kind == KIND_FAV) Helper.deleteFavCategory(LibraryActivity.this, name);
                            else if (kind == KIND_ARCHIVE) Helper.deleteArchiveCategory(LibraryActivity.this, name);
                            else Helper.deleteBhajanCategory(LibraryActivity.this, name);
                            Toast.makeText(LibraryActivity.this, "Category deleted", Toast.LENGTH_SHORT).show();
                            refresh();
                        });
            }
        });

        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);

        RecyclerView rvCategories = findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(cats, this, c -> { category = c; refresh(); });
        rvCategories.setAdapter(categoryAdapter);

        segments = new TextView[]{
                findViewById(R.id.seg_all),
                findViewById(R.id.seg_recent),
                findViewById(R.id.seg_fav),
                findViewById(R.id.seg_archived),
                findViewById(R.id.seg_category)
        };
        for (int i = 0; i < segments.length; i++) {
            final int t = i;
            segments[i].setOnClickListener(v -> setTab(t));
        }

        btnSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
            hideKeyboard();
        });

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

        btnClearAll.setOnClickListener(v -> {
            // Category list views → this button becomes "+ New Category"
            if (tab == TAB_CATEGORY && selectedCategory == null) {
                showCreateCategoryDialog(null, KIND_GENERAL);
                return;
            }
            if (tab == TAB_FAV && showSubCategoryList) {
                showCreateCategoryDialog(null, KIND_FAV);
                return;
            }
            if (tab == TAB_ARCHIVE && showSubCategoryList) {
                showCreateCategoryDialog(null, KIND_ARCHIVE);
                return;
            }
            if (tab == TAB_RECENT) {
                Helper.showConfirm(this, "Clear recent", "Remove all recently opened bhajans?", "Clear", true, () -> {
                    Helper.clearRecents(this);
                    refresh();
                    Toast.makeText(this, "Recent cleared", Toast.LENGTH_SHORT).show();
                });
            } else if (tab == TAB_FAV) {
                Helper.showConfirm(this, "Clear favourites", "Remove all bhajans from your favourite list?", "Clear", true, () -> {
                    Helper.clearFavs(this);
                    refresh();
                    Toast.makeText(this, "Favourites cleared", Toast.LENGTH_SHORT).show();
                });
            } else if (tab == TAB_ARCHIVE) {
                Helper.showConfirm(this, "Unarchive all", "Restore all archived bhajans?", "Restore", true, () -> {
                    Helper.clearArchived(this);
                    refresh();
                    Toast.makeText(this, "All bhajans unarchived", Toast.LENGTH_SHORT).show();
                });
            }
        });

        if (savedInstanceState == null) applyIntent(getIntent());
        styleSegments();

        BhajanRepo.load(this, list -> {
            if (isFinishing() || isDestroyed()) return;
            all = list;
            refresh();
        });
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

    private void applyIntent(Intent in) {
        if (in == null) return;
        if (in.hasExtra(EXTRA_CATEGORY)) {
            String c = in.getStringExtra(EXTRA_CATEGORY);
            category = (c == null || !cats.contains(c)) ? "All" : c;
            categoryAdapter.setSelected(category);
        }
        if (in.hasExtra(EXTRA_TAB)) {
            tab = Math.max(0, Math.min(4, in.getIntExtra(EXTRA_TAB, TAB_ALL)));
            selectedCategory = null;
            selectedSubCategory = null;
            showSubCategoryList = (tab == TAB_FAV || tab == TAB_ARCHIVE);
            styleSegments();
        }
        if (in.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            etSearch.requestFocus();
            etSearch.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
            });
        }
        in.removeExtra(EXTRA_CATEGORY);
        in.removeExtra(EXTRA_TAB);
        in.removeExtra(EXTRA_FOCUS_SEARCH);
        refresh();
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t;
        selectedCategory = null;
        selectedSubCategory = null;
        showSubCategoryList = (t == TAB_FAV || t == TAB_ARCHIVE);
        styleSegments();
        refresh();
    }

    private void styleSegments() {
        for (int i = 0; i < segments.length; i++) {
            boolean on = i == tab;
            segments[i].setBackgroundResource(on ? R.drawable.bg_chip_selected : android.R.color.transparent);
            segments[i].setTextColor(getColor(on ? R.color.on_accent : R.color.text_secondary));
        }
    }

    private void refresh() {
        if (adapter == null) return;

        // ══ Category list views ══
        if ((tab == TAB_CATEGORY && selectedCategory == null)
                || (tab == TAB_FAV && showSubCategoryList)
                || (tab == TAB_ARCHIVE && showSubCategoryList)) {
            showCategoryList();
            return;
        }

        if (rvList.getAdapter() != adapter) rvList.setAdapter(adapter);

        btnClearAll.setText("Clear");
        btnClearAll.setTextColor(getColor(R.color.red));

        String q = etSearch.getText().toString().trim().toLowerCase();
        int recentLimit = Helper.getLibraryRecentLimit(this);

        List<Bhajan> base = new ArrayList<>();
        if (tab == TAB_RECENT) {
            List<String> recentIds = Helper.getRecentIds(this);
            if (recentIds.size() > recentLimit) recentIds = recentIds.subList(0, recentLimit);
            for (String id : recentIds) {
                Bhajan b = BhajanRepo.find(all, id);
                if (b != null) base.add(b);
            }
        } else if (tab == TAB_FAV) {
            if (selectedSubCategory == null) {
                for (Bhajan b : all) if (Helper.isFav(this, b.id)) base.add(b);
            } else {
                List<String> ids = Helper.getFavCategories(this).get(selectedSubCategory);
                if (ids == null) { selectedSubCategory = null; showSubCategoryList = true; refresh(); return; }
                for (String id : ids) {
                    Bhajan b = BhajanRepo.find(all, id);
                    if (b != null && Helper.isFav(this, b.id)) base.add(b);
                }
            }
        } else if (tab == TAB_ARCHIVE) {
            if (selectedSubCategory == null) {
                for (Bhajan b : all) if (Helper.isArchived(this, b.id)) base.add(b);
            } else {
                List<String> ids = Helper.getArchiveCategories(this).get(selectedSubCategory);
                if (ids == null) { selectedSubCategory = null; showSubCategoryList = true; refresh(); return; }
                for (String id : ids) {
                    Bhajan b = BhajanRepo.find(all, id);
                    if (b != null && Helper.isArchived(this, b.id)) base.add(b);
                }
            }
        } else if (tab == TAB_CATEGORY && selectedCategory != null) {
            List<String> ids = Helper.getBhajanCategories(this).get(selectedCategory);
            if (ids == null) { selectedCategory = null; refresh(); return; }
            for (String id : ids) {
                Bhajan b = BhajanRepo.find(all, id);
                if (b != null) base.add(b);
            }
        } else {
            for (Bhajan b : all) {
                if (!Helper.isArchived(this, b.id)) base.add(b);
            }
        }

        boolean customView = (tab == TAB_CATEGORY && selectedCategory != null)
                || ((tab == TAB_FAV || tab == TAB_ARCHIVE) && selectedSubCategory != null);

        List<Bhajan> out = new ArrayList<>();
        for (Bhajan b : base) {
            if (!customView && !BhajanRepo.inCategory(b, category)) continue;
            if (!BhajanRepo.matches(b, q)) continue;
            out.add(b);
        }

        adapter.update(out);

        boolean swipeEnabled = (tab == TAB_ALL || tab == TAB_RECENT);
        adapter.setSwipeArchiveEnabled(swipeEnabled);

        String noun = out.size() == 1 ? "bhajan" : "bhajans";
        if (customView) {
            String viewName = tab == TAB_CATEGORY ? selectedCategory : selectedSubCategory;
            tvCount.setText(out.size() + " " + noun + "  \u00B7  " + viewName);
        } else {
            String where = "All".equals(category) ? "" : "  \u00B7  " + category;
            tvCount.setText(out.size() + " " + noun + where);
        }

        boolean clearVisible = (tab == TAB_RECENT
                || ((tab == TAB_FAV || tab == TAB_ARCHIVE) && selectedSubCategory == null))
                && !base.isEmpty();
        btnClearAll.setVisibility(clearVisible ? View.VISIBLE : View.GONE);

        boolean empty = out.isEmpty() && !all.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvList.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            if (!q.isEmpty() || (!"All".equals(category) && !customView)) {
                tvEmptyTitle.setText("No bhajans found");
                tvEmptySub.setText("Try a different search or choose another deity.");
            } else if (tab == TAB_RECENT) {
                tvEmptyTitle.setText("Nothing opened yet");
                tvEmptySub.setText("Bhajans you read will show up here.");
            } else if (tab == TAB_FAV) {
                if (selectedSubCategory != null) {
                    tvEmptyTitle.setText("No bhajans here yet");
                    tvEmptySub.setText("Long press any bhajan and choose \u201CAdd to Favourite Category\u201D.");
                } else {
                    tvEmptyTitle.setText("No favourites yet");
                    tvEmptySub.setText("Tap the heart on any bhajan to save it here.");
                }
            } else if (tab == TAB_ARCHIVE) {
                if (selectedSubCategory != null) {
                    tvEmptyTitle.setText("No bhajans here yet");
                    tvEmptySub.setText("Long press any bhajan and choose \u201CAdd to Archive Category\u201D.");
                } else {
                    tvEmptyTitle.setText("No archived bhajans");
                    tvEmptySub.setText("Swipe a bhajan left or right in All or Recent to archive it.");
                }
            } else if (tab == TAB_CATEGORY) {
                tvEmptyTitle.setText("No bhajans here yet");
                tvEmptySub.setText("Long press any bhajan and choose \u201CAdd to Category\u201D.");
            } else {
                tvEmptyTitle.setText("No bhajans here");
                tvEmptySub.setText("Everything is archived. Check the Archived tab.");
            }
        }
    }

    private void showCategoryList() {
        if (rvList.getAdapter() != bhajanCategoryAdapter) rvList.setAdapter(bhajanCategoryAdapter);
        adapter.setSwipeArchiveEnabled(false);

        Map<String, List<String>> categoryMap;
        String allName = null;
        int allCount = 0;

        if (tab == TAB_FAV) {
            categoryMap = Helper.getFavCategories(this);
            allName = "All Favourites";
            allCount = Helper.favCount(this);
        } else if (tab == TAB_ARCHIVE) {
            categoryMap = Helper.getArchiveCategories(this);
            allName = "All Archived";
            allCount = Helper.archivedCount(this);
        } else {
            categoryMap = Helper.getBhajanCategories(this);
        }

        bhajanCategoryAdapter.setAllItem(allName, allCount);

        List<String> names = new ArrayList<>(categoryMap.keySet());
        Map<String, Integer> counts = new HashMap<>();
        for (String n : names) {
            List<String> ids = categoryMap.get(n);
            int c = 0;
            for (String id : ids) {
                if (tab == TAB_FAV) { if (Helper.isFav(this, id)) c++; }
                else if (tab == TAB_ARCHIVE) { if (Helper.isArchived(this, id)) c++; }
                else c++;
            }
            counts.put(n, c);
        }
        bhajanCategoryAdapter.update(names, counts);

        tvCount.setText(names.size() + (names.size() == 1 ? " category" : " categories"));

        btnClearAll.setText("+ New Category");
        btnClearAll.setTextColor(getColor(R.color.accent));
        btnClearAll.setVisibility(View.VISIBLE);

        boolean empty = names.isEmpty() && allName == null;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvList.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (empty) {
            tvEmptyTitle.setText("No categories yet");
            tvEmptySub.setText("Create a category to organise bhajans your way.");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LONG PRESS OPTIONS SHEET
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
        TextView sheetRemoveCategory = v.findViewById(R.id.sheet_remove_category);
        View sheetClose = v.findViewById(R.id.sheet_close);

        sheetTitle.setText(b.titleNepali);

        StringBuilder sub = new StringBuilder();
        if (b.type != null && !b.type.isEmpty()) sub.append(b.type);
        if (b.category != null && !b.category.isEmpty()) {
            if (sub.length() > 0) sub.append("  \u00B7  ");
            sub.append(BhajanCategories.nepaliFor(b.category));
        }
        if (sub.length() > 0) {
            sheetSub.setText(sub.toString());
            sheetSub.setVisibility(View.VISIBLE);
        } else {
            sheetSub.setVisibility(View.GONE);
        }

        // Add to the general custom category
        sheetCategory.setOnClickListener(x -> { dialog.dismiss(); showAddToCategoryDialog(b); });

        // Favourite: long press asks for a category (with a default option)
        if (fav) {
            sheetFav.setText("Remove from Favourites");
            sheetFav.setTextColor(getColor(R.color.accent));
            sheetFav.setOnClickListener(x -> { dialog.dismiss(); confirmRemoveFavourite(b); });
        } else {
            sheetFav.setText("Add to Favourite Category");
            sheetFav.setTextColor(getColor(R.color.text_primary));
            sheetFav.setOnClickListener(x -> { dialog.dismiss(); showAddToFavCategoryDialog(b); });
        }

        // Archive: long press asks for a category (with a default option)
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
            sheetArchive.setText("Add to Archive Category");
            sheetArchive.setOnClickListener(x -> { dialog.dismiss(); showAddToArchiveCategoryDialog(b); });
        }

        // Remove from Recent — whenever this bhajan is in recent history
        if (Helper.getRecentIds(this).contains(b.id)) {
            sheetRemoveRecent.setVisibility(View.VISIBLE);
            sheetRemoveRecent.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeRecent(this, b.id);
                Toast.makeText(this, "Removed from recent", Toast.LENGTH_SHORT).show();
                refresh();
            });
        }

        // Remove from category ONLY inside the general Category tab
        if (tab == TAB_CATEGORY && selectedCategory != null) {
            final String catName = selectedCategory;
            sheetRemoveCategory.setVisibility(View.VISIBLE);
            sheetRemoveCategory.setOnClickListener(x -> {
                dialog.dismiss();
                Helper.removeFromBhajanCategory(this, catName, b.id);
                Toast.makeText(this, "Removed from \u201C" + catName + "\u201D", Toast.LENGTH_SHORT).show();
                refresh();
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
                    refresh();
                });
    }

    // ═══════════════════════════════════════════════════════════
    // CATEGORY DIALOGS (general / favourite / archive)
    // ═══════════════════════════════════════════════════════════
    private void showAddToCategoryDialog(Bhajan b) {
        if (b == null) return;
        Map<String, List<String>> userCats = Helper.getBhajanCategories(this);
        List<String> names = new ArrayList<>(userCats.keySet());
        if (names.isEmpty()) { showCreateCategoryDialog(b, KIND_GENERAL); return; }
        names.add("+ Create new category");
        new AlertDialog.Builder(this)
                .setTitle("Add to Category")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == names.size() - 1) {
                        showCreateCategoryDialog(b, KIND_GENERAL);
                    } else {
                        Helper.addToBhajanCategory(this, names.get(which), b.id);
                        Toast.makeText(this, "Added to \u201C" + names.get(which) + "\u201D", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * Favourite picker:
     *  1. All Favourites (default)  → plain favourite, no custom category
     *  2. existing favourite categories
     *  3. + Create new category
     */
    private void showAddToFavCategoryDialog(Bhajan b) {
        if (b == null) return;
        Map<String, List<String>> favCats = Helper.getFavCategories(this);
        List<String> names = new ArrayList<>();
        names.add("All Favourites (default)");
        names.addAll(favCats.keySet());
        names.add("+ Create new category");
        new AlertDialog.Builder(this)
                .setTitle("Add to Favourite Category")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        // Default: simple favourite without a custom category
                        Helper.ensureFav(this, b.id);
                        Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if (which == names.size() - 1) {
                        showCreateCategoryDialog(b, KIND_FAV);
                    } else {
                        Helper.addToFavCategory(this, names.get(which), b.id);
                        Toast.makeText(this, "Added to favourites \u00B7 \u201C" + names.get(which) + "\u201D",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .show();
    }

    /**
     * Archive picker:
     *  1. All Archived (default)  → plain archive, no custom category
     *  2. existing archive categories
     *  3. + Create new category
     */
    private void showAddToArchiveCategoryDialog(Bhajan b) {
        if (b == null) return;
        Map<String, List<String>> archiveCats = Helper.getArchiveCategories(this);
        List<String> names = new ArrayList<>();
        names.add("All Archived (default)");
        names.addAll(archiveCats.keySet());
        names.add("+ Create new category");
        new AlertDialog.Builder(this)
                .setTitle("Add to Archive Category")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        // Default: simple archive without a custom category
                        Helper.ensureArchived(this, b.id);
                        Toast.makeText(this, "Bhajan archived", Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if (which == names.size() - 1) {
                        showCreateCategoryDialog(b, KIND_ARCHIVE);
                    } else {
                        Helper.addToArchiveCategory(this, names.get(which), b.id);
                        Toast.makeText(this, "Archived \u00B7 \u201C" + names.get(which) + "\u201D",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .show();
    }

    private void showCreateCategoryDialog(Bhajan bhajanToAdd, int kind) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_category_name, null);
        EditText etName = view.findViewById(R.id.et_category_name);
        String title = kind == KIND_FAV ? "New Favourite Category"
                : kind == KIND_ARCHIVE ? "New Archive Category" : "New Category";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = Helper.sanitizeCategoryName(etName.getText().toString());
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (kind == KIND_FAV) {
                        Helper.createFavCategory(this, name);
                        if (bhajanToAdd != null) {
                            Helper.addToFavCategory(this, name, bhajanToAdd.id);
                            Toast.makeText(this, "Added to favourites \u00B7 \u201C" + name + "\u201D",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Category created", Toast.LENGTH_SHORT).show();
                        }
                    } else if (kind == KIND_ARCHIVE) {
                        Helper.createArchiveCategory(this, name);
                        if (bhajanToAdd != null) {
                            Helper.addToArchiveCategory(this, name, bhajanToAdd.id);
                            Toast.makeText(this, "Archived \u00B7 \u201C" + name + "\u201D",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Category created", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Helper.createBhajanCategory(this, name);
                        if (bhajanToAdd != null) {
                            Helper.addToBhajanCategory(this, name, bhajanToAdd.id);
                            Toast.makeText(this, "Added to \u201C" + name + "\u201D", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Category created", Toast.LENGTH_SHORT).show();
                        }
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
        // Step 1: search focused OR has text
        if (etSearch.hasFocus() || etSearch.getText().length() > 0) {
            hideKeyboard();
            etSearch.clearFocus();
            root.requestFocus();
            if (etSearch.getText().length() > 0) etSearch.setText("");
            return;
        }
        // Step 2: inside a custom category (Category tab) → back to category list
        if (tab == TAB_CATEGORY && selectedCategory != null) {
            selectedCategory = null;
            refresh();
            return;
        }
        // Step 3: inside Favourites/Archived → back to their category list
        if ((tab == TAB_FAV || tab == TAB_ARCHIVE) && !showSubCategoryList) {
            showSubCategoryList = true;
            selectedSubCategory = null;
            refresh();
            return;
        }
        // Step 4: filtered → reset
        if (!"All".equals(category) || tab != TAB_ALL) {
            category = "All";
            categoryAdapter.setSelected("All");
            tab = TAB_ALL;
            selectedCategory = null;
            selectedSubCategory = null;
            showSubCategoryList = false;
            styleSegments();
            refresh();
            return;
        }
        super.onBackPressed();
    }
}