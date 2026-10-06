package com.rkant.bhajanapp.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.rkant.bhajanapp.model.AudioTrack;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AudioRepository {

    private static final String MANIFEST_URL =
            "https://rajnishsubedi0.github.io/audio-streams/audio.json";

    public interface LoadCallback {
        void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork);
        void onError(String message);
    }

    /**
     * Load tracks with offline-first strategy:
     * 1. Immediately return tracks from local database (works offline)
     * 2. If internet available, sync from network and update database
     */
    public static void loadTracks(Context context, LoadCallback callback) {
        new Thread(() -> {
            AudioDatabase db = AudioDatabase.getInstance(context);

            // Step 1: Load from database first (instant, offline-capable)
            List<AudioTrack> localTracks = db.getAllTracks();

            // Verify download files still exist (user might have cleared storage)
            for (AudioTrack t : localTracks) {
                if (t.isDownloaded && t.localPath != null) {
                    java.io.File f = new java.io.File(t.localPath);
                    if (!f.exists()) {
                        // File was deleted, update database
                        db.markNotDownloaded(t.id);
                        t.isDownloaded = false;
                        t.localPath = null;
                        t.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
                    }
                }
            }

            if (!localTracks.isEmpty()) {
                callback.onTracksLoaded(localTracks, false);
            }

            // Step 2: Try to sync from network if internet is available
            if (isNetworkAvailable(context)) {
                try {
                    String json = fetch(MANIFEST_URL);
                    List<AudioTrack> networkTracks = parseTracks(json);

                    if (!networkTracks.isEmpty()) {
                        // Save to database (preserves fav/download state)
                        db.saveTracks(networkTracks);

                        // Re-read from DB to get merged fav/download state
                        List<AudioTrack> mergedTracks = db.getAllTracks();

                        // Verify downloads again after merge
                        for (AudioTrack t : mergedTracks) {
                            if (t.isDownloaded && t.localPath != null) {
                                java.io.File f = new java.io.File(t.localPath);
                                if (!f.exists()) {
                                    db.markNotDownloaded(t.id);
                                    t.isDownloaded = false;
                                    t.localPath = null;
                                    t.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
                                }
                            }
                        }

                        callback.onTracksLoaded(mergedTracks, true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Network failed but we already showed local data, so just log it
                    if (localTracks.isEmpty()) {
                        callback.onError("Failed to load audio list");
                    }
                }
            } else {
                // No internet - if we have no local data either, show error
                if (localTracks.isEmpty()) {
                    callback.onError("No internet connection. Please connect to load bhajans.");
                }
            }
        }).start();
    }

    /**
     * Force sync from network only (used when internet reconnects)
     */
    public static void syncFromNetwork(Context context, LoadCallback callback) {
        new Thread(() -> {
            if (!isNetworkAvailable(context)) {
                return;
            }

            try {
                String json = fetch(MANIFEST_URL);
                List<AudioTrack> networkTracks = parseTracks(json);

                if (!networkTracks.isEmpty()) {
                    AudioDatabase db = AudioDatabase.getInstance(context);
                    db.saveTracks(networkTracks);

                    List<AudioTrack> mergedTracks = db.getAllTracks();

                    // Verify downloads
                    for (AudioTrack t : mergedTracks) {
                        if (t.isDownloaded && t.localPath != null) {
                            java.io.File f = new java.io.File(t.localPath);
                            if (!f.exists()) {
                                db.markNotDownloaded(t.id);
                                t.isDownloaded = false;
                                t.localPath = null;
                                t.downloadState = AudioTrack.STATE_NOT_DOWNLOADED;
                            }
                        }
                    }

                    callback.onTracksLoaded(mergedTracks, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static List<AudioTrack> parseTracks(String json) throws Exception {
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
        return tracks;
    }

    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            return false;
        }
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