package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.Models;

public class AccountProfileModal {

    public interface Actions {
        void onOpenTvSettings();
        void onCheckUpdates();
        void onLogout();
    }

    private final Context context;
    private final View backgroundToBlur;
    private final Actions actions;

    private final FrameLayout rootOverlay;
    private final LinearLayout mainCard;
    private final ImageView avatarIv;
    private final TextView nameTv;
    private final TextView emailTv;
    private final TextView rolePillTv;
    private final TextView deviceTv;
    private final LinearLayout btnSettings;
    private final LinearLayout btnUpdate;
    private final LinearLayout btnLogout;
    private final ImageView btnClose;

    private Runnable onVisibilityChanged;
    private boolean showing = false;

    public AccountProfileModal(Context context, View backgroundToBlur, Actions actions) {
        this.context = context;
        this.backgroundToBlur = backgroundToBlur;
        this.actions = actions;

        rootOverlay = new FrameLayout(context);
        rootOverlay.setBackground(UiHelper.createOverlayBackdropDrawable());
        rootOverlay.setVisibility(View.GONE);
        rootOverlay.setClickable(true);
        UiHelper.makeNonFocusable(rootOverlay);
        UiHelper.setFocusBlocked(rootOverlay, true);
        rootOverlay.setClipChildren(false);
        rootOverlay.setClipToPadding(false);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels > 0 ? dm.widthPixels : UiHelper.dp(context, 960);
        int cardW = Math.min(UiHelper.dp(context, 420), Math.round(screenW * 0.80f));

        mainCard = new LinearLayout(context);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        mainCard.setGravity(Gravity.CENTER_HORIZONTAL);
        mainCard.setClipChildren(false);
        mainCard.setClipToPadding(false);
        mainCard.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 22),
                UiHelper.dp(context, 24), UiHelper.dp(context, 22));
        UiHelper.applyGlassCardStyle(mainCard, context, 20, UiHelper.COLOR_SURFACE_GLASS, 18);
        mainCard.setClipToOutline(false);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardW, FrameLayout.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        rootOverlay.addView(mainCard, cardLp);

        // Header Row
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setClipChildren(false);
        headerRow.setClipToPadding(false);
        LinearLayout.LayoutParams hdrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hdrLp.bottomMargin = UiHelper.dp(context, 20);

        TextView hdrTitle = new TextView(context);
        hdrTitle.setText("Tài khoản thành viên");
        hdrTitle.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        hdrTitle.setTextSize(19f);
        hdrTitle.setTypeface(null, Typeface.BOLD);
        headerRow.addView(hdrTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnClose = new ImageView(context);
        btnClose.setId(View.generateViewId());
        btnClose.setImageResource(R.drawable.ic_close);
        btnClose.setColorFilter(UiHelper.COLOR_TEXT_PRIMARY, PorterDuff.Mode.SRC_IN);
        btnClose.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 8), UiHelper.dp(context, 8), UiHelper.dp(context, 8));
        UiHelper.applyFocusableGlassButton(btnClose, context, 20, false);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        headerRow.addView(btnClose, new LinearLayout.LayoutParams(UiHelper.dp(context, 38), UiHelper.dp(context, 38)));
        mainCard.addView(headerRow, hdrLp);

        avatarIv = new ImageView(context);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 82), UiHelper.dp(context, 82));
        avLp.bottomMargin = UiHelper.dp(context, 12);
        mainCard.addView(avatarIv, avLp);

        nameTv = new TextView(context);
        nameTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        nameTv.setTextSize(20f);
        nameTv.setTypeface(null, Typeface.BOLD);
        nameTv.setGravity(Gravity.CENTER);
        mainCard.addView(nameTv);

        emailTv = new TextView(context);
        emailTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        emailTv.setTextSize(13f);
        emailTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        emLp.topMargin = UiHelper.dp(context, 4);
        mainCard.addView(emailTv, emLp);

        rolePillTv = new TextView(context);
        rolePillTv.setTextSize(12f);
        rolePillTv.setTypeface(null, Typeface.BOLD);
        rolePillTv.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 5), UiHelper.dp(context, 14), UiHelper.dp(context, 5));
        UiHelper.applySubtleGlassPill(rolePillTv, context, 20);
        LinearLayout.LayoutParams rpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rpLp.topMargin = UiHelper.dp(context, 10);
        mainCard.addView(rolePillTv, rpLp);

        deviceTv = new TextView(context);
        deviceTv.setText("Thiết bị: " + Build.MANUFACTURER + " " + Build.MODEL);
        deviceTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        deviceTv.setTextSize(12f);
        LinearLayout.LayoutParams dvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dvLp.topMargin = UiHelper.dp(context, 8);
        dvLp.bottomMargin = UiHelper.dp(context, 22);
        mainCard.addView(deviceTv, dvLp);

        btnSettings = buildActionRow(R.drawable.ic_settings, "Cài đặt TV & Lịch sử phiên bản", false);
        btnSettings.setId(View.generateViewId());
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
                if (AccountProfileModal.this.actions != null) AccountProfileModal.this.actions.onOpenTvSettings();
            }
        });
        mainCard.addView(btnSettings);

        btnUpdate = buildActionRow(R.drawable.ic_refresh, "Kiểm tra cập nhật ứng dụng", false);
        btnUpdate.setId(View.generateViewId());
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
                if (AccountProfileModal.this.actions != null) AccountProfileModal.this.actions.onCheckUpdates();
            }
        });
        mainCard.addView(btnUpdate);

        btnLogout = buildActionRow(R.drawable.ic_logout, "Đăng xuất tài khoản", true);
        btnLogout.setId(View.generateViewId());
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
                if (AccountProfileModal.this.actions != null) AccountProfileModal.this.actions.onLogout();
            }
        });
        mainCard.addView(btnLogout);

        // Trap D-Pad cleanly inside modal
        btnClose.setNextFocusDownId(btnSettings.getId());
        btnClose.setNextFocusUpId(btnClose.getId());
        btnClose.setNextFocusLeftId(btnClose.getId());
        btnClose.setNextFocusRightId(btnClose.getId());

        btnSettings.setNextFocusUpId(btnClose.getId());
        btnSettings.setNextFocusDownId(btnUpdate.getId());
        btnSettings.setNextFocusLeftId(btnSettings.getId());
        btnSettings.setNextFocusRightId(btnSettings.getId());

        btnUpdate.setNextFocusUpId(btnSettings.getId());
        btnUpdate.setNextFocusDownId(btnLogout.getId());
        btnUpdate.setNextFocusLeftId(btnUpdate.getId());
        btnUpdate.setNextFocusRightId(btnUpdate.getId());

        btnLogout.setNextFocusUpId(btnUpdate.getId());
        btnLogout.setNextFocusDownId(btnClose.getId());
        btnLogout.setNextFocusLeftId(btnLogout.getId());
        btnLogout.setNextFocusRightId(btnLogout.getId());
    }

    private LinearLayout buildActionRow(int iconRes, String title, boolean accent) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(context, 18), UiHelper.dp(context, 13),
                UiHelper.dp(context, 18), UiHelper.dp(context, 13));
        UiHelper.applyFocusableGlassButton(row, context, 24, accent);

        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 20), UiHelper.dp(context, 20));
        iLp.rightMargin = UiHelper.dp(context, 12);
        row.addView(iv, iLp);

        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14.5f);
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(context, 10);
        row.setLayoutParams(lp);
        return row;
    }

    public View getView() {
        return rootOverlay;
    }

    public boolean isShowing() {
        return showing;
    }

    public void setOnVisibilityChangedListener(Runnable listener) {
        this.onVisibilityChanged = listener;
    }

    public boolean hasValidFocus() {
        return UiHelper.isValidInteractiveFocus(rootOverlay.findFocus(), rootOverlay);
    }

    public boolean ensureValidFocus() {
        if (!showing) return false;
        return UiHelper.requestTvFocus(btnSettings);
    }

    public void show(Models.User user) {
        if (user != null) {
            nameTv.setText(user.name != null && !user.name.isEmpty() ? user.name : user.username);
            emailTv.setText(user.email != null && !user.email.isEmpty() ? user.email : "ID #" + user.userId);
            boolean isVip = "vip".equalsIgnoreCase(user.role) || "admin".equalsIgnoreCase(user.role);
            rolePillTv.setText(user.role != null && !user.role.isEmpty()
                    ? user.role.toUpperCase() : "THÀNH VIÊN");
            rolePillTv.setTextColor(isVip ? UiHelper.COLOR_GOLD : UiHelper.COLOR_TEXT_PRIMARY);
            UiHelper.safeLoadAvatar(avatarIv, user.avatarUrl);
        } else {
            nameTv.setText("Thành viên Đặng Việt Anh");
            emailTv.setText("");
            rolePillTv.setText("THÀNH VIÊN");
            rolePillTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            avatarIv.setImageResource(R.drawable.ic_person);
        }

        showing = true;
        rootOverlay.animate().cancel();
        mainCard.animate().cancel();
        UiHelper.setBackdropBlur(backgroundToBlur, true);
        rootOverlay.setAlpha(0f);
        rootOverlay.setVisibility(View.VISIBLE);
        UiHelper.setFocusBlocked(rootOverlay, false);
        if (onVisibilityChanged != null) onVisibilityChanged.run();
        mainCard.setScaleX(0.94f);
        mainCard.setScaleY(0.94f);
        rootOverlay.animate().alpha(1f).setDuration(200).setListener(null).start();
        mainCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        UiHelper.postTvFocus(btnSettings);
    }

    public void hide() {
        if (!showing) return;
        showing = false;
        rootOverlay.animate().cancel();
        UiHelper.setFocusBlocked(rootOverlay, true);
        UiHelper.setBackdropBlur(backgroundToBlur, false);
        if (onVisibilityChanged != null) onVisibilityChanged.run();
        rootOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!showing) {
                            rootOverlay.setVisibility(View.GONE);
                        }
                    }
                })
                .start();
    }
}
