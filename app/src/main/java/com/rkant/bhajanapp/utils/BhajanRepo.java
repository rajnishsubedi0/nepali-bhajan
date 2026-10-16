package com.rkant.bhajanapp.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** Loads the bhajan index once and shares it between Home, Library and Detail. */
public final class BhajanRepo {
    private BhajanRepo() {}

    public interface Callback { void onLoaded(List<Bhajan> all); }

    private static volatile List<Bhajan> cache;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Delivers the sorted list on the main thread. */
    public static void load(Context ctx, Callback cb) {
        List<Bhajan> cached = cache;
        if (cached != null) { cb.onLoaded(cached); return; }
        final Context app = ctx.getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Bhajan> list = parse(app);
            cache = list;
            MAIN.post(() -> cb.onLoaded(list));
        });
    }

    private static List<Bhajan> parse(Context app) {
        List<Bhajan> temp = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Helper.readRaw(app, R.raw.bhajan_list));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                temp.add(new Bhajan(Helper.getJson(o, "id"), Helper.getJson(o, "bhajan_nepali"),
                        Helper.getJson(o, "bhajan_english"), Helper.getJson(o, "bhajan"),
                        Helper.getJson(o, "bhajan_type")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Helper.sortNepali(temp);
    }

    public static Bhajan find(List<Bhajan> all, String id) {
        if (id == null) return null;
        for (Bhajan b : all) if (id.equals(b.id)) return b;
        return null;
    }

    /** Case-insensitive match on Nepali title, English keywords and bhajan type. */
    public static boolean matches(Bhajan b, String lowerQuery) {
        if (lowerQuery == null || lowerQuery.isEmpty()) return true;
        return (b.titleNepali != null && b.titleNepali.toLowerCase().contains(lowerQuery))
                || (b.titleEnglish != null && b.titleEnglish.toLowerCase().contains(lowerQuery))
                || (b.type != null && b.type.toLowerCase().contains(lowerQuery));
    }

    public static boolean inCategory(Bhajan b, String category) {
        return category == null || "All".equals(category)
                || (b.category != null && b.category.equalsIgnoreCase(category));
    }
}
