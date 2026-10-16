package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.ui.DetailActivity;
import com.rkant.bhajanapp.utils.Helper;
import java.util.List;

public class BhajanAdapter extends RecyclerView.Adapter<BhajanAdapter.VH> {
    /** Called after a favourite was toggled so the screen can refresh counts / lists. */
    public interface OnFavChanged { void onChanged(boolean nowFavourite); }

    private List<Bhajan> list; private final Context ctx;
    private OnFavChanged favListener;

    public BhajanAdapter(List<Bhajan> list, Context ctx) { this.list = list; this.ctx = ctx; }
    public void update(List<Bhajan> n) { this.list = n; notifyDataSetChanged(); }
    public void setOnFavChanged(OnFavChanged l) { this.favListener = l; }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_bhajan, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Bhajan b = list.get(pos);
        boolean fav = Helper.isFav(ctx, b.id);
        h.number.setText(Helper.getNepaliNumber(pos + 1));
        h.number.setTextSize(Helper.listNumberSize(ctx));
        h.title.setText(b.titleNepali);
        h.title.setTextSize(Helper.listTitleSize(ctx));
        h.sub.setText(b.type);
        h.sub.setVisibility(b.type == null || b.type.isEmpty() ? View.GONE : View.VISIBLE);
        bindHeart(h.fav, fav);

        h.row.setOnClickListener(v -> {
            Intent i = new Intent(ctx, DetailActivity.class);
            i.putExtra("ID", b.id); i.putExtra("TITLE", b.titleNepali);
            ctx.startActivity(i);
        });

        h.fav.setOnClickListener(v -> toggle(b, h));
        h.row.setOnLongClickListener(v -> {
            boolean isFav = Helper.isFav(ctx, b.id);
            if (!isFav) {
                Helper.showConfirm(ctx, "Add to favourite",
                        "Add \"" + b.titleNepali + "\" to your favourite list?", "Add", false, () -> toggle(b, h));
            } else {
                Helper.showConfirm(ctx, "Remove from favourite",
                        "Remove \"" + b.titleNepali + "\" from your favourite list?", "Remove", true, () -> toggle(b, h));
            }
            return true;
        });
    }

    private void toggle(Bhajan b, VH h) {
        Helper.toggleFav(ctx, b.id);
        boolean now = Helper.isFav(ctx, b.id);
        Toast.makeText(ctx, now ? "Added to favourites" : "Removed from favourites", Toast.LENGTH_SHORT).show();
        if (favListener != null) {
            favListener.onChanged(now);
        } else {
            int p = h.getAdapterPosition();
            if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
        }
    }

    private void bindHeart(ImageView iv, boolean fav) {
        iv.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        iv.setColorFilter(ctx.getColor(fav ? R.color.red : R.color.text_secondary));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView number, title, sub; ImageView fav; View row;
        VH(View v) { super(v);
            row = v.findViewById(R.id.row); number = v.findViewById(R.id.tv_number);
            title = v.findViewById(R.id.tv_title); sub = v.findViewById(R.id.tv_sub); fav = v.findViewById(R.id.iv_fav);
        }
    }
}
