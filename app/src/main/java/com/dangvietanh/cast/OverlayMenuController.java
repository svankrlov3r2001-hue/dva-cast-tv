package com.dangvietanh.cast;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
        overlayRoot = new FrameLayout(context);
        overlayRoot.setVisibility(View.GONE);
        overlayRoot.setBackgroundColor(Color.argb(198, 5, 7, 12));
        overlayRoot.setClickable(true);
        overlayRoot.setFocusable(false);
        overlayRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        mainCard = new LinearLayout(context);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        int padH = UiHelper.dp(context, 30);
        int padV = UiHelper.dp(context, 28);
        mainCard.setPadding(padH, padV, padH, padV);
        mainCard.setClickable(true);
        UiHelper.applyGlassCardStyle(mainCard, context, 40, UiHelper.COLOR_SURFACE_GLASS, 24);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                UiHelper.dp(context, 640),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        cardLp.setMargins(UiHelper.dp(context, 24), UiHelper.dp(context, 20),
                UiHelper.dp(context, 24), UiHelper.dp(context, 20));

        // 1. Header
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = UiHelper.dp(context, 18);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView titleTv = new TextView(context);
        titleTv.setText("Tiện ích & Bảo trì");
        titleTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        titleTv.setTextSize(22);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subTv = new TextView(context);
        subTv.setText("Đặng Việt Anh · Nhấn Menu hoặc giữ OK để mở nhanh");
        subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        subTv.setTextSize(12);
        subTv.setPadding(0, UiHelper.dp(context, 3), 0, 0);

        titleCol.addView(titleTv);
        titleCol.addView(subTv);
        headerRow.addView(titleCol, titleColLp);

        TextView verPill = new TextView(context);
        verPill.setText("v" + actions.getVersionName());
        verPill.setTextColor(Color.WHITE);
        verPill.setTextSize(12);
        verPill.setTypeface(Typeface.DEFAULT_BOLD);
        verPill.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 7),
                UiHelper.dp(context, 14), UiHelper.dp(context, 7));
        UiHelper.applyGlassCardStyle(verPill, context, 24, UiHelper.COLOR_SURFACE_GLASS, 6);
        headerRow.addView(verPill);

        mainCard.addView(headerRow, headerLp);

        // 2. Wi-Fi IP Info Banner (rounded 24dp)
        LinearLayout ipCard = new LinearLayout(context);
        ipCard.setOrientation(LinearLayout.HORIZONTAL);
        ipCard.setGravity(Gravity.CENTER_VERTICAL);
        ipCard.setPadding(UiHelper.dp(context, 18), UiHelper.dp(context, 14),
                UiHelper.dp(context, 18), UiHelper.dp(context, 14));
        UiHelper.applyGlassCardStyle(ipCard, context, 24, UiHelper.COLOR_SURFACE_GLASS, 8);
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ipLp.bottomMargin = UiHelper.dp(context, 16);

        ImageView wifiIcon = new ImageView(context);
        wifiIcon.setImageResource(R.drawable.ic_wifi);
        wifiIcon.setColorFilter(Color.parseColor("#64D2FF"));
        LinearLayout.LayoutParams wifiIconLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 24), UiHelper.dp(context, 24));
        wifiIconLp.rightMargin = UiHelper.dp(context, 14);
        ipCard.addView(wifiIcon, wifiIconLp);

        LinearLayout ipTextCol = new LinearLayout(context);
        ipTextCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ipTextLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView ipLabel = new TextView(context);
        ipLabel.setText("Địa chỉ IP Wi-Fi của máy chiếu");
        ipLabel.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        ipLabel.setTextSize(12);

        adbHintView = new TextView(context);
        adbHintView.setText("adb connect ...:5555");
        adbHintView.setTextColor(Color.argb(180, 176, 184, 200));
        adbHintView.setTextSize(11);
        adbHintView.setPadding(0, UiHelper.dp(context, 2), 0, 0);

        ipTextCol.addView(ipLabel);
        ipTextCol.addView(adbHintView);
        ipCard.addView(ipTextCol, ipTextLp);

        ipValueView = new TextView(context);
        ipValueView.setText("...");
        ipValueView.setTextColor(Color.WHITE);
        ipValueView.setTextSize(16);
        ipValueView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        ipCard.addView(ipValueView);

        mainCard.addView(ipCard, ipLp);

        // 3. 2x2 Action Tiles (rounded 24dp)
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row1Lp.bottomMargin = UiHelper.dp(context, 12);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.bottomMargin = UiHelper.dp(context, 18);

        View tileReload = createActionTile(
                R.drawable.ic_reload, "#64D2FF",
                "Tải lại trang", "Làm mới mã QR & kết nối",
                null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        actions.onReloadPage();
                    }
                });

        View tileCache = createActionTile(
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
        autoStartBadge.setTextSize(11);
        autoStartBadge.setTypeface(Typeface.DEFAULT_BOLD);
        autoStartBadge.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 4),
                UiHelper.dp(context, 10), UiHelper.dp(context, 4));

        View tileAutoStart = createActionTile(
                R.drawable.ic_power, "#30D158",
                "Tự khởi động", "Mở app khi bật máy chiếu",
                autoStartBadge,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean next = !Prefs.isAutoStartEnabled(context);
                        Prefs.setAutoStartEnabled(context, next);
                        refreshAutoStartBadge();
                    }
                });

        View tileUpdate = createActionTile(
                R.drawable.ic_update, "#FF453A",
                "Kiểm tra cập nhật", "Tải & cài đặt bản mới qua Wi-Fi",
                null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hide();
                        actions.onCheckUpdates();
                    }
                });

        LinearLayout.LayoutParams leftTileLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftTileLp.rightMargin = UiHelper.dp(context, 6);
        LinearLayout.LayoutParams rightTileLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightTileLp.leftMargin = UiHelper.dp(context, 6);

        row1.addView(tileReload, leftTileLp);
        row1.addView(tileCache, rightTileLp);
        row2.addView(tileAutoStart, new LinearLayout.LayoutParams(leftTileLp));
        row2.addView(tileUpdate, new LinearLayout.LayoutParams(rightTileLp));

        mainCard.addView(row1, row1Lp);
        mainCard.addView(row2, row2Lp);

        // 4. Close Button (rounded 24dp)
        TextView closeBtn = new TextView(context);
        closeBtn.setId(View.generateViewId());
        closeBtn.setText("Đóng menu");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 12),
                UiHelper.dp(context, 24), UiHelper.dp(context, 12));
        UiHelper.applyFocusableTileStyle(closeBtn, context, 24,
                UiHelper.COLOR_SURFACE_GLASS, Color.argb(90, 255, 255, 255));
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.gravity = Gravity.CENTER_HORIZONTAL;
        mainCard.addView(closeBtn, closeLp);

        // Wire explicit D-Pad navigation so focus never escapes behind the overlay
        tileReload.setId(View.generateViewId());
        tileCache.setId(View.generateViewId());
        tileAutoStart.setId(View.generateViewId());
        tileUpdate.setId(View.generateViewId());

        tileReload.setNextFocusRightId(tileCache.getId());
        tileReload.setNextFocusLeftId(tileReload.getId());
        tileReload.setNextFocusUpId(tileReload.getId());
        tileReload.setNextFocusDownId(tileAutoStart.getId());

        tileCache.setNextFocusLeftId(tileReload.getId());
        tileCache.setNextFocusRightId(tileCache.getId());
        tileCache.setNextFocusUpId(tileCache.getId());
        tileCache.setNextFocusDownId(tileUpdate.getId());

        tileAutoStart.setNextFocusUpId(tileReload.getId());
        tileAutoStart.setNextFocusRightId(tileUpdate.getId());
        tileAutoStart.setNextFocusLeftId(tileAutoStart.getId());
        tileAutoStart.setNextFocusDownId(closeBtn.getId());

        tileUpdate.setNextFocusUpId(tileCache.getId());
        tileUpdate.setNextFocusLeftId(tileAutoStart.getId());
        tileUpdate.setNextFocusRightId(tileUpdate.getId());
        tileUpdate.setNextFocusDownId(closeBtn.getId());

        closeBtn.setNextFocusUpId(tileAutoStart.getId());
        closeBtn.setNextFocusDownId(closeBtn.getId());
        closeBtn.setNextFocusLeftId(closeBtn.getId());
        closeBtn.setNextFocusRightId(closeBtn.getId());

        firstTile = tileReload;
        overlayRoot.addView(mainCard, cardLp);
    }

    private View createActionTile(int iconRes, String iconTintHex, String title, String subtitle,
                                  TextView optionalBadge, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(UiHelper.dp(context, 18), UiHelper.dp(context, 16),
                UiHelper.dp(context, 18), UiHelper.dp(context, 16));
        UiHelper.applyFocusableTileStyle(tile, context, 24,
                UiHelper.COLOR_SURFACE_GLASS, Color.argb(64, 255, 255, 255));
        tile.setOnClickListener(listener);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = UiHelper.dp(context, 12);

        FrameLayout iconBox = new FrameLayout(context);
        UiHelper.applyGlassCardStyle(iconBox, context, 14, Color.argb(72, 0, 0, 0), 2);
        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.parseColor(iconTintHex));
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(
                UiHelper.dp(context, 20), UiHelper.dp(context, 20), Gravity.CENTER);
        iconBox.addView(iv, ivLp);

        LinearLayout.LayoutParams iconBoxLp = new LinearLayout.LayoutParams(
                UiHelper.dp(context, 38), UiHelper.dp(context, 38));
        topRow.addView(iconBox, iconBoxLp);

        if (optionalBadge != null) {
            View spacer = new View(context);
            topRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            topRow.addView(optionalBadge);
        }

        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(15);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subTv = new TextView(context);
        subTv.setText(subtitle);
        subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        subTv.setTextSize(12);
        subTv.setPadding(0, UiHelper.dp(context, 3), 0, 0);

        tile.addView(topRow, topLp);
        tile.addView(titleTv);
        tile.addView(subTv);
        return tile;
    }

    private void refreshNetworkInfo() {
        String ip = UiHelper.getWifiIpAddress(context);
        ipValueView.setText(ip);
        if (ip.contains(".")) {
            adbHintView.setText("Cài qua mạng: adb connect " + ip + ":5555");
        } else {
            adbHintView.setText("Kiểm tra lại kết nối Wi-Fi / LAN trên máy chiếu");
        }
    }

    private void refreshAutoStartBadge() {
        boolean on = Prefs.isAutoStartEnabled(context);
        autoStartBadge.setText(on ? "ĐANG BẬT" : "ĐANG TẮT");
        autoStartBadge.setTextColor(on ? UiHelper.COLOR_GREEN : UiHelper.COLOR_TEXT_MUTED);
        UiHelper.applyGlassCardStyle(autoStartBadge, context, 12,
                on ? Color.argb(46, 48, 209, 88) : UiHelper.COLOR_SURFACE_GLASS, 0);
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
        mainCard.setScaleX(0.94f);
        mainCard.setScaleY(0.94f);

        overlayRoot.animate().alpha(1f).setDuration(220).start();
        mainCard.animate().scaleX(1f).scaleY(1f).setDuration(240)
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

        overlayRoot.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
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
