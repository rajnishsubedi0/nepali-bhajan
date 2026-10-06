package com.rkant.bhajanapp.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Helper {
    private static final String PREFS = "gpt_prefs";
    private static final String FAVS = "favs";
    private static final String RECENT = "recent";
    private static final String THEME = "theme_mode";
    private static final String TIMEOUT = "screen_timeout";
    public static final int MODE_SYSTEM = 0, MODE_LIGHT = 1, MODE_DARK = 2;
    public static String scaleName(int scale) { return SIZE_NAMES[clamp(scale)]; }
    public static float scaleSp(int scale, boolean lyric) { return (lyric ? LYRIC_SIZES : LIST_SIZES)[clamp(scale)]; }

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean isFav(Context c, String id) {
        Set<String> f = p(c).getStringSet(FAVS, new HashSet<>());
        return f != null && f.contains(id);
    }
    public static void toggleFav(Context c, String id) {
        Set<String> f = new HashSet<>(p(c).getStringSet(FAVS, new HashSet<>()));
        if (f.contains(id)) f.remove(id); else f.add(id);
        p(c).edit().putStringSet(FAVS, f).apply();
    }
    public static int favCount(Context c) { return p(c).getStringSet(FAVS, new HashSet<>()).size(); }
    public static void clearFavs(Context c) { p(c).edit().remove(FAVS).apply(); }

    // --- Recents (max 10) ---
    public static void addRecent(Context c, String id) {
        List<String> ids = new ArrayList<>(Arrays.asList(getRecentIds(c)));
        ids.remove(id); ids.add(0, id);
        if (ids.size() > 10) ids = ids.subList(0, 10);
        p(c).edit().putString(RECENT, String.join(",", ids)).apply();
    }
    public static String[] getRecentIds(Context c) {
        String s = p(c).getString(RECENT, "");
        return s.isEmpty() ? new String[0] : s.split(",");
    }
    public static void clearRecents(Context c) { p(c).edit().remove(RECENT).apply(); }

    // --- Theme ---
    public static int getThemeMode(Context c) { return p(c).getInt(THEME, MODE_SYSTEM); }
    public static void setThemeMode(Context c, int mode) { p(c).edit().putInt(THEME, mode).apply(); applyTheme(c); }
    public static void applyTheme(Context c) {
        int m = getThemeMode(c);
        AppCompatDelegate.setDefaultNightMode(m == MODE_LIGHT ? AppCompatDelegate.MODE_NIGHT_NO :
                m == MODE_DARK ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }


    public static int getScreenTimeout(Context c) { return p(c).getInt(TIMEOUT, 3); }
    public static void setScreenTimeout(Context c, int mins) { p(c).edit().putInt(TIMEOUT, mins).apply(); }


    private static final String FONT_LIST = "font_scale_list";
    private static final String FONT_LYRIC = "font_scale_lyric";
    private static final float[] LIST_SIZES  = {14f, 16f, 19f, 22f, 26f};
    private static final float[] NUM_SIZES   = {12f, 14f, 16f, 19f, 22f};
    private static final float[] LYRIC_SIZES = {16f, 18f, 21f, 25f, 30f};
    private static final String[] SIZE_NAMES = {"XS", "Small", "Medium", "Large", "XL"};

    private static int clamp(int s) { return Math.max(0, Math.min(4, s)); }
    public static int getListFontScale(Context c) { return clamp(p(c).getInt(FONT_LIST, 2)); }
    public static int getLyricFontScale(Context c) { return clamp(p(c).getInt(FONT_LYRIC, 2)); }
    public static void setListFontScale(Context c, int s) { p(c).edit().putInt(FONT_LIST, clamp(s)).apply(); }
    public static void setLyricFontScale(Context c, int s) { p(c).edit().putInt(FONT_LYRIC, clamp(s)).apply(); }
    public static float listTitleSize(Context c) { return LIST_SIZES[getListFontScale(c)]; }
    public static float listNumberSize(Context c) { return NUM_SIZES[getListFontScale(c)]; }
    public static float lyricSize(Context c) { return LYRIC_SIZES[getLyricFontScale(c)]; }

    // --- JSON ---
    public static String readRaw(Context c, int resId) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getResources().openRawResource(resId), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) { return "[]"; }
    }
    public static String getJson(JSONObject o, String key) {
        String v = o.optString(key);
        if (v.isEmpty()) v = o.optString(key + " ");
        return v.trim();
    }
    public static String getYouTubeLink(Context c, String id) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(readRaw(c, c.getResources().getIdentifier("youtube_link", "raw", c.getPackageName())));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (id.equals(getJson(o, "id"))) return getJson(o, "link");
            }
        } catch (Exception e) {}
        return null;
    }

    public static String getNepaliNumber(int n) {
        String[] d = {"०","१","२","३","४","५","६","७","८","९"};
        StringBuilder sb = new StringBuilder();
        for (char ch : String.valueOf(n).toCharArray()) sb.append(d[ch - '0']);
        return sb.toString();
    }

    public static List<Bhajan> sortNepali(List<Bhajan> list) {
        String[] order = {"अ","आ","इ","ई","उ","ऊ","ऋ","ए","ऐ","ओ","औ","अं","अः","क","ख","ग","घ","ङ","च","छ","ज","झ","ञ","ट","ठ","ड","ढ","ण","त","थ","द","ध","न","प","फ","ब","भ","म","य","र","ल","व","श","ष","स","ह","क्ष","त्र","ज्ञ"};
        List<Bhajan> sorted = new ArrayList<>();
        for (String l : order) for (Bhajan b : list)
            if (b.titleNepali != null && b.titleNepali.trim().startsWith(l)) sorted.add(b);
        for (Bhajan b : list) if (!sorted.contains(b)) sorted.add(b);
        return sorted;
    }

    // --- Generic confirmation dialog ---
    public static void showConfirm(Context c, String title, String message, String confirmLabel, boolean destructive, Runnable onConfirm) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_confirm);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) d.findViewById(R.id.dialog_title)).setText(title);
        ((TextView) d.findViewById(R.id.dialog_message)).setText(message);
        MaterialButton confirm = d.findViewById(R.id.btn_confirm);
        confirm.setText(confirmLabel);
        confirm.setBackgroundTintList(ColorStateList.valueOf(c.getColor(destructive ? R.color.red : R.color.accent)));
        confirm.setTextColor(c.getColor(R.color.on_accent));
        d.findViewById(R.id.btn_cancel).setOnClickListener(v -> d.dismiss());
        confirm.setOnClickListener(v -> { d.dismiss(); onConfirm.run(); });
        d.show();
    }

    public static String formatTime(long ms) {
        if (ms <= 0 || ms == androidx.media3.common.C.TIME_UNSET) return "--:--";
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = ms / (1000 * 60 * 60);
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%02d:%02d", minutes, seconds);
    }
}