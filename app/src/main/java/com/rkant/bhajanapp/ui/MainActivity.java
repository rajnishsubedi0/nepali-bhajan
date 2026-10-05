package com.rkant.bhajanapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.BhajanAdapter;
import com.rkant.bhajanapp.adapter.CategoryAdapter;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.utils.Helper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private BhajanAdapter adapter;
    private RecyclerView rvCategories;
    private List<Bhajan> all = new ArrayList<>();
    private final List<String> cats = Arrays.asList("All", "Krishna", "Shiva", "Hari", "Ram", "Others");
    private String category = "All";
    private int currentTab = 0;
    private boolean backOnce = false;
    private long lastBack = 0;
    private long searchFocusGainedAt = 0;
    private final Handler backHandler = new Handler(Looper.getMainLooper());
    private View root, btnClear, btnClearAll;
    private EditText etSearch;
    private TextView tvCount, tvFavCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        etSearch = findViewById(R.id.et_search);
        btnClear = findViewById(R.id.btn_search_clear);
        btnClearAll = findViewById(R.id.btn_clear_all);
        tvCount = findViewById(R.id.tv_count);
        tvFavCount = findViewById(R.id.tv_fav_count);
        rvCategories = findViewById(R.id.rv_categories);
        root.requestFocus(); // search never auto-focuses

        RecyclerView rvList = findViewById(R.id.recyclerView);
        adapter = new BhajanAdapter(new ArrayList<>(), this);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(adapter);

        findViewById(R.id.btn_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { currentTab = t.getPosition(); refresh(); }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(new CategoryAdapter(cats, this, c -> { category = c; refresh(); }));

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
            hideKeyboard();
            refresh();
        });

        btnClearAll.setOnClickListener(v -> {
            if (currentTab == 1) {
                Helper.showConfirm(this, "Clear recent", "Remove all recently opened bhajans?", "Clear", true, () -> {
                    Helper.clearRecents(this); refresh();
                    Toast.makeText(this, "Recent cleared", Toast.LENGTH_SHORT).show();
                });
            } else if (currentTab == 2) {
                Helper.showConfirm(this, "Clear favourites", "Remove all bhajans from your favourite list?", "Clear", true, () -> {
                    Helper.clearFavs(this); refresh();
                    Toast.makeText(this, "Favourites cleared", Toast.LENGTH_SHORT).show();
                });
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                refresh();
            }
            public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) searchFocusGainedAt = System.currentTimeMillis(); });

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            if (!insets.isVisible(WindowInsetsCompat.Type.ime())) {
                v.postDelayed(this::dropFocusIfKeyboardHidden, 350);
            }
            return ViewCompat.onApplyWindowInsets(v, insets);
        });

        loadData();
    }

    @Override
    protected void onResume() { super.onResume(); root.requestFocus(); refresh(); }

    private void dropFocusIfKeyboardHidden() {
        WindowInsetsCompat ins = ViewCompat.getRootWindowInsets(root);
        boolean imeVisible = ins != null && ins.isVisible(WindowInsetsCompat.Type.ime());
        if (!imeVisible && etSearch.hasFocus() && System.currentTimeMillis() - searchFocusGainedAt > 700) {
            etSearch.clearFocus();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    private void loadData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONArray arr = new JSONArray(Helper.readRaw(this, R.raw.bhajan_list));
                List<Bhajan> temp = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    temp.add(new Bhajan(Helper.getJson(o, "id"), Helper.getJson(o, "bhajan_nepali"),
                            Helper.getJson(o, "bhajan_english"), Helper.getJson(o, "bhajan"), Helper.getJson(o, "bhajan_type")));
                }
                all = Helper.sortNepali(temp);
                runOnUiThread(this::refresh);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void refresh() {
        String q = etSearch.getText().toString().trim().toLowerCase();
        List<Bhajan> base = new ArrayList<>();
        if (currentTab == 0) {
            rvCategories.setVisibility(View.VISIBLE);
            for (Bhajan b : all)
                if (category.equals("All") || (b.category != null && b.category.equalsIgnoreCase(category))) base.add(b);
        } else {
            rvCategories.setVisibility(View.GONE);
            if (currentTab == 1) {
                for (String id : Helper.getRecentIds(this))
                    for (Bhajan b : all) if (b.id.equals(id)) { base.add(b); break; }
            } else {
                for (Bhajan b : all) if (Helper.isFav(this, b.id)) base.add(b);
            }
        }
        List<Bhajan> out = new ArrayList<>();
        for (Bhajan b : base)
            if (q.isEmpty() || (b.titleNepali != null && b.titleNepali.toLowerCase().contains(q))
                    || (b.titleEnglish != null && b.titleEnglish.toLowerCase().contains(q))) out.add(b);
        adapter.update(out);
        tvCount.setText(out.size() + " bhajans");
        tvFavCount.setText(Helper.favCount(this) + " favourites");

        boolean showClear = (currentTab == 1 || currentTab == 2);
        btnClearAll.setVisibility(showClear ? View.VISIBLE : View.GONE);
        tvFavCount.setVisibility(showClear ? View.GONE : View.VISIBLE);
    }

    // BACK: 1) close keyboard  2) clear search text  3) double-press exit (2s)
    @Override
    public void onBackPressed() {
        if (etSearch.hasFocus()) { hideKeyboard(); etSearch.clearFocus(); return; }
        if (etSearch.getText().length() > 0) { etSearch.setText(""); return; }
        long now = System.currentTimeMillis();
        if (backOnce && (now - lastBack) <= 2000) {
            super.onBackPressed();
        } else {
            backOnce = true; lastBack = now;
            Toast.makeText(this, "Double press to exit", Toast.LENGTH_SHORT).show();
            backHandler.postDelayed(() -> backOnce = false, 2000);
        }
    }
}