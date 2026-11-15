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

public class OverlayMenuController {

    public interface Actions {
        void onReloadPage();
        void onClearCache();
        void onCheckUpdates();
        String getVersionName();
    }

    private final Context context;
    private final Actions actions;
    private final View backgroundToBlur;

    private FrameLayout overlayRoot;
    private LinearLayout mainCard;
    private TextView ipValueView;
    private TextView adbHintView;
    private TextView autoStartBadge;
    private View firstTile;
    private View previouslyFocused;
    private boolean showing = false;

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
        int cardWidth = Math.min(UiHelper.dp(context, 610), Math.round(screenW * 0.90f));

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

        // ScrollView wrapper so small projector viewports never clip top or bottom
        ScrollView scrollWrapper = new ScrollView(context);
        scrollWrapper.setVerticalScrollBarEnabled(false);
        scrollWrapper.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollWrapper.setClipChildren(false);
        scrollWrapper.setClipToPadding(false);
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
        int padH = UiHelper.dp(context, 22);
        int padV = UiHelper.dp(context, 18);
        mainCard.setPadding(padH, padV, padH, padV);
        mainCard.setClickable(true);
        mainCard.setClipChildren(false);
        mainCard.setClipToPadding(false);
        UiHelper.applyGlassCardStyle(mainCard, context, 28, UiHelper.COLOR_SURFACE_CARD, 20);

        // 1. Compact Apple TV / Netflix TV Header Row
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setClipChildren(false);
        headerRow.setClipToPadding(false);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = UiHelper.dp(context, 12);

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

        TextView titleTv = new TextView(context);
        titleTv.setText("Tiện ích & Bảo trì");
        titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(18);
        titleTv.setSingleLine(true);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleBadgeRow.addView(titleTv);

        TextView verPill = new TextView(context);
        verPill.setText("v" + actions.getVersionName());
        verPill.setTextColor(Color.parseColor("#FF8A9B"));
        verPill.setTextSize(11);
        verPill.setSingleLine(true);
        verPill.setTypeface(Typeface.DEFAULT_BOLD);
        verPill.setPadding(UiHelper.dp(context, 9), UiHelper.dp(context, 3),
                UiHelper.dp(context, 9), UiHelper.dp(context, 3));
        verPill.setBackground(UiHelper.createGlassRect(context, 999,
                UiHelper.COLOR_BTN_THEME_NORMAL, UiHelper.COLOR_BORDER_THEME, 1));
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        verLp.leftMargin = UiHelper.dp(context, 8);
        titleBadgeRow.addView(verPill, verLp);

        TextView subTv = new TextView(context);
        subTv.setText("DVA Cast TV · Trung tâm điều khiển & chẩn đoán");
        subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        subTv.setTextSize(11.5f);
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);
        subTv.setPadding(0, UiHelper.dp(context, 2), 0, 0);

        titleCol.addView(titleBadgeRow);
        titleCol.addView(subTv);
        headerRow.addView(titleCol, titleColLp);

        // Top-right Close Button (saves vertical height)
        TextView closeBtn = new TextView(context);
        closeBtn.setId(View.generateViewId());
        closeBtn.setText("✕  Đóng");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(13);
        closeBtn.setSingleLine(true);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 8),
                UiHelper.dp(context, 16), UiHelper.dp(context, 8));
        UiHelper.applyFocusableTileStyle(closeBtn, context, 999,
                UiHelper.COLOR_BTN_THEME_NORMAL, UiHelper.COLOR_BTN_THEME_FOCUSED);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.leftMargin = UiHelper.dp(context, 10);
        headerRow.addView(closeBtn, closeLp);

        mainCard.addView(headerRow, headerLp);

        // 2. Compact Wi-Fi IP & ADB Status Banner
        LinearLayout ipCard = new LinearLayout(context);
        ipCard.setOrientation(LinearLayout.HORIZONTAL);
        ipCard.setGravity(Gravity.CENTER_VERTICAL);
        ipCard.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 10),
                UiHelper.dp(context, 14), UiHelper.dp(context, 10));
        ipCard.setBackground(UiHelper.createGlassRect(context, 18,
                UiHelper.COLOR_SURFACE_PILL, UiHelper.COLOR_BORDER_THEME, 1));
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ipLp.bottomMargin = UiHelper.dp(context, 12);

        FrameLayout wifiBox = new FrameLayout(context);
        wifiBox.setBackground(UiHelper.createThemeGradientRect(context, 12, 55,
                UiHelper.COLOR_BORDER_THEME, 1));
        ImageView wifiIcon = new ImageView(context);
        wifiIcon.setImageResource(R.drawable.ic_wifi);
        wifiIcon.setColorFilter(Color.parseColor("#FF4D6D"));
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
        ipLabel.setText("Địa chỉ IP mạng của máy chiếu");
        ipLabel.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        ipLabel.setTextSize(12);
        ipLabel.setSingleLine(true);
        ipLabel.setTypeface(Typeface.DEFAULT_BOLD);

        adbHintView = new TextView(context);
        adbHintView.setText("adb connect ...:5555");
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
        ipValueView.setTextSize(13.5f);
        ipValueView.setSingleLine(true);
        ipValueView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        ipValueView.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 5),
                UiHelper.dp(context, 12), UiHelper.dp(context, 5));
        ipValueView.setBackground(UiHelper.createGlassRect(context, 999,
                UiHelper.COLOR_BTN_THEME_NORMAL, UiHelper.COLOR_BORDER_THEME, 1));
        ipCard.addView(ipValueView);

        mainCard.addView(ipCard, ipLp);

        // 3. 2x2 Sleek Horizontal Action Cards (Apple TV / Netflix TV Control Center style)
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
                R.drawable.ic_reload, "#FF4D6D",
                "Tải lại trang", "Làm mới mã QR & kết nối",
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
                "Xóa Cache", "Dọn bộ nhớ đệm & tải lại",
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
                "Tự khởi động", "Mở khi bật máy",
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
                R.drawable.ic_update, "#E50914",
                "Cập nhật OTA", "Kiểm tra & tải bản mới",
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

        mainCard.addView(row1, row1Lp);
        mainCard.addView(row2, row2Lp);

        // 4. Compact Footer Hint
        TextView footerHint = new TextView(context);
        footerHint.setText("Nhấn phím Back / Menu để đóng · Giữ phím OK bất kỳ lúc nào để mở lại");
        footerHint.setTextColor(Color.argb(165, 176, 184, 200));
        footerHint.setTextSize(11);
        footerHint.setSingleLine(true);
        footerHint.setEllipsize(TextUtils.TruncateAt.END);
        footerHint.setGravity(Gravity.CENTER_HORIZONTAL);
        mainCard.addView(footerHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Wire explicit D-Pad navigation so focus never escapes behind the overlay
        tileReload.setId(View.generateViewId());
        tileCache.setId(View.generateViewId());
        tileAutoStart.setId(View.generateViewId());
        tileUpdate.setId(View.generateViewId());

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
        tileAutoStart.setNextFocusDownId(closeBtn.getId());

        tileUpdate.setNextFocusUpId(tileCache.getId());
        tileUpdate.setNextFocusLeftId(tileAutoStart.getId());
        tileUpdate.setNextFocusRightId(closeBtn.getId());
        tileUpdate.setNextFocusDownId(closeBtn.getId());

        closeBtn.setNextFocusDownId(tileCache.getId());
        closeBtn.setNextFocusLeftId(tileReload.getId());
        closeBtn.setNextFocusUpId(closeBtn.getId());
        closeBtn.setNextFocusRightId(closeBtn.getId());

        firstTile = tileReload;
        cardContainer.addView(mainCard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollWrapper.addView(cardContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(scrollWrapper, scrollLp);
    }

    private View createHorizontalActionTile(int iconRes, String iconTintHex, String title, String subtitle,
                                            TextView optionalBadge, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 12),
                UiHelper.dp(context, 14), UiHelper.dp(context, 12));
        UiHelper.applyFocusableTileStyle(tile, context, 20,
                UiHelper.COLOR_SURFACE_PILL, UiHelper.COLOR_BTN_THEME_FOCUSED);
        tile.setOnClickListener(listener);

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(UiHelper.createThemeGradientRect(context, 14, 52,
                UiHelper.COLOR_BORDER_THEME, 1));
        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.parseColor(iconTintHex));
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                UiHelper.dp(context, 20), UiHelper.dp(context, 20), Gravity.CENTER);
        iconBox.addView(iv, ivLp);

        LinearLayout.LayoutParams iconBoxLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 40), UiHelper.dp(context, 40));
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
            badgeLp.leftMargin = UiHelper.dp(context, 6);
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

    private void refreshNetworkInfo() {
        String ip = UiHelper.getWifiIpAddress(context);
        ipValueView.setText(ip);
        if (ip.contains(".")) {
            adbHintView.setText("ADB mạng LAN: adb connect " + ip + ":5555");
        } else {
            adbHintView.setText("Kiểm tra kết nối Wi-Fi / LAN trên máy chiếu");
        }
    }

    private void refreshAutoStartBadge() {
        boolean on = Prefs.isAutoStartEnabled(context);
        autoStartBadge.setText(on ? "BẬT" : "TẮT");
        autoStartBadge.setTextColor(on ? UiHelper.COLOR_GREEN : UiHelper.COLOR_TEXT_MUTED);
        autoStartBadge.setBackground(UiHelper.createGlassRect(context, 999,
                on ? Color.argb(52, 48, 209, 88) : Color.argb(90, 20, 24, 36),
                on ? Color.argb(120, 48, 209, 88) : UiHelper.COLOR_BORDER_GLASS, 1));
    }

    public void show() {
        if (showing) return;
        showing = true;
        previouslyFocused = overlayRoot.getRootView().findFocus();
        refreshNetworkInfo();
        refreshAutoStartBadge();

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
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_MENU) {
            hide();
            return true;
        }
        return false;
    }
}
