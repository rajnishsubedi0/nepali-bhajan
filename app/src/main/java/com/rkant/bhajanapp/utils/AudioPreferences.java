package com.rkant.bhajanapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class AudioPreferences {
    private static final String PREFS = "audio_prefs";
    private static final String FAVS = "audio_favs";
    private static final String DOWNLOADS = "audio_downloads";

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isFav(Context c, String id) {
        Set<String> f = p(c).getStringSet(FAVS, new HashSet<>());
        return f != null && f.contains(id);
    }

    public static void toggleFav(Context c, String id) {
        Set<String> f = new HashSet<>(p(c).getStringSet(FAVS, new HashSet<>()));
        if (f.contains(id)) f.remove(id); else f.add(id);
        p(c).edit().putStringSet(FAVS, f).apply();
    }

    public static void saveDownloadPath(Context c, String id, String path) {
        Set<String> d = new HashSet<>(p(c).getStringSet(DOWNLOADS, new HashSet<>()));
        d.add(id + "|" + path);
        p(c).edit().putStringSet(DOWNLOADS, d).apply();
    }

    public static String getLocalPath(Context c, String id) {
        Set<String> d = p(c).getStringSet(DOWNLOADS, new HashSet<>());
        if (d != null) {
            for (String entry : d) {
                if (entry.startsWith(id + "|")) return entry.split("\\|", 2)[1];
            }
        }
        return null;
    }
}