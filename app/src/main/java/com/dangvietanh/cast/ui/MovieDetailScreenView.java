package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dangvietanh.cast.Prefs;
import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;

import java.util.ArrayList;
import java.util.List;

public class MovieDetailScreenView extends FrameLayout {

    public interface Listener {
        void onCloseDetail();
        void onLaunchPlayer(Models.PlayerLaunchRequest request);
        void onShowToast(String message, int accentColor);
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
    private final TextView badgeQualityTv;
    private final TextView titleTv;
    private final TextView metaTv;
    private final TextView progressTv;
    private final TextView descTv;
    private final TextView btnPlay;
    private final TextView btnWishlist;
    private final TextView btnLike;
    private final TextView btnDislike;
    private final TextView btnBack;

    private final LinearLayout seasonSection;
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

    public MovieDetailScreenView(Context context, ApiClient apiClient, Listener listener) {
        super(context);
        this.apiClient = apiClient;
        this.listener = listener;

        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setBackgroundColor(UiHelper.COLOR_BG_BASE);
        setClickable(true);

        backdropIv = new ImageView(context);
        backdropIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropIv.setAlpha(0.38f);
        addView(backdropIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(175, 7, 9, 15), Color.argb(232, 7, 9, 15), UiHelper.COLOR_BG_BASE}));
        addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipChildren(false);
        addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setPadding(UiHelper.dp(context, 48), UiHelper.dp(context, 28),
                UiHelper.dp(context, 48), UiHelper.dp(context, 48));
        contentCol.setClipChildren(false);
        scrollView.addView(contentCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // Top Bar
        LinearLayout topNavRow = new LinearLayout(context);
        topNavRow.setOrientation(LinearLayout.HORIZONTAL);
        topNavRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tnLp.bottomMargin = UiHelper.dp(context, 20);

        btnBack = makeGlassActionBtn(context, "←  Quay lại", false);
        btnBack.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleBack()) {
                    if (MovieDetailScreenView.this.listener != null) MovieDetailScreenView.this.listener.onCloseDetail();
                }
            }
        });
        topNavRow.addView(btnBack);
        contentCol.addView(topNavRow, tnLp);

        // Hero Header Card (Sleek Frosted Card)
        LinearLayout heroGlass = new LinearLayout(context);
        heroGlass.setOrientation(LinearLayout.HORIZONTAL);
        heroGlass.setPadding(UiHelper.dp(context, 20), UiHelper.dp(context, 18),
                UiHelper.dp(context, 20), UiHelper.dp(context, 18));
        UiHelper.applyGlassCardStyle(heroGlass, context, 18, UiHelper.COLOR_SURFACE_GLASS, 16);
        LinearLayout.LayoutParams hgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hgLp.bottomMargin = UiHelper.dp(context, 20);

        // Left Poster
        FrameLayout posterWrap = new FrameLayout(context);
        LinearLayout.LayoutParams pwLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 154), UiHelper.dp(ctxOrThis(context), 226));
        pwLp.rightMargin = UiHelper.dp(context, 22);
        posterIv = new ImageView(context);
        posterIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterWrap.addView(posterIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        badgeQualityTv = new TextView(context);
        badgeQualityTv.setTextColor(Color.WHITE);
        badgeQualityTv.setTextSize(10f);
        badgeQualityTv.setTypeface(null, Typeface.BOLD);
        badgeQualityTv.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 3), UiHelper.dp(context, 8), UiHelper.dp(context, 3));
        badgeQualityTv.setBackground(UiHelper.createGlassRect(context, 6, Color.argb(190, 229, 9, 20), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams bqLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bqLp.setMargins(UiHelper.dp(context, 8), UiHelper.dp(context, 8), 0, 0);
        posterWrap.addView(badgeQualityTv, bqLp);
        heroGlass.addView(posterWrap, pwLp);

        // Right Info Column
        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setClipChildren(false);
        heroGlass.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        titleTv = new TextView(context);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(23f);
        titleTv.setTypeface(null, Typeface.BOLD);
        infoCol.addView(titleTv);

        metaTv = new TextView(context);
        metaTv.setTextColor(UiHelper.COLOR_GOLD);
        metaTv.setTextSize(12.5f);
        metaTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLp.topMargin = UiHelper.dp(context, 6);
        infoCol.addView(metaTv, mLp);

        progressTv = new TextView(context);
        progressTv.setTextColor(UiHelper.COLOR_GREEN);
        progressTv.setTextSize(12f);
        progressTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pLp.topMargin = UiHelper.dp(context, 4);
        infoCol.addView(progressTv, pLp);

        descTv = new TextView(context);
        descTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        descTv.setTextSize(13f);
        descTv.setLineSpacing(UiHelper.dp(context, 2), 1f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = UiHelper.dp(context, 9);
        dLp.bottomMargin = UiHelper.dp(context, 16);
        infoCol.addView(descTv, dLp);

        // Action Buttons Row
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        actionsRow.setClipChildren(false);

        btnPlay = makeGlassActionBtn(context, "▶  Xem ngay", true);
        btnWishlist = makeGlassActionBtn(context, "+  Danh sách của tôi", false);
        btnLike = makeGlassActionBtn(context, "👍  Thích", false);
        btnDislike = makeGlassActionBtn(context, "👎  Không thích", false);

        actionsRow.addView(btnPlay, marginRightLp(context, 10));
        actionsRow.addView(btnWishlist, marginRightLp(context, 10));
        actionsRow.addView(btnLike, marginRightLp(context, 10));
        actionsRow.addView(btnDislike, marginRightLp(context, 0));
        infoCol.addView(actionsRow);

        contentCol.addView(heroGlass, hgLp);

        // Seasons & Episodes Section
        seasonSection = new LinearLayout(context);
        seasonSection.setOrientation(LinearLayout.VERTICAL);
        seasonSection.setVisibility(GONE);

        TextView epTitle = makeSectionTitle(context, "Danh sách tập phim");
        seasonSection.addView(epTitle);

        HorizontalScrollView seasonScroll = new HorizontalScrollView(context);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonTabsRow = new LinearLayout(context);
        seasonTabsRow.setOrientation(LinearLayout.HORIZONTAL);
        seasonScroll.addView(seasonTabsRow);
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ssLp.bottomMargin = UiHelper.dp(context, 12);
        seasonSection.addView(seasonScroll, ssLp);

        episodeGridWrap = new LinearLayout(context);
        episodeGridWrap.setOrientation(LinearLayout.VERTICAL);
        episodeGridWrap.setClipChildren(false);
        seasonSection.addView(episodeGridWrap);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        secLp.bottomMargin = UiHelper.dp(context, 24);
        contentCol.addView(seasonSection, secLp);

        // Sources Section (Movies)
        sourcesSection = new LinearLayout(context);
        sourcesSection.setOrientation(LinearLayout.VERTICAL);
        sourcesSection.setVisibility(GONE);
        sourcesSection.addView(makeSectionTitle(context, "Nguồn phát khả dụng"));
        HorizontalScrollView srcScroll = new HorizontalScrollView(context);
        srcScroll.setHorizontalScrollBarEnabled(false);
        sourcesRow = new LinearLayout(context);
        sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
        srcScroll.addView(sourcesRow);
        sourcesSection.addView(srcScroll);
        contentCol.addView(sourcesSection, secLp);

        // Cast & Crew Section
        castSection = new LinearLayout(context);
        castSection.setOrientation(LinearLayout.VERTICAL);
        castSection.setVisibility(GONE);
        castSection.addView(makeSectionTitle(context, "Đạo diễn & Diễn viên"));
        HorizontalScrollView castScroll = new HorizontalScrollView(context);
        castScroll.setHorizontalScrollBarEnabled(false);
        castRow = new LinearLayout(context);
        castRow.setOrientation(LinearLayout.HORIZONTAL);
        castScroll.addView(castRow);
        castSection.addView(castScroll);
        contentCol.addView(castSection, secLp);

        // Related Movies Section
        relatedSection = new LinearLayout(context);
        relatedSection.setOrientation(LinearLayout.VERTICAL);
        relatedSection.setVisibility(GONE);
        relatedSection.addView(makeSectionTitle(context, "Có thể bạn cũng thích"));
        relatedRv = new RecyclerView(context);
        relatedRv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        relatedRv.setClipChildren(false);
        relatedRv.setClipToPadding(false);
        relatedRv.setAdapter(relatedAdapter);
        relatedSection.addView(relatedRv);
        contentCol.addView(relatedSection);

        // Loading overlay
        loadingBox = new LinearLayout(context);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        loadingBox.setBackgroundColor(Color.argb(180, 7, 9, 15));
        loadingBox.setVisibility(GONE);
        loadingBox.addView(new UiHelper.SpinnerView(context),
                new LinearLayout.LayoutParams(UiHelper.dp(context, 42), UiHelper.dp(context, 42)));
        addView(loadingBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Error overlay
        errorBox = new LinearLayout(context);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setBackgroundColor(Color.argb(210, 7, 9, 15));
        errorBox.setVisibility(GONE);
        errorTv = new TextView(context);
        errorTv.setTextColor(Color.WHITE);
        errorTv.setTextSize(15f);
        errorBox.addView(errorTv);
        TextView btnRetry = makeGlassActionBtn(context, "Quay lại", true);
        btnRetry.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleBack() && MovieDetailScreenView.this.listener != null) {
                    MovieDetailScreenView.this.listener.onCloseDetail();
                }
            }
        });
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = UiHelper.dp(context, 16);
        errorBox.addView(btnRetry, brLp);
        addView(errorBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bindActionListeners();
    }

    private static Context ctxOrThis(Context ctx) {
        return ctx;
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
        UiHelper.safeLoadBackdrop(backdropIv, bgUrl);
        UiHelper.safeLoadImage(posterIv, !m.posterUrl.isEmpty() ? m.posterUrl : bgUrl, R.drawable.default_poster, 24);

        badgeQualityTv.setText(m.videoQuality != null && !m.videoQuality.isEmpty() ? m.videoQuality : "FHD");
        titleTv.setText(m.title);

        List<String> metaParts = new ArrayList<>();
        if (!m.imdbRating.isEmpty() && !"0".equals(m.imdbRating)) metaParts.add("IMDb ★ " + m.imdbRating);
        if (!m.year.isEmpty()) metaParts.add(m.year);
        if (!m.runtime.isEmpty()) metaParts.add(m.runtime);
        if (m.totalView > 0) metaParts.add(m.totalView + " lượt xem");
        if (!m.genres.isEmpty()) {
            List<String> gn = new ArrayList<>();
            for (Models.FilterOption g : m.genres) gn.add(g.name);
            metaParts.add(TextUtils.join(", ", gn));
        }
        if (!m.countries.isEmpty()) {
            List<String> cn = new ArrayList<>();
            for (Models.FilterOption c : m.countries) cn.add(c.name);
            metaParts.add(TextUtils.join(", ", cn));
        }
        metaTv.setText(TextUtils.join("  •  ", metaParts));

        if (m.watchHistory != null && m.watchHistory.progressPercent > 0) {
            progressTv.setVisibility(VISIBLE);
            progressTv.setText("Đã xem " + m.watchHistory.viewedRate
                    + (!m.watchHistory.timeRemaining.isEmpty() ? " (" + m.watchHistory.timeRemaining + ")" : ""));
            btnPlay.setText("▶  Xem tiếp");
        } else {
            progressTv.setVisibility(GONE);
            btnPlay.setText("▶  Xem ngay");
        }

        descTv.setText(m.description != null && !m.description.isEmpty() ? m.description : "Chưa có mô tả chi tiết.");
        updateWishlistVisual();
        updateReactionVisuals();

        // Seasons & Episodes
        renderSeasonsAndEpisodes(m);

        // Sources
        sourcesRow.removeAllViews();
        if (!m.sources.isEmpty() && m.isTvSeries == 0) {
            sourcesSection.setVisibility(VISIBLE);
            Context ctx = getContext();
            for (final Models.SourceItem src : m.sources) {
                TextView btn = makeGlassActionBtn(ctx, src.label, false);
                btn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launchWithStream(src.streamCode, 0, 1, "", src.label);
                    }
                });
                sourcesRow.addView(btn, marginRightLp(ctx, 10));
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
            Context ctx = getContext();
            for (Models.StarItem st : people) {
                LinearLayout personCard = new LinearLayout(ctx);
                personCard.setOrientation(LinearLayout.VERTICAL);
                personCard.setGravity(Gravity.CENTER_HORIZONTAL);
                personCard.setPadding(UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 10));
                UiHelper.applyFocusableGlassButton(personCard, ctx, 24, false);

                ImageView iv = new ImageView(ctx);
                UiHelper.safeLoadAvatar(iv, st.imageUrl);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 64), UiHelper.dp(ctx, 64));
                ivLp.bottomMargin = UiHelper.dp(ctx, 8);
                personCard.addView(iv, ivLp);

                TextView nm = new TextView(ctx);
                nm.setText(st.name);
                nm.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
                nm.setTextSize(12.5f);
                nm.setTypeface(null, Typeface.BOLD);
                nm.setMaxLines(1);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                nm.setGravity(Gravity.CENTER);
                personCard.addView(nm);

                TextView role = new TextView(ctx);
                role.setText("director".equalsIgnoreCase(st.starType) ? "Đạo diễn" : "Diễn viên");
                role.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                role.setTextSize(11f);
                role.setGravity(Gravity.CENTER);
                personCard.addView(role);

                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 114), LinearLayout.LayoutParams.WRAP_CONTENT);
                cLp.rightMargin = UiHelper.dp(ctx, 12);
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

    private void renderSeasonsAndEpisodes(final Models.MovieDetail m) {
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
            for (final Models.SeasonItem s : m.seasons) {
                boolean active = s.seasonsId == selectedSeasonId;
                TextView tab = makeGlassActionBtn(ctx, s.seasonsName, active);
                tab.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectedSeasonId = s.seasonsId;
                        renderSeasonsAndEpisodes(m);
                    }
                });
                seasonTabsRow.addView(tab, marginRightLp(ctx, 10));
                if (active) displayEps.addAll(s.episodes);
            }
        } else {
            displayEps.addAll(m.episodes);
        }

        if (displayEps.isEmpty()) {
            seasonSection.setVisibility(GONE);
            return;
        }
        seasonSection.setVisibility(VISIBLE);
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
            boolean isLastWatched = m.watchHistory != null
                    && ((ep.episodesId > 0 && ep.episodesId == m.watchHistory.lastEpisodeId)
                    || (!ep.streamCode.isEmpty() && ep.streamCode.equals(m.watchHistory.lastStreamCode)));

            TextView epBtn = makeGlassActionBtn(
                    ctx,
                    (isLastWatched ? "▶ " : "") + (ep.episodesName.isEmpty() ? "Tập " + epNum : ep.episodesName),
                    isLastWatched);
            epBtn.setGravity(Gravity.CENTER);
            epBtn.setSingleLine(true);
            epBtn.setEllipsize(TextUtils.TruncateAt.END);
            epBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchWithStream(ep.streamCode, ep.episodesId, epNum,
                            ep.seasonsId > 0 ? String.valueOf(ep.seasonsId) : "", ep.episodesName);
                }
            });
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if ((i % cols) < cols - 1) bLp.rightMargin = UiHelper.dp(ctx, 10);
            currentRow.addView(epBtn, bLp);
        }
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
    }

    private void updateWishlistVisual() {
        if (currentDetail == null) return;
        btnWishlist.setText(currentDetail.inWishlist ? "✓  Đã trong danh sách" : "+  Danh sách của tôi");
        UiHelper.setButtonSelectedVisual(btnWishlist, getContext(), 24, currentDetail.inWishlist);
    }

    private void updateReactionVisuals() {
        if (currentDetail == null) return;
        String react = Prefs.getReaction(getContext(), String.valueOf(currentDetail.videosId));
        UiHelper.setButtonSelectedVisual(btnLike, getContext(), 24, "like".equals(react));
        UiHelper.setButtonSelectedVisual(btnDislike, getContext(), 24, "dislike".equals(react));
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

    private TextView makeGlassActionBtn(Context ctx, String text, boolean accent) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(UiHelper.dp(ctx, 15), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 15), UiHelper.dp(ctx, 8));
        UiHelper.applyFocusableGlassButton(tv, ctx, 15, accent);
        return tv;
    }

    private TextView makeSectionTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        tv.setTextSize(15.5f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(ctx, 9);
        tv.setLayoutParams(lp);
        return tv;
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
