package com.dangvietanh.cast;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
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

import java.util.List;

public class OverlayMenuController {

    public interface Actions {
        void onReloadPage();
        void onClearCache();
        void onCheckUpdates();
        void onOpenSubtitleSettings();
        void onFetchHistory(Runnable onComplete);
        String getVersionName();
        long getVersionCode();
    }

    private final Context context;
    private final Actions actions;
    private final View backgroundToBlur;

    private FrameLayout overlayRoot;
    private LinearLayout mainCard;

    // Header views
    private FrameLayout backBtn;
    private TextView headerTitleTv;
    private TextView headerSubTv;
    private TextView verPill;
    private FrameLayout closeBtn;

    // Main Settings Section
    private LinearLayout settingsSection;
    private TextView ipValueView;
    private TextView adbHintView;
    private TextView autoStartBadge;
    private TextView changelogBadge;
    private View firstTile;
    private View tileChangelog;

    // Changelog History Section
    private LinearLayout historySection;
    private ScrollView historyScrollView;
    private LinearLayout historyListContainer;

    private View previouslyFocused;
    private boolean showing = false;
    private boolean showingHistory = false;

    public OverlayMenuController(Context context, View backgroundToBlur, Actions actions) {
        this.context = context;
        this.backgroundToBlur = backgroundToBlur;
        this.actions = actions;
        buildUi();
    }

    public View getView() {
        return overlayRoot;
    }

    public boolean isShowing() {
        return showing;
    }

    private void buildUi() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels > 0 ? dm.widthPixels : UiHelper.dp(context, 960);
        int screenH = dm.heightPixels > 0 ? dm.heightPixels : UiHelper.dp(context, 540);
        int cardWidth = Math.min(UiHelper.dp(context, 640), Math.round(screenW * 0.90f));
        int historyMaxH = Math.min(UiHelper.dp(context, 300), Math.round(screenH * 0.54f));

        overlayRoot = new FrameLayout(context);
        overlayRoot.setVisibility(View.GONE);
        overlayRoot.setBackground(UiHelper.createOverlayBackdropDrawable());
        overlayRoot.setClickable(true);
        overlayRoot.setFocusable(false);
        overlayRoot.setClipChildren(false);
        overlayRoot.setClipToPadding(false);
        overlayRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        ScrollView outerScroll = new ScrollView(context);
        outerScroll.setVerticalScrollBarEnabled(false);
        outerScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        outerScroll.setClipChildren(false);
        outerScroll.setClipToPadding(false);
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        scrollLp.setMargins(UiHelper.dp(context, 16), UiHelper.dp(context, 12),
                UiHelper.dp(context, 16), UiHelper.dp(context, 12));

        FrameLayout cardContainer = new FrameLayout(context);
        cardContainer.setClipChildren(false);
        cardContainer.setClipToPadding(false);
        int outerPad = UiHelper.dp(context, 6);
        cardContainer.setPadding(outerPad, outerPad, outerPad, outerPad);

        mainCard = new LinearLayout(context);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        // Matches CSS: padding: 30px 32px; border-radius: 40px; rgba(0, 0, 0, 0.25); 1px rgba(255,255,255,0.16)
        mainCard.setPadding(UiHelper.dp(context, 32), UiHelper.dp(context, 30),
                UiHelper.dp(context, 32), UiHelper.dp(context, 30));
        mainCard.setClickable(true);
        mainCard.setClipChildren(false);
        mainCard.setClipToPadding(false);
        UiHelper.applyGlassCardStyle(mainCard, context, 40, UiHelper.COLOR_SURFACE_GLASS, 24);

        // 1. Header Row
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setClipChildren(false);
        headerRow.setClipToPadding(false);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = UiHelper.dp(context, 14);

        backBtn = createHeaderIconButton(R.drawable.ic_back, "Quay lại Cài đặt");
        backBtn.setId(View.generateViewId());
        backBtn.setVisibility(View.GONE);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToSettingsView();
            }
        });
        int headerIconSize = UiHelper.dp(context, 40);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(headerIconSize, headerIconSize);
        backLp.rightMargin = UiHelper.dp(context, 12);
        headerRow.addView(backBtn, backLp);

        ImageView brandLogo = new ImageView(context);
        brandLogo.setImageResource(R.drawable.splash_logo);
        brandLogo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 36), UiHelper.dp(context, 36));
        logoLp.rightMargin = UiHelper.dp(context, 12);
        headerRow.addView(brandLogo, logoLp);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        LinearLayout titleBadgeRow = new LinearLayout(context);
        titleBadgeRow.setOrientation(LinearLayout.HORIZONTAL);
        titleBadgeRow.setGravity(Gravity.CENTER_VERTICAL);

        headerTitleTv = new TextView(context);
        headerTitleTv.setText("Cài đặt");
        headerTitleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        headerTitleTv.setTextSize(19);
        headerTitleTv.setSingleLine(true);
        headerTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleBadgeRow.addView(headerTitleTv);

        verPill = new TextView(context);
        verPill.setText("v" + actions.getVersionName());
        verPill.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        verPill.setTextSize(11);
        verPill.setSingleLine(true);
        verPill.setTypeface(Typeface.DEFAULT_BOLD);
        verPill.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 3),
                UiHelper.dp(context, 10), UiHelper.dp(context, 3));
        verPill.setBackground(UiHelper.createGlassRect(context, 24,
                UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verLp.leftMargin = UiHelper.dp(context, 8);
        titleBadgeRow.addView(verPill, verLp);

        headerSubTv = new TextView(context);
        headerSubTv.setText("DVA Cast TV · Thiết lập trình phát & rạp phim");
        headerSubTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        headerSubTv.setTextSize(11.5f);
        headerSubTv.setSingleLine(true);
        headerSubTv.setEllipsize(TextUtils.TruncateAt.END);
        headerSubTv.setPadding(0, UiHelper.dp(context, 2), 0, 0);

        titleCol.addView(titleBadgeRow);
        titleCol.addView(headerSubTv);
        headerRow.addView(titleCol, titleColLp);

        closeBtn = createHeaderIconButton(R.drawable.ic_close, "Đóng Cài đặt");
        closeBtn.setId(View.generateViewId());
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(headerIconSize, headerIconSize);
        closeLp.leftMargin = UiHelper.dp(context, 10);
        headerRow.addView(closeBtn, closeLp);

        mainCard.addView(headerRow, headerLp);

        // ============================================================
        // SECTION A: SETTINGS DASHBOARD
        // ============================================================
        settingsSection = new LinearLayout(context);
        settingsSection.setOrientation(LinearLayout.VERTICAL);
        settingsSection.setClipChildren(false);
        settingsSection.setClipToPadding(false);

        // Wi-Fi IP & ADB Banner
        LinearLayout ipCard = new LinearLayout(context);
        ipCard.setOrientation(LinearLayout.HORIZONTAL);
        ipCard.setGravity(Gravity.CENTER_VERTICAL);
        ipCard.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 10),
                UiHelper.dp(context, 14), UiHelper.dp(context, 10));
        ipCard.setBackground(UiHelper.createGlassRect(context, 24,
                UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ipLp.bottomMargin = UiHelper.dp(context, 12);

        FrameLayout wifiBox = new FrameLayout(context);
        wifiBox.setBackground(UiHelper.createGlassRect(context, 14,
                UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));
        ImageView wifiIcon = new ImageView(context);
        wifiIcon.setImageResource(R.drawable.ic_wifi);
        wifiIcon.setColorFilter(Color.parseColor("#64D2FF"));
        wifiBox.addView(wifiIcon, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 18), UiHelper.dp(context, 18), Gravity.CENTER));
        LinearLayout.LayoutParams wifiBoxLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 34), UiHelper.dp(context, 34));
        wifiBoxLp.rightMargin = UiHelper.dp(context, 12);
        ipCard.addView(wifiBox, wifiBoxLp);

        LinearLayout ipTextCol = new LinearLayout(context);
        ipTextCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ipTextLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView ipLabel = new TextView(context);
        ipLabel.setText("Kết nối mạng của thiết bị");
        ipLabel.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        ipLabel.setTextSize(12);
        ipLabel.setSingleLine(true);
        ipLabel.setTypeface(Typeface.DEFAULT_BOLD);

        adbHintView = new TextView(context);
        adbHintView.setText("ADB nội bộ: adb connect ...:5555");
        adbHintView.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        adbHintView.setTextSize(11);
        adbHintView.setSingleLine(true);
        adbHintView.setEllipsize(TextUtils.TruncateAt.END);

        ipTextCol.addView(ipLabel);
        ipTextCol.addView(adbHintView);
        ipCard.addView(ipTextCol, ipTextLp);

        ipValueView = new TextView(context);
        ipValueView.setText("...");
        ipValueView.setTextColor(Color.WHITE);
        ipValueView.setTextSize(13);
        ipValueView.setSingleLine(true);
        ipValueView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        ipValueView.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 5),
                UiHelper.dp(context, 12), UiHelper.dp(context, 5));
        ipValueView.setBackground(UiHelper.createGlassRect(context, 24,
                UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));
        ipCard.addView(ipValueView);

        settingsSection.addView(ipCard, ipLp);

        // 2x2 Action Cards
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setClipChildren(false);
        row1.setClipToPadding(false);
        LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row1Lp.bottomMargin = UiHelper.dp(context, 10);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setClipChildren(false);
        row2.setClipToPadding(false);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.bottomMargin = UiHelper.dp(context, 10);

        View tileReload = createHorizontalActionTile(
                R.drawable.ic_reload, "#64D2FF",
                "Khởi tạo lại mã QR", "Tạo phiên kết nối & mã ghép nối mới",
                null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        actions.onReloadPage();
                    }
                });

        View tileCache = createHorizontalActionTile(
                R.drawable.ic_clean, "#FFD60A",
                "Dọn bộ nhớ đệm", "Làm sạch dữ liệu tạm & tối ưu trình phát",
                null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        actions.onClearCache();
                    }
                });

        autoStartBadge = new TextView(context);
        autoStartBadge.setTextSize(10);
        autoStartBadge.setSingleLine(true);
        autoStartBadge.setTypeface(Typeface.DEFAULT_BOLD);
        autoStartBadge.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 3),
                UiHelper.dp(context, 8), UiHelper.dp(context, 3));

        View tileAutoStart = createHorizontalActionTile(
                R.drawable.ic_power, "#30D158",
                "Tự mở khi bật máy", "Khởi chạy rạp phim khi mở thiết bị",
                autoStartBadge,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean next = !Prefs.isAutoStartEnabled(context);
                        Prefs.setAutoStartEnabled(context, next);
                        refreshAutoStartBadge();
                    }
                });

        View tileUpdate = createHorizontalActionTile(
                R.drawable.ic_update, "#FF453A",
                "Cập nhật phiên bản", "Kiểm tra & cài đặt bản mới qua Wi-Fi",
                null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        actions.onCheckUpdates();
                    }
                });

        LinearLayout.LayoutParams leftTileLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftTileLp.rightMargin = UiHelper.dp(context, 5);
        LinearLayout.LayoutParams rightTileLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightTileLp.leftMargin = UiHelper.dp(context, 5);

        row1.addView(tileReload, leftTileLp);
        row1.addView(tileCache, rightTileLp);
        row2.addView(tileAutoStart, new LinearLayout.LayoutParams(leftTileLp));
        row2.addView(tileUpdate, new LinearLayout.LayoutParams(rightTileLp));

        settingsSection.addView(row1, row1Lp);
        settingsSection.addView(row2, row2Lp);

        // Full-width Subtitle Customization Row
        View tileSubtitle = createHorizontalActionTile(
                R.drawable.ic_subtitles, "#FF375F",
                "Tuỳ chỉnh phụ đề", "Phông chữ, màu sắc, cỡ chữ, vị trí, viền chữ & dịch",
                null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        actions.onOpenSubtitleSettings();
                    }
                });
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = UiHelper.dp(context, 10);
        settingsSection.addView(tileSubtitle, subLp);

        // Full-width Changelog Row
        changelogBadge = new TextView(context);
        changelogBadge.setTextSize(10.5f);
        changelogBadge.setSingleLine(true);
        changelogBadge.setTypeface(Typeface.DEFAULT_BOLD);
        changelogBadge.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        changelogBadge.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 4),
                UiHelper.dp(context, 10), UiHelper.dp(context, 4));
        changelogBadge.setBackground(UiHelper.createGlassRect(context, 24,
                UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));

        tileChangelog = createHorizontalActionTile(
                R.drawable.ic_update, "#BF5AF2",
                "Nhật ký cập nhật", "Xem chi tiết thay đổi theo từng phiên bản",
                changelogBadge,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        switchToHistoryView();
                    }
                });
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clLp.bottomMargin = UiHelper.dp(context, 10);
        settingsSection.addView(tileChangelog, clLp);

        TextView footerHint = new TextView(context);
        footerHint.setText("Nhấn phím Back / Menu để đóng · Giữ OK bất kỳ lúc nào để mở Cài đặt");
        footerHint.setTextColor(Color.argb(165, 176, 184, 200));
        footerHint.setTextSize(11);
        footerHint.setSingleLine(true);
        footerHint.setEllipsize(TextUtils.TruncateAt.END);
        footerHint.setGravity(Gravity.CENTER_HORIZONTAL);
        settingsSection.addView(footerHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mainCard.addView(settingsSection);

        // ============================================================
        // SECTION B: UPDATE CHANGELOG HISTORY VIEW
        // ============================================================
        historySection = new LinearLayout(context);
        historySection.setOrientation(LinearLayout.VERTICAL);
        historySection.setVisibility(View.GONE);

        historyScrollView = new ScrollView(context);
        historyScrollView.setVerticalScrollBarEnabled(true);
        historyScrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        historyScrollView.setFocusable(false);

        historyListContainer = new LinearLayout(context);
        historyListContainer.setOrientation(LinearLayout.VERTICAL);
        historyListContainer.setPadding(0, 0, UiHelper.dp(context, 4), 0);
        historyScrollView.addView(historyListContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams hsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, historyMaxH);
        historySection.addView(historyScrollView, hsLp);

        mainCard.addView(historySection);

        // Wire D-Pad navigation
        tileReload.setId(View.generateViewId());
        tileCache.setId(View.generateViewId());
        tileAutoStart.setId(View.generateViewId());
        tileUpdate.setId(View.generateViewId());
        tileSubtitle.setId(View.generateViewId());
        tileChangelog.setId(View.generateViewId());

        tileReload.setNextFocusRightId(tileCache.getId());
        tileReload.setNextFocusLeftId(tileReload.getId());
        tileReload.setNextFocusUpId(closeBtn.getId());
        tileReload.setNextFocusDownId(tileAutoStart.getId());

        tileCache.setNextFocusLeftId(tileReload.getId());
        tileCache.setNextFocusRightId(closeBtn.getId());
        tileCache.setNextFocusUpId(closeBtn.getId());
        tileCache.setNextFocusDownId(tileUpdate.getId());

        tileAutoStart.setNextFocusUpId(tileReload.getId());
        tileAutoStart.setNextFocusRightId(tileUpdate.getId());
        tileAutoStart.setNextFocusLeftId(tileAutoStart.getId());
        tileAutoStart.setNextFocusDownId(tileSubtitle.getId());

        tileUpdate.setNextFocusUpId(tileCache.getId());
        tileUpdate.setNextFocusLeftId(tileAutoStart.getId());
        tileUpdate.setNextFocusRightId(closeBtn.getId());
        tileUpdate.setNextFocusDownId(tileSubtitle.getId());

        tileSubtitle.setNextFocusUpId(tileAutoStart.getId());
        tileSubtitle.setNextFocusDownId(tileChangelog.getId());
        tileSubtitle.setNextFocusLeftId(tileSubtitle.getId());
        tileSubtitle.setNextFocusRightId(tileSubtitle.getId());

        tileChangelog.setNextFocusUpId(tileSubtitle.getId());
        tileChangelog.setNextFocusDownId(closeBtn.getId());
        tileChangelog.setNextFocusLeftId(tileChangelog.getId());
        tileChangelog.setNextFocusRightId(tileChangelog.getId());

        closeBtn.setNextFocusDownId(tileCache.getId());
        closeBtn.setNextFocusLeftId(backBtn.getId());
        closeBtn.setNextFocusUpId(closeBtn.getId());
        closeBtn.setNextFocusRightId(closeBtn.getId());

        backBtn.setNextFocusRightId(closeBtn.getId());
        backBtn.setNextFocusDownId(closeBtn.getId());
        backBtn.setNextFocusLeftId(backBtn.getId());
        backBtn.setNextFocusUpId(backBtn.getId());

        firstTile = tileReload;
        cardContainer.addView(mainCard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outerScroll.addView(cardContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(outerScroll, scrollLp);
    }

    private FrameLayout createHeaderIconButton(int iconRes, String contentDesc) {
        FrameLayout btn = new FrameLayout(context);
        btn.setContentDescription(contentDesc);
        UiHelper.applyFocusableTileStyle(btn, context, 20,
                UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);
        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE);
        btn.addView(iv, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 18), UiHelper.dp(context, 18), Gravity.CENTER));
        return btn;
    }

    private View createHorizontalActionTile(int iconRes, String iconTintHex, String title, String subtitle,
                                            TextView optionalBadge, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 12),
                UiHelper.dp(context, 14), UiHelper.dp(context, 12));
        UiHelper.applyFocusableTileStyle(tile, context, 24,
                UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);
        tile.setOnClickListener(listener);

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(UiHelper.createGlassRect(context, 14,
                UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));
        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.parseColor(iconTintHex));
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                UiHelper.dp(context, 20), UiHelper.dp(context, 20), Gravity.CENTER);
        iconBox.addView(iv, ivLp);

        LinearLayout.LayoutParams iconBoxLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 38), UiHelper.dp(context, 38));
        iconBoxLp.rightMargin = UiHelper.dp(context, 12);
        tile.addView(iconBox, iconBoxLp);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(14);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleTvLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(titleTv, titleTvLp);

        if (optionalBadge != null) {
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.leftMargin = UiHelper.dp(context, 8);
            titleRow.addView(optionalBadge, badgeLp);
        }

        TextView subTv = new TextView(context);
        subTv.setText(subtitle);
        subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        subTv.setTextSize(11.5f);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        subTv.setPadding(0, UiHelper.dp(context, 2), 0, 0);

        textCol.addView(titleRow);
        textCol.addView(subTv);
        tile.addView(textCol, textColLp);

        return tile;
    }

    private void switchToSettingsView() {
        showingHistory = false;
        backBtn.setVisibility(View.GONE);
        headerTitleTv.setText("Cài đặt");
        headerSubTv.setText("DVA Cast TV · Thiết lập trình phát & rạp phim");
        settingsSection.setVisibility(View.VISIBLE);
        historySection.setVisibility(View.GONE);
        closeBtn.setNextFocusDownId(tileChangelog.getId());
        tileChangelog.post(new Runnable() {
            @Override
            public void run() {
                tileChangelog.requestFocus();
            }
        });
    }

    private void switchToHistoryView() {
        showingHistory = true;
        backBtn.setVisibility(View.VISIBLE);
        headerTitleTv.setText("Nhật ký cập nhật");
        headerSubTv.setText("Lịch sử phiên bản & tính năng mới của DVA Cast TV");
        settingsSection.setVisibility(View.GONE);
        historySection.setVisibility(View.VISIBLE);

        populateHistoryList();
        actions.onFetchHistory(new Runnable() {
            @Override
            public void run() {
                if (showing && showingHistory) {
                    populateHistoryList();
                }
            }
        });

        backBtn.post(new Runnable() {
            @Override
            public void run() {
                backBtn.requestFocus();
            }
        });
    }

    private void populateHistoryList() {
        historyListContainer.removeAllViews();
        List<UpdateHistoryStore.HistoryEntry> entries = UpdateHistoryStore.getHistory(context);
        long localCode = actions.getVersionCode();

        for (int i = 0; i < entries.size(); i++) {
            UpdateHistoryStore.HistoryEntry entry = entries.get(i);
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 14),
                    UiHelper.dp(context, 16), UiHelper.dp(context, 14));
            UiHelper.applyFocusableTileStyle(card, context, 24,
                    UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < entries.size() - 1) {
                cardLp.bottomMargin = UiHelper.dp(context, 10);
            }

            // Top row: Version badge + status + date
            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topLp.bottomMargin = UiHelper.dp(context, 6);

            TextView verBadge = new TextView(context);
            verBadge.setText("v" + entry.versionName);
            verBadge.setTextColor(Color.WHITE);
            verBadge.setTextSize(12);
            verBadge.setTypeface(Typeface.DEFAULT_BOLD);
            verBadge.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 3),
                    UiHelper.dp(context, 10), UiHelper.dp(context, 3));
            verBadge.setBackground(UiHelper.createGlassRect(context, 24,
                    UiHelper.COLOR_SURFACE_GLASS, Color.TRANSPARENT, 0));
            topRow.addView(verBadge);

            if (entry.versionCode == localCode) {
                TextView curBadge = new TextView(context);
                curBadge.setText("ĐANG DÙNG");
                curBadge.setTextColor(UiHelper.COLOR_GREEN);
                curBadge.setTextSize(10);
                curBadge.setTypeface(Typeface.DEFAULT_BOLD);
                curBadge.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 3),
                        UiHelper.dp(context, 8), UiHelper.dp(context, 3));
                curBadge.setBackground(UiHelper.createGlassRect(context, 24,
                        Color.argb(40, 48, 209, 88), Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cbLp.leftMargin = UiHelper.dp(context, 8);
                topRow.addView(curBadge, cbLp);
            } else if (entry.versionCode > localCode) {
                TextView newBadge = new TextView(context);
                newBadge.setText("BẢN MỚI");
                newBadge.setTextColor(Color.parseColor("#FFD60A"));
                newBadge.setTextSize(10);
                newBadge.setTypeface(Typeface.DEFAULT_BOLD);
                newBadge.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 3),
                        UiHelper.dp(context, 8), UiHelper.dp(context, 3));
                newBadge.setBackground(UiHelper.createGlassRect(context, 24,
                        Color.argb(40, 255, 214, 10), Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams nbLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nbLp.leftMargin = UiHelper.dp(context, 8);
                topRow.addView(newBadge, nbLp);
            }

            View spacer = new View(context);
            topRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

            TextView dateTv = new TextView(context);
            dateTv.setText(entry.date != null && !entry.date.isEmpty() ? entry.date : "");
            dateTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
            dateTv.setTextSize(11.5f);
            topRow.addView(dateTv);

            card.addView(topRow, topLp);

            // Title
            TextView titleTv = new TextView(context);
            titleTv.setText(entry.title);
            titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            titleTv.setTextSize(13.5f);
            titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.bottomMargin = UiHelper.dp(context, 5);
            card.addView(titleTv, titleLp);

            // Bullet changes
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < entry.changes.size(); c++) {
                if (c > 0) sb.append('\n');
                sb.append("•  ").append(entry.changes.get(c));
            }
            TextView changesTv = new TextView(context);
            changesTv.setText(sb.toString());
            changesTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
            changesTv.setTextSize(12);
            changesTv.setLineSpacing(UiHelper.dp(context, 3), 1f);
            card.addView(changesTv);

            historyListContainer.addView(card, cardLp);
        }
        historyScrollView.scrollTo(0, 0);
    }

    private void refreshNetworkInfo() {
        String ip = UiHelper.getWifiIpAddress(context);
        ipValueView.setText(ip);
        if (ip.contains(".")) {
            adbHintView.setText("ADB nội bộ: adb connect " + ip + ":5555");
        } else {
            adbHintView.setText("Kiểm tra lại kết nối Wi-Fi / LAN trên thiết bị");
        }
    }

    private void refreshAutoStartBadge() {
        boolean on = Prefs.isAutoStartEnabled(context);
        autoStartBadge.setText(on ? "BẬT" : "TẮT");
        autoStartBadge.setTextColor(on ? UiHelper.COLOR_GREEN : UiHelper.COLOR_TEXT_MUTED);
        autoStartBadge.setBackground(UiHelper.createGlassRect(context, 24,
                on ? Color.argb(46, 48, 209, 88) : UiHelper.COLOR_SURFACE_GLASS,
                Color.TRANSPARENT, 0));
    }

    private void refreshChangelogBadge() {
        String releaseDate = UpdateHistoryStore.getCurrentReleaseDate(context, actions.getVersionCode());
        changelogBadge.setText("v" + actions.getVersionName() + " · " + releaseDate + "  ›");
    }

    public void show() {
        if (showing) return;
        showing = true;
        showingHistory = false;
        previouslyFocused = overlayRoot.getRootView().findFocus();

        backBtn.setVisibility(View.GONE);
        headerTitleTv.setText("Cài đặt");
        headerSubTv.setText("DVA Cast TV · Thiết lập trình phát & rạp phim");
        settingsSection.setVisibility(View.VISIBLE);
        historySection.setVisibility(View.GONE);

        refreshNetworkInfo();
        refreshAutoStartBadge();
        refreshChangelogBadge();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backgroundToBlur != null) {
            backgroundToBlur.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP));
        }

        overlayRoot.setAlpha(0f);
        overlayRoot.setVisibility(View.VISIBLE);
        mainCard.setScaleX(0.95f);
        mainCard.setScaleY(0.95f);

        overlayRoot.animate().alpha(1f).setDuration(200).start();
        mainCard.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();

        firstTile.post(new Runnable() {
            @Override
            public void run() {
                firstTile.requestFocus();
            }
        });
    }

    public void hide() {
        if (!showing) return;
        showing = false;
        showingHistory = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backgroundToBlur != null) {
            backgroundToBlur.setRenderEffect(null);
        }

        overlayRoot.animate().alpha(0f).setDuration(170).withEndAction(new Runnable() {
            @Override
            public void run() {
                overlayRoot.setVisibility(View.GONE);
                if (previouslyFocused != null) {
                    previouslyFocused.requestFocus();
                } else if (backgroundToBlur != null) {
                    backgroundToBlur.requestFocus();
                }
            }
        }).start();
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (!showing) return false;
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (showingHistory) {
                switchToSettingsView();
            } else {
                hide();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            hide();
            return true;
        }
        return false;
    }
}
