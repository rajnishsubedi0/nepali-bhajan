package com.rkant.bhajanapp.utils;

import com.rkant.bhajanapp.model.AudioTrack;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class M3U8Parser {

    /**
     * Fetches and parses an M3U8 file.
     * - If it is a MEDIA playlist (#EXTINF) it reads each track.
     * - If it is a MASTER playlist (#EXT-X-STREAM-INF) it picks the highest
     *   quality stream and creates a demo playlist so shuffle/repeat/next work.
     */
    public static List<AudioTrack> fetchAndParse(String urlString) {
        List<AudioTrack> tracks = new ArrayList<>();
        try {
            URL url = new URL(urlString);
            String baseUrl = urlString.substring(0, urlString.lastIndexOf('/') + 1);

            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) lines.add(line.trim());
            reader.close();

            boolean isMaster = false;
            for (String l : lines) if (l.contains("#EXT-X-STREAM-INF")) { isMaster = true; break; }

            if (isMaster) {
                // Pick the highest bandwidth variant stream
                String bestStream = null;
                int bestBandwidth = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("#EXT-X-STREAM-INF")) {
                        int bw = extractBandwidth(lines.get(i));
                        if (i + 1 < lines.size() && !lines.get(i + 1).startsWith("#")) {
                            if (bw > bestBandwidth) {
                                bestBandwidth = bw;
                                bestStream = lines.get(i + 1);
                            }
                        }
                    }
                }
                if (bestStream != null) {
                    if (!bestStream.startsWith("http")) bestStream = baseUrl + bestStream;
                    // Build a demo playlist pointing to the same stream so
                    // next / previous / shuffle / repeat are functional.
                    String[] names = {"Bhajan Track 1", "Bhajan Track 2", "Bhajan Track 3"};
                    for (int i = 0; i < names.length; i++) {
                        tracks.add(new AudioTrack("track_" + i, names[i], bestStream, -1));
                    }
                }
            } else {
                // Standard media playlist with #EXTINF entries
                String title = "";
                int duration = -1;
                for (int i = 0; i < lines.size(); i++) {
                    String l = lines.get(i);
                    if (l.startsWith("#EXTINF:")) {
                        String[] parts = l.split(",", 2);
                        try { duration = (int) Float.parseFloat(parts[0].replace("#EXTINF:", "").trim()); }
                        catch (Exception e) { duration = -1; }
                        title = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "Bhajan";
                    } else if (!l.startsWith("#") && !l.isEmpty()) {
                        String trackUrl = l;
                        if (!trackUrl.startsWith("http")) trackUrl = baseUrl + trackUrl;
                        tracks.add(new AudioTrack(String.valueOf(trackUrl.hashCode()), title, trackUrl, duration));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tracks;
    }

    private static int extractBandwidth(String line) {
        try {
            int idx = line.indexOf("BANDWIDTH=");
            if (idx == -1) return 0;
            String sub = line.substring(idx + "BANDWIDTH=".length());
            int end = sub.indexOf(',');
            if (end == -1) end = sub.length();
            return Integer.parseInt(sub.substring(0, end).trim());
        } catch (Exception e) { return 0; }
    }
}