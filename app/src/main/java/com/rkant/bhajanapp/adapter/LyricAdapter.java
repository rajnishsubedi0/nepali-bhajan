package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.utils.BhajanCategories;
import com.rkant.bhajanapp.utils.Helper;
import java.util.List;

/** Title header followed by lyric lines; blank source lines become stanza gaps. */
public class LyricAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_LINE = 1;

    private final List<String> lyrics;
    private final Context ctx;
    private final String title, type, category;

    public LyricAdapter(List<String> lyrics, Context ctx) { this(lyrics, ctx, null, null, null); }

    public LyricAdapter(List<String> lyrics, Context ctx, String title, String type, String category) {
        this.lyrics = lyrics; this.ctx = ctx;
        this.title = title; this.type = type; this.category = category;
    }

    private boolean hasHeader() { return title != null && !title.isEmpty(); }

    @Override public int getItemViewType(int position) {
        return hasHeader() && position == 0 ? TYPE_HEADER : TYPE_LINE;
    }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
        LayoutInflater inf = LayoutInflater.from(ctx);
        if (viewType == TYPE_HEADER) return new HeaderVH(inf.inflate(R.layout.item_lyric_header, p, false));
        return new LineVH(inf.inflate(R.layout.item_lyric, p, false));
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int p) {
        if (holder instanceof HeaderVH) {
            HeaderVH h = (HeaderVH) holder;
            h.title.setText(title);
            boolean hasType = type != null && !type.isEmpty();
            h.type.setText(type);
            h.type.setVisibility(hasType ? View.VISIBLE : View.GONE);
            boolean hasCat = category != null && !category.isEmpty();
            h.tag.setVisibility(hasCat ? View.VISIBLE : View.GONE);
            if (hasCat) {
                h.tag.setBackgroundResource(BhajanCategories.gradientFor(category));
                h.tag.setText(BhajanCategories.nepaliFor(category));
            }
            return;
        }

        int index = hasHeader() ? p - 1 : p;
        String line = lyrics.get(index);
        TextView t = ((LineVH) holder).text;
        ViewGroup.LayoutParams lp = t.getLayoutParams();
        float density = ctx.getResources().getDisplayMetrics().density;

        if (line.trim().isEmpty()) {
            // A single gap between stanzas; leading / repeated blanks collapse to nothing.
            boolean collapse = index == 0 || lyrics.get(index - 1).trim().isEmpty();
            t.setText("");
            t.setPadding(0, 0, 0, 0);
            lp.height = collapse ? 0 : (int) (18 * density);
        } else {
            t.setText(line.trim());
            t.setTextSize(Helper.lyricSize(ctx));
            int pad = (int) (5 * density);
            t.setPadding(0, pad, 0, pad);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        t.setLayoutParams(lp);
    }

    @Override public int getItemCount() { return lyrics.size() + (hasHeader() ? 1 : 0); }

    static class LineVH extends RecyclerView.ViewHolder {
        TextView text; LineVH(View v) { super(v); text = v.findViewById(R.id.tv_lyric); }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView title, tag, type;
        HeaderVH(View v) {
            super(v);
            title = v.findViewById(R.id.tv_head_title);
            tag = v.findViewById(R.id.tv_head_tag);
            type = v.findViewById(R.id.tv_head_type);
        }
    }
}
