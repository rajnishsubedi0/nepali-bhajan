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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AudioRepository {

    private static final String MANIFEST_URL =
            "https://rajnishsubedi0.github.io/audio-streams/audio.json";

    public interface LoadCallback {
        void onTracksLoaded(List<AudioTrack> tracks, boolean fromNetwork);
        void onError(String message);
        void onComplete(); // Always called when loading is finished
    }

    public static void loadTracks(Context context, LoadCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            AudioDatabase db = AudioDatabase.getInstance(app);

            // Step 1: Load from database first (instant, offline-capable)
            List<AudioTrack> localTracks = db.getAllTracks();
            reconcile(app, db, localTracks);

            if (!localTracks.isEmpty()) {
                callback.onTracksLoaded(localTracks, false);
            }

            // Step 2: Try to sync from network if internet is available
            if (isNetworkAvailable(app)) {
                try {
                    List<AudioTrack> networkTracks = parseTracks(fetch(MANIFEST_URL));

                    if (!networkTracks.isEmpty()) {
                        db.saveTracks(networkTracks);
                        List<AudioTrack> mergedTracks = db.getAllTracks();
                        reconcile(app, db, mergedTracks);
                        callback.onTracksLoaded(mergedTracks, true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (localTracks.isEmpty()) {
                        callback.onError("Failed to load audio list");
                    }
                }
            } else {
                // No internet
                if (localTracks.isEmpty()) {
                    callback.onError("No internet connection. Please connect to load bhajans.");
                }
            }

            // Always signal completion
            callback.onComplete();
        }).start();
    }

    public static void syncFromNetwork(Context context, LoadCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            if (!isNetworkAvailable(app)) {
                callback.onComplete();
                return;
            }

            try {
                List<AudioTrack> networkTracks = parseTracks(fetch(MANIFEST_URL));

                if (!networkTracks.isEmpty()) {
                    AudioDatabase db = AudioDatabase.getInstance(app);
                    db.saveTracks(networkTracks);
                    List<AudioTrack> mergedTracks = db.getAllTracks();
                    reconcile(app, db, mergedTracks);
                    callback.onTracksLoaded(mergedTracks, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            callback.onComplete();
        }).start();
    }

    /**
     * Brings each track's download state in line with what is really on disk / in
     * DownloadManager:
     *  - flagged downloaded but the file is gone       -> not downloaded
     *  - not flagged but the file exists (the screen that would have recorded the
     *    finished download was closed meanwhile)       -> downloaded
     *  - still queued / running in DownloadManager      -> downloading
     */
    public static void reconcile(Context context, AudioDatabase db, List<AudioTrack> tracks) {
        java.util.Set<String> active = DownloadHelper.activeTrackIds(context);

        for (AudioTrack t : tracks) {
            java.io.File local = DownloadHelper.getLocalFile(context, t.id);
            boolean downloading = active.contains(t.id);

            if (t.isDownloaded) {
                boolean present = t.localPath != null && new java.io.File(t.localPath).exists();
                if (!present && local != null) {
                    // Path moved (e.g. storage remounted) but the file is there.
                    t.localPath = local.getAbsolutePath();
                    db.markDownloaded(t.id, t.localPath);
                } else if (!present) {
                    db.markNotDownloaded(t.id);
                    t.isDownloaded = false;
                    t.localPath = null;
                }
            } else if (local != null && !downloading) {
                // A partly written file also exists while a download is running, hence the check.
                t.isDownloaded = true;
                t.localPath = local.getAbsolutePath();
                db.markDownloaded(t.id, t.localPath);
            }

            if (t.isDownloaded) t.downloadState = AudioTrack.STATE_DOWNLOADED;
            else t.downloadState = downloading ? AudioTrack.STATE_DOWNLOADING : AudioTrack.STATE_NOT_DOWNLOADED;
        }
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
        // url.openStream() has no timeouts at all: on a flaky connection the sync would hang
        // forever and leave the refresh spinner / "isSyncing" flag stuck.
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP " + conn.getResponseCode());
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}