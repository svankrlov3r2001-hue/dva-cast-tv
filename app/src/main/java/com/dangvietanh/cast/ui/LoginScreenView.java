package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;

import java.security.SecureRandom;
import java.util.Locale;

public class LoginScreenView extends FrameLayout {

    public interface Listener {
        void onLoginSuccess(Models.User user, String token);
        void onOpenSettingsMenu();
    }

    private static final long POLL_INTERVAL_MS = 2000L;
    private static final int QR_TTL_SEC = 180;

    private final ApiClient apiClient;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();

    private ImageView qrImageView;
    private LinearLayout qrBox;
    private LinearLayout claimedBox;
    private ImageView claimedAvatarIv;
    private TextView claimedNameTv;
    private TextView claimedEmailTv;
    private TextView codeTv;
    private TextView statusTv;
    private UiHelper.SpinnerView statusSpinner;
    private TextView ipBadgeTv;
    private TextView btnRefreshQr;
    private TextView btnSettings;

    private String currentQrKey = "";
    private long expiresAtMs = 0L;
    private boolean pollingActive = false;
    private boolean pollInFlight = false;
    private boolean isClaimedState = false;

    public LoginScreenView(Context context, ApiClient apiClient, Listener listener) {
        super(context);
        this.apiClient = apiClient;
        this.listener = listener;
        buildUi(context);
    }

    private void buildUi(Context ctx) {
        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Full-bleed poster backdrop + ambient cinema gradient scrim
        ImageView bgPoster = new ImageView(ctx);
        bgPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgPoster.setImageResource(R.drawable.default_poster);
        bgPoster.setAlpha(0.32f);
        UiHelper.setBackdropBlur(bgPoster, true);
        addView(bgPoster, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(ctx);
        scrim.setBackground(UiHelper.createOverlayBackdropDrawable());
        addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels > 0 ? dm.widthPixels : UiHelper.dp(ctx, 960);

        ScrollView scroll = new ScrollView(ctx);
        UiHelper.makeNonFocusable(scroll);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout outerCol = new LinearLayout(ctx);
        outerCol.setOrientation(LinearLayout.VERTICAL);
        outerCol.setGravity(Gravity.CENTER_HORIZONTAL);
        outerCol.setPadding(UiHelper.dp(ctx, 44), UiHelper.dp(ctx, 28),
                UiHelper.dp(ctx, 44), UiHelper.dp(ctx, 28));
        scroll.addView(outerCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // Top Brand Row
        LinearLayout topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = UiHelper.dp(ctx, 22);

        ImageView logoIv = new ImageView(ctx);
        logoIv.setImageResource(R.drawable.splash_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 42), UiHelper.dp(ctx, 42));
        logoLp.rightMargin = UiHelper.dp(ctx, 12);
        topBar.addView(logoIv, logoLp);

        TextView brandTv = new TextView(ctx);
        brandTv.setText("Đặng Việt Anh");
        brandTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        brandTv.setTextSize(20f);
        brandTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topBar.addView(brandTv, brandLp);

        ipBadgeTv = new TextView(ctx);
        ipBadgeTv.setText("Mạng: " + UiHelper.getWifiIpAddress(ctx));
        ipBadgeTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        ipBadgeTv.setTextSize(12.5f);
        ipBadgeTv.setPadding(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 8));
        UiHelper.applySubtleGlassPill(ipBadgeTv, ctx, 24);
        topBar.addView(ipBadgeTv);

        outerCol.addView(topBar, topLp);

        // Center Frosted Glass Card (sleek 20dp radius, crisp border)
        int cardW = Math.min(UiHelper.dp(ctx, 760), Math.round(screenW * 0.88f));
        LinearLayout glassCard = new LinearLayout(ctx);
        glassCard.setOrientation(LinearLayout.HORIZONTAL);
        glassCard.setGravity(Gravity.CENTER_VERTICAL);
        glassCard.setPadding(UiHelper.dp(ctx, 26), UiHelper.dp(ctx, 24),
                UiHelper.dp(ctx, 26), UiHelper.dp(ctx, 24));
        UiHelper.applyGlassCardStyle(glassCard, ctx, 20, UiHelper.COLOR_SURFACE_GLASS, 16);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardW, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        outerCol.addView(glassCard, cardLp);

        // Left Column: QR Code or Claimed User Card
        LinearLayout leftCol = new LinearLayout(ctx);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                UiHelper.dp(ctx, 232), LinearLayout.LayoutParams.WRAP_CONTENT);
        leftLp.rightMargin = UiHelper.dp(ctx, 26);
        glassCard.addView(leftCol, leftLp);

        // White rounded QR frame
        qrBox = new LinearLayout(ctx);
        qrBox.setOrientation(LinearLayout.VERTICAL);
        qrBox.setGravity(Gravity.CENTER);
        qrBox.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 12));
        GradientDrawable whiteFrame = new GradientDrawable();
        whiteFrame.setColor(Color.WHITE);
        whiteFrame.setCornerRadius(UiHelper.dp(ctx, 16));
        qrBox.setBackground(whiteFrame);
        qrBox.setElevation(UiHelper.dp(ctx, 10));

        qrImageView = new ImageView(ctx);
        qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrBox.addView(qrImageView, new LinearLayout.LayoutParams(UiHelper.dp(ctx, 184), UiHelper.dp(ctx, 184)));
        leftCol.addView(qrBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Claimed user state (shown right after phone scans QR)
        claimedBox = new LinearLayout(ctx);
        claimedBox.setOrientation(LinearLayout.VERTICAL);
        claimedBox.setGravity(Gravity.CENTER);
        claimedBox.setPadding(UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 20), UiHelper.dp(ctx, 24));
        UiHelper.applyGlassCardStyle(claimedBox, ctx, 28, UiHelper.COLOR_SURFACE_CARD, 14);
        claimedBox.setVisibility(GONE);

        claimedAvatarIv = new ImageView(ctx);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 84), UiHelper.dp(ctx, 84));
        avLp.bottomMargin = UiHelper.dp(ctx, 14);
        claimedBox.addView(claimedAvatarIv, avLp);

        claimedNameTv = new TextView(ctx);
        claimedNameTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        claimedNameTv.setTextSize(17f);
        claimedNameTv.setTypeface(null, Typeface.BOLD);
        claimedNameTv.setGravity(Gravity.CENTER);
        claimedBox.addView(claimedNameTv);

        claimedEmailTv = new TextView(ctx);
        claimedEmailTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        claimedEmailTv.setTextSize(12.5f);
        claimedEmailTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        emLp.topMargin = UiHelper.dp(ctx, 4);
        claimedBox.addView(claimedEmailTv, emLp);

        TextView claimedHintTv = new TextView(ctx);
        claimedHintTv.setText("Hãy nhấn Xác nhận trên điện thoại của bạn");
        claimedHintTv.setTextColor(UiHelper.COLOR_GREEN);
        claimedHintTv.setTextSize(12.5f);
        claimedHintTv.setTypeface(null, Typeface.BOLD);
        claimedHintTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chLp.topMargin = UiHelper.dp(ctx, 12);
        claimedBox.addView(claimedHintTv, chLp);

        leftCol.addView(claimedBox, new LinearLayout.LayoutParams(UiHelper.dp(ctx, 242), UiHelper.dp(ctx, 242)));

        TextView qrCaptionTv = new TextView(ctx);
        qrCaptionTv.setText("Quét bằng ứng dụng Đặng Việt Anh hoặc Camera");
        qrCaptionTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        qrCaptionTv.setTextSize(12.5f);
        qrCaptionTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = UiHelper.dp(ctx, 14);
        leftCol.addView(qrCaptionTv, capLp);

        // Right Column: Heading, 9-Digit Device Code, Steps, Actions
        LinearLayout rightCol = new LinearLayout(ctx);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.CENTER_VERTICAL);
        glassCard.addView(rightCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView badgeTv = new TextView(ctx);
        badgeTv.setText("ĐĂNG NHẬP THIẾT BỊ");
        badgeTv.setTextColor(UiHelper.COLOR_ACCENT_RED);
        badgeTv.setTextSize(11.5f);
        badgeTv.setTypeface(null, Typeface.BOLD);
        badgeTv.setLetterSpacing(0.08f);
        rightCol.addView(badgeTv);

        TextView headingTv = new TextView(ctx);
        headingTv.setText("Kết nối tài khoản lên màn hình TV");
        headingTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        headingTv.setTextSize(25f);
        headingTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams hdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hdLp.topMargin = UiHelper.dp(ctx, 6);
        rightCol.addView(headingTv, hdLp);

        TextView descTv = new TextView(ctx);
        descTv.setText("Mở dangvietanh.com/login hoặc ứng dụng Đặng Việt Anh trên điện thoại, quét mã QR bên trái hoặc nhập mã thiết bị 9 số bên dưới:");
        descTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        descTv.setTextSize(13.5f);
        descTv.setLineSpacing(UiHelper.dp(ctx, 3), 1f);
        LinearLayout.LayoutParams dsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dsLp.topMargin = UiHelper.dp(ctx, 10);
        dsLp.bottomMargin = UiHelper.dp(ctx, 18);
        rightCol.addView(descTv, dsLp);

        // 9-Digit Device Code Frosted Box
        LinearLayout codePill = new LinearLayout(ctx);
        codePill.setOrientation(LinearLayout.VERTICAL);
        codePill.setGravity(Gravity.CENTER_VERTICAL);
        codePill.setPadding(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 11));
        UiHelper.applyGlassCardStyle(codePill, ctx, 14, UiHelper.COLOR_SURFACE_CARD, 10);

        TextView codeLabel = new TextView(ctx);
        codeLabel.setText("MÃ THIẾT BỊ");
        codeLabel.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        codeLabel.setTextSize(10.5f);
        codeLabel.setLetterSpacing(0.10f);
        codeLabel.setTypeface(null, Typeface.BOLD);
        codePill.addView(codeLabel);

        codeTv = new TextView(ctx);
        codeTv.setText("--- --- ---");
        codeTv.setTextColor(Color.WHITE);
        codeTv.setTextSize(28f);
        codeTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeTv.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = UiHelper.dp(ctx, 2);
        codePill.addView(codeTv, cLp);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pillLp.bottomMargin = UiHelper.dp(ctx, 14);
        rightCol.addView(codePill, pillLp);

        // Live Polling Status Row
        LinearLayout statusRow = new LinearLayout(ctx);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.bottomMargin = UiHelper.dp(ctx, 16);

        statusSpinner = new UiHelper.SpinnerView(ctx);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 18));
        spLp.rightMargin = UiHelper.dp(ctx, 9);
        statusRow.addView(statusSpinner, spLp);

        statusTv = new TextView(ctx);
        statusTv.setText("Đang khởi tạo mã xác thực...");
        statusTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        statusTv.setTextSize(12.5f);
        statusRow.addView(statusTv);

        rightCol.addView(statusRow, srLp);

        // Focusable Actions Row
        rightCol.setClipChildren(false);
        rightCol.setClipToPadding(false);
        glassCard.setClipChildren(false);
        glassCard.setClipToPadding(false);
        glassCard.setClipToOutline(false);
        outerCol.setClipChildren(false);
        outerCol.setClipToPadding(false);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        btnRow.setClipChildren(false);
        btnRow.setClipToPadding(false);
        btnRow.setPadding(UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 4));

        btnRefreshQr = new TextView(ctx);
        btnRefreshQr.setId(View.generateViewId());
        btnRefreshQr.setText("Làm mới mã QR");
        btnRefreshQr.setTextColor(Color.WHITE);
        btnRefreshQr.setTextSize(13f);
        btnRefreshQr.setTypeface(null, Typeface.BOLD);
        btnRefreshQr.setGravity(Gravity.CENTER);
        btnRefreshQr.setPadding(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 9));
        UiHelper.applyPrimaryGradientButton(btnRefreshQr, ctx, 16);
        btnRefreshQr.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                regenerateAndStartPolling();
            }
        });
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rLp.rightMargin = UiHelper.dp(ctx, 12);
        btnRow.addView(btnRefreshQr, rLp);

        btnSettings = new TextView(ctx);
        btnSettings.setId(View.generateViewId());
        btnSettings.setText("Cài đặt & Cập nhật");
        btnSettings.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        btnSettings.setTextSize(13f);
        btnSettings.setTypeface(null, Typeface.BOLD);
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setPadding(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 9));
        UiHelper.applyFocusableGlassButton(btnSettings, ctx, 16, false);
        btnSettings.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenSettingsMenu();
            }
        });
        btnRow.addView(btnSettings);

        btnRefreshQr.setNextFocusRightId(btnSettings.getId());
        btnRefreshQr.setNextFocusLeftId(btnRefreshQr.getId());
        btnRefreshQr.setNextFocusUpId(btnRefreshQr.getId());
        btnRefreshQr.setNextFocusDownId(btnRefreshQr.getId());

        btnSettings.setNextFocusLeftId(btnRefreshQr.getId());
        btnSettings.setNextFocusRightId(btnSettings.getId());
        btnSettings.setNextFocusUpId(btnSettings.getId());
        btnSettings.setNextFocusDownId(btnSettings.getId());
        rightCol.addView(btnRow);
    }

    public void start() {
        ipBadgeTv.setText("Mạng: " + UiHelper.getWifiIpAddress(getContext()));
        regenerateAndStartPolling();
        requestInitialFocus();
    }

    public void stop() {
        pollingActive = false;
        handler.removeCallbacksAndMessages(null);
        if (!currentQrKey.isEmpty()) {
            apiClient.cancelQrLogin(currentQrKey);
            currentQrKey = "";
        }
    }

    public void requestInitialFocus() {
        UiHelper.postTvFocus(btnRefreshQr);
    }

    public boolean hasValidFocus() {
        return UiHelper.isValidInteractiveFocus(findFocus(), this);
    }

    public boolean ensureValidFocus() {
        if (getVisibility() != VISIBLE) return false;
        return UiHelper.requestTvFocus(btnRefreshQr);
    }

    private void regenerateAndStartPolling() {
        pollingActive = false;
        handler.removeCallbacksAndMessages(null);
        if (!currentQrKey.isEmpty()) {
            apiClient.cancelQrLogin(currentQrKey);
        }

        int num = 100000000 + random.nextInt(900000000);
        currentQrKey = String.valueOf(num);
        isClaimedState = false;
        expiresAtMs = System.currentTimeMillis() + (QR_TTL_SEC * 1000L);

        qrBox.setVisibility(VISIBLE);
        claimedBox.setVisibility(GONE);
        statusSpinner.setVisibility(VISIBLE);
        statusSpinner.setSpinning(true);

        codeTv.setText(formatCode(currentQrKey));
        String qrPayload = "https://dangvietanh.com/?backQR=" + currentQrKey;
        Bitmap bmp = UiHelper.generateQrBitmap(qrPayload, UiHelper.dp(getContext(), 210));
        if (bmp != null) {
            qrImageView.setImageBitmap(bmp);
        }

        pollingActive = true;
        pollInFlight = false;
        updateCountdownUi();
        schedulePoll(350L);
        scheduleCountdownTick();
    }

    private void schedulePoll(long delayMs) {
        if (!pollingActive) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performPoll();
            }
        }, delayMs);
    }

    private void scheduleCountdownTick() {
        if (!pollingActive) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!pollingActive) return;
                updateCountdownUi();
                scheduleCountdownTick();
            }
        }, 1000L);
    }

    private void updateCountdownUi() {
        long remMs = expiresAtMs - System.currentTimeMillis();
        if (remMs <= 0) {
            onExpiredState("Mã thiết bị đã hết hạn. Đang tạo mã mới...");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getVisibility() == VISIBLE) {
                        regenerateAndStartPolling();
                    }
                }
            }, 1400L);
            return;
        }
        int totalSec = (int) (remMs / 1000L);
        String mmss = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60);
        if (isClaimedState) {
            statusTv.setTextColor(UiHelper.COLOR_GREEN);
            statusTv.setText("Đã quét mã! Chờ bạn xác nhận trên điện thoại (" + mmss + ")");
        } else {
            statusTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
            statusTv.setText("Đang chờ quét mã QR hoặc nhập mã trên Web (" + mmss + ")");
        }
    }

    private void performPoll() {
        if (!pollingActive || pollInFlight || currentQrKey.isEmpty()) return;
        final String keySnapshot = currentQrKey;
        pollInFlight = true;
        apiClient.pollQrLogin(keySnapshot, new ApiClient.Callback<Models.QrPollResponse>() {
            @Override
            public void onSuccess(Models.QrPollResponse r) {
                pollInFlight = false;
                if (!pollingActive || !keySnapshot.equals(currentQrKey)) return;

                if ("approved".equalsIgnoreCase(r.state) && r.token != null && !r.token.isEmpty()) {
                    pollingActive = false;
                    handler.removeCallbacksAndMessages(null);
                    statusSpinner.setSpinning(false);
                    statusSpinner.setVisibility(GONE);
                    statusTv.setTextColor(UiHelper.COLOR_GREEN);
                    statusTv.setText("Đăng nhập thành công! Đang vào trang chủ...");
                    apiClient.setAuthToken(r.token);
                    if (r.user != null) {
                        apiClient.saveUser(r.user);
                    }
                    if (listener != null) {
                        listener.onLoginSuccess(r.user, r.token);
                    }
                    return;
                }

                if ("claimed".equalsIgnoreCase(r.state)) {
                    if (!isClaimedState) {
                        isClaimedState = true;
                        qrBox.setVisibility(GONE);
                        claimedBox.setVisibility(VISIBLE);
                        claimedNameTv.setText(r.claimedName != null && !r.claimedName.isEmpty()
                                ? r.claimedName : "Thành viên Đặng Việt Anh");
                        claimedEmailTv.setText(r.claimedEmail != null ? r.claimedEmail : "");
                        UiHelper.safeLoadAvatar(claimedAvatarIv, r.claimedAvatar);
                        updateCountdownUi();
                    }
                    schedulePoll(POLL_INTERVAL_MS);
                    return;
                }

                if ("expired".equalsIgnoreCase(r.state)) {
                    onExpiredState("Mã đã hết hạn. Vui lòng nhấn Làm mới mã QR.");
                    return;
                }

                if ("error".equalsIgnoreCase(r.state)) {
                    statusTv.setTextColor(UiHelper.COLOR_ACCENT_RED);
                    statusTv.setText(r.error != null && !r.error.isEmpty() ? r.error : "Lỗi xác thực, thử lại...");
                    schedulePoll(3500L);
                    return;
                }

                isClaimedState = false;
                if (qrBox.getVisibility() != VISIBLE) {
                    qrBox.setVisibility(VISIBLE);
                    claimedBox.setVisibility(GONE);
                }
                schedulePoll(POLL_INTERVAL_MS);
            }

            @Override
            public void onError(String message, int httpCode) {
                pollInFlight = false;
                if (!pollingActive || !keySnapshot.equals(currentQrKey)) return;
                statusTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                statusTv.setText("Đang kết nối lại máy chủ xác thực...");
                schedulePoll(3200L);
            }
        });
    }

    private void onExpiredState(String msg) {
        pollingActive = false;
        statusSpinner.setSpinning(false);
        statusSpinner.setVisibility(GONE);
        statusTv.setTextColor(UiHelper.COLOR_GOLD);
        statusTv.setText(msg);
    }

    private static String formatCode(String raw) {
        if (raw == null || raw.length() != 9) return raw != null ? raw : "--- --- ---";
        return raw.substring(0, 3) + " " + raw.substring(3, 6) + " " + raw.substring(6, 9);
    }
}
