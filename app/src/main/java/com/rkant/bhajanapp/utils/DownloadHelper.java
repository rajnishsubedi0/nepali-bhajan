package com.rkant.bhajanapp.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import com.rkant.bhajanapp.model.AudioTrack;

import java.io.File;

public class DownloadHelper {

    private static final String PREFS = "audio_download_meta";
    private static final String TRACK_PREFIX = "track_for_dl_";

    /**
     * Starts an MP3 download into app-private storage (no permission needed).
     * Returns -1 when this track is already being downloaded.
     */
    public static long enqueue(Context c, AudioTrack track) {
        if (isDownloading(c, track.id)) return -1;

        // DownloadManager refuses to overwrite an existing destination file
        // (ERROR_FILE_ALREADY_EXISTS), so clear leftovers from a failed / empty attempt.
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (dir != null) {
            File stale = new File(dir, track.id + ".mp3");
            if (stale.exists()) stale.delete();
        }

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

    /** True while DownloadManager still has a queued / running / paused job for this track. */
    public static boolean isDownloading(Context c, String trackId) {
        return trackId != null && activeTrackIds(c).contains(trackId);
    }

    /** Ids of all tracks DownloadManager is currently queueing / downloading (one query). */
    public static java.util.Set<String> activeTrackIds(Context c) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return ids;
        DownloadManager.Query q = new DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_PENDING | DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_PAUSED);
        Cursor cursor = null;
        try {
            cursor = dm.query(q);
            if (cursor == null) return ids;
            int idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
            while (cursor.moveToNext()) {
                String trackId = trackIdForDownload(c, cursor.getLong(idCol));
                if (trackId != null) ids.add(trackId);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return ids;
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