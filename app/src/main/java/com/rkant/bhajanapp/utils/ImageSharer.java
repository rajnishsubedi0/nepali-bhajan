package com.rkant.bhajanapp.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageSharer {
    public static void shareBhajan(Context context, String title, List<String> lyrics) {
        try {
            int width = 1080, pad = 72;
            boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) != 0;
            int bg = night ? Color.parseColor("#0A0E15") : Color.parseColor("#F8FAFC");
            int titleColor = night ? Color.parseColor("#4EC3F7") : Color.parseColor("#12396B");
            int textColor = night ? Color.parseColor("#E6EBF2") : Color.parseColor("#243447");
            int subColor = night ? Color.parseColor("#8A97A8") : Color.parseColor("#8CA0B3");

            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG); tp.setColor(titleColor); tp.setTextSize(56); tp.setFakeBoldText(true);
            TextPaint lp = new TextPaint(Paint.ANTI_ALIAS_FLAG); lp.setColor(textColor); lp.setTextSize(40);
            TextPaint fp = new TextPaint(Paint.ANTI_ALIAS_FLAG); fp.setColor(subColor); fp.setTextSize(30);
            int tw = width - pad * 2;

            StaticLayout titleLayout = make(title, tp, tw);
            List<StaticLayout> lines = new ArrayList<>();
            int height = pad + titleLayout.getHeight() + 86;
            for (String s : lyrics) {
                if (s.trim().isEmpty()) { lines.add(null); height += 28; continue; }
                StaticLayout sl = make(s, lp, tw);
                lines.add(sl); height += sl.getHeight() + 14;
            }
            height += pad + 40;

            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp); cv.drawColor(bg);
            float y = pad;
            cv.save(); cv.translate(pad, y); titleLayout.draw(cv); cv.restore();
            y += titleLayout.getHeight() + 40;
            Paint div = new Paint(); div.setColor(subColor); div.setAlpha(90);
            cv.drawRect(pad, y, width - pad, y + 3, div);
            y += 46;
            for (StaticLayout sl : lines) {
                if (sl == null) { y += 28; continue; }
                if (y > height - 120) break;
                cv.save(); cv.translate(pad, y); sl.draw(cv); cv.restore();
                y += sl.getHeight() + 14;
            }
            cv.drawText("Bhajan Sangraha • भजन संग्रह", pad, height - 52, fp);

            File dir = new File(context.getCacheDir(), "images"); dir.mkdirs();
            File file = new File(dir, "bhajan_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close();

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setType("image/png");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share Bhajan"));
        } catch (Exception e) { e.printStackTrace(); }
    }
    private static StaticLayout make(String s, TextPaint p, int w) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), p, w).build();
    }
}