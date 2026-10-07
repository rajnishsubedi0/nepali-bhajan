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
        if (mode == 1) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mode == 2) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static int getThemeMode(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .getInt("theme_mode", 0);
    }

    public static void setThemeMode(Context ctx, int mode) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putInt("theme_mode", mode)
                .apply();
    }

    // ═══════════════════════════════════════════════════════════
    // SCREEN TIMEOUT
    // ═══════════════════════════════════════════════════════════

    public static int getScreenTimeout(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .getInt("screen_timeout", 3);
    }

    public static void setScreenTimeout(Context ctx, int timeout) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putInt("screen_timeout", timeout)
                .apply();
    }

    // ═══════════════════════════════════════════════════════════
    // FONT SCALE
    // ═══════════════════════════════════════════════════════════

    public static int getListFontScale(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .getInt("list_font_scale", 2);
    }

    public static void setListFontScale(Context ctx, int scale) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putInt("list_font_scale", scale)
                .apply();
    }

    public static int getLyricFontScale(Context ctx) {
        return ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .getInt("lyric_font_scale", 2);
    }

    public static void setLyricFontScale(Context ctx, int scale) {
        ctx.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putInt("lyric_font_scale", scale)
                .apply();
    }

    public static String scaleName(int scale) {
        switch (scale) {
            case 0:
                return "Extra Small";
            case 1:
                return "Small";
            case 3:
                return "Large";
            case 4:
                return "Extra Large";
            default:
                return "Medium";
        }
    }

    public static float scaleSp(int scale, boolean lyric) {
        float base = lyric ? 21f : 19f;
        switch (scale) {
            case 0:
                return base - 4f;
            case 1:
                return base - 2f;
            case 3:
                return base + 2f;
            case 4:
                return base + 4f;
            default:
                return base;
        }
    }

    public static float listNumberSize(Context ctx) {
        float size = scaleSp(getListFontScale(ctx), false) - 3f;
        return Math.max(12f, size);
    }

    public static float listTitleSize(Context ctx) {
        return scaleSp(getListFontScale(ctx), false);
    }

    public static float lyricSize(Context ctx) {
        return scaleSp(getLyricFontScale(ctx), true);
    }

    // ═══════════════════════════════════════════════════════════
    // TIME FORMAT
    // ═══════════════════════════════════════════════════════════

    public static String formatTime(long ms) {
        if (ms < 0) return "--:--";

        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // NEPALI NUMBER
    // ═══════════════════════════════════════════════════════════

    public static String getNepaliNumber(int number) {
        String english = String.valueOf(number);
        StringBuilder builder = new StringBuilder();

        for (char c : english.toCharArray()) {
            switch (c) {
                case '0':
                    builder.append("०");
                    break;
                case '1':
                    builder.append("१");
                    break;
                case '2':
                    builder.append("२");
                    break;
                case '3':
                    builder.append("३");
                    break;
                case '4':
                    builder.append("४");
                    break;
                case '5':
                    builder.append("५");
                    break;
                case '6':
                    builder.append("६");
                    break;
                case '7':
                    builder.append("७");
                    break;
                case '8':
                    builder.append("८");
                    break;
                case '9':
                    builder.append("९");
                    break;
                default:
                    builder.append(c);
                    break;
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
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    public static String getJson(JSONObject obj, String key) {
        try {
            if (obj == null) return "";
            if (!obj.has(key)) return "";
            return obj.optString(key, "").trim();
        } catch (Exception e) {
            return "";
        }
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
            int resId = ctx.getResources().getIdentifier(
                    "youtube_links",
                    "raw",
                    ctx.getPackageName()
            );

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
        } catch (Exception ignored) {
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // BHAJAN FAVOURITES
    // ═══════════════════════════════════════════════════════════

    private static Set<String> getFavouriteSet(Context ctx) {
        return new HashSet<>(
                ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                        .getStringSet("favs", new HashSet<>())
        );
    }

    public static boolean isFav(Context ctx, String id) {
        if (id == null) return false;
        return getFavouriteSet(ctx).contains(id);
    }

    public static void toggleFav(Context ctx, String id) {
        if (id == null) return;

        Set<String> set = getFavouriteSet(ctx);
        if (set.contains(id)) {
            set.remove(id);
        } else {
            set.add(id);
        }

        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .putStringSet("favs", set)
                .apply();
    }

    public static int favCount(Context ctx) {
        return getFavouriteSet(ctx).size();
    }

    public static void clearFavs(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .remove("favs")
                .apply();
    }

    // ═══════════════════════════════════════════════════════════
    // BHAJAN RECENTS
    // ═══════════════════════════════════════════════════════════

    public static void addRecent(Context ctx, String id) {
        if (id == null || id.isEmpty()) return;

        List<String> recents = new ArrayList<>(getRecentIds(ctx));
        recents.remove(id);
        recents.add(0, id);

        if (recents.size() > 50) {
            recents = new ArrayList<>(recents.subList(0, 50));
        }

        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .putString("recents", TextUtils.join(",", recents))
                .apply();
    }

    public static List<String> getRecentIds(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .getString("recents", "");

        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;

        for (String id : stored.split(",")) {
            if (!id.trim().isEmpty()) {
                result.add(id.trim());
            }
        }

        return result;
    }

    public static void clearRecents(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .remove("recents")
                .apply();
    }

    public static void addAudioRecent(Context ctx, String id) {
        if (id == null || id.isEmpty()) return;

        List<String> recents = new ArrayList<>(getAudioRecentIds(ctx));
        recents.remove(id);
        recents.add(0, id);

        if (recents.size() > 10) {
            recents = new ArrayList<>(recents.subList(0, 10));
        }

        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .putString("audio_recents", TextUtils.join(",", recents))
                .apply();
    }

    public static List<String> getAudioRecentIds(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .getString("audio_recents", "");

        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;

        for (String id : stored.split(",")) {
            if (!id.trim().isEmpty()) {
                result.add(id.trim());
            }
        }

        return result;
    }

    public static void clearAudioRecents(Context ctx) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .remove("audio_recents")
                .apply();
    }

    // ═══════════════════════════════════════════════════════════
    // CUSTOM PLAYLISTS
    // ═══════════════════════════════════════════════════════════

    public static String sanitizePlaylistName(String name) {
        if (name == null) return "";
        return name.replace(",", " ")
                .replace("\n", " ")
                .trim();
    }

    private static List<String> getPlaylistOrder(Context ctx) {
        String stored = ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .getString("playlist_order", "");

        List<String> result = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return result;

        for (String name : stored.split(",")) {
            if (!name.trim().isEmpty()) {
                result.add(name.trim());
            }
        }

        return result;
    }

    private static void savePlaylistOrder(Context ctx, List<String> order) {
        ctx.getSharedPreferences(PREF_DATA, Context.MODE_PRIVATE)
                .edit()
                .putString("playlist_order", TextUtils.join(",", order))
                .apply();
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

        // Include any playlists that exist but somehow are not in order
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

        for (String id : stored.split(",")) {
            if (!id.trim().isEmpty()) {
                result.add(id.trim());
            }
        }

        return result;
    }

    public static void createPlaylist(Context ctx, String rawName) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty()) return;

        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        if (!playlistPrefs.contains(name)) {
            playlistPrefs.edit().putString(name, "").apply();
        }

        List<String> order = getPlaylistOrder(ctx);
        if (!order.contains(name)) {
            order.add(0, name);
            savePlaylistOrder(ctx, order);
        }
    }

    public static void deletePlaylist(Context ctx, String rawName) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty()) return;

        ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE)
                .edit()
                .remove(name)
                .apply();

        List<String> order = getPlaylistOrder(ctx);
        if (order.contains(name)) {
            order.remove(name);
            savePlaylistOrder(ctx, order);
        }
    }

    public static void addToPlaylist(Context ctx, String rawName, String trackId) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty() || trackId == null || trackId.isEmpty()) return;

        createPlaylist(ctx, name);

        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(playlistPrefs.getString(name, ""));

        if (!ids.contains(trackId)) {
            ids.add(trackId);
            playlistPrefs.edit()
                    .putString(name, TextUtils.join(",", ids))
                    .apply();
        }
    }

    public static void removeFromPlaylist(Context ctx, String rawName, String trackId) {
        String name = sanitizePlaylistName(rawName);
        if (name.isEmpty() || trackId == null || trackId.isEmpty()) return;

        SharedPreferences playlistPrefs = ctx.getSharedPreferences(PREF_PLAYLISTS, Context.MODE_PRIVATE);
        List<String> ids = parsePlaylistIds(playlistPrefs.getString(name, ""));

        if (ids.contains(trackId)) {
            ids.remove(trackId);
            playlistPrefs.edit()
                    .putString(name, TextUtils.join(",", ids))
                    .apply();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CONFIRM DIALOG
    // ═══════════════════════════════════════════════════════════

    public static void showConfirm(
            Context ctx,
            String title,
            String message,
            String positiveText,
            boolean destructive,
            Runnable onConfirm
    ) {
        try {
            View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_confirm, null);

            TextView tvTitle = view.findViewById(R.id.dialog_title);
            TextView tvMessage = view.findViewById(R.id.dialog_message);
            MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
            MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm);

            tvTitle.setText(title);
            tvMessage.setText(message);
            btnConfirm.setText(positiveText);

            if (destructive) {
                btnConfirm.setTextColor(ctx.getColor(R.color.red));
            } else {
                btnConfirm.setTextColor(ctx.getColor(R.color.on_accent));
            }

            AlertDialog dialog = new AlertDialog.Builder(ctx)
                    .setView(view)
                    .setCancelable(true)
                    .create();

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                if (onConfirm != null) {
                    onConfirm.run();
                }
            });

            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            if (onConfirm != null) {
                onConfirm.run();
            }
        }
    }
}