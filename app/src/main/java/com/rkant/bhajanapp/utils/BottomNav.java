package com.rkant.bhajanapp.utils;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.ui.AudioListActivity;
import com.rkant.bhajanapp.ui.LibraryActivity;
import com.rkant.bhajanapp.ui.MainActivity;
import com.rkant.bhajanapp.ui.SettingsActivity;

/**
 * Shared bottom navigation: Home | Library | Audio | Settings.
 * Pass 0..3 for the tab that is currently showing.
 */
public final class BottomNav {
    private BottomNav() {}

    public static final int HOME = 0;
    public static final int LIBRARY = 1;
    public static final int AUDIO = 2;
    public static final int SETTINGS = 3;

    private static final int[] ROWS = {R.id.nav_home, R.id.nav_lyrics, R.id.nav_audio, R.id.nav_settings};
    private static final int[] PILLS = {R.id.nav_home_pill, R.id.nav_lyrics_pill, R.id.nav_audio_pill, R.id.nav_settings_pill};
    private static final int[] ICONS = {R.id.nav_home_icon, R.id.nav_lyrics_icon, R.id.nav_audio_icon, R.id.nav_settings_icon};
    private static final int[] LABELS = {R.id.nav_home_label, R.id.nav_lyrics_label, R.id.nav_audio_label, R.id.nav_settings_label};
    private static final Class<?>[] TARGETS = {
            MainActivity.class, LibraryActivity.class, AudioListActivity.class, SettingsActivity.class};

    public static void setup(Activity a, int selected) {
        for (int i = 0; i < ROWS.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            int color = a.getColor(on ? R.color.accent : R.color.text_secondary);
            ((ImageView) a.findViewById(ICONS[i])).setColorFilter(color);
            ((TextView) a.findViewById(LABELS[i])).setTextColor(color);
            View pill = a.findViewById(PILLS[i]);
            pill.setBackgroundResource(on ? R.drawable.bg_nav_active : android.R.color.transparent);
            a.findViewById(ROWS[i]).setOnClickListener(v -> {
                if (idx == selected) return;
                open(a, idx);
            });
        }
    }

    /** Opens a tab. An existing instance is reused and everything above it is cleared. */
    public static void open(Activity a, int tab) {
        Intent in = new Intent(a, TARGETS[tab]);
        in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        a.startActivity(in);
        a.overridePendingTransition(0, 0);
    }
}
