package com.dangvietanh.cast.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.dangvietanh.cast.Prefs;
import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Full-bleed cinematic Movie Detail Screen synchronized with the unified left TvNavigationRail:
 * - Right-weighted full-bleed landscape backdrop + portrait poster
 * - Primary title + original subtitle, IMDb / Runtime / Year / Quality pill stats row
 * - Frosted genre & country tags, synopsis, capsule icon-text CTA buttons
 * - Compact vertical scrollable 5-column episode grid sorted ascending (Tập 1 -> Tập cuối)
 * - Crisp white focus borders + smooth TV D-Pad navigation
 */
public class MovieDetailScreenView extends FrameLayout {

    public interface Listener {
        void onCloseDetail();
        void onLaunchPlayer(Models.PlayerLaunchRequest request);
        void onShowToast(String message, int accentColor);
        void onNavigateTab(String tabId);
        void onOpenSettings();
    }

    private final ApiClient apiClient;
    private final Listener listener;
    private final List<Integer> historyStack = new ArrayList<>();

    private final ImageView backdropIv;
    private final ScrollView scrollView;
    private final LinearLayout contentCol;
    private final LinearLayout loadingBox;
    private final LinearLayout errorBox;
    private final TextView errorTv;

    private final ImageView posterIv;
    private final TextView primaryTitleTv;
    private final TextView subTitleTv;
    private final LinearLayout statsRow;
    private final LinearLayout chipsRow;
    private final TextView descTv;

    private final LinearLayout btnPlay;
    private final TextView btnPlayText;
    private final LinearLayout btnWishlist;
    private final ImageView btnWishlistIcon;
    private final TextView btnWishlistText;
    private final LinearLayout btnLike;
    private final LinearLayout btnDislike;
    private final LinearLayout btnBack;

    private final LinearLayout seasonSection;
    private final TextView epTotalCountTv;
    private final LinearLayout seasonTabsRow;
    private final RecyclerView episodeRv;
    private final LinearLayoutManager episodeLayoutManager;
    private final LinearLayout sourcesSection;
    private final LinearLayout sourcesRow;
    private final LinearLayout castSection;
    private final LinearLayout castRow;
    private final LinearLayout relatedSection;
    private final RecyclerView relatedRv;

    private Models.MovieDetail currentDetail;
    private final List<Models.EpisodeItem> displayedEpisodes = new ArrayList<>();
    private final EpisodeAdapter episodeAdapter = new EpisodeAdapter();
    private String currentEpFallbackUrl = "";
    private int activeEpisodeIndex = 0;

    private final List<Models.MovieItem> relatedMovies = new ArrayList<>();
    private final RelatedAdapter relatedAdapter = new RelatedAdapter();
    private int selectedSeasonId = 0;
    private int requestSeq = 0;

    public MovieDetailScreenView(Context context, ApiClient apiClient, final Listener listener) {
        super(context);
        this.apiClient = apiClient;
        this.listener = listener;

        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setBackgroundColor(UiHelper.COLOR_BG_BASE);
        setClickable(true);

        // 1. Right-weighted full-bleed cinematic backdrop (NO header box)
        backdropIv = new ImageView(context);
        backdropIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropIv.setAlpha(0.90f);
        addView(backdropIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 388), Gravity.TOP));

        View leftScrim = new View(context);
        leftScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(250, 9, 11, 18),
                        Color.argb(226, 9, 11, 18),
                        Color.argb(120, 9, 11, 18),
                        Color.TRANSPARENT
                }));
        addView(leftScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 392), Gravity.TOP));

        View bottomScrim = new View(context);
        bottomScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(145, 9, 11, 18),
                        Color.argb(246, 9, 11, 18),
                        UiHelper.COLOR_BG_BASE
                }));
        addView(bottomScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 2. Main Content ScrollView (sits cleanly beside the shared TvNavigationRail in MainActivity)
        setClipChildren(false);
        setClipToPadding(false);
        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);
        FrameLayout.LayoutParams svLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        svLp.leftMargin = 0;
        addView(scrollView, svLp);

        contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 22),
                UiHelper.dp(context, 24), UiHelper.dp(context, 28));
        contentCol.setClipChildren(false);
        contentCol.setClipToPadding(false);
        scrollView.addView(contentCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 3. Open Split Hero Header Row (Poster + Info Stack)
        LinearLayout heroRow = new LinearLayout(context);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.TOP);
        heroRow.setClipChildren(false);
        heroRow.setClipToPadding(false);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrLp.bottomMargin = UiHelper.dp(context, 20);

        // Left Portrait Poster
        final FrameLayout posterWrap = new FrameLayout(context);
        posterWrap.setBackgroundColor(Color.parseColor("#141824"));
        posterWrap.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), 14));
            }
        });
        posterWrap.setClipToOutline(true);
        posterWrap.setElevation(UiHelper.dp(context, 16));

        posterIv = new ImageView(context);
        posterIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterWrap.addView(posterIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View posterRim = new View(context);
        posterRim.setBackground(UiHelper.createFocusRingDrawable(
                context, 14, Color.argb(135, 180, 210, 255), 1.3f));
        posterWrap.addView(posterRim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams pwLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 158), UiHelper.dp(context, 234));
        pwLp.rightMargin = UiHelper.dp(context, 24);
        heroRow.addView(posterWrap, pwLp);

        // Right Movie Info Column (maxWidth ~510dp so right side backdrop remains clearly visible)
        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setClipChildren(false);
        heroRow.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        primaryTitleTv = new TextView(context);
        primaryTitleTv.setTextColor(Color.WHITE);
        primaryTitleTv.setTextSize(26f);
        primaryTitleTv.setTypeface(null, Typeface.BOLD);
        primaryTitleTv.setMaxLines(2);
        primaryTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        primaryTitleTv.setShadowLayer(8f, 0f, 2f, Color.argb(180, 0, 0, 0));
        infoCol.addView(primaryTitleTv);

        subTitleTv = new TextView(context);
        subTitleTv.setTextColor(Color.parseColor("#B8C2D6"));
        subTitleTv.setTextSize(14f);
        subTitleTv.setMaxLines(1);
        subTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = UiHelper.dp(context, 2);
        infoCol.addView(subTitleTv, stLp);

        // Stats Row (IMDb | Runtime | Year | Quality Badge)
        statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.topMargin = UiHelper.dp(context, 9);
        infoCol.addView(statsRow, srLp);

        // Genre & Country Frosted Pills
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setGravity(Gravity.CENTER_VERTICAL);
        chipsScroll.addView(chipsRow);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        csLp.topMargin = UiHelper.dp(context, 9);
        infoCol.addView(chipsScroll, csLp);

        // Synopsis (max 4 lines, constrained width for cinema balance)
        descTv = new TextView(context);
        descTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        descTv.setTextSize(11.5f);
        descTv.setMaxLines(4);
        descTv.setEllipsize(TextUtils.TruncateAt.END);
        descTv.setLineSpacing(UiHelper.dp(context, 2.5f), 1f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 495), LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = UiHelper.dp(context, 9);
        dLp.bottomMargin = UiHelper.dp(context, 14);
        infoCol.addView(descTv, dLp);

        // Capsule Icon-Text CTA Buttons Row
        infoCol.setClipToPadding(false);
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        actionsRow.setClipChildren(false);
        actionsRow.setClipToPadding(false);

        btnPlay = makeCapsuleCta(context, R.drawable.ic_play, "Xem ngay", true);
        btnPlayText = (TextView) btnPlay.getChildAt(1);

        btnWishlist = makeCapsuleCta(context, R.drawable.ic_bookmark_outline, "Thêm vào danh sách", false);
        btnWishlistIcon = (ImageView) btnWishlist.getChildAt(0);
        btnWishlistText = (TextView) btnWishlist.getChildAt(1);

        btnLike = makeCapsuleCta(context, R.drawable.ic_thumb_up, "Thích", false);
        btnDislike = makeCapsuleCta(context, R.drawable.ic_thumb_down, "Không thích", false);
        btnBack = makeCapsuleCta(context, R.drawable.ic_back, "Quay lại", false);

        btnPlay.setId(View.generateViewId());
        btnWishlist.setId(View.generateViewId());
        btnLike.setId(View.generateViewId());
        btnDislike.setId(View.generateViewId());
        btnBack.setId(View.generateViewId());

        btnPlay.setNextFocusRightId(btnWishlist.getId());
        btnWishlist.setNextFocusLeftId(btnPlay.getId());
        btnWishlist.setNextFocusRightId(btnLike.getId());
        btnLike.setNextFocusLeftId(btnWishlist.getId());
        btnLike.setNextFocusRightId(btnDislike.getId());
        btnDislike.setNextFocusLeftId(btnLike.getId());
        btnDislike.setNextFocusRightId(btnBack.getId());
        btnBack.setNextFocusLeftId(btnDislike.getId());

        OnKeyListener heroDownKeyListener = new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return focusFirstSectionBelowHero();
                }
                return false;
            }
        };
        btnPlay.setOnKeyListener(heroDownKeyListener);
        btnWishlist.setOnKeyListener(heroDownKeyListener);
        btnLike.setOnKeyListener(heroDownKeyListener);
        btnDislike.setOnKeyListener(heroDownKeyListener);
        btnBack.setOnKeyListener(heroDownKeyListener);

        actionsRow.addView(btnPlay, marginRightLp(context, 9));
        actionsRow.addView(btnWishlist, marginRightLp(context, 9));
        actionsRow.addView(btnLike, marginRightLp(context, 9));
        actionsRow.addView(btnDislike, marginRightLp(context, 9));
        actionsRow.addView(btnBack, marginRightLp(context, 0));
        infoCol.addView(actionsRow);

        contentCol.addView(heroRow, hrLp);

        // 5. Episodes Section (Compact Vertical Scrollable 5-Column Grid, Sorted Tập 1 -> Tập cuối)
        seasonSection = new LinearLayout(context);
        seasonSection.setOrientation(LinearLayout.VERTICAL);
        seasonSection.setClipChildren(false);
        seasonSection.setClipToPadding(false);
        seasonSection.setVisibility(GONE);

        LinearLayout epHdrRow = new LinearLayout(context);
        epHdrRow.setOrientation(LinearLayout.HORIZONTAL);
        epHdrRow.setGravity(Gravity.CENTER_VERTICAL);
        epHdrRow.setClipChildren(false);
        epHdrRow.setClipToPadding(false);
        LinearLayout.LayoutParams ehrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ehrLp.bottomMargin = UiHelper.dp(context, 10);

        View epBar = new View(context);
        epBar.setBackground(UiHelper.createGlassRect(context, 2, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams ebLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 3), UiHelper.dp(context, 15));
        ebLp.rightMargin = UiHelper.dp(context, 8);
        epHdrRow.addView(epBar, ebLp);

        TextView epTitle = new TextView(context);
        epTitle.setText("Danh sách tập phim");
        epTitle.setTextColor(Color.WHITE);
        epTitle.setTextSize(14.5f);
        epTitle.setTypeface(null, Typeface.BOLD);
        epHdrRow.addView(epTitle, marginRightLp(context, 14));

        HorizontalScrollView seasonScroll = new HorizontalScrollView(context);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonScroll.setClipChildren(false);
        seasonScroll.setClipToPadding(false);
        seasonTabsRow = new LinearLayout(context);
        seasonTabsRow.setOrientation(LinearLayout.HORIZONTAL);
        seasonTabsRow.setClipChildren(false);
        seasonTabsRow.setClipToPadding(false);
        seasonTabsRow.setPadding(UiHelper.dp(context, 2), UiHelper.dp(context, 3),
                UiHelper.dp(context, 2), UiHelper.dp(context, 3));
        seasonScroll.addView(seasonTabsRow);
        epHdrRow.addView(seasonScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        epTotalCountTv = new TextView(context);
        epTotalCountTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        epTotalCountTv.setTextSize(11.5f);
        epTotalCountTv.setTypeface(null, Typeface.BOLD);
        epHdrRow.addView(epTotalCountTv);
        seasonSection.addView(epHdrRow, ehrLp);

        episodeLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        episodeRv = new RecyclerView(context);
        episodeRv.setId(View.generateViewId());
        episodeRv.setLayoutManager(episodeLayoutManager);
        episodeRv.setItemAnimator(null);
        episodeRv.setVerticalScrollBarEnabled(false);
        episodeRv.setHorizontalScrollBarEnabled(false);
        episodeRv.setNestedScrollingEnabled(false);
        episodeRv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        episodeRv.setClipChildren(false);
        episodeRv.setClipToPadding(false);
        episodeRv.setPadding(UiHelper.dp(context, 4), UiHelper.dp(context, 6),
                UiHelper.dp(context, 16), UiHelper.dp(context, 6));
        episodeRv.setAdapter(episodeAdapter);

        seasonSection.addView(episodeRv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 114)));
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        secLp.bottomMargin = UiHelper.dp(context, 18);
        contentCol.addView(seasonSection, secLp);

        // 6. Sources Section (Movies)
        sourcesSection = new LinearLayout(context);
        sourcesSection.setOrientation(LinearLayout.VERTICAL);
        sourcesSection.setClipChildren(false);
        sourcesSection.setVisibility(GONE);
        sourcesSection.addView(makeSectionHeader(context, "Nguồn phát khả dụng"));
        HorizontalScrollView srcScroll = new HorizontalScrollView(context);
        srcScroll.setHorizontalScrollBarEnabled(false);
        srcScroll.setClipChildren(false);
        srcScroll.setClipToPadding(false);
        sourcesRow = new LinearLayout(context);
        sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
        sourcesRow.setClipChildren(false);
        srcScroll.addView(sourcesRow);
        sourcesSection.addView(srcScroll);
        contentCol.addView(sourcesSection, secLp);

        // 7. Cast & Crew Section
        castSection = new LinearLayout(context);
        castSection.setOrientation(LinearLayout.VERTICAL);
        castSection.setClipChildren(false);
        castSection.setVisibility(GONE);
        castSection.addView(makeSectionHeader(context, "Đạo diễn & Diễn viên"));
        HorizontalScrollView castScroll = new HorizontalScrollView(context);
        castScroll.setHorizontalScrollBarEnabled(false);
        castScroll.setClipChildren(false);
        castScroll.setClipToPadding(false);
        castRow = new LinearLayout(context);
        castRow.setOrientation(LinearLayout.HORIZONTAL);
        castRow.setClipChildren(false);
        castScroll.addView(castRow);
        castSection.addView(castScroll);
        contentCol.addView(castSection, secLp);

        // 8. Related Movies Section
        relatedSection = new LinearLayout(context);
        relatedSection.setOrientation(LinearLayout.VERTICAL);
        relatedSection.setClipChildren(false);
        relatedSection.setClipToPadding(false);
        relatedSection.setVisibility(GONE);
        relatedSection.addView(makeSectionHeader(context, "Có thể bạn cũng thích"));
        relatedRv = new RecyclerView(context);
        relatedRv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        relatedRv.setClipChildren(false);
        relatedRv.setClipToPadding(false);
        relatedRv.setAdapter(relatedAdapter);
        relatedSection.addView(relatedRv);
        contentCol.addView(relatedSection);

        // 9. Loading overlay
        loadingBox = new LinearLayout(context);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        loadingBox.setBackgroundColor(Color.argb(195, 9, 11, 18));
        loadingBox.setVisibility(GONE);
        loadingBox.addView(new UiHelper.SpinnerView(context),
                new LinearLayout.LayoutParams(UiHelper.dp(context, 38), UiHelper.dp(context, 38)));
        addView(loadingBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 10. Error overlay
        errorBox = new LinearLayout(context);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setBackgroundColor(Color.argb(220, 9, 11, 18));
        errorBox.setVisibility(GONE);
        errorTv = new TextView(context);
        errorTv.setTextColor(Color.WHITE);
        errorTv.setTextSize(14.5f);
        errorBox.addView(errorTv);
        LinearLayout btnRetry = makeCapsuleCta(context, R.drawable.ic_back, "Quay lại", true);
        btnRetry.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleBack() && listener != null) {
                    listener.onCloseDetail();
                }
            }
        });
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = UiHelper.dp(context, 14);
        errorBox.addView(btnRetry, brLp);
        addView(errorBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bindActionListeners();
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        if (btnPlay != null && scrollView != null
                && loadingBox != null && loadingBox.getVisibility() != VISIBLE
                && errorBox != null && errorBox.getVisibility() != VISIBLE) {
            scrollView.smoothScrollTo(0, 0);
            return btnPlay.requestFocus();
        }
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    private boolean focusFirstSectionBelowHero() {
        if (seasonSection.getVisibility() == VISIBLE) {
            if (seasonTabsRow.getChildCount() > 0) {
                return seasonTabsRow.getChildAt(0).requestFocus();
            }
            if (focusEpisodeGrid()) {
                return true;
            }
        }
        return focusFirstBelowEpisodes();
    }

    private boolean focusEpisodeGrid() {
        if (seasonSection.getVisibility() != VISIBLE || displayedEpisodes.isEmpty()) return false;
        int targetPos = Math.max(0, Math.min(activeEpisodeIndex, displayedEpisodes.size() - 1));
        RecyclerView.ViewHolder vh = episodeRv.findViewHolderForAdapterPosition(targetPos);
        if (vh instanceof EpisodeHolder) {
            return ((EpisodeHolder) vh).card.requestFocus();
        }
        if (episodeRv.getChildCount() > 0) {
            return episodeRv.getChildAt(0).requestFocus();
        }
        episodeRv.scrollToPosition(targetPos);
        episodeRv.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder holder = episodeRv.findViewHolderForAdapterPosition(activeEpisodeIndex);
                if (holder instanceof EpisodeHolder) {
                    ((EpisodeHolder) holder).card.requestFocus();
                } else if (episodeRv.getChildCount() > 0) {
                    episodeRv.getChildAt(0).requestFocus();
                }
            }
        });
        return true;
    }

    private boolean focusFirstBelowEpisodes() {
        if (sourcesSection.getVisibility() == VISIBLE && sourcesRow.getChildCount() > 0) {
            return sourcesRow.getChildAt(0).requestFocus();
        }
        if (castSection.getVisibility() == VISIBLE && castRow.getChildCount() > 0) {
            return castRow.getChildAt(0).requestFocus();
        }
        if (relatedSection.getVisibility() == VISIBLE) {
            if (relatedRv.getChildCount() > 0) {
                return relatedRv.getChildAt(0).requestFocus();
            }
            return relatedRv.requestFocus();
        }
        return false;
    }

    public void openMovie(int videosId, boolean pushHistory) {
        if (pushHistory && currentDetail != null && currentDetail.videosId > 0 && currentDetail.videosId != videosId) {
            historyStack.add(currentDetail.videosId);
        }
        loadDetail(videosId);
    }

    public void clearHistory() {
        historyStack.clear();
        currentDetail = null;
    }

    public boolean handleBack() {
        if (!historyStack.isEmpty()) {
            int prevId = historyStack.remove(historyStack.size() - 1);
            loadDetail(prevId);
            return true;
        }
        return false;
    }

    private void loadDetail(int videosId) {
        final int seq = ++requestSeq;
        loadingBox.setVisibility(VISIBLE);
        errorBox.setVisibility(GONE);
        scrollView.scrollTo(0, 0);

        apiClient.fetchMovieDetails(videosId, new ApiClient.Callback<Models.MovieDetailResponse>() {
            @Override
            public void onSuccess(Models.MovieDetailResponse data) {
                if (seq != requestSeq) return;
                loadingBox.setVisibility(GONE);
                if (data == null || data.movie == null) {
                    errorBox.setVisibility(VISIBLE);
                    errorTv.setText("Không tìm thấy thông tin phim");
                    return;
                }
                bindDetail(data);
            }

            @Override
            public void onError(String message, int httpCode) {
                if (seq != requestSeq) return;
                loadingBox.setVisibility(GONE);
                errorBox.setVisibility(VISIBLE);
                errorTv.setText(message != null ? message : "Lỗi tải thông tin phim");
            }
        });
    }

    private void bindDetail(Models.MovieDetailResponse res) {
        Models.MovieDetail m = res.movie;
        currentDetail = m;

        // Landscape (poster_image) for wide backdrop & episode fallback; Portrait (video_thumb) for vertical poster
        String landscapeUrl = m.getLandscapeUrl();
        String portraitUrl = m.getPortraitUrl();
        UiHelper.safeLoadBackdrop(backdropIv, landscapeUrl);
        loadPosterWithFallback(getContext(), posterIv, portraitUrl, landscapeUrl);

        // Split Title + Original Subtitle cleanly
        String rawTitle = m.title != null ? m.title.trim() : "";
        int dashIdx = rawTitle.indexOf(" - ");
        if (dashIdx > 0) {
            primaryTitleTv.setText(rawTitle.substring(0, dashIdx).trim());
            String sec = rawTitle.substring(dashIdx + 3).trim();
            subTitleTv.setText(sec);
            subTitleTv.setVisibility(sec.isEmpty() ? GONE : VISIBLE);
        } else {
            primaryTitleTv.setText(rawTitle);
            subTitleTv.setVisibility(GONE);
        }

        // Build Stats Row: ★ IMDb | Runtime | Year | Red-Pink Quality Pill | Watched progress
        statsRow.removeAllViews();
        Context ctx = getContext();
        boolean needSep = false;
        if (m.imdbRating != null && !m.imdbRating.isEmpty() && !"0".equals(m.imdbRating)) {
            TextView starTv = new TextView(ctx);
            starTv.setText("★  " + m.imdbRating + "/10");
            starTv.setTextColor(UiHelper.COLOR_GOLD);
            starTv.setTextSize(12.5f);
            starTv.setTypeface(null, Typeface.BOLD);
            statsRow.addView(starTv);
            needSep = true;
        }
        if (m.runtime != null && !m.runtime.isEmpty()) {
            if (needSep) addStatDivider(ctx);
            TextView rtTv = new TextView(ctx);
            rtTv.setText(m.runtime);
            rtTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            rtTv.setTextSize(12.5f);
            rtTv.setTypeface(null, Typeface.BOLD);
            statsRow.addView(rtTv);
            needSep = true;
        }
        if (m.year != null && !m.year.isEmpty()) {
            if (needSep) addStatDivider(ctx);
            TextView yTv = new TextView(ctx);
            yTv.setText(m.year);
            yTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            yTv.setTextSize(12.5f);
            yTv.setTypeface(null, Typeface.BOLD);
            statsRow.addView(yTv);
            needSep = true;
        }
        String q = m.videoQuality != null && !m.videoQuality.isEmpty() ? m.videoQuality : "FHD";
        if (needSep) addStatDivider(ctx);
        TextView qBadge = new TextView(ctx);
        qBadge.setText(q);
        qBadge.setTextColor(Color.WHITE);
        qBadge.setTextSize(10.5f);
        qBadge.setTypeface(null, Typeface.BOLD);
        qBadge.setPadding(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 2));
        GradientDrawable qGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#E50914"), Color.parseColor("#D50055")});
        qGrad.setCornerRadius(UiHelper.dp(ctx, 6));
        qBadge.setBackground(qGrad);
        statsRow.addView(qBadge);

        if (m.watchHistory != null && m.watchHistory.progressPercent > 0) {
            TextView progTag = new TextView(ctx);
            progTag.setText("Đã xem " + m.watchHistory.viewedRate);
            progTag.setTextColor(UiHelper.COLOR_GREEN);
            progTag.setTextSize(11.5f);
            progTag.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ptLp.leftMargin = UiHelper.dp(ctx, 10);
            statsRow.addView(progTag, ptLp);
            btnPlayText.setText("Xem tiếp");
        } else {
            btnPlayText.setText("Xem ngay");
        }

        // Build Genre & Country Chips
        chipsRow.removeAllViews();
        for (Models.FilterOption g : m.genres) {
            addFrostedChip(ctx, g.name);
        }
        for (Models.FilterOption c : m.countries) {
            addFrostedChip(ctx, c.name);
        }

        descTv.setText(m.description != null && !m.description.isEmpty() ? m.description : "Chưa có mô tả chi tiết.");
        updateWishlistVisual();
        updateReactionVisuals();

        // Seasons & Compact Vertical Scrollable 5-Column Episode Grid
        renderSeasonsAndEpisodes(m, landscapeUrl);

        // Sources
        sourcesRow.removeAllViews();
        if (!m.sources.isEmpty() && m.isTvSeries == 0) {
            sourcesSection.setVisibility(VISIBLE);
            for (int sIdx = 0; sIdx < m.sources.size(); sIdx++) {
                final Models.SourceItem src = m.sources.get(sIdx);
                LinearLayout sBtn = makeCapsuleCta(ctx, R.drawable.ic_play, src.label, false);
                sBtn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchWithStream(src.streamCode, 0, 1, "", src.label);
                    }
                });
                sourcesRow.addView(sBtn, marginRightLp(ctx, 9));
            }
        } else {
            sourcesSection.setVisibility(GONE);
        }

        // Cast & Directors
        castRow.removeAllViews();
        List<Models.StarItem> people = new ArrayList<>();
        people.addAll(m.directors);
        people.addAll(m.stars);
        if (!people.isEmpty()) {
            castSection.setVisibility(VISIBLE);
            for (int pIdx = 0; pIdx < people.size(); pIdx++) {
                Models.StarItem st = people.get(pIdx);
                LinearLayout personCard = new LinearLayout(ctx);
                personCard.setOrientation(LinearLayout.VERTICAL);
                personCard.setGravity(Gravity.CENTER_HORIZONTAL);
                personCard.setPadding(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8));
                UiHelper.applyFocusableGlassButton(personCard, ctx, 14, false);

                ImageView iv = new ImageView(ctx);
                UiHelper.safeLoadAvatar(iv, st.imageUrl);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 54), UiHelper.dp(ctx, 54));
                ivLp.bottomMargin = UiHelper.dp(ctx, 6);
                personCard.addView(iv, ivLp);

                TextView nm = new TextView(ctx);
                nm.setText(st.name);
                nm.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
                nm.setTextSize(11.5f);
                nm.setTypeface(null, Typeface.BOLD);
                nm.setMaxLines(1);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                nm.setGravity(Gravity.CENTER);
                personCard.addView(nm);

                TextView role = new TextView(ctx);
                role.setText("director".equalsIgnoreCase(st.starType) ? "Đạo diễn" : "Diễn viên");
                role.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                role.setTextSize(10f);
                role.setGravity(Gravity.CENTER);
                personCard.addView(role);

                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 98), LinearLayout.LayoutParams.WRAP_CONTENT);
                cLp.rightMargin = UiHelper.dp(ctx, 10);
                castRow.addView(personCard, cLp);
            }
        } else {
            castSection.setVisibility(GONE);
        }

        // Related movies
        relatedMovies.clear();
        if (res.related != null) relatedMovies.addAll(res.related);
        relatedAdapter.notifyDataSetChanged();
        relatedSection.setVisibility(relatedMovies.isEmpty() ? GONE : VISIBLE);

        btnPlay.post(new Runnable() {
            @Override
            public void run() {
                btnPlay.requestFocus();
            }
        });
    }

    private void addStatDivider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Color.argb(64, 255, 255, 255));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 1), UiHelper.dp(ctx, 12));
        lp.leftMargin = UiHelper.dp(ctx, 10);
        lp.rightMargin = UiHelper.dp(ctx, 10);
        statsRow.addView(v, lp);
    }

    private void addFrostedChip(Context ctx, String label) {
        if (label == null || label.trim().isEmpty()) return;
        TextView tv = new TextView(ctx);
        tv.setText(label.trim());
        tv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        tv.setTextSize(10.5f);
        tv.setPadding(UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 3));
        tv.setBackground(UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(ctx, 6);
        chipsRow.addView(tv, lp);
    }

    private void renderSeasonsAndEpisodes(final Models.MovieDetail m, final String fallbackThumbUrl) {
        seasonTabsRow.removeAllViews();
        displayedEpisodes.clear();
        currentEpFallbackUrl = fallbackThumbUrl != null ? fallbackThumbUrl : "";

        List<Models.EpisodeItem> rawEps = new ArrayList<>();
        if (!m.seasons.isEmpty()) {
            boolean foundSelected = false;
            for (Models.SeasonItem s : m.seasons) {
                if (s.seasonsId == selectedSeasonId) {
                    foundSelected = true;
                    break;
                }
            }
            if (!foundSelected) {
                selectedSeasonId = m.seasons.get(0).seasonsId;
            }
            Context ctx = getContext();
            if (m.seasons.size() > 1) {
                for (int sIdx = 0; sIdx < m.seasons.size(); sIdx++) {
                    final Models.SeasonItem s = m.seasons.get(sIdx);
                    final int tabIndex = sIdx;
                    boolean active = s.seasonsId == selectedSeasonId;
                    TextView tab = new TextView(ctx);
                    tab.setId(View.generateViewId());
                    tab.setText(s.seasonsName);
                    tab.setTextColor(Color.WHITE);
                    tab.setTextSize(11f);
                    tab.setTypeface(null, Typeface.BOLD);
                    tab.setPadding(UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 4));
                    UiHelper.applyCompactChipStyle(tab, ctx, 12, active);
                    tab.setNextFocusUpId(btnPlay.getId());
                    tab.setOnKeyListener(new OnKeyListener() {
                        @Override
                        public boolean onKey(View v, int keyCode, KeyEvent event) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    scrollView.smoothScrollTo(0, 0);
                                    btnPlay.requestFocus();
                                    return true;
                                }
                                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    return focusEpisodeGrid();
                                }
                            }
                            return false;
                        }
                    });
                    tab.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSeasonId = s.seasonsId;
                            renderSeasonsAndEpisodes(m, fallbackThumbUrl);
                            if (tabIndex < seasonTabsRow.getChildCount()) {
                                seasonTabsRow.getChildAt(tabIndex).requestFocus();
                            }
                        }
                    });
                    seasonTabsRow.addView(tab, marginRightLp(ctx, 8));
                }
            }
            for (Models.SeasonItem s : m.seasons) {
                if (s.seasonsId == selectedSeasonId) {
                    rawEps.addAll(s.episodes);
                    break;
                }
            }
        } else {
            rawEps.addAll(m.episodes);
        }

        if (rawEps.isEmpty()) {
            seasonSection.setVisibility(GONE);
            episodeAdapter.notifyDataSetChanged();
            return;
        }

        sortEpisodesAscending(rawEps);
        displayedEpisodes.addAll(rawEps);

        // Determine currently watching episode index
        activeEpisodeIndex = 0;
        boolean matchedHistory = false;
        if (m.watchHistory != null) {
            String lastCode = m.watchHistory.lastStreamCode != null ? m.watchHistory.lastStreamCode : "";
            for (int i = 0; i < displayedEpisodes.size(); i++) {
                Models.EpisodeItem ep = displayedEpisodes.get(i);
                String epCode = ep != null && ep.streamCode != null ? ep.streamCode : "";
                if (ep != null && ((ep.episodesId > 0 && ep.episodesId == m.watchHistory.lastEpisodeId)
                        || (!epCode.isEmpty() && epCode.equals(lastCode)))) {
                    activeEpisodeIndex = i;
                    matchedHistory = true;
                    break;
                }
            }
        }
        if (!matchedHistory) {
            activeEpisodeIndex = 0;
        }

        seasonSection.setVisibility(VISIBLE);
        epTotalCountTv.setText(displayedEpisodes.size() > 5
                ? ("Tổng " + displayedEpisodes.size() + " tập  ·  Trái/Phải để xem thêm")
                : ("Tổng " + displayedEpisodes.size() + " tập"));

        episodeAdapter.notifyDataSetChanged();
        if (activeEpisodeIndex > 0) {
            episodeLayoutManager.scrollToPositionWithOffset(activeEpisodeIndex, UiHelper.dp(getContext(), 12));
        } else {
            episodeLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    private static void sortEpisodesAscending(List<Models.EpisodeItem> list) {
        if (list == null || list.size() <= 1) return;
        Collections.sort(list, new Comparator<Models.EpisodeItem>() {
            @Override
            public int compare(Models.EpisodeItem a, Models.EpisodeItem b) {
                if (a == b) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                int numA = extractEpisodeNumber(a.episodesName);
                int numB = extractEpisodeNumber(b.episodesName);
                if (numA != Integer.MAX_VALUE || numB != Integer.MAX_VALUE) {
                    if (numA != numB) {
                        return numA < numB ? -1 : 1;
                    }
                }
                if (a.order > 0 && b.order > 0 && a.order != b.order) {
                    return a.order < b.order ? -1 : 1;
                }
                if (a.episodesId != b.episodesId) {
                    return a.episodesId < b.episodesId ? -1 : 1;
                }
                return 0;
            }
        });
    }

    private static int extractEpisodeNumber(String name) {
        if (name == null) return Integer.MAX_VALUE;
        int len = name.length();
        int i = 0;
        while (i < len && !Character.isDigit(name.charAt(i))) {
            i++;
        }
        if (i >= len) return Integer.MAX_VALUE;
        int val = 0;
        while (i < len && Character.isDigit(name.charAt(i))) {
            int d = name.charAt(i) - '0';
            if (val > 100000) break;
            val = val * 10 + d;
            i++;
        }
        return val;
    }

    private static final class EpisodeHolder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView thumbIv;
        final ImageView playIconIv;
        final FrameLayout playBadgeWrap;
        final TextView epTv;
        final TextView watchingBadgeTv;
        final LinearLayout eqRow;
        final FrameLayout progressTrack;
        final View progressFill;
        final View focusFrame;

        EpisodeHolder(
                FrameLayout root,
                FrameLayout card,
                ImageView thumbIv,
                ImageView playIconIv,
                FrameLayout playBadgeWrap,
                TextView epTv,
                TextView watchingBadgeTv,
                LinearLayout eqRow,
                FrameLayout progressTrack,
                View progressFill,
                View focusFrame) {
            super(root);
            this.card = card;
            this.thumbIv = thumbIv;
            this.playIconIv = playIconIv;
            this.playBadgeWrap = playBadgeWrap;
            this.epTv = epTv;
            this.watchingBadgeTv = watchingBadgeTv;
            this.eqRow = eqRow;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
            this.focusFrame = focusFrame;
        }
    }

    private class EpisodeAdapter extends RecyclerView.Adapter<EpisodeHolder> {
        @NonNull
        @Override
        public EpisodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context ctx = parent.getContext();
            final int radiusDp = 10;

            FrameLayout wrapper = new FrameLayout(ctx);
            wrapper.setClipChildren(false);
            wrapper.setClipToPadding(false);
            wrapper.setPadding(UiHelper.dp(ctx, 5), UiHelper.dp(ctx, 5),
                    UiHelper.dp(ctx, 5), UiHelper.dp(ctx, 5));
            RecyclerView.LayoutParams wLp = new RecyclerView.LayoutParams(
                    UiHelper.dp(ctx, 176), UiHelper.dp(ctx, 102));
            wrapper.setLayoutParams(wLp);

            final FrameLayout card = new FrameLayout(ctx);
            card.setFocusable(true);
            card.setClickable(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                card.setDefaultFocusHighlightEnabled(false);
            }
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), radiusDp));
                }
            });
            card.setClipToOutline(true);
            card.setElevation(UiHelper.dp(ctx, 6));
            wrapper.addView(card, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            ImageView thumbIv = new ImageView(ctx);
            thumbIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(thumbIv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            View scrim = new View(ctx);
            scrim.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, Color.argb(220, 9, 11, 18)}));
            card.addView(scrim, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 44), Gravity.BOTTOM));

            // Top-Left "Đang xem" pill badge for active episode
            TextView watchingBadgeTv = new TextView(ctx);
            watchingBadgeTv.setText("Đang xem");
            watchingBadgeTv.setTextColor(Color.WHITE);
            watchingBadgeTv.setTextSize(9f);
            watchingBadgeTv.setTypeface(null, Typeface.BOLD);
            watchingBadgeTv.setPadding(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 2),
                    UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 2));
            watchingBadgeTv.setBackground(UiHelper.createGlassRect(
                    ctx, 6, Color.argb(215, 229, 9, 20), Color.TRANSPARENT, 0));
            watchingBadgeTv.setVisibility(GONE);
            FrameLayout.LayoutParams wbLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            wbLp.setMargins(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 6), 0, 0);
            card.addView(watchingBadgeTv, wbLp);

            // Bottom-Left: Play indicator + Episode Title
            LinearLayout leftLabelRow = new LinearLayout(ctx);
            leftLabelRow.setOrientation(LinearLayout.HORIZONTAL);
            leftLabelRow.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout playBadgeWrap = new FrameLayout(ctx);
            ImageView playIconIv = new ImageView(ctx);
            playIconIv.setImageResource(R.drawable.ic_play);
            playIconIv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            playBadgeWrap.addView(playIconIv, new FrameLayout.LayoutParams(
                    UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 9), Gravity.CENTER));
            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 16));
            pbLp.rightMargin = UiHelper.dp(ctx, 5);
            leftLabelRow.addView(playBadgeWrap, pbLp);

            TextView epTv = new TextView(ctx);
            epTv.setTextColor(Color.WHITE);
            epTv.setTextSize(11.5f);
            epTv.setTypeface(null, Typeface.BOLD);
            epTv.setMaxLines(1);
            epTv.setEllipsize(TextUtils.TruncateAt.END);
            leftLabelRow.addView(epTv);

            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            llp.setMargins(UiHelper.dp(ctx, 7), 0, UiHelper.dp(ctx, 26), UiHelper.dp(ctx, 7));
            card.addView(leftLabelRow, llp);

            // Equalizer mini bars on active episode (bottom-right)
            LinearLayout eqRow = new LinearLayout(ctx);
            eqRow.setOrientation(LinearLayout.HORIZONTAL);
            eqRow.setGravity(Gravity.BOTTOM);
            int[] heights = {7, 12, 9};
            for (int i = 0; i < heights.length; i++) {
                View bar = new View(ctx);
                bar.setBackground(UiHelper.createGlassRect(ctx, 1, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        UiHelper.dp(ctx, 2.2f), UiHelper.dp(ctx, heights[i]));
                if (i > 0) blp.leftMargin = UiHelper.dp(ctx, 2);
                eqRow.addView(bar, blp);
            }
            eqRow.setVisibility(GONE);
            FrameLayout.LayoutParams eqLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            eqLp.setMargins(0, 0, UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8));
            card.addView(eqRow, eqLp);

            // Bottom progress bar for active episode
            FrameLayout progressTrack = new FrameLayout(ctx);
            progressTrack.setBackgroundColor(Color.argb(95, 255, 255, 255));
            progressTrack.setVisibility(GONE);
            View progressFill = new View(ctx);
            progressFill.setBackgroundColor(UiHelper.COLOR_ACCENT_RED);
            progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
            card.addView(progressTrack, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 3), Gravity.BOTTOM));

            // Crisp White Focus Border ONLY (zero red/pink border or glow)
            View focusFrame = new View(ctx);
            focusFrame.setBackground(UiHelper.createWhiteFocusBorderDrawable(ctx, radiusDp, UiHelper.FOCUS_BORDER_DP));
            focusFrame.setAlpha(0f);
            card.addView(focusFrame, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            return new EpisodeHolder(wrapper, card, thumbIv, playIconIv, playBadgeWrap,
                    epTv, watchingBadgeTv, eqRow, progressTrack, progressFill, focusFrame);
        }

        @Override
        public void onBindViewHolder(@NonNull final EpisodeHolder h, int position) {
            final Context ctx = h.card.getContext();
            final int pos = position;
            final Models.EpisodeItem ep = displayedEpisodes.get(pos);
            final int epNum = pos + 1;
            final boolean isActiveEp = (pos == activeEpisodeIndex);

            loadEpisodeThumbWithFallback(ctx, h.thumbIv, ep.streamCode, currentEpFallbackUrl);

            String name = (ep.episodesName != null && !ep.episodesName.isEmpty())
                    ? ep.episodesName : ("Tập " + epNum);
            h.epTv.setText(name);

            if (isActiveEp) {
                h.playBadgeWrap.setBackground(UiHelper.createGlassRect(
                        ctx, 8, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
                h.watchingBadgeTv.setVisibility(VISIBLE);
                h.eqRow.setVisibility(VISIBLE);
                int pct = (currentDetail != null && currentDetail.watchHistory != null)
                        ? currentDetail.watchHistory.progressPercent : 0;
                if (pct > 0) {
                    h.progressTrack.setVisibility(VISIBLE);
                    final int clamped = Math.max(4, Math.min(100, pct));
                    h.progressTrack.post(new Runnable() {
                        @Override
                        public void run() {
                            int w = h.progressTrack.getWidth();
                            if (w > 0) {
                                ViewGroup.LayoutParams lp = h.progressFill.getLayoutParams();
                                lp.width = Math.round(w * (clamped / 100f));
                                h.progressFill.setLayoutParams(lp);
                            }
                        }
                    });
                } else {
                    h.progressTrack.setVisibility(GONE);
                }
            } else {
                h.playBadgeWrap.setBackground(null);
                h.watchingBadgeTv.setVisibility(GONE);
                h.eqRow.setVisibility(GONE);
                h.progressTrack.setVisibility(GONE);
            }

            boolean focused = h.card.hasFocus();
            h.focusFrame.setAlpha(focused ? 1f : 0f);
            h.card.setScaleX(focused ? 1.055f : 1f);
            h.card.setScaleY(focused ? 1.055f : 1f);

            h.card.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    UiHelper.applyTvFocusEffect(v, ctx, hasFocus, 1.055f, 6);
                    h.focusFrame.animate().alpha(hasFocus ? 1f : 0f).setDuration(120).start();
                }
            });

            h.card.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchWithStream(ep.streamCode, ep.episodesId, epNum,
                            ep.seasonsId > 0 ? String.valueOf(ep.seasonsId) : "", ep.episodesName);
                }
            });

            h.card.setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (seasonTabsRow.getChildCount() > 0) {
                            seasonTabsRow.getChildAt(0).requestFocus();
                        } else {
                            scrollView.smoothScrollTo(0, 0);
                            btnPlay.requestFocus();
                        }
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return focusFirstBelowEpisodes();
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return displayedEpisodes.size();
        }
    }

    private void loadPosterWithFallback(Context ctx, ImageView target, String portraitUrl, String landscapeFallbackUrl) {
        if (target == null || ctx == null) return;
        if (ctx instanceof Activity) {
            Activity act = (Activity) ctx;
            if (act.isFinishing() || act.isDestroyed()) return;
        }
        String primary = portraitUrl != null ? portraitUrl.trim() : "";
        String fallback = landscapeFallbackUrl != null ? landscapeFallbackUrl.trim() : "";
        if (primary.isEmpty()) {
            UiHelper.safeLoadImage(target, fallback, R.drawable.default_poster, 0);
            return;
        }
        if (fallback.isEmpty() || fallback.equals(primary)) {
            UiHelper.safeLoadImage(target, primary, R.drawable.default_poster, 0);
            return;
        }
        Glide.with(ctx)
                .load(primary)
                .apply(new RequestOptions().centerCrop().placeholder(R.drawable.default_poster))
                .error(Glide.with(ctx)
                        .load(fallback)
                        .apply(new RequestOptions().centerCrop().error(R.drawable.default_poster)))
                .transition(DrawableTransitionOptions.withCrossFade(220))
                .into(target);
    }

    private void loadEpisodeThumbWithFallback(Context ctx, ImageView target, String streamCode, String fallbackUrl) {
        if (ctx instanceof Activity) {
            Activity act = (Activity) ctx;
            if (act.isFinishing() || act.isDestroyed()) return;
        }
        String codeThumb = "";
        if (streamCode != null && !streamCode.trim().isEmpty()) {
            try {
                codeThumb = "https://image.s1dangvietanh.io.vn/image.php?code="
                        + URLEncoder.encode(streamCode.trim(), "UTF-8").replace("+", "%20");
            } catch (Exception ignored) {
            }
        }
        String fb = (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) ? fallbackUrl.trim() : "";
        if (codeThumb.isEmpty()) {
            UiHelper.safeLoadImage(target, fb, R.drawable.default_poster, 0);
            return;
        }
        if (fb.isEmpty()) {
            UiHelper.safeLoadImage(target, codeThumb, R.drawable.default_poster, 0);
            return;
        }
        Glide.with(ctx)
                .load(codeThumb)
                .apply(new RequestOptions().centerCrop().placeholder(R.drawable.default_poster))
                .error(Glide.with(ctx)
                        .load(fb)
                        .apply(new RequestOptions().centerCrop().error(R.drawable.default_poster)))
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .into(target);
    }

    private void bindActionListeners() {
        btnPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDetail == null) return;
                String targetCode = currentDetail.streamCode;
                int epId = 0;
                int epNum = 1;
                String epTitle = "";
                if (currentDetail.watchHistory != null && !currentDetail.watchHistory.lastStreamCode.isEmpty()) {
                    targetCode = currentDetail.watchHistory.lastStreamCode;
                    epId = currentDetail.watchHistory.lastEpisodeId;
                }
                List<Models.EpisodeItem> flat = collectAllEpisodes(currentDetail);
                for (int i = 0; i < flat.size(); i++) {
                    Models.EpisodeItem e = flat.get(i);
                    if ((!targetCode.isEmpty() && targetCode.equals(e.streamCode)) || (epId > 0 && epId == e.episodesId)) {
                        targetCode = e.streamCode;
                        epId = e.episodesId;
                        epNum = i + 1;
                        epTitle = e.episodesName;
                        break;
                    }
                }
                if (targetCode.isEmpty() && !flat.isEmpty()) {
                    Models.EpisodeItem e0 = flat.get(0);
                    targetCode = e0.streamCode;
                    epId = e0.episodesId;
                    epNum = 1;
                    epTitle = e0.episodesName;
                }
                if (targetCode.isEmpty() && !currentDetail.sources.isEmpty()) {
                    targetCode = currentDetail.sources.get(0).streamCode;
                }
                if (targetCode.isEmpty()) targetCode = String.valueOf(currentDetail.videosId);
                launchWithStream(targetCode, epId, epNum,
                        selectedSeasonId > 0 ? String.valueOf(selectedSeasonId) : "", epTitle);
            }
        });

        btnWishlist.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDetail == null) return;
                apiClient.toggleWishlist(currentDetail.videosId, new ApiClient.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean added) {
                        if (currentDetail == null) return;
                        currentDetail.inWishlist = added;
                        updateWishlistVisual();
                        if (listener != null) {
                            listener.onShowToast(
                                    added ? "Đã thêm vào Danh sách của tôi" : "Đã bỏ khỏi Danh sách của tôi",
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

        btnLike.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDetail == null) return;
                String key = String.valueOf(currentDetail.videosId);
                String cur = Prefs.getReaction(getContext(), key);
                String next = "like".equals(cur) ? "" : "like";
                Prefs.setReaction(getContext(), key, next);
                updateReactionVisuals();
                if (listener != null) {
                    listener.onShowToast(
                            "like".equals(next) ? "Bạn đã thích bộ phim này" : "Đã bỏ lượt thích",
                            UiHelper.COLOR_GREEN);
                }
            }
        });

        btnDislike.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDetail == null) return;
                String key = String.valueOf(currentDetail.videosId);
                String cur = Prefs.getReaction(getContext(), key);
                String next = "dislike".equals(cur) ? "" : "dislike";
                Prefs.setReaction(getContext(), key, next);
                updateReactionVisuals();
                if (listener != null) {
                    listener.onShowToast(
                            "dislike".equals(next) ? "Đã ghi nhận: Không thích phim này" : "Đã huỷ phản hồi",
                            UiHelper.COLOR_TEXT_MUTED);
                }
            }
        });

        btnBack.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleBack() && listener != null) {
                    listener.onCloseDetail();
                }
            }
        });
    }

    private void updateWishlistVisual() {
        if (currentDetail == null) return;
        btnWishlistIcon.setImageResource(currentDetail.inWishlist ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        btnWishlistText.setText(currentDetail.inWishlist ? "Đã lưu" : "Thêm vào danh sách");
        UiHelper.setButtonSelectedVisual(btnWishlist, getContext(), 18, currentDetail.inWishlist);
    }

    private void updateReactionVisuals() {
        if (currentDetail == null) return;
        String react = Prefs.getReaction(getContext(), String.valueOf(currentDetail.videosId));
        UiHelper.setButtonSelectedVisual(btnLike, getContext(), 18, "like".equals(react));
        UiHelper.setButtonSelectedVisual(btnDislike, getContext(), 18, "dislike".equals(react));
    }

    private void launchWithStream(String streamCode, int episodeId, int episodeNumber, String seasonId, String episodeTitle) {
        if (currentDetail == null || listener == null) return;
        Models.PlayerLaunchRequest req = new Models.PlayerLaunchRequest();
        req.videosId = currentDetail.videosId;
        req.streamCode = (streamCode != null && !streamCode.isEmpty()) ? streamCode : currentDetail.streamCode;
        req.episodeId = episodeId;
        req.episodeNumber = episodeNumber;
        req.seasonId = seasonId != null ? seasonId : "";
        req.title = currentDetail.title;
        req.episodeTitle = episodeTitle != null ? episodeTitle : "";
        req.posterUrl = currentDetail.posterUrl;
        req.thumbnailUrl = currentDetail.thumbnailUrl;
        if (currentDetail.watchHistory != null && currentDetail.watchHistory.resumeSeconds > 5
                && req.streamCode.equals(currentDetail.watchHistory.lastStreamCode)) {
            req.initialResumeSec = currentDetail.watchHistory.resumeSeconds;
        }
        req.episodes.addAll(collectAllEpisodes(currentDetail));
        req.seasons.addAll(currentDetail.seasons);
        listener.onLaunchPlayer(req);
    }

    private static List<Models.EpisodeItem> collectAllEpisodes(Models.MovieDetail m) {
        List<Models.EpisodeItem> out = new ArrayList<>();
        if (m == null) return out;
        if (!m.episodes.isEmpty()) {
            out.addAll(m.episodes);
        } else {
            for (Models.SeasonItem s : m.seasons) {
                out.addAll(s.episodes);
            }
        }
        sortEpisodesAscending(out);
        return out;
    }

    private LinearLayout makeCapsuleCta(Context ctx, int iconRes, String label, boolean accent) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(UiHelper.dp(ctx, 15), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 8));
        UiHelper.applyFocusableGlassButton(row, ctx, UiHelper.GLASS_RADIUS_DP, accent);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 15), UiHelper.dp(ctx, 15));
        iLp.rightMargin = UiHelper.dp(ctx, 7);
        row.addView(iv, iLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiHelper.dp(ctx, 35)));
        return row;
    }

    private LinearLayout makeSectionHeader(Context ctx, String text) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(ctx, 8);
        row.setLayoutParams(lp);

        View bar = new View(ctx);
        bar.setBackground(UiHelper.createGlassRect(ctx, 2, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 15));
        bLp.rightMargin = UiHelper.dp(ctx, 8);
        row.addView(bar, bLp);

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14.5f);
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);
        return row;
    }

    private LinearLayout.LayoutParams marginRightLp(Context ctx, int dpVal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(ctx, dpVal);
        return lp;
    }

    private class RelatedAdapter extends RecyclerView.Adapter<MovieCardView.Holder> {
        @NonNull
        @Override
        public MovieCardView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return MovieCardView.createHolder(parent.getContext(), MovieCardView.TYPE_VERTICAL, false,
                    new MovieCardView.OnMovieClickListener() {
                        @Override
                        public void onMovieClick(Models.MovieItem item) {
                            openMovie(item.videosId, true);
                        }
                    }, null);
        }

        @Override
        public void onBindViewHolder(@NonNull MovieCardView.Holder holder, int position) {
            MovieCardView.bind(holder, relatedMovies.get(position), MovieCardView.TYPE_VERTICAL, position);
            holder.itemView.setNextFocusLeftId(View.NO_ID);
        }

        @Override
        public int getItemCount() {
            return relatedMovies.size();
        }
    }
}
