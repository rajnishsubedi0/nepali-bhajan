package com.rkant.bhajanapp.model;

import android.net.Uri;
import java.io.File;

public class AudioTrack {
    public static final int STATE_NOT_DOWNLOADED = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_DOWNLOADED = 2;

    public String id;
    public String title;
    public String url;
    public int durationSec;
    public String category;
    public boolean isDownloaded;
    public String localPath;
    public int downloadState = STATE_NOT_DOWNLOADED;

    public AudioTrack(String id, String title, String url, int durationSec) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.durationSec = durationSec;
    }

    /**
     * Returns a proper URI string for ExoPlayer:
     * - file:// URI if downloaded locally
     * - http(s) URL if streaming online
     */
    public String playableUrl() {
        if (isDownloaded && localPath != null) {
            File f = new File(localPath);
            if (f.exists()) {
                return Uri.fromFile(f).toString();
            }
        }
        return url;
    }
}