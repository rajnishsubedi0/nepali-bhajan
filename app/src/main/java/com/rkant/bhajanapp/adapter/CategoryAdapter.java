package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rkant.bhajanapp.R;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnClick { void onClick(String cat); }
    private List<String> cats; private Context ctx; private OnClick l; private int sel = 0;
    public CategoryAdapter(List<String> cats, Context ctx, OnClick l) { this.cats = cats; this.ctx = ctx; this.l = l; }

    /** Called by swipe gestures so chips stay in sync without re-triggering the click listener */
    public void selectExternal(int pos) {
        if (pos != sel) { int old = sel; sel = pos; notifyItemChanged(old); notifyItemChanged(sel); }
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_category, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int p) {
        h.t.setText(cats.get(p));
        boolean s = p == sel;
        h.t.setBackgroundResource(s ? R.drawable.bg_button_accent : R.drawable.bg_pill);
        h.t.setTextColor(ctx.getColor(s ? R.color.on_accent : R.color.text_secondary));
        h.t.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos != sel) {
                int old = sel; sel = pos;
                notifyItemChanged(old); notifyItemChanged(sel);
                l.onClick(cats.get(sel));
            }
        });
    }
    @Override public int getItemCount() { return cats.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView t; VH(View v) { super(v); t = (TextView) v; }
    }
}