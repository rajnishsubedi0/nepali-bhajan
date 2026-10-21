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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rkant.bhajanapp.R;
import com.rkant.bhajanapp.adapter.HomeExpandAdapter;
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

public class MainActivity extends AppCompatActivity {

    private static final int MAX_HORIZONTAL_CARDS = 10;

    private List<Bhajan> all = new ArrayList<>();
    private boolean backOnce = false;
    private long lastBack = 0;
    private final Handler backHandler = new Handler(Looper.getMainLooper());

    private View cardDaily, cardNowPlaying, sectionRecent, favEmpty;
    private View hsDeity, hsRecent, scrollFav;
    private TextView tvDailyTitle, tvDailyType, tvNowTitle;
    private TextView seeAllLibrary, seeAllRecent, seeAllFav;
    private ImageView btnNowPlay;
    private LinearLayout categoriesContainer, recentContainer, favContainer, deityExpandedContainer;
    private RecyclerView rvRecentExpanded, rvFavExpanded;
    private View btnAudio;
    private ObjectAnimator pulseAnim;

    // Expansion state
    private boolean deityExpanded = false;
    private boolean recentExpanded = false;
    private boolean favExpanded = false;

    private HomeExpandAdapter recentExpandAdapter;
    private HomeExpandAdapter favExpandAdapter;
    private final List<Bhajan> expandedRecent = new ArrayList<>();
    private final List<Bhajan> expandedFav = new ArrayList<>();

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
        hsDeity = findViewById(R.id.hs_deity);
        hsRecent = findViewById(R.id.hs_recent);
        tvDailyTitle = findViewById(R.id.tv_daily_title);
        tvDailyType = findViewById(R.id.tv_daily_type);
        tvNowTitle = findViewById(R.id.tv_now_title);
        btnNowPlay = findViewById(R.id.btn_now_play);
        seeAllLibrary = findViewById(R.id.see_all_library);
        seeAllRecent = findViewById(R.id.see_all_recent);
        seeAllFav = findViewById(R.id.see_all_fav);
        categoriesContainer = findViewById(R.id.categories_container);
        recentContainer = findViewById(R.id.recent_container);
        favContainer = findViewById(R.id.fav_container);
        deityExpandedContainer = findViewById(R.id.deity_expanded_container);
        rvRecentExpanded = findViewById(R.id.rv_recent_expanded);
        rvFavExpanded = findViewById(R.id.rv_fav_expanded);

        ((TextView) findViewById(R.id.tv_greeting)).setText(greeting());

        findViewById(R.id.search_box).setOnClickListener(v ->
                startActivity(LibraryActivity.intent(this, "All", LibraryActivity.TAB_ALL, true)));

        // "See all" now expands in place (no redirect)
        seeAllLibrary.setOnClickListener(v -> toggleDeity());
        seeAllRecent.setOnClickListener(v -> toggleRecent());
        seeAllFav.setOnClickListener(v -> toggleFav());

        findViewById(R.id.card_audio).setOnClickListener(v -> BottomNav.open(this, BottomNav.AUDIO));
        findViewById(R.id.btn_random).setOnClickListener(v -> {
            if (all.isEmpty()) return;
            openBhajan(all.get(new Random().nextInt(all.size())));
        });
        cardNowPlaying.setOnClickListener(v -> startActivity(new Intent(this, MusicPlayerActivity.class)));
        btnNowPlay.setOnClickListener(v -> PlaybackManager.getInstance().playPause());

        setupExpandedAdapters();

        BhajanRepo.load(this, list -> {
            if (isFinishing() || isDestroyed()) return;
            all = list;
            bindContent();
        });
    }

    private void setupExpandedAdapters() {
        // Recent: swipe removes from recent history
        recentExpandAdapter = new HomeExpandAdapter(expandedRecent, this, false,
                (b, pos) -> {
                    Helper.removeRecent(this, b.id);
                    if (expandedRecent.size() <= 1) recentExpanded = false;
                    bindContent();
                    Toast.makeText(this, "Removed from recent", Toast.LENGTH_SHORT).show();
                },
                this::openBhajan);
        rvRecentExpanded.setLayoutManager(new LinearLayoutManager(this));
        rvRecentExpanded.setNestedScrollingEnabled(false);
        rvRecentExpanded.setAdapter(recentExpandAdapter);

        // Favourites: swipe removes from favourites
        favExpandAdapter = new HomeExpandAdapter(expandedFav, this, true,
                (b, pos) -> {
                    Helper.toggleFav(this, b.id);
                    if (expandedFav.size() <= 1) favExpanded = false;
                    bindContent();
                    Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show();
                },
                this::openBhajan);
        rvFavExpanded.setLayoutManager(new LinearLayoutManager(this));
        rvFavExpanded.setNestedScrollingEnabled(false);
        rvFavExpanded.setAdapter(favExpandAdapter);
    }

    // ═══════════════════════════════════════════════════════════
    // EXPANSION TOGGLES
    // ═══════════════════════════════════════════════════════════
    private void toggleDeity() {
        deityExpanded = !deityExpanded;
        applyDeityExpansion();
    }

    private void toggleRecent() {
        recentExpanded = !recentExpanded;
        applyRecentExpansion();
    }

    private void toggleFav() {
        favExpanded = !favExpanded;
        applyFavExpansion();
    }

    private void applyDeityExpansion() {
        if (deityExpanded) {
            hsDeity.setVisibility(View.GONE);
            deityExpandedContainer.setVisibility(View.VISIBLE);
            seeAllLibrary.setText("Close");
        } else {
            hsDeity.setVisibility(View.VISIBLE);
            deityExpandedContainer.setVisibility(View.GONE);
            seeAllLibrary.setText("See all");
        }
    }

    private void applyRecentExpansion() {
        if (recentExpanded) {
            hsRecent.setVisibility(View.GONE);
            rvRecentExpanded.setVisibility(View.VISIBLE);
            seeAllRecent.setText("Close");
        } else {
            hsRecent.setVisibility(View.VISIBLE);
            rvRecentExpanded.setVisibility(View.GONE);
            seeAllRecent.setText("See all");
        }
    }

    private void applyFavExpansion() {
        if (favExpanded) {
            scrollFav.setVisibility(View.GONE);
            rvFavExpanded.setVisibility(View.VISIBLE);
            seeAllFav.setText("Close");
        } else {
            scrollFav.setVisibility(View.VISIBLE);
            rvFavExpanded.setVisibility(View.GONE);
            seeAllFav.setText("See all");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CONTENT BINDING
    // ═══════════════════════════════════════════════════════════
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
        // Horizontal (collapsed)
        categoriesContainer.removeAllViews();
        // Vertical (expanded)
        deityExpandedContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < BhajanCategories.KEYS.length; i++) {
            int count = 0;
            for (Bhajan b : all) if (BhajanCategories.KEYS[i].equalsIgnoreCase(b.category)) count++;
            if (count == 0) continue;

            final String english = BhajanCategories.ENGLISH[i];
            final int gradient = BhajanCategories.GRADIENTS[i];

            // Horizontal tile
            View tile = inflater.inflate(R.layout.item_home_category, categoriesContainer, false);
            tile.setBackgroundResource(gradient);
            ((TextView) tile.findViewById(R.id.tv_cat_name)).setText(BhajanCategories.NEPALI[i]);
            ((TextView) tile.findViewById(R.id.tv_cat_en)).setText(BhajanCategories.ENGLISH[i]);
            ((TextView) tile.findViewById(R.id.tv_cat_count)).setText(count + (count == 1 ? " bhajan" : " bhajans"));
            tile.setOnClickListener(v ->
                    startActivity(LibraryActivity.intent(this, english, LibraryActivity.TAB_ALL, false)));
            categoriesContainer.addView(tile);

            // Vertical row
            View row = inflater.inflate(R.layout.item_home_deity_row, deityExpandedContainer, false);
            row.setBackgroundResource(gradient);
            ((TextView) row.findViewById(R.id.tv_deity_name)).setText(BhajanCategories.NEPALI[i]);
            ((TextView) row.findViewById(R.id.tv_deity_en)).setText(BhajanCategories.ENGLISH[i]);
            ((TextView) row.findViewById(R.id.tv_deity_count)).setText(count + (count == 1 ? " bhajan" : " bhajans"));
            row.setOnClickListener(v ->
                    startActivity(LibraryActivity.intent(this, english, LibraryActivity.TAB_ALL, false)));
            deityExpandedContainer.addView(row);
        }
        applyDeityExpansion();
    }

    private void bindRecent() {
        recentContainer.removeAllViews();
        expandedRecent.clear();

        int limit = Helper.getLibraryRecentLimit(this);
        List<Bhajan> recentList = new ArrayList<>();
        for (String id : Helper.getRecentIds(this)) {
            Bhajan b = BhajanRepo.find(all, id);
            if (b != null) recentList.add(b);
            if (recentList.size() >= limit) break;
        }

        // Horizontal strip (capped)
        for (int i = 0; i < Math.min(recentList.size(), MAX_HORIZONTAL_CARDS); i++) {
            recentContainer.addView(makeCard(recentList.get(i), recentContainer));
        }

        // Vertical expanded (all)
        expandedRecent.addAll(recentList);
        recentExpandAdapter.update(expandedRecent);

        sectionRecent.setVisibility(recentList.isEmpty() ? View.GONE : View.VISIBLE);
        applyRecentExpansion();
    }

    private void bindFavourites() {
        favContainer.removeAllViews();
        expandedFav.clear();

        List<Bhajan> favList = new ArrayList<>();
        for (Bhajan b : all) if (Helper.isFav(this, b.id)) favList.add(b);

        // Horizontal strip (capped)
        for (int i = 0; i < Math.min(favList.size(), MAX_HORIZONTAL_CARDS); i++) {
            favContainer.addView(makeCard(favList.get(i), favContainer));
        }

        // Vertical expanded (all)
        expandedFav.addAll(favList);
        favExpandAdapter.update(expandedFav);

        scrollFav.setVisibility(favList.isEmpty() ? View.GONE : View.VISIBLE);
        favEmpty.setVisibility(favList.isEmpty() ? View.VISIBLE : View.GONE);
        applyFavExpansion();
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
        if (h < 5) text = "\u0936\u0941\u092D \u0930\u093E\u0924\u094D\u0930\u0940 ";
        else if (h < 12) text = "\u0936\u0941\u092D \u092A\u094D\u0930\u092D\u093E\u0924 ";
        else if (h < 17) text = "\u0936\u0941\u092D \u0926\u093F\u0928 ";
        else if (h < 20) text = "\u0936\u0941\u092D \u0938\u0928\u094D\u0927\u094D\u092F\u093E ";
        else text = "\u0936\u0941\u092D \u0930\u093E\u0924\u094D\u0930\u0940 ";
        return text + " \uD83D\uDE4F ";
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
    public void onBackPressed() {
        // Collapse any expanded section before exiting
        if (deityExpanded || recentExpanded || favExpanded) {
            deityExpanded = false;
            recentExpanded = false;
            favExpanded = false;
            applyDeityExpansion();
            applyRecentExpansion();
            applyFavExpansion();
            return;
        }
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