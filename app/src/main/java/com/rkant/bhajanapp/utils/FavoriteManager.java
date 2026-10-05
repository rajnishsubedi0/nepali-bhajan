package com.rkant.bhajanapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class FavoriteManager {
    private static final String PREFS_NAME = "bhajan_favs";
    private static final String KEY_FAVS = "fav_ids";

    public static boolean isFavorite(Context context, String id) {
        Set<String> favs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_FAVS, new HashSet<>());
        return favs.contains(id);
    }

    public static void toggleFavorite(Context context, String id) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> favs = new HashSet<>(prefs.getStringSet(KEY_FAVS, new HashSet<>()));
        if (favs.contains(id)) favs.remove(id);
        else favs.add(id);
        prefs.edit().putStringSet(KEY_FAVS, favs).apply();
    }
}