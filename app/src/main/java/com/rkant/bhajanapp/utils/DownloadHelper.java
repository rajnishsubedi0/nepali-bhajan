package com.rkant.bhajanapp.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.rkant.bhajanapp.model.AudioTrack;

import java.io.File;

public class DownloadHelper {

    private static final String PREFS = "audio_download_meta";
    private static final String TRACK_PREFIX = "track_for_dl_";

    /** Starts an MP3 download into app-private storage (no permission needed). */
    public static long enqueue(Context c, AudioTrack track) {
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(track.url));
        req.setTitle(track.title);
        req.setDescription("Downloading bhajan");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(c, Environment.DIRECTORY_MUSIC, track.id + ".mp3");
        long id = dm.enqueue(req);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(TRACK_PREFIX + id, track.id).apply();
        return id;
    }

    public static String trackIdForDownload(Context c, long downloadId) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TRACK_PREFIX + downloadId, null);
    }

    public static File getLocalFile(Context c, String trackId) {
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (dir == null) return null;
        File f = new File(dir, trackId + ".mp3");
        return (f.exists() && f.length() > 0) ? f : null;
    }

    public static void delete(Context c, String trackId) {
        File f = getLocalFile(c, trackId);
        if (f != null) f.delete();
    }
}