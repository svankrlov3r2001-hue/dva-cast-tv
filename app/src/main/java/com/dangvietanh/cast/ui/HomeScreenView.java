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

import java.util.ArrayList;
import java.util.List;

public class HomeScreenView extends FrameLayout {

    public interface Listener {
        void onOpenMovieDetail(Models.MovieItem item);
        void onPlayMovie(Models.MovieItem item);
        void onShowToast(String message, int accentColor);
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

    private final ImageView ambientBackdropIv;
    private final RecyclerView verticalRv;
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

    public HomeScreenView(Context context, ApiClient apiClient, Listener listener) {
        super(context);
        this.apiClient = apiClient;
        this.listener = listener;

        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Crossfading full-bleed cinema backdrop
        ambientBackdropIv = new ImageView(context);
        ambientBackdropIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ambientBackdropIv.setAlpha(0.45f);
        addView(ambientBackdropIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View leftScrim = new View(context);
        leftScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(242, 7, 9, 15), Color.argb(196, 7, 9, 15), Color.argb(90, 7, 9, 15)}));
        addView(leftScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View bottomScrim = new View(context);
        bottomScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(180, 7, 9, 15), UiHelper.COLOR_BG_BASE}));
        addView(bottomScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        verticalRv = new RecyclerView(context);
        verticalRv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        verticalRv.setClipToPadding(false);
        verticalRv.setClipChildren(false);
        verticalRv.setPadding(0, UiHelper.dp(context, 14), 0, UiHelper.dp(context, 48));
        verticalAdapter = new VerticalAdapter();
        verticalRv.setAdapter(verticalAdapter);
        addView(verticalRv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Loading overlay
        loadingBox = new LinearLayout(context);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        UiHelper.SpinnerView spin = new UiHelper.SpinnerView(context);
        loadingBox.addView(spin, new LinearLayout.LayoutParams(UiHelper.dp(context, 42), UiHelper.dp(context, 42)));
        TextView loadTv = new TextView(context);
        loadTv.setText("Đang tải kho phim...");
        loadTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        loadTv.setTextSize(14f);
        LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ltLp.topMargin = UiHelper.dp(context, 12);
        loadingBox.addView(loadTv, ltLp);
        addView(loadingBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Error overlay
        errorBox = new LinearLayout(context);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setVisibility(GONE);

        errorTv = new TextView(context);
        errorTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        errorTv.setTextSize(15f);
        errorTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errLp.bottomMargin = UiHelper.dp(context, 16);
        errorBox.addView(errorTv, errLp);

        retryBtn = new TextView(context);
        retryBtn.setText("Thử lại");
        retryBtn.setTextColor(Color.WHITE);
        retryBtn.setTextSize(14f);
        retryBtn.setTypeface(null, Typeface.BOLD);
        retryBtn.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 11), UiHelper.dp(context, 24), UiHelper.dp(context, 11));
        UiHelper.applyPrimaryGradientButton(retryBtn, context, 24);
        retryBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                loadHome(currentMode, currentGenreId, false);
            }
        });
        errorBox.addView(retryBtn);
        addView(errorBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
                if (activeHeroHolder != null && activeHeroHolder.btnPlay != null) {
                    activeHeroHolder.btnPlay.requestFocus();
                } else {
                    verticalRv.requestFocus();
                }
            }
        });
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
        if (!genres.isEmpty()) {
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
        handler.postDelayed(pendingBackdropRunnable, 160L);
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

    // ---- Row 0: Hero Showcase ----
    private class HeroHolder extends RecyclerView.ViewHolder {
        final ImageView heroThumbIv;
        final TextView metaTv;
        final TextView titleTv;
        final TextView descTv;
        final TextView btnPlay;
        final TextView btnInfo;
        final TextView btnWishlist;
        final TextView btnPrev;
        final TextView btnNext;
        final LinearLayout dotsRow;

        HeroHolder(Context ctx) {
            super(new FrameLayout(ctx));
            FrameLayout wrap = (FrameLayout) itemView;
            wrap.setClipChildren(false);
            wrap.setClipToPadding(false);
            RecyclerView.LayoutParams rLp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 372));
            rLp.setMargins(UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 16));
            wrap.setLayoutParams(rLp);

            FrameLayout card = new FrameLayout(ctx);
            UiHelper.applyGlassCardStyle(card, ctx, 36, UiHelper.COLOR_SURFACE_GLASS, 24);
            wrap.addView(card, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            heroThumbIv = new ImageView(ctx);
            heroThumbIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroThumbIv.setAlpha(0.76f);
            card.addView(heroThumbIv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            View leftGrad = new View(ctx);
            leftGrad.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.argb(238, 8, 10, 16), Color.argb(176, 8, 10, 16), Color.TRANSPARENT}));
            card.addView(leftGrad, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout contentCol = new LinearLayout(ctx);
            contentCol.setOrientation(LinearLayout.VERTICAL);
            contentCol.setGravity(Gravity.BOTTOM | Gravity.START);
            contentCol.setPadding(UiHelper.dp(ctx, 34), UiHelper.dp(ctx, 24),
                    UiHelper.dp(ctx, 34), UiHelper.dp(ctx, 26));
            card.addView(contentCol, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            metaTv = new TextView(ctx);
            metaTv.setTextColor(UiHelper.COLOR_GOLD);
            metaTv.setTextSize(12.5f);
            metaTv.setTypeface(null, Typeface.BOLD);
            contentCol.addView(metaTv);

            titleTv = new TextView(ctx);
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTextSize(30f);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setMaxLines(2);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    UiHelper.dp(ctx, 560), LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = UiHelper.dp(ctx, 6);
            contentCol.addView(titleTv, tLp);

            descTv = new TextView(ctx);
            descTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
            descTv.setTextSize(13.5f);
            descTv.setMaxLines(2);
            descTv.setEllipsize(TextUtils.TruncateAt.END);
            descTv.setLineSpacing(UiHelper.dp(ctx, 2), 1f);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                    UiHelper.dp(ctx, 540), LinearLayout.LayoutParams.WRAP_CONTENT);
            dLp.topMargin = UiHelper.dp(ctx, 8);
            dLp.bottomMargin = UiHelper.dp(ctx, 18);
            contentCol.addView(descTv, dLp);

            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.CENTER_VERTICAL);
            btnRow.setClipChildren(false);
            contentCol.addView(btnRow);

            btnPlay = makeHeroBtn(ctx, "▶  Xem ngay", true);
            btnInfo = makeHeroBtn(ctx, "ℹ  Thông tin", false);
            btnWishlist = makeHeroBtn(ctx, "+  Danh sách", false);
            btnPrev = makeArrowBtn(ctx, "◀");
            btnNext = makeArrowBtn(ctx, "▶");

            btnRow.addView(btnPlay, heroBtnLp(ctx, 12));
            btnRow.addView(btnInfo, heroBtnLp(ctx, 12));
            btnRow.addView(btnWishlist, heroBtnLp(ctx, 16));
            btnRow.addView(btnPrev, heroBtnLp(ctx, 8));
            btnRow.addView(btnNext, heroBtnLp(ctx, 0));

            dotsRow = new LinearLayout(ctx);
            dotsRow.setOrientation(LinearLayout.HORIZONTAL);
            dotsRow.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            dotsLp.gravity = Gravity.BOTTOM | Gravity.END;
            dotsLp.setMargins(0, 0, UiHelper.dp(ctx, 30), UiHelper.dp(ctx, 28));
            card.addView(dotsRow, dotsLp);

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

            String img = !m.thumbnailUrl.isEmpty() ? m.thumbnailUrl : m.posterUrl;
            UiHelper.safeLoadBackdrop(heroThumbIv, img);
            updateBackdropDebounced(m);

            StringBuilder meta = new StringBuilder();
            if (!m.videoQuality.isEmpty()) meta.append(m.videoQuality);
            if (!m.imdbRating.isEmpty() && !"0".equals(m.imdbRating)) meta.append("  •  IMDb ★ ").append(m.imdbRating);
            if (!m.year.isEmpty()) meta.append("  •  ").append(m.year);
            if (!m.episodeBadge.isEmpty()) meta.append("  •  ").append(m.episodeBadge);
            if (!m.genres.isEmpty()) meta.append("  •  ").append(TextUtils.join(", ", m.genres));
            metaTv.setText(meta.toString());

            titleTv.setText(m.getDisplayTitle());
            descTv.setText(m.description != null && !m.description.isEmpty()
                    ? m.description
                    : (m.subtitle != null ? m.subtitle : ""));

            btnPlay.setText(m.progressPercent > 0 ? "▶  Xem tiếp" : "▶  Xem ngay");
            btnWishlist.setText(m.inWishlist ? "✓  Đã lưu" : "+  Danh sách");

            dotsRow.removeAllViews();
            int maxDots = Math.min(heroMovies.size(), 8);
            for (int i = 0; i < maxDots; i++) {
                View dot = new View(ctx);
                boolean active = i == (heroIndex % maxDots);
                dot.setBackground(UiHelper.createGlassRect(
                        ctx, 4, active ? UiHelper.COLOR_ACCENT_RED : Color.argb(90, 255, 255, 255), Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        UiHelper.dp(ctx, active ? 22 : 7), UiHelper.dp(ctx, 7));
                dlp.leftMargin = UiHelper.dp(ctx, 6);
                dotsRow.addView(dot, dlp);
            }
            if (focusPlayIfNeeded && !itemView.hasFocus()) {
                btnPlay.requestFocus();
            }
        }

        private TextView makeHeroBtn(Context ctx, String text, boolean accent) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setPadding(UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 11));
            UiHelper.applyFocusableGlassButton(tv, ctx, 24, accent);
            return tv;
        }

        private TextView makeArrowBtn(Context ctx, String text) {
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(13f);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 11));
            UiHelper.applyFocusableGlassButton(tv, ctx, 24, false);
            return tv;
        }

        private LinearLayout.LayoutParams heroBtnLp(Context ctx, int rightMarginDp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiHelper.dp(ctx, rightMarginDp);
            return lp;
        }
    }

    // ---- Genre Filter Chips Row ----
    private class GenreStripHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;

        GenreStripHolder(Context ctx) {
            super(new HorizontalScrollView(ctx));
            HorizontalScrollView hsv = (HorizontalScrollView) itemView;
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setClipToPadding(false);
            hsv.setPadding(UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 10));
            hsv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            hsv.addView(row);
        }

        void bind() {
            Context ctx = itemView.getContext();
            row.removeAllViews();
            addChip(ctx, "", "Tất cả thể loại");
            for (Models.FilterOption g : genres) {
                addChip(ctx, String.valueOf(g.id), g.name);
            }
        }

        private void addChip(Context ctx, final String gid, String label) {
            boolean selected = gid.equals(currentGenreId);
            TextView chip = new TextView(ctx);
            chip.setText(label);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(13f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setPadding(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 8));
            UiHelper.applyFocusableGlassButton(chip, ctx, 24, selected);
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadHome(currentMode, gid, false);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UiHelper.dp(ctx, 10);
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

            titleTv = new TextView(ctx);
            titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            titleTv.setTextSize(19f);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setPadding(UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 8));
            root.addView(titleTv);

            rv = new RecyclerView(ctx);
            rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            rv.setClipChildren(false);
            rv.setPadding(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 14));
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
                box.setPadding(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 14));
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(UiHelper.dp(ctx, 210), UiHelper.dp(ctx, 88));
                lp.setMargins(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 6));
                box.setLayoutParams(lp);
                UiHelper.applyFocusableGlassButton(box, ctx, 24, false);

                TextView t1 = new TextView(ctx);
                t1.setTextColor(Color.WHITE);
                t1.setTextSize(15f);
                t1.setTypeface(null, Typeface.BOLD);
                t1.setMaxLines(1);
                t1.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(t1);

                TextView t2 = new TextView(ctx);
                t2.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                t2.setTextSize(12f);
                t2.setMaxLines(1);
                t2.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                l2.topMargin = UiHelper.dp(ctx, 6);
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
        final RecyclerView rv;
        final List<Models.MovieItem> items = new ArrayList<>();
        int cardType = MovieCardView.TYPE_VERTICAL;
        final InnerAdapter adapter = new InnerAdapter();

        CarouselHolder(Context ctx) {
            super(new LinearLayout(ctx));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            root.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            titleTv = new TextView(ctx);
            titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            titleTv.setTextSize(19.5f);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setPadding(UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 6));
            root.addView(titleTv);

            rv = new RecyclerView(ctx);
            rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false));
            rv.setClipToPadding(false);
            rv.setClipChildren(false);
            rv.setPadding(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 16));
            rv.setAdapter(adapter);
            root.addView(rv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        void bind(RowEntry row) {
            titleTv.setText(row.title);
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
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        }
    }
}
