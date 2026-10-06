package com.rkant.bhajanapp.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.BatteryHelper;

public class SettingsActivity extends AppCompatActivity {
    private int themeMode, timeout, listScale, lyricScale;
    private TextView[] themeBtns, timeoutBtns;
    private TextView tvListSize, tvLyricSize;
    private final int[] timeoutValues = {1, 3, 5, 10, -1};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        themeMode = Helper.getThemeMode(this);
        timeout = Helper.getScreenTimeout(this);
        listScale = Helper.getListFontScale(this);
        lyricScale = Helper.getLyricFontScale(this);
        tvListSize = findViewById(R.id.tv_list_size);
        tvLyricSize = findViewById(R.id.tv_lyric_size);


        themeBtns = new TextView[]{findViewById(R.id.btn_system), findViewById(R.id.btn_light), findViewById(R.id.btn_dark)};
        timeoutBtns = new TextView[]{findViewById(R.id.btn_t1), findViewById(R.id.btn_t3), findViewById(R.id.btn_t5), findViewById(R.id.btn_t10), findViewById(R.id.btn_talways)};

        for (int i = 0; i < 3; i++) { int m = i; themeBtns[i].setOnClickListener(v -> { themeMode = m; update(); }); }
        for (int i = 0; i < timeoutBtns.length; i++) { int t = timeoutValues[i]; timeoutBtns[i].setOnClickListener(v -> { timeout = t; update(); }); }

        findViewById(R.id.btn_battery).setOnClickListener(v -> BatteryHelper.showGuide(this));
        findViewById(R.id.btn_list_minus).setOnClickListener(v -> { listScale = clamp(listScale - 1); update(); });
        findViewById(R.id.btn_list_plus).setOnClickListener(v -> { listScale = clamp(listScale + 1); update(); });
        findViewById(R.id.btn_lyric_minus).setOnClickListener(v -> { lyricScale = clamp(lyricScale - 1); update(); });
        findViewById(R.id.btn_lyric_plus).setOnClickListener(v -> { lyricScale = clamp(lyricScale + 1); update(); });

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            Helper.setThemeMode(this, themeMode);
            Helper.setScreenTimeout(this, timeout);
            Helper.setListFontScale(this, listScale);
            Helper.setLyricFontScale(this, lyricScale);
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        update();
    }

    private int clamp(int s) { return Math.max(0, Math.min(4, s)); }

    private void update() {
        for (int i = 0; i < 3; i++) style(themeBtns[i], themeMode == i);
        for (int i = 0; i < timeoutBtns.length; i++) style(timeoutBtns[i], timeout == timeoutValues[i]);
        tvListSize.setText(Helper.scaleName(listScale, false) + " (" + (int) Helper.scaleSp(listScale, false) + "sp)");
        tvLyricSize.setText(Helper.scaleName(lyricScale, true) + " (" + (int) Helper.scaleSp(lyricScale, true) + "sp)");
    }

    private void style(TextView v, boolean selected) {
        v.setBackgroundResource(selected ? R.drawable.bg_button_accent : android.R.color.transparent);
        v.setTextColor(getColor(selected ? R.color.on_accent : R.color.text_secondary));
    }
}