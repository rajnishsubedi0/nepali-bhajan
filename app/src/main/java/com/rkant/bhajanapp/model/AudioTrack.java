package com.rkant.bhajanapp.model;

public class AudioTrack {
    public String id;
    public String title;
    public String url;
    public int durationSec;      // -1 = unknown/live stream
    public boolean isDownloaded;
    public String localPath;

    public AudioTrack(String id, String title, String url, int durationSec) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.durationSec = durationSec;
    }

    /**
     * Returns the local file path if the track has been downloaded,
     * otherwise returns the remote streaming URL.
     */
    public String playableUrl() {
        return (isDownloaded && localPath != null) ? localPath : url;
    }
}