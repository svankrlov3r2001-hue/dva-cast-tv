package com.dangvietanh.cast.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.Models;

import java.util.ArrayList;
import java.util.List;

/**
 * Square-cornered (0dp radius), edge-flush Android TV navigation rail:
 * - Collapsed (64dp, icons only) whenever focus is outside the rail.
 * - Expands smoothly (208dp, full text + user label) whenever focus enters the rail.
 * - Shared across Home, Search, Explore, Library, and Movie Detail screens.
 */
public class TvNavigationRail extends FrameLayout {

    public static final int COLLAPSED_WIDTH_DP = 64;
    public static final int EXPANDED_WIDTH_DP = 208;

    public interface Listener {
        void onSelectTab(String tabId);
        void onOpenAccountProfile();
        void onOpenSettingsMenu();
        void onMoveFocusToContent();
    }

    private static final class NavItem {
        final String id;
        final String label;
        final int iconRes;
        LinearLayout rowView;
        ImageView iconIv;
        TextView labelTv;

        NavItem(String id, String label, int iconRes) {
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
        }
    }

    private final Listener listener;
    private final LinearLayout railCard;
    private final LinearLayout brandRow;
    private final ImageView logoIv;
    private final TextView brandTv;
    private final LinearLayout statusChip;
    private final FrameLayout statusIconWrap;
    private final LinearLayout scTextCol;
    private final TextView statusUserTv;
    private final List<NavItem> navItems = new ArrayList<>();
    private final List<View> focusChain = new ArrayList<>();
    private LinearLayout settingsRow;
    private ImageView settingsIcon;
    private TextView settingsLabel;

    private String activeTabId = "home";
    private boolean isExpanded = false;
    private ValueAnimator widthAnimator;

    private final Runnable focusCheckRunnable = new Runnable() {
        @Override
        public void run() {
            setExpanded(hasFocus(), true);
        }
    };

    public TvNavigationRail(Context context, Listener listener) {
        super(context);
        this.listener = listener;

        int collapsedW = UiHelper.dp(context, COLLAPSED_WIDTH_DP);
        FrameLayout.LayoutParams selfLp = new FrameLayout.LayoutParams(
                collapsedW, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START);
        selfLp.setMargins(0, 0, 0, 0);
        setLayoutParams(selfLp);
        setClipChildren(false);
        setClipToPadding(false);

        railCard = new LinearLayout(context);
        railCard.setOrientation(LinearLayout.VERTICAL);
        railCard.setClipChildren(false);
        railCard.setClipToPadding(false);
        railCard.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 16),
                UiHelper.dp(context, 8), UiHelper.dp(context, 14));
        railCard.setBackground(createSquareRailBackground(false));
        addView(railCard, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Subtle 1dp right hairline divider (square, flush)
        View rightDivider = new View(context);
        rightDivider.setBackgroundColor(Color.argb(28, 255, 255, 255));
        addView(rightDivider, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 1), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END));

        // 1. Top Brand Row
        brandRow = new LinearLayout(context);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER);
        brandRow.setPadding(UiHelper.dp(context, 4), UiHelper.dp(context, 4),
                UiHelper.dp(context, 4), UiHelper.dp(context, 14));

        logoIv = new ImageView(context);
        logoIv.setImageResource(R.drawable.splash_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 28), UiHelper.dp(context, 28));
        brandRow.addView(logoIv, logoLp);

        brandTv = new TextView(context);
        brandTv.setText("DVA Cast");
        brandTv.setTextColor(Color.WHITE);
        brandTv.setTextSize(14.5f);
        brandTv.setTypeface(null, Typeface.BOLD);
        brandTv.setMaxLines(1);
        brandTv.setVisibility(GONE);
        brandRow.addView(brandTv);
        railCard.addView(brandRow);

        // 2. Scrollable Clean Nav Items
        ScrollView navScroll = new ScrollView(context);
        navScroll.setVerticalScrollBarEnabled(false);
        navScroll.setClipChildren(false);
        navScroll.setClipToPadding(false);
        LinearLayout navCol = new LinearLayout(context);
        navCol.setOrientation(LinearLayout.VERTICAL);
        navCol.setClipChildren(false);
        navCol.setClipToPadding(false);
        navScroll.addView(navCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        railCard.addView(navScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        addTab(context, navCol, "home", "Trang chủ", R.drawable.ic_home);
        addTab(context, navCol, "series", "Phim Bộ", R.drawable.ic_tv);
        addTab(context, navCol, "movies", "Phim Lẻ", R.drawable.ic_movie);
        addTab(context, navCol, "anime", "Anime", R.drawable.ic_anime);
        addTab(context, navCol, "shows", "TV Show", R.drawable.ic_show);
        addTab(context, navCol, "kids", "Thiếu Nhi", R.drawable.ic_kids);

        // Delicate Divider
        View div = new View(context);
        div.setBackgroundColor(Color.argb(24, 255, 255, 255));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 1));
        divLp.setMargins(UiHelper.dp(context, 6), UiHelper.dp(context, 6),
                UiHelper.dp(context, 6), UiHelper.dp(context, 6));
        navCol.addView(div, divLp);

        addTab(context, navCol, "explore", "Khám phá", R.drawable.ic_explore);
        addTab(context, navCol, "library", "Danh sách", R.drawable.ic_bookmark);
        addTab(context, navCol, "search", "Tìm kiếm", R.drawable.ic_search);

        settingsRow = buildNavRow(context, R.drawable.ic_settings, "Cài đặt", false);
        settingsIcon = (ImageView) settingsRow.getChildAt(0);
        settingsLabel = (TextView) settingsRow.getChildAt(1);
        settingsRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TvNavigationRail.this.listener != null) {
                    TvNavigationRail.this.listener.onOpenSettingsMenu();
                }
            }
        });
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 38));
        stLp.bottomMargin = UiHelper.dp(context, 3);
        navCol.addView(settingsRow, stLp);
        focusChain.add(settingsRow);

        // 3. Bottom Account / Connection Chip
        statusChip = new LinearLayout(context);
        statusChip.setId(View.generateViewId());
        statusChip.setOrientation(LinearLayout.HORIZONTAL);
        statusChip.setGravity(Gravity.CENTER);
        statusChip.setPadding(0, UiHelper.dp(context, 6), 0, UiHelper.dp(context, 6));
        statusChip.setFocusable(true);
        statusChip.setFocusableInTouchMode(false);
        statusChip.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            statusChip.setDefaultFocusHighlightEnabled(false);
        }
        statusChip.setBackground(UiHelper.createSidebarItemStateDrawable(context, 10, false));

        statusIconWrap = new FrameLayout(context);
        ImageView accountIv = new ImageView(context);
        accountIv.setImageResource(R.drawable.ic_person);
        accountIv.setColorFilter(UiHelper.COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN);
        statusIconWrap.addView(accountIv, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 18), UiHelper.dp(context, 18), Gravity.CENTER));

        View greenDot = new View(context);
        greenDot.setBackground(UiHelper.createGlassRect(context, 4, UiHelper.COLOR_GREEN, Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams gdLp = new FrameLayout.LayoutParams(
                UiHelper.dp(context, 7), UiHelper.dp(context, 7), Gravity.BOTTOM | Gravity.END);
        statusIconWrap.addView(greenDot, gdLp);

        LinearLayout.LayoutParams iwLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 24), UiHelper.dp(context, 24));
        statusChip.addView(statusIconWrap, iwLp);

        scTextCol = new LinearLayout(context);
        scTextCol.setOrientation(LinearLayout.VERTICAL);
        scTextCol.setVisibility(GONE);

        TextView scTop = new TextView(context);
        scTop.setText("Đã kết nối");
        scTop.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        scTop.setTextSize(9.5f);
        scTop.setMaxLines(1);
        scTextCol.addView(scTop);

        statusUserTv = new TextView(context);
        statusUserTv.setText("Thành viên");
        statusUserTv.setTextColor(Color.WHITE);
        statusUserTv.setTextSize(11.5f);
        statusUserTv.setTypeface(null, Typeface.BOLD);
        statusUserTv.setMaxLines(1);
        statusUserTv.setEllipsize(TextUtils.TruncateAt.END);
        scTextCol.addView(statusUserTv);

        statusChip.addView(scTextCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusChip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TvNavigationRail.this.listener != null) {
                    TvNavigationRail.this.listener.onOpenAccountProfile();
                }
            }
        });
        statusChip.setOnKeyListener(railKeyListener());
        statusChip.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                UiHelper.applyTvFocusEffect(v, v.getContext(), hasFocus, 1.04f, 4);
                onRailChildFocusChanged(hasFocus);
            }
        });
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 42));
        scLp.topMargin = UiHelper.dp(context, 6);
        railCard.addView(statusChip, scLp);
        focusChain.add(statusChip);

        wireVerticalFocusChain();
        updateTabVisuals();
        applyExpandedVisualState(false);
    }

    private GradientDrawable createSquareRailBackground(boolean expanded) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(0f);
        d.setColor(expanded ? Color.argb(242, 10, 12, 20) : Color.argb(214, 10, 12, 20));
        return d;
    }

    private void wireVerticalFocusChain() {
        int count = focusChain.size();
        for (int i = 0; i < count; i++) {
            View cur = focusChain.get(i);
            if (cur.getId() == View.NO_ID) {
                cur.setId(View.generateViewId());
            }
        }
        for (int i = 0; i < count; i++) {
            View cur = focusChain.get(i);
            cur.setNextFocusUpId(i > 0 ? focusChain.get(i - 1).getId() : cur.getId());
            cur.setNextFocusDownId(i < count - 1 ? focusChain.get(i + 1).getId() : cur.getId());
            cur.setNextFocusLeftId(cur.getId());
        }
    }

    private void addTab(Context ctx, LinearLayout parent, final String id, String label, int iconRes) {
        final NavItem item = new NavItem(id, label, iconRes);
        LinearLayout row = buildNavRow(ctx, iconRes, label, false);
        item.rowView = row;
        item.iconIv = (ImageView) row.getChildAt(0);
        item.labelTv = (TextView) row.getChildAt(1);
        row.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setActiveTab(id);
                if (listener != null) {
                    listener.onSelectTab(id);
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 38));
        lp.bottomMargin = UiHelper.dp(ctx, 3);
        parent.addView(row, lp);
        navItems.add(item);
        focusChain.add(row);
    }

    private LinearLayout buildNavRow(final Context ctx, int iconRes, String label, boolean active) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, UiHelper.dp(ctx, 5), 0, UiHelper.dp(ctx, 5));
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);
        row.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        row.setBackground(UiHelper.createSidebarItemStateDrawable(ctx, 10, active));

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(active ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UiHelper.dp(ctx, 19), UiHelper.dp(ctx, 19));
        row.addView(iv, iconLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(active ? Color.WHITE : UiHelper.COLOR_TEXT_SECONDARY);
        tv.setTextSize(13f);
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setVisibility(GONE);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnKeyListener(railKeyListener());
        attachNavFocusBehavior(row, iv, tv, active);
        return row;
    }

    private void attachNavFocusBehavior(final View row, final ImageView iv, final TextView tv, final boolean isActive) {
        row.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                UiHelper.applyTvFocusEffect(v, v.getContext(), hasFocus, 1.04f, 4);
                boolean bright = hasFocus || isActive;
                iv.setColorFilter(bright ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED, PorterDuff.Mode.SRC_IN);
                tv.setTextColor(bright ? Color.WHITE : UiHelper.COLOR_TEXT_SECONDARY);
                tv.setTypeface(null, bright ? Typeface.BOLD : Typeface.NORMAL);
                onRailChildFocusChanged(hasFocus);
            }
        });
    }

    private void onRailChildFocusChanged(boolean childHasFocus) {
        removeCallbacks(focusCheckRunnable);
        if (childHasFocus) {
            setExpanded(true, true);
        } else {
            post(focusCheckRunnable);
        }
    }

    public boolean isRailExpanded() {
        return isExpanded;
    }

    public void setExpanded(final boolean expand, boolean animate) {
        if (expand == isExpanded) return;
        isExpanded = expand;
        applyExpandedVisualState(expand);

        final int targetW = UiHelper.dp(getContext(), expand ? EXPANDED_WIDTH_DP : COLLAPSED_WIDTH_DP);
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) return;

        if (widthAnimator != null) {
            widthAnimator.cancel();
        }
        int startW = lp.width > 0 ? lp.width : getWidth();
        if (!animate || startW <= 0 || startW == targetW) {
            lp.width = targetW;
            setLayoutParams(lp);
            return;
        }
        widthAnimator = ValueAnimator.ofInt(startW, targetW);
        widthAnimator.setDuration(170L);
        widthAnimator.setInterpolator(new DecelerateInterpolator());
        widthAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ViewGroup.LayoutParams curLp = getLayoutParams();
                if (curLp != null) {
                    curLp.width = (Integer) animation.getAnimatedValue();
                    requestLayout();
                }
            }
        });
        widthAnimator.start();
    }

    private void applyExpandedVisualState(boolean expand) {
        Context ctx = getContext();
        railCard.setBackground(createSquareRailBackground(expand));

        // Brand row
        brandRow.setGravity(expand ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
        brandRow.setPadding(
                UiHelper.dp(ctx, expand ? 10 : 4),
                UiHelper.dp(ctx, 4),
                UiHelper.dp(ctx, expand ? 6 : 4),
                UiHelper.dp(ctx, 14));
        LinearLayout.LayoutParams logoLp = (LinearLayout.LayoutParams) logoIv.getLayoutParams();
        logoLp.rightMargin = expand ? UiHelper.dp(ctx, 10) : 0;
        logoIv.setLayoutParams(logoLp);
        brandTv.setVisibility(expand ? VISIBLE : GONE);

        // Nav items
        for (NavItem item : navItems) {
            applyRowExpandedState(ctx, item.rowView, item.iconIv, item.labelTv, expand);
        }
        if (settingsRow != null) {
            applyRowExpandedState(ctx, settingsRow, settingsIcon, settingsLabel, expand);
        }

        // Bottom account chip
        statusChip.setGravity(expand ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
        statusChip.setPadding(
                UiHelper.dp(ctx, expand ? 10 : 0),
                UiHelper.dp(ctx, 6),
                UiHelper.dp(ctx, expand ? 10 : 0),
                UiHelper.dp(ctx, 6));
        LinearLayout.LayoutParams iwLp = (LinearLayout.LayoutParams) statusIconWrap.getLayoutParams();
        iwLp.rightMargin = expand ? UiHelper.dp(ctx, 9) : 0;
        statusIconWrap.setLayoutParams(iwLp);
        scTextCol.setVisibility(expand ? VISIBLE : GONE);
    }

    private void applyRowExpandedState(Context ctx, LinearLayout row, ImageView iv, TextView tv, boolean expand) {
        row.setGravity(expand ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
        row.setPadding(
                UiHelper.dp(ctx, expand ? 12 : 0),
                UiHelper.dp(ctx, 5),
                UiHelper.dp(ctx, expand ? 10 : 0),
                UiHelper.dp(ctx, 5));
        LinearLayout.LayoutParams iconLp = (LinearLayout.LayoutParams) iv.getLayoutParams();
        iconLp.rightMargin = expand ? UiHelper.dp(ctx, 11) : 0;
        iv.setLayoutParams(iconLp);
        tv.setVisibility(expand ? VISIBLE : GONE);
    }

    private OnKeyListener railKeyListener() {
        return new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (listener != null) {
                        listener.onMoveFocusToContent();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    return true;
                }
                return false;
            }
        };
    }

    public void setUser(Models.User user) {
        if (user == null) {
            statusUserTv.setText("Thành viên");
            return;
        }
        String display = (user.name != null && !user.name.isEmpty()) ? user.name : user.username;
        statusUserTv.setText(display != null && !display.isEmpty() ? display : "Thành viên");
    }

    public void setActiveTab(String tabId) {
        this.activeTabId = tabId != null ? tabId : "home";
        updateTabVisuals();
    }

    public String getActiveTabId() {
        return activeTabId;
    }

    public void focusActiveTab() {
        setExpanded(true, true);
        for (NavItem item : navItems) {
            if (item.id.equals(activeTabId) && item.rowView != null) {
                item.rowView.requestFocus();
                return;
            }
        }
        if (!navItems.isEmpty() && navItems.get(0).rowView != null) {
            navItems.get(0).rowView.requestFocus();
        }
    }

    private void updateTabVisuals() {
        Context ctx = getContext();
        for (NavItem item : navItems) {
            boolean active = item.id.equals(activeTabId);
            item.rowView.setBackground(UiHelper.createSidebarItemStateDrawable(ctx, 10, active));
            boolean bright = active || item.rowView.hasFocus();
            item.iconIv.setColorFilter(bright ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED, PorterDuff.Mode.SRC_IN);
            item.labelTv.setTextColor(bright ? Color.WHITE : UiHelper.COLOR_TEXT_SECONDARY);
            item.labelTv.setTypeface(null, bright ? Typeface.BOLD : Typeface.NORMAL);
            attachNavFocusBehavior(item.rowView, item.iconIv, item.labelTv, active);
        }
        if (settingsRow != null) {
            attachNavFocusBehavior(settingsRow, settingsIcon, settingsLabel, false);
        }
    }
}
