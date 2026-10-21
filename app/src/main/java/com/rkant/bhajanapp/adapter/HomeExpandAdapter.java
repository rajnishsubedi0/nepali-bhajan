package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.utils.BhajanCategories;

import java.util.List;

/**
 * Vertical bhajan rows for the expanded Home sections.
 * Swipe left/right reveals a "Remove" action; tapping it slides the card
 * away and removes the row with a smooth RecyclerView animation.
 */
public class HomeExpandAdapter extends RecyclerView.Adapter<HomeExpandAdapter.VH> {

    public interface OnRemove { void onRemove(Bhajan b, int position); }
    public interface OnClick  { void onClick(Bhajan b); }

    private List<Bhajan> list;
    private final Context ctx;
    private final OnRemove removeListener;
    private final OnClick clickListener;
    private final boolean showHeart;

    public HomeExpandAdapter(List<Bhajan> list, Context ctx, boolean showHeart,
                             OnRemove removeListener, OnClick clickListener) {
        this.list = list;
        this.ctx = ctx;
        this.showHeart = showHeart;
        this.removeListener = removeListener;
        this.clickListener = clickListener;
    }

    public void update(List<Bhajan> n) {
        this.list = n;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_home_swipe_row, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Bhajan b = list.get(pos);
        h.title.setText(b.titleNepali);

        StringBuilder sub = new StringBuilder();
        if (b.type != null && !b.type.isEmpty()) sub.append(b.type);
        if (b.category != null && !b.category.isEmpty()) {
            if (sub.length() > 0) sub.append("  \u00B7  ");
            sub.append(BhajanCategories.nepaliFor(b.category));
        }
        h.sub.setText(sub.toString());
        h.sub.setVisibility(sub.length() > 0 ? View.VISIBLE : View.GONE);

        // Heart is always hidden on Home expanded rows
        h.fav.setVisibility(View.GONE);

        h.cardContainer.setTranslationX(0);
        h.cardContainer.setAlpha(1f);
        h.reveal.setVisibility(View.GONE);

        h.row.setOnClickListener(v -> {
            if (Math.abs(h.cardContainer.getTranslationX()) > 1f) {
                h.cardContainer.animate().translationX(0).setDuration(120).start();
                h.reveal.setVisibility(View.GONE);
                return;
            }
            if (clickListener != null) clickListener.onClick(b);
        });

        setupSwipe(h, b);
    }

    private void setupSwipe(VH h, Bhajan b) {
        final float density = ctx.getResources().getDisplayMetrics().density;
        final float revealWidth = 90 * density;
        final float threshold = 150 * density;
        final float startSlop = 26 * density;
        final float[] startX = {0};
        final boolean[] swiping = {false};

        h.row.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    swiping[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    if (!swiping[0] && Math.abs(dx) > startSlop) {
                        swiping[0] = true;
                        v.setPressed(false);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swiping[0]) {
                        float shift = Math.max(-revealWidth, Math.min(revealWidth, dx));
                        h.cardContainer.setTranslationX(shift);
                        if (Math.abs(shift) > 24 * density) h.reveal.setVisibility(View.VISIBLE);
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (swiping[0]) {
                        float upDx = event.getRawX() - startX[0];
                        if (Math.abs(upDx) > threshold) {
                            float holdX = upDx > 0 ? revealWidth : -revealWidth;
                            h.cardContainer.animate().translationX(holdX).setDuration(120).start();
                            h.reveal.setVisibility(View.VISIBLE);
                            h.reveal.setOnClickListener(rv -> animateRemoval(h, b));
                        } else {
                            h.cardContainer.animate().translationX(0).setDuration(120).start();
                            h.reveal.setVisibility(View.GONE);
                        }
                        swiping[0] = false;
                        return true;
                    }
                    swiping[0] = false;
                    return false;
            }
            return false;
        });
    }

    /** Slides the card off-screen, then removes the row with animation. */
    private void animateRemoval(VH h, Bhajan b) {
        final int position = h.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;
        float direction = h.cardContainer.getTranslationX() >= 0 ? 1f : -1f;
        h.reveal.setVisibility(View.GONE);
        h.cardContainer.animate()
                .translationX(direction * h.cardContainer.getWidth())
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    // Remove from the (shared) list first, with the removal animation,
                    // then notify the Activity so it can update Helper + the horizontal strip.
                    int idx = list.indexOf(b);
                    if (idx >= 0) {
                        list.remove(idx);
                        notifyItemRemoved(idx);
                    }
                    if (removeListener != null) removeListener.onRemove(b, position);
                })
                .start();
    }

    @Override
    public int getItemCount() { return list == null ? 0 : list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, sub;
        ImageView fav;
        View row, cardContainer, reveal;

        VH(View v) {
            super(v);
            row = v.findViewById(R.id.row);
            cardContainer = v.findViewById(R.id.card_container);
            reveal = v.findViewById(R.id.reveal);
            title = v.findViewById(R.id.tv_title);
            sub = v.findViewById(R.id.tv_sub);
            fav = v.findViewById(R.id.iv_fav);
        }
    }
}