package com.dangvietanh.cast.ui;

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

/**
 * Slim always-docked Netflix / VieON style Android TV navigation rail (176dp wide)
 * with flat unfocused rows, vibrant red-pink active indicator, and compact bottom status pill.
 */
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
    private final LinearLayout statusChip;
    private final TextView statusUserTv;
    private final List<NavItem> navItems = new ArrayList<>();
    private LinearLayout settingsRow;
    private ImageView settingsIcon;
    private TextView settingsLabel;

    private String activeTabId = "home";

    public TvNavigationRail(Context context, Listener listener) {
        super(context);
        this.listener = listener;

        int dockedW = UiHelper.dp(context, 176);
        FrameLayout.LayoutParams selfLp = new FrameLayout.LayoutParams(
                dockedW, FrameLayout.LayoutParams.MATCH_PARENT);
        selfLp.setMargins(UiHelper.dp(context, 10), UiHelper.dp(context, 10), 0, UiHelper.dp(context, 10));
        setLayoutParams(selfLp);

        railCard = new LinearLayout(context);
        railCard.setOrientation(LinearLayout.VERTICAL);
        railCard.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 14),
                UiHelper.dp(context, 10), UiHelper.dp(context, 12));
        railCard.setBackground(UiHelper.createPillDrawable(
                context, 18, Color.argb(228, 13, 15, 24), Color.argb(20, 255, 255, 255), 1.0f));
        addView(railCard, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 1. Clean Top Brand Row
        LinearLayout brandRow = new LinearLayout(context);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 4),
                UiHelper.dp(context, 6), UiHelper.dp(context, 12));

        ImageView logoIv = new ImageView(context);
        logoIv.setImageResource(R.drawable.splash_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 26), UiHelper.dp(context, 26));
        logoLp.rightMargin = UiHelper.dp(context, 9);
        brandRow.addView(logoIv, logoLp);

        TextView brandTv = new TextView(context);
        brandTv.setText("DVA Cast");
        brandTv.setTextColor(Color.WHITE);
        brandTv.setTextSize(14f);
        brandTv.setTypeface(null, Typeface.BOLD);
        brandTv.setMaxLines(1);
        brandRow.addView(brandTv);
        railCard.addView(brandRow);

        // 2. Scrollable Clean Nav Items
        ScrollView navScroll = new ScrollView(context);
        navScroll.setVerticalScrollBarEnabled(false);
        navScroll.setClipChildren(false);
        LinearLayout navCol = new LinearLayout(context);
        navCol.setOrientation(LinearLayout.VERTICAL);
        navCol.setClipChildren(false);
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
        div.setBackgroundColor(Color.argb(18, 255, 255, 255));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 1));
        divLp.setMargins(UiHelper.dp(context, 8), UiHelper.dp(context, 6),
                UiHelper.dp(context, 8), UiHelper.dp(context, 6));
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
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 34));
        stLp.bottomMargin = UiHelper.dp(context, 2);
        navCol.addView(settingsRow, stLp);

        // 3. Compact Bottom Account / Connection Chip
        statusChip = new LinearLayout(context);
        statusChip.setOrientation(LinearLayout.HORIZONTAL);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 6),
                UiHelper.dp(context, 10), UiHelper.dp(context, 6));
        statusChip.setBackground(UiHelper.createPillDrawable(
                context, 11, Color.argb(115, 255, 255, 255) & 0x16FFFFFF, Color.argb(24, 255, 255, 255), 1.0f));
        UiHelper.applyFocusableGlassButton(statusChip, context, 11, false);

        View greenDot = new View(context);
        greenDot.setBackground(UiHelper.createGlassRect(context, 4, UiHelper.COLOR_GREEN, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams gdLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 7), UiHelper.dp(context, 7));
        gdLp.rightMargin = UiHelper.dp(context, 9);
        statusChip.addView(greenDot, gdLp);

        LinearLayout scTextCol = new LinearLayout(context);
        scTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView scTop = new TextView(context);
        scTop.setText("Đã kết nối");
        scTop.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        scTop.setTextSize(9.5f);
        scTextCol.addView(scTop);

        statusUserTv = new TextView(context);
        statusUserTv.setText("Thành viên");
        statusUserTv.setTextColor(Color.WHITE);
        statusUserTv.setTextSize(11f);
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
        statusChip.setOnKeyListener(rightMoveKeyListener());
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 40));
        scLp.topMargin = UiHelper.dp(context, 6);
        railCard.addView(statusChip, scLp);

        updateTabVisuals();
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
                LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(ctx, 34));
        lp.bottomMargin = UiHelper.dp(ctx, 2);
        parent.addView(row, lp);
        navItems.add(item);
    }

    private LinearLayout buildNavRow(final Context ctx, int iconRes, String label, boolean active) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 5), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 5));
        row.setFocusable(true);
        row.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        row.setBackground(UiHelper.createSidebarItemStateDrawable(ctx, 11, active));

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(active ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 17), UiHelper.dp(ctx, 17));
        iconLp.rightMargin = UiHelper.dp(ctx, 10);
        row.addView(iv, iconLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(active ? Color.WHITE : UiHelper.COLOR_TEXT_SECONDARY);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnKeyListener(rightMoveKeyListener());
        attachNavFocusBehavior(row, iv, tv, active);
        return row;
    }

    private void attachNavFocusBehavior(final View row, final ImageView iv, final TextView tv, final boolean isActive) {
        row.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = hasFocus ? 1.02f : 1.0f;
                v.animate().scaleX(sc).scaleY(sc).setDuration(110).setInterpolator(new DecelerateInterpolator()).start();
                boolean bright = hasFocus || isActive;
                iv.setColorFilter(bright ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED, PorterDuff.Mode.SRC_IN);
                tv.setTextColor(bright ? Color.WHITE : UiHelper.COLOR_TEXT_SECONDARY);
                tv.setTypeface(null, bright ? Typeface.BOLD : Typeface.NORMAL);
            }
        });
    }

    private OnKeyListener rightMoveKeyListener() {
        return new OnKeyListener() {
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
            item.rowView.setBackground(UiHelper.createSidebarItemStateDrawable(ctx, 11, active));
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
