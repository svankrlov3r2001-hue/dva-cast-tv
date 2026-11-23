package com.dangvietanh.cast.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
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
import java.util.List;

/**
 * Full-bleed cinematic Movie Detail Screen matching the target Android TV design:
 * - Slim 56dp left icon rail (always clean, quick navigation)
 * - Right-weighted full-bleed backdrop + luminous-rimmed portrait poster
 * - Primary title + original subtitle, IMDb / Runtime / Year / Quality pill stats row
 * - Frosted genre & country tags, synopsis, capsule icon-text CTA buttons
 * - 5-column 16:9 thumbnail episode grid with active crimson-pink highlight + equalizer
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
    private final LinearLayout leftIconRail;
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
    private final LinearLayout episodeGridWrap;
    private final LinearLayout sourcesSection;
    private final LinearLayout sourcesRow;
    private final LinearLayout castSection;
    private final LinearLayout castRow;
    private final LinearLayout relatedSection;
    private final RecyclerView relatedRv;

    private Models.MovieDetail currentDetail;
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

        // 2. Main Content ScrollView (to the right of 56dp left icon rail)
        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipChildren(false);
        FrameLayout.LayoutParams svLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        svLp.leftMargin = UiHelper.dp(context, 56);
        addView(scrollView, svLp);

        contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 22),
                UiHelper.dp(context, 24), UiHelper.dp(context, 28));
        contentCol.setClipChildren(false);
        scrollView.addView(contentCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 3. Slim Left Icon-Only Rail (56dp)
        leftIconRail = buildLeftIconRail(context);
        addView(leftIconRail, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 56), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START));

        // 4. Open Split Hero Header Row (Poster + Info Stack)
        LinearLayout heroRow = new LinearLayout(context);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.TOP);
        heroRow.setClipChildren(false);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrLp.bottomMargin = UiHelper.dp(context, 20);

        // Left Portrait Poster with luminous outline glow
        final FrameLayout posterWrap = new FrameLayout(context);
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
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        actionsRow.setClipChildren(false);

        btnPlay = makeCapsuleCta(context, R.drawable.ic_play, "Xem ngay", true);
        btnPlayText = (TextView) btnPlay.getChildAt(1);

        btnWishlist = makeCapsuleCta(context, R.drawable.ic_bookmark_outline, "Thêm vào danh sách", false);
        btnWishlistIcon = (ImageView) btnWishlist.getChildAt(0);
        btnWishlistText = (TextView) btnWishlist.getChildAt(1);

        btnLike = makeCapsuleCta(context, R.drawable.ic_thumb_up, "Thích", false);
        btnDislike = makeCapsuleCta(context, R.drawable.ic_thumb_down, "Không thích", false);
        btnBack = makeCapsuleCta(context, R.drawable.ic_back, "Quay lại", false);

        actionsRow.addView(btnPlay, marginRightLp(context, 9));
        actionsRow.addView(btnWishlist, marginRightLp(context, 9));
        actionsRow.addView(btnLike, marginRightLp(context, 9));
        actionsRow.addView(btnDislike, marginRightLp(context, 9));
        actionsRow.addView(btnBack, marginRightLp(context, 0));
        infoCol.addView(actionsRow);

        contentCol.addView(heroRow, hrLp);

        // 5. Episodes Section (5-Column Thumbnail Grid)
        seasonSection = new LinearLayout(context);
        seasonSection.setOrientation(LinearLayout.VERTICAL);
        seasonSection.setClipChildren(false);
        seasonSection.setVisibility(GONE);

        LinearLayout epHdrRow = new LinearLayout(context);
        epHdrRow.setOrientation(LinearLayout.HORIZONTAL);
        epHdrRow.setGravity(Gravity.CENTER_VERTICAL);
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
        seasonTabsRow = new LinearLayout(context);
        seasonTabsRow.setOrientation(LinearLayout.HORIZONTAL);
        seasonScroll.addView(seasonTabsRow);
        epHdrRow.addView(seasonScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        epTotalCountTv = new TextView(context);
        epTotalCountTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        epTotalCountTv.setTextSize(11.5f);
        epTotalCountTv.setTypeface(null, Typeface.BOLD);
        epHdrRow.addView(epTotalCountTv);
        seasonSection.addView(epHdrRow, ehrLp);

        episodeGridWrap = new LinearLayout(context);
        episodeGridWrap.setOrientation(LinearLayout.VERTICAL);
        episodeGridWrap.setClipChildren(false);
        seasonSection.addView(episodeGridWrap);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        secLp.bottomMargin = UiHelper.dp(context, 18);
        contentCol.addView(seasonSection, secLp);

        // 6. Sources Section (Movies)
        sourcesSection = new LinearLayout(context);
        sourcesSection.setOrientation(LinearLayout.VERTICAL);
        sourcesSection.setVisibility(GONE);
        sourcesSection.addView(makeSectionHeader(context, "Nguồn phát khả dụng"));
        HorizontalScrollView srcScroll = new HorizontalScrollView(context);
        srcScroll.setHorizontalScrollBarEnabled(false);
        sourcesRow = new LinearLayout(context);
        sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
        srcScroll.addView(sourcesRow);
        sourcesSection.addView(srcScroll);
        contentCol.addView(sourcesSection, secLp);

        // 7. Cast & Crew Section
        castSection = new LinearLayout(context);
        castSection.setOrientation(LinearLayout.VERTICAL);
        castSection.setVisibility(GONE);
        castSection.addView(makeSectionHeader(context, "Đạo diễn & Diễn viên"));
        HorizontalScrollView castScroll = new HorizontalScrollView(context);
        castScroll.setHorizontalScrollBarEnabled(false);
        castRow = new LinearLayout(context);
        castRow.setOrientation(LinearLayout.HORIZONTAL);
        castScroll.addView(castRow);
        castSection.addView(castScroll);
        contentCol.addView(castSection, secLp);

        // 8. Related Movies Section
        relatedSection = new LinearLayout(context);
        relatedSection.setOrientation(LinearLayout.VERTICAL);
        relatedSection.setClipChildren(false);
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

    private LinearLayout buildLeftIconRail(Context ctx) {
        LinearLayout rail = new LinearLayout(ctx);
        rail.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.setBackgroundColor(Color.argb(228, 12, 14, 22));
        inner.setPadding(0, UiHelper.dp(ctx, 18), 0, UiHelper.dp(ctx, 18));

        addRailIcon(ctx, inner, R.drawable.ic_home, true, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onNavigateTab("home");
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_search, false, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onNavigateTab("search");
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_movie, false, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onNavigateTab("movies");
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_tv, false, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onNavigateTab("series");
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_explore, false, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onNavigateTab("explore");
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_bookmark, false, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onNavigateTab("library");
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_settings, false, new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onOpenSettings();
            }
        });
        addRailIcon(ctx, inner, R.drawable.ic_back, false, new Runnable() {
            @Override
            public void run() {
                if (!handleBack() && listener != null) listener.onCloseDetail();
            }
        });

        rail.addView(inner, new LinearLayout.LayoutParams(
                UiHelper.dp(ctx, 55), LinearLayout.LayoutParams.MATCH_PARENT));

        View rightHairline = new View(ctx);
        rightHairline.setBackgroundColor(Color.argb(18, 255, 255, 255));
        rail.addView(rightHairline, new LinearLayout.LayoutParams(
                UiHelper.dp(ctx, 1), LinearLayout.LayoutParams.MATCH_PARENT));
        return rail;
    }

    private void addRailIcon(Context ctx, LinearLayout parent, int iconRes, boolean accentActive, final Runnable action) {
        FrameLayout btn = new FrameLayout(ctx);
        if (accentActive) {
            UiHelper.applyFocusableGlassButton(btn, ctx, 18, true);
        } else {
            UiHelper.applyCircularIconButtonStyle(btn, ctx, 36);
            btn.setBackground(UiHelper.createSidebarItemStateDrawable(ctx, 18, false));
        }
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(accentActive ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED, PorterDuff.Mode.SRC_IN);
        btn.addView(iv, new FrameLayout.LayoutParams(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 18), Gravity.CENTER));
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        btn.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnPlay.requestFocus();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 36), UiHelper.dp(ctx, 36));
        lp.topMargin = UiHelper.dp(ctx, 6);
        lp.bottomMargin = UiHelper.dp(ctx, 6);
        parent.addView(btn, lp);
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

        String bgUrl = !m.thumbnailUrl.isEmpty() ? m.thumbnailUrl : m.posterUrl;
        String posterUrl = !m.posterUrl.isEmpty() ? m.posterUrl : bgUrl;
        UiHelper.safeLoadBackdrop(backdropIv, bgUrl);
        UiHelper.safeLoadImage(posterIv, posterUrl, R.drawable.default_poster, 0);

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

        // Seasons & 5-Column Thumbnail Episode Grid
        renderSeasonsAndEpisodes(m, posterUrl);

        // Sources
        sourcesRow.removeAllViews();
        if (!m.sources.isEmpty() && m.isTvSeries == 0) {
            sourcesSection.setVisibility(VISIBLE);
            for (final Models.SourceItem src : m.sources) {
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
            for (Models.StarItem st : people) {
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
                ctx, 10, Color.argb(135, 34, 38, 54), Color.argb(32, 255, 255, 255), 0.8f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(ctx, 6);
        chipsRow.addView(tv, lp);
    }

    private void renderSeasonsAndEpisodes(final Models.MovieDetail m, final String fallbackThumbUrl) {
        seasonTabsRow.removeAllViews();
        episodeGridWrap.removeAllViews();

        List<Models.EpisodeItem> displayEps = new ArrayList<>();
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
                for (final Models.SeasonItem s : m.seasons) {
                    boolean active = s.seasonsId == selectedSeasonId;
                    TextView tab = new TextView(ctx);
                    tab.setText(s.seasonsName);
                    tab.setTextColor(Color.WHITE);
                    tab.setTextSize(11f);
                    tab.setTypeface(null, Typeface.BOLD);
                    tab.setPadding(UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 4));
                    UiHelper.applyCompactChipStyle(tab, ctx, 12, active);
                    tab.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSeasonId = s.seasonsId;
                            renderSeasonsAndEpisodes(m, fallbackThumbUrl);
                        }
                    });
                    seasonTabsRow.addView(tab, marginRightLp(ctx, 8));
                }
            }
            for (Models.SeasonItem s : m.seasons) {
                if (s.seasonsId == selectedSeasonId) {
                    displayEps.addAll(s.episodes);
                    break;
                }
            }
        } else {
            displayEps.addAll(m.episodes);
        }

        if (displayEps.isEmpty()) {
            seasonSection.setVisibility(GONE);
            return;
        }
        seasonSection.setVisibility(VISIBLE);
        epTotalCountTv.setText("Tổng " + displayEps.size() + " tập  ›");

        Context ctx = getContext();
        int cols = 5;
        LinearLayout currentRow = null;
        for (int i = 0; i < displayEps.size(); i++) {
            if (i % cols == 0) {
                currentRow = new LinearLayout(ctx);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setClipChildren(false);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rLp.bottomMargin = UiHelper.dp(ctx, 10);
                episodeGridWrap.addView(currentRow, rLp);
            }
            final Models.EpisodeItem ep = displayEps.get(i);
            final int epNum = i + 1;
            boolean isActiveEp = m.watchHistory != null
                    && ((ep.episodesId > 0 && ep.episodesId == m.watchHistory.lastEpisodeId)
                    || (!ep.streamCode.isEmpty() && ep.streamCode.equals(m.watchHistory.lastStreamCode)));
            if (!isActiveEp && i == 0 && (m.watchHistory == null || m.watchHistory.lastStreamCode.isEmpty())) {
                isActiveEp = true;
            }

            FrameLayout epCard = buildThumbnailEpisodeCard(ctx, ep, epNum, isActiveEp, fallbackThumbUrl);
            epCard.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchWithStream(ep.streamCode, ep.episodesId, epNum,
                            ep.seasonsId > 0 ? String.valueOf(ep.seasonsId) : "", ep.episodesName);
                }
            });

            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, UiHelper.dp(ctx, 82), 1f);
            if ((i % cols) < cols - 1) bLp.rightMargin = UiHelper.dp(ctx, 10);
            currentRow.addView(epCard, bLp);
        }

        // Pad incomplete final row so 16:9 width matches cleanly across all rows
        int rem = displayEps.size() % cols;
        if (rem != 0 && currentRow != null) {
            for (int k = rem; k < cols; k++) {
                View spacer = new View(ctx);
                LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, UiHelper.dp(ctx, 82), 1f);
                if (k < cols - 1) spLp.rightMargin = UiHelper.dp(ctx, 10);
                currentRow.addView(spacer, spLp);
            }
        }
    }

    private FrameLayout buildThumbnailEpisodeCard(
            final Context ctx, Models.EpisodeItem ep, int epNum, final boolean isActiveEp, String fallbackThumbUrl) {
        final int radiusDp = 10;
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
        card.setElevation(UiHelper.dp(ctx, isActiveEp ? 12 : 6));

        ImageView thumbIv = new ImageView(ctx);
        thumbIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(thumbIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        loadEpisodeThumbWithFallback(ctx, thumbIv, ep.streamCode, fallbackThumbUrl);

        // Dark bottom scrim inside thumbnail card
        View scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(215, 9, 11, 18)}));
        card.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 44), Gravity.BOTTOM));

        // Bottom-Left: Play indicator + Episode Title
        LinearLayout leftLabelRow = new LinearLayout(ctx);
        leftLabelRow.setOrientation(LinearLayout.HORIZONTAL);
        leftLabelRow.setGravity(Gravity.CENTER_VERTICAL);

        if (isActiveEp) {
            FrameLayout pinkPlayDot = new FrameLayout(ctx);
            GradientDrawable pg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#FF2E63"), Color.parseColor("#D50055")});
            pg.setCornerRadius(UiHelper.dp(ctx, 8));
            pinkPlayDot.setBackground(pg);
            ImageView piv = new ImageView(ctx);
            piv.setImageResource(R.drawable.ic_play);
            piv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            pinkPlayDot.addView(piv, new FrameLayout.LayoutParams(
                    UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8), Gravity.CENTER));
            LinearLayout.LayoutParams pdLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 16));
            pdLp.rightMargin = UiHelper.dp(ctx, 6);
            leftLabelRow.addView(pinkPlayDot, pdLp);
        } else {
            ImageView piv = new ImageView(ctx);
            piv.setImageResource(R.drawable.ic_play);
            piv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 11));
            pLp.rightMargin = UiHelper.dp(ctx, 5);
            leftLabelRow.addView(piv, pLp);
        }

        TextView epTv = new TextView(ctx);
        String name = (ep.episodesName != null && !ep.episodesName.isEmpty()) ? ep.episodesName : ("Tập " + epNum);
        epTv.setText(name);
        epTv.setTextColor(Color.WHITE);
        epTv.setTextSize(11.5f);
        epTv.setTypeface(null, Typeface.BOLD);
        epTv.setMaxLines(1);
        epTv.setEllipsize(TextUtils.TruncateAt.END);
        leftLabelRow.addView(epTv);

        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        llp.setMargins(UiHelper.dp(ctx, 8), 0, UiHelper.dp(ctx, 28), UiHelper.dp(ctx, 7));
        card.addView(leftLabelRow, llp);

        // Equalizer mini bars on active episode (bottom-right)
        if (isActiveEp) {
            LinearLayout eqRow = new LinearLayout(ctx);
            eqRow.setOrientation(LinearLayout.HORIZONTAL);
            eqRow.setGravity(Gravity.BOTTOM);
            int[] heights = {7, 12, 9};
            for (int i = 0; i < heights.length; i++) {
                View bar = new View(ctx);
                bar.setBackground(UiHelper.createGlassRect(ctx, 1, Color.parseColor("#FF3B7F"), Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        UiHelper.dp(ctx, 2.2f), UiHelper.dp(ctx, heights[i]));
                if (i > 0) blp.leftMargin = UiHelper.dp(ctx, 2);
                eqRow.addView(bar, blp);
            }
            FrameLayout.LayoutParams eqLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            eqLp.setMargins(0, 0, UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8));
            card.addView(eqRow, eqLp);
        }

        // Luminous Crimson-Pink Focus Frame Overlay
        final View focusFrame = new View(ctx);
        focusFrame.setBackground(UiHelper.createFocusRingDrawable(
                ctx, radiusDp, Color.parseColor("#FF4078"), 2.2f));
        focusFrame.setAlpha(isActiveEp ? 0.60f : 0f);
        card.addView(focusFrame, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final float baseElev = UiHelper.dp(ctx, isActiveEp ? 12 : 6);
        final float focusElev = UiHelper.dp(ctx, 18);
        card.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = hasFocus ? 1.045f : 1.0f;
                v.animate().scaleX(sc).scaleY(sc).setDuration(130).setInterpolator(new DecelerateInterpolator()).start();
                card.setElevation(hasFocus ? focusElev : baseElev);
                focusFrame.animate().alpha(hasFocus ? 1f : (isActiveEp ? 0.60f : 0f)).setDuration(120).start();
            }
        });

        return card;
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
        return out;
    }

    private LinearLayout makeCapsuleCta(Context ctx, int iconRes, String label, boolean accent) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(UiHelper.dp(ctx, 15), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 8));
        UiHelper.applyFocusableGlassButton(row, ctx, 18, accent);

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
        }

        @Override
        public int getItemCount() {
            return relatedMovies.size();
        }
    }
}
