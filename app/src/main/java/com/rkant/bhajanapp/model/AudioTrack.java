package com.rkant.bhajanapp.model;

public class AudioTrack {
    public static final int STATE_NOT_DOWNLOADED = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_DOWNLOADED = 2;

    public String id;
    public String title;
    public String url;
    public int durationSec;      // -1 = unknown
    public String category;
    public boolean isDownloaded;
    public String localPath;
    public int downloadState = STATE_NOT_DOWNLOADED; // transient UI state

    public AudioTrack(String id, String title, String url, int durationSec) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.durationSec = durationSec;
    }

    /** Plays the offline MP3 if downloaded, otherwise streams the online URL. */
    public String playableUrl() {
        return (isDownloaded && localPath != null) ? localPath : url;
    }
}