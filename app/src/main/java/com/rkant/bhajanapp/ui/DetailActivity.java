package com.rkant.bhajanapp.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.LyricAdapter;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.ImageSharer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {
    private String bhajanId, title;
    private List<String> lyrics = new ArrayList<>();
    private LyricAdapter lyricAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        bhajanId = getIntent().getStringExtra("ID");
        title = getIntent().getStringExtra("TITLE");
        Helper.addRecent(this, bhajanId);

        findViewById(R.id.btn_share).setOnClickListener(v -> ImageSharer.shareBhajan(this, title, lyrics));

        ImageView btnYoutube = findViewById(R.id.btn_youtube);
        Executors.newSingleThreadExecutor().execute(() -> {
            String link = Helper.getYouTubeLink(this, bhajanId);
            runOnUiThread(() -> {
                if (link != null && !link.isEmpty()) {
                    btnYoutube.setVisibility(View.VISIBLE);
                    btnYoutube.setOnClickListener(v -> Helper.showConfirm(this, "Play on YouTube",
                            "Watch \"" + title + "\" on YouTube?", "Open", false, () -> {
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); }
                                catch (Exception e) { Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show(); }
                            }));
                }
            });
        });

        // Screen timeout: default 3 min, customizable (-1 = always on)
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int mins = Helper.getScreenTimeout(this);
        if (mins > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON), mins * 60000L);
        }

        loadLyrics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply lyric font size if it was changed in Settings
        if (lyricAdapter != null) lyricAdapter.notifyDataSetChanged();
    }

    private void loadLyrics() {
        RecyclerView rv = findViewById(R.id.rv_lyrics);
        rv.setLayoutManager(new LinearLayoutManager(this));
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONArray arr = new JSONArray(Helper.readRaw(this, R.raw.bhajan_data));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    if (bhajanId.equals(Helper.getJson(o, "id"))) {
                        JSONArray l = o.getJSONArray("bhajan");
                        for (int j = 0; j < l.length(); j++) lyrics.add(l.getString(j));
                        break;
                    }
                }
                runOnUiThread(() -> {
                    lyricAdapter = new LyricAdapter(lyrics, this);
                    rv.setAdapter(lyricAdapter);
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}