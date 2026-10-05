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
    private List<Bhajan> list; private Context ctx;
    public BhajanAdapter(List<Bhajan> list, Context ctx) { this.list = list; this.ctx = ctx; }
    public void update(List<Bhajan> n) { this.list = n; notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_bhajan, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Bhajan b = list.get(pos);
        h.number.setText(Helper.getNepaliNumber(pos + 1));
        h.number.setTextSize(Helper.listNumberSize(ctx)); // number scales WITH the list font size
        h.title.setText(b.titleNepali);
        h.title.setTextSize(Helper.listTitleSize(ctx));
        h.sub.setText(b.type);
        h.fav.setVisibility(Helper.isFav(ctx, b.id) ? View.VISIBLE : View.GONE);

        h.row.setOnClickListener(v -> {
            Intent i = new Intent(ctx, DetailActivity.class);
            i.putExtra("ID", b.id); i.putExtra("TITLE", b.titleNepali);
            ctx.startActivity(i);
        });

        h.row.setOnLongClickListener(v -> {
            boolean fav = Helper.isFav(ctx, b.id);
            if (!fav) {
                Helper.showConfirm(ctx, "Add to favourite",
                        "Add \"" + b.titleNepali + "\" to your favourite list?", "Add", false, () -> {
                            Helper.toggleFav(ctx, b.id);
                            notifyDataSetChanged();
                            Toast.makeText(ctx, "Added to favourites", Toast.LENGTH_SHORT).show();
                        });
            } else {
                Helper.showConfirm(ctx, "Remove from favourite",
                        "Remove \"" + b.titleNepali + "\" from your favourite list?", "Remove", true, () -> {
                            Helper.toggleFav(ctx, b.id);
                            notifyDataSetChanged();
                            Toast.makeText(ctx, "Removed", Toast.LENGTH_SHORT).show();
                        });
            }
            return true;
        });
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