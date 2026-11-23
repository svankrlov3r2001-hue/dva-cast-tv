package com.dangvietanh.cast.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dangvietanh.cast.NativePlayerController;
import com.dangvietanh.cast.Prefs;
import com.dangvietanh.cast.R;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Full-screen native Android TV player screen with borderless Liquid Glass HUD,
 * adaptive ServerOrchestrator streaming, seamless dual-player episode preload,
 * dual subtitles + 108-language live translator, AI voice dubbing, and 5 TV sub-modals.
 */
public class PlayerScreenController {

    public interface Listener {
        void onPlayerClosed(boolean shouldRefreshHome);
        void onAudioFocusRequested(boolean active);
        void onShowToast(String message, int accentColor);
    }

    private static final int MODAL_NONE = 0;
    private static final int MODAL_EPISODES = 1;
    private static final int MODAL_SERVERS = 2;
    private static final int MODAL_SUBTITLES = 3;
    private static final int MODAL_SETTINGS = 4;
    private static final int MODAL_INFO = 5;
    private static final int MODAL_MEMBER = 6;

    private static final long HUD_AUTO_HIDE_MS = 6500L;
    private static final long CHECK_FILE_POLL_MS = 4000L;
    private static final long PROGRESS_SAVE_INTERVAL_MS = 10000L;

    private final Context context;
    private final ApiClient apiClient;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final NativePlayerController nativePlayer;
    private final ServerOrchestrator serverOrchestrator;
    private final SubtitleEngine subtitleEngine;
    private final SubtitleEngine.SubtitleOverlayView subtitleOverlay;
    private final DubbingTtsEngine dubbingEngine;

    private final FrameLayout rootFrame;
    private final View contrastOverlay;
    private final View transitionVeil;

    // Loading pill
    private final LinearLayout loadingCenter;
    private final UiHelper.SpinnerView loadingSpinner;
    private final TextView loadingStatusTv;

    // Skip Intro & Auto-Next Cards
    private final TextView skipIntroBtn;
    private final LinearLayout nextEpCard;
    private final TextView nextEpTitleTv;
    private final TextView nextEpCountdownTv;
    private final TextView btnNextPlayNow;
    private final TextView btnNextCancel;

    // HUD Controls
    private final FrameLayout hudLayer;
    private final TextView btnBackHud;
    private final TextView hudTitleTv;
    private final TextView hudMetaTv;
    private final TextView sleepBadgeTv;
    private final LinearLayout btnFillToggle;
    private final TextView fillToggleText;
    private final TextView btnAutoNextQuick;
    private final LinearLayout btnDubQuick;
    private final TextView btnPlayPause;
    private final ImageView playPauseIcon;

    // Interactive SeekBar
    private final FrameLayout seekBarFocusBox;
    private final View seekBufferedFill;
    private final View seekProgressFill;
    private final View seekThumbDot;
    private final TextView timeCurrentTv;
    private final TextView timeDurationTv;

    // Bottom Bar Actions
    private final LinearLayout btnNextEpBar;
    private final LinearLayout btnWishlistBar;
    private final ImageView wishlistIconBar;
    private final TextView wishlistTextBar;
    private final LinearLayout btnLikeBar;
    private final LinearLayout btnDislikeBar;
    private final LinearLayout btnSpeedBar;
    private final TextView speedTextBar;

    // Sub-Modal Overlay
    private final FrameLayout subModalHost;
    private final LinearLayout subModalCard;
    private final TextView subModalTitleTv;
    private final TextView subModalCloseBtn;
    private final ScrollView subModalScroll;
    private final LinearLayout subModalBody;

    // Playback Session State
    private boolean showing = false;
    private boolean hudVisible = true;
    private int activeModal = MODAL_NONE;
    private int episodeSessionSeq = 0;

    private Models.PlayerLaunchRequest launchReq;
    private Models.StreamResolvedData resolvedData;
    private Models.EpisodeListApiResponse cachedRichEpisodes;
    private String selectedSeasonFilter = "";

    private final List<ServerOrchestrator.ServerState> serverStates = new ArrayList<>();
    private final Set<String> erroredServerIds = new HashSet<>();
    private String activeServerId = "";
    private String activeStreamUrl = "";
    private String activeQualityLabel = "FHD";
    private ServerOrchestrator.GgdFlags ggdFlags = new ServerOrchestrator.GgdFlags();

    private final List<SubtitleEngine.Cue> vnCues = new ArrayList<>();
    private final List<SubtitleEngine.Cue> enCues = new ArrayList<>();

    private boolean isPlayingNow = false;
    private double lastPosSec = 0.0;
    private double lastDurSec = 0.0;
    private int lastBufferedPct = 0;
    private boolean autoSkippedIntroThisEp = false;
    private boolean initialResumeApplied = false;
    private boolean wishlistLocalState = false;

    // Seamless Preload & Next Episode Countdown
    private Models.EpisodeItem nextEpisodeTarget;
    private String preloadedNextUrl = "";
    private boolean preloadAttemptedThisEp = false;
    private boolean nextCountdownDismissed = false;
    private boolean nextCountdownRunning = false;
    private int nextCountdownRemaining = 0;
    private boolean wasHudTriggeredByAutoDubNext = false;

    // Check-file polling & Sleep timer & Save progress
    private boolean checkFilePolling = false;
    private long lastProgressSaveElapsedMs = 0L;
    private long sleepTargetElapsedMs = 0L;
    private int sleepMinutesSetting = 0;

    private final Runnable hudAutoHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (showing && hudVisible && activeModal == MODAL_NONE && isPlayingNow) {
                setHudVisible(false);
            }
        }
    };

    private final Runnable checkFilePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!showing || !checkFilePolling || resolvedData == null || resolvedData.serverFileKey.isEmpty()) {
                return;
            }
            final int seq = episodeSessionSeq;
            final String sfk = resolvedData.serverFileKey;
            serverOrchestrator.checkFileStatus(sfk, new ServerOrchestrator.Callback<ServerOrchestrator.CheckFileResult>() {
                @Override
                public void onResult(ServerOrchestrator.CheckFileResult res) {
                    if (!showing || seq != episodeSessionSeq) return;
                    List<ServerOrchestrator.ServerState> updated = serverOrchestrator.applyCheckFile(serverStates, sfk, res);
                    serverStates.clear();
                    serverStates.addAll(updated);

                    if (activeModal == MODAL_SERVERS) {
                        renderSubModalContent();
                    }
                    if (activeStreamUrl.isEmpty() && loadingCenter.getVisibility() == View.VISIBLE && res != null) {
                        loadingStatusTv.setText("Đang chuẩn bị máy chủ (" + res.progressNum + "%)...");
                    }

                    boolean anyStillPreparing = false;
                    for (ServerOrchestrator.ServerState s : serverStates) {
                        if (("11".equals(s.id) || "36".equals(s.id))
                                && (ServerOrchestrator.STATUS_CHECKING.equals(s.status)
                                || ServerOrchestrator.STATUS_DOWNLOADING.equals(s.status)
                                || ServerOrchestrator.STATUS_PREVIEW_10P.equals(s.status)
                                || ServerOrchestrator.STATUS_PREVIEW_30P.equals(s.status))) {
                            anyStillPreparing = true;
                        }
                    }

                    // Upgrade automatically if active stream wasn't set or user enabled autoOptimal and full READY became available
                    if (activeStreamUrl.isEmpty()) {
                        tryStartBestPlayableServer(false);
                    } else if (Prefs.isAutoOptimalServer(context) && !"11".equals(activeServerId) && !"36".equals(activeServerId)) {
                        ServerOrchestrator.ServerState best = serverOrchestrator.selectOptimalServer(serverStates);
                        if (best != null && ServerOrchestrator.STATUS_READY.equals(best.status)
                                && ("11".equals(best.id) || "36".equals(best.id))
                                && !best.streamUrl.equals(activeStreamUrl)) {
                            switchToServer(best, false);
                        }
                    }

                    if (anyStillPreparing && checkFilePolling) {
                        handler.postDelayed(checkFilePollRunnable, CHECK_FILE_POLL_MS);
                    } else {
                        checkFilePolling = false;
                    }
                }
            });
        }
    };

    private final Runnable nextCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (!showing || !nextCountdownRunning || nextEpisodeTarget == null) return;
            if (nextCountdownRemaining <= 0) {
                nextCountdownRunning = false;
                nextEpCard.setVisibility(View.GONE);
                playNextEpisode(true);
                return;
            }
            nextEpCountdownTv.setText("Phát sau " + nextCountdownRemaining + " giây");
            nextCountdownRemaining--;
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable sleepTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!showing || sleepTargetElapsedMs <= 0L) {
                sleepBadgeTv.setVisibility(View.GONE);
                return;
            }
            long remMs = sleepTargetElapsedMs - android.os.SystemClock.elapsedRealtime();
            if (remMs <= 0L) {
                sleepTargetElapsedMs = 0L;
                sleepMinutesSetting = 0;
                sleepBadgeTv.setVisibility(View.GONE);
                if (listener != null) {
                    listener.onShowToast("Đã hết thời gian hẹn giờ tắt phát", UiHelper.COLOR_GOLD);
                }
                close();
                return;
            }
            int minLeft = (int) Math.max(1L, Math.ceil(remMs / 60000.0));
            sleepBadgeTv.setVisibility(View.VISIBLE);
            sleepBadgeTv.setText("Hẹn giờ: " + minLeft + "p");
            handler.postDelayed(this, 15000L);
        }
    };

    public PlayerScreenController(Context context, ApiClient apiClient, Listener listener) {
        this.context = context.getApplicationContext();
        this.apiClient = apiClient;
        this.listener = listener;
        this.serverOrchestrator = new ServerOrchestrator();
        this.subtitleEngine = new SubtitleEngine();
        this.dubbingEngine = new DubbingTtsEngine(this.context, subtitleEngine);
        this.subtitleOverlay = new SubtitleEngine.SubtitleOverlayView(this.context, subtitleEngine);

        this.nativePlayer = new NativePlayerController(this.context, "DVACastTV/4.0",
                new NativePlayerController.PlayerEventListener() {
                    @Override
                    public void onPlaybackState(boolean isPlaying, boolean isBuffering, boolean isEnded, boolean isReady) {
                        handlePlaybackState(isPlaying, isBuffering, isEnded, isReady);
                    }

                    @Override
                    public void onProgress(double posSec, double durSec, int bufferedPct) {
                        handleProgress(posSec, durSec, bufferedPct);
                    }

                    @Override
                    public void onFirstFrame() {
                        loadingCenter.setVisibility(View.GONE);
                        fadeTransitionVeilOut();
                    }

                    @Override
                    public void onVideoSize(int width, int height) {
                    }

                    @Override
                    public void onError(String errorMsg, boolean isCodecError) {
                        handleStreamError(errorMsg);
                    }

                    @Override
                    public void onVisibilityChanged(boolean active) {
                    }
                });

        rootFrame = new FrameLayout(context);
        rootFrame.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        rootFrame.setBackgroundColor(Color.BLACK);
        rootFrame.setVisibility(View.GONE);
        rootFrame.setClickable(true);

        // 1. Video Surface
        rootFrame.addView(nativePlayer.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 2. Contrast Comfort Overlay
        contrastOverlay = new View(context);
        contrastOverlay.setBackgroundColor(Color.BLACK);
        contrastOverlay.setClickable(false);
        contrastOverlay.setFocusable(false);
        rootFrame.addView(contrastOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        applyContrastSetting();

        // 3. Subtitle Overlay View
        rootFrame.addView(subtitleOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 4. Episode Transition Veil
        transitionVeil = new View(context);
        transitionVeil.setBackgroundColor(UiHelper.COLOR_BG_BASE);
        transitionVeil.setAlpha(0f);
        transitionVeil.setVisibility(View.GONE);
        rootFrame.addView(transitionVeil, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 5. Loading Pill
        loadingCenter = new LinearLayout(context);
        loadingCenter.setOrientation(LinearLayout.HORIZONTAL);
        loadingCenter.setGravity(Gravity.CENTER_VERTICAL);
        loadingCenter.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 14),
                UiHelper.dp(context, 26), UiHelper.dp(context, 14));
        UiHelper.applyGlassCardStyle(loadingCenter, context, 32, UiHelper.COLOR_SURFACE_GLASS, 20);
        loadingSpinner = new UiHelper.SpinnerView(context);
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(UiHelper.dp(context, 26), UiHelper.dp(context, 26));
        lsp.rightMargin = UiHelper.dp(context, 12);
        loadingCenter.addView(loadingSpinner, lsp);
        loadingStatusTv = new TextView(context);
        loadingStatusTv.setText("Đang khởi tạo trình phát...");
        loadingStatusTv.setTextColor(Color.WHITE);
        loadingStatusTv.setTextSize(14.5f);
        loadingStatusTv.setTypeface(null, Typeface.BOLD);
        loadingCenter.addView(loadingStatusTv);
        FrameLayout.LayoutParams lcLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        rootFrame.addView(loadingCenter, lcLp);

        // 6. Skip Intro Pill
        skipIntroBtn = new TextView(context);
        skipIntroBtn.setText("Bỏ qua giới thiệu →");
        skipIntroBtn.setTextColor(Color.WHITE);
        skipIntroBtn.setTextSize(14f);
        skipIntroBtn.setTypeface(null, Typeface.BOLD);
        skipIntroBtn.setPadding(UiHelper.dp(context, 20), UiHelper.dp(context, 11),
                UiHelper.dp(context, 20), UiHelper.dp(context, 11));
        UiHelper.applyFocusableGlassButton(skipIntroBtn, context, 24, false);
        skipIntroBtn.setVisibility(View.GONE);
        skipIntroBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resolvedData != null && resolvedData.skipIntroSec > 0) {
                    nativePlayer.seekTo(resolvedData.skipIntroSec);
                    skipIntroBtn.setVisibility(View.GONE);
                    resetHudAutoHide();
                }
            }
        });
        FrameLayout.LayoutParams siLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        siLp.setMargins(0, 0, UiHelper.dp(context, 36), UiHelper.dp(context, 116));
        rootFrame.addView(skipIntroBtn, siLp);

        // 7. Auto-Next Episode Countdown Card
        nextEpCard = new LinearLayout(context);
        nextEpCard.setOrientation(LinearLayout.VERTICAL);
        nextEpCard.setPadding(UiHelper.dp(context, 22), UiHelper.dp(context, 18),
                UiHelper.dp(context, 22), UiHelper.dp(context, 18));
        UiHelper.applyGlassCardStyle(nextEpCard, context, 32, UiHelper.COLOR_SURFACE_GLASS, 24);
        nextEpCard.setVisibility(View.GONE);

        TextView nxLabel = new TextView(context);
        nxLabel.setText("TẬP TIẾP THEO");
        nxLabel.setTextColor(UiHelper.COLOR_ACCENT_RED);
        nxLabel.setTextSize(11f);
        nxLabel.setTypeface(null, Typeface.BOLD);
        nxLabel.setLetterSpacing(0.08f);
        nextEpCard.addView(nxLabel);

        nextEpTitleTv = new TextView(context);
        nextEpTitleTv.setTextColor(Color.WHITE);
        nextEpTitleTv.setTextSize(16f);
        nextEpTitleTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams ntLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ntLp.topMargin = UiHelper.dp(context, 4);
        nextEpCard.addView(nextEpTitleTv, ntLp);

        nextEpCountdownTv = new TextView(context);
        nextEpCountdownTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        nextEpCountdownTv.setTextSize(13f);
        LinearLayout.LayoutParams ncLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ncLp.topMargin = UiHelper.dp(context, 2);
        ncLp.bottomMargin = UiHelper.dp(context, 14);
        nextEpCard.addView(nextEpCountdownTv, ncLp);

        LinearLayout nxBtns = new LinearLayout(context);
        nxBtns.setOrientation(LinearLayout.HORIZONTAL);
        btnNextPlayNow = makeTextPill(context, "▶  Phát ngay", true);
        btnNextPlayNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelNextCountdown(true);
                playNextEpisode(true);
            }
        });
        LinearLayout.LayoutParams npLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        npLp.rightMargin = UiHelper.dp(context, 10);
        nxBtns.addView(btnNextPlayNow, npLp);

        btnNextCancel = makeTextPill(context, "Huỷ", false);
        btnNextCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelNextCountdown(true);
            }
        });
        nxBtns.addView(btnNextCancel);
        nextEpCard.addView(nxBtns);

        FrameLayout.LayoutParams neLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        neLp.setMargins(0, 0, UiHelper.dp(context, 36), UiHelper.dp(context, 118));
        rootFrame.addView(nextEpCard, neLp);

        // 8. Main HUD Layer
        hudLayer = new FrameLayout(context);
        hudLayer.setClipChildren(false);
        rootFrame.addView(hudLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View topGrad = new View(context);
        topGrad.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(220, 7, 9, 15), Color.TRANSPARENT}));
        hudLayer.addView(topGrad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 140), Gravity.TOP));

        View botGrad = new View(context);
        botGrad.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(235, 7, 9, 15)}));
        hudLayer.addView(botGrad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 196), Gravity.BOTTOM));

        // Top Bar Container
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        topBar.setPadding(UiHelper.dp(context, 28), UiHelper.dp(context, 20),
                UiHelper.dp(context, 28), UiHelper.dp(context, 10));

        btnBackHud = makeTextPill(context, "←", false);
        btnBackHud.setTextSize(17f);
        btnBackHud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        LinearLayout.LayoutParams bbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bbLp.rightMargin = UiHelper.dp(context, 14);
        topBar.addView(btnBackHud, bbLp);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        hudTitleTv = new TextView(context);
        hudTitleTv.setTextColor(Color.WHITE);
        hudTitleTv.setTextSize(19f);
        hudTitleTv.setTypeface(null, Typeface.BOLD);
        hudTitleTv.setMaxLines(1);
        hudTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(hudTitleTv);

        LinearLayout metaLine = new LinearLayout(context);
        metaLine.setOrientation(LinearLayout.HORIZONTAL);
        metaLine.setGravity(Gravity.CENTER_VERTICAL);
        hudMetaTv = new TextView(context);
        hudMetaTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        hudMetaTv.setTextSize(12.5f);
        metaLine.addView(hudMetaTv);

        sleepBadgeTv = new TextView(context);
        sleepBadgeTv.setTextColor(UiHelper.COLOR_GOLD);
        sleepBadgeTv.setTextSize(11.5f);
        sleepBadgeTv.setTypeface(null, Typeface.BOLD);
        sleepBadgeTv.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 2), UiHelper.dp(context, 10), UiHelper.dp(context, 2));
        UiHelper.applySubtleGlassPill(sleepBadgeTv, context, 14);
        sleepBadgeTv.setVisibility(View.GONE);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbLp.leftMargin = UiHelper.dp(context, 10);
        metaLine.addView(sleepBadgeTv, sbLp);

        titleCol.addView(metaLine);
        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Top-Right Quick Action Pills
        HorizontalScrollView topQuickScroll = new HorizontalScrollView(context);
        topQuickScroll.setHorizontalScrollBarEnabled(false);
        topQuickScroll.setClipChildren(false);
        LinearLayout topQuickRow = new LinearLayout(context);
        topQuickRow.setOrientation(LinearLayout.HORIZONTAL);
        topQuickRow.setClipChildren(false);
        topQuickScroll.addView(topQuickRow);

        btnFillToggle = makeIconTextPill(context, Prefs.isVideoFillCover(context) ? R.drawable.ic_fit_screen : R.drawable.ic_fullscreen,
                Prefs.isVideoFillCover(context) ? "Vừa khung" : "Tràn viền");
        fillToggleText = (TextView) btnFillToggle.getChildAt(1);
        btnFillToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isVideoFillCover(PlayerScreenController.this.context);
                Prefs.setVideoFillCover(PlayerScreenController.this.context, next);
                applyResizeSetting();
                toast(next ? "Đã bật chế độ Tràn viền màn hình" : "Đã về tỷ lệ khung hình gốc", UiHelper.COLOR_CYAN);
                resetHudAutoHide();
            }
        });
        topQuickRow.addView(btnFillToggle, rightMarginLp(8));

        btnAutoNextQuick = makeTextPill(context, Prefs.isAutoPlayNext(context) ? "Tự chuyển: BẬT" : "Tự chuyển: TẮT", false);
        btnAutoNextQuick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isAutoPlayNext(PlayerScreenController.this.context);
                Prefs.setAutoPlayNext(PlayerScreenController.this.context, next);
                refreshQuickBadges();
                toast(next ? "Đã bật Tự động chuyển tập mượt mà" : "Đã tắt Tự động chuyển tập", UiHelper.COLOR_GREEN);
                resetHudAutoHide();
            }
        });
        topQuickRow.addView(btnAutoNextQuick, rightMarginLp(8));

        LinearLayout btnSubQuick = makeIconTextPill(context, R.drawable.ic_subtitles, "Phụ đề");
        btnSubQuick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SUBTITLES);
            }
        });
        topQuickRow.addView(btnSubQuick, rightMarginLp(8));

        btnDubQuick = makeIconTextPill(context, R.drawable.ic_voice, "Thuyết minh");
        btnDubQuick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isSubAiVoiceEnabled(PlayerScreenController.this.context);
                Prefs.setSubAiVoiceEnabled(PlayerScreenController.this.context, next);
                if (!next) dubbingEngine.pauseNow();
                refreshQuickBadges();
                toast(next ? "Đã bật Thuyết minh AI" : "Đã tắt Thuyết minh AI", UiHelper.COLOR_GREEN);
                resetHudAutoHide();
            }
        });
        topQuickRow.addView(btnDubQuick, rightMarginLp(8));

        LinearLayout btnServerQuick = makeIconTextPill(context, R.drawable.ic_server, "Máy chủ");
        btnServerQuick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SERVERS);
            }
        });
        topQuickRow.addView(btnServerQuick, rightMarginLp(8));

        LinearLayout btnSettingsQuick = makeIconTextPill(context, R.drawable.ic_settings, "Cài đặt");
        btnSettingsQuick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SETTINGS);
            }
        });
        topQuickRow.addView(btnSettingsQuick);

        topBar.addView(topQuickScroll);
        hudLayer.addView(topBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // Center Transport Row
        LinearLayout centerTransport = new LinearLayout(context);
        centerTransport.setOrientation(LinearLayout.HORIZONTAL);
        centerTransport.setGravity(Gravity.CENTER);
        centerTransport.setClipChildren(false);

        LinearLayout btnRew = makeIconOnlyCircle(context, R.drawable.ic_rewind, 52, false);
        btnRew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBySec(-10);
            }
        });
        centerTransport.addView(btnRew, rightMarginLp(24));

        FrameLayout centerPlayWrap = new FrameLayout(context);
        UiHelper.applyFocusableGlassButton(centerPlayWrap, context, 36, true);
        playPauseIcon = new ImageView(context);
        playPauseIcon.setImageResource(R.drawable.ic_pause);
        playPauseIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        centerPlayWrap.addView(playPauseIcon, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 30), UiHelper.dp(context, 30), Gravity.CENTER));
        centerPlayWrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });
        // Hidden helper reference on btnPlayPause (actually we focus centerPlayWrap cleanly via view field)
        btnPlayPause = new TextView(context);
        btnPlayPause.setVisibility(View.GONE);
        centerPlayWrap.setTag("play_center");
        centerTransport.addView(centerPlayWrap, new LinearLayout.LayoutParams(UiHelper.dp(context, 68), UiHelper.dp(context, 68)));

        LinearLayout btnFwd = makeIconOnlyCircle(context, R.drawable.ic_forward, 52, false);
        btnFwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBySec(10);
            }
        });
        LinearLayout.LayoutParams fwLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 52), UiHelper.dp(context, 52));
        fwLp.leftMargin = UiHelper.dp(context, 24);
        centerTransport.addView(btnFwd, fwLp);

        hudLayer.addView(centerTransport, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Bottom Stack: Seekbar + Action Bar
        LinearLayout bottomHudCol = new LinearLayout(context);
        bottomHudCol.setOrientation(LinearLayout.VERTICAL);
        bottomHudCol.setClipChildren(false);
        bottomHudCol.setPadding(UiHelper.dp(context, 28), UiHelper.dp(context, 6),
                UiHelper.dp(context, 28), UiHelper.dp(context, 20));

        LinearLayout seekRow = new LinearLayout(context);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srLp.bottomMargin = UiHelper.dp(context, 12);

        timeCurrentTv = new TextView(context);
        timeCurrentTv.setText("00:00");
        timeCurrentTv.setTextColor(Color.WHITE);
        timeCurrentTv.setTextSize(13f);
        timeCurrentTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        seekRow.addView(timeCurrentTv, rightMarginLp(12));

        seekBarFocusBox = new FrameLayout(context);
        seekBarFocusBox.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 10),
                UiHelper.dp(context, 8), UiHelper.dp(context, 10));
        UiHelper.applyFocusableGlassButton(seekBarFocusBox, context, 18, false);

        FrameLayout trackWrap = new FrameLayout(context);
        View trackBg = new View(context);
        trackBg.setBackground(UiHelper.createGlassRect(context, 4, Color.argb(72, 255, 255, 255), Color.TRANSPARENT, 0));
        trackWrap.addView(trackBg, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 6), Gravity.CENTER_VERTICAL));

        seekBufferedFill = new View(context);
        seekBufferedFill.setBackground(UiHelper.createGlassRect(context, 4, Color.argb(95, 255, 255, 255), Color.TRANSPARENT, 0));
        trackWrap.addView(seekBufferedFill, new FrameLayout.LayoutParams(0, UiHelper.dp(context, 6), Gravity.CENTER_VERTICAL));

        seekProgressFill = new View(context);
        seekProgressFill.setBackground(UiHelper.createGlassRect(context, 4, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        trackWrap.addView(seekProgressFill, new FrameLayout.LayoutParams(0, UiHelper.dp(context, 6), Gravity.CENTER_VERTICAL));

        seekThumbDot = new View(context);
        seekThumbDot.setBackground(UiHelper.createGlassRect(context, 8, Color.WHITE, Color.TRANSPARENT, 0));
        trackWrap.addView(seekThumbDot, new FrameLayout.LayoutParams(UiHelper.dp(context, 14), UiHelper.dp(context, 14), Gravity.CENTER_VERTICAL));

        seekBarFocusBox.addView(trackWrap, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 18)));

        seekBarFocusBox.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    seekBySec(-10);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    seekBySec(10);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    togglePlayPause();
                    return true;
                }
                return false;
            }
        });
        seekBarFocusBox.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                    int w = v.getWidth();
                    if (w > 0 && lastDurSec > 0) {
                        float ratio = Math.max(0f, Math.min(1f, event.getX() / w));
                        nativePlayer.seekTo(ratio * lastDurSec);
                        resetHudAutoHide();
                    }
                }
                return true;
            }
        });

        seekRow.addView(seekBarFocusBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        timeDurationTv = new TextView(context);
        timeDurationTv.setText("00:00");
        timeDurationTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        timeDurationTv.setTextSize(13f);
        timeDurationTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams tdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tdLp.leftMargin = UiHelper.dp(context, 12);
        seekRow.addView(timeDurationTv, tdLp);
        bottomHudCol.addView(seekRow, srLp);

        // Bottom Scrollable Action Strip
        HorizontalScrollView bottomScroll = new HorizontalScrollView(context);
        bottomScroll.setHorizontalScrollBarEnabled(false);
        bottomScroll.setClipChildren(false);
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setClipChildren(false);
        bottomScroll.addView(bottomRow);

        btnNextEpBar = makeIconTextPill(context, R.drawable.ic_skip_next, "Tập tiếp theo");
        btnNextEpBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNextEpisode(false);
            }
        });
        bottomRow.addView(btnNextEpBar, rightMarginLp(10));

        LinearLayout btnEpisodesBar = makeIconTextPill(context, R.drawable.ic_episodes, "Danh sách tập");
        btnEpisodesBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_EPISODES);
            }
        });
        bottomRow.addView(btnEpisodesBar, rightMarginLp(10));

        btnWishlistBar = makeIconTextPill(context, R.drawable.ic_bookmark_outline, "Danh sách");
        wishlistIconBar = (ImageView) btnWishlistBar.getChildAt(0);
        wishlistTextBar = (TextView) btnWishlistBar.getChildAt(1);
        btnWishlistBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWishlist();
            }
        });
        bottomRow.addView(btnWishlistBar, rightMarginLp(10));

        btnLikeBar = makeIconTextPill(context, R.drawable.ic_thumb_up, "Thích");
        btnLikeBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleReaction("like");
            }
        });
        bottomRow.addView(btnLikeBar, rightMarginLp(10));

        btnDislikeBar = makeIconTextPill(context, R.drawable.ic_thumb_down, "Không thích");
        btnDislikeBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleReaction("dislike");
            }
        });
        bottomRow.addView(btnDislikeBar, rightMarginLp(10));

        LinearLayout btnInfoBar = makeIconTextPill(context, R.drawable.ic_info, "Thông tin phim");
        btnInfoBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_INFO);
            }
        });
        bottomRow.addView(btnInfoBar, rightMarginLp(10));

        LinearLayout btnMemberBar = makeIconTextPill(context, R.drawable.ic_person, "Thành viên");
        btnMemberBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_MEMBER);
            }
        });
        bottomRow.addView(btnMemberBar, rightMarginLp(10));

        btnSpeedBar = makeIconTextPill(context, R.drawable.ic_speed, "1.0x");
        speedTextBar = (TextView) btnSpeedBar.getChildAt(1);
        btnSpeedBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSpeed();
            }
        });
        bottomRow.addView(btnSpeedBar, rightMarginLp(10));

        LinearLayout btnReloadBar = makeIconTextPill(context, R.drawable.ic_reload, "Tải lại");
        btnReloadBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadCurrentEpisode();
            }
        });
        bottomRow.addView(btnReloadBar);

        bottomHudCol.addView(bottomScroll);
        hudLayer.addView(bottomHudCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // 9. Sub-Modal Host (Sleek Frosted Glass 20dp)
        subModalHost = new FrameLayout(context);
        subModalHost.setBackgroundColor(Color.argb(190, 9, 11, 18));
        subModalHost.setVisibility(View.GONE);
        subModalHost.setClickable(true);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels > 0 ? dm.widthPixels : UiHelper.dp(context, 960);
        int screenH = dm.heightPixels > 0 ? dm.heightPixels : UiHelper.dp(context, 540);
        int modalW = Math.min(UiHelper.dp(context, 700), Math.round(screenW * 0.84f));
        int modalMaxH = Math.round(screenH * 0.82f);

        subModalCard = new LinearLayout(context);
        subModalCard.setOrientation(LinearLayout.VERTICAL);
        subModalCard.setPadding(UiHelper.dp(context, 22), UiHelper.dp(context, 18),
                UiHelper.dp(context, 22), UiHelper.dp(context, 18));
        UiHelper.applyGlassCardStyle(subModalCard, context, 20, UiHelper.COLOR_SURFACE_GLASS, 18);

        LinearLayout smHeader = new LinearLayout(context);
        smHeader.setOrientation(LinearLayout.HORIZONTAL);
        smHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams smhLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        smhLp.bottomMargin = UiHelper.dp(context, 12);

        subModalTitleTv = new TextView(context);
        subModalTitleTv.setTextColor(Color.WHITE);
        subModalTitleTv.setTextSize(17.5f);
        subModalTitleTv.setTypeface(null, Typeface.BOLD);
        smHeader.addView(subModalTitleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        subModalCloseBtn = makeTextPill(context, "Đóng", false);
        subModalCloseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSubModal();
            }
        });
        smHeader.addView(subModalCloseBtn);
        subModalCard.addView(smHeader, smhLp);

        subModalScroll = new ScrollView(context);
        subModalScroll.setVerticalScrollBarEnabled(false);
        subModalBody = new LinearLayout(context);
        subModalBody.setOrientation(LinearLayout.VERTICAL);
        subModalBody.setClipChildren(false);
        subModalScroll.addView(subModalBody, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        subModalCard.addView(subModalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, modalMaxH - UiHelper.dp(context, 96)));

        FrameLayout.LayoutParams smcLp = new FrameLayout.LayoutParams(
                modalW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        subModalHost.addView(subModalCard, smcLp);
        rootFrame.addView(subModalHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Link center play wrap focus nicely
        centerPlayWrap.setNextFocusDownId(View.NO_ID);
    }

    public View getView() {
        return rootFrame;
    }

    public boolean isShowing() {
        return showing;
    }

    public boolean isPlaying() {
        return showing && nativePlayer.isPlaying();
    }

    public String getCurrentTitle() {
        if (resolvedData != null && !resolvedData.title.isEmpty()) return resolvedData.title;
        return launchReq != null ? launchReq.title : "Đang phát phim";
    }

    public String getCurrentSubtitle() {
        if (resolvedData != null && !resolvedData.episodeTitle.isEmpty()) return resolvedData.episodeTitle;
        return launchReq != null ? launchReq.episodeTitle : "";
    }

    public long getPositionMs() {
        return Math.round(lastPosSec * 1000.0);
    }

    public long getDurationMs() {
        return Math.round(lastDurSec * 1000.0);
    }

    public void open(Models.PlayerLaunchRequest request) {
        if (request == null) return;
        this.launchReq = request;
        this.cachedRichEpisodes = null;
        this.selectedSeasonFilter = request.seasonId != null ? request.seasonId : "";
        this.showing = true;
        this.activeModal = MODAL_NONE;
        subModalHost.setVisibility(View.GONE);
        rootFrame.setVisibility(View.VISIBLE);
        if (listener != null) listener.onAudioFocusRequested(true);
        startEpisodeSession(request, false);
    }

    public void close() {
        if (!showing) return;
        persistProgressNow();
        showing = false;
        checkFilePolling = false;
        nextCountdownRunning = false;
        handler.removeCallbacksAndMessages(null);
        dubbingEngine.release();
        nativePlayer.stopMedia();
        subModalHost.setVisibility(View.GONE);
        rootFrame.setVisibility(View.GONE);
        if (listener != null) {
            listener.onAudioFocusRequested(false);
            listener.onPlayerClosed(true);
        }
    }

    public void onHostPause() {
        if (showing) {
            persistProgressNow();
            nativePlayer.onHostPause();
            dubbingEngine.pauseNow();
        }
    }

    public void release() {
        checkFilePolling = false;
        nextCountdownRunning = false;
        handler.removeCallbacksAndMessages(null);
        dubbingEngine.release();
        nativePlayer.release();
    }

    // ---- MediaSession / Direct Transport API ----

    public void togglePlayPause() {
        if (!showing) return;
        if (nativePlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
        setHudVisible(true);
    }

    public void play() {
        if (!showing) return;
        nativePlayer.play();
        playPauseIcon.setImageResource(R.drawable.ic_pause);
        resetHudAutoHide();
    }

    public void pause() {
        if (!showing) return;
        nativePlayer.pause();
        dubbingEngine.pauseNow();
        playPauseIcon.setImageResource(R.drawable.ic_play);
        setHudVisible(true);
    }

    public void seekBySec(double deltaSec) {
        if (!showing) return;
        double target = Math.max(0.0, lastPosSec + deltaSec);
        if (lastDurSec > 0) target = Math.min(lastDurSec, target);
        nativePlayer.seekTo(target);
        setHudVisible(true);
    }

    public void playNextEpisodeIfAvailable() {
        if (showing && nextEpisodeTarget != null) {
            playNextEpisode(false);
        }
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (!showing) return false;

        if (activeModal != MODAL_NONE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                closeSubModal();
                return true;
            }
            return false;
        }

        if (nextEpCard.getVisibility() == View.VISIBLE
                && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
            cancelNextCountdown(true);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) play();
            else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) pause();
            else togglePlayPause();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            seekBySec(-10);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBySec(10);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            playNextEpisodeIfAvailable();
            return true;
        }

        if (!hudVisible) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                close();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                togglePlayPause();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                seekBySec(-10);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                seekBySec(10);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setHudVisible(true);
                focusCenterPlayButton();
                return true;
            }
            setHudVisible(true);
            return true;
        }

        // HUD visible
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            setHudVisible(false);
            return true;
        }
        resetHudAutoHide();
        return false;
    }

    // ---- Episode Session Loading ----

    private void startEpisodeSession(final Models.PlayerLaunchRequest req, final boolean fromAutoNext) {
        final int seq = ++episodeSessionSeq;
        this.launchReq = req;
        this.resolvedData = null;
        this.serverStates.clear();
        this.erroredServerIds.clear();
        this.activeServerId = "";
        this.activeStreamUrl = "";
        this.lastPosSec = 0.0;
        this.lastDurSec = 0.0;
        this.autoSkippedIntroThisEp = false;
        this.initialResumeApplied = false;
        this.preloadAttemptedThisEp = false;
        this.preloadedNextUrl = "";
        this.nextCountdownDismissed = false;
        this.nextCountdownRunning = false;
        this.nextEpisodeTarget = null;
        this.checkFilePolling = false;
        handler.removeCallbacks(checkFilePollRunnable);
        handler.removeCallbacks(nextCountdownRunnable);

        if (fromAutoNext && Prefs.isAutoDubNext(context)) {
            Prefs.setSubAiVoiceEnabled(context, true);
        }

        if (fromAutoNext && Prefs.isTransitionEffect(context)) {
            transitionVeil.setVisibility(View.VISIBLE);
            transitionVeil.setAlpha(0.85f);
        }

        skipIntroBtn.setVisibility(View.GONE);
        nextEpCard.setVisibility(View.GONE);
        loadingCenter.setVisibility(View.VISIBLE);
        loadingSpinner.setSpinning(true);
        loadingStatusTv.setText("Đang giải mã nguồn phát phim...");
        subtitleOverlay.setCues(new ArrayList<SubtitleEngine.Cue>(), new ArrayList<SubtitleEngine.Cue>(), "");
        dubbingEngine.setEpisodeSignature(req.streamCode);

        hudTitleTv.setText(req.title);
        hudMetaTv.setText(!req.episodeTitle.isEmpty() ? req.episodeTitle : "Đang kết nối máy chủ...");
        setHudVisible(true);
        focusCenterPlayButton();
        refreshQuickBadges();

        apiClient.resolveStream(req.streamCode, req.videosId, new ApiClient.Callback<Models.StreamResolvedData>() {
            @Override
            public void onSuccess(Models.StreamResolvedData data) {
                if (!showing || seq != episodeSessionSeq) return;
                onStreamResolved(data, req);
            }

            @Override
            public void onError(String message, int httpCode) {
                if (!showing || seq != episodeSessionSeq) return;
                loadingCenter.setVisibility(View.GONE);
                fadeTransitionVeilOut();
                toast(message != null ? message : "Không thể tải luồng phát", UiHelper.COLOR_ACCENT_RED);
            }
        });
    }

    private void onStreamResolved(final Models.StreamResolvedData data, Models.PlayerLaunchRequest req) {
        this.resolvedData = data;
        if (data.movie != null) {
            wishlistLocalState = data.movie.inWishlist;
        }
        nextEpisodeTarget = computeNextEpisode(data, req);
        btnNextEpBar.setVisibility(nextEpisodeTarget != null ? View.VISIBLE : View.GONE);

        String title = !data.title.isEmpty() ? data.title : req.title;
        String epTitle = !data.episodeTitle.isEmpty() ? data.episodeTitle : req.episodeTitle;
        hudTitleTv.setText(title);
        hudMetaTv.setText(!epTitle.isEmpty() ? epTitle : "Đang chuẩn bị...");
        refreshQuickBadges();

        // Build Server List immediately from resolved payload
        ggdFlags = new ServerOrchestrator.GgdFlags();
        ggdFlags.ggdriver1 = data.ggdriver1;
        ggdFlags.ggdriver2 = data.ggdriver2;
        ggdFlags.ggdriver3 = data.ggdriver3;

        serverStates.clear();
        serverStates.addAll(serverOrchestrator.buildInitialServers(data.serverFileKey, data, ggdFlags, erroredServerIds));

        // Fallback quick start if primaryUrl is valid & Phiên 11/36 haven't checked yet
        if (!data.primaryUrl.isEmpty() && !data.primaryUrl.contains("check_file")) {
            startMediaStream(data.primaryUrl, data.streamType, "");
        }

        // Load Dual Subtitles & AI Voice signature
        vnCues.clear();
        enCues.clear();
        dubbingEngine.setEpisodeSignature(data.streamCode + "_" + data.serverFileKey);
        subtitleEngine.loadDualSubtitles(data.serverFileKey, data.subtitles, false, new SubtitleEngine.LoadCallback() {
            @Override
            public void onLoaded(SubtitleEngine.LoadResult result) {
                if (!showing) return;
                vnCues.clear();
                enCues.clear();
                vnCues.addAll(result.vnCues);
                enCues.addAll(result.enCues);
                subtitleOverlay.setCues(vnCues, enCues, resolvedData != null ? resolvedData.subHigh : "");
            }
        });

        // Probe pings, GGD, video quality, and check_file
        final int seq = episodeSessionSeq;
        serverOrchestrator.measurePings(new ServerOrchestrator.Callback<ServerOrchestrator.PingSnapshot>() {
            @Override
            public void onResult(ServerOrchestrator.PingSnapshot snap) {
                if (!showing || seq != episodeSessionSeq) return;
                for (ServerOrchestrator.ServerState s : serverStates) {
                    if ("11".equals(s.id)) {
                        s.pingMs = snap.ping11;
                        s.pingFailed = snap.ping11 < 0;
                    } else if ("36".equals(s.id)) {
                        s.pingMs = snap.ping36;
                        s.pingFailed = snap.ping36 < 0;
                    } else if ("40".equals(s.id)) {
                        s.pingMs = snap.ping40;
                    } else if ("3".equals(s.id)) {
                        s.pingMs = snap.ping3;
                    }
                }
                if (activeModal == MODAL_SERVERS) renderSubModalContent();
            }
        });

        if (!data.serverFileKey.isEmpty()) {
            serverOrchestrator.checkGgdriver(data.serverFileKey, data.serverMovieTitle, new ServerOrchestrator.Callback<ServerOrchestrator.GgdFlags>() {
                @Override
                public void onResult(ServerOrchestrator.GgdFlags flags) {
                    if (!showing || seq != episodeSessionSeq) return;
                    ggdFlags = flags;
                    List<ServerOrchestrator.ServerState> rebuilt = serverOrchestrator.buildInitialServers(
                            data.serverFileKey, data, flags, erroredServerIds);
                    serverStates.clear();
                    serverStates.addAll(rebuilt);
                    if (activeStreamUrl.isEmpty()) {
                        tryStartBestPlayableServer(false);
                    }
                    if (activeModal == MODAL_SERVERS) renderSubModalContent();
                }
            });

            checkFilePolling = true;
            handler.post(checkFilePollRunnable);

            serverOrchestrator.probeVideoQualityLabel(
                    data.serverFileKey,
                    activeServerId.isEmpty() ? "11" : activeServerId,
                    data.movie != null ? data.movie.videoQuality : "FHD",
                    new ServerOrchestrator.Callback<String>() {
                        @Override
                        public void onResult(String label) {
                            if (!showing || seq != episodeSessionSeq) return;
                            activeQualityLabel = label;
                            updateHudMetaText();
                        }
                    });
        } else {
            tryStartBestPlayableServer(false);
        }
    }

    private void tryStartBestPlayableServer(boolean preserveCurrentPos) {
        ServerOrchestrator.ServerState best = serverOrchestrator.selectOptimalServer(serverStates);
        if (best != null && !best.streamUrl.isEmpty()) {
            switchToServer(best, preserveCurrentPos);
        } else if (resolvedData != null && !resolvedData.primaryUrl.isEmpty() && activeStreamUrl.isEmpty()) {
            startMediaStream(resolvedData.primaryUrl, resolvedData.streamType, "fallback");
        }
    }

    private void switchToServer(ServerOrchestrator.ServerState server, boolean preservePos) {
        if (server == null || server.streamUrl.isEmpty()) return;
        this.activeServerId = server.id;
        if (preservePos && lastPosSec > 2.0) {
            initialResumeApplied = true;
            startMediaStreamAt(server.streamUrl, server.format, server.id, lastPosSec);
        } else {
            startMediaStream(server.streamUrl, server.format, server.id);
        }
        toast("Đang phát qua " + server.label, UiHelper.COLOR_CYAN);
        if (activeModal == MODAL_SERVERS) renderSubModalContent();
    }

    private void startMediaStream(String url, String type, String serverId) {
        double resume = 0.0;
        if (!initialResumeApplied) {
            initialResumeApplied = true;
            if (launchReq != null && launchReq.initialResumeSec > 5) {
                resume = launchReq.initialResumeSec;
            } else if (resolvedData != null && resolvedData.resumeSeconds > 5) {
                resume = resolvedData.resumeSeconds;
            }
        }
        startMediaStreamAt(url, type, serverId, resume);
    }

    private void startMediaStreamAt(String url, String type, String serverId, double startPosSec) {
        this.activeStreamUrl = url;
        this.activeServerId = serverId;
        applyResizeSetting();
        float rate = Prefs.getPlaybackRate(context);
        nativePlayer.loadMedia(url, type, startPosSec, false, false, rate);
        updateHudMetaText();
    }

    private void handlePlaybackState(boolean isPlaying, boolean isBuffering, boolean isEnded, boolean isReady) {
        this.isPlayingNow = isPlaying;
        playPauseIcon.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        if (isBuffering && activeStreamUrl.isEmpty()) {
            loadingCenter.setVisibility(View.VISIBLE);
            loadingSpinner.setSpinning(true);
            loadingStatusTv.setText("Đang đệm video...");
        } else if (isBuffering) {
            loadingCenter.setVisibility(View.VISIBLE);
            loadingSpinner.setSpinning(true);
            loadingStatusTv.setText("Đang tải khung hình...");
        } else if (isReady || isPlaying) {
            loadingCenter.setVisibility(View.GONE);
            fadeTransitionVeilOut();
        }

        if (!isPlaying) {
            dubbingEngine.pauseNow();
        }

        if (isEnded && nextEpisodeTarget != null && Prefs.isAutoPlayNext(context)) {
            cancelNextCountdown(false);
            playNextEpisode(true);
        }
        resetHudAutoHide();
    }

    private void handleProgress(double posSec, double durSec, int bufferedPct) {
        this.lastPosSec = posSec;
        this.lastDurSec = durSec;
        this.lastBufferedPct = bufferedPct;

        timeCurrentTv.setText(formatTime(posSec));
        timeDurationTv.setText(durSec > 0 ? formatTime(durSec) : "--:--");
        updateSeekBarVisual();

        subtitleOverlay.renderAt(posSec);
        SubtitleEngine.Cue activeCue = subtitleOverlay.getActiveMainVnCue(posSec);
        int activeIdx = subtitleOverlay.getActiveMainVnIndex(posSec);
        dubbingEngine.updateTick(posSec, isPlayingNow, false, vnCues, activeCue, activeIdx);

        // Save watch progress every 10s
        long nowElapsed = android.os.SystemClock.elapsedRealtime();
        if (isPlayingNow && (nowElapsed - lastProgressSaveElapsedMs) >= PROGRESS_SAVE_INTERVAL_MS) {
            lastProgressSaveElapsedMs = nowElapsed;
            persistProgressNow();
        }

        // Skip Intro check
        if (resolvedData != null && resolvedData.skipIntroSec > 6) {
            if (posSec >= 2.0 && posSec < resolvedData.skipIntroSec) {
                if (Prefs.isAutoSkipIntro(context) && !autoSkippedIntroThisEp) {
                    autoSkippedIntroThisEp = true;
                    nativePlayer.seekTo(resolvedData.skipIntroSec);
                    toast("Đã tự động bỏ qua phần giới thiệu", UiHelper.COLOR_CYAN);
                } else if (!Prefs.isAutoSkipIntro(context)) {
                    skipIntroBtn.setVisibility(View.VISIBLE);
                }
            } else {
                skipIntroBtn.setVisibility(View.GONE);
            }
        } else {
            skipIntroBtn.setVisibility(View.GONE);
        }

        // Seamless Preload + Next Episode Countdown
        if (durSec > 20.0 && nextEpisodeTarget != null) {
            double remSec = durSec - posSec;

            // Preload 65s before end
            if (Prefs.isSeamlessPreload(context) && !preloadAttemptedThisEp && remSec <= 65.0) {
                preloadAttemptedThisEp = true;
                resolveAndPreloadNextEpisode();
            }

            boolean triggerCredits = resolvedData != null && resolvedData.endCreditsSec > 30
                    && posSec >= resolvedData.endCreditsSec;
            int triggerWindow = Math.max(8, Prefs.getNextEpisodeDelaySec(context) + 2);
            if (Prefs.isAutoPlayNext(context) && !nextCountdownDismissed && !nextCountdownRunning
                    && (triggerCredits || remSec <= triggerWindow)) {
                startNextCountdown();
            }
        }
    }

    private void handleStreamError(String errMsg) {
        if (!showing) return;
        if (!activeServerId.isEmpty()) {
            erroredServerIds.add(activeServerId);
            for (ServerOrchestrator.ServerState s : serverStates) {
                if (s.id.equals(activeServerId)) {
                    s.errored = true;
                    s.status = ServerOrchestrator.STATUS_ERROR;
                    s.progressLabel = "Lỗi phát";
                }
            }
        }
        ServerOrchestrator.ServerState alt = serverOrchestrator.selectOptimalServer(serverStates);
        if (alt != null && !alt.streamUrl.equals(activeStreamUrl)) {
            toast("Đang tự chuyển sang " + alt.label + "...", UiHelper.COLOR_GOLD);
            switchToServer(alt, true);
        } else {
            loadingCenter.setVisibility(View.GONE);
            toast("Nguồn phát đang bận (" + errMsg + "). Hãy chọn Máy chủ khác.", UiHelper.COLOR_ACCENT_RED);
            openSubModal(MODAL_SERVERS);
        }
    }

    private void resolveAndPreloadNextEpisode() {
        if (nextEpisodeTarget == null || resolvedData == null) return;
        final String targetCode = nextEpisodeTarget.streamCode;
        final double nextIntro = (resolvedData.nextEpisodeMeta != null && resolvedData.nextEpisodeMeta.streamCode.equals(targetCode))
                ? resolvedData.nextEpisodeMeta.skipIntroSec : 0.0;
        apiClient.resolveStream(targetCode, launchReq != null ? launchReq.videosId : 0,
                new ApiClient.Callback<Models.StreamResolvedData>() {
                    @Override
                    public void onSuccess(Models.StreamResolvedData nextData) {
                        if (!showing || nextData == null) return;
                        List<ServerOrchestrator.ServerState> nextServers = serverOrchestrator.buildInitialServers(
                                nextData.serverFileKey, nextData, null, null);
                        ServerOrchestrator.ServerState opt = serverOrchestrator.selectOptimalServer(nextServers);
                        String url = (opt != null && !opt.streamUrl.isEmpty()) ? opt.streamUrl : nextData.primaryUrl;
                        String fmt = opt != null ? opt.format : nextData.streamType;
                        if (url != null && !url.isEmpty() && !url.contains("check_file")) {
                            preloadedNextUrl = url;
                            nativePlayer.preloadNext(url, fmt, nextIntro);
                        }
                    }

                    @Override
                    public void onError(String message, int httpCode) {
                    }
                });
    }

    private void startNextCountdown() {
        if (nextEpisodeTarget == null) return;
        nextCountdownRunning = true;
        nextCountdownRemaining = Math.max(3, Prefs.getNextEpisodeDelaySec(context));
        nextEpTitleTv.setText(nextEpisodeTarget.episodesName.isEmpty() ? "Tập tiếp theo" : nextEpisodeTarget.episodesName);
        nextEpCard.setVisibility(View.VISIBLE);
        handler.removeCallbacks(nextCountdownRunnable);
        handler.post(nextCountdownRunnable);
    }

    private void cancelNextCountdown(boolean dismissedByUser) {
        nextCountdownRunning = false;
        if (dismissedByUser) nextCountdownDismissed = true;
        handler.removeCallbacks(nextCountdownRunnable);
        nextEpCard.setVisibility(View.GONE);
    }

    private void playNextEpisode(boolean fromAutoNext) {
        if (nextEpisodeTarget == null || launchReq == null) return;
        Models.PlayerLaunchRequest nextReq = new Models.PlayerLaunchRequest();
        nextReq.videosId = launchReq.videosId;
        nextReq.streamCode = nextEpisodeTarget.streamCode;
        nextReq.episodeId = nextEpisodeTarget.episodesId;
        nextReq.episodeNumber = launchReq.episodeNumber + 1;
        nextReq.seasonId = nextEpisodeTarget.seasonsId > 0 ? String.valueOf(nextEpisodeTarget.seasonsId) : launchReq.seasonId;
        nextReq.title = launchReq.title;
        nextReq.episodeTitle = nextEpisodeTarget.episodesName;
        nextReq.posterUrl = launchReq.posterUrl;
        nextReq.thumbnailUrl = launchReq.thumbnailUrl;
        nextReq.initialResumeSec = 0;
        nextReq.episodes.addAll(!launchReq.episodes.isEmpty()
                ? launchReq.episodes
                : (resolvedData != null ? resolvedData.episodes : new ArrayList<Models.EpisodeItem>()));
        nextReq.seasons.addAll(!launchReq.seasons.isEmpty()
                ? launchReq.seasons
                : (resolvedData != null ? resolvedData.seasons : new ArrayList<Models.SeasonItem>()));
        startEpisodeSession(nextReq, fromAutoNext);
    }

    private Models.EpisodeItem computeNextEpisode(Models.StreamResolvedData data, Models.PlayerLaunchRequest req) {
        if (data != null && data.nextEpisode != null && !data.nextEpisode.streamCode.isEmpty()) {
            return data.nextEpisode;
        }
        List<Models.EpisodeItem> list = (data != null && !data.episodes.isEmpty()) ? data.episodes : req.episodes;
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i).streamCode.equals(req.streamCode)) {
                return list.get(i + 1);
            }
        }
        return null;
    }

    private void reloadCurrentEpisode() {
        if (launchReq == null) return;
        Models.PlayerLaunchRequest retry = new Models.PlayerLaunchRequest();
        retry.videosId = launchReq.videosId;
        retry.streamCode = launchReq.streamCode;
        retry.episodeId = launchReq.episodeId;
        retry.episodeNumber = launchReq.episodeNumber;
        retry.seasonId = launchReq.seasonId;
        retry.title = launchReq.title;
        retry.episodeTitle = launchReq.episodeTitle;
        retry.posterUrl = launchReq.posterUrl;
        retry.thumbnailUrl = launchReq.thumbnailUrl;
        retry.initialResumeSec = (int) Math.round(lastPosSec);
        retry.episodes.addAll(launchReq.episodes);
        retry.seasons.addAll(launchReq.seasons);
        startEpisodeSession(retry, false);
    }

    private void persistProgressNow() {
        if (launchReq == null || launchReq.videosId <= 0 || lastPosSec < 2.0) return;
        String code = resolvedData != null && !resolvedData.streamCode.isEmpty() ? resolvedData.streamCode : launchReq.streamCode;
        apiClient.saveWatchProgress(
                launchReq.videosId,
                code,
                lastPosSec,
                lastDurSec,
                launchReq.episodeNumber,
                launchReq.seasonId,
                getCurrentTitle());
    }

    // ---- HUD Helpers ----

    private void setHudVisible(boolean visible) {
        this.hudVisible = visible;
        handler.removeCallbacks(hudAutoHideRunnable);
        if (visible) {
            hudLayer.setVisibility(View.VISIBLE);
            hudLayer.animate().alpha(1f).setDuration(180).start();
            resetHudAutoHide();
        } else {
            hudLayer.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!hudVisible) hudLayer.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void resetHudAutoHide() {
        handler.removeCallbacks(hudAutoHideRunnable);
        if (hudVisible && activeModal == MODAL_NONE && isPlayingNow) {
            handler.postDelayed(hudAutoHideRunnable, HUD_AUTO_HIDE_MS);
        }
    }

    private void focusCenterPlayButton() {
        View v = rootFrame.findViewWithTag("play_center");
        if (v != null) {
            v.post(new Runnable() {
                @Override
                public void run() {
                    View t = rootFrame.findViewWithTag("play_center");
                    if (t != null) t.requestFocus();
                }
            });
        }
    }

    private void updateHudMetaText() {
        String ep = getCurrentSubtitle();
        String srvName = activeServerId.isEmpty() ? "Tự động" : findServerLabel(activeServerId);
        StringBuilder sb = new StringBuilder();
        if (!ep.isEmpty()) sb.append(ep).append("  •  ");
        sb.append(activeQualityLabel).append("  •  ").append(srvName);
        if (nativePlayer.isPreloadedFor(preloadedNextUrl)) {
            sb.append("  •  Sẵn sàng tập kế");
        }
        hudMetaTv.setText(sb.toString());
    }

    private String findServerLabel(String id) {
        for (ServerOrchestrator.ServerState s : serverStates) {
            if (s.id.equals(id)) return s.label;
        }
        if ("11".equals(id)) return "Phiên 11";
        if ("36".equals(id)) return "Phiên 36";
        if ("40".equals(id)) return "Phiên 40";
        if ("3".equals(id)) return "Hàn Quốc";
        return "Nguồn gốc";
    }

    private void updateSeekBarVisual() {
        View parent = (View) seekProgressFill.getParent();
        int trackW = parent.getWidth();
        if (trackW <= 0) return;

        float ratio = (lastDurSec > 0) ? (float) Math.max(0.0, Math.min(1.0, lastPosSec / lastDurSec)) : 0f;
        float bufRatio = Math.max(ratio, Math.min(1f, lastBufferedPct / 100f));

        seekBufferedFill.getLayoutParams().width = Math.round(trackW * bufRatio);
        seekBufferedFill.requestLayout();

        int progW = Math.round(trackW * ratio);
        seekProgressFill.getLayoutParams().width = progW;
        seekProgressFill.requestLayout();

        int dotMargin = Math.max(0, Math.min(trackW - UiHelper.dp(context, 14), progW - UiHelper.dp(context, 7)));
        ((FrameLayout.LayoutParams) seekThumbDot.getLayoutParams()).leftMargin = dotMargin;
        seekThumbDot.requestLayout();
    }

    private void refreshQuickBadges() {
        boolean fill = Prefs.isVideoFillCover(context);
        fillToggleText.setText(fill ? "Vừa khung" : "Tràn viền");
        ((ImageView) btnFillToggle.getChildAt(0)).setImageResource(fill ? R.drawable.ic_fit_screen : R.drawable.ic_fullscreen);

        btnAutoNextQuick.setText(Prefs.isAutoPlayNext(context) ? "Tự chuyển: BẬT" : "Tự chuyển: TẮT");
        UiHelper.setButtonSelectedVisual(btnDubQuick, context, 20, Prefs.isSubAiVoiceEnabled(context));

        wishlistIconBar.setImageResource(wishlistLocalState ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        wishlistTextBar.setText(wishlistLocalState ? "Đã lưu" : "Danh sách");
        UiHelper.setButtonSelectedVisual(btnWishlistBar, context, 20, wishlistLocalState);

        String key = launchReq != null ? String.valueOf(launchReq.videosId) : "";
        String react = Prefs.getReaction(context, key);
        UiHelper.setButtonSelectedVisual(btnLikeBar, context, 20, "like".equals(react));
        UiHelper.setButtonSelectedVisual(btnDislikeBar, context, 20, "dislike".equals(react));

        float r = Prefs.getPlaybackRate(context);
        speedTextBar.setText(formatRate(r));
    }

    private void toggleWishlist() {
        if (launchReq == null || launchReq.videosId <= 0) return;
        apiClient.toggleWishlist(launchReq.videosId, new ApiClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean added) {
                wishlistLocalState = added;
                refreshQuickBadges();
                toast(added ? "Đã thêm vào Danh sách của tôi" : "Đã bỏ khỏi Danh sách của tôi", UiHelper.COLOR_GREEN);
            }

            @Override
            public void onError(String message, int httpCode) {
                toast(message, UiHelper.COLOR_ACCENT_RED);
            }
        });
    }

    private void toggleReaction(String kind) {
        if (launchReq == null || launchReq.videosId <= 0) return;
        String key = String.valueOf(launchReq.videosId);
        String cur = Prefs.getReaction(context, key);
        String next = kind.equals(cur) ? "" : kind;
        Prefs.setReaction(context, key, next);
        refreshQuickBadges();
        toast("like".equals(next) ? "Đã thích phim" : ("dislike".equals(next) ? "Đã đánh dấu Không thích" : "Đã bỏ phản hồi"),
                UiHelper.COLOR_CYAN);
    }

    private void cycleSpeed() {
        float[] rates = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        float cur = Prefs.getPlaybackRate(context);
        int idx = 1;
        for (int i = 0; i < rates.length; i++) {
            if (Math.abs(rates[i] - cur) < 0.05f) {
                idx = (i + 1) % rates.length;
                break;
            }
        }
        applySpeedSetting(rates[idx]);
    }

    private void applySpeedSetting(float rate) {
        Prefs.setPlaybackRate(context, rate);
        nativePlayer.setPlaybackRate(rate);
        refreshQuickBadges();
        toast("Tốc độ phát: " + formatRate(rate), UiHelper.COLOR_CYAN);
    }

    private void applyResizeSetting() {
        nativePlayer.setResizeMode(Prefs.isVideoFillCover(context));
        refreshQuickBadges();
    }

    private void applyContrastSetting() {
        String level = Prefs.getContrastLevel(context);
        float alpha = 0f;
        if ("Cao".equals(level)) alpha = 0.14f;
        else if ("Rất cao".equals(level)) alpha = 0.28f;
        contrastOverlay.setAlpha(alpha);
    }

    private void fadeTransitionVeilOut() {
        if (transitionVeil.getVisibility() == View.VISIBLE) {
            transitionVeil.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() {
                @Override
                public void run() {
                    transitionVeil.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    // ---- 5 Borderless Liquid Glass Sub-Modals ----

    private void openSubModal(int modalType) {
        this.activeModal = modalType;
        handler.removeCallbacks(hudAutoHideRunnable);
        subModalHost.setAlpha(0f);
        subModalHost.setVisibility(View.VISIBLE);
        subModalHost.animate().alpha(1f).setDuration(180).start();
        subModalScroll.scrollTo(0, 0);
        renderSubModalContent();

        if (modalType == MODAL_EPISODES && cachedRichEpisodes == null) {
            loadRichEpisodesForModal();
        }
        subModalCloseBtn.post(new Runnable() {
            @Override
            public void run() {
                View focusTarget = findFirstFocusableChild(subModalBody);
                if (focusTarget != null) focusTarget.requestFocus();
                else subModalCloseBtn.requestFocus();
            }
        });
    }

    private void closeSubModal() {
        activeModal = MODAL_NONE;
        subModalHost.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
            @Override
            public void run() {
                subModalHost.setVisibility(View.GONE);
            }
        }).start();
        focusCenterPlayButton();
        resetHudAutoHide();
    }

    private void renderSubModalContent() {
        subModalBody.removeAllViews();
        if (activeModal == MODAL_EPISODES) {
            subModalTitleTv.setText("Danh sách tập phim");
            renderEpisodesModal();
        } else if (activeModal == MODAL_SERVERS) {
            subModalTitleTv.setText("Chọn máy chủ phát");
            renderServersModal();
        } else if (activeModal == MODAL_SUBTITLES) {
            subModalTitleTv.setText("Tùy chỉnh Phụ đề & Thuyết minh");
            renderSubtitlesAndDubModal();
        } else if (activeModal == MODAL_SETTINGS) {
            subModalTitleTv.setText("Cài đặt trình phát");
            renderSettingsModal();
        } else if (activeModal == MODAL_INFO || activeModal == MODAL_MEMBER) {
            subModalTitleTv.setText(activeModal == MODAL_INFO ? "Thông tin bộ phim" : "Hồ sơ thành viên");
            renderInfoAndMemberModal();
        }
    }

    private void loadRichEpisodesForModal() {
        if (launchReq == null) return;
        Models.User u = apiClient.getCachedUser();
        int uid = u != null && u.userId > 0 ? u.userId : 1;
        String srvMovie = resolvedData != null ? resolvedData.serverMovieTitle : launchReq.title;
        String srvTap = resolvedData != null ? resolvedData.serverTapPhim : "";
        final int seq = episodeSessionSeq;

        apiClient.fetchRichEpisodeList(uid, launchReq.title, launchReq.streamCode, srvMovie, srvTap, selectedSeasonFilter,
                new ApiClient.Callback<Models.EpisodeListApiResponse>() {
                    @Override
                    public void onSuccess(Models.EpisodeListApiResponse data) {
                        if (!showing || seq != episodeSessionSeq) return;
                        cachedRichEpisodes = data;
                        if (selectedSeasonFilter.isEmpty() && data != null) {
                            selectedSeasonFilter = !data.watchingSeasonId.isEmpty() ? data.watchingSeasonId : data.currentSeasonId;
                        }
                        if (activeModal == MODAL_EPISODES) renderSubModalContent();
                    }

                    @Override
                    public void onError(String message, int httpCode) {
                    }
                });
    }

    private void renderEpisodesModal() {
        // Season filter row
        List<Models.ApiSeasonItem> apiSeasons = cachedRichEpisodes != null ? cachedRichEpisodes.seasons : new ArrayList<Models.ApiSeasonItem>();
        List<Models.SeasonItem> fallbackSeasons = (resolvedData != null && !resolvedData.seasons.isEmpty())
                ? resolvedData.seasons : (launchReq != null ? launchReq.seasons : new ArrayList<Models.SeasonItem>());

        if (!apiSeasons.isEmpty() || !fallbackSeasons.isEmpty()) {
            HorizontalScrollView hsv = new HorizontalScrollView(context);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout sRow = new LinearLayout(context);
            sRow.setOrientation(LinearLayout.HORIZONTAL);
            hsv.addView(sRow);
            if (!apiSeasons.isEmpty()) {
                for (final Models.ApiSeasonItem as : apiSeasons) {
                    boolean active = as.seasonsId.equals(selectedSeasonFilter);
                    TextView chip = makeTextPill(context, as.seasonsName, active);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSeasonFilter = as.seasonsId;
                            cachedRichEpisodes = null;
                            renderSubModalContent();
                            loadRichEpisodesForModal();
                        }
                    });
                    sRow.addView(chip, rightMarginLp(10));
                }
            } else {
                for (final Models.SeasonItem fs : fallbackSeasons) {
                    final String idStr = String.valueOf(fs.seasonsId);
                    boolean active = idStr.equals(selectedSeasonFilter) || (selectedSeasonFilter.isEmpty() && fs == fallbackSeasons.get(0));
                    TextView chip = makeTextPill(context, fs.seasonsName, active);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSeasonFilter = idStr;
                            renderSubModalContent();
                        }
                    });
                    sRow.addView(chip, rightMarginLp(10));
                }
            }
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            hLp.bottomMargin = UiHelper.dp(context, 14);
            subModalBody.addView(hsv, hLp);
        }

        // Episodes from Rich API first, fallback to resolved episodes
        if (cachedRichEpisodes != null && !cachedRichEpisodes.data.isEmpty()) {
            for (int i = 0; i < cachedRichEpisodes.data.size(); i++) {
                final Models.ApiEpisodeItem ep = cachedRichEpisodes.data.get(i);
                final int num = i + 1;
                boolean current = ep.isWatching || (launchReq != null && ep.streamKey.equals(launchReq.streamCode));
                LinearLayout row = buildEpisodeRowCard(
                        buildEpisodeThumbUrl(ep.posterUrl, ep.streamKey),
                        ep.episodeName.isEmpty() ? ("Tập " + num) : ep.episodeName,
                        ep.watchedStatus + (ep.remainingTime.isEmpty() ? "" : " • " + ep.remainingTime),
                        ep.percentWatched,
                        current);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        closeSubModal();
                        switchToEpisodeCode(ep.streamKey, ep.episodeName, num, ep.seasonsId);
                    }
                });
                subModalBody.addView(row);
            }
            return;
        }

        List<Models.EpisodeItem> eps = (resolvedData != null && !resolvedData.episodes.isEmpty())
                ? resolvedData.episodes : (launchReq != null ? launchReq.episodes : new ArrayList<Models.EpisodeItem>());
        if (eps.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Bộ phim này chỉ có 1 tập đầy đủ.");
            empty.setTextColor(UiHelper.COLOR_TEXT_MUTED);
            empty.setTextSize(14f);
            subModalBody.addView(empty);
            return;
        }
        for (int i = 0; i < eps.size(); i++) {
            final Models.EpisodeItem ep = eps.get(i);
            final int num = i + 1;
            boolean current = launchReq != null && ep.streamCode.equals(launchReq.streamCode);
            LinearLayout row = buildEpisodeRowCard(
                    buildEpisodeThumbUrl("", ep.streamCode),
                    ep.episodesName.isEmpty() ? ("Tập " + num) : ep.episodesName,
                    current ? "Đang phát" : "Nhấn OK để phát",
                    current ? 100 : 0,
                    current);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSubModal();
                    switchToEpisodeCode(ep.streamCode, ep.episodesName, num, String.valueOf(ep.seasonsId));
                }
            });
            subModalBody.addView(row);
        }
    }

    private LinearLayout buildEpisodeRowCard(String thumbUrl, String title, String statusText, int progressPct, boolean current) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 10),
                UiHelper.dp(context, 16), UiHelper.dp(context, 10));
        UiHelper.applyFocusableGlassButton(row, context, 22, current);

        ImageView thumb = new ImageView(context);
        UiHelper.safeLoadImage(thumb, thumbUrl, R.drawable.default_poster, 14);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 122), UiHelper.dp(context, 70));
        tLp.rightMargin = UiHelper.dp(context, 14);
        row.addView(thumb, tLp);

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(context);
        t1.setText((current ? "▶ " : "") + title);
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(15f);
        t1.setTypeface(null, Typeface.BOLD);
        col.addView(t1);

        TextView t2 = new TextView(context);
        t2.setText(statusText != null && !statusText.isEmpty() ? statusText : "Sẵn sàng phát");
        t2.setTextColor(current ? UiHelper.COLOR_GREEN : UiHelper.COLOR_TEXT_MUTED);
        t2.setTextSize(12.5f);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        l2.topMargin = UiHelper.dp(context, 3);
        col.addView(t2, l2);

        if (progressPct > 0) {
            FrameLayout pTrack = new FrameLayout(context);
            pTrack.setBackgroundColor(Color.argb(60, 255, 255, 255));
            View pFill = new View(context);
            pFill.setBackgroundColor(UiHelper.COLOR_ACCENT_RED);
            pTrack.addView(pFill, new FrameLayout.LayoutParams(
                    UiHelper.dp(context, Math.round(160 * Math.min(100, progressPct) / 100f)), UiHelper.dp(context, 3)));
            LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 160), UiHelper.dp(context, 3));
            ptLp.topMargin = UiHelper.dp(context, 6);
            col.addView(pTrack, ptLp);
        }

        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = UiHelper.dp(context, 10);
        row.setLayoutParams(rLp);
        return row;
    }

    private void switchToEpisodeCode(String code, String epTitle, int epNum, String seasonId) {
        if (launchReq == null || code == null || code.isEmpty()) return;
        persistProgressNow();
        Models.PlayerLaunchRequest next = new Models.PlayerLaunchRequest();
        next.videosId = launchReq.videosId;
        next.streamCode = code;
        next.episodeNumber = epNum;
        next.seasonId = seasonId != null ? seasonId : launchReq.seasonId;
        next.title = launchReq.title;
        next.episodeTitle = epTitle != null ? epTitle : "";
        next.posterUrl = launchReq.posterUrl;
        next.thumbnailUrl = launchReq.thumbnailUrl;
        next.initialResumeSec = 0;
        next.episodes.addAll(launchReq.episodes);
        next.seasons.addAll(launchReq.seasons);
        startEpisodeSession(next, false);
    }

    private String buildEpisodeThumbUrl(String primary, String streamKey) {
        if (primary != null && !primary.trim().isEmpty()) return primary.trim();
        if (streamKey != null && !streamKey.trim().isEmpty()) {
            try {
                return "https://image.s1dangvietanh.io.vn/image.php?code="
                        + URLEncoder.encode(streamKey.trim(), "UTF-8").replace("+", "%20");
            } catch (Exception ignored) {
            }
        }
        return launchReq != null ? launchReq.posterUrl : "";
    }

    private void renderServersModal() {
        LinearLayout topTools = new LinearLayout(context);
        topTools.setOrientation(LinearLayout.HORIZONTAL);
        boolean autoOpt = Prefs.isAutoOptimalServer(context);
        TextView btnAutoOpt = makeTextPill(context, autoOpt ? "✓ Tự chọn máy chủ tối ưu: BẬT" : "Tự chọn máy chủ tối ưu: TẮT", autoOpt);
        btnAutoOpt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isAutoOptimalServer(context);
                Prefs.setAutoOptimalServer(context, next);
                renderSubModalContent();
            }
        });
        topTools.addView(btnAutoOpt, rightMarginLp(10));

        TextView btnPing = makeTextPill(context, "Đo lại tốc độ", false);
        btnPing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverOrchestrator.measurePings(new ServerOrchestrator.Callback<ServerOrchestrator.PingSnapshot>() {
                    @Override
                    public void onResult(ServerOrchestrator.PingSnapshot snap) {
                        for (ServerOrchestrator.ServerState s : serverStates) {
                            if ("11".equals(s.id)) s.pingMs = snap.ping11;
                            else if ("36".equals(s.id)) s.pingMs = snap.ping36;
                        }
                        if (activeModal == MODAL_SERVERS) renderSubModalContent();
                    }
                });
            }
        });
        topTools.addView(btnPing);
        LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ttLp.bottomMargin = UiHelper.dp(context, 14);
        subModalBody.addView(topTools, ttLp);

        for (final ServerOrchestrator.ServerState srv : serverStates) {
            boolean active = srv.id.equals(activeServerId);
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(UiHelper.dp(context, 18), UiHelper.dp(context, 14),
                    UiHelper.dp(context, 18), UiHelper.dp(context, 14));
            UiHelper.applyFocusableGlassButton(card, context, 24, active);

            LinearLayout left = new LinearLayout(context);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(context);
            title.setText(srv.label + (active ? "  •  ĐANG PHÁT" : ""));
            title.setTextColor(Color.WHITE);
            title.setTextSize(15.5f);
            title.setTypeface(null, Typeface.BOLD);
            left.addView(title);

            String eta = ServerOrchestrator.formatVietnameseEta(srv.etaText);
            TextView sub = new TextView(context);
            sub.setText(srv.subtitle + (!eta.isEmpty() ? "  •  Còn " + eta : ""));
            sub.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
            sub.setTextSize(12.5f);
            left.addView(sub);
            card.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout right = new LinearLayout(context);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            TextView status = new TextView(context);
            status.setText(srv.progressLabel);
            status.setTextColor(srv.isPlayable() ? UiHelper.COLOR_GREEN : UiHelper.COLOR_GOLD);
            status.setTextSize(13f);
            status.setTypeface(null, Typeface.BOLD);
            right.addView(status);

            if (srv.pingMs > 0 && !srv.pingFailed) {
                TextView pingTv = new TextView(context);
                pingTv.setText(srv.pingMs + " ms");
                pingTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                pingTv.setTextSize(11.5f);
                right.addView(pingTv);
            }
            card.addView(right);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (srv.isPlayable()) {
                        closeSubModal();
                        switchToServer(srv, true);
                    } else if (("11".equals(srv.id) || "36".equals(srv.id)) && resolvedData != null && !resolvedData.serverFileKey.isEmpty()) {
                        serverOrchestrator.triggerPrepare(resolvedData.serverFileKey);
                        toast("Đang yêu cầu máy chủ chuẩn bị luồng phát...", UiHelper.COLOR_GOLD);
                    } else {
                        toast("Máy chủ này chưa sẵn sàng cho phim hiện tại", UiHelper.COLOR_ACCENT_RED);
                    }
                }
            });
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cLp.bottomMargin = UiHelper.dp(context, 10);
            subModalBody.addView(card, cLp);
        }
    }

    private void renderSubtitlesAndDubModal() {
        // Section 1: Display Toggles
        addModalSectionLabel("Hiển thị phụ đề");
        LinearLayout toggles = new LinearLayout(context);
        toggles.setOrientation(LinearLayout.HORIZONTAL);
        TextView tVi = makeTogglePill("Tiếng Việt", Prefs.isSubShowVi(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setSubShowVi(context, !Prefs.isSubShowVi(context));
            }
        });
        TextView tEn = makeTogglePill("Tiếng Anh / Dịch", Prefs.isSubShowEn(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setSubShowEn(context, !Prefs.isSubShowEn(context));
            }
        });
        TextView tRev = makeTogglePill("Đảo vị trí 2 dòng", Prefs.isSubReverse(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setSubReverse(context, !Prefs.isSubReverse(context));
            }
        });
        TextView tAuto = makeTogglePill("Tự động cỡ & vị trí", Prefs.isSubAutoLayout(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setSubAutoLayout(context, !Prefs.isSubAutoLayout(context));
            }
        });
        toggles.addView(tVi, rightMarginLp(8));
        toggles.addView(tEn, rightMarginLp(8));
        toggles.addView(tRev, rightMarginLp(8));
        toggles.addView(tAuto);
        addSpacedView(toggles, 14);

        // Steppers: Font size, Position, Delay, Extend
        addModalSectionLabel("Kích thước, Vị trí & Đồng bộ thời gian");
        addStepperRow("Cỡ chữ thủ công", String.valueOf(Prefs.getSubFontStep(context)),
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubFontStep(context, Prefs.getSubFontStep(context) - 1);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubFontStep(context, Prefs.getSubFontStep(context) + 1);
                    }
                });
        addStepperRow("Vị trí từ đáy màn hình (%)", Prefs.getSubPositionPct(context) + "%",
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubPositionPct(context, Prefs.getSubPositionPct(context) - 2);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubPositionPct(context, Prefs.getSubPositionPct(context) + 2);
                    }
                });
        addStepperRow("Độ trễ phụ đề (giây)", String.format(Locale.US, "%+.1fs", Prefs.getSubDelaySec(context)),
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubDelaySec(context, Prefs.getSubDelaySec(context) - 0.5f);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubDelaySec(context, Prefs.getSubDelaySec(context) + 0.5f);
                    }
                });
        addStepperRow("Kéo dài thời gian hiển thị", String.format(Locale.US, "+%.1fs", Prefs.getSubExtendSec(context)),
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubExtendSec(context, Math.max(0f, Prefs.getSubExtendSec(context) - 0.5f));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubExtendSec(context, Prefs.getSubExtendSec(context) + 0.5f);
                    }
                });

        // Font, Color, Outline pickers
        addModalSectionLabel("Phông chữ & Màu sắc");
        HorizontalScrollView fontScroll = new HorizontalScrollView(context);
        fontScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout fontRow = new LinearLayout(context);
        fontScroll.addView(fontRow);
        String curFont = Prefs.getSubFont(context);
        for (final SubtitleEngine.FontOption f : SubtitleEngine.FONT_OPTIONS) {
            TextView tv = makeTextPill(context, f.label, f.value.equals(curFont));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setSubFont(context, f.value);
                    subtitleOverlay.renderAt(lastPosSec);
                    renderSubModalContent();
                }
            });
            fontRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(fontScroll, 10);

        HorizontalScrollView colorScroll = new HorizontalScrollView(context);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colorRow = new LinearLayout(context);
        colorScroll.addView(colorRow);
        String curColor = Prefs.getSubColor(context);
        for (final SubtitleEngine.ColorOption c : SubtitleEngine.COLOR_OPTIONS) {
            TextView tv = makeTextPill(context, c.label, c.key.equals(curColor));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setSubColor(context, c.key);
                    subtitleOverlay.renderAt(lastPosSec);
                    renderSubModalContent();
                }
            });
            colorRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(colorScroll, 10);

        HorizontalScrollView outScroll = new HorizontalScrollView(context);
        outScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout outRow = new LinearLayout(context);
        outScroll.addView(outRow);
        String curOut = Prefs.getSubOutline(context);
        for (final SubtitleEngine.OutlineOption o : SubtitleEngine.OUTLINE_OPTIONS) {
            TextView tv = makeTextPill(context, o.label, o.key.equals(curOut));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setSubOutline(context, o.key);
                    subtitleOverlay.renderAt(lastPosSec);
                    renderSubModalContent();
                }
            });
            outRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(outScroll, 14);

        // Translation Language (108 Languages)
        addModalSectionLabel("Dịch phụ đề trực tiếp (108 ngôn ngữ)");
        final String curLang = Prefs.getSubTranslateLang(context);
        LinearLayout langCtrl = new LinearLayout(context);
        langCtrl.setOrientation(LinearLayout.HORIZONTAL);
        langCtrl.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnLangOff = makeTextPill(context, "Tắt dịch (EN gốc)", curLang.isEmpty());
        btnLangOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setSubTranslateLang(context, "");
                subtitleOverlay.renderAt(lastPosSec);
                renderSubModalContent();
            }
        });
        langCtrl.addView(btnLangOff, rightMarginLp(8));

        TextView btnLangPrev = makeTextPill(context, "◀ Trước", false);
        btnLangPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleTranslateLanguage(-1);
            }
        });
        langCtrl.addView(btnLangPrev, rightMarginLp(8));

        TextView curLangLabel = new TextView(context);
        curLangLabel.setText(findTranslateLangName(curLang));
        curLangLabel.setTextColor(UiHelper.COLOR_CYAN);
        curLangLabel.setTextSize(14f);
        curLangLabel.setTypeface(null, Typeface.BOLD);
        curLangLabel.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 6), UiHelper.dp(context, 12), UiHelper.dp(context, 6));
        langCtrl.addView(curLangLabel, rightMarginLp(8));

        TextView btnLangNext = makeTextPill(context, "Sau ▶", false);
        btnLangNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleTranslateLanguage(1);
            }
        });
        langCtrl.addView(btnLangNext);
        addSpacedView(langCtrl, 14);

        // AI Voice Dubbing
        addModalSectionLabel("Thuyết minh thông minh (AI Voice)");
        LinearLayout dubRow = new LinearLayout(context);
        dubRow.setOrientation(LinearLayout.HORIZONTAL);
        dubRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dubToggle = makeTogglePill("Bật Thuyết minh AI", Prefs.isSubAiVoiceEnabled(context), new Runnable() {
            @Override
            public void run() {
                boolean next = !Prefs.isSubAiVoiceEnabled(context);
                Prefs.setSubAiVoiceEnabled(context, next);
                if (!next) dubbingEngine.pauseNow();
                refreshQuickBadges();
            }
        });
        dubRow.addView(dubToggle, rightMarginLp(8));

        String[] vModes = {"adaptive", "hoaimy", "namminh", "google"};
        String[] vLabels = {"Đổi giọng tự động", "Hoài My", "Nam Minh", "Google"};
        String curMode = Prefs.getSubAiVoiceMode(context);
        for (int i = 0; i < vModes.length; i++) {
            final String vm = vModes[i];
            TextView tv = makeTextPill(context, vLabels[i], vm.equals(curMode));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setSubAiVoiceMode(context, vm);
                    dubbingEngine.pauseNow();
                    renderSubModalContent();
                }
            });
            dubRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(dubRow, 10);

        addStepperRow("Âm lượng Thuyết minh", Prefs.getSubAiVoiceVolume(context) + "%",
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAiVoiceVolume(context, Prefs.getSubAiVoiceVolume(context) - 10);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAiVoiceVolume(context, Prefs.getSubAiVoiceVolume(context) + 10);
                    }
                });

        // Reload / Reset Actions
        LinearLayout utilRow = new LinearLayout(context);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView btnReloadSub = makeTextPill(context, "Tải lại phụ đề", false);
        btnReloadSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resolvedData == null) return;
                subtitleEngine.clearTranslationCache();
                dubbingEngine.clearCacheAndReset();
                subtitleEngine.loadDualSubtitles(resolvedData.serverFileKey, resolvedData.subtitles, true, new SubtitleEngine.LoadCallback() {
                    @Override
                    public void onLoaded(SubtitleEngine.LoadResult result) {
                        vnCues.clear();
                        enCues.clear();
                        vnCues.addAll(result.vnCues);
                        enCues.addAll(result.enCues);
                        subtitleOverlay.setCues(vnCues, enCues, resolvedData.subHigh);
                        toast("Đã tải lại phụ đề mới nhất", UiHelper.COLOR_GREEN);
                    }
                });
            }
        });
        utilRow.addView(btnReloadSub, rightMarginLp(10));

        TextView btnResetSub = makeTextPill(context, "Khôi phục mặc định", true);
        btnResetSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.resetSubtitlePrefs(context);
                dubbingEngine.pauseNow();
                subtitleOverlay.renderAt(lastPosSec);
                refreshQuickBadges();
                renderSubModalContent();
                toast("Đã khôi phục cài đặt phụ đề mặc định", UiHelper.COLOR_CYAN);
            }
        });
        utilRow.addView(btnResetSub);
        subModalBody.addView(utilRow);
    }

    private void renderSettingsModal() {
        addModalSectionLabel("Tốc độ phát video");
        float[] rates = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        float curRate = Prefs.getPlaybackRate(context);
        LinearLayout speedRow = new LinearLayout(context);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final float r : rates) {
            TextView tv = makeTextPill(context, formatRate(r), Math.abs(curRate - r) < 0.05f);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applySpeedSetting(r);
                    renderSubModalContent();
                }
            });
            speedRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(speedRow, 14);

        addModalSectionLabel("Chế độ phát & Chuyển tập");
        LinearLayout r1 = new LinearLayout(context);
        r1.addView(makeTogglePill("Tràn viền màn hình", Prefs.isVideoFillCover(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setVideoFillCover(context, !Prefs.isVideoFillCover(context));
                applyResizeSetting();
            }
        }), rightMarginLp(8));
        r1.addView(makeTogglePill("Tự chuyển tập", Prefs.isAutoPlayNext(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setAutoPlayNext(context, !Prefs.isAutoPlayNext(context));
                refreshQuickBadges();
            }
        }), rightMarginLp(8));
        r1.addView(makeTogglePill("Tải trước tập kế mượt mà", Prefs.isSeamlessPreload(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setSeamlessPreload(context, !Prefs.isSeamlessPreload(context));
            }
        }));
        addSpacedView(r1, 8);

        LinearLayout r2 = new LinearLayout(context);
        r2.addView(makeTogglePill("Hiệu ứng chuyển cảnh", Prefs.isTransitionEffect(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setTransitionEffect(context, !Prefs.isTransitionEffect(context));
            }
        }), rightMarginLp(8));
        r2.addView(makeTogglePill("Tự bỏ qua giới thiệu", Prefs.isAutoSkipIntro(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setAutoSkipIntro(context, !Prefs.isAutoSkipIntro(context));
            }
        }), rightMarginLp(8));
        r2.addView(makeTogglePill("Giữ Thuyết minh khi chuyển tập", Prefs.isAutoDubNext(context), new Runnable() {
            @Override
            public void run() {
                Prefs.setAutoDubNext(context, !Prefs.isAutoDubNext(context));
            }
        }));
        addSpacedView(r2, 14);

        addModalSectionLabel("Thời gian đếm ngược chuyển tập");
        int[] delays = {5, 10, 15, 20};
        int curDelay = Prefs.getNextEpisodeDelaySec(context);
        LinearLayout dRow = new LinearLayout(context);
        for (final int sec : delays) {
            TextView tv = makeTextPill(context, sec + " giây", sec == curDelay);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setNextEpisodeDelaySec(context, sec);
                    renderSubModalContent();
                }
            });
            dRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(dRow, 14);

        addModalSectionLabel("Độ tương phản dịu mắt (Phòng tối / Máy chiếu)");
        List<String> contrasts = Arrays.asList("Thấp", "Mặc định", "Cao", "Rất cao");
        String curContrast = Prefs.getContrastLevel(context);
        LinearLayout cRow = new LinearLayout(context);
        for (final String lv : contrasts) {
            TextView tv = makeTextPill(context, lv, lv.equals(curContrast));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setContrastLevel(context, lv);
                    applyContrastSetting();
                    renderSubModalContent();
                }
            });
            cRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(cRow, 14);

        addModalSectionLabel("Hẹn giờ tự động tắt phát");
        int[] sleepOpts = {0, 15, 30, 60, 90};
        LinearLayout slRow = new LinearLayout(context);
        for (final int min : sleepOpts) {
            TextView tv = makeTextPill(context, min == 0 ? "Tắt" : (min + " phút"), min == sleepMinutesSetting);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sleepMinutesSetting = min;
                    handler.removeCallbacks(sleepTickRunnable);
                    if (min == 0) {
                        sleepTargetElapsedMs = 0L;
                        sleepBadgeTv.setVisibility(View.GONE);
                        toast("Đã tắt Hẹn giờ phát", UiHelper.COLOR_TEXT_MUTED);
                    } else {
                        sleepTargetElapsedMs = android.os.SystemClock.elapsedRealtime() + (min * 60000L);
                        handler.post(sleepTickRunnable);
                        toast("Sẽ tự tắt sau " + min + " phút", UiHelper.COLOR_GOLD);
                    }
                    renderSubModalContent();
                }
            });
            slRow.addView(tv, rightMarginLp(8));
        }
        addSpacedView(slRow, 16);

        TextView btnReload = makeTextPill(context, "Tải lại trình phát hiện tại", true);
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSubModal();
                reloadCurrentEpisode();
            }
        });
        subModalBody.addView(btnReload);
    }

    private void renderInfoAndMemberModal() {
        LinearLayout tabs = new LinearLayout(context);
        TextView tInfo = makeTextPill(context, "Thông tin phim", activeModal == MODAL_INFO);
        tInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeModal = MODAL_INFO;
                renderSubModalContent();
            }
        });
        TextView tMem = makeTextPill(context, "Hồ sơ thành viên", activeModal == MODAL_MEMBER);
        tMem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeModal = MODAL_MEMBER;
                renderSubModalContent();
            }
        });
        tabs.addView(tInfo, rightMarginLp(10));
        tabs.addView(tMem);
        addSpacedView(tabs, 16);

        if (activeModal == MODAL_INFO) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 16), UiHelper.dp(context, 16), UiHelper.dp(context, 16));
            UiHelper.applyGlassCardStyle(card, context, 28, UiHelper.COLOR_SURFACE_CARD, 14);

            ImageView poster = new ImageView(context);
            String pUrl = resolvedData != null && resolvedData.movie != null && !resolvedData.movie.posterUrl.isEmpty()
                    ? resolvedData.movie.posterUrl : (launchReq != null ? launchReq.posterUrl : "");
            UiHelper.safeLoadImage(poster, pUrl, R.drawable.default_poster, 18);
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 130), UiHelper.dp(context, 190));
            pLp.rightMargin = UiHelper.dp(context, 18);
            card.addView(poster, pLp);

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(context);
            title.setText(getCurrentTitle());
            title.setTextColor(Color.WHITE);
            title.setTextSize(19f);
            title.setTypeface(null, Typeface.BOLD);
            col.addView(title);

            Models.StreamMovieContext mc = resolvedData != null ? resolvedData.movie : null;
            if (mc != null) {
                TextView meta = new TextView(context);
                meta.setText(mc.videoQuality + (mc.year.isEmpty() ? "" : " • " + mc.year)
                        + (mc.imdbRating.isEmpty() ? "" : " • IMDb ★ " + mc.imdbRating));
                meta.setTextColor(UiHelper.COLOR_GOLD);
                meta.setTextSize(13f);
                col.addView(meta);

                TextView desc = new TextView(context);
                desc.setText(mc.description.isEmpty() ? "Chưa có mô tả chi tiết." : mc.description);
                desc.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
                desc.setTextSize(13.5f);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                dlp.topMargin = UiHelper.dp(context, 10);
                col.addView(desc, dlp);
            }
            card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            addSpacedView(card, 14);

            LinearLayout acts = new LinearLayout(context);
            TextView wBtn = makeTextPill(context, wishlistLocalState ? "✓ Đã trong Danh sách" : "+ Thêm vào Danh sách", wishlistLocalState);
            wBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleWishlist();
                    renderSubModalContent();
                }
            });
            acts.addView(wBtn);
            subModalBody.addView(acts);
            return;
        }

        // Member VIP Card
        Models.StreamMemberInfo mem = resolvedData != null ? resolvedData.member : null;
        Models.User user = apiClient.getCachedUser();

        LinearLayout mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.HORIZONTAL);
        mCard.setGravity(Gravity.CENTER_VERTICAL);
        mCard.setPadding(UiHelper.dp(context, 22), UiHelper.dp(context, 20), UiHelper.dp(context, 22), UiHelper.dp(context, 20));
        UiHelper.applyGlassCardStyle(mCard, context, 28, UiHelper.COLOR_SURFACE_CARD, 14);

        ImageView av = new ImageView(context);
        String avUrl = (mem != null && !mem.avatarUrl.isEmpty()) ? mem.avatarUrl : (user != null ? user.avatarUrl : "");
        UiHelper.safeLoadAvatar(av, avUrl);
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 78), UiHelper.dp(context, 78));
        avLp.rightMargin = UiHelper.dp(context, 20);
        mCard.addView(av, avLp);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        String dName = (mem != null && !mem.name.isEmpty()) ? mem.name : (user != null ? user.name : "Thành viên Đặng Việt Anh");
        TextView nTv = new TextView(context);
        nTv.setText(dName);
        nTv.setTextColor(Color.WHITE);
        nTv.setTextSize(19f);
        nTv.setTypeface(null, Typeface.BOLD);
        info.addView(nTv);

        String rankTxt = (mem != null ? mem.rank : (user != null ? user.role : "Thành viên"))
                + (mem != null && mem.isVip ? "  •  VIP còn " + mem.daysLeft + " ngày" : "");
        TextView rTv = new TextView(context);
        rTv.setText(rankTxt);
        rTv.setTextColor(mem != null && mem.isVip ? UiHelper.COLOR_GOLD : UiHelper.COLOR_GREEN);
        rTv.setTextSize(14f);
        rTv.setTypeface(null, Typeface.BOLD);
        info.addView(rTv);

        if (mem != null) {
            TextView stats = new TextView(context);
            stats.setText("Điểm tích luỹ: " + mem.points
                    + (!mem.joinDate.isEmpty() ? "  •  Tham gia: " + mem.joinDate : "")
                    + (!mem.expiryDate.isEmpty() ? "  •  Hạn VIP: " + mem.expiryDate : ""));
            stats.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
            stats.setTextSize(13f);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stLp.topMargin = UiHelper.dp(context, 6);
            info.addView(stats, stLp);
        }
        mCard.addView(info);
        subModalBody.addView(mCard);
    }

    // ---- Modal Helper Widgets ----

    private void addModalSectionLabel(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(context, 8);
        subModalBody.addView(tv, lp);
    }

    private void addSpacedView(View v, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(context, bottomDp);
        subModalBody.addView(v, lp);
    }

    private void addStepperRow(String label, String value, final Runnable onDec, final Runnable onInc) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 8),
                UiHelper.dp(context, 14), UiHelper.dp(context, 8));
        UiHelper.applyGlassCardStyle(row, context, 22, UiHelper.COLOR_SURFACE_CARD, 8);

        TextView lbl = new TextView(context);
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(14f);
        row.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView minus = makeTextPill(context, "−", false);
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDec.run();
                subtitleOverlay.renderAt(lastPosSec);
                renderSubModalContent();
            }
        });
        row.addView(minus, rightMarginLp(10));

        TextView valTv = new TextView(context);
        valTv.setText(value);
        valTv.setTextColor(UiHelper.COLOR_CYAN);
        valTv.setTextSize(14f);
        valTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valTv.setGravity(Gravity.CENTER);
        valTv.setMinWidth(UiHelper.dp(context, 62));
        row.addView(valTv, rightMarginLp(10));

        TextView plus = makeTextPill(context, "+", false);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInc.run();
                subtitleOverlay.renderAt(lastPosSec);
                renderSubModalContent();
            }
        });
        row.addView(plus);

        addSpacedView(row, 8);
    }

    private TextView makeTogglePill(String label, boolean on, final Runnable onToggle) {
        TextView tv = makeTextPill(context, (on ? "✓ " : "") + label, on);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggle.run();
                subtitleOverlay.renderAt(lastPosSec);
                renderSubModalContent();
            }
        });
        return tv;
    }

    private void cycleTranslateLanguage(int step) {
        List<SubtitleEngine.LangOption> langs = SubtitleEngine.TRANSLATE_LANGUAGES;
        if (langs.isEmpty()) return;
        String cur = Prefs.getSubTranslateLang(context);
        int idx = 0;
        if (!cur.isEmpty()) {
            for (int i = 0; i < langs.size(); i++) {
                if (langs.get(i).code.equals(cur)) {
                    idx = (i + step + langs.size()) % langs.size();
                    break;
                }
            }
        } else if (step < 0) {
            idx = langs.size() - 1;
        }
        String newCode = langs.get(idx).code;
        Prefs.setSubTranslateLang(context, newCode);
        Prefs.setSubShowEn(context, true);
        subtitleOverlay.renderAt(lastPosSec);
        renderSubModalContent();
    }

    private String findTranslateLangName(String code) {
        if (code == null || code.isEmpty()) return "Đang tắt (Phụ đề EN gốc)";
        for (SubtitleEngine.LangOption l : SubtitleEngine.TRANSLATE_LANGUAGES) {
            if (l.code.equals(code)) return l.name;
        }
        return code;
    }

    private View findFirstFocusableChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) return child;
            if (child instanceof ViewGroup) {
                View found = findFirstFocusableChild((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView makeTextPill(Context ctx, String text, boolean accent) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7));
        UiHelper.applyFocusableGlassButton(tv, ctx, 14, accent);
        return tv;
    }

    private LinearLayout makeIconTextPill(Context ctx, int iconRes, String label) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7));
        UiHelper.applyFocusableGlassButton(row, ctx, 14, false);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 18));
        iLp.rightMargin = UiHelper.dp(ctx, 8);
        row.addView(iv, iLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv);
        return row;
    }

    private LinearLayout makeIconOnlyCircle(Context ctx, int iconRes, int sizeDp, boolean accent) {
        LinearLayout box = new LinearLayout(ctx);
        box.setGravity(Gravity.CENTER);
        UiHelper.applyFocusableGlassButton(box, ctx, sizeDp / 2, accent);
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        box.addView(iv, new LinearLayout.LayoutParams(UiHelper.dp(ctx, 24), UiHelper.dp(ctx, 24)));
        box.setLayoutParams(new LinearLayout.LayoutParams(UiHelper.dp(ctx, sizeDp), UiHelper.dp(ctx, sizeDp)));
        return box;
    }

    private LinearLayout.LayoutParams rightMarginLp(int dpVal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(context, dpVal);
        return lp;
    }

    private void toast(String msg, int color) {
        if (listener != null) listener.onShowToast(msg, color);
    }

    private static String formatRate(float r) {
        if (Math.abs(r - 1.0f) < 0.02f) return "1.0x";
        if (Math.abs(r - Math.round(r)) < 0.02f) return ((int) Math.round(r)) + ".0x";
        return String.format(Locale.US, "%.2fx", r).replace("0x", "x");
    }

    private static String formatTime(double totalSec) {
        int sec = Math.max(0, (int) Math.round(totalSec));
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
}
