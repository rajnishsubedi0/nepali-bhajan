package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.AudioTrack;
import com.rkant.bhajanapp.utils.AudioPreferences;
import com.rkant.bhajanapp.utils.Helper;

import java.util.List;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.VH> {

    public interface OnTrackAction {
        void onPlayClick(AudioTrack track, int position);
        void onDownloadClick(AudioTrack track);
        void onFavClick(AudioTrack track);
    }

    private List<AudioTrack> tracks;
    private Context ctx;
    private OnTrackAction listener;
    private String currentPlayingId = null;
    private boolean isPlaying = false;

    public AudioAdapter(List<AudioTrack> tracks, Context ctx, OnTrackAction listener) {
        this.tracks = tracks;
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setPlaybackState(String playingId, boolean playing) {
        this.currentPlayingId = playingId;
        this.isPlaying = playing;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_audio, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AudioTrack t = tracks.get(position);
        h.title.setText(t.title);

        if (t.durationSec > 0) h.duration.setText(Helper.formatTime(t.durationSec * 1000L));
        else h.duration.setText("Stream");

        boolean isFav = AudioPreferences.isFav(ctx, t.id);
        h.fav.setVisibility(isFav ? View.VISIBLE : View.GONE);

        h.download.setColorFilter(ctx.getColor(t.isDownloaded ? R.color.accent : R.color.text_secondary));

        boolean isThis = t.id.equals(currentPlayingId);
        h.row.setBackgroundColor(isThis ? ctx.getColor(R.color.muted) : 0x00000000);

        if (isThis) {
            h.play.setImageResource(isPlaying ? R.drawable.ic_equalizer : R.drawable.ic_play_arrow);
            h.play.setColorFilter(ctx.getColor(R.color.accent));
        } else {
            h.play.setImageResource(R.drawable.ic_play_arrow);
            h.play.setColorFilter(ctx.getColor(R.color.text_secondary));
        }

        h.row.setOnClickListener(v -> listener.onPlayClick(t, h.getAdapterPosition()));
        h.download.setOnClickListener(v -> listener.onDownloadClick(t));
        h.fav.setOnClickListener(v -> listener.onFavClick(t));
    }

    @Override
    public int getItemCount() { return tracks.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, duration;
        ImageView fav, download, play;
        View row;

        VH(View v) {
            super(v);
            row = v.findViewById(R.id.row);
            title = v.findViewById(R.id.tv_title);
            duration = v.findViewById(R.id.tv_duration);
            fav = v.findViewById(R.id.iv_fav);
            download = v.findViewById(R.id.iv_download);
            play = v.findViewById(R.id.iv_play);
        }
    }
}