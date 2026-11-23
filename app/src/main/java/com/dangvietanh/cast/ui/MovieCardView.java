package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
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
        public final FrameLayout posterContainer;
        public final ImageView posterIv;
        public final View focusGlowOverlay;
        public final TextView badgeLeftTv;
        public final TextView badgeRightTv;
        public final View progressTrack;
        public final View progressFill;
        public final TextView titleTv;
        public final TextView subTv;
        public final TextView rankTv;
        public Models.MovieItem boundItem;

        private Holder(View root,
                       FrameLayout posterContainer,
                       ImageView posterIv,
                       View focusGlowOverlay,
                       TextView badgeLeftTv,
                       TextView badgeRightTv,
                       View progressTrack,
                       View progressFill,
                       TextView titleTv,
                       TextView subTv,
                       TextView rankTv) {
            super(root);
            this.posterContainer = posterContainer;
            this.posterIv = posterIv;
            this.focusGlowOverlay = focusGlowOverlay;
            this.badgeLeftTv = badgeLeftTv;
            this.badgeRightTv = badgeRightTv;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
            this.titleTv = titleTv;
            this.subTv = subTv;
            this.rankTv = rankTv;
        }
    }

    public static Holder createHolder(Context ctx, int cardType, boolean inGrid,
                                      final OnMovieClickListener clickListener,
                                      final OnMovieFocusListener focusListener) {
        int posterW;
        int posterH;
        if (cardType == TYPE_LANDSCAPE || cardType == TYPE_ANIME_HERO) {
            posterW = UiHelper.dp(ctx, 268);
            posterH = UiHelper.dp(ctx, 152);
        } else if (cardType == TYPE_TOP10) {
            posterW = UiHelper.dp(ctx, 134);
            posterH = UiHelper.dp(ctx, 200);
        } else if (inGrid) {
            posterW = UiHelper.dp(ctx, 156);
            posterH = UiHelper.dp(ctx, 232);
        } else {
            posterW = UiHelper.dp(ctx, 146);
            posterH = UiHelper.dp(ctx, 218);
        }

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(cardType == TYPE_TOP10 ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        outer.setGravity(cardType == TYPE_TOP10 ? Gravity.BOTTOM : Gravity.START);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        outer.setFocusable(true);
        outer.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            outer.setDefaultFocusHighlightEnabled(false);
        }

        RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                cardType == TYPE_TOP10 ? LinearLayout.LayoutParams.WRAP_CONTENT : posterW,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootLp.setMargins(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 10));
        outer.setLayoutParams(rootLp);

        TextView rankTv = null;
        if (cardType == TYPE_TOP10) {
            rankTv = new TextView(ctx);
            rankTv.setTextColor(Color.argb(220, 255, 255, 255));
            rankTv.setTextSize(56f);
            rankTv.setTypeface(null, Typeface.BOLD);
            rankTv.setIncludeFontPadding(false);
            rankTv.setShadowLayer(UiHelper.dp(ctx, 8), 0, UiHelper.dp(ctx, 4), Color.argb(180, 229, 9, 20));
            LinearLayout.LayoutParams rkLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rkLp.rightMargin = UiHelper.dp(ctx, -6);
            rkLp.bottomMargin = UiHelper.dp(ctx, 24);
            outer.addView(rankTv, rkLp);
        }

        LinearLayout cardBody = new LinearLayout(ctx);
        cardBody.setOrientation(LinearLayout.VERTICAL);
        outer.addView(cardBody, new LinearLayout.LayoutParams(posterW, LinearLayout.LayoutParams.WRAP_CONTENT));

        final FrameLayout posterContainer = new FrameLayout(ctx);
        final int radiusDp = 22;
        posterContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), radiusDp));
            }
        });
        posterContainer.setClipToOutline(true);
        posterContainer.setElevation(UiHelper.dp(ctx, 10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            posterContainer.setOutlineAmbientShadowColor(Color.argb(150, 0, 0, 0));
            posterContainer.setOutlineSpotShadowColor(Color.argb(200, 0, 0, 0));
        }

        ImageView posterIv = new ImageView(ctx);
        posterIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterContainer.addView(posterIv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Bottom contrast scrim inside poster
        View bottomScrim = new View(ctx);
        GradientDrawable scrimGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(185, 7, 9, 15)});
        bottomScrim.setBackground(scrimGrad);
        FrameLayout.LayoutParams bsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 64));
        bsLp.gravity = Gravity.BOTTOM;
        posterContainer.addView(bottomScrim, bsLp);

        // Borderless inner liquid sheen overlay on focus (zero outline border)
        final View focusGlowOverlay = new View(ctx);
        GradientDrawable glowGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(56, 255, 255, 255), Color.TRANSPARENT, Color.argb(38, 229, 9, 20)});
        focusGlowOverlay.setBackground(glowGrad);
        focusGlowOverlay.setAlpha(0f);
        posterContainer.addView(focusGlowOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Top-left Episode / Quality badge (borderless glass pill)
        TextView badgeLeftTv = new TextView(ctx);
        badgeLeftTv.setTextColor(Color.WHITE);
        badgeLeftTv.setTextSize(10.5f);
        badgeLeftTv.setTypeface(null, Typeface.BOLD);
        badgeLeftTv.setPadding(UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 3));
        badgeLeftTv.setBackground(UiHelper.createGlassRect(ctx, 14, Color.argb(170, 20, 20, 24), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams blLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blLp.gravity = Gravity.TOP | Gravity.START;
        blLp.setMargins(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8), 0, 0);
        posterContainer.addView(badgeLeftTv, blLp);

        // Top-right IMDb / Rating badge
        TextView badgeRightTv = new TextView(ctx);
        badgeRightTv.setTextColor(UiHelper.COLOR_GOLD);
        badgeRightTv.setTextSize(10.5f);
        badgeRightTv.setTypeface(null, Typeface.BOLD);
        badgeRightTv.setPadding(UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 3));
        badgeRightTv.setBackground(UiHelper.createGlassRect(ctx, 14, Color.argb(175, 16, 16, 20), Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams brLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        brLp.gravity = Gravity.TOP | Gravity.END;
        brLp.setMargins(0, UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 8), 0);
        posterContainer.addView(badgeRightTv, brLp);

        // Progress bar at bottom of poster
        FrameLayout progressTrack = new FrameLayout(ctx);
        progressTrack.setBackgroundColor(Color.argb(95, 255, 255, 255));
        View progressFill = new View(ctx);
        progressFill.setBackgroundColor(UiHelper.COLOR_ACCENT_RED);
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams ptLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 4));
        ptLp.gravity = Gravity.BOTTOM;
        posterContainer.addView(progressTrack, ptLp);

        cardBody.addView(posterContainer, new LinearLayout.LayoutParams(posterW, posterH));

        TextView titleTv = new TextView(ctx);
        titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(13.5f);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = UiHelper.dp(ctx, 8);
        cardBody.addView(titleTv, tLp);

        TextView subTv = new TextView(ctx);
        subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        subTv.setTextSize(11.5f);
        subTv.setMaxLines(1);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = UiHelper.dp(ctx, 2);
        cardBody.addView(subTv, sLp);

        final Holder holder = new Holder(outer, posterContainer, posterIv, focusGlowOverlay,
                badgeLeftTv, badgeRightTv, progressTrack, progressFill, titleTv, subTv, rankTv);

        outer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.boundItem != null && clickListener != null) {
                    clickListener.onMovieClick(holder.boundItem);
                }
            }
        });

        final float baseElev = UiHelper.dp(ctx, 10);
        final float focusElev = UiHelper.dp(ctx, 24);
        outer.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = hasFocus ? 1.075f : 1.0f;
                v.animate()
                        .scaleX(sc)
                        .scaleY(sc)
                        .setDuration(150)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                posterContainer.setElevation(hasFocus ? focusElev : baseElev);
                focusGlowOverlay.animate().alpha(hasFocus ? 1f : 0f).setDuration(150).start();
                titleTv.setTextColor(hasFocus ? Color.WHITE : UiHelper.COLOR_TEXT_PRIMARY);
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
        String imgUrl = (cardType == TYPE_LANDSCAPE || cardType == TYPE_ANIME_HERO)
                ? (!item.thumbnailUrl.isEmpty() ? item.thumbnailUrl : item.posterUrl)
                : (!item.posterUrl.isEmpty() ? item.posterUrl : item.thumbnailUrl);
        UiHelper.safeLoadImage(h.posterIv, imgUrl, R.drawable.default_poster, 0);

        h.titleTv.setText(item.getDisplayTitle());

        String leftBadge = item.episodeBadge != null && !item.episodeBadge.isEmpty()
                ? item.episodeBadge
                : (item.videoQuality != null && !item.videoQuality.isEmpty() ? item.videoQuality : "");
        if (!leftBadge.isEmpty()) {
            h.badgeLeftTv.setVisibility(View.VISIBLE);
            h.badgeLeftTv.setText(leftBadge);
        } else {
            h.badgeLeftTv.setVisibility(View.GONE);
        }

        if (item.imdbRating != null && !item.imdbRating.isEmpty() && !"0".equals(item.imdbRating)) {
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
                    : UiHelper.dp(ctx, 146);
            int pct = Math.max(4, Math.min(100, item.progressPercent));
            h.progressFill.getLayoutParams().width = Math.round(width * (pct / 100f));
            h.progressFill.requestLayout();
        } else {
            h.progressTrack.setVisibility(View.GONE);
        }

        String sub = "";
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
