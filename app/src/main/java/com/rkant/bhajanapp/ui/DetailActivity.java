package com.rkant.bhajanapp.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.LyricAdapter;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.utils.BhajanRepo;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.ImageSharer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    private static final long SCROLL_TICK_MS = 30L;
    // Added 0.3x and 0.5x for slower scrolling
    private static final float[] SCROLL_DP_PER_TICK = {0.3f, 0.5f, 0.6f, 1.2f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.3x", "0.5x", "1x", "2x", "3x"};

    private String bhajanId, title;
    private final List<String> lyrics = new ArrayList<>();
    private LyricAdapter lyricAdapter;
    private RecyclerView rv;
    private ImageView btnFav, btnAutoScroll;
    private TextView tvSpeed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoScrolling = false;
    private int speedLevel = 2; // default 1x
    private float scrollRemainder = 0f;

    private final Runnable scrollTick = new Runnable() {
        @Override
        public void run() {
            if (!autoScrolling || rv == null) return;
            if (!rv.canScrollVertically(1)) { stopAutoScroll(); return; }
            float px = SCROLL_DP_PER_TICK[speedLevel] * getResources().getDisplayMetrics().density + scrollRemainder;
            int whole = (int) px;
            scrollRemainder = px - whole;
            if (whole > 0) rv.scrollBy(0, whole);
            handler.postDelayed(this, SCROLL_TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        bhajanId = getIntent().getStringExtra("ID");
        title = getIntent().getStringExtra("TITLE");
        Helper.addRecent(this, bhajanId);

        rv = findViewById(R.id.rv_lyrics);
        btnFav = findViewById(R.id.btn_fav);
        btnAutoScroll = findViewById(R.id.btn_autoscroll);
        tvSpeed = findViewById(R.id.tv_scroll_speed);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_share).setOnClickListener(v -> ImageSharer.shareBhajan(this, title, lyrics));

        updateFavIcon();
        btnFav.setOnClickListener(v -> {
            Helper.toggleFav(this, bhajanId);
            updateFavIcon();
            Toast.makeText(this, Helper.isFav(this, bhajanId) ? "Added to favourites" : "Removed from favourites",
                    Toast.LENGTH_SHORT).show();
        });

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

        findViewById(R.id.btn_font_minus).setOnClickListener(v -> changeFont(-1));
        findViewById(R.id.btn_font_plus).setOnClickListener(v -> changeFont(1));

        btnAutoScroll.setOnClickListener(v -> {
            if (autoScrolling) stopAutoScroll();
            else startAutoScroll();
        });

        tvSpeed.setOnClickListener(v -> {
            speedLevel = (speedLevel + 1) % SCROLL_DP_PER_TICK.length;
            updateSpeedLabel();
        });

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView r, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && autoScrolling) stopAutoScroll();
            }
        });

        updateSpeedLabel();
        updateAutoScrollIcon();

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
        if (lyricAdapter != null) lyricAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoScroll();
    }

    private void changeFont(int delta) {
        int cur = Helper.getLyricFontScale(this);
        int next = Math.max(0, Math.min(4, cur + delta));
        if (next == cur) {
            Toast.makeText(this, delta > 0 ? "Largest size" : "Smallest size", Toast.LENGTH_SHORT).show();
            return;
        }
        Helper.setLyricFontScale(this, next);
        if (lyricAdapter != null) lyricAdapter.notifyDataSetChanged();
    }

    private void startAutoScroll() {
        autoScrolling = true;
        scrollRemainder = 0f;
        updateAutoScrollIcon();
        handler.removeCallbacks(scrollTick);
        handler.postDelayed(scrollTick, SCROLL_TICK_MS);
    }

    private void stopAutoScroll() {
        if (!autoScrolling) return;
        autoScrolling = false;
        handler.removeCallbacks(scrollTick);
        updateAutoScrollIcon();
    }

    private void updateAutoScrollIcon() {
        btnAutoScroll.setImageResource(autoScrolling ? R.drawable.ic_pause : R.drawable.ic_auto_scroll);
    }

    private void updateSpeedLabel() {
        tvSpeed.setText(SPEED_LABELS[speedLevel]);
    }

    private void updateFavIcon() {
        boolean fav = Helper.isFav(this, bhajanId);
        btnFav.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        // Use accent color instead of red
        btnFav.setColorFilter(getColor(fav ? R.color.accent : R.color.text_secondary));
    }

    private void loadLyrics() {
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
                runOnUiThread(() -> BhajanRepo.load(this, all -> {
                    if (isFinishing() || isDestroyed()) return;
                    Bhajan meta = BhajanRepo.find(all, bhajanId);
                    lyricAdapter = new LyricAdapter(lyrics, this, title,
                            meta != null ? meta.type : null, meta != null ? meta.category : null);
                    rv.setAdapter(lyricAdapter);
                }));
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}
