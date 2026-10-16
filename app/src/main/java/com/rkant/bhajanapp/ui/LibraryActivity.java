package com.rkant.bhajanapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.BhajanAdapter;
import com.rkant.bhajanapp.adapter.CategoryAdapter;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.utils.BhajanRepo;
import com.rkant.bhajanapp.utils.BottomNav;
import com.rkant.bhajanapp.utils.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Redesigned Library tab: search, deity chips, All / Recent / Favourites.
 * Recent and Favourites counts are controlled by Settings.
 */
public class LibraryActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY = "CATEGORY";
    public static final String EXTRA_TAB = "TAB";
    public static final String EXTRA_FOCUS_SEARCH = "FOCUS_SEARCH";

    public static final int TAB_ALL = 0;
    public static final int TAB_RECENT = 1;
    public static final int TAB_FAV = 2;

    private final List<String> cats = Arrays.asList("All", "Krishna", "Shiva", "Hari", "Ram", "Others");

    private List<Bhajan> all = new ArrayList<>();
    private String category = "All";
    private int tab = TAB_ALL;

    private BhajanAdapter adapter;
    private CategoryAdapter categoryAdapter;
    private RecyclerView rvList;
    private View root, btnSearchClear, btnClearAll, emptyState;
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
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);

        RecyclerView rvCategories = findViewById(R.id.rv_categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(cats, this, c -> { category = c; refresh(); });
        rvCategories.setAdapter(categoryAdapter);

        segments = new TextView[]{
            findViewById(R.id.seg_all),
            findViewById(R.id.seg_recent),
            findViewById(R.id.seg_fav)
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
            tab = Math.max(0, Math.min(2, in.getIntExtra(EXTRA_TAB, TAB_ALL)));
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
            for (Bhajan b : all) if (Helper.isFav(this, b.id)) base.add(b);
        } else {
            base.addAll(all);
        }

        List<Bhajan> out = new ArrayList<>();
        for (Bhajan b : base) {
            if (BhajanRepo.inCategory(b, category) && BhajanRepo.matches(b, q)) out.add(b);
        }

        adapter.update(out);

        String noun = out.size() == 1 ? "bhajan" : "bhajans";
        String where = "All".equals(category) ? "" : "  \u00B7  " + category;
        tvCount.setText(out.size() + " " + noun + where);

        btnClearAll.setVisibility((tab == TAB_RECENT || tab == TAB_FAV) && !base.isEmpty() ? View.VISIBLE : View.GONE);

        boolean empty = out.isEmpty() && !all.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvList.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        if (empty) {
            if (!q.isEmpty() || !"All".equals(category)) {
                tvEmptyTitle.setText("No bhajans found");
                tvEmptySub.setText("Try a different search or choose another deity.");
            } else if (tab == TAB_RECENT) {
                tvEmptyTitle.setText("Nothing opened yet");
                tvEmptySub.setText("Bhajans you read will show up here.");
            } else {
                tvEmptyTitle.setText("No favourites yet");
                tvEmptySub.setText("Tap the heart on any bhajan to save it here.");
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (etSearch.hasFocus()) { hideKeyboard(); etSearch.clearFocus(); root.requestFocus(); return; }
        if (etSearch.getText().length() > 0) { etSearch.setText(""); return; }
        if (!"All".equals(category) || tab != TAB_ALL) {
            category = "All";
            categoryAdapter.setSelected("All");
            tab = TAB_ALL;
            styleSegments();
            refresh();
            return;
        }
        super.onBackPressed();
    }
}
