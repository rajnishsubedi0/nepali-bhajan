package com.rkant.bhajanapp.utils;

import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.model.Bhajan;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Central, reusable search engine used by Library and Audio tabs.
 *
 * How it works (Google-input style):
 *  - Query is normalized (NFC, lowercase, single spaces) and split into tokens.
 *  - Each item field is scored per token: exact > prefix > contains.
 *  - Fields have weights (title highest).
 *  - Matching ALL query tokens gives a completion bonus, so precise queries rank first.
 *  - The full phrase gets a big boost when present.
 *  - Results are returned sorted by descending relevance.
 */
public final class SearchEngine {

    private SearchEngine() {}

    /** Normalize for comparison: NFC (merges Devanagari matras), lowercase, collapse spaces. */
    public static String normalize(String s) {
        if (s == null) return "";
        String out = Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
        return out.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Split the query into clean tokens. */
    public static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        for (String p : normalize(query).split(" ")) {
            if (!p.isEmpty()) tokens.add(p);
        }
        return tokens;
    }

    /** Score one field against all query tokens. */
    private static float fieldScore(String field, List<String> tokens) {
        if (field == null || field.isEmpty() || tokens.isEmpty()) return 0f;
        String f = normalize(field);
        float total = 0f;
        int matched = 0;
        for (String t : tokens) {
            if (f.equals(t)) {            // exact field match
                total += 3f; matched++;
            } else if (f.startsWith(t)) { // prefix match
                total += 2f; matched++;
            } else if (f.contains(t)) {   // substring match
                total += 1f; matched++;
            }
        }
        if (matched == 0) return 0f;
        // Bonus for matching every token in this field (strong signal).
        if (matched == tokens.size()) total += 2f;
        // Penalize partial token coverage slightly so full matches win.
        return total * ((float) matched / tokens.size());
    }

    /** Big boost when the whole phrase appears verbatim in a field. */
    private static float phraseBoost(String field, String phrase) {
        if (field == null || phrase == null || phrase.isEmpty()) return 0f;
        return normalize(field).contains(phrase) ? 6f : 0f;
    }

    public static float scoreBhajan(Bhajan b, String rawQuery) {
        List<String> tokens = tokenize(rawQuery);
        if (tokens.isEmpty()) return 1f;
        String phrase = normalize(rawQuery);
        float s = 0f;
        s += fieldScore(b.titleNepali, tokens) * 4f;
        s += fieldScore(b.titleEnglish, tokens) * 3f;
        s += fieldScore(b.type, tokens) * 1.5f;
        s += fieldScore(b.category, tokens) * 1.5f;
        s += phraseBoost(b.titleNepali, phrase) * 2f;
        s += phraseBoost(b.titleEnglish, phrase) * 2f;
        return s;
    }

    public static float scoreTrack(AudioTrack t, String rawQuery) {
        List<String> tokens = tokenize(rawQuery);
        if (tokens.isEmpty()) return 1f;
        String phrase = normalize(rawQuery);
        float s = 0f;
        s += fieldScore(t.title, tokens) * 4f;
        s += fieldScore(t.category, tokens) * 1.5f;
        s += phraseBoost(t.title, phrase) * 2f;
        return s;
    }

    public static boolean matchesBhajan(Bhajan b, String query) {
        return scoreBhajan(b, query) > 0f;
    }

    public static boolean matchesTrack(AudioTrack t, String query) {
        return scoreTrack(t, query) > 0f;
    }

    /** Returns a relevance-sorted copy. Empty query returns the input order. */
    public static List<Bhajan> searchBhajans(List<Bhajan> all, String query) {
        List<Bhajan> out = new ArrayList<>();
        if (all == null) return out;
        if (query == null || query.trim().isEmpty()) { out.addAll(all); return out; }
        for (Bhajan b : all) if (scoreBhajan(b, query) > 0f) out.add(b);
        Collections.sort(out, (a, c) -> Float.compare(scoreBhajan(c, query), scoreBhajan(a, query)));
        return out;
    }

    public static List<AudioTrack> searchTracks(List<AudioTrack> all, String query) {
        List<AudioTrack> out = new ArrayList<>();
        if (all == null) return out;
        if (query == null || query.trim().isEmpty()) { out.addAll(all); return out; }
        for (AudioTrack t : all) if (scoreTrack(t, query) > 0f) out.add(t);
        Collections.sort(out, (a, c) -> Float.compare(scoreTrack(c, query), scoreTrack(a, query)));
        return out;
    }
}
