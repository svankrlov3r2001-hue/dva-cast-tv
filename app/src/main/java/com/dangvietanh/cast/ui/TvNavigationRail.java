package com.dangvietanh.cast.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
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

public class TvNavigationRail extends FrameLayout {

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
    private final LinearLayout userRow;
    private final ImageView avatarIv;
    private final TextView userNameTv;
    private final LinearLayout settingsRow;
    private final TextView settingsLabelTv;
    private final List<NavItem> navItems = new ArrayList<>();

    private String activeTabId = "home";
    private boolean expanded = false;
    private ValueAnimator widthAnimator;

    public TvNavigationRail(Context context, Listener listener) {
        super(context);
        this.listener = listener;

        int collapsedW = UiHelper.dp(context, 72);
        FrameLayout.LayoutParams selfLp = new FrameLayout.LayoutParams(
                collapsedW, FrameLayout.LayoutParams.MATCH_PARENT);
        selfLp.setMargins(UiHelper.dp(context, 14), UiHelper.dp(context, 16), 0, UiHelper.dp(context, 16));
        setLayoutParams(selfLp);

        railCard = new LinearLayout(context);
        railCard.setOrientation(LinearLayout.VERTICAL);
        railCard.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 14),
                UiHelper.dp(context, 10), UiHelper.dp(context, 14));
        UiHelper.applyGlassCardStyle(railCard, context, 36, UiHelper.COLOR_SURFACE_GLASS, 24);
        addView(railCard, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Top User Profile Row
        userRow = buildIconLabelRow(context, R.drawable.ic_person, "Thành viên", false);
        avatarIv = (ImageView) userRow.getChildAt(0);
        avatarIv.clearColorFilter();
        userNameTv = (TextView) userRow.getChildAt(1);
        userRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TvNavigationRail.this.listener != null) {
                    TvNavigationRail.this.listener.onOpenAccountProfile();
                }
            }
        });
        LinearLayout.LayoutParams urLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 46));
        urLp.bottomMargin = UiHelper.dp(context, 10);
        railCard.addView(userRow, urLp);

        // Middle Scrollable Tabs
        ScrollView navScroll = new ScrollView(context);
        navScroll.setVerticalScrollBarEnabled(false);
        navScroll.setClipChildren(false);
        LinearLayout navCol = new LinearLayout(context);
        navCol.setOrientation(LinearLayout.VERTICAL);
        navScroll.addView(navCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        railCard.addView(navScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        addTab(context, navCol, "search", "Tìm kiếm", R.drawable.ic_search);
        addTab(context, navCol, "home", "Trang chủ", R.drawable.ic_home);
        addTab(context, navCol, "series", "Phim Bộ", R.drawable.ic_tv);
        addTab(context, navCol, "movies", "Phim Lẻ", R.drawable.ic_movie);
        addTab(context, navCol, "anime", "Anime", R.drawable.ic_anime);
        addTab(context, navCol, "shows", "TV Show", R.drawable.ic_show);
        addTab(context, navCol, "kids", "Thiếu Nhi", R.drawable.ic_kids);
        addTab(context, navCol, "explore", "Khám phá", R.drawable.ic_explore);
        addTab(context, navCol, "library", "Danh sách", R.drawable.ic_bookmark);

        // Bottom Settings Row
        settingsRow = buildIconLabelRow(context, R.drawable.ic_settings, "Cài đặt TV", true);
        settingsLabelTv = (TextView) settingsRow.getChildAt(1);
        settingsRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TvNavigationRail.this.listener != null) {
                    TvNavigationRail.this.listener.onOpenSettingsMenu();
                }
            }
        });
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 44));
        stLp.topMargin = UiHelper.dp(context, 8);
        railCard.addView(settingsRow, stLp);

        updateTabVisuals();
        applyExpandedVisuals(false);
    }

    private void addTab(Context ctx, LinearLayout parent, final String id, String label, int iconRes) {
        final NavItem item = new NavItem(id, label, iconRes);
        LinearLayout row = buildIconLabelRow(ctx, iconRes, label, true);
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
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 44));
        lp.bottomMargin = UiHelper.dp(ctx, 4);
        parent.addView(row, lp);
        navItems.add(item);
    }

    private LinearLayout buildIconLabelRow(final Context ctx, int iconRes, String label, boolean tintIcon) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 8));
        UiHelper.applyFocusableGlassButton(row, ctx, 24, false);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        if (tintIcon) {
            iv.setColorFilter(UiHelper.COLOR_TEXT_SECONDARY, PorterDuff.Mode.SRC_IN);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 22), UiHelper.dp(ctx, 22));
        iconLp.rightMargin = UiHelper.dp(ctx, 12);
        row.addView(iv, iconLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        tv.setTextSize(14f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final View.OnFocusChangeListener prevListener = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (prevListener != null) prevListener.onFocusChange(v, hasFocus);
                post(new Runnable() {
                    @Override
                    public void run() {
                        setExpanded(hasFocus());
                    }
                });
            }
        });

        row.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (listener != null) {
                        listener.onMoveFocusToContent();
                        return true;
                    }
                }
                return false;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        return row;
    }

    public void setUser(Models.User user) {
        if (user == null) {
            userNameTv.setText("Thành viên");
            avatarIv.setImageResource(R.drawable.ic_person);
            return;
        }
        String display = (user.name != null && !user.name.isEmpty()) ? user.name : user.username;
        userNameTv.setText(display != null && !display.isEmpty() ? display : "Thành viên");
        UiHelper.safeLoadAvatar(avatarIv, user.avatarUrl);
    }

    public void setActiveTab(String tabId) {
        this.activeTabId = tabId != null ? tabId : "home";
        updateTabVisuals();
    }

    public String getActiveTabId() {
        return activeTabId;
    }

    public void focusActiveTab() {
        for (NavItem item : navItems) {
            if (item.id.equals(activeTabId) && item.rowView != null) {
                item.rowView.requestFocus();
                return;
            }
        }
        if (!navItems.isEmpty() && navItems.get(1).rowView != null) {
            navItems.get(1).rowView.requestFocus();
        }
    }

    private void updateTabVisuals() {
        Context ctx = getContext();
        for (NavItem item : navItems) {
            boolean active = item.id.equals(activeTabId);
            final View.OnFocusChangeListener prev = item.rowView.getOnFocusChangeListener();
            UiHelper.setButtonSelectedVisual(item.rowView, ctx, 24, active);
            // Re-attach rail focus expansion wrapper after setButtonSelectedVisual updates background/focus listener
            final View.OnFocusChangeListener glassFocus = item.rowView.getOnFocusChangeListener();
            item.rowView.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (glassFocus != null) glassFocus.onFocusChange(v, hasFocus);
                    post(new Runnable() {
                        @Override
                        public void run() {
                            setExpanded(hasFocus());
                        }
                    });
                }
            });
            item.iconIv.setColorFilter(
                    active ? Color.WHITE : UiHelper.COLOR_TEXT_SECONDARY,
                    PorterDuff.Mode.SRC_IN);
            item.labelTv.setTextColor(active ? Color.WHITE : UiHelper.COLOR_TEXT_PRIMARY);
            if (prev == null) {
                item.rowView.invalidate();
            }
        }
    }

    public void setExpanded(boolean expand) {
        if (this.expanded == expand) return;
        this.expanded = expand;
        applyExpandedVisuals(true);
    }

    private void applyExpandedVisuals(boolean animate) {
        final int targetW = UiHelper.dp(getContext(), expanded ? 228 : 72);
        int labelVis = expanded ? VISIBLE : GONE;
        userNameTv.setVisibility(labelVis);
        settingsLabelTv.setVisibility(labelVis);
        for (NavItem item : navItems) {
            item.labelTv.setVisibility(labelVis);
        }
        if (widthAnimator != null) widthAnimator.cancel();
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int currentW = lp.width > 0 ? lp.width : targetW;
        if (!animate || currentW == targetW) {
            lp.width = targetW;
            setLayoutParams(lp);
            return;
        }
        widthAnimator = ValueAnimator.ofInt(currentW, targetW);
        widthAnimator.setDuration(170);
        widthAnimator.setInterpolator(new DecelerateInterpolator());
        widthAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                lp.width = (Integer) animation.getAnimatedValue();
                setLayoutParams(lp);
            }
        });
        widthAnimator.start();
    }
}
