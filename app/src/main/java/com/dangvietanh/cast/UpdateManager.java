package com.dangvietanh.cast;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class UpdateManager {

    private static final String VERSION_JSON_URL = "https://dangvietanh.com/dva_cast/app-version.json";
    private static final String DEFAULT_APK_URL = "https://dangvietanh.com/dva_cast/dva_cast.apk";

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout updateOverlay;
    private LinearLayout mainCard;

    // Prompt section
    private LinearLayout promptSection;
    private TextView promptTitleTv;
    private TextView promptVersionTv;
    private TextView promptChangelogTv;
    private TextView btnUpdateNow;
    private TextView btnLater;

    // Progress section
    private LinearLayout progressSection;
    private TextView progressStatusTv;
    private View progressFillView;
    private FrameLayout progressTrack;
    private TextView statPercentVal;
    private TextView statBytesSub;
    private TextView statSpeedVal;
    private TextView statEtaVal;
    private TextView btnCancelDownload;

    private volatile boolean isShowing = false;
    private volatile boolean isDownloading = false;
    private volatile boolean cancelRequested = false;
    private boolean pendingInstallAfterPerm = false;
    private String pendingApkUrl = DEFAULT_APK_URL;
    private String pendingVersionName = "";
    private String pendingChangelog = "";
    private View previouslyFocused;

    public UpdateManager(Activity activity) {
        this.activity = activity;
        buildUi();
    }

    public View getView() {
        return updateOverlay;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public long getLocalVersionCode() {
        try {
            PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Exception e) {
            return 1L;
        }
    }

    public String getLocalVersionName() {
        try {
            PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    private void buildUi() {
        updateOverlay = new FrameLayout(activity);
        updateOverlay.setVisibility(View.GONE);
        updateOverlay.setBackground(UiHelper.createCastBackgroundDrawable());
        updateOverlay.setClickable(true);

        mainCard = new LinearLayout(activity);
        mainCard.setOrientation(LinearLayout.VERTICAL);
        mainCard.setPadding(UiHelper.dp(activity, 32), UiHelper.dp(activity, 30),
                UiHelper.dp(activity, 32), UiHelper.dp(activity, 30));
        UiHelper.applyGlassCardStyle(mainCard, activity, 40, UiHelper.COLOR_SURFACE_GLASS, 24);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                UiHelper.dp(activity, 620),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        cardLp.setMargins(UiHelper.dp(activity, 24), UiHelper.dp(activity, 20),
                UiHelper.dp(activity, 24), UiHelper.dp(activity, 20));

        // Top Brand Row
        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandLp.bottomMargin = UiHelper.dp(activity, 18);

        ImageView logoIv = new ImageView(activity);
        logoIv.setImageResource(R.drawable.splash_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                UiHelper.dp(activity, 44), UiHelper.dp(activity, 44));
        logoLp.rightMargin = UiHelper.dp(activity, 12);
        brandRow.addView(logoIv, logoLp);

        LinearLayout brandTextCol = new LinearLayout(activity);
        brandTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView brandName = new TextView(activity);
        brandName.setText("Đặng Việt Anh · Cập nhật hệ thống");
        brandName.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        brandName.setTextSize(15);
        brandName.setTypeface(Typeface.DEFAULT_BOLD);
        TextView brandSub = new TextView(activity);
        brandSub.setText("Tự động cập nhật phiên bản mới");
        brandSub.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        brandSub.setTextSize(12);
        brandTextCol.addView(brandName);
        brandTextCol.addView(brandSub);
        brandRow.addView(brandTextCol);

        mainCard.addView(brandRow, brandLp);

        // === SECTION 1: UPDATE PROMPT ===
        promptSection = new LinearLayout(activity);
        promptSection.setOrientation(LinearLayout.VERTICAL);

        promptTitleTv = new TextView(activity);
        promptTitleTv.setText("Có bản cập nhật mới — Bấm OK để cập nhật");
        promptTitleTv.setTextColor(Color.WHITE);
        promptTitleTv.setTextSize(21);
        promptTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptLp.bottomMargin = UiHelper.dp(activity, 14);
        promptSection.addView(promptTitleTv, ptLp);

        // Info sub-card (rounded 24dp)
        LinearLayout infoCard = new LinearLayout(activity);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 16),
                UiHelper.dp(activity, 20), UiHelper.dp(activity, 16));
        UiHelper.applyGlassCardStyle(infoCard, activity, 24, UiHelper.COLOR_SURFACE_GLASS, 10);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        icLp.bottomMargin = UiHelper.dp(activity, 20);

        promptVersionTv = new TextView(activity);
        promptVersionTv.setTextColor(Color.parseColor("#64D2FF"));
        promptVersionTv.setTextSize(14);
        promptVersionTv.setTypeface(Typeface.DEFAULT_BOLD);
        promptVersionTv.setPadding(0, 0, 0, UiHelper.dp(activity, 6));

        promptChangelogTv = new TextView(activity);
        promptChangelogTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        promptChangelogTv.setTextSize(13);
        promptChangelogTv.setLineSpacing(UiHelper.dp(activity, 4), 1f);

        infoCard.addView(promptVersionTv);
        infoCard.addView(promptChangelogTv);
        promptSection.addView(infoCard, icLp);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        btnUpdateNow = new TextView(activity);
        btnUpdateNow.setId(View.generateViewId());
        btnUpdateNow.setText("Cập nhật ngay (OK)");
        btnUpdateNow.setTextColor(Color.WHITE);
        btnUpdateNow.setTextSize(15);
        btnUpdateNow.setTypeface(Typeface.DEFAULT_BOLD);
        btnUpdateNow.setGravity(Gravity.CENTER);
        btnUpdateNow.setPadding(UiHelper.dp(activity, 18), UiHelper.dp(activity, 14),
                UiHelper.dp(activity, 18), UiHelper.dp(activity, 14));
        UiHelper.applyFocusableTileStyle(btnUpdateNow, activity, 24,
                UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);
        btnUpdateNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownloadAndInstall();
            }
        });

        btnLater = new TextView(activity);
        btnLater.setId(View.generateViewId());
        btnLater.setText("Để sau");
        btnLater.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        btnLater.setTextSize(15);
        btnLater.setTypeface(Typeface.DEFAULT_BOLD);
        btnLater.setGravity(Gravity.CENTER);
        btnLater.setPadding(UiHelper.dp(activity, 18), UiHelper.dp(activity, 14),
                UiHelper.dp(activity, 18), UiHelper.dp(activity, 14));
        UiHelper.applyFocusableTileStyle(btnLater, activity, 24,
                UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);
        btnLater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        btnUpdateNow.setNextFocusRightId(btnLater.getId());
        btnUpdateNow.setNextFocusLeftId(btnUpdateNow.getId());
        btnUpdateNow.setNextFocusUpId(btnUpdateNow.getId());
        btnUpdateNow.setNextFocusDownId(btnUpdateNow.getId());

        btnLater.setNextFocusLeftId(btnUpdateNow.getId());
        btnLater.setNextFocusRightId(btnLater.getId());
        btnLater.setNextFocusUpId(btnLater.getId());
        btnLater.setNextFocusDownId(btnLater.getId());

        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f);
        b1Lp.rightMargin = UiHelper.dp(activity, 8);
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b2Lp.leftMargin = UiHelper.dp(activity, 8);
        btnRow.addView(btnUpdateNow, b1Lp);
        btnRow.addView(btnLater, b2Lp);
        promptSection.addView(btnRow);

        mainCard.addView(promptSection);

        // === SECTION 2: DOWNLOAD PROGRESS UI ===
        progressSection = new LinearLayout(activity);
        progressSection.setOrientation(LinearLayout.VERTICAL);
        progressSection.setVisibility(View.GONE);

        TextView dlHeading = new TextView(activity);
        dlHeading.setText("Đang cập nhật ứng dụng Đặng Việt Anh");
        dlHeading.setTextColor(Color.WHITE);
        dlHeading.setTextSize(20);
        dlHeading.setTypeface(Typeface.DEFAULT_BOLD);
        progressSection.addView(dlHeading);

        progressStatusTv = new TextView(activity);
        progressStatusTv.setText("Đang thiết lập kết nối an toàn tới máy chủ cập nhật...");
        progressStatusTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        progressStatusTv.setTextSize(13);
        progressStatusTv.setPadding(0, UiHelper.dp(activity, 6), 0, UiHelper.dp(activity, 18));
        progressSection.addView(progressStatusTv);

        // Progress Bar (rounded 24dp track + gradient fill)
        progressTrack = new FrameLayout(activity);
        UiHelper.applyGlassCardStyle(progressTrack, activity, 24, UiHelper.COLOR_SURFACE_GLASS, 4);
        int trackH = UiHelper.dp(activity, 16);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, trackH);
        trackLp.bottomMargin = UiHelper.dp(activity, 18);

        progressFillView = new View(activity);
        GradientDrawable fillGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiHelper.COLOR_ACCENT_RED, UiHelper.COLOR_ACCENT_PINK});
        fillGrad.setCornerRadius(UiHelper.dp(activity, 24));
        progressFillView.setBackground(fillGrad);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        progressTrack.addView(progressFillView, fillLp);
        progressSection.addView(progressTrack, trackLp);

        // 3 Telemetry Tiles (rounded 24dp)
        LinearLayout statsRow = new LinearLayout(activity);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsRowLp.bottomMargin = UiHelper.dp(activity, 20);

        statPercentVal = new TextView(activity);
        statBytesSub = new TextView(activity);
        statSpeedVal = new TextView(activity);
        statEtaVal = new TextView(activity);

        View cardPct = buildStatTile("Tiến độ", statPercentVal, statBytesSub);
        View cardSpeed = buildStatTile("Tốc độ tải", statSpeedVal, null);
        View cardEta = buildStatTile("Thời gian dự kiến", statEtaVal, null);

        LinearLayout.LayoutParams s1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        s1Lp.rightMargin = UiHelper.dp(activity, 6);
        LinearLayout.LayoutParams s2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        s2Lp.leftMargin = UiHelper.dp(activity, 6);
        s2Lp.rightMargin = UiHelper.dp(activity, 6);
        LinearLayout.LayoutParams s3Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        s3Lp.leftMargin = UiHelper.dp(activity, 6);

        statsRow.addView(cardPct, s1Lp);
        statsRow.addView(cardSpeed, s2Lp);
        statsRow.addView(cardEta, s3Lp);
        progressSection.addView(statsRow, statsRowLp);

        btnCancelDownload = new TextView(activity);
        btnCancelDownload.setId(View.generateViewId());
        btnCancelDownload.setText("Hủy cập nhật");
        btnCancelDownload.setTextColor(Color.WHITE);
        btnCancelDownload.setTextSize(14);
        btnCancelDownload.setTypeface(Typeface.DEFAULT_BOLD);
        btnCancelDownload.setGravity(Gravity.CENTER);
        btnCancelDownload.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 12),
                UiHelper.dp(activity, 24), UiHelper.dp(activity, 12));
        UiHelper.applyFocusableTileStyle(btnCancelDownload, activity, 24,
                UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);
        btnCancelDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelRequested = true;
                isDownloading = false;
                hide();
                Toast.makeText(activity, "Đã hủy quá trình cập nhật", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressSection.addView(btnCancelDownload, cancelLp);

        mainCard.addView(progressSection);
        updateOverlay.addView(mainCard, cardLp);
    }

    private View buildStatTile(String label, TextView valueTv, TextView subTv) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 14),
                UiHelper.dp(activity, 16), UiHelper.dp(activity, 14));
        UiHelper.applyGlassCardStyle(tile, activity, 24, UiHelper.COLOR_SURFACE_GLASS, 8);

        TextView lbl = new TextView(activity);
        lbl.setText(label);
        lbl.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        lbl.setTextSize(11);

        valueTv.setText("--");
        valueTv.setTextColor(Color.WHITE);
        valueTv.setTextSize(17);
        valueTv.setTypeface(Typeface.DEFAULT_BOLD);
        valueTv.setPadding(0, UiHelper.dp(activity, 4), 0, 0);

        tile.addView(lbl);
        tile.addView(valueTv);
        if (subTv != null) {
            subTv.setText("Đang chuẩn bị...");
            subTv.setTextColor(Color.argb(190, 176, 184, 200));
            subTv.setTextSize(11);
            subTv.setPadding(0, UiHelper.dp(activity, 2), 0, 0);
            tile.addView(subTv);
        }
        return tile;
    }

    public void fetchHistoryAsync(final Runnable onComplete) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(VERSION_JSON_URL + "?t=" + System.currentTimeMillis());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setUseCaches(false);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        UpdateHistoryStore.saveServerPayload(activity, sb.toString());
                    }
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }
        }).start();
    }

    public void checkForUpdates(final boolean userInitiated) {
        if (isDownloading) return;
        if (userInitiated) {
            Toast.makeText(activity, "Đang kiểm tra bản cập nhật mới...", Toast.LENGTH_SHORT).show();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(VERSION_JSON_URL + "?t=" + System.currentTimeMillis());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setUseCaches(false);

                    if (conn.getResponseCode() != 200) {
                        if (userInitiated) showToast("Không thể kết nối máy chủ cập nhật");
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String rawJson = sb.toString();
                    UpdateHistoryStore.saveServerPayload(activity, rawJson);
                    JSONObject json = new JSONObject(rawJson);
                    final long remoteCode = json.optLong("versionCode", 0);
                    final String remoteName = json.optString("versionName", String.valueOf(remoteCode));
                    final String apkUrl = json.optString("apkUrl", DEFAULT_APK_URL);
                    final String changelog = json.optString("changelog",
                            "Cải thiện hiệu năng trình phát và tối ưu trải nghiệm Android TV.");
                    final long localCode = getLocalVersionCode();

                    if (remoteCode > localCode) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdatePrompt(remoteName, apkUrl, changelog);
                            }
                        });
                    } else if (userInitiated) {
                        showToast("Bạn đang dùng phiên bản mới nhất (v" + getLocalVersionName() + ")");
                    }
                } catch (Exception e) {
                    if (userInitiated) {
                        showToast("Lỗi kiểm tra cập nhật. Vui lòng thử lại sau.");
                    }
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private void showUpdatePrompt(String versionName, String apkUrl, String changelog) {
        if (activity.isFinishing()) return;
        pendingApkUrl = (apkUrl != null && !apkUrl.isEmpty()) ? apkUrl : DEFAULT_APK_URL;
        pendingVersionName = versionName;
        pendingChangelog = changelog;

        promptVersionTv.setText("Phiên bản hiện tại: v" + getLocalVersionName() + "   →   Mới nhất: v" + pendingVersionName);
        promptChangelogTv.setText(pendingChangelog);

        promptSection.setVisibility(View.VISIBLE);
        progressSection.setVisibility(View.GONE);
        showOverlay();
        btnUpdateNow.post(new Runnable() {
            @Override
            public void run() {
                btnUpdateNow.requestFocus();
            }
        });
    }

    private void showOverlay() {
        if (isShowing) return;
        isShowing = true;
        previouslyFocused = updateOverlay.getRootView().findFocus();
        updateOverlay.setAlpha(0f);
        updateOverlay.setVisibility(View.VISIBLE);
        mainCard.setScaleX(0.95f);
        mainCard.setScaleY(0.95f);
        updateOverlay.animate().alpha(1f).setDuration(250).start();
        mainCard.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public void hide() {
        if (!isShowing) return;
        isShowing = false;
        updateOverlay.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                updateOverlay.setVisibility(View.GONE);
                if (previouslyFocused != null) {
                    previouslyFocused.requestFocus();
                }
            }
        }).start();
    }

    private void startDownloadAndInstall() {
        if (isDownloading) return;
        isDownloading = true;
        cancelRequested = false;

        promptSection.setVisibility(View.GONE);
        progressSection.setVisibility(View.VISIBLE);
        updateProgressUi(0, 0, -1, 0, "Đang thiết lập kết nối an toàn tới máy chủ cập nhật...");
        btnCancelDownload.post(new Runnable() {
            @Override
            public void run() {
                btnCancelDownload.requestFocus();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                File targetFile = ApkFileProvider.getUpdateApkFile(activity);
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                try {
                    URL url = new URL(pendingApkUrl + "?t=" + System.currentTimeMillis());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);

                    if (conn.getResponseCode() != 200) {
                        failDownload("Máy chủ trả về mã lỗi " + conn.getResponseCode());
                        return;
                    }

                    final long totalBytes = getContentLengthSafe(conn);
                    InputStream input = new BufferedInputStream(conn.getInputStream(), 16384);
                    FileOutputStream output = new FileOutputStream(targetFile);

                    byte[] buffer = new byte[16384];
                    long downloaded = 0;
                    long startTimeMs = SystemClock.elapsedRealtime();
                    long lastUiMs = startTimeMs;
                    long lastSampleBytes = 0;
                    long lastSampleMs = startTimeMs;
                    double smoothedSpeedBps = 0;

                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (cancelRequested) {
                            output.close();
                            input.close();
                            targetFile.delete();
                            return;
                        }
                        output.write(buffer, 0, count);
                        downloaded += count;

                        long now = SystemClock.elapsedRealtime();
                        if (now - lastUiMs >= 220) {
                            long dtMs = Math.max(1L, now - lastSampleMs);
                            double instSpeed = ((downloaded - lastSampleBytes) * 1000.0) / dtMs;
                            smoothedSpeedBps = (smoothedSpeedBps <= 0)
                                    ? instSpeed
                                    : (smoothedSpeedBps * 0.65 + instSpeed * 0.35);

                            lastSampleBytes = downloaded;
                            lastSampleMs = now;
                            lastUiMs = now;

                            int pct = (totalBytes > 0) ? (int) Math.min(100, (downloaded * 100) / totalBytes) : -1;
                            String statusText = pct >= 90
                                    ? "Đang hoàn tất gói cài đặt. Xin vui lòng chờ trong giây lát..."
                                    : "Đang tải xuống phiên bản v" + pendingVersionName + ". Vui lòng không tắt nguồn máy chiếu...";
                            updateProgressUi(pct, downloaded, totalBytes, smoothedSpeedBps, statusText);
                        }
                    }

                    output.flush();
                    output.close();
                    input.close();

                    if (cancelRequested) {
                        targetFile.delete();
                        return;
                    }

                    updateProgressUi(100, downloaded, Math.max(downloaded, totalBytes), smoothedSpeedBps,
                            "Đang xác thực tính toàn vẹn gói cài đặt...");
                    SystemClock.sleep(450);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isDownloading = false;
                            progressStatusTv.setText("Tải xuống hoàn tất! Đang mở trình cài đặt hệ thống...");
                            proceedToInstallApk();
                        }
                    });
                } catch (Exception e) {
                    if (targetFile.exists()) targetFile.delete();
                    if (!cancelRequested) {
                        failDownload("Không thể tải tệp cập nhật. Vui lòng kiểm tra Wi-Fi và thử lại.");
                    }
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private void updateProgressUi(final int pct, final long downloaded, final long total,
                                  final double speedBps, final String statusMsg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isShowing) return;
                progressStatusTv.setText(statusMsg);

                int displayPct = Math.max(0, pct);
                statPercentVal.setText(pct >= 0 ? (displayPct + "%") : "Đang tải...");
                statBytesSub.setText(formatSize(downloaded) + (total > 0 ? " / " + formatSize(total) : ""));
                statSpeedVal.setText(speedBps > 0 ? formatSpeed(speedBps) : "Đang đo...");

                if (total > 0 && speedBps > 1024 && downloaded < total) {
                    long remainSec = Math.max(1L, Math.round((total - downloaded) / speedBps));
                    statEtaVal.setText(formatDuration(remainSec));
                } else if (pct >= 100) {
                    statEtaVal.setText("Hoàn tất");
                } else {
                    statEtaVal.setText("Đang tính...");
                }

                int trackWidth = progressTrack.getWidth();
                if (trackWidth > 0) {
                    ViewGroup.LayoutParams lp = progressFillView.getLayoutParams();
                    lp.width = (int) ((trackWidth * displayPct) / 100f);
                    progressFillView.setLayoutParams(lp);
                }
            }
        });
    }

    private void failDownload(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                isDownloading = false;
                hide();
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void proceedToInstallApk() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            pendingInstallAfterPerm = true;
            progressStatusTv.setText("Vui lòng bật 'Cho phép cài đặt từ nguồn này' để hoàn tất nâng cấp...");
            try {
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permIntent);
            } catch (Exception e) {
                pendingInstallAfterPerm = false;
                Toast.makeText(activity, "Hãy cấp quyền cài ứng dụng không rõ nguồn gốc trong Cài đặt", Toast.LENGTH_LONG).show();
                hide();
            }
            return;
        }
        launchApkInstaller();
    }

    public void onHostResume() {
        if (pendingInstallAfterPerm) {
            pendingInstallAfterPerm = false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    || activity.getPackageManager().canRequestPackageInstalls()) {
                launchApkInstaller();
            } else {
                Toast.makeText(activity, "Chưa có quyền cài đặt bản cập nhật", Toast.LENGTH_SHORT).show();
                hide();
            }
        }
    }

    private void launchApkInstaller() {
        try {
            File apkFile = ApkFileProvider.getUpdateApkFile(activity);
            if (!apkFile.exists()) {
                Toast.makeText(activity, "Không tìm thấy tệp APK vừa tải", Toast.LENGTH_SHORT).show();
                hide();
                return;
            }
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = ApkFileProvider.getUpdateApkUri();
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
            hide();
        } catch (Exception e) {
            Toast.makeText(activity, "Không thể mở trình cài đặt hệ thống", Toast.LENGTH_LONG).show();
            hide();
        }
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (!isShowing) return false;
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (isDownloading) {
                cancelRequested = true;
                isDownloading = false;
                Toast.makeText(activity, "Đã hủy quá trình cập nhật", Toast.LENGTH_SHORT).show();
            }
            hide();
            return true;
        }
        return false;
    }

    private void showToast(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static long getContentLengthSafe(HttpURLConnection conn) {
        String header = conn.getHeaderField("Content-Length");
        if (header != null) {
            try {
                return Long.parseLong(header.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return conn.getContentLength();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private static String formatSpeed(double bps) {
        if (bps < 1024) return String.format(Locale.US, "%.0f B/s", bps);
        if (bps < 1024 * 1024) return String.format(Locale.US, "%.0f KB/s", bps / 1024.0);
        return String.format(Locale.US, "%.2f MB/s", bps / (1024.0 * 1024.0));
    }

    private static String formatDuration(long sec) {
        if (sec < 60) return "Còn ~" + sec + " giây";
        long m = sec / 60;
        long s = sec % 60;
        return "Còn ~" + m + "p " + s + "s";
    }
}
