package com.rkant.bhajanapp.utils;

import com.rkant.bhajanapp.model.AudioTrack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AudioRepository {

    // ⚠️ Point this to your GitHub Pages audio.json
    private static final String MANIFEST_URL =
            "https://rajnishsubedi0.github.io/audio-streams/audio.json";

    public interface Callback {
        void onLoaded(List<AudioTrack> tracks);
        void onError(String message);
    }

    public static void loadTracks(Callback callback) {
        new Thread(() -> {
            try {
                String json = fetch(MANIFEST_URL);
                String baseUrl = MANIFEST_URL.substring(0, MANIFEST_URL.lastIndexOf('/') + 1);

                JSONObject root = new JSONObject(json);
                JSONArray arr = root.getJSONArray("tracks");
                List<AudioTrack> tracks = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String id = o.optString("id", "track_" + i);
                    String title = o.optString("title", "Bhajan " + (i + 1));
                    String url = o.optString("url", "");
                    int duration = o.optInt("duration", -1);
                    String category = o.optString("category", "");

                    if (!url.isEmpty() && !url.startsWith("http")) url = baseUrl + url;

                    AudioTrack t = new AudioTrack(id, title, url, duration);
                    t.category = category;
                    tracks.add(t);
                }
                callback.onLoaded(tracks);
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static String fetch(String urlString) throws Exception {
        URL url = new URL(urlString);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }
}