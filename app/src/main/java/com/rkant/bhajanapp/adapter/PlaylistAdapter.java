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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

    public interface OnPlaylistAction {
        void onPlaylistClick(String name);
        void onPlaylistDelete(String name);
    }

    private final Context ctx;
    private final OnPlaylistAction listener;

    private List<String> names = new ArrayList<>();
    private Map<String, Integer> counts = new HashMap<>();

    public PlaylistAdapter(Context ctx, OnPlaylistAction listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void update(List<String> names, Map<String, Integer> counts) {
        this.names = names == null ? new ArrayList<>() : names;
        this.counts = counts == null ? new HashMap<>() : counts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_playlist, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String name = names.get(position);
        int count = counts.containsKey(name) ? counts.get(name) : 0;

        holder.title.setText(name);
        holder.subtitle.setText(count + (count == 1 ? " track" : " tracks"));

        holder.row.setOnClickListener(v -> listener.onPlaylistClick(name));

        holder.delete.setOnClickListener(v -> listener.onPlaylistDelete(name));
    }

    @Override
    public int getItemCount() {
        return names == null ? 0 : names.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View row;
        TextView title, subtitle;
        ImageView delete;

        VH(View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.row);
            title = itemView.findViewById(R.id.tv_playlist_name);
            subtitle = itemView.findViewById(R.id.tv_playlist_count);
            delete = itemView.findViewById(R.id.iv_playlist_delete);
        }
    }
}