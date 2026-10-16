package com.rkant.bhajanapp.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.model.Bhajan;
import com.rkant.bhajanapp.utils.BhajanCategories;
import com.rkant.bhajanapp.utils.BhajanRepo;
import com.rkant.bhajanapp.utils.BottomNav;
import com.rkant.bhajanapp.utils.Helper;
import com.rkant.bhajanapp.utils.PlaybackManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Home dashboard: bhajan of the day, deity shortcuts, continue reading,
 * favourites and a now-playing strip.
 */
public class MainActivity extends AppCompatActivity {

    private static final int MAX_HORIZONTAL_CARDS = 10;

    private List<Bhajan> all = new ArrayList<>();
    private boolean backOnce = false;
    private long lastBack = 0;
    private final Handler backHandler = new Handler(Looper.getMainLooper());

    private View cardDaily, cardNowPlaying, sectionRecent, favEmpty, scrollFav;
    private TextView tvDailyTitle, tvDailyType, tvNowTitle;
    private ImageView btnNowPlay;
    private LinearLayout categoriesContainer, recentContainer, favContainer;
    private View btnAudio;
    private ObjectAnimator pulseAnim;

    private final PlaybackManager.Listener playbackListener = new PlaybackManager.Listener() {
        @Override
        public void onStateChanged(boolean isPlaying, String title, String mediaId) {
            updateNowPlaying(isPlaying, title);
            if (btnAudio == null) return;
            if (isPlaying) {
                if (pulseAnim == null) {
                    pulseAnim = ObjectAnimator.ofPropertyValuesHolder(btnAudio,
                            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f, 1f),
                            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f, 1f));
                    pulseAnim.setDuration(1000);
                    pulseAnim.setRepeatCount(ObjectAnimator.INFINITE);
                }
                pulseAnim.start();
            } else {
                if (pulseAnim != null) pulseAnim.cancel();
                btnAudio.setScaleX(1f);
                btnAudio.setScaleY(1f);
            }
        }

        @Override
        public void onProgress(long position, long duration) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Helper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnAudio = findViewById(R.id.nav_audio_icon);
        BottomNav.setup(this, BottomNav.HOME);

        cardDaily = findViewById(R.id.card_daily);
        cardNowPlaying = findViewById(R.id.card_now_playing);
        sectionRecent = findViewById(R.id.section_recent);
        favEmpty = findViewById(R.id.fav_empty);
        scrollFav = findViewById(R.id.scroll_fav);
        tvDailyTitle = findViewById(R.id.tv_daily_title);
        tvDailyType = findViewById(R.id.tv_daily_type);
        tvNowTitle = findViewById(R.id.tv_now_title);
        btnNowPlay = findViewById(R.id.btn_now_play);
        categoriesContainer = findViewById(R.id.categories_container);
        recentContainer = findViewById(R.id.recent_container);
        favContainer = findViewById(R.id.fav_container);

        ((TextView) findViewById(R.id.tv_greeting)).setText(greeting());

        // Search box opens Library with keyboard
        findViewById(R.id.search_box).setOnClickListener(v ->
                startActivity(LibraryActivity.intent(this, "All", LibraryActivity.TAB_ALL, true)));

        // "See all" links
        findViewById(R.id.see_all_library).setOnClickListener(v ->
                startActivity(LibraryActivity.intent(this, "All", LibraryActivity.TAB_ALL, false)));
        findViewById(R.id.see_all_recent).setOnClickListener(v ->
                startActivity(LibraryActivity.intent(this, "All", LibraryActivity.TAB_RECENT, false)));
        findViewById(R.id.see_all_fav).setOnClickListener(v ->
                startActivity(LibraryActivity.intent(this, "All", LibraryActivity.TAB_FAV, false)));

        // Audio shortcut
        findViewById(R.id.card_audio).setOnClickListener(v -> BottomNav.open(this, BottomNav.AUDIO));

        // Random bhajan
        findViewById(R.id.btn_random).setOnClickListener(v -> {
            if (all.isEmpty()) return;
            openBhajan(all.get(new Random().nextInt(all.size())));
        });

        cardNowPlaying.setOnClickListener(v -> startActivity(new Intent(this, MusicPlayerActivity.class)));
        btnNowPlay.setOnClickListener(v -> PlaybackManager.getInstance().playPause());

        BhajanRepo.load(this, list -> {
            if (isFinishing() || isDestroyed()) return;
            all = list;
            bindContent();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        PlaybackManager.getInstance().connect(this);
        PlaybackManager.getInstance().addListener(playbackListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        PlaybackManager.getInstance().removeListener(playbackListener);
        if (pulseAnim != null) pulseAnim.cancel();
        if (btnAudio != null) { btnAudio.setScaleX(1f); btnAudio.setScaleY(1f); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindContent();
    }

    private void bindContent() {
        if (all.isEmpty()) return;
        bindDaily();
        bindCategories();
        bindRecent();
        bindFavourites();
    }

    private void bindDaily() {
        Calendar c = Calendar.getInstance();
        int seed = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
        Bhajan b = all.get(seed % all.size());
        tvDailyTitle.setText(b.titleNepali);
        tvDailyType.setText(b.type);
        tvDailyType.setVisibility(b.type == null || b.type.isEmpty() ? View.GONE : View.VISIBLE);
        cardDaily.setVisibility(View.VISIBLE);
        cardDaily.setOnClickListener(v -> openBhajan(b));
    }

    private void bindCategories() {
        categoriesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < BhajanCategories.KEYS.length; i++) {
            int count = 0;
            for (Bhajan b : all) if (BhajanCategories.KEYS[i].equalsIgnoreCase(b.category)) count++;
            if (count == 0) continue;

            View tile = inflater.inflate(R.layout.item_home_category, categoriesContainer, false);
            tile.setBackgroundResource(BhajanCategories.GRADIENTS[i]);
            ((TextView) tile.findViewById(R.id.tv_cat_name)).setText(BhajanCategories.NEPALI[i]);
            ((TextView) tile.findViewById(R.id.tv_cat_en)).setText(BhajanCategories.ENGLISH[i]);
            ((TextView) tile.findViewById(R.id.tv_cat_count)).setText(count + (count == 1 ? " bhajan" : " bhajans"));

            final String english = BhajanCategories.ENGLISH[i];
            tile.setOnClickListener(v ->
                    startActivity(LibraryActivity.intent(this, english, LibraryActivity.TAB_ALL, false)));
            categoriesContainer.addView(tile);
        }
    }

    private void bindRecent() {
        recentContainer.removeAllViews();
        int shown = 0;
        int limit = Helper.getLibraryRecentLimit(this);
        for (String id : Helper.getRecentIds(this)) {
            if (shown >= limit || shown >= MAX_HORIZONTAL_CARDS) break;
            Bhajan b = BhajanRepo.find(all, id);
            if (b == null) continue;
            recentContainer.addView(makeCard(b, recentContainer));
            shown++;
        }
        sectionRecent.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private void bindFavourites() {
        favContainer.removeAllViews();
        int shown = 0;
        for (Bhajan b : all) {
            if (!Helper.isFav(this, b.id)) continue;
            if (shown >= MAX_HORIZONTAL_CARDS) break;
            favContainer.addView(makeCard(b, favContainer));
            shown++;
        }
        scrollFav.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        favEmpty.setVisibility(shown > 0 ? View.GONE : View.VISIBLE);
    }

    private View makeCard(Bhajan b, LinearLayout parent) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_home_card, parent, false);
        TextView tag = card.findViewById(R.id.tv_card_tag);
        tag.setBackgroundResource(BhajanCategories.gradientFor(b.category));
        tag.setText(BhajanCategories.nepaliFor(b.category));
        ((TextView) card.findViewById(R.id.tv_card_title)).setText(b.titleNepali);
        ((TextView) card.findViewById(R.id.tv_card_type)).setText(b.type);
        card.setOnClickListener(v -> openBhajan(b));
        return card;
    }

    private void openBhajan(Bhajan b) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("ID", b.id);
        i.putExtra("TITLE", b.titleNepali);
        startActivity(i);
    }

    private void updateNowPlaying(boolean isPlaying, String title) {
        if (cardNowPlaying == null) return;
        boolean has = title != null && !title.isEmpty();
        cardNowPlaying.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) tvNowTitle.setText(title);
        btnNowPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String text;
        if (h < 5) text = "\u0936\u0941\u092D \u0930\u093E\u0924\u094D\u0930\u093F";
        else if (h < 12) text = "\u0936\u0941\u092D \u092A\u094D\u0930\u092D\u093E\u0924";
        else if (h < 17) text = "\u0936\u0941\u092D \u0926\u093F\u0928";
        else if (h < 20) text = "\u0936\u0941\u092D \u0938\u0928\u094D\u0927\u094D\u092F\u093E";
        else text = "\u0936\u0941\u092D \u0930\u093E\u0924\u094D\u0930\u093F";
        return text + " \uD83D\uDE4F";
    }

    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (backOnce && (now - lastBack) <= 2000) {
            super.onBackPressed();
        } else {
            backOnce = true;
            lastBack = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
            backHandler.postDelayed(() -> backOnce = false, 2000);
        }
    }
}
