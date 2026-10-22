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
import com.rkant.bhajanapp.utils.Helper;

import java.util.List;

public class AudioAdapter extends RecyclerView.Adapter<AudioAdapter.VH> {

    public interface OnTrackAction {
        void onRowClick(AudioTrack track, int position);
        void onPlayPauseClick(AudioTrack track, int position);
        void onOptionsClick(AudioTrack track, int position);
    }

    private final List<AudioTrack> tracks;
    private final Context ctx;
    private final OnTrackAction listener;
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

    /** Removes one row with the built-in RecyclerView removal animation. */
    public void removeItemAt(int position) {
        if (tracks == null || position < 0 || position >= tracks.size()) return;
        tracks.remove(position);
        notifyItemRemoved(position);
    }

    /** Finds the current adapter position of a track (by id). */
    public int positionOf(AudioTrack t) {
        if (t == null || t.id == null || tracks == null) return -1;
        for (int i = 0; i < tracks.size(); i++) {
            if (t.id.equals(tracks.get(i).id)) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_audio, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AudioTrack track = tracks.get(position);
        h.title.setText(track.title);
        if (track.durationSec > 0) {
            h.duration.setText(Helper.formatTime(track.durationSec * 1000L));
        } else {
            h.duration.setText("--:--");
        }

        // Favourite indicator
        h.favInd.setVisibility(track.isFavourite ? View.VISIBLE : View.GONE);
        h.favInd.setColorFilter(ctx.getColor(R.color.accent));

        // Download indicator
        if (track.isDownloaded) {
            h.dlInd.setVisibility(View.VISIBLE);
            h.dlInd.setImageResource(R.drawable.ic_check);
            h.dlInd.setColorFilter(ctx.getColor(R.color.accent));
        } else if (track.downloadState == AudioTrack.STATE_DOWNLOADING) {
            h.dlInd.setVisibility(View.VISIBLE);
            h.dlInd.setImageResource(R.drawable.ic_download);
            h.dlInd.setColorFilter(ctx.getColor(R.color.accent));
        } else {
            h.dlInd.setVisibility(View.GONE);
        }

        boolean archived = Helper.isAudioArchived(ctx, track.id);
        boolean isThisTrack = track.id != null && track.id.equals(currentPlayingId);
        h.row.setBackgroundResource(isThisTrack ? R.drawable.bg_card_playing
                : archived ? R.drawable.bg_card_archived : R.drawable.bg_card);

        if (isThisTrack) {
            h.play.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            h.play.setColorFilter(ctx.getColor(R.color.accent));
        } else {
            h.play.setImageResource(R.drawable.ic_play_arrow);
            h.play.setColorFilter(ctx.getColor(R.color.text_secondary));
        }

        h.row.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onRowClick(track, pos);
        });
        h.row.setOnLongClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onOptionsClick(track, pos);
            return true;
        });
        h.play.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onPlayPauseClick(track, pos);
        });
    }

    @Override
    public int getItemCount() {
        return tracks == null ? 0 : tracks.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, duration;
        ImageView favInd, dlInd, play;
        View row;

        VH(View v) {
            super(v);
            row = v.findViewById(R.id.row);
            title = v.findViewById(R.id.tv_title);
            duration = v.findViewById(R.id.tv_duration);
            favInd = v.findViewById(R.id.iv_fav_ind);
            dlInd = v.findViewById(R.id.iv_dl_ind);
            play = v.findViewById(R.id.iv_play);
        }
    }
}