package com.rkant.bhajanapp.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.TextView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.ui.AudioListActivity;
import com.rkant.bhajanapp.ui.MainActivity;
import com.rkant.bhajanapp.ui.SettingsActivity;

/** Shared bottom navigation: Lyrics | Audio | Settings. Pass 0 or 1 for the current tab. */
public final class BottomNav {
    private BottomNav() {}

    private static final int[] ROWS = {R.id.nav_lyrics, R.id.nav_audio, R.id.nav_settings};
    private static final int[] ICONS = {R.id.nav_lyrics_icon, R.id.nav_audio_icon, R.id.nav_settings_icon};
    private static final int[] LABELS = {R.id.nav_lyrics_label, R.id.nav_audio_label, R.id.nav_settings_label};
    private static final Class<?>[] TARGETS = {MainActivity.class, AudioListActivity.class, SettingsActivity.class};

    public static void setup(Activity a, int selected) {
        for (int i = 0; i < ROWS.length; i++) {
            final int idx = i;
            int color = a.getColor(i == selected ? R.color.accent : R.color.text_secondary);
            ((ImageView) a.findViewById(ICONS[i])).setColorFilter(color);
            ((TextView) a.findViewById(LABELS[i])).setTextColor(color);
            a.findViewById(ROWS[i]).setOnClickListener(v -> {
                if (idx == selected) return;
                Intent in = new Intent(a, TARGETS[idx]);
                boolean tab = idx < 2;
                if (tab) in.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                a.startActivity(in);
                if (tab) a.overridePendingTransition(0, 0);
            });
        }
    }
}
