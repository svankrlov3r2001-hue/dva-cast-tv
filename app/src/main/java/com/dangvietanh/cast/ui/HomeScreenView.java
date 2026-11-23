package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-bleed Netflix / VieON style Android TV Home Screen:
 * - Edge-to-edge cinematic Hero artwork fading into deep obsidian bottom canvas
 * - Compact Top-Right utility bar (Search, Danh sách, Cài đặt, Avatar, Clock, Wi-Fi)
 * - Clean compact carousels ("Tiếp tục xem" 16:9 + portrait rows) without chunky boxes
 */
public class HomeScreenView extends FrameLayout {

    public interface Listener {
        void onOpenMovieDetail(Models.MovieItem item);
        void onPlayMovie(Models.MovieItem item);
        void onShowToast(String message, int accentColor);
        void onOpenSearch();
        void onOpenLibrary();
        void onOpenSettings();
        void onOpenAccount();
        void onOpenExploreCategory(String sectionTitle);
        void onMoveFocusToRail();
    }

    private static final class RowEntry {
        static final int KIND_HERO = 0;
        static final int KIND_GENRES = 1;
        static final int KIND_TOPICS = 2;
        static final int KIND_CAROUSEL = 3;

        final int kind;
        String sectionId = "";
        String title = "";
        String layout = "vertical";
        final List<Models.MovieItem> movies = new ArrayList<>();
        final List<Models.TopicItem> topics = new ArrayList<>();

        RowEntry(int kind) {
            this.kind = kind;
        }
    }

    private final ApiClient apiClient;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final ImageView ambientBackdropIv;
    private final RecyclerView verticalRv;
    private final FrameLayout btnTopSearch;
    private final FrameLayout btnTopLibrary;
    private final FrameLayout btnTopSettings;
    private final FrameLayout btnTopAccount;
    private final ImageView topAvatarIv;
    private final TextView clockTv;
    private final LinearLayout loadingBox;
    private final LinearLayout errorBox;
    private final TextView errorTv;
    private final TextView retryBtn;

    private final List<RowEntry> rows = new ArrayList<>();
    private final List<Models.MovieItem> heroMovies = new ArrayList<>();
    private final List<Models.FilterOption> genres = new ArrayList<>();
    private final VerticalAdapter verticalAdapter;

    private String currentMode = "home";
    private String currentGenreId = "";
    private int heroIndex = 0;
    private String lastBackdropUrl = "";
    private Runnable pendingBackdropRunnable;
    private HeroHolder activeHeroHolder;
    private int requestSeq = 0;

    private final OnKeyListener moveFocusToRailLeftListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (listener != null) {
                    listener.onMoveFocusToRail();
                    return true;
                }
            }
            return false;
        }
    };

    private final OnKeyListener topUtilDownListener = new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusFirstMainContent();
                return true;
            }
            return false;
        }
    };

    private final Runnable heroAutoSlideRunnable = new Runnable() {
        @Override
        public void run() {
            if (getVisibility() == VISIBLE && heroMovies.size() > 1 && activeHeroHolder != null) {
                heroIndex = (heroIndex + 1) % heroMovies.size();
                activeHeroHolder.bindCurrentHero(false);
            }
            handler.postDelayed(this, 9000L);
        }
    };

    private final Runnable clockTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (clockTv != null) {
                clockTv.setText(clockFormat.format(new Date()));
            }
            handler.postDelayed(this, 20000L);
        }
    };

    public HomeScreenView(Context context, ApiClient apiClient, final Listener listener) {
        super(context);
        this.apiClient = apiClient;
        this.listener = listener;

        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Full-bleed edge-to-edge cinema backdrop
        ambientBackdropIv = new ImageView(context);
        ambientBackdropIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ambientBackdropIv.setAlpha(0.90f);
        addView(ambientBackdropIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 420), Gravity.TOP));

        // Left-to-right readability vignette (lets right-side poster artwork stay vibrant)
        View leftScrim = new View(context);
        leftScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(246, 9, 11, 18),
                        Color.argb(198, 9, 11, 18),
                        Color.argb(72, 9, 11, 18),
                        Color.TRANSPARENT
                }));
        addView(leftScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 420), Gravity.TOP));

        // Top subtle vignette for status bar readability
        View topScrim = new View(context);
        topScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(150, 9, 11, 18), Color.TRANSPARENT}));
        addView(topScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 72), Gravity.TOP));

        // Bottom-to-top melt into deep obsidian canvas
        View bottomScrim = new View(context);
        bottomScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(130, 9, 11, 18),
                        Color.argb(242, 9, 11, 18),
                        UiHelper.COLOR_BG_BASE
                }));
        addView(bottomScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 2. Main Vertical RecyclerView (Hero + Carousels)
        verticalRv = new RecyclerView(context);
        verticalRv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        verticalRv.setClipToPadding(false);
        verticalRv.setClipChildren(false);
        verticalRv.setPadding(0, 0, 0, UiHelper.dp(context, 28));
        verticalAdapter = new VerticalAdapter();
        verticalRv.setAdapter(verticalAdapter);
        addView(verticalRv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 3. Top-Right Utility Bar (Search, Library, Settings, User Avatar, Clock, Wi-Fi)
        LinearLayout topUtilBar = new LinearLayout(context);
        topUtilBar.setOrientation(LinearLayout.HORIZONTAL);
        topUtilBar.setGravity(Gravity.CENTER_VERTICAL);
        topUtilBar.setClipChildren(false);
        topUtilBar.setClipToPadding(false);
        topUtilBar.setElevation(UiHelper.dp(context, 16));

        btnTopSearch = makeCircleUtilIcon(context, R.drawable.ic_search);
        btnTopSearch.setId(View.generateViewId());
        btnTopSearch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenSearch();
            }
        });
        topUtilBar.addView(btnTopSearch, utilMarginLp(context, 7));

        btnTopLibrary = makeCircleUtilIcon(context, R.drawable.ic_bookmark);
        btnTopLibrary.setId(View.generateViewId());
        btnTopLibrary.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenLibrary();
            }
        });
        topUtilBar.addView(btnTopLibrary, utilMarginLp(context, 7));

        btnTopSettings = makeCircleUtilIcon(context, R.drawable.ic_settings);
        btnTopSettings.setId(View.generateViewId());
        btnTopSettings.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenSettings();
            }
        });
        topUtilBar.addView(btnTopSettings, utilMarginLp(context, 7));

        btnTopAccount = new FrameLayout(context);
        btnTopAccount.setId(View.generateViewId());
        UiHelper.applyCircularIconButtonStyle(btnTopAccount, context, 32);
        topAvatarIv = new ImageView(context);
        topAvatarIv.setImageResource(R.drawable.ic_person);
        btnTopAccount.addView(topAvatarIv, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 26), UiHelper.dp(context, 26), Gravity.CENTER));
        btnTopAccount.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenAccount();
            }
        });
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 32), UiHelper.dp(context, 32));
        avLp.rightMargin = UiHelper.dp(context, 12);
        topUtilBar.addView(btnTopAccount, avLp);

        // Deterministic D-Pad routing inside Top Utility Bar
        btnTopSearch.setNextFocusRightId(btnTopLibrary.getId());
        btnTopLibrary.setNextFocusLeftId(btnTopSearch.getId());
        btnTopLibrary.setNextFocusRightId(btnTopSettings.getId());
        btnTopSettings.setNextFocusLeftId(btnTopLibrary.getId());
        btnTopSettings.setNextFocusRightId(btnTopAccount.getId());
        btnTopAccount.setNextFocusLeftId(btnTopSettings.getId());

        btnTopSearch.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && listener != null) {
                    listener.onMoveFocusToRail();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusFirstMainContent();
                    return true;
                }
                return false;
            }
        });
        btnTopLibrary.setOnKeyListener(topUtilDownListener);
        btnTopSettings.setOnKeyListener(topUtilDownListener);
        btnTopAccount.setOnKeyListener(topUtilDownListener);

        clockTv = new TextView(context);
        clockTv.setText(clockFormat.format(new Date()));
        clockTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        clockTv.setTextSize(13f);
        clockTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams ckLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ckLp.rightMargin = UiHelper.dp(context, 7);
        topUtilBar.addView(clockTv, ckLp);

        ImageView wifiIv = new ImageView(context);
        wifiIv.setImageResource(R.drawable.ic_wifi);
        wifiIv.setColorFilter(UiHelper.COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN);
        topUtilBar.addView(wifiIv, new LinearLayout.LayoutParams(UiHelper.dp(context, 16), UiHelper.dp(context, 16)));

        FrameLayout.LayoutParams tubLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        tubLp.setMargins(0, UiHelper.dp(context, 12), UiHelper.dp(context, 20), 0);
        addView(topUtilBar, tubLp);

        // 4. Loading overlay
        loadingBox = new LinearLayout(context);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        UiHelper.SpinnerView spin = new UiHelper.SpinnerView(context);
        loadingBox.addView(spin, new LinearLayout.LayoutParams(UiHelper.dp(context, 36), UiHelper.dp(context, 36)));
        TextView loadTv = new TextView(context);
        loadTv.setText("Đang tải kho phim...");
        loadTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        loadTv.setTextSize(13f);
        LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ltLp.topMargin = UiHelper.dp(context, 10);
        loadingBox.addView(loadTv, ltLp);
        addView(loadingBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 5. Error overlay
        errorBox = new LinearLayout(context);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setVisibility(GONE);

        errorTv = new TextView(context);
        errorTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        errorTv.setTextSize(14f);
        errorTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = UiHelper.dp(context, 14);
        errorBox.addView(errorTv, errLp);

        retryBtn = new TextView(context);
        retryBtn.setText("Thử lại");
        retryBtn.setTextColor(Color.WHITE);
        retryBtn.setTextSize(13f);
        retryBtn.setTypeface(null, Typeface.BOLD);
        retryBtn.setPadding(UiHelper.dp(context, 20), UiHelper.dp(context, 9), UiHelper.dp(context, 20), UiHelper.dp(context, 9));
        UiHelper.applyPrimaryGradientButton(retryBtn, context, 17);
        retryBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                loadHome(currentMode, currentGenreId, false);
            }
        });
        errorBox.addView(retryBtn);
        addView(errorBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        handler.post(clockTickRunnable);
    }

    public void setUser(Models.User user) {
        if (topAvatarIv == null) return;
        UiHelper.safeLoadAvatar(topAvatarIv, user != null ? user.avatarUrl : "");
    }

    public void loadHome(String mode, String genreId, final boolean silent) {
        this.currentMode = (mode != null && !mode.isEmpty()) ? mode : "home";
        this.currentGenreId = genreId != null ? genreId : "";
        final int seq = ++requestSeq;

        if (!silent) {
            loadingBox.setVisibility(VISIBLE);
            errorBox.setVisibility(GONE);
        }

        apiClient.fetchHomeHub(this.currentMode, this.currentGenreId, new ApiClient.Callback<Models.HomeHubResponse>() {
            @Override
            public void onSuccess(Models.HomeHubResponse data) {
                if (seq != requestSeq) return;
                loadingBox.setVisibility(GONE);
                errorBox.setVisibility(GONE);
                rebuildRows(data);
            }

            @Override
            public void onError(String message, int httpCode) {
                if (seq != requestSeq) return;
                loadingBox.setVisibility(GONE);
                if (!silent || rows.isEmpty()) {
                    errorBox.setVisibility(VISIBLE);
                    errorTv.setText(message != null ? message : "Không thể tải danh sách phim");
                    retryBtn.requestFocus();
                }
            }
        });
    }

    public void silentRefresh() {
        loadHome(currentMode, currentGenreId, true);
    }

    public void startAutoSlide() {
        handler.removeCallbacks(heroAutoSlideRunnable);
        handler.postDelayed(heroAutoSlideRunnable, 9000L);
    }

    public void stopAutoSlide() {
        handler.removeCallbacks(heroAutoSlideRunnable);
    }

    public void requestInitialFocus() {
        verticalRv.post(new Runnable() {
            @Override
            public void run() {
                focusFirstMainContent();
            }
        });
    }

    private void focusFirstMainContent() {
        if (activeHeroHolder != null && activeHeroHolder.btnPlay != null
                && activeHeroHolder.itemView.getParent() != null) {
            activeHeroHolder.btnPlay.requestFocus();
        } else {
            verticalRv.requestFocus();
        }
    }

    private void rebuildRows(Models.HomeHubResponse data) {
        rows.clear();
        heroMovies.clear();
        genres.clear();
        if (data != null) {
            heroMovies.addAll(data.hero);
            genres.addAll(data.genres);
        }
        heroIndex = 0;

        if (!heroMovies.isEmpty()) {
            rows.add(new RowEntry(RowEntry.KIND_HERO));
        }
        // Show category filter strip only on sub-tabs (series, movies, anime, etc.) so Home stays clean like Netflix/VieON
        if (!"home".equals(currentMode) && !genres.isEmpty()) {
            rows.add(new RowEntry(RowEntry.KIND_GENRES));
        }

        if (data != null) {
            for (Models.HomeSection sec : data.sections) {
                if ("topics".equalsIgnoreCase(sec.layout) && !sec.topics.isEmpty()) {
                    RowEntry re = new RowEntry(RowEntry.KIND_TOPICS);
                    re.sectionId = sec.id;
                    re.title = sec.title;
                    re.topics.addAll(sec.topics);
                    rows.add(re);
                } else if ("country_hub".equalsIgnoreCase(sec.layout) && !sec.hubRows.isEmpty()) {
                    for (Models.CountryHubRow cr : sec.hubRows) {
                        if (cr.items.isEmpty()) continue;
                        RowEntry re = new RowEntry(RowEntry.KIND_CAROUSEL);
                        re.sectionId = sec.id + "_" + cr.id;
                        re.title = cr.title;
                        re.layout = "vertical";
                        re.movies.addAll(cr.items);
                        rows.add(re);
                    }
                } else if (!sec.items.isEmpty()) {
                    RowEntry re = new RowEntry(RowEntry.KIND_CAROUSEL);
                    re.sectionId = sec.id;
                    re.title = sec.title;
                    re.layout = sec.layout;
                    re.movies.addAll(sec.items);
                    rows.add(re);
                }
            }
        }

        verticalAdapter.notifyDataSetChanged();
        if (!heroMovies.isEmpty()) {
            updateBackdropDebounced(heroMovies.get(0));
            startAutoSlide();
        }
        verticalRv.scrollToPosition(0);
    }

    private void updateBackdropDebounced(final Models.MovieItem item) {
        if (item == null) return;
        final String url = !item.thumbnailUrl.isEmpty() ? item.thumbnailUrl : item.posterUrl;
        if (url.isEmpty() || url.equals(lastBackdropUrl)) return;
        if (pendingBackdropRunnable != null) {
            handler.removeCallbacks(pendingBackdropRunnable);
        }
        pendingBackdropRunnable = new Runnable() {
            @Override
            public void run() {
                lastBackdropUrl = url;
                UiHelper.safeLoadBackdrop(ambientBackdropIv, url);
            }
        };
        handler.postDelayed(pendingBackdropRunnable, 140L);
    }

    private void scrollToSectionId(String targetId) {
        if (targetId == null || targetId.isEmpty()) return;
        for (int i = 0; i < rows.size(); i++) {
            RowEntry r = rows.get(i);
            if (targetId.equals(r.sectionId) || r.sectionId.startsWith(targetId)) {
                verticalRv.smoothScrollToPosition(i);
                return;
            }
        }
    }

    private FrameLayout makeCircleUtilIcon(Context ctx, int iconRes) {
        FrameLayout box = new FrameLayout(ctx);
        UiHelper.applyCircularIconButtonStyle(box, ctx, 32);
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        box.addView(iv, new FrameLayout.LayoutParams(
                UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 16), Gravity.CENTER));
        return box;
    }

    private static LinearLayout.LayoutParams utilMarginLp(Context ctx, int rightMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 32), UiHelper.dp(ctx, 32));
        lp.rightMargin = UiHelper.dp(ctx, rightMarginDp);
        return lp;
    }

    private class VerticalAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).kind;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            if (viewType == RowEntry.KIND_HERO) {
                HeroHolder h = new HeroHolder(ctx);
                activeHeroHolder = h;
                return h;
            }
            if (viewType == RowEntry.KIND_GENRES) {
                return new GenreStripHolder(ctx);
            }
            if (viewType == RowEntry.KIND_TOPICS) {
                return new TopicsHolder(ctx);
            }
            return new CarouselHolder(ctx);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RowEntry row = rows.get(position);
            if (holder instanceof HeroHolder) {
                activeHeroHolder = (HeroHolder) holder;
                ((HeroHolder) holder).bindCurrentHero(true);
            } else if (holder instanceof GenreStripHolder) {
                ((GenreStripHolder) holder).bind();
            } else if (holder instanceof TopicsHolder) {
                ((TopicsHolder) holder).bind(row);
            } else if (holder instanceof CarouselHolder) {
                ((CarouselHolder) holder).bind(row);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    // ---- Row 0: Open Full-Bleed Cinematic Hero (No Card Box) ----
    private class HeroHolder extends RecyclerView.ViewHolder {
        final TextView kickerTv;
        final TextView titleTv;
        final LinearLayout badgesRow;
        final TextView descTv;
        final TextView btnPlay;
        final TextView btnWishlist;
        final TextView btnInfo;
        final TextView btnPrev;
        final TextView btnNext;
        final LinearLayout dotsRow;

        HeroHolder(Context ctx) {
            super(new FrameLayout(ctx));
            FrameLayout heroRoot = (FrameLayout) itemView;
            heroRoot.setClipChildren(false);
            heroRoot.setClipToPadding(false);
            heroRoot.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 266)));

            LinearLayout contentCol = new LinearLayout(ctx);
            contentCol.setOrientation(LinearLayout.VERTICAL);
            contentCol.setGravity(Gravity.BOTTOM | Gravity.START);
            contentCol.setClipChildren(false);
            contentCol.setPadding(UiHelper.dp(ctx, 22), UiHelper.dp(ctx, 36),
                    UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 14));
            heroRoot.addView(contentCol, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            // Kicker Tag
            LinearLayout kickerRow = new LinearLayout(ctx);
            kickerRow.setOrientation(LinearLayout.HORIZONTAL);
            kickerRow.setGravity(Gravity.CENTER_VERTICAL);
            View kBar = new View(ctx);
            kBar.setBackgroundColor(UiHelper.COLOR_ACCENT_RED);
            LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 11));
            kbLp.rightMargin = UiHelper.dp(ctx, 6);
            kickerRow.addView(kBar, kbLp);

            kickerTv = new TextView(ctx);
            kickerTv.setText("NỔI BẬT");
            kickerTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
            kickerTv.setTextSize(10f);
            kickerTv.setTypeface(null, Typeface.BOLD);
            kickerTv.setLetterSpacing(0.10f);
            kickerRow.addView(kickerTv);
            contentCol.addView(kickerRow);

            // Hero Movie Title
            titleTv = new TextView(ctx);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTextSize(27f);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setMaxLines(2);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            titleTv.setShadowLayer(8f, 0f, 2f, Color.argb(180, 0, 0, 0));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    UiHelper.dp(ctx, 430), LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = UiHelper.dp(ctx, 4);
            contentCol.addView(titleTv, tLp);

            // Sharp Metadata Tags Row (16+, HDR, 4K, IMDb)
            badgesRow = new LinearLayout(ctx);
            badgesRow.setOrientation(LinearLayout.HORIZONTAL);
            badgesRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            brLp.topMargin = UiHelper.dp(ctx, 7);
            contentCol.addView(badgesRow, brLp);

            // Description
            descTv = new TextView(ctx);
            descTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
            descTv.setTextSize(11.5f);
            descTv.setMaxLines(2);
            descTv.setEllipsize(TextUtils.TruncateAt.END);
            descTv.setLineSpacing(UiHelper.dp(ctx, 2), 1f);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                    UiHelper.dp(ctx, 420), LinearLayout.LayoutParams.WRAP_CONTENT);
            dLp.topMargin = UiHelper.dp(ctx, 7);
            dLp.bottomMargin = UiHelper.dp(ctx, 13);
            contentCol.addView(descTv, dLp);

            // Compact Pill Buttons Row
            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.CENTER_VERTICAL);
            btnRow.setClipChildren(false);
            btnRow.setClipToPadding(false);
            contentCol.setClipToPadding(false);
            contentCol.addView(btnRow);

            btnPlay = makeHeroPill(ctx, "▶  Xem ngay", true);
            btnWishlist = makeHeroPill(ctx, "+  Thêm vào", false);
            btnInfo = makeHeroPill(ctx, "ℹ  Thông tin", false);
            btnPrev = makeCompactCircleArrow(ctx, "‹");
            btnNext = makeCompactCircleArrow(ctx, "›");

            btnPlay.setId(View.generateViewId());
            btnWishlist.setId(View.generateViewId());
            btnInfo.setId(View.generateViewId());
            btnPrev.setId(View.generateViewId());
            btnNext.setId(View.generateViewId());

            btnPlay.setNextFocusRightId(btnWishlist.getId());
            btnWishlist.setNextFocusLeftId(btnPlay.getId());
            btnWishlist.setNextFocusRightId(btnInfo.getId());
            btnInfo.setNextFocusLeftId(btnWishlist.getId());
            btnInfo.setNextFocusRightId(btnPrev.getId());
            btnPrev.setNextFocusLeftId(btnInfo.getId());
            btnPrev.setNextFocusRightId(btnNext.getId());
            btnNext.setNextFocusLeftId(btnPrev.getId());

            if (btnTopSearch != null) {
                btnPlay.setNextFocusUpId(btnTopSearch.getId());
                btnWishlist.setNextFocusUpId(btnTopSearch.getId());
                btnInfo.setNextFocusUpId(btnTopLibrary.getId());
                btnPrev.setNextFocusUpId(btnTopSettings.getId());
                btnNext.setNextFocusUpId(btnTopAccount.getId());
            }
            btnPlay.setOnKeyListener(moveFocusToRailLeftListener);

            btnRow.addView(btnPlay, heroBtnLp(ctx, 10));
            btnRow.addView(btnWishlist, heroBtnLp(ctx, 10));
            btnRow.addView(btnInfo, heroBtnLp(ctx, 14));
            btnRow.addView(btnPrev, arrowBtnLp(ctx, 6));
            btnRow.addView(btnNext, arrowBtnLp(ctx, 0));

            // Bottom-Right Pager Dots
            dotsRow = new LinearLayout(ctx);
            dotsRow.setOrientation(LinearLayout.HORIZONTAL);
            dotsRow.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            dotsLp.setMargins(0, 0, UiHelper.dp(ctx, 22), UiHelper.dp(ctx, 16));
            heroRoot.addView(dotsRow, dotsLp);

            btnPlay.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Models.MovieItem m = getCurrentHero();
                    if (m != null && listener != null) listener.onPlayMovie(m);
                }
            });
            btnInfo.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Models.MovieItem m = getCurrentHero();
                    if (m != null && listener != null) listener.onOpenMovieDetail(m);
                }
            });
            btnWishlist.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Models.MovieItem m = getCurrentHero();
                    if (m == null) return;
                    apiClient.toggleWishlist(m.videosId, new ApiClient.Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean added) {
                            m.inWishlist = added;
                            bindCurrentHero(false);
                            if (listener != null) {
                                listener.onShowToast(
                                        added ? "Đã thêm vào Danh sách của tôi" : "Đã bỏ khỏi Danh sách",
                                        added ? UiHelper.COLOR_GREEN : UiHelper.COLOR_TEXT_MUTED);
                            }
                        }

                        @Override
                        public void onError(String message, int httpCode) {
                            if (listener != null) listener.onShowToast(message, UiHelper.COLOR_ACCENT_RED);
                        }
                    });
                }
            });
            btnPrev.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (heroMovies.isEmpty()) return;
                    heroIndex = (heroIndex - 1 + heroMovies.size()) % heroMovies.size();
                    bindCurrentHero(false);
                    startAutoSlide();
                }
            });
            btnNext.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (heroMovies.isEmpty()) return;
                    heroIndex = (heroIndex + 1) % heroMovies.size();
                    bindCurrentHero(false);
                    startAutoSlide();
                }
            });
        }

        private Models.MovieItem getCurrentHero() {
            if (heroMovies.isEmpty()) return null;
            if (heroIndex < 0 || heroIndex >= heroMovies.size()) heroIndex = 0;
            return heroMovies.get(heroIndex);
        }

        void bindCurrentHero(boolean focusPlayIfNeeded) {
            Models.MovieItem m = getCurrentHero();
            if (m == null) return;
            Context ctx = itemView.getContext();

            updateBackdropDebounced(m);
            kickerTv.setText(m.isTvSeries == 1 ? "PHIM BỘ NỔI BẬT" : "PHIM LẺ ĐỀ XUẤT");
            titleTv.setText(m.getDisplayTitle());
            descTv.setText(m.description != null && !m.description.isEmpty()
                    ? m.description
                    : (m.subtitle != null ? m.subtitle : ""));

            badgesRow.removeAllViews();
            if (!m.videoQuality.isEmpty()) addHeroTag(ctx, m.videoQuality, Color.WHITE);
            if (!m.imdbRating.isEmpty() && !"0".equals(m.imdbRating)) addHeroTag(ctx, "IMDb ★ " + m.imdbRating, UiHelper.COLOR_GOLD);
            if (!m.year.isEmpty()) addHeroTag(ctx, m.year, UiHelper.COLOR_TEXT_SECONDARY);
            if (!m.episodeBadge.isEmpty()) addHeroTag(ctx, m.episodeBadge, UiHelper.COLOR_CYAN);
            if (!m.genres.isEmpty()) addHeroTag(ctx, m.genres.get(0), UiHelper.COLOR_TEXT_SECONDARY);

            btnPlay.setText(m.progressPercent > 0 ? "▶  Xem tiếp" : "▶  Xem ngay");
            btnWishlist.setText(m.inWishlist ? "✓  Đã lưu" : "+  Thêm vào");

            dotsRow.removeAllViews();
            int maxDots = Math.min(heroMovies.size(), 9);
            for (int i = 0; i < maxDots; i++) {
                View dot = new View(ctx);
                boolean active = i == (heroIndex % maxDots);
                dot.setBackground(UiHelper.createGlassRect(
                        ctx, 3, active ? UiHelper.COLOR_ACCENT_RED : Color.argb(95, 255, 255, 255), Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        UiHelper.dp(ctx, active ? 18 : 5), UiHelper.dp(ctx, 5));
                dlp.leftMargin = UiHelper.dp(ctx, 5);
                dotsRow.addView(dot, dlp);
            }
            if (focusPlayIfNeeded && !itemView.hasFocus()) {
                btnPlay.requestFocus();
            }
        }

        private void addHeroTag(Context ctx, String text, int textColor) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(textColor);
            tv.setTextSize(10f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setPadding(UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 2));
            tv.setBackground(UiHelper.createPillDrawable(
                    ctx, 4, Color.argb(175, 24, 27, 40), Color.argb(34, 255, 255, 255), 0.8f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiHelper.dp(ctx, 6);
            badgesRow.addView(tv, lp);
        }

        private TextView makeHeroPill(Context ctx, String text, boolean accent) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(12.5f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 8));
            UiHelper.applyFocusableGlassButton(tv, ctx, 17, accent);
            return tv;
        }

        private TextView makeCompactCircleArrow(Context ctx, String text) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            UiHelper.applyCircularIconButtonStyle(tv, ctx, 30);
            return tv;
        }

        private LinearLayout.LayoutParams heroBtnLp(Context ctx, int rightMarginDp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, UiHelper.dp(ctx, 34));
            lp.rightMargin = UiHelper.dp(ctx, rightMarginDp);
            return lp;
        }

        private LinearLayout.LayoutParams arrowBtnLp(Context ctx, int rightMarginDp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 30), UiHelper.dp(ctx, 30));
            lp.rightMargin = UiHelper.dp(ctx, rightMarginDp);
            return lp;
        }
    }

    // ---- Genre Filter Chips Row (Shown on sub-tabs only) ----
    private class GenreStripHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;

        GenreStripHolder(Context ctx) {
            super(new HorizontalScrollView(ctx));
            HorizontalScrollView hsv = (HorizontalScrollView) itemView;
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setClipChildren(false);
            hsv.setClipToPadding(false);
            hsv.setPadding(UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 6));
            hsv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            hsv.addView(row);
        }

        void bind() {
            Context ctx = itemView.getContext();
            row.removeAllViews();
            addChip(ctx, "", "Tất cả thể loại", true);
            for (Models.FilterOption g : genres) {
                addChip(ctx, String.valueOf(g.id), g.name, false);
            }
        }

        private void addChip(Context ctx, final String gid, String label, boolean isFirst) {
            boolean selected = gid.equals(currentGenreId);
            TextView chip = new TextView(ctx);
            chip.setText(label);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(11.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setPadding(UiHelper.dp(ctx, 13), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 13), UiHelper.dp(ctx, 6));
            UiHelper.applyCompactChipStyle(chip, ctx, 14, selected);
            if (isFirst) {
                chip.setOnKeyListener(moveFocusToRailLeftListener);
            }
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadHome(currentMode, gid, false);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiHelper.dp(ctx, 8);
            row.addView(chip, lp);
        }
    }

    // ---- Topics Row ----
    private class TopicsHolder extends RecyclerView.ViewHolder {
        final TextView titleTv;
        final RecyclerView rv;
        final List<Models.TopicItem> list = new ArrayList<>();
        final TopicAdapter adapter = new TopicAdapter();

        TopicsHolder(Context ctx) {
            super(new LinearLayout(ctx));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            LinearLayout hdr = buildSectionHeaderRow(ctx, "Chủ đề nổi bật", null);
            titleTv = (TextView) hdr.getChildAt(1);
            root.addView(hdr);

            rv = new RecyclerView(ctx);
            rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            rv.setClipChildren(false);
            rv.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 8));
            rv.setAdapter(adapter);
            root.addView(rv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        void bind(RowEntry entry) {
            titleTv.setText(entry.title != null && !entry.title.isEmpty() ? entry.title : "Chủ đề nổi bật");
            list.clear();
            list.addAll(entry.topics);
            adapter.notifyDataSetChanged();
        }

        class TopicAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                Context ctx = parent.getContext();
                LinearLayout box = new LinearLayout(ctx);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 10));
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(UiHelper.dp(ctx, 172), UiHelper.dp(ctx, 64));
                lp.setMargins(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 4));
                box.setLayoutParams(lp);
                UiHelper.applyFocusableGlassButton(box, ctx, 12, false);

                TextView t1 = new TextView(ctx);
                t1.setTextColor(Color.WHITE);
                t1.setTextSize(13f);
                t1.setTypeface(null, Typeface.BOLD);
                t1.setMaxLines(1);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(t1);

                TextView t2 = new TextView(ctx);
                t2.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                t2.setTextSize(10.5f);
                t2.setMaxLines(1);
                t2.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                l2.topMargin = UiHelper.dp(ctx, 3);
                box.addView(t2, l2);

                return new RecyclerView.ViewHolder(box) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                final Models.TopicItem t = list.get(position);
                LinearLayout box = (LinearLayout) holder.itemView;
                ((TextView) box.getChildAt(0)).setText(t.title);
                ((TextView) box.getChildAt(1)).setText(t.subtitle);
                box.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scrollToSectionId(t.targetSection);
                    }
                });
                box.setOnKeyListener(position == 0 ? moveFocusToRailLeftListener : null);
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        }
    }

    // ---- Movie Carousel Row ----
    private class CarouselHolder extends RecyclerView.ViewHolder {
        final TextView titleTv;
        final TextView seeAllTv;
        final RecyclerView rv;
        final List<Models.MovieItem> items = new ArrayList<>();
        int cardType = MovieCardView.TYPE_VERTICAL;
        String currentTitle = "";
        final InnerAdapter adapter = new InnerAdapter();

        CarouselHolder(Context ctx) {
            super(new LinearLayout(ctx));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            LinearLayout hdr = buildSectionHeaderRow(ctx, "", new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onOpenExploreCategory(currentTitle);
                }
            });
            titleTv = (TextView) hdr.getChildAt(1);
            seeAllTv = (TextView) hdr.getChildAt(2);
            root.addView(hdr);

            rv = new RecyclerView(ctx);
            rv.setId(View.generateViewId());
            rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            rv.setClipChildren(false);
            rv.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 10));
            rv.setAdapter(adapter);
            seeAllTv.setNextFocusDownId(rv.getId());
            root.addView(rv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        void bind(RowEntry row) {
            this.currentTitle = row.title != null ? row.title : "";
            titleTv.setText(currentTitle);
            seeAllTv.setVisibility(listener != null ? VISIBLE : GONE);
            this.cardType = MovieCardView.resolveCardType(row.layout);
            items.clear();
            items.addAll(row.movies);
            adapter.notifyDataSetChanged();
        }

        class InnerAdapter extends RecyclerView.Adapter<MovieCardView.Holder> {
            @Override
            public int getItemViewType(int position) {
                return cardType;
            }

            @NonNull
            @Override
            public MovieCardView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return MovieCardView.createHolder(parent.getContext(), viewType, false,
                        new MovieCardView.OnMovieClickListener() {
                            @Override
                            public void onMovieClick(Models.MovieItem item) {
                                if (listener != null) listener.onOpenMovieDetail(item);
                            }
                        },
                        new MovieCardView.OnMovieFocusListener() {
                            @Override
                            public void onMovieFocused(Models.MovieItem item, boolean hasFocus) {
                                if (hasFocus) updateBackdropDebounced(item);
                            }
                        });
            }

            @Override
            public void onBindViewHolder(@NonNull MovieCardView.Holder holder, int position) {
                MovieCardView.bind(holder, items.get(position), cardType, position);
                holder.itemView.setOnKeyListener(position == 0 ? moveFocusToRailLeftListener : null);
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        }
    }

    private LinearLayout buildSectionHeaderRow(Context ctx, String title, OnClickListener onSeeAll) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 22), UiHelper.dp(ctx, 4));

        View bar = new View(ctx);
        bar.setBackground(UiHelper.createGlassRect(ctx, 2, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 14));
        bLp.rightMargin = UiHelper.dp(ctx, 8);
        row.addView(bar, bLp);

        TextView titleTv = new TextView(ctx);
        titleTv.setText(title);
        titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(14.5f);
        titleTv.setTypeface(null, Typeface.BOLD);
        row.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView seeAll = new TextView(ctx);
        seeAll.setText("Xem tất cả  ›");
        seeAll.setTextColor(Color.WHITE);
        seeAll.setTextSize(11.5f);
        seeAll.setTypeface(null, Typeface.BOLD);
        seeAll.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 5), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 5));
        if (onSeeAll != null) {
            UiHelper.applyCompactChipStyle(seeAll, ctx, 12, false);
            seeAll.setOnClickListener(onSeeAll);
            seeAll.setOnKeyListener(moveFocusToRailLeftListener);
        } else {
            seeAll.setVisibility(GONE);
        }
        row.addView(seeAll);
        return row;
    }
}
