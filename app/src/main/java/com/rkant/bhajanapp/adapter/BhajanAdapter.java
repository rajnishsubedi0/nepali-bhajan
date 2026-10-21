package com.rkant.bhajanapp.adapter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.ui.DetailActivity;
import com.rkant.bhajanapp.utils.BhajanCategories;
import com.rkant.bhajanapp.utils.Helper;

import java.util.List;

public class BhajanAdapter extends RecyclerView.Adapter<BhajanAdapter.VH> {

    public interface OnFavChanged { void onChanged(boolean nowFavourite); }
    public interface OnArchiveChanged { void onArchived(String bhajanId); }
    public interface OnBhajanOptions { void onOptions(Bhajan bhajan, int position); }

    private List<Bhajan> list;
    private final Context ctx;
    private OnFavChanged favListener;
    private OnArchiveChanged archiveListener;
    private OnBhajanOptions optionsListener;
    private boolean swipeArchiveEnabled = true;
    private final Handler swipeHandler = new Handler(Looper.getMainLooper());

    // Deity dot drawables
    private static final int[] DOT_DRAWABLES = {
            R.drawable.dot_krishna, R.drawable.dot_shiva, R.drawable.dot_hari,
            R.drawable.dot_ram, R.drawable.dot_other
    };

    public BhajanAdapter(List<Bhajan> list, Context ctx) {
        this.list = list;
        this.ctx = ctx;
    }

    public void update(List<Bhajan> n) {
        this.list = n;
        notifyDataSetChanged();
    }

    public void setOnFavChanged(OnFavChanged l) { this.favListener = l; }
    public void setOnArchiveChanged(OnArchiveChanged l) { this.archiveListener = l; }
    public void setOnBhajanOptions(OnBhajanOptions l) { this.optionsListener = l; }
    public void setSwipeArchiveEnabled(boolean e) { this.swipeArchiveEnabled = e; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_bhajan, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Bhajan b = list.get(pos);
        boolean fav = Helper.isFav(ctx, b.id);
        boolean archived = Helper.isArchived(ctx, b.id);

        h.number.setText(Helper.getNepaliNumber(pos + 1));
        h.number.setTextSize(Helper.listNumberSize(ctx));
        h.title.setText(b.titleNepali);
        h.title.setTextSize(Helper.listTitleSize(ctx));

        // Slim sub-row: dot + type text
        int dotIndex = BhajanCategories.indexOf(b.category);
        h.deityDot.setBackgroundResource(DOT_DRAWABLES[dotIndex]);

        if (b.type != null && !b.type.isEmpty()) {
            h.sub.setText(b.type);
            h.sub.setVisibility(View.VISIBLE);
        } else {
            h.sub.setVisibility(View.GONE);
        }

        bindHeart(h.fav, fav);
        h.row.setBackgroundResource(archived ? R.drawable.bg_card_archived : R.drawable.bg_card);

        h.cardContainer.setTranslationX(0);
        h.archiveReveal.setVisibility(View.GONE);
        cancelPendingLongPress(h.row);

        h.row.setOnClickListener(v -> {
            if (Math.abs(h.cardContainer.getTranslationX()) > 1f) {
                h.cardContainer.animate().translationX(0).setDuration(120).start();
                h.archiveReveal.setVisibility(View.GONE);
                return;
            }
            Intent i = new Intent(ctx, DetailActivity.class);
            i.putExtra("ID", b.id);
            i.putExtra("TITLE", b.titleNepali);
            ctx.startActivity(i);
        });

        h.fav.setOnClickListener(v -> toggleFav(b, h));

        if (swipeArchiveEnabled && !archived) {
            h.row.setOnLongClickListener(null);
            setupSwipe(h, b);
        } else {
            h.row.setOnTouchListener(null);
            h.row.setOnLongClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION && optionsListener != null) {
                    optionsListener.onOptions(list.get(p), p);
                    return true;
                }
                return false;
            });
        }
    }

    private void cancelPendingLongPress(View row) {
        Object tag = row.getTag();
        if (tag instanceof Runnable) {
            swipeHandler.removeCallbacks((Runnable) tag);
            row.setTag(null);
        }
    }

    private void setupSwipe(VH h, Bhajan b) {
        final float density = ctx.getResources().getDisplayMetrics().density;
        final float revealWidth = 90 * density;
        final float threshold = 150 * density;
        final float startSlop = 26 * density;
        final float[] startX = {0};
        final float[] startY = {0};
        final boolean[] swiping = {false};
        final boolean[] longPressFired = {false};

        final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                if (swiping[0]) return;
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION && optionsListener != null) {
                    longPressFired[0] = true;
                    optionsListener.onOptions(list.get(p), p);
                }
            }
        };

        h.row.setTag(longPress);
        h.row.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    swiping[0] = false;
                    longPressFired[0] = false;
                    swipeHandler.removeCallbacks(longPress);
                    swipeHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    float dy = event.getRawY() - startY[0];
                    if (!swiping[0] && Math.abs(dx) > startSlop && Math.abs(dx) > Math.abs(dy) * 2.2f) {
                        swiping[0] = true;
                        swipeHandler.removeCallbacks(longPress);
                        v.setPressed(false);
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swiping[0]) {
                        float shift = Math.max(-revealWidth, Math.min(revealWidth, dx));
                        h.cardContainer.setTranslationX(shift);
                        if (Math.abs(shift) > 24 * density) {
                            h.archiveReveal.setVisibility(View.VISIBLE);
                        }
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    swipeHandler.removeCallbacks(longPress);
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (swiping[0]) {
                        float upDx = event.getRawX() - startX[0];
                        if (Math.abs(upDx) > threshold) {
                            float holdX = upDx > 0 ? revealWidth : -revealWidth;
                            h.cardContainer.animate().translationX(holdX).setDuration(120).start();
                            h.archiveReveal.setVisibility(View.VISIBLE);
                            h.tvArchiveAction.setOnClickListener(av -> doArchive(b, h));
                            h.archiveReveal.setOnClickListener(av -> doArchive(b, h));
                        } else {
                            h.cardContainer.animate().translationX(0).setDuration(120).start();
                            h.archiveReveal.setVisibility(View.GONE);
                        }
                        swiping[0] = false;
                        return true;
                    }
                    swiping[0] = false;
                    return longPressFired[0];
            }
            return false;
        });
    }

    private void doArchive(Bhajan b, VH h) {
        Helper.toggleArchive(ctx, b.id);
        Toast.makeText(ctx, "Bhajan archived", Toast.LENGTH_SHORT).show();
        if (archiveListener != null) {
            archiveListener.onArchived(b.id);
        }
    }

    private void toggleFav(Bhajan b, VH h) {
        boolean currentlyFav = Helper.isFav(ctx, b.id);
        if (currentlyFav) {
            Helper.showConfirm(ctx, "Remove from Favourites",
                    "Remove \u201C" + b.titleNepali + "\u201D from your favourite list?",
                    "Remove", true, () -> {
                        Helper.toggleFav(ctx, b.id);
                        Toast.makeText(ctx, "Removed from favourites", Toast.LENGTH_SHORT).show();
                        if (favListener != null) {
                            favListener.onChanged(false);
                        } else {
                            int p = h.getAdapterPosition();
                            if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
                        }
                    });
        } else {
            Helper.toggleFav(ctx, b.id);
            Toast.makeText(ctx, "Added to favourites", Toast.LENGTH_SHORT).show();
            if (favListener != null) {
                favListener.onChanged(true);
            } else {
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
            }
        }
    }

    private void bindHeart(ImageView iv, boolean fav) {
        iv.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        iv.setColorFilter(ctx.getColor(fav ? R.color.accent : R.color.text_secondary));
    }

    @Override
    public int getItemCount() { return list == null ? 0 : list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView number, title, sub, tvArchiveAction;
        ImageView fav;
        View row, cardContainer, archiveReveal, deityDot;

        VH(View v) {
            super(v);
            row = v.findViewById(R.id.row);
            cardContainer = v.findViewById(R.id.card_container);
            archiveReveal = v.findViewById(R.id.archive_reveal);
            tvArchiveAction = v.findViewById(R.id.tv_archive_action);
            number = v.findViewById(R.id.tv_number);
            title = v.findViewById(R.id.tv_title);
            sub = v.findViewById(R.id.tv_sub);
            deityDot = v.findViewById(R.id.deity_dot);
            fav = v.findViewById(R.id.iv_fav);
        }
    }
}