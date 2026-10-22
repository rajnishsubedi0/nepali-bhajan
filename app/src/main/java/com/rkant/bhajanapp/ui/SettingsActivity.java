
package com.rkant.bhajanapp.ui;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rkant.bhajanapp.BuildConfig;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.utils.BottomNav;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.BatteryHelper;

public class SettingsActivity extends AppCompatActivity {

    private int themeMode, timeout, listScale, lyricScale;
    private int libraryRecentLimit, audioRecentLimit;
    private TextView[] themeBtns, timeoutBtns;
    private TextView tvListSize, tvLyricSize, tvPreview;
    private TextView tvLibraryRecentValue, tvAudioRecentValue;
    private Switch switchArchivedSearch;
    private Switch switchArchivedInList;
    private Switch switchAudioArchivedSearch;
    private Switch switchAudioArchivedInList;

    private final int[] timeoutValues = {1, 3, 5, 10, -1};
    private final int[] recentLimitOptions = {5, 10, 15, 20, 25, 30};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        BottomNav.setup(this, BottomNav.SETTINGS);

        themeMode = Helper.getThemeMode(this);
        timeout = Helper.getScreenTimeout(this);
        listScale = Helper.getListFontScale(this);
        lyricScale = Helper.getLyricFontScale(this);
        libraryRecentLimit = Helper.getLibraryRecentLimit(this);
        audioRecentLimit = Helper.getAudioRecentLimit(this);

        tvListSize = findViewById(R.id.tv_list_size);
        tvLyricSize = findViewById(R.id.tv_lyric_size);
        tvPreview = findViewById(R.id.tv_lyric_preview);
        tvLibraryRecentValue = findViewById(R.id.tv_library_recent_value);
        tvAudioRecentValue = findViewById(R.id.tv_audio_recent_value);
        switchArchivedSearch = findViewById(R.id.switch_archived_search);
        switchArchivedInList = findViewById(R.id.switch_archived_in_list);
        switchAudioArchivedSearch = findViewById(R.id.switch_audio_archived_search);
        switchAudioArchivedInList = findViewById(R.id.switch_audio_archived_in_list);

        ((TextView) findViewById(R.id.tv_version)).setText("Version " + BuildConfig.VERSION_NAME);

        themeBtns = new TextView[]{
                findViewById(R.id.btn_system), findViewById(R.id.btn_light), findViewById(R.id.btn_dark)
        };
        timeoutBtns = new TextView[]{
                findViewById(R.id.btn_t1), findViewById(R.id.btn_t3), findViewById(R.id.btn_t5),
                findViewById(R.id.btn_t10), findViewById(R.id.btn_talways)
        };

        for (int i = 0; i < 3; i++) {
            int m = i;
            themeBtns[i].setOnClickListener(v -> {
                if (themeMode == m) return;
                themeMode = m;
                Helper.setThemeMode(this, m);
                update();
                Helper.applyTheme(this);
            });
        }

        for (int i = 0; i < timeoutBtns.length; i++) {
            int t = timeoutValues[i];
            timeoutBtns[i].setOnClickListener(v -> { timeout = t; Helper.setScreenTimeout(this, t); update(); });
        }

        findViewById(R.id.btn_battery).setOnClickListener(v -> BatteryHelper.showGuide(this));

        findViewById(R.id.btn_list_minus).setOnClickListener(v -> { listScale = clamp(listScale - 1); Helper.setListFontScale(this, listScale); update(); });
        findViewById(R.id.btn_list_plus).setOnClickListener(v -> { listScale = clamp(listScale + 1); Helper.setListFontScale(this, listScale); update(); });
        findViewById(R.id.btn_lyric_minus).setOnClickListener(v -> { lyricScale = clamp(lyricScale - 1); Helper.setLyricFontScale(this, lyricScale); update(); });
        findViewById(R.id.btn_lyric_plus).setOnClickListener(v -> { lyricScale = clamp(lyricScale + 1); Helper.setLyricFontScale(this, lyricScale); update(); });

        findViewById(R.id.btn_library_recent_minus).setOnClickListener(v -> {
            libraryRecentLimit = cycleRecentLimit(libraryRecentLimit, -1);
            Helper.setLibraryRecentLimit(this, libraryRecentLimit);
            update();
        });
        findViewById(R.id.btn_library_recent_plus).setOnClickListener(v -> {
            libraryRecentLimit = cycleRecentLimit(libraryRecentLimit, 1);
            Helper.setLibraryRecentLimit(this, libraryRecentLimit);
            update();
        });
        findViewById(R.id.btn_audio_recent_minus).setOnClickListener(v -> {
            audioRecentLimit = cycleRecentLimit(audioRecentLimit, -1);
            Helper.setAudioRecentLimit(this, audioRecentLimit);
            update();
        });
        findViewById(R.id.btn_audio_recent_plus).setOnClickListener(v -> {
            audioRecentLimit = cycleRecentLimit(audioRecentLimit, 1);
            Helper.setAudioRecentLimit(this, audioRecentLimit);
            update();
        });

        // ── Bhajan (Library) archive settings ──
        switchArchivedSearch.setChecked(Helper.isArchivedSearchable(this));
        switchArchivedSearch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Helper.setArchivedSearchable(this, isChecked);
            Toast.makeText(this, isChecked ? "Archived bhajans will appear in search" : "Archived bhajans hidden from search",
                    Toast.LENGTH_SHORT).show();
        });
        switchArchivedInList.setChecked(Helper.isArchivedInListEnabled(this));
        switchArchivedInList.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Helper.setArchivedInListEnabled(this, isChecked);
            Toast.makeText(this, isChecked
                            ? "Archived bhajans will stay visible in the list"
                            : "Archived bhajans will be hidden from the list",
                    Toast.LENGTH_SHORT).show();
        });

        // ── Audio archive settings (mirror of the Library archive settings) ──
        switchAudioArchivedInList.setChecked(Helper.isAudioArchivedInListEnabled(this));
        switchAudioArchivedInList.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Helper.setAudioArchivedInListEnabled(this, isChecked);
            Toast.makeText(this, isChecked
                            ? "Archived tracks will stay visible in the All list"
                            : "Archived tracks will be hidden from the All list",
                    Toast.LENGTH_SHORT).show();
        });
        switchAudioArchivedSearch.setChecked(Helper.isAudioArchivedSearchable(this));
        switchAudioArchivedSearch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Helper.setAudioArchivedSearchable(this, isChecked);
            Toast.makeText(this, isChecked
                            ? "Archived tracks will appear in search"
                            : "Archived tracks hidden from search",
                    Toast.LENGTH_SHORT).show();
        });

        update();
    }

    private int cycleRecentLimit(int current, int direction) {
        int idx = 1;
        for (int i = 0; i < recentLimitOptions.length; i++) {
            if (recentLimitOptions[i] == current) { idx = i; break; }
        }
        int next = idx + direction;
        if (next < 0) next = 0;
        if (next >= recentLimitOptions.length) next = recentLimitOptions.length - 1;
        return recentLimitOptions[next];
    }

    private int clamp(int s) { return Math.max(0, Math.min(4, s)); }

    private void update() {
        for (int i = 0; i < 3; i++) style(themeBtns[i], themeMode == i);
        for (int i = 0; i < timeoutBtns.length; i++) style(timeoutBtns[i], timeout == timeoutValues[i]);
        tvListSize.setText(Helper.scaleName(listScale) + " (" + (int) Helper.scaleSp(listScale, false) + "sp)");
        tvLyricSize.setText(Helper.scaleName(lyricScale) + " (" + (int) Helper.scaleSp(lyricScale, true) + "sp)");
        tvPreview.setTextSize(Helper.scaleSp(lyricScale, true));
        tvLibraryRecentValue.setText(libraryRecentLimit + " items");
        tvAudioRecentValue.setText(audioRecentLimit + " items");
    }

    private void style(TextView v, boolean selected) {
        v.setBackgroundResource(selected ? R.drawable.bg_chip_selected : android.R.color.transparent);
        v.setTextColor(getColor(selected ? R.color.on_accent : R.color.text_secondary));
    }
}