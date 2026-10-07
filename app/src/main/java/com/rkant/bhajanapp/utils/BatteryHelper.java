package com.rkant.bhajanapp.utils;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.rkant.bhajanapp.R;

public class BatteryHelper {

    private static final String PREFS = "gpt_prefs";
    private static final String KEY_GUIDE_COUNT = "battery_guide_count";
    private static final int MAX_AUTO_SHOW = 3;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isExempt(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }


    public static void showGuideIfNeeded(Context c) {
        if (isExempt(c)) return;
        int shown = p(c).getInt(KEY_GUIDE_COUNT, 0);
        if (shown >= MAX_AUTO_SHOW) return;
        p(c).edit().putInt(KEY_GUIDE_COUNT, shown + 1).apply();
        showGuide(c);
    }


    public static void showGuide(Context c) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_battery);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView status = d.findViewById(R.id.tv_battery_status);
        TextView deviceTip = d.findViewById(R.id.tv_device_tip);
        TextView enable = d.findViewById(R.id.btn_enable);
        TextView deviceSettings = d.findViewById(R.id.btn_device_settings);
        TextView later = d.findViewById(R.id.btn_later);

        boolean exempt = isExempt(c);
        refreshStatus(status, exempt);
        setupDeviceTip(deviceTip);

        if (exempt) {

            enable.setVisibility(View.GONE);
            deviceSettings.setVisibility(View.GONE);
            later.setText("Close");
        }

        enable.setOnClickListener(v -> { requestExemption(c); d.dismiss(); });
        deviceSettings.setOnClickListener(v -> {
            if (!openOemSettings(c)) openBatterySettingsList(c);
            d.dismiss();
        });
        later.setOnClickListener(v -> d.dismiss());

        d.show();
    }

    private static void refreshStatus(TextView status, boolean exempt) {
        Context c = status.getContext();
        if (exempt) {
            status.setText("\u2713 Background playback is already allowed");
            status.setTextColor(c.getColor(R.color.accent));
        } else {
            status.setText("Battery optimization is currently ON for this app");
            status.setTextColor(c.getColor(R.color.red));
        }
    }

    public static void requestExemption(Context c) {

        // 1) Direct "ignore battery optimizations" system dialog.
        if (requestExemptionDialog(c)) return;

        // 2) Fallback: list of apps' battery-optimization settings.
        if (openBatterySettingsList(c)) {
            toast(c, "Find this app and choose \u201CDon\u2019t optimize\u201D");
            return;
        }

        // 3) Last resort: the app's info page.
        openAppInfo(c);
    }

    private static boolean requestExemptionDialog(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean openBatterySettingsList(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean openAppInfo(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- OEM-specific battery / auto-start managers ----------

    public static boolean openOemSettings(Context c) {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        String[][] targets;

        if (m.contains("transsion") || m.contains("infinix") || m.contains("tecno") || m.contains("itel")) {
            targets = new String[][]{
                    {"com.transsion.securitycenter", "com.transsion.securitycenter.activity.BatteryManagerActivity"},
                    {"com.transsion.hilauncher", null},
            };
        } else if (m.contains("samsung")) {
            targets = new String[][]{
                    {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
            };
        } else if (m.contains("huawei") || m.contains("honor")) {
            targets = new String[][]{
                    {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            };
        } else if (m.contains("xiaomi") || m.contains("redmi")) {
            targets = new String[][]{
                    {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            };
        } else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            targets = new String[][]{
                    {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                    {"com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"},
            };
        } else if (m.contains("vivo")) {
            targets = new String[][]{
                    {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            };
        } else if (m.contains("asus")) {
            targets = new String[][]{
                    {"com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"},
            };
        } else if (m.contains("htc")) {
            targets = new String[][]{
                    {"com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity"},
            };
        } else {
            return false;
        }

        for (String[] t : targets) {
            if (tryLaunch(c, t[0], t[1])) return true;
        }
        return false;
    }

    private static boolean tryLaunch(Context c, String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (cls != null) i.setComponent(new ComponentName(pkg, cls));
            else { i.setPackage(pkg); i.setAction(Intent.ACTION_MAIN); }
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Device-specific tip text ----------

    private static void setupDeviceTip(TextView tip) {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        String text = null;

        if (m.contains("transsion") || m.contains("infinix") || m.contains("tecno") || m.contains("itel")) {
            text = "Infinix tip: also open \u201CPhone Master\u201D \u2192 \u201CAuto-start / Freezer\u201D and allow this app to run in the background.";
        } else if (m.contains("samsung")) {
            text = "Samsung tip: Settings \u2192 Battery \u2192 Background usage limits \u2192 remove this app from \u201CSleeping apps\u201D.";
        } else if (m.contains("xiaomi") || m.contains("redmi")) {
            text = "Xiaomi tip: Settings \u2192 Apps \u2192 Autostart \u2192 enable for this app, and set battery saver to \u201CNo restrictions\u201D.";
        } else if (m.contains("huawei") || m.contains("honor")) {
            text = "Huawei tip: Settings \u2192 Battery \u2192 App launch \u2192 turn off \u201CManage automatically\u201D and allow background activity.";
        } else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            text = "Tip: Settings \u2192 Battery \u2192 App battery management \u2192 allow background activity for this app.";
        } else if (m.contains("vivo")) {
            text = "Vivo tip: iManager \u2192 App manager \u2192 Autostart \u2192 allow this app.";
        }

        if (text != null) {
            tip.setText(text);
            tip.setVisibility(View.VISIBLE);
        }
    }

    private static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }
}