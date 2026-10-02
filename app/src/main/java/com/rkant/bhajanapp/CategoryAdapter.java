package com.rkant.bhajanapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(String categoryName);
    }

    public static class CategoryItem {
        public String name;
        public int iconResId;

        public CategoryItem(String name, int iconResId) {
            this.name = name;
            this.iconResId = iconResId;
        }
    }

    private final Context context;
    private final List<CategoryItem> categories;
    private final OnCategoryClickListener listener;
    private int selectedPosition = 0;

    public CategoryAdapter(Context context, List<CategoryItem> categories, OnCategoryClickListener listener) {
        this.context = context;
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryItem item = categories.get(position);
        holder.chipText.setText(item.name);
        holder.chipIcon.setImageResource(item.iconResId);

        boolean isSelected = (position == selectedPosition);

        if (isSelected) {
            holder.chipContainer.setBackgroundResource(R.drawable.bg_chip_selected);
            holder.chipText.setTextColor(ContextCompat.getColor(context, R.color.accent_icon));
            holder.chipIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_icon));
        } else {
            holder.chipContainer.setBackgroundResource(R.drawable.bg_chip_unselected);
            holder.chipText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.chipIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
        }

        holder.chipContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int clickedPosition = holder.getAdapterPosition();
                if (clickedPosition != RecyclerView.NO_POSITION && clickedPosition != selectedPosition) {
                    int previousSelected = selectedPosition;
                    selectedPosition = clickedPosition;
                    notifyItemChanged(previousSelected);
                    notifyItemChanged(selectedPosition);
                    if (listener != null) {
                        listener.onCategoryClick(categories.get(clickedPosition).name);
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        LinearLayout chipContainer;
        ImageView chipIcon;
        TextView chipText;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            chipContainer = itemView.findViewById(R.id.chip_container);
            chipIcon = itemView.findViewById(R.id.chip_icon);
            chipText = itemView.findViewById(R.id.chip_text);
        }
    }
}
