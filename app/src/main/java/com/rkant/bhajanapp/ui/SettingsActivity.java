package com.rkant.bhajanapp.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rkant.bhajanapp.BuildConfig;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.utils.BottomNav;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.BatteryHelper;

/** Settings tab. Every change is applied and saved immediately. */
public class SettingsActivity extends AppCompatActivity {

    private int themeMode, timeout, listScale, lyricScale, recentLimit, audioRecentLimit;
    private TextView[] themeBtns, timeoutBtns;
    private TextView tvListSize, tvLyricSize, tvPreview, tvHistSize, tvHistAudioSize;
    private final int[] timeoutValues = {1, 3, 5, 10, -1};

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
        recentLimit = Helper.getRecentLimit(this);
        audioRecentLimit = Helper.getAudioRecentLimit(this);

        tvListSize = findViewById(R.id.tv_list_size);
        tvLyricSize = findViewById(R.id.tv_lyric_size);
        tvPreview = findViewById(R.id.tv_lyric_preview);
        tvHistSize = findViewById(R.id.tv_hist_size);
        tvHistAudioSize = findViewById(R.id.tv_hist_audio_size);

        ((TextView) findViewById(R.id.tv_version)).setText("Version " + BuildConfig.VERSION_NAME);

        themeBtns = new TextView[]{findViewById(R.id.btn_system), findViewById(R.id.btn_light), findViewById(R.id.btn_dark)};
        timeoutBtns = new TextView[]{findViewById(R.id.btn_t1), findViewById(R.id.btn_t3), findViewById(R.id.btn_t5), findViewById(R.id.btn_t10), findViewById(R.id.btn_talways)};

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

        // Recent history size steppers (Library + Audio)
        findViewById(R.id.btn_hist_minus).setOnClickListener(v -> { recentLimit = clampLimit(recentLimit - 5); Helper.setRecentLimit(this, recentLimit); update(); });
        findViewById(R.id.btn_hist_plus).setOnClickListener(v -> { recentLimit = clampLimit(recentLimit + 5); Helper.setRecentLimit(this, recentLimit); update(); });
        findViewById(R.id.btn_hist_audio_minus).setOnClickListener(v -> { audioRecentLimit = clampLimit(audioRecentLimit - 5); Helper.setAudioRecentLimit(this, audioRecentLimit); update(); });
        findViewById(R.id.btn_hist_audio_plus).setOnClickListener(v -> { audioRecentLimit = clampLimit(audioRecentLimit + 5); Helper.setAudioRecentLimit(this, audioRecentLimit); update(); });

        findViewById(R.id.btn_clear_recent).setOnClickListener(v ->
                Helper.showConfirm(this, "Clear reading history", "Remove all recently opened bhajans?", "Clear", true, () -> {
                    Helper.clearRecents(this);
                    Toast.makeText(this, "Reading history cleared", Toast.LENGTH_SHORT).show();
                }));
        findViewById(R.id.btn_clear_favs).setOnClickListener(v ->
                Helper.showConfirm(this, "Clear favourites", "Remove all bhajans from your favourite list?", "Clear", true, () -> {
                    Helper.clearFavs(this);
                    Toast.makeText(this, "Favourites cleared", Toast.LENGTH_SHORT).show();
                }));

        update();
    }

    private int clamp(int s) { return Math.max(0, Math.min(4, s)); }
    private int clampLimit(int v) { return Math.max(5, Math.min(50, v)); }

    private void update() {
        for (int i = 0; i < 3; i++) style(themeBtns[i], themeMode == i);
        for (int i = 0; i < timeoutBtns.length; i++) style(timeoutBtns[i], timeout == timeoutValues[i]);
        tvListSize.setText(Helper.scaleName(listScale) + " (" + (int) Helper.scaleSp(listScale, false) + "sp)");
        tvLyricSize.setText(Helper.scaleName(lyricScale) + " (" + (int) Helper.scaleSp(lyricScale, true) + "sp)");
        tvPreview.setTextSize(Helper.scaleSp(lyricScale, true));
        tvHistSize.setText(recentLimit + " items");
        tvHistAudioSize.setText(audioRecentLimit + " items");
    }

    private void style(TextView v, boolean selected) {
        v.setBackgroundResource(selected ? R.drawable.bg_chip_selected : android.R.color.transparent);
        v.setTextColor(getColor(selected ? R.color.on_accent : R.color.text_secondary));
    }
}
