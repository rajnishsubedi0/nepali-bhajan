package com.rkant.bhajanapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Helper {

    private static final String PREF_SETTINGS = "bhajan_settings";
    private static final String PREF_DATA = "bhajan_data";
    private static final String PREF_PLAYLISTS = "bhajan_playlists";

    // ═══════════════════════════════════════════════════════════
    // THEME
    // ═══════════════════════════════════════════════════════════

    public static void applyTheme(Context ctx) {
        int mode = getThemeMode(ctx);
        if (mode == 1) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if (mode == 2) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static int getThemeMode(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).getInt("theme_mode", 0);
    }

    public static void setThemeMode(Context ctx, int mode) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).edit().putInt("theme_mode", mode).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // SCREEN TIMEOUT
    // ═══════════════════════════════════════════════════════════

    public static int getScreenTimeout(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).getInt("screen_timeout", 3);
    }

    public static void setScreenTimeout(Context ctx, int timeout) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).edit().putInt("screen_timeout", timeout).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // RECENT LIST SIZE SETTINGS
    // ═══════════════════════════════════════════════════════════

    public static int getLibraryRecentLimit(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).getInt("library_recent_limit", 10);
    }

    public static void setLibraryRecentLimit(Context ctx, int limit) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).edit().putInt("library_recent_limit", limit).apply();
    }

    public static int getAudioRecentLimit(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).getInt("audio_recent_limit", 10);
    }

    public static void setAudioRecentLimit(Context ctx, int limit) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).edit().putInt("audio_recent_limit", limit).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // ARCHIVED BHAJANS
    // ═══════════════════════════════════════════════════════════

    public static void toggleArchive(Context ctx, String id) {
        if (id == null) return;
        Set<String> set = new HashSet<>(getArchivedSet(ctx));
        boolean removing = set.contains(id);
        if (removing) set.remove(id);
        else set.add(id);
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().putStringSet("archived", set).apply();
        // Sync: unarchiving also removes it from every archive sub-category
        if (removing) removeIdFromAllSubCategories(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order", id);
    }

    public static boolean isArchived(Context ctx, String id) {
        if (id == null) return false;
        return getArchivedSet(ctx).contains(id);
    }

    public static void unarchive(Context ctx, String id) {
        if (id == null) return;
        Set<String> set = new HashSet<>(getArchivedSet(ctx));
        set.remove(id);
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().putStringSet("archived", set).apply();
        // Sync: unarchiving also removes it from every archive sub-category
        removeIdFromAllSubCategories(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order", id);
    }

    public static void clearArchived(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().remove("archived").apply();
        clearAllSubCategoryContents(ctx, PREF_ARCHIVE_CATEGORIES);
    }
    private static Set<String> getArchivedSet(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .getStringSet("archived", new HashSet<>()));
    }

    public static int archivedCount(Context ctx) { return getArchivedSet(ctx).size(); }

    // ═══════════════════════════════════════════════════════════
    // SEARCH ARCHIVED SETTING
    // ═══════════════════════════════════════════════════════════

    public static boolean isArchivedSearchable(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .getBoolean("archived_searchable", false);
    }

    public static void setArchivedSearchable(Context ctx, boolean value) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .edit().putBoolean("archived_searchable", value).apply();
    }

    // ═══════════════════════════════════════════════════════════
    // FONT SCALE
    // ═══════════════════════════════════════════════════════════

    public static int getListFontScale(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).getInt("list_font_scale", 2);
    }

    public static void setListFontScale(Context ctx, int scale) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).edit().putInt("list_font_scale", scale).apply();
    }

    public static int getLyricFontScale(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).getInt("lyric_font_scale", 2);
    }

    public static void setLyricFontScale(Context ctx, int scale) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE).edit().putInt("lyric_font_scale", scale).apply();
    }

    public static String scaleName(int scale) {
        switch (scale) {
            case 0: return "Extra Small";
            case 1: return "Small";
            case 3: return "Large";
            case 4: return "Extra Large";
            default: return "Medium";
        }
    }

    public static float scaleSp(int scale, boolean lyric) {
        float base = lyric ? 21f : 19f;
        switch (scale) {
            case 0: return base - 4f;
            case 1: return base - 2f;
            case 3: return base + 2f;
            case 4: return base + 4f;
            default: return base;
        }
    }

    public static float listNumberSize(Context ctx) {
        float size = scaleSp(getListFontScale(ctx), false) - 3f;
        return Math.max(12f, size);
    }

    public static float listTitleSize(Context ctx) { return scaleSp(getListFontScale(ctx), false); }

    public static float lyricSize(Context ctx) { return scaleSp(getLyricFontScale(ctx), true); }

    // ═══════════════════════════════════════════════════════════
    // TIME FORMAT
    // ═══════════════════════════════════════════════════════════

    public static String formatTime(long ms) {
        if (ms < 0) return "--:--";
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        else return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    // ═══════════════════════════════════════════════════════════
    // NEPALI NUMBER
    // ═══════════════════════════════════════════════════════════

    public static String getNepaliNumber(int number) {
        String english = String.valueOf(number);
        StringBuilder builder = new StringBuilder();
        for (char c : english.toCharArray()) {
            switch (c) {
                case '0': builder.append("\u0966"); break;
                case '1': builder.append("\u0967"); break;
                case '2': builder.append("\u0968"); break;
                case '3': builder.append("\u0969"); break;
                case '4': builder.append("\u096A"); break;
                case '5': builder.append("\u096B"); break;
                case '6': builder.append("\u096C"); break;
                case '7': builder.append("\u096D"); break;
                case '8': builder.append("\u096E"); break;
                case '9': builder.append("\u096F"); break;
                default: builder.append(c); break;
            }
        }
        return builder.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // RAW FILE / JSON HELPERS
    // ═══════════════════════════════════════════════════════════

    public static String readRaw(Context ctx, int rawId) {
        StringBuilder builder = new StringBuilder();
        try {
            InputStream is = ctx.getResources().openRawResource(rawId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            reader.close();
        } catch (Exception e) { e.printStackTrace(); }
        return builder.toString();
    }

    public static String getJson(JSONObject obj, String key) {
        try {
            if (obj == null) return "";
            if (!obj.has(key)) return "";
            return obj.optString(key, "").trim();
        } catch (Exception e) { return ""; }
    }

    public static List<Bhajan> sortNepali(List<Bhajan> list) {
        try {
            final Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            Collections.sort(list, (a, b) -> {
                String left = a.titleNepali == null ? "" : a.titleNepali;
                String right = b.titleNepali == null ? "" : b.titleNepali;
                return collator.compare(left, right);
            });
        } catch (Exception e) {
            Collections.sort(list, (a, b) -> {
                String left = a.titleNepali == null ? "" : a.titleNepali;
                String right = b.titleNepali == null ? "" : b.titleNepali;
                return left.compareTo(right);
            });
        }
        return list;
    }

    public static String getYouTubeLink(Context ctx, String bhajanId) {
        try {
            int resId = ctx.getResources().getIdentifier("youtube_link", "raw", ctx.getPackageName());
            if (resId == 0) resId = ctx.getResources().getIdentifier("youtube_links", "raw", ctx.getPackageName());
            if (resId == 0) return null;
            JSONArray arr = new JSONArray(readRaw(ctx, resId));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (bhajanId.equals(getJson(obj, "id"))) {
                    String link = getJson(obj, "link");
                    if (link.isEmpty()) link = getJson(obj, "url");
                    return link;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // BHAJAN FAVOURITES
    // ═══════════════════════════════════════════════════════════

    private static Set<String> getFavouriteSet(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .getStringSet("favs", new HashSet<>()));
    }

    public static boolean isFav(Context ctx, String id) {
        if (id == null) return false;
        return getFavouriteSet(ctx).contains(id);
    }

    public static void toggleFav(Context ctx, String id) {
        if (id == null) return;
        Set<String> set = getFavouriteSet(ctx);
        boolean removing = set.contains(id);
        if (removing) set.remove(id);
        else set.add(id);
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().putStringSet("favs", set).apply();
        // Sync: removing a favourite also removes it from every favourite sub-category
        if (removing) removeIdFromAllSubCategories(ctx, PREF_FAV_CATEGORIES, "fav_category_order", id);
    }

    public static int favCount(Context ctx) { return getFavouriteSet(ctx).size(); }

    public static void clearFavs(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().remove("favs").apply();
        clearAllSubCategoryContents(ctx, PREF_FAV_CATEGORIES);
    }

    // ═══════════════════════════════════════════════════════════
    // BHAJAN RECENTS
    // ═══════════════════════════════════════════════════════════

    public static void addRecent(Context ctx, String id) {
        if (id == null || id.isEmpty()) return;
        List<String> recents = new ArrayList<>(getRecentIds(ctx));
        recents.remove(id);
        recents.add(0, id);
        if (recents.size() > 50) recents = new ArrayList<>(recents.subList(0, 50));
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                .putString("recents", TextUtils.join(",", recents)).apply();
    }

    public static List<String> getRecentIds(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).getString("recents", "");
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String id : stored.split(",")) if (!id.trim().isEmpty()) result.add(id.trim());
        return result;
    }

    public static void clearRecents(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().remove("recents").apply();
    }

    public static void addAudioRecent(Context ctx, String id) {
        if (id == null || id.isEmpty()) return;
        List<String> recents = new ArrayList<>(getAudioRecentIds(ctx));
        recents.remove(id);
        recents.add(0, id);
        if (recents.size() > 50) recents = new ArrayList<>(recents.subList(0, 50));
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                .putString("audio_recents", TextUtils.join(",", recents)).apply();
    }

    public static List<String> getAudioRecentIds(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).getString("audio_recents", "");
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String id : stored.split(",")) if (!id.trim().isEmpty()) result.add(id.trim());
        return result;
    }

    public static void clearAudioRecents(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().remove("audio_recents").apply();
    }

    // ═══════════════════════════════════════════════════════════
    // CUSTOM PLAYLISTS
    // ═══════════════════════════════════════════════════════════

    public static String sanitizePlaylistName(String name) {
        if (name == null) return "";
        return name.replace(",", " ").replace("\n", " ").trim();
    }

    private static List<String> getPlaylistOrder(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).getString("playlist_order", "");
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String name : stored.split(",")) if (!name.trim().isEmpty()) result.add(name.trim());
        return result;
    }

    private static void savePlaylistOrder(Context ctx, List<String> order) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                .putString("playlist_order", TextUtils.join(",", order)).apply();
    }

    public static Map<String, List<String>> getPlaylists(Context ctx) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        List<String> order = getPlaylistOrder(ctx);
        for (String name : order) {
            if (playlistPrefs.contains(name)) {
                result.put(name, parsePlaylistIds(playlistPrefs.getString(name, "")));
            }
        }
        for (Map.Entry<String, ?> entry : playlistPrefs.getAll().entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), parsePlaylistIds((String) entry.getValue()));
            }
        }
        return result;
    }

    private static List<String> parsePlaylistIds(String stored) {
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String id : stored.split(",")) if (!id.trim().isEmpty()) result.add(id.trim());
        return result;
    }

    public static void createPlaylist(Context ctx, String rawName) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty()) return;
        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        if (!playlistPrefs.contains(name)) playlistPrefs.edit().putString(name, "").apply();
        List<String> order = getPlaylistOrder(ctx);
        if (!order.contains(name)) { order.add(0, name); savePlaylistOrder(ctx, order); }
    }

    public static void deletePlaylist(Context ctx, String rawName) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty()) return;
        ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE).edit().remove(name).apply();
        List<String> order = getPlaylistOrder(ctx);
        if (order.contains(name)) { order.remove(name); savePlaylistOrder(ctx, order); }
    }

    public static void addToPlaylist(Context ctx, String rawName, String trackId) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty() || trackId == null || trackId.isEmpty()) return;
        createPlaylist(ctx, name);
        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(playlistPrefs.getString(name, ""));
        if (!ids.contains(trackId)) {
            ids.add(trackId);
            playlistPrefs.edit().putString(name, TextUtils.join(",", ids)).apply();
        }
    }

    public static void removeFromPlaylist(Context ctx, String rawName, String trackId) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty() || trackId == null || trackId.isEmpty()) return;
        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(playlistPrefs.getString(name, ""));
        if (ids.contains(trackId)) {
            ids.remove(trackId);
            playlistPrefs.edit().putString(name, TextUtils.join(",", ids)).apply();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CONFIRM DIALOG
    // ═══════════════════════════════════════════════════════════

    public static void showConfirm(Context ctx, String title, String message,
                                   String positiveText, boolean destructive, Runnable onConfirm) {
        try {
            View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_confirm, null);
            TextView tvTitle = view.findViewById(R.id.dialog_title);
            TextView tvMessage = view.findViewById(R.id.dialog_message);
            MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
            MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm);
            tvTitle.setText(title);
            tvMessage.setText(message);
            btnConfirm.setText(positiveText);

            AlertDialog dialog = new AlertDialog.Builder(ctx)
                    .setView(view)
                    .setCancelable(true)
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                if (onConfirm != null) onConfirm.run();
            });
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            if (onConfirm != null) onConfirm.run();
        }
    }
    // ═══════════════════════════════════════════════════════════
    // REMOVE A SINGLE BHAJAN FROM RECENT
    // ═══════════════════════════════════════════════════════════
    public static void removeRecent(Context ctx, String id) {
        if (id == null || id.isEmpty()) return;
        List<String> recents = new ArrayList<>(getRecentIds(ctx));
        if (recents.remove(id)) {
            ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                    .putString("recents", TextUtils.join(",", recents)).apply();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CUSTOM BHAJAN CATEGORIES (ditto copy of custom playlists)
    // ═══════════════════════════════════════════════════════════
    private static final String PREF_CATEGORIES = "bhajan_categories";

    public static String sanitizeCategoryName(String name) {
        if (name == null) return "";
        return name.replace(",", " ").replace("\n", " ").trim();
    }

    private static List<String> getCategoryOrder(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).getString("category_order", "");
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String name : stored.split(",")) if (!name.trim().isEmpty()) result.add(name.trim());
        return result;
    }

    private static void saveCategoryOrder(Context ctx, List<String> order) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                .putString("category_order", TextUtils.join(",", order)).apply();
    }

    public static Map<String, List<String>> getBhajanCategories(Context ctx) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        SharedPreferences categoryPrefs = ctx.getSharedPreferences(PREF_CATEGORIES, Context.MODE_PRIVATE);
        List<String> order = getCategoryOrder(ctx);
        for (String name : order) {
            if (categoryPrefs.contains(name)) {
                result.put(name, parsePlaylistIds(categoryPrefs.getString(name, "")));
            }
        }
        for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), parsePlaylistIds((String) entry.getValue()));
            }
        }
        return result;
    }

    public static void createBhajanCategory(Context ctx, String rawName) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty()) return;
        SharedPreferences categoryPrefs = ctx.getSharedPreferences(PREF_CATEGORIES, Context.MODE_PRIVATE);
        if (!categoryPrefs.contains(name)) categoryPrefs.edit().putString(name, "").apply();
        List<String> order = getCategoryOrder(ctx);
        if (!order.contains(name)) { order.add(0, name); saveCategoryOrder(ctx, order); }
    }

    public static void deleteBhajanCategory(Context ctx, String rawName) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty()) return;
        ctx.getSharedPreferences(PREF_CATEGORIES, Context.MODE_PRIVATE).edit().remove(name).apply();
        List<String> order = getCategoryOrder(ctx);
        if (order.contains(name)) { order.remove(name); saveCategoryOrder(ctx, order); }
    }

    public static void addToBhajanCategory(Context ctx, String rawName, String bhajanId) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty() || bhajanId == null || bhajanId.isEmpty()) return;
        createBhajanCategory(ctx, name);
        SharedPreferences categoryPrefs = ctx.getSharedPreferences(PREF_CATEGORIES, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(categoryPrefs.getString(name, ""));
        if (!ids.contains(bhajanId)) {
            ids.add(bhajanId);
            categoryPrefs.edit().putString(name, TextUtils.join(",", ids)).apply();
        }
    }

    public static void removeFromBhajanCategory(Context ctx, String rawName, String bhajanId) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty() || bhajanId == null || bhajanId.isEmpty()) return;
        SharedPreferences categoryPrefs = ctx.getSharedPreferences(PREF_CATEGORIES, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(categoryPrefs.getString(name, ""));
        if (ids.contains(bhajanId)) {
            ids.remove(bhajanId);
            categoryPrefs.edit().putString(name, TextUtils.join(",", ids)).apply();
        }
    }
    // ═══════════════════════════════════════════════════════════
    // FAVOURITE SUB-CATEGORIES & ARCHIVE SUB-CATEGORIES
    // ═══════════════════════════════════════════════════════════
    private static final String PREF_FAV_CATEGORIES = "bhajan_fav_categories";
    private static final String PREF_ARCHIVE_CATEGORIES = "bhajan_archive_categories";

    public static void ensureFav(Context ctx, String id) {
        if (id == null) return;
        Set<String> set = getFavouriteSet(ctx);
        if (!set.contains(id)) {
            set.add(id);
            ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().putStringSet("favs", set).apply();
        }
    }

    public static void ensureArchived(Context ctx, String id) {
        if (id == null) return;
        Set<String> set = new HashSet<>(getArchivedSet(ctx));
        if (!set.contains(id)) {
            set.add(id);
            ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit().putStringSet("archived", set).apply();
        }
    }

    // ── generic core ──
    private static List<String> getSubCategoryOrder(Context ctx, String orderKey) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).getString(orderKey, "");
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;
        for (String name : stored.split(",")) if (!name.trim().isEmpty()) result.add(name.trim());
        return result;
    }

    private static void saveSubCategoryOrder(Context ctx, String orderKey, List<String> order) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE).edit()
                .putString(orderKey, TextUtils.join(",", order)).apply();
    }

    private static Map<String, List<String>> getSubCategories(Context ctx, String prefsName, String orderKey) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        List<String> order = getSubCategoryOrder(ctx, orderKey);
        for (String name : order) {
            if (prefs.contains(name)) result.put(name, parsePlaylistIds(prefs.getString(name, "")));
        }
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), parsePlaylistIds((String) entry.getValue()));
            }
        }
        return result;
    }

    private static void createSubCategory(Context ctx, String prefsName, String orderKey, String rawName) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty()) return;
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        if (!prefs.contains(name)) prefs.edit().putString(name, "").apply();
        List<String> order = getSubCategoryOrder(ctx, orderKey);
        if (!order.contains(name)) { order.add(0, name); saveSubCategoryOrder(ctx, orderKey, order); }
    }

    private static void deleteSubCategory(Context ctx, String prefsName, String orderKey, String rawName) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty()) return;
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().remove(name).apply();
        List<String> order = getSubCategoryOrder(ctx, orderKey);
        if (order.contains(name)) { order.remove(name); saveSubCategoryOrder(ctx, orderKey, order); }
    }

    private static void addToSubCategory(Context ctx, String prefsName, String orderKey, String rawName, String id) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty() || id == null || id.isEmpty()) return;
        createSubCategory(ctx, prefsName, orderKey, name);
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(prefs.getString(name, ""));
        if (!ids.contains(id)) {
            ids.add(id);
            prefs.edit().putString(name, TextUtils.join(",", ids)).apply();
        }
    }

    private static void removeFromSubCategory(Context ctx, String prefsName, String orderKey, String rawName, String id) {
        String name = sanitizeCategoryName(rawName);
        if (name.isEmpty() || id == null || id.isEmpty()) return;
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(prefs.getString(name, ""));
        if (ids.contains(id)) {
            ids.remove(id);
            prefs.edit().putString(name, TextUtils.join(",", ids)).apply();
        }
    }

    private static void removeIdFromAllSubCategories(Context ctx, String prefsName, String orderKey, String id) {
        if (id == null || id.isEmpty()) return;
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        for (String name : new ArrayList<>(prefs.getAll().keySet())) {
            List<String> ids = parsePlaylistIds(prefs.getString(name, ""));
            if (ids.contains(id)) {
                ids.remove(id);
                prefs.edit().putString(name, TextUtils.join(",", ids)).apply();
            }
        }
    }

    private static void clearAllSubCategoryContents(Context ctx, String prefsName) {
        SharedPreferences prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (String name : prefs.getAll().keySet()) editor.putString(name, "");
        editor.apply();
    }

    // ── favourite sub-categories (public) ──
    public static Map<String, List<String>> getFavCategories(Context ctx) {
        return getSubCategories(ctx, PREF_FAV_CATEGORIES, "fav_category_order");
    }

    public static void createFavCategory(Context ctx, String name) {
        createSubCategory(ctx, PREF_FAV_CATEGORIES, "fav_category_order", name);
    }

    public static void deleteFavCategory(Context ctx, String name) {
        deleteSubCategory(ctx, PREF_FAV_CATEGORIES, "fav_category_order", name);
    }

    public static void addToFavCategory(Context ctx, String name, String id) {
        addToSubCategory(ctx, PREF_FAV_CATEGORIES, "fav_category_order", name, id);
        ensureFav(ctx, id); // sync: being inside a favourite category means it is a favourite
    }

    public static void removeFromFavCategory(Context ctx, String name, String id) {
        removeFromSubCategory(ctx, PREF_FAV_CATEGORIES, "fav_category_order", name, id);
    }

    // ── archive sub-categories (public) ──
    public static Map<String, List<String>> getArchiveCategories(Context ctx) {
        return getSubCategories(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order");
    }

    public static void createArchiveCategory(Context ctx, String name) {
        createSubCategory(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order", name);
    }

    public static void deleteArchiveCategory(Context ctx, String name) {
        deleteSubCategory(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order", name);
    }

    public static void addToArchiveCategory(Context ctx, String name, String id) {
        addToSubCategory(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order", name, id);
        ensureArchived(ctx, id); // sync: being inside an archive category means it is archived
    }

    public static void removeFromArchiveCategory(Context ctx, String name, String id) {
        removeFromSubCategory(ctx, PREF_ARCHIVE_CATEGORIES, "archive_category_order", name, id);
    }
}
