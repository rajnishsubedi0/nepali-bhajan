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

/**
 * List of bhajan categories (Category tab, Favourite sub-categories,
 * Archive sub-categories). Same look as the audio playlist rows but
 * counts "bhajans" instead of "tracks" and can pin a built-in
 * "All ..." row that has no delete button.
 */
public class BhajanCategoryAdapter extends RecyclerView.Adapter<BhajanCategoryAdapter.VH> {

    /** Click callback value for the pinned "All ..." row. */
    public static final String ALL_ITEM = "__all__";

    public interface OnCategoryAction {
        void onCategoryClick(String name);
        void onCategoryDelete(String name);
    }

    private final Context ctx;
    private final OnCategoryAction listener;
    private List<String> names = new ArrayList<>();
    private Map<String, Integer> counts = new HashMap<>();
    private String allItemName = null;
    private int allItemCount = 0;

    public BhajanCategoryAdapter(Context ctx, OnCategoryAction listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    /** Pinned first row without a delete button. Pass null to hide it. */
    public void setAllItem(String name, int count) {
        this.allItemName = name;
        this.allItemCount = count;
    }

    public void update(List<String> names, Map<String, Integer> counts) {
        this.names = names == null ? new ArrayList<>() : names;
        this.counts = counts == null ? new HashMap<>() : counts;
        notifyDataSetChanged();
    }

    private boolean hasAllItem() { return allItemName != null; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_playlist, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        // Pinned "All Favourites" / "All Archived" row
        if (hasAllItem() && position == 0) {
            holder.title.setText(allItemName);
            holder.subtitle.setText(countText(allItemCount));
            holder.delete.setVisibility(View.GONE);
            holder.row.setOnClickListener(v -> listener.onCategoryClick(ALL_ITEM));
            return;
        }

        String name = hasAllItem() ? names.get(position - 1) : names.get(position);
        int count = counts.containsKey(name) ? counts.get(name) : 0;
        holder.title.setText(name);
        holder.subtitle.setText(countText(count));
        holder.delete.setVisibility(View.VISIBLE);
        holder.row.setOnClickListener(v -> listener.onCategoryClick(name));
        holder.delete.setOnClickListener(v -> listener.onCategoryDelete(name));
    }

    private String countText(int count) {
        return count + (count == 1 ? " bhajan" : " bhajans");
    }

    @Override
    public int getItemCount() {
        int size = names == null ? 0 : names.size();
        return size + (hasAllItem() ? 1 : 0);
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