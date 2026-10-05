package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.utils.Helper;
import java.util.List;

public class LyricAdapter extends RecyclerView.Adapter<LyricAdapter.VH> {
    private List<String> lyrics; private Context ctx;
    public LyricAdapter(List<String> lyrics, Context ctx) { this.lyrics = lyrics; this.ctx = ctx; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_lyric, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int p) {
        String line = lyrics.get(p);
        h.text.setText(line);
        h.text.setTextSize(Helper.lyricSize(ctx));
        h.text.setVisibility(line.trim().isEmpty() ? View.GONE : View.VISIBLE);
    }
    @Override public int getItemCount() { return lyrics.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView text; VH(View v) { super(v); text = v.findViewById(R.id.tv_lyric); }
    }
}