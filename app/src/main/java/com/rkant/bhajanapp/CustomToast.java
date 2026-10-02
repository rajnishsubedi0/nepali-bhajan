package com.rkant.bhajanapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

public class CustomToast {
    public static final int TYPE_SUCCESS = 1;
    public static final int TYPE_INFO = 2;
    public static final int TYPE_ERROR = 3;

    public static void show(Context context, String message, int type) {
        if (context == null) return;

        try {
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.custom_toast_layout, null);

            ImageView iconView = view.findViewById(R.id.toast_icon);
            TextView textView = view.findViewById(R.id.toast_text);

            if (textView != null) {
                textView.setText(message);
            }

            if (iconView != null) {
                if (type == TYPE_SUCCESS) {
                    iconView.setImageResource(R.drawable.ic_heart);
                    iconView.setVisibility(View.VISIBLE);
                    iconView.setImageTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.accent_icon)
                    ));
                } else if (type == TYPE_ERROR) {
                    iconView.setImageResource(R.drawable.ic_close_custom);
                    iconView.setVisibility(View.VISIBLE);
                    // Beautiful warning/error soft red
                    iconView.setImageTintList(ColorStateList.valueOf(
                            0xFFEF4444 // hex representation of #EF4444
                    ));
                } else {
                    // Default / Info: Use a beautiful heart or custom icon, let's use search icon or info.
                    // Since shiva_icon_vector might be complex or colored, let's check if the vector supports tinting.
                    // To be safe and premium, let's use the shiva icon, it's very custom and beautiful!
                    iconView.setImageResource(R.drawable.shiva_icon_vector);
                    iconView.setVisibility(View.VISIBLE);
                    iconView.setImageTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.accent_icon)
                    ));
                }
            }

            Toast toast = new Toast(context);
            // Stand out elegantly above bottom navigation / margins
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 180);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(view);
            toast.show();
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback gracefully to system Toast in case of any system/Layout inflation exception
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showSuccess(Context context, String message) {
        show(context, message, TYPE_SUCCESS);
    }

    public static void showInfo(Context context, String message) {
        show(context, message, TYPE_INFO);
    }

    public static void showError(Context context, String message) {
        show(context, message, TYPE_ERROR);
    }
}
 