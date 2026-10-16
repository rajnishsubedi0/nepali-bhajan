package com.rkant.bhajanapp.utils;

import com.rkant.bhajanapp.R;

/** The five deity groups used across Home and Library. */
public final class BhajanCategories {
    private BhajanCategories() {}

    public static final String[] KEYS = {"krishna", "shiva", "hari", "ram", "others"};
    public static final String[] ENGLISH = {"Krishna", "Shiva", "Hari", "Ram", "Others"};
    // कृष्ण, शिव, हरि, राम, अन्य
    public static final String[] NEPALI = {
            "\u0915\u0943\u0937\u094D\u0923", "\u0936\u093F\u0935", "\u0939\u0930\u093F",
            "\u0930\u093E\u092E", "\u0905\u0928\u094D\u092F"};
    public static final int[] GRADIENTS = {
            R.drawable.bg_gradient_krishna, R.drawable.bg_gradient_shiva, R.drawable.bg_gradient_hari,
            R.drawable.bg_gradient_ram, R.drawable.bg_gradient_other};

    public static int indexOf(String category) {
        if (category != null) {
            for (int i = 0; i < KEYS.length; i++) {
                if (KEYS[i].equalsIgnoreCase(category) || ENGLISH[i].equalsIgnoreCase(category)) return i;
            }
        }
        return KEYS.length - 1;
    }

    public static int gradientFor(String category) { return GRADIENTS[indexOf(category)]; }

    public static String nepaliFor(String category) { return NEPALI[indexOf(category)]; }
}
