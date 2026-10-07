package com.rkant.bhajanapp.utils;

public class SheetBus {

    public interface Listener {
        void onSheetOpenProgress(float openProgress);
    }

    private static Listener listener;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void emit(float openProgress) {
        if (listener != null) {
            listener.onSheetOpenProgress(openProgress);
        }
    }
}