package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.Models;

/**
 * Compact pro Android TV movie card helper matching Netflix / VieON proportions:
 * - Landscape ("Tiếp tục xem") 198x112dp with inside-bottom title, circular play icon, 3dp progress bar
 * - Vertical portrait 124x178dp with crisp corner badges and glowing TV focus ring
 * - Top 10 rank + 116x168dp portrait
 */
public final class MovieCardView {

    public static final int TYPE_VERTICAL = 0;
    public static final int TYPE_LANDSCAPE = 1;
    public static final int TYPE_TOP10 = 2;
    public static final int TYPE_ANIME_HERO = 3;

    public interface OnMovieClickListener {
        void onMovieClick(Models.MovieItem item);
    }

    public interface OnMovieFocusListener {
        void onMovieFocused(Models.MovieItem item, boolean hasFocus);
    }

    private MovieCardView() {
    }

    public static int resolveCardType(String layout) {
        if (layout == null) return TYPE_VERTICAL;
        String l = layout.toLowerCase();
        if ("watched".equals(l) || "landscape".equals(l) || "theatrical".equals(l)) {
            return TYPE_LANDSCAPE;
        }
        if ("top10".equals(l)) {
            return TYPE_TOP10;
        }
        if ("anime_hero".equals(l)) {
            return TYPE_ANIME_HERO;
        }
        return TYPE_VERTICAL;
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        public final int cardType;
        public final FrameLayout posterContainer;
        public final ImageView posterIv;
        public final View focusBorderOverlay;
        public final TextView badgeLeftTv;
        public final TextView badgeRightTv;
        public final FrameLayout progressTrack;
        public final View progressFill;
        public final TextView overlayTitleTv;
        public final FrameLayout playBadge;
        public final TextView titleTv;
        public final TextView subTv;
        public final TextView rankTv;
        public Models.MovieItem boundItem;

        private Holder(int cardType,
                       View root,
                       FrameLayout posterContainer,
                       ImageView posterIv,
                       View focusBorderOverlay,
                       TextView badgeLeftTv,
                       TextView badgeRightTv,
                       FrameLayout progressTrack,
                       View progressFill,
                       TextView overlayTitleTv,
                       FrameLayout playBadge,
                       TextView titleTv,
                       TextView subTv,
                       TextView rankTv) {
            super(root);
            this.cardType = cardType;
            this.posterContainer = posterContainer;
            this.posterIv = posterIv;
            this.focusBorderOverlay = focusBorderOverlay;
            this.badgeLeftTv = badgeLeftTv;
            this.badgeRightTv = badgeRightTv;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
            this.overlayTitleTv = overlayTitleTv;
            this.playBadge = playBadge;
            this.titleTv = titleTv;
            this.subTv = subTv;
            this.rankTv = rankTv;
        }
    }

    public static Holder createHolder(Context ctx, final int cardType, boolean inGrid,
                                      final OnMovieClickListener clickListener,
                                      final OnMovieFocusListener focusListener) {
        final boolean isLandscape = (cardType == TYPE_LANDSCAPE || cardType == TYPE_ANIME_HERO);
        int posterW;
        int posterH;
        if (isLandscape) {
            posterW = UiHelper.dp(ctx, 198);
            posterH = UiHelper.dp(ctx, 112);
        } else if (cardType == TYPE_TOP10) {
            posterW = UiHelper.dp(ctx, 116);
            posterH = UiHelper.dp(ctx, 174);
        } else if (inGrid) {
            posterW = UiHelper.dp(ctx, 132);
            posterH = UiHelper.dp(ctx, 198);
        } else {
            posterW = UiHelper.dp(ctx, 124);
            posterH = UiHelper.dp(ctx, 186);
        }

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(cardType == TYPE_TOP10 ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        outer.setGravity(cardType == TYPE_TOP10 ? Gravity.BOTTOM : Gravity.START);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        outer.setFocusable(true);
        outer.setFocusableInTouchMode(false);
        outer.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            outer.setDefaultFocusHighlightEnabled(false);
        }

        RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                cardType == TYPE_TOP10 ? LinearLayout.LayoutParams.WRAP_CONTENT : posterW,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootLp.setMargins(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 4));
        outer.setLayoutParams(rootLp);

        TextView rankTv = null;
        if (cardType == TYPE_TOP10) {
            rankTv = new TextView(ctx);
            rankTv.setTextColor(Color.argb(230, 255, 255, 255));
            rankTv.setTextSize(44f);
            rankTv.setTypeface(null, Typeface.BOLD);
            rankTv.setIncludeFontPadding(false);
            rankTv.setShadowLayer(UiHelper.dp(ctx, 6), 0, UiHelper.dp(ctx, 2), Color.argb(180, 0, 0, 0));
            LinearLayout.LayoutParams rkLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rkLp.rightMargin = UiHelper.dp(ctx, -4);
            rkLp.bottomMargin = UiHelper.dp(ctx, 28);
            outer.addView(rankTv, rkLp);
        }

        LinearLayout cardBody = new LinearLayout(ctx);
        cardBody.setOrientation(LinearLayout.VERTICAL);
        cardBody.setClipChildren(false);
        cardBody.setClipToPadding(false);
        outer.addView(cardBody, new LinearLayout.LayoutParams(posterW, LinearLayout.LayoutParams.WRAP_CONTENT));

        final FrameLayout posterContainer = new FrameLayout(ctx);
        posterContainer.setBackgroundColor(Color.parseColor("#141824"));
        final int radiusDp = 12;
        posterContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), radiusDp));
            }
        });
        posterContainer.setClipToOutline(true);
        posterContainer.setElevation(UiHelper.dp(ctx, 6));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            posterContainer.setOutlineAmbientShadowColor(Color.argb(145, 0, 0, 0));
            posterContainer.setOutlineSpotShadowColor(Color.argb(190, 0, 0, 0));
        }

        ImageView posterIv = new ImageView(ctx);
        posterIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterContainer.addView(posterIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Bottom contrast gradient scrim inside card
        View bottomScrim = new View(ctx);
        bottomScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(isLandscape ? 210 : 145, 9, 11, 18)}));
        FrameLayout.LayoutParams bsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, isLandscape ? 58 : 42));
        bsLp.gravity = Gravity.BOTTOM;
        posterContainer.addView(bottomScrim, bsLp);

        // Top-left Episode / Quality tag (sharp 5dp radius)
        TextView badgeLeftTv = new TextView(ctx);
        badgeLeftTv.setTextColor(Color.WHITE);
        badgeLeftTv.setTextSize(9.5f);
        badgeLeftTv.setTypeface(null, Typeface.BOLD);
        badgeLeftTv.setPadding(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 2));
        badgeLeftTv.setBackground(UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
        FrameLayout.LayoutParams blLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        blLp.setMargins(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 6), 0, 0);
        posterContainer.addView(badgeLeftTv, blLp);

        // Top-right IMDb Rating tag
        TextView badgeRightTv = new TextView(ctx);
        badgeRightTv.setTextColor(UiHelper.COLOR_GOLD);
        badgeRightTv.setTextSize(9.5f);
        badgeRightTv.setTypeface(null, Typeface.BOLD);
        badgeRightTv.setPadding(UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 2));
        badgeRightTv.setBackground(UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
        FrameLayout.LayoutParams brLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        brLp.setMargins(0, UiHelper.dp(ctx, 6), UiHelper.dp(ctx, 6), 0);
        posterContainer.addView(badgeRightTv, brLp);

        TextView overlayTitleTv = null;
        FrameLayout playBadge = null;
        if (isLandscape) {
            overlayTitleTv = new TextView(ctx);
            overlayTitleTv.setTextColor(Color.WHITE);
            overlayTitleTv.setTextSize(12f);
            overlayTitleTv.setTypeface(null, Typeface.BOLD);
            overlayTitleTv.setMaxLines(1);
            overlayTitleTv.setEllipsize(TextUtils.TruncateAt.END);
            overlayTitleTv.setShadowLayer(4f, 0f, 1f, Color.BLACK);
            FrameLayout.LayoutParams otLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            otLp.setMargins(UiHelper.dp(ctx, 10), 0, UiHelper.dp(ctx, 42), UiHelper.dp(ctx, 10));
            posterContainer.addView(overlayTitleTv, otLp);

            playBadge = new FrameLayout(ctx);
            playBadge.setBackground(UiHelper.createPillDrawable(
                    ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
            ImageView playIv = new ImageView(ctx);
            playIv.setImageResource(R.drawable.ic_play);
            playIv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            playBadge.addView(playIv, new FrameLayout.LayoutParams(
                    UiHelper.dp(ctx, 13), UiHelper.dp(ctx, 13), Gravity.CENTER));
            FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                    UiHelper.dp(ctx, 26), UiHelper.dp(ctx, 26), Gravity.BOTTOM | Gravity.END);
            pbLp.setMargins(0, 0, UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 8));
            posterContainer.addView(playBadge, pbLp);
        }

        // Thin 3dp watch progress bar inside card bottom edge
        FrameLayout progressTrack = new FrameLayout(ctx);
        progressTrack.setBackgroundColor(Color.argb(90, 255, 255, 255));
        View progressFill = new View(ctx);
        progressFill.setBackgroundColor(UiHelper.COLOR_ACCENT_RED);
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams ptLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 3));
        ptLp.gravity = Gravity.BOTTOM;
        posterContainer.addView(progressTrack, ptLp);

        // Crisp White TV Focus Border Overlay (2.5dp, zero pink/red glow)
        final View focusBorderOverlay = new View(ctx);
        focusBorderOverlay.setBackground(UiHelper.createWhiteFocusBorderDrawable(
                ctx, radiusDp, UiHelper.FOCUS_BORDER_DP));
        focusBorderOverlay.setAlpha(0f);
        posterContainer.addView(focusBorderOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        cardBody.addView(posterContainer, new LinearLayout.LayoutParams(posterW, posterH));

        TextView titleTv = null;
        TextView subTv = null;
        if (!isLandscape) {
            titleTv = new TextView(ctx);
            titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            titleTv.setTextSize(11.5f);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setMaxLines(1);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = UiHelper.dp(ctx, 5);
            cardBody.addView(titleTv, tLp);

            subTv = new TextView(ctx);
            subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
            subTv.setTextSize(10f);
            subTv.setMaxLines(1);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = UiHelper.dp(ctx, 1);
            cardBody.addView(subTv, sLp);
        }

        final Holder holder = new Holder(cardType, outer, posterContainer, posterIv, focusBorderOverlay,
                badgeLeftTv, badgeRightTv, progressTrack, progressFill, overlayTitleTv, playBadge,
                titleTv, subTv, rankTv);

        outer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.boundItem != null && clickListener != null) {
                    clickListener.onMovieClick(holder.boundItem);
                }
            }
        });

        final float baseElev = UiHelper.dp(ctx, 6);
        final float focusElev = UiHelper.dp(ctx, 22);
        final Context fCtx = ctx;
        final TextView refTitleTv = titleTv;
        outer.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                UiHelper.applyTvFocusEffect(v, fCtx, hasFocus, UiHelper.FOCUS_SCALE_DEFAULT, 6);
                posterContainer.setElevation(hasFocus ? focusElev : baseElev);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (hasFocus) {
                        posterContainer.setOutlineSpotShadowColor(UiHelper.COLOR_FOCUS_SHADOW_SPOT);
                        posterContainer.setOutlineAmbientShadowColor(UiHelper.COLOR_FOCUS_SHADOW_AMBIENT);
                    } else {
                        posterContainer.setOutlineSpotShadowColor(UiHelper.COLOR_GLASS_SHADOW_SPOT);
                        posterContainer.setOutlineAmbientShadowColor(UiHelper.COLOR_GLASS_SHADOW_AMBIENT);
                    }
                }
                focusBorderOverlay.animate().alpha(hasFocus ? 1f : 0f).setDuration(130).start();
                if (refTitleTv != null) {
                    refTitleTv.setTextColor(hasFocus ? Color.WHITE : UiHelper.COLOR_TEXT_PRIMARY);
                }
                if (holder.boundItem != null && focusListener != null) {
                    focusListener.onMovieFocused(holder.boundItem, hasFocus);
                }
            }
        });

        return holder;
    }

    public static void bind(Holder h, Models.MovieItem item, int cardType, int position) {
        h.boundItem = item;
        if (item == null) return;

        Context ctx = h.itemView.getContext();
        boolean isLandscape = (cardType == TYPE_LANDSCAPE || cardType == TYPE_ANIME_HERO);
        String primaryUrl = isLandscape ? item.getLandscapeUrl() : item.getPortraitUrl();
        String fallbackUrl = isLandscape ? item.getPortraitUrl() : item.getLandscapeUrl();
        UiHelper.safeLoadImageWithFallback(h.posterIv, primaryUrl, fallbackUrl, R.drawable.default_poster, 0);

        String leftBadge = item.episodeBadge != null && !item.episodeBadge.isEmpty()
                ? item.episodeBadge
                : (item.videoQuality != null && !item.videoQuality.isEmpty() ? item.videoQuality : "");
        if (!isLandscape && !leftBadge.isEmpty()) {
            h.badgeLeftTv.setVisibility(View.VISIBLE);
            h.badgeLeftTv.setText(leftBadge);
        } else {
            h.badgeLeftTv.setVisibility(View.GONE);
        }

        if (!isLandscape && item.imdbRating != null && !item.imdbRating.isEmpty() && !"0".equals(item.imdbRating)) {
            h.badgeRightTv.setVisibility(View.VISIBLE);
            h.badgeRightTv.setText("★ " + item.imdbRating);
        } else {
            h.badgeRightTv.setVisibility(View.GONE);
        }

        if (h.rankTv != null) {
            int rankNum = item.rank > 0 ? item.rank : (position + 1);
            h.rankTv.setText(String.valueOf(rankNum));
        }

        if (item.progressPercent > 0) {
            h.progressTrack.setVisibility(View.VISIBLE);
            int width = h.posterContainer.getLayoutParams() != null && h.posterContainer.getLayoutParams().width > 0
                    ? h.posterContainer.getLayoutParams().width
                    : UiHelper.dp(ctx, isLandscape ? 198 : 124);
            int pct = Math.max(4, Math.min(100, item.progressPercent));
            h.progressFill.getLayoutParams().width = Math.round(width * (pct / 100f));
            h.progressFill.requestLayout();
        } else {
            h.progressTrack.setVisibility(View.GONE);
        }

        String displayTitle = item.getDisplayTitle();
        if (h.overlayTitleTv != null) {
            h.overlayTitleTv.setText(displayTitle);
        }
        if (h.titleTv != null) {
            h.titleTv.setText(displayTitle);
        }

        if (h.subTv != null) {
            String sub;
            if (item.timeRemaining != null && !item.timeRemaining.isEmpty()) {
                sub = item.timeRemaining;
            } else if (item.viewedInfo != null && !item.viewedInfo.isEmpty()) {
                sub = item.viewedInfo;
            } else if (item.subtitle != null && !item.subtitle.isEmpty()) {
                sub = item.subtitle;
            } else {
                StringBuilder sb = new StringBuilder();
                if (!item.year.isEmpty()) sb.append(item.year);
                if (!item.genres.isEmpty()) {
                    if (sb.length() > 0) sb.append(" • ");
                    sb.append(item.genres.get(0));
                }
                sub = sb.toString();
            }
            h.subTv.setText(sub);
        }
    }
}
