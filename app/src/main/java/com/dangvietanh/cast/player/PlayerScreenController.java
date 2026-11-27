package com.dangvietanh.cast.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
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
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final Activity hostActivity;
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

    // Center Buffering Spinner (replaces center Play/Pause icon when loading/buffering)
    private final FrameLayout centerPlayWrap;
    private final UiHelper.SpinnerView centerPlaySpinner;
    private final FrameLayout detachedBufferingHost;
    private final UiHelper.SpinnerView detachedBufferingSpinner;
    private boolean isLoadingOrBuffering = false;

    // Skip Intro, Resume Recovery & Auto-Next Cards
    private final TextView skipIntroBtn;
    private final TextView resumeRecoveryBtn;
    private final LinearLayout nextEpCard;
    private final TextView nextEpTitleTv;
    private final TextView nextEpCountdownTv;
    private final TextView btnNextPlayNow;
    private final TextView btnNextCancel;

    // HUD Controls (iOS / Apple TV Minimalist Frosted Layout)
    private final FrameLayout hudLayer;
    private final FrameLayout btnBackHud;
    private final TextView hudTitleTv;
    private final TextView hudMetaTv;
    private final TextView sleepBadgeTv;
    private final FrameLayout btnDubTop;
    private final ImageView dubTopIcon;
    private final FrameLayout btnDockContrast;
    private final ImageView dockContrastIcon;
    private final FrameLayout btnDockAutoNext;
    private final ImageView dockAutoNextIcon;
    private final FrameLayout btnDockFill;
    private final ImageView dockFillIcon;
    private final ImageView playPauseIcon;

    // Top-Center Floating Player Toast
    private final LinearLayout playerToastPill;
    private final ImageView playerToastIcon;
    private final TextView playerToastMsgTv;
    private Runnable hidePlayerToastRunnable;

    // Interactive SeekBar
    private final FrameLayout seekBarFocusBox;
    private final FrameLayout seekTrackContainer;
    private final View seekBufferedFill;
    private final View seekProgressFill;
    private final View seekIntroMarker;
    private final View seekCreditsMarker;
    private final View seekSeamlessMarker;
    private final View seekThumbDot;
    private final TextView timeCombinedTv;
    private final TextView seekCueNoteTv;

    // Bottom Bar Actions
    private final LinearLayout btnMemberPill;
    private final ImageView memberAvatarIv;
    private final FrameLayout btnWishlistBar;
    private final ImageView wishlistIconBar;
    private final FrameLayout btnEpisodesBar;
    private final FrameLayout btnLikeBar;
    private final ImageView likeIconBar;
    private final FrameLayout btnDislikeBar;
    private final ImageView dislikeIconBar;
    private final LinearLayout btnSpeedBar;
    private final TextView speedTextBar;

    // Sub-Modal Overlay
    private final FrameLayout subModalHost;
    private final LinearLayout subModalCard;
    private final TextView subModalTitleTv;
    private final TextView subModalCloseBtn;
    private final ScrollView subModalScroll;
    private final LinearLayout subModalBody;
    private final int screenW;
    private final int screenH;

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
    private boolean safeResumeChecked = false;
    private boolean episodeEndedNotified = false;
    private double savedResumeGuardSec = 0.0;
    private boolean pendingInitialSeekActive = false;
    private double pendingInitialSeekTargetSec = 0.0;
    private boolean resumePositionVerified = true;
    private boolean resumeRecoveryToastShown = false;
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
                    List<ServerOrchestrator.ServerState> updated = serverOrchestrator.applyCheckFile(
                            serverStates, sfk, res, (long) lastDurSec);
                    serverStates.clear();
                    serverStates.addAll(updated);

                    if (activeModal == MODAL_SERVERS) {
                        refreshServersModalPreservingFocus();
                    }
                    if (activeStreamUrl.isEmpty() && isLoadingOrBuffering && res != null) {
                        hudMetaTv.setText("Đang chuẩn bị máy chủ (" + res.progressNum + "%)...");
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
        this.hostActivity = (context instanceof Activity) ? (Activity) context : null;
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
                        updateBufferingIndicator(false);
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
        UiHelper.makeNonFocusable(rootFrame);

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

        // 5. Detached Center Buffering Spinner (shown at exact Play/Pause center when HUD is hidden)
        detachedBufferingHost = new FrameLayout(context);
        detachedBufferingHost.setFocusable(false);
        detachedBufferingHost.setClickable(false);
        detachedBufferingHost.setBackground(UiHelper.createPillDrawable(
                context, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
        detachedBufferingHost.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), UiHelper.GLASS_RADIUS_DP));
            }
        });
        detachedBufferingHost.setClipToOutline(true);
        detachedBufferingSpinner = new UiHelper.SpinnerView(context);
        detachedBufferingSpinner.setSpinning(false);
        detachedBufferingHost.addView(detachedBufferingSpinner, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 34), UiHelper.dp(context, 34), Gravity.CENTER));
        detachedBufferingHost.setVisibility(View.GONE);
        rootFrame.addView(detachedBufferingHost, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 62), UiHelper.dp(context, 62), Gravity.CENTER));

        // 6. Skip Intro Pill
        skipIntroBtn = new TextView(context);
        skipIntroBtn.setText("Bỏ qua giới thiệu →");
        skipIntroBtn.setTextColor(Color.WHITE);
        skipIntroBtn.setTextSize(14f);
        skipIntroBtn.setTypeface(null, Typeface.BOLD);
        skipIntroBtn.setPadding(UiHelper.dp(context, 20), UiHelper.dp(context, 11),
                UiHelper.dp(context, 20), UiHelper.dp(context, 11));
        applyPlayerFocusButton(skipIntroBtn, context, 24, false, 1.07f);
        skipIntroBtn.setVisibility(View.GONE);
        skipIntroBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resolvedData != null && resolvedData.skipIntroSec > 0) {
                    nativePlayer.seekTo(resolvedData.skipIntroSec);
                    skipIntroBtn.setVisibility(View.GONE);
                    syncPlayerFocusLayers();
                    toast("Đã bỏ qua giới thiệu", UiHelper.COLOR_CYAN);
                    if (hudVisible) focusCenterPlayButton();
                    resetHudAutoHide();
                }
            }
        });
        FrameLayout.LayoutParams siLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        siLp.setMargins(0, 0, UiHelper.dp(context, 36), UiHelper.dp(context, 116));
        rootFrame.addView(skipIntroBtn, siLp);

        // 6B. Saved Watch Position Recovery Pill (protects & restores previous watch position)
        resumeRecoveryBtn = new TextView(context);
        resumeRecoveryBtn.setId(View.generateViewId());
        resumeRecoveryBtn.setText("↺ Tua tới vị trí cũ");
        resumeRecoveryBtn.setTextColor(Color.WHITE);
        resumeRecoveryBtn.setTextSize(13.5f);
        resumeRecoveryBtn.setTypeface(null, Typeface.BOLD);
        resumeRecoveryBtn.setPadding(UiHelper.dp(context, 18), UiHelper.dp(context, 10),
                UiHelper.dp(context, 18), UiHelper.dp(context, 10));
        applyPlayerFocusButton(resumeRecoveryBtn, context, 24, true, 1.07f);
        resumeRecoveryBtn.setVisibility(View.GONE);
        resumeRecoveryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (savedResumeGuardSec > 5.0) {
                    double target = savedResumeGuardSec;
                    if (lastDurSec > 20.0 && target >= lastDurSec - 5.0) {
                        target = Math.max(0.0, lastDurSec - 15.0);
                    }
                    pendingInitialSeekActive = false;
                    resumePositionVerified = true;
                    resumeRecoveryBtn.setVisibility(View.GONE);
                    syncPlayerFocusLayers();
                    nativePlayer.seekTo(target);
                    toast("Đã tua tới vị trí cũ (" + formatTime(target) + ")", UiHelper.COLOR_CYAN);
                    if (hudVisible) focusCenterPlayButton();
                    resetHudAutoHide();
                }
            }
        });
        FrameLayout.LayoutParams rrLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        rrLp.setMargins(UiHelper.dp(context, 24), 0, 0, UiHelper.dp(context, 116));
        rootFrame.addView(resumeRecoveryBtn, rrLp);

        // 7. Auto-Next Episode Countdown Card
        nextEpCard = new LinearLayout(context);
        nextEpCard.setOrientation(LinearLayout.VERTICAL);
        nextEpCard.setClipChildren(false);
        nextEpCard.setClipToPadding(false);
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
        nxBtns.setClipChildren(false);
        nxBtns.setClipToPadding(false);
        btnNextPlayNow = makeTextPill(context, "▶  Phát ngay", true);
        btnNextPlayNow.setId(View.generateViewId());
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
        btnNextCancel.setId(View.generateViewId());
        btnNextPlayNow.setNextFocusLeftId(btnNextPlayNow.getId());
        btnNextPlayNow.setNextFocusRightId(btnNextCancel.getId());
        btnNextCancel.setNextFocusLeftId(btnNextPlayNow.getId());
        btnNextCancel.setNextFocusRightId(btnNextCancel.getId());
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

        // 8. Main HUD Layer (Matching iOS Minimalist Frosted Player Layout)
        hudLayer = new FrameLayout(context);
        hudLayer.setClipChildren(false);
        hudLayer.setClipToPadding(false);
        rootFrame.setClipChildren(false);
        rootFrame.addView(hudLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View topGrad = new View(context);
        topGrad.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(185, 7, 9, 15), Color.TRANSPARENT}));
        hudLayer.addView(topGrad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 108), Gravity.TOP));

        View botGrad = new View(context);
        botGrad.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, Color.argb(215, 7, 9, 15)}));
        hudLayer.addView(botGrad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 150), Gravity.BOTTOM));

        // 8A. Top Bar Container (Left: Back + Title/Meta; Right: Icon Groups + Gear)
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        topBar.setPadding(UiHelper.dp(context, 20), UiHelper.dp(context, 12),
                UiHelper.dp(context, 20), UiHelper.dp(context, 6));

        btnBackHud = makeHudCircleIcon(context, R.drawable.ic_back, 36);
        btnBackHud.setId(View.generateViewId());
        btnBackHud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        LinearLayout.LayoutParams bbLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 36), UiHelper.dp(context, 36));
        bbLp.rightMargin = UiHelper.dp(context, 12);
        topBar.addView(btnBackHud, bbLp);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        hudTitleTv = new TextView(context);
        hudTitleTv.setTextColor(Color.WHITE);
        hudTitleTv.setTextSize(15.5f);
        hudTitleTv.setTypeface(null, Typeface.BOLD);
        hudTitleTv.setMaxLines(1);
        hudTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        hudTitleTv.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        titleCol.addView(hudTitleTv);

        LinearLayout metaLine = new LinearLayout(context);
        metaLine.setOrientation(LinearLayout.HORIZONTAL);
        metaLine.setGravity(Gravity.CENTER_VERTICAL);
        hudMetaTv = new TextView(context);
        hudMetaTv.setTextColor(Color.argb(215, 225, 232, 244));
        hudMetaTv.setTextSize(11.5f);
        hudMetaTv.setMaxLines(1);
        hudMetaTv.setEllipsize(TextUtils.TruncateAt.END);
        metaLine.addView(hudMetaTv);

        sleepBadgeTv = new TextView(context);
        sleepBadgeTv.setTextColor(UiHelper.COLOR_GOLD);
        sleepBadgeTv.setTextSize(10.5f);
        sleepBadgeTv.setTypeface(null, Typeface.BOLD);
        sleepBadgeTv.setPadding(UiHelper.dp(context, 8), UiHelper.dp(context, 1), UiHelper.dp(context, 8), UiHelper.dp(context, 1));
        UiHelper.applySubtleGlassPill(sleepBadgeTv, context, 12);
        sleepBadgeTv.setVisibility(View.GONE);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbLp.leftMargin = UiHelper.dp(context, 8);
        metaLine.addView(sleepBadgeTv, sbLp);

        titleCol.addView(metaLine);
        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Top-Right Icon Groups (NO bulky text labels)
        LinearLayout topGroup1 = makeFrostedCapsuleGroup(context);
        FrameLayout btnServerTop = makeGroupSlotIcon(context, R.drawable.ic_server);
        btnServerTop.setId(View.generateViewId());
        btnServerTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SERVERS);
            }
        });
        topGroup1.addView(btnServerTop);

        btnDubTop = makeGroupSlotIcon(context, R.drawable.ic_voice);
        btnDubTop.setId(View.generateViewId());
        dubTopIcon = (ImageView) btnDubTop.getChildAt(0);
        btnDubTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isSubAiVoiceEnabled(PlayerScreenController.this.context);
                Prefs.setSubAiVoiceEnabled(PlayerScreenController.this.context, next);
                if (!next) dubbingEngine.stopAndResetQueue();
                refreshQuickBadges();
                toast(next ? "Đã bật Thuyết minh AI" : "Đã tắt Thuyết minh AI", UiHelper.COLOR_GREEN);
                resetHudAutoHide();
            }
        });
        topGroup1.addView(btnDubTop);

        FrameLayout btnSubTop = makeGroupSlotIcon(context, R.drawable.ic_subtitles);
        btnSubTop.setId(View.generateViewId());
        btnSubTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SUBTITLES);
            }
        });
        topGroup1.addView(btnSubTop);
        topBar.addView(topGroup1, rightMarginLp(8));

        LinearLayout topGroup2 = makeFrostedCapsuleGroup(context);
        FrameLayout btnInfoTop = makeGroupSlotIcon(context, R.drawable.ic_info);
        btnInfoTop.setId(View.generateViewId());
        btnInfoTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_INFO);
            }
        });
        topGroup2.addView(btnInfoTop);

        FrameLayout btnReloadTop = makeGroupSlotIcon(context, R.drawable.ic_reload);
        btnReloadTop.setId(View.generateViewId());
        btnReloadTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadCurrentEpisode();
            }
        });
        topGroup2.addView(btnReloadTop);
        topBar.addView(topGroup2, rightMarginLp(8));

        FrameLayout btnSettingsTop = makeHudCircleIcon(context, R.drawable.ic_settings, 36);
        btnSettingsTop.setId(View.generateViewId());
        btnSettingsTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SETTINGS);
            }
        });
        topBar.addView(btnSettingsTop, new LinearLayout.LayoutParams(UiHelper.dp(context, 36), UiHelper.dp(context, 36)));

        hudLayer.addView(topBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // 8B. Top-Center Floating Player Status Toast Pill (matching screenshot "Đặng Việt Anh / Tràn viền màn hình")
        playerToastPill = new LinearLayout(context);
        playerToastPill.setOrientation(LinearLayout.HORIZONTAL);
        playerToastPill.setGravity(Gravity.CENTER_VERTICAL);
        playerToastPill.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 7),
                UiHelper.dp(context, 16), UiHelper.dp(context, 7));
        UiHelper.applyGlassCardStyle(playerToastPill, context, UiHelper.GLASS_RADIUS_DP, UiHelper.COLOR_SURFACE_GLASS, 20);
        playerToastPill.setVisibility(View.GONE);

        FrameLayout ptIconWrap = new FrameLayout(context);
        ptIconWrap.setBackground(UiHelper.createPillDrawable(
                context, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
        playerToastIcon = new ImageView(context);
        playerToastIcon.setImageResource(R.drawable.ic_fullscreen);
        playerToastIcon.setColorFilter(UiHelper.COLOR_CYAN, PorterDuff.Mode.SRC_IN);
        ptIconWrap.addView(playerToastIcon, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 16), UiHelper.dp(context, 16), Gravity.CENTER));
        LinearLayout.LayoutParams ptiLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 28), UiHelper.dp(context, 28));
        ptiLp.rightMargin = UiHelper.dp(context, 10);
        playerToastPill.addView(ptIconWrap, ptiLp);

        LinearLayout ptCol = new LinearLayout(context);
        ptCol.setOrientation(LinearLayout.VERTICAL);
        TextView ptBrand = new TextView(context);
        ptBrand.setText("Đặng Việt Anh");
        ptBrand.setTextColor(Color.WHITE);
        ptBrand.setTextSize(12f);
        ptBrand.setTypeface(null, Typeface.BOLD);
        ptCol.addView(ptBrand);

        playerToastMsgTv = new TextView(context);
        playerToastMsgTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        playerToastMsgTv.setTextSize(11f);
        ptCol.addView(playerToastMsgTv);
        playerToastPill.addView(ptCol);

        FrameLayout.LayoutParams ptRootLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ptRootLp.topMargin = UiHelper.dp(context, 12);
        rootFrame.addView(playerToastPill, ptRootLp);

        // 8C. Right-Edge Vertical Floating 3-Button Dock (Contrast, Auto-Next, Fullscreen Cover)
        LinearLayout rightDock = new LinearLayout(context);
        rightDock.setOrientation(LinearLayout.VERTICAL);
        rightDock.setGravity(Gravity.CENTER_HORIZONTAL);
        rightDock.setPadding(UiHelper.dp(context, 3), UiHelper.dp(context, 6),
                UiHelper.dp(context, 3), UiHelper.dp(context, 6));
        UiHelper.applyGlassCardStyle(rightDock, context, UiHelper.GLASS_RADIUS_DP, UiHelper.COLOR_SURFACE_GLASS, 20);
        rightDock.setClipToOutline(false);
        rightDock.setClipChildren(false);
        rightDock.setClipToPadding(false);

        btnDockContrast = makeGroupSlotIcon(context, R.drawable.ic_contrast);
        btnDockContrast.setId(View.generateViewId());
        dockContrastIcon = (ImageView) btnDockContrast.getChildAt(0);
        btnDockContrast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleContrastQuick();
            }
        });
        rightDock.addView(btnDockContrast);

        btnDockAutoNext = makeGroupSlotIcon(context, R.drawable.ic_skip_next);
        btnDockAutoNext.setId(View.generateViewId());
        dockAutoNextIcon = (ImageView) btnDockAutoNext.getChildAt(0);
        btnDockAutoNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isAutoPlayNext(PlayerScreenController.this.context);
                Prefs.setAutoPlayNext(PlayerScreenController.this.context, next);
                refreshQuickBadges();
                toast(next ? "Tự động chuyển tập: BẬT" : "Tự động chuyển tập: TẮT", UiHelper.COLOR_ACCENT_RED);
                resetHudAutoHide();
            }
        });
        rightDock.addView(btnDockAutoNext);

        btnDockFill = makeGroupSlotIcon(context, R.drawable.ic_fullscreen);
        btnDockFill.setId(View.generateViewId());
        dockFillIcon = (ImageView) btnDockFill.getChildAt(0);
        btnDockFill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isVideoFillCover(PlayerScreenController.this.context);
                Prefs.setVideoFillCover(PlayerScreenController.this.context, next);
                applyResizeSetting();
                toast(next ? "Tràn viền màn hình" : "Tỷ lệ khung hình gốc", UiHelper.COLOR_CYAN);
                resetHudAutoHide();
            }
        });
        rightDock.addView(btnDockFill);

        FrameLayout.LayoutParams rdLp = new FrameLayout.LayoutParams(
                UiHelper.dp(context, 40), FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        rdLp.rightMargin = UiHelper.dp(context, 16);
        hudLayer.addView(rightDock, rdLp);

        // 8D. Center Transport Row (-10s | Frosted Squircle Play/Pause | +10s)
        LinearLayout centerTransport = new LinearLayout(context);
        centerTransport.setOrientation(LinearLayout.HORIZONTAL);
        centerTransport.setGravity(Gravity.CENTER);
        centerTransport.setClipChildren(false);
        centerTransport.setClipToPadding(false);

        FrameLayout btnRew = makeClearTransportIcon(context, R.drawable.ic_rewind_10);
        btnRew.setId(View.generateViewId());
        btnRew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBySec(-10);
            }
        });
        LinearLayout.LayoutParams rwLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 48), UiHelper.dp(context, 48));
        rwLp.rightMargin = UiHelper.dp(context, 36);
        centerTransport.addView(btnRew, rwLp);

        centerPlayWrap = new FrameLayout(context);
        centerPlayWrap.setId(View.generateViewId());
        centerPlayWrap.setBackground(makeSquircleSelectorDrawable(context));
        centerPlayWrap.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), UiHelper.GLASS_RADIUS_DP));
            }
        });
        centerPlayWrap.setClipToOutline(true);
        applyPlayerTvFocusAnimation(centerPlayWrap, context, 1.10f, 16);
        playPauseIcon = new ImageView(context);
        playPauseIcon.setImageResource(R.drawable.ic_pause);
        playPauseIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        centerPlayWrap.addView(playPauseIcon, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 28), UiHelper.dp(context, 28), Gravity.CENTER));
        centerPlaySpinner = new UiHelper.SpinnerView(context);
        centerPlaySpinner.setSpinning(false);
        centerPlaySpinner.setVisibility(View.GONE);
        centerPlayWrap.addView(centerPlaySpinner, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 34), UiHelper.dp(context, 34), Gravity.CENTER));
        centerPlayWrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });
        centerPlayWrap.setTag("play_center");
        centerTransport.addView(centerPlayWrap, new LinearLayout.LayoutParams(UiHelper.dp(context, 62), UiHelper.dp(context, 62)));

        FrameLayout btnFwd = makeClearTransportIcon(context, R.drawable.ic_forward_10);
        btnFwd.setId(View.generateViewId());
        btnFwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBySec(10);
            }
        });
        LinearLayout.LayoutParams fwLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 48), UiHelper.dp(context, 48));
        fwLp.leftMargin = UiHelper.dp(context, 36);
        centerTransport.addView(btnFwd, fwLp);

        hudLayer.addView(centerTransport, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // 8E. Bottom Stack: Combined Time Readout + Thin Borderless Red Timeline + Minimalist Bottom Bar
        LinearLayout bottomHudCol = new LinearLayout(context);
        bottomHudCol.setOrientation(LinearLayout.VERTICAL);
        bottomHudCol.setClipChildren(false);
        bottomHudCol.setClipToPadding(false);
        bottomHudCol.setPadding(UiHelper.dp(context, 20), 0,
                UiHelper.dp(context, 20), UiHelper.dp(context, 16));

        LinearLayout timeNoteRow = new LinearLayout(context);
        timeNoteRow.setOrientation(LinearLayout.HORIZONTAL);
        timeNoteRow.setGravity(Gravity.CENTER_VERTICAL);

        timeCombinedTv = new TextView(context);
        timeCombinedTv.setText("00:00 / 00:00");
        timeCombinedTv.setTextColor(Color.WHITE);
        timeCombinedTv.setTextSize(12f);
        timeCombinedTv.setTypeface(null, Typeface.BOLD);
        timeCombinedTv.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        timeNoteRow.addView(timeCombinedTv);

        seekCueNoteTv = new TextView(context);
        seekCueNoteTv.setTextColor(UiHelper.COLOR_GOLD);
        seekCueNoteTv.setTextSize(11.5f);
        seekCueNoteTv.setTypeface(null, Typeface.BOLD);
        seekCueNoteTv.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        seekCueNoteTv.setVisibility(View.GONE);
        LinearLayout.LayoutParams scnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scnLp.leftMargin = UiHelper.dp(context, 10);
        timeNoteRow.addView(seekCueNoteTv, scnLp);

        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tcLp.bottomMargin = UiHelper.dp(context, 3);
        bottomHudCol.addView(timeNoteRow, tcLp);

        seekBarFocusBox = new FrameLayout(context);
        seekBarFocusBox.setId(View.generateViewId());
        seekBarFocusBox.setFocusable(true);
        seekBarFocusBox.setFocusableInTouchMode(false);
        seekBarFocusBox.setClickable(true);
        seekBarFocusBox.setClipChildren(false);
        seekBarFocusBox.setClipToPadding(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBarFocusBox.setDefaultFocusHighlightEnabled(false);
        }
        seekBarFocusBox.setPadding(UiHelper.dp(context, 4), UiHelper.dp(context, 6),
                UiHelper.dp(context, 4), UiHelper.dp(context, 6));
        android.graphics.drawable.StateListDrawable seekFocusStates = new android.graphics.drawable.StateListDrawable();
        seekFocusStates.addState(new int[]{android.R.attr.state_focused},
                UiHelper.createPillDrawable(context, 10, Color.argb(30, 255, 255, 255), Color.argb(245, 255, 255, 255), 2.2f));
        seekFocusStates.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        seekBarFocusBox.setBackground(seekFocusStates);

        seekTrackContainer = new FrameLayout(context);
        seekTrackContainer.setClipChildren(false);
        seekTrackContainer.setClipToPadding(false);
        View trackBg = new View(context);
        trackBg.setBackground(UiHelper.createGlassRect(context, 2, Color.argb(72, 255, 255, 255), Color.TRANSPARENT, 0));
        seekTrackContainer.addView(trackBg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 3.5f), Gravity.CENTER_VERTICAL));

        seekBufferedFill = new View(context);
        seekBufferedFill.setBackground(UiHelper.createGlassRect(context, 2, Color.argb(120, 255, 255, 255), Color.TRANSPARENT, 0));
        seekTrackContainer.addView(seekBufferedFill, new FrameLayout.LayoutParams(
                0, UiHelper.dp(context, 3.5f), Gravity.CENTER_VERTICAL));

        seekProgressFill = new View(context);
        seekProgressFill.setBackground(UiHelper.createGlassRect(context, 2, UiHelper.COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        seekTrackContainer.addView(seekProgressFill, new FrameLayout.LayoutParams(
                0, UiHelper.dp(context, 3.5f), Gravity.CENTER_VERTICAL));

        seekIntroMarker = new View(context);
        seekIntroMarker.setBackground(UiHelper.createGlassRect(context, 2, Color.WHITE, Color.TRANSPARENT, 0));
        seekIntroMarker.setVisibility(View.GONE);
        seekTrackContainer.addView(seekIntroMarker, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 3), UiHelper.dp(context, 9), Gravity.CENTER_VERTICAL));

        seekCreditsMarker = new View(context);
        seekCreditsMarker.setBackground(UiHelper.createGlassRect(context, 2, UiHelper.COLOR_GOLD, Color.TRANSPARENT, 0));
        seekCreditsMarker.setVisibility(View.GONE);
        seekTrackContainer.addView(seekCreditsMarker, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 3), UiHelper.dp(context, 9), Gravity.CENTER_VERTICAL));

        seekSeamlessMarker = new View(context);
        seekSeamlessMarker.setBackground(UiHelper.createGlassRect(context, 4, UiHelper.COLOR_CYAN, Color.TRANSPARENT, 0));
        seekSeamlessMarker.setVisibility(View.GONE);
        seekTrackContainer.addView(seekSeamlessMarker, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 6), UiHelper.dp(context, 6), Gravity.CENTER_VERTICAL));

        seekThumbDot = new View(context);
        seekThumbDot.setBackground(UiHelper.createGlassRect(context, 6, UiHelper.COLOR_ACCENT_RED, Color.WHITE, 1));
        seekTrackContainer.addView(seekThumbDot, new FrameLayout.LayoutParams(
                UiHelper.dp(context, 12), UiHelper.dp(context, 12), Gravity.CENTER_VERTICAL));

        seekBarFocusBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = hasFocus ? 1.45f : 1.0f;
                seekThumbDot.animate().scaleX(sc).scaleY(sc).setDuration(130).start();
                applyNeutralFocusScale(v, PlayerScreenController.this.context, hasFocus, 1.01f, 4);
            }
        });

        seekBarFocusBox.addView(seekTrackContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiHelper.dp(context, 14)));

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
        LinearLayout.LayoutParams sbWrapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbWrapLp.bottomMargin = UiHelper.dp(context, 8);
        bottomHudCol.addView(seekBarFocusBox, sbWrapLp);

        // Bottom Minimalist Control Row (Left: Avatar + 4 icons; Right: Speed + Tùy chọn khác)
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setClipChildren(false);
        bottomRow.setClipToPadding(false);

        btnMemberPill = new LinearLayout(context);
        btnMemberPill.setId(View.generateViewId());
        btnMemberPill.setOrientation(LinearLayout.HORIZONTAL);
        btnMemberPill.setGravity(Gravity.CENTER_VERTICAL);
        btnMemberPill.setPadding(UiHelper.dp(context, 5), UiHelper.dp(context, 4),
                UiHelper.dp(context, 10), UiHelper.dp(context, 4));
        applyPlayerFocusButton(btnMemberPill, context, 18, false, 1.07f);
        memberAvatarIv = new ImageView(context);
        memberAvatarIv.setImageResource(R.drawable.ic_person);
        LinearLayout.LayoutParams mavLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 26), UiHelper.dp(context, 26));
        mavLp.rightMargin = UiHelper.dp(context, 6);
        btnMemberPill.addView(memberAvatarIv, mavLp);
        TextView chevTv = new TextView(context);
        chevTv.setText("›");
        chevTv.setTextColor(UiHelper.COLOR_TEXT_SECONDARY);
        chevTv.setTextSize(15f);
        chevTv.setTypeface(null, Typeface.BOLD);
        btnMemberPill.addView(chevTv);
        btnMemberPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_MEMBER);
            }
        });
        LinearLayout.LayoutParams mpLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiHelper.dp(context, 36));
        mpLp.rightMargin = UiHelper.dp(context, 8);
        bottomRow.addView(btnMemberPill, mpLp);

        btnWishlistBar = makeHudCircleIcon(context, R.drawable.ic_bookmark_outline, 36);
        btnWishlistBar.setId(View.generateViewId());
        wishlistIconBar = (ImageView) btnWishlistBar.getChildAt(0);
        btnWishlistBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWishlist();
            }
        });
        bottomRow.addView(btnWishlistBar, fixedSquareMarginLp(36, 8));

        btnEpisodesBar = makeHudCircleIcon(context, R.drawable.ic_episodes, 36);
        btnEpisodesBar.setId(View.generateViewId());
        btnEpisodesBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_EPISODES);
            }
        });
        bottomRow.addView(btnEpisodesBar, fixedSquareMarginLp(36, 8));

        btnLikeBar = makeHudCircleIcon(context, R.drawable.ic_thumb_up, 36);
        btnLikeBar.setId(View.generateViewId());
        likeIconBar = (ImageView) btnLikeBar.getChildAt(0);
        btnLikeBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleReaction("like");
            }
        });
        bottomRow.addView(btnLikeBar, fixedSquareMarginLp(36, 8));

        btnDislikeBar = makeHudCircleIcon(context, R.drawable.ic_thumb_down, 36);
        btnDislikeBar.setId(View.generateViewId());
        dislikeIconBar = (ImageView) btnDislikeBar.getChildAt(0);
        btnDislikeBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleReaction("dislike");
            }
        });
        bottomRow.addView(btnDislikeBar, fixedSquareMarginLp(36, 0));

        // Open middle space keeps bottom-center subtitles 100% unobstructed
        bottomRow.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));

        btnSpeedBar = makeIconTextPill(context, R.drawable.ic_play, "1.0x");
        btnSpeedBar.setId(View.generateViewId());
        ((ImageView) btnSpeedBar.getChildAt(0)).getLayoutParams().width = UiHelper.dp(context, 12);
        ((ImageView) btnSpeedBar.getChildAt(0)).getLayoutParams().height = UiHelper.dp(context, 12);
        speedTextBar = (TextView) btnSpeedBar.getChildAt(1);
        speedTextBar.setTextSize(12.5f);
        btnSpeedBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSpeed();
            }
        });
        bottomRow.addView(btnSpeedBar, rightMarginLp(8));

        TextView btnMoreBar = makeTextPill(context, "Tùy chọn khác  ⌄", false);
        btnMoreBar.setId(View.generateViewId());
        btnMoreBar.setTextSize(12.5f);
        btnMoreBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSubModal(MODAL_SETTINGS);
            }
        });
        bottomRow.addView(btnMoreBar);

        bottomHudCol.addView(bottomRow);
        hudLayer.addView(bottomHudCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // Deterministic 5-Zone D-Pad Focus Matrix across Player HUD (zero dead ends)
        btnBackHud.setNextFocusLeftId(btnBackHud.getId());
        btnBackHud.setNextFocusRightId(btnServerTop.getId());
        btnServerTop.setNextFocusLeftId(btnBackHud.getId());
        btnServerTop.setNextFocusRightId(btnDubTop.getId());
        btnDubTop.setNextFocusLeftId(btnServerTop.getId());
        btnDubTop.setNextFocusRightId(btnSubTop.getId());
        btnSubTop.setNextFocusLeftId(btnDubTop.getId());
        btnSubTop.setNextFocusRightId(btnInfoTop.getId());
        btnInfoTop.setNextFocusLeftId(btnSubTop.getId());
        btnInfoTop.setNextFocusRightId(btnReloadTop.getId());
        btnReloadTop.setNextFocusLeftId(btnInfoTop.getId());
        btnReloadTop.setNextFocusRightId(btnSettingsTop.getId());
        btnSettingsTop.setNextFocusLeftId(btnReloadTop.getId());
        btnSettingsTop.setNextFocusRightId(btnSettingsTop.getId());

        btnBackHud.setNextFocusDownId(btnRew.getId());
        btnServerTop.setNextFocusDownId(centerPlayWrap.getId());
        btnDubTop.setNextFocusDownId(centerPlayWrap.getId());
        btnSubTop.setNextFocusDownId(btnFwd.getId());
        btnInfoTop.setNextFocusDownId(btnFwd.getId());
        btnReloadTop.setNextFocusDownId(btnDockContrast.getId());
        btnSettingsTop.setNextFocusDownId(btnDockContrast.getId());

        btnRew.setNextFocusUpId(btnBackHud.getId());
        btnRew.setNextFocusLeftId(btnRew.getId());
        btnRew.setNextFocusRightId(centerPlayWrap.getId());
        btnRew.setNextFocusDownId(seekBarFocusBox.getId());

        centerPlayWrap.setNextFocusUpId(btnDubTop.getId());
        centerPlayWrap.setNextFocusLeftId(btnRew.getId());
        centerPlayWrap.setNextFocusRightId(btnFwd.getId());
        centerPlayWrap.setNextFocusDownId(seekBarFocusBox.getId());

        btnFwd.setNextFocusUpId(btnSubTop.getId());
        btnFwd.setNextFocusLeftId(centerPlayWrap.getId());
        btnFwd.setNextFocusRightId(btnDockContrast.getId());
        btnFwd.setNextFocusDownId(seekBarFocusBox.getId());

        btnDockContrast.setNextFocusUpId(btnSettingsTop.getId());
        btnDockContrast.setNextFocusDownId(btnDockAutoNext.getId());
        btnDockContrast.setNextFocusLeftId(btnFwd.getId());
        btnDockContrast.setNextFocusRightId(btnDockContrast.getId());

        btnDockAutoNext.setNextFocusUpId(btnDockContrast.getId());
        btnDockAutoNext.setNextFocusDownId(btnDockFill.getId());
        btnDockAutoNext.setNextFocusLeftId(btnFwd.getId());
        btnDockAutoNext.setNextFocusRightId(btnDockAutoNext.getId());

        btnDockFill.setNextFocusUpId(btnDockAutoNext.getId());
        btnDockFill.setNextFocusDownId(seekBarFocusBox.getId());
        btnDockFill.setNextFocusLeftId(btnFwd.getId());
        btnDockFill.setNextFocusRightId(btnDockFill.getId());

        seekBarFocusBox.setNextFocusUpId(centerPlayWrap.getId());
        seekBarFocusBox.setNextFocusDownId(btnMemberPill.getId());

        btnMemberPill.setNextFocusUpId(seekBarFocusBox.getId());
        btnMemberPill.setNextFocusLeftId(btnMemberPill.getId());
        btnMemberPill.setNextFocusRightId(btnWishlistBar.getId());
        btnWishlistBar.setNextFocusUpId(seekBarFocusBox.getId());
        btnWishlistBar.setNextFocusLeftId(btnMemberPill.getId());
        btnWishlistBar.setNextFocusRightId(btnEpisodesBar.getId());
        btnEpisodesBar.setNextFocusUpId(seekBarFocusBox.getId());
        btnEpisodesBar.setNextFocusLeftId(btnWishlistBar.getId());
        btnEpisodesBar.setNextFocusRightId(btnLikeBar.getId());
        btnLikeBar.setNextFocusUpId(seekBarFocusBox.getId());
        btnLikeBar.setNextFocusLeftId(btnEpisodesBar.getId());
        btnLikeBar.setNextFocusRightId(btnDislikeBar.getId());
        btnDislikeBar.setNextFocusUpId(seekBarFocusBox.getId());
        btnDislikeBar.setNextFocusLeftId(btnLikeBar.getId());
        btnDislikeBar.setNextFocusRightId(btnSpeedBar.getId());
        btnSpeedBar.setNextFocusUpId(seekBarFocusBox.getId());
        btnSpeedBar.setNextFocusLeftId(btnDislikeBar.getId());
        btnSpeedBar.setNextFocusRightId(btnMoreBar.getId());
        btnMoreBar.setNextFocusUpId(seekBarFocusBox.getId());
        btnMoreBar.setNextFocusLeftId(btnSpeedBar.getId());
        btnMoreBar.setNextFocusRightId(btnMoreBar.getId());

        // 9. Sub-Modal Host (Sleek Frosted Glass, dynamic centered / right-drawer for live preview)
        subModalHost = new FrameLayout(context);
        subModalHost.setBackgroundColor(Color.argb(180, 9, 11, 18));
        subModalHost.setVisibility(View.GONE);
        subModalHost.setClickable(true);
        UiHelper.makeNonFocusable(subModalHost);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        this.screenW = dm.widthPixels > 0 ? dm.widthPixels : UiHelper.dp(context, 960);
        this.screenH = dm.heightPixels > 0 ? dm.heightPixels : UiHelper.dp(context, 540);
        int modalW = Math.min(UiHelper.dp(context, 700), Math.round(screenW * 0.84f));
        int modalMaxH = Math.round(screenH * 0.84f);

        subModalCard = new LinearLayout(context);
        subModalCard.setOrientation(LinearLayout.VERTICAL);
        subModalCard.setClipChildren(false);
        subModalCard.setClipToPadding(false);
        subModalCard.setPadding(UiHelper.dp(context, 22), UiHelper.dp(context, 16),
                UiHelper.dp(context, 22), UiHelper.dp(context, 16));
        UiHelper.applyGlassCardStyle(subModalCard, context, 24, UiHelper.COLOR_SURFACE_GLASS, 18);

        LinearLayout smHeader = new LinearLayout(context);
        smHeader.setOrientation(LinearLayout.HORIZONTAL);
        smHeader.setGravity(Gravity.CENTER_VERTICAL);
        smHeader.setClipChildren(false);
        smHeader.setClipToPadding(false);
        LinearLayout.LayoutParams smhLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        smhLp.bottomMargin = UiHelper.dp(context, 10);

        subModalTitleTv = new TextView(context);
        subModalTitleTv.setTextColor(Color.WHITE);
        subModalTitleTv.setTextSize(17.5f);
        subModalTitleTv.setTypeface(null, Typeface.BOLD);
        smHeader.addView(subModalTitleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        subModalCloseBtn = makeTextPill(context, "Đóng", false);
        subModalCloseBtn.setId(View.generateViewId());
        subModalCloseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSubModal();
            }
        });
        smHeader.addView(subModalCloseBtn);
        subModalCard.addView(smHeader, smhLp);

        subModalScroll = new ScrollView(context);
        UiHelper.makeNonFocusable(subModalScroll);
        subModalScroll.setVerticalScrollBarEnabled(false);
        subModalScroll.setClipChildren(false);
        subModalScroll.setClipToPadding(false);
        subModalScroll.setPadding(UiHelper.dp(context, 4), UiHelper.dp(context, 4),
                UiHelper.dp(context, 4), UiHelper.dp(context, 28));
        subModalBody = new LinearLayout(context);
        subModalBody.setOrientation(LinearLayout.VERTICAL);
        subModalBody.setClipChildren(false);
        subModalBody.setClipToPadding(false);
        subModalScroll.addView(subModalBody, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        subModalCard.addView(subModalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, modalMaxH - UiHelper.dp(context, 84)));

        FrameLayout.LayoutParams smcLp = new FrameLayout.LayoutParams(
                modalW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        subModalHost.addView(subModalCard, smcLp);
        rootFrame.addView(subModalHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        syncPlayerFocusLayers();
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
        this.hudVisible = false;
        subModalHost.animate().cancel();
        subModalHost.setVisibility(View.GONE);
        rootFrame.setVisibility(View.VISIBLE);
        UiHelper.setFocusBlocked(rootFrame, false);
        syncPlayerFocusLayers();
        applyContrastSetting();
        if (listener != null) listener.onAudioFocusRequested(true);
        startEpisodeSession(request, false);
    }

    public void close() {
        if (!showing) return;
        persistProgressNow();
        restoreWindowBrightness();
        showing = false;
        activeModal = MODAL_NONE;
        updateBufferingIndicator(false);
        checkFilePolling = false;
        nextCountdownRunning = false;
        serverOrchestrator.cancelPendingTasks();
        handler.removeCallbacksAndMessages(null);
        dubbingEngine.release();
        nativePlayer.stopMedia();
        hudLayer.animate().cancel();
        subModalHost.animate().cancel();
        subModalHost.setVisibility(View.GONE);
        rootFrame.setVisibility(View.GONE);
        syncPlayerFocusLayers();
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
        serverOrchestrator.shutdown();
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
            if (isDpadNavOrSelectKey(keyCode) && !hasValidModalFocus()) {
                focusCurrentSubModalDefault();
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
            if ((nextEpCard.getVisibility() == View.VISIBLE && nextEpCard.hasFocus())
                    || (skipIntroBtn.getVisibility() == View.VISIBLE && skipIntroBtn.hasFocus()
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER))
                    || (resumeRecoveryBtn.getVisibility() == View.VISIBLE && resumeRecoveryBtn.hasFocus()
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER))) {
                return false;
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
        if (isDpadNavOrSelectKey(keyCode) && !hasValidHudFocus()) {
            focusCenterPlayButton();
            return true;
        }
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
        this.safeResumeChecked = false;
        this.episodeEndedNotified = false;
        this.savedResumeGuardSec = req.initialResumeSec > 10 ? req.initialResumeSec : 0.0;
        this.pendingInitialSeekActive = this.savedResumeGuardSec > 10.0;
        this.pendingInitialSeekTargetSec = this.savedResumeGuardSec;
        this.resumePositionVerified = this.savedResumeGuardSec <= 10.0;
        this.resumeRecoveryToastShown = false;
        this.preloadAttemptedThisEp = false;
        this.preloadedNextUrl = "";
        this.nextCountdownDismissed = false;
        this.nextCountdownRunning = false;
        this.nextEpisodeTarget = null;
        this.checkFilePolling = false;
        serverOrchestrator.cancelPendingTasks();
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
        resumeRecoveryBtn.setVisibility(View.GONE);
        nextEpCard.setVisibility(View.GONE);
        syncPlayerFocusLayers();
        updateEpisodesButtonVisibility();
        updateBufferingIndicator(true);
        applySubtitleBgSetting();
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
                updateBufferingIndicator(false);
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
        updateEpisodesButtonVisibility();

        double bestSaved = Math.max(savedResumeGuardSec, data.resumeSeconds);
        if (bestSaved > 10.0) {
            savedResumeGuardSec = bestSaved;
            resumePositionVerified = false;
            pendingInitialSeekTargetSec = bestSaved;
            pendingInitialSeekActive = true;
        }

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
            String initialSrvId = resolveServerIdForUrl(data.primaryUrl, "");
            startMediaStream(data.primaryUrl, data.streamType, initialSrvId);
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
                dubbingEngine.onSubtitlesLoaded(vnCues, lastPosSec);
            }
        });

        // Probe pings, GGD, video quality, and check_file
        final int seq = episodeSessionSeq;
        serverOrchestrator.measurePings(new ServerOrchestrator.Callback<ServerOrchestrator.PingSnapshot>() {
            @Override
            public void onResult(ServerOrchestrator.PingSnapshot snap) {
                if (!showing || seq != episodeSessionSeq) return;
                List<ServerOrchestrator.ServerState> updated = serverOrchestrator.applyPings(serverStates, snap);
                serverStates.clear();
                serverStates.addAll(updated);
                if (activeModal == MODAL_SERVERS) refreshServersModalPreservingFocus();
            }
        });

        if (!data.serverFileKey.isEmpty()) {
            serverOrchestrator.checkGgdriver(data.serverFileKey, data.serverMovieTitle, new ServerOrchestrator.Callback<ServerOrchestrator.GgdFlags>() {
                @Override
                public void onResult(ServerOrchestrator.GgdFlags flags) {
                    if (!showing || seq != episodeSessionSeq) return;
                    ggdFlags = flags;
                    List<ServerOrchestrator.ServerState> updated = serverOrchestrator.applyGgdriver(
                            serverStates, data.serverFileKey, data, flags);
                    serverStates.clear();
                    serverStates.addAll(updated);
                    if (activeStreamUrl.isEmpty()) {
                        tryStartBestPlayableServer(false);
                    }
                    if (activeModal == MODAL_SERVERS) refreshServersModalPreservingFocus();
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

    private String resolveServerIdForUrl(String url, String preferredId) {
        if (preferredId != null && !preferredId.trim().isEmpty()) {
            return preferredId.trim();
        }
        String cleanUrl = url != null ? url.trim() : "";
        if (cleanUrl.isEmpty()) return "";
        for (ServerOrchestrator.ServerState s : serverStates) {
            if (s.streamUrl != null && !s.streamUrl.isEmpty() && cleanUrl.equals(s.streamUrl.trim())) {
                return s.id;
            }
        }
        String lower = cleanUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("dangvietanh.io.vn/?code=")) return "40";
        if (lower.contains("handle.dangvietanh.com")) return "36";
        if (lower.contains("dangvietanh.io.vn")) return "11";
        if (lower.contains("workers.dev")) return "3";
        return "fallback";
    }

    private void tryStartBestPlayableServer(boolean preserveCurrentPos) {
        ServerOrchestrator.ServerState best = serverOrchestrator.selectOptimalServer(serverStates, lastPosSec);
        if (best != null && !best.streamUrl.isEmpty()) {
            switchToServer(best, preserveCurrentPos);
        } else if (resolvedData != null && !resolvedData.primaryUrl.isEmpty() && activeStreamUrl.isEmpty()) {
            String fallbackId = resolveServerIdForUrl(resolvedData.primaryUrl, "");
            startMediaStream(resolvedData.primaryUrl, resolvedData.streamType, fallbackId);
        }
    }

    private void switchToServer(ServerOrchestrator.ServerState server, boolean preservePos) {
        if (server == null || server.streamUrl.isEmpty()) return;
        this.activeServerId = resolveServerIdForUrl(server.streamUrl, server.id);
        if (preservePos && lastPosSec > 2.0) {
            initialResumeApplied = true;
            startMediaStreamAt(server.streamUrl, server.format, this.activeServerId, lastPosSec);
        } else {
            startMediaStream(server.streamUrl, server.format, this.activeServerId);
        }
        toast("Đang phát qua " + server.label, UiHelper.COLOR_CYAN);
        if (activeModal == MODAL_SERVERS) refreshServersModalPreservingFocus();
    }

    private void startMediaStream(String url, String type, String serverId) {
        double resume = 0.0;
        if (!initialResumeApplied) {
            initialResumeApplied = true;
            if (savedResumeGuardSec > 5.0) {
                resume = savedResumeGuardSec;
            } else if (launchReq != null && launchReq.initialResumeSec > 5) {
                resume = launchReq.initialResumeSec;
            } else if (resolvedData != null && resolvedData.resumeSeconds > 5) {
                resume = resolvedData.resumeSeconds;
            }
            if (resume > 10.0) {
                savedResumeGuardSec = Math.max(savedResumeGuardSec, resume);
                resumePositionVerified = false;
                pendingInitialSeekTargetSec = savedResumeGuardSec;
                pendingInitialSeekActive = true;
            }
            // Match iOS PlayerModal: auto-skip intro cleanly on fresh start
            if (resume <= 2.0 && Prefs.isAutoSkipIntro(context)
                    && resolvedData != null && resolvedData.skipIntroSec > 5) {
                resume = resolvedData.skipIntroSec;
                autoSkippedIntroThisEp = true;
                toast("Đã tự động bỏ qua phần giới thiệu", UiHelper.COLOR_CYAN);
            }
        }
        startMediaStreamAt(url, type, serverId, resume);
    }

    private void startMediaStreamAt(String url, String type, String serverId, double startPosSec) {
        this.activeStreamUrl = url != null ? url : "";
        this.activeServerId = resolveServerIdForUrl(this.activeStreamUrl, serverId);
        applyResizeSetting();
        float rate = Prefs.getPlaybackRate(context);
        nativePlayer.loadMedia(this.activeStreamUrl, type, startPosSec, false, false, rate);
        updateHudMetaText();
    }

    private ServerOrchestrator.ServerState findActiveServerState() {
        if (activeServerId == null || activeServerId.isEmpty()) return null;
        for (ServerOrchestrator.ServerState s : serverStates) {
            if (activeServerId.equals(s.id)) return s;
        }
        return null;
    }

    private void handlePlaybackState(boolean isPlaying, boolean isBuffering, boolean isEnded, boolean isReady) {
        this.isPlayingNow = isPlaying;
        playPauseIcon.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        if (isBuffering) {
            updateBufferingIndicator(true);
        } else if (isReady || isPlaying || isEnded) {
            updateBufferingIndicator(false);
            fadeTransitionVeilOut();
            double d = nativePlayer.getDurationSec();
            if (d > 0) {
                lastDurSec = d;
                updateHudMetaText();
                // Match iOS computeSafeResumeSec + apply pendingInitialSeek once player is truly READY with valid duration
                if (isReady && !safeResumeChecked && d > 20.0) {
                    safeResumeChecked = true;
                    double targetResume = pendingInitialSeekActive ? pendingInitialSeekTargetSec : nativePlayer.getCurrentPositionSec();
                    if (d > 60.0 && targetResume > 15.0) {
                        int endCredits = resolvedData != null ? resolvedData.endCreditsSec : 0;
                        double triggerSec = (endCredits > 15 && d > endCredits) ? (d - endCredits) : (d - 15.0);
                        if (targetResume >= Math.max(10.0, triggerSec - 15.0)) {
                            double rewoundSec = Math.max(0.0, triggerSec - 120.0);
                            if (rewoundSec < targetResume - 10.0) {
                                targetResume = rewoundSec;
                                savedResumeGuardSec = rewoundSec;
                                pendingInitialSeekTargetSec = rewoundSec;
                                toast("Đã tự động lùi lại 2 phút trước đoạn chuyển tập", UiHelper.COLOR_CYAN);
                            }
                        }
                    }
                    if (pendingInitialSeekActive && pendingInitialSeekTargetSec > 5.0) {
                        double curPos = nativePlayer.getCurrentPositionSec();
                        double clampedTarget = Math.min(Math.max(0.0, d - 5.0), pendingInitialSeekTargetSec);
                        pendingInitialSeekActive = false;
                        if (curPos + 5.0 < clampedTarget) {
                            nativePlayer.seekTo(clampedTarget);
                        }
                    }
                }
            }
        }

        if (!isPlaying) {
            dubbingEngine.pauseNow();
        }

        if (isEnded) {
            // Match iOS handleActiveLayerPlayToEnd: never treat end of a 10p/30p preview clip as end of episode
            ServerOrchestrator.ServerState curSrv = findActiveServerState();
            if (curSrv != null && curSrv.previewTag != null && !curSrv.previewTag.isEmpty()) {
                ServerOrchestrator.ServerState alt = serverOrchestrator.selectFailoverServer(
                        serverStates, activeServerId, lastPosSec);
                if (alt != null && !alt.streamUrl.equals(activeStreamUrl)) {
                    toast("Đoạn xem trước vừa kết thúc, đang chuyển sang " + alt.label + "...", UiHelper.COLOR_CYAN);
                    switchToServer(alt, true);
                }
                return;
            }
            if (nextEpisodeTarget != null && Prefs.isAutoPlayNext(context)) {
                cancelNextCountdown(false);
                playNextEpisode(true);
            } else if (!episodeEndedNotified) {
                episodeEndedNotified = true;
                cancelNextCountdown(false);
                setHudVisible(true);
                focusCenterPlayButton();
                toast(nextEpisodeTarget == null
                        ? "Đã phát hết tập cuối của bộ phim"
                        : "Đã phát hết tập phim", UiHelper.COLOR_GOLD);
            }
        }
        resetHudAutoHide();
    }

    private void handleProgress(double posSec, double durSec, int bufferedPct) {
        this.lastPosSec = posSec;
        this.lastDurSec = durSec;
        this.lastBufferedPct = bufferedPct;

        timeCombinedTv.setText(formatTime(posSec) + " / " + (durSec > 0 ? formatTime(durSec) : "--:--"));
        updateSeekBarVisual();

        subtitleOverlay.renderAt(posSec);
        SubtitleEngine.Cue activeCue = subtitleOverlay.getActiveMainVnCue(posSec);
        int activeIdx = subtitleOverlay.getActiveMainVnIndex(posSec);
        dubbingEngine.updateTick(posSec, isPlayingNow, false, vnCues, activeCue, activeIdx);

        // Check preview duration limit -> auto failover to full Tier 2 server
        if (!activeServerId.isEmpty() && posSec > 60.0) {
            for (ServerOrchestrator.ServerState s : serverStates) {
                if (s.id.equals(activeServerId) && ServerOrchestrator.isPreviewLimitReached(s, posSec)) {
                    ServerOrchestrator.ServerState alt = serverOrchestrator.selectFailoverServer(
                            serverStates, activeServerId, posSec);
                    if (alt != null && !alt.streamUrl.equals(activeStreamUrl)) {
                        toast("Đang chuyển sang " + alt.label + " để xem tiếp đầy đủ...", UiHelper.COLOR_CYAN);
                        switchToServer(alt, true);
                    }
                    break;
                }
            }
        }

        // Saved Resume Position Guard & Quick Recovery Pill
        if (savedResumeGuardSec > 10.0 && !resumePositionVerified) {
            if (posSec >= (savedResumeGuardSec - 15.0)) {
                resumePositionVerified = true;
                boolean hadFocus = resumeRecoveryBtn.hasFocus();
                resumeRecoveryBtn.setVisibility(View.GONE);
                syncPlayerFocusLayers();
                if (hadFocus && hudVisible && activeModal == MODAL_NONE) {
                    focusCenterPlayButton();
                }
            } else if (durSec > 20.0 && posSec >= 2.0 && (posSec + 15.0) < savedResumeGuardSec) {
                boolean wasHidden = resumeRecoveryBtn.getVisibility() != View.VISIBLE;
                resumeRecoveryBtn.setText("↺ Tua tới vị trí cũ (" + formatTime(savedResumeGuardSec) + ")");
                resumeRecoveryBtn.setVisibility(View.VISIBLE);
                syncPlayerFocusLayers();
                if (wasHidden) {
                    if (!resumeRecoveryToastShown) {
                        resumeRecoveryToastShown = true;
                        toast("Đang ở " + formatTime(posSec) + " • Nhấn để tua tới vị trí cũ (" + formatTime(savedResumeGuardSec) + ")", UiHelper.COLOR_GOLD);
                    }
                    if (!hudVisible && activeModal == MODAL_NONE
                            && nextEpCard.getVisibility() != View.VISIBLE
                            && skipIntroBtn.getVisibility() != View.VISIBLE) {
                        UiHelper.requestTvFocus(resumeRecoveryBtn);
                    }
                }
            }
        } else if (resumeRecoveryBtn.getVisibility() == View.VISIBLE) {
            boolean hadFocus = resumeRecoveryBtn.hasFocus();
            resumeRecoveryBtn.setVisibility(View.GONE);
            syncPlayerFocusLayers();
            if (hadFocus && hudVisible && activeModal == MODAL_NONE) {
                focusCenterPlayButton();
            }
        }

        // Save watch progress every 10s
        long nowElapsed = android.os.SystemClock.elapsedRealtime();
        if (isPlayingNow && (nowElapsed - lastProgressSaveElapsedMs) >= PROGRESS_SAVE_INTERVAL_MS) {
            lastProgressSaveElapsedMs = nowElapsed;
            persistProgressNow();
        }

        // Skip Intro check (100% synced with iOS PlayerModal: skipIntroSec > 5 && pos >= 1 && pos < skipIntroSec - 2)
        if (resolvedData != null && resolvedData.skipIntroSec > 5 && durSec > resolvedData.skipIntroSec) {
            if (posSec >= 1.0 && posSec < (resolvedData.skipIntroSec - 2.0)) {
                if (Prefs.isAutoSkipIntro(context) && !autoSkippedIntroThisEp) {
                    autoSkippedIntroThisEp = true;
                    boolean hadFocus = skipIntroBtn.hasFocus();
                    skipIntroBtn.setVisibility(View.GONE);
                    syncPlayerFocusLayers();
                    if (hadFocus && hudVisible && activeModal == MODAL_NONE) {
                        focusCenterPlayButton();
                    }
                    nativePlayer.seekTo(resolvedData.skipIntroSec);
                    toast("Đã tự động bỏ qua phần giới thiệu", UiHelper.COLOR_CYAN);
                } else if (!Prefs.isAutoSkipIntro(context)) {
                    boolean wasHidden = skipIntroBtn.getVisibility() != View.VISIBLE;
                    skipIntroBtn.setVisibility(View.VISIBLE);
                    syncPlayerFocusLayers();
                    if (wasHidden && !hudVisible && activeModal == MODAL_NONE && nextEpCard.getVisibility() != View.VISIBLE) {
                        UiHelper.requestTvFocus(skipIntroBtn);
                    }
                }
            } else if (skipIntroBtn.getVisibility() == View.VISIBLE) {
                boolean hadFocus = skipIntroBtn.hasFocus();
                skipIntroBtn.setVisibility(View.GONE);
                syncPlayerFocusLayers();
                if (hadFocus && hudVisible && activeModal == MODAL_NONE) {
                    focusCenterPlayButton();
                }
            }
        } else if (skipIntroBtn.getVisibility() == View.VISIBLE) {
            boolean hadFocus = skipIntroBtn.hasFocus();
            skipIntroBtn.setVisibility(View.GONE);
            syncPlayerFocusLayers();
            if (hadFocus && hudVisible && activeModal == MODAL_NONE) {
                focusCenterPlayButton();
            }
        }

        // Seamless Preload + Outro Auto-Next Countdown (100% synced with iOS PlayerModal)
        // Note: endCreditsSec from API is duration FROM THE END of the video (trigger at durSec - endCreditsSec)!
        ServerOrchestrator.ServerState curSrv = findActiveServerState();
        boolean isPreviewStream = curSrv != null && curSrv.previewTag != null && !curSrv.previewTag.isEmpty();
        if (durSec > 30.0 && nextEpisodeTarget != null && !isPreviewStream) {
            int endCredits = resolvedData != null ? resolvedData.endCreditsSec : 0;

            // Preload next episode at T - endCredits - 120s (same as iOS)
            double preloadTriggerSec = Math.max(5.0, durSec - endCredits - 120.0);
            if (Prefs.isSeamlessPreload(context) && !preloadAttemptedThisEp && durSec > 60.0 && posSec >= preloadTriggerSec) {
                preloadAttemptedThisEp = true;
                resolveAndPreloadNextEpisode();
            }

            // Outro trigger timestamp: durSec - endCredits (if endCredits > 15s and valid), else near real end
            boolean hasValidOutro = endCredits > 15 && durSec > (endCredits + 30.0);
            double fallbackWindow = Math.max(8.0, Prefs.getNextEpisodeDelaySec(context) + 2.0);
            double outroTriggerSec = hasValidOutro ? (durSec - endCredits) : (durSec - fallbackWindow);

            // If user rewound out of outro zone, cancel countdown & allow future trigger
            if (nextCountdownRunning && posSec < (outroTriggerSec - 5.0)) {
                cancelNextCountdown(false);
            }
            if (nextCountdownDismissed && posSec < (outroTriggerSec - 15.0)) {
                nextCountdownDismissed = false;
            }

            if (Prefs.isAutoPlayNext(context)
                    && !nextCountdownDismissed
                    && !nextCountdownRunning
                    && posSec >= outroTriggerSec
                    && posSec < (durSec - 2.0)) {
                int delaySec = Prefs.getNextEpisodeDelaySec(context);
                if (delaySec <= 0) {
                    playNextEpisode(true);
                } else {
                    startNextCountdown();
                }
            }
        } else if (nextCountdownRunning) {
            cancelNextCountdown(false);
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
        ServerOrchestrator.ServerState alt = serverOrchestrator.selectFailoverServer(serverStates, activeServerId, lastPosSec);
        if (alt != null && !alt.streamUrl.equals(activeStreamUrl)) {
            toast("Đang tự chuyển sang " + alt.label + "...", UiHelper.COLOR_GOLD);
            switchToServer(alt, true);
        } else {
            updateBufferingIndicator(false);
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
        nextCountdownRemaining = Math.max(1, Prefs.getNextEpisodeDelaySec(context));
        nextEpTitleTv.setText(nextEpisodeTarget.episodesName.isEmpty() ? "Tập tiếp theo" : nextEpisodeTarget.episodesName);
        nextEpCard.setVisibility(View.VISIBLE);
        syncPlayerFocusLayers();
        if (activeModal == MODAL_NONE && btnNextPlayNow != null) {
            UiHelper.requestTvFocus(btnNextPlayNow);
        }
        handler.removeCallbacks(nextCountdownRunnable);
        handler.post(nextCountdownRunnable);
    }

    private void cancelNextCountdown(boolean dismissedByUser) {
        nextCountdownRunning = false;
        if (dismissedByUser) nextCountdownDismissed = true;
        handler.removeCallbacks(nextCountdownRunnable);
        boolean hadFocus = nextEpCard.hasFocus();
        nextEpCard.setVisibility(View.GONE);
        syncPlayerFocusLayers();
        if (hadFocus && hudVisible && activeModal == MODAL_NONE) {
            focusCenterPlayButton();
        }
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
        // Protect saved watch progress: NEVER overwrite server progress if player hasn't reached saved position yet
        if (!resumePositionVerified && savedResumeGuardSec > 10.0 && (lastPosSec + 15.0) < savedResumeGuardSec) {
            return;
        }
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

    private boolean isSeriesMovie() {
        if (resolvedData != null) {
            if (resolvedData.movie != null && resolvedData.movie.isTvSeries == 1) return true;
            if (resolvedData.episodes.size() > 1 || resolvedData.seasons.size() > 1) return true;
            if (resolvedData.movie != null && resolvedData.movie.isTvSeries == 0 && resolvedData.episodes.size() <= 1) {
                return false;
            }
        }
        if (launchReq != null) {
            return launchReq.episodes.size() > 1 || launchReq.seasons.size() > 1;
        }
        return false;
    }

    private void updateEpisodesButtonVisibility() {
        boolean showEpisodes = isSeriesMovie();
        btnEpisodesBar.setVisibility(showEpisodes ? View.VISIBLE : View.GONE);
        if (showEpisodes) {
            btnWishlistBar.setNextFocusRightId(btnEpisodesBar.getId());
            btnEpisodesBar.setNextFocusLeftId(btnWishlistBar.getId());
            btnEpisodesBar.setNextFocusRightId(btnLikeBar.getId());
            btnLikeBar.setNextFocusLeftId(btnEpisodesBar.getId());
        } else {
            btnWishlistBar.setNextFocusRightId(btnLikeBar.getId());
            btnLikeBar.setNextFocusLeftId(btnWishlistBar.getId());
        }
    }

    // ---- HUD Helpers ----

    private void setHudVisible(boolean visible) {
        boolean wasVisible = this.hudVisible;
        this.hudVisible = visible;
        handler.removeCallbacks(hudAutoHideRunnable);
        hudLayer.animate().cancel();
        if (visible) {
            hudLayer.setVisibility(View.VISIBLE);
            syncPlayerFocusLayers();
            hudLayer.animate().alpha(1f).setDuration(180).setListener(null).start();
            if (activeModal == MODAL_NONE && (!wasVisible || !hasValidHudFocus())) {
                focusCenterPlayButton();
            }
            resetHudAutoHide();
        } else {
            syncPlayerFocusLayers();
            hudLayer.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!hudVisible) {
                        hudLayer.setVisibility(View.GONE);
                        syncPlayerFocusLayers();
                    }
                }
            }).start();
        }
        syncBufferingIndicatorVisibility();
    }

    private void syncPlayerFocusLayers() {
        boolean active = showing && rootFrame.getVisibility() == View.VISIBLE;
        boolean modalOpen = active && activeModal != MODAL_NONE && subModalHost.getVisibility() == View.VISIBLE;
        UiHelper.setFocusBlocked(hudLayer, !active || modalOpen || !hudVisible);
        UiHelper.setFocusBlocked(nextEpCard, !active || modalOpen || nextEpCard.getVisibility() != View.VISIBLE);
        skipIntroBtn.setFocusable(active && !modalOpen && skipIntroBtn.getVisibility() == View.VISIBLE);
        resumeRecoveryBtn.setFocusable(active && !modalOpen && resumeRecoveryBtn.getVisibility() == View.VISIBLE);
        UiHelper.setFocusBlocked(subModalHost, !active || !modalOpen);
    }

    private boolean hasValidHudFocus() {
        View f = rootFrame.findFocus();
        if (f == null || f == rootFrame || f == subModalHost || f == subModalScroll) return false;
        if (!f.isAttachedToWindow() || !f.isShown() || !f.isFocusable()) return false;
        return UiHelper.isDescendantOf(f, hudLayer)
                || f == skipIntroBtn
                || f == resumeRecoveryBtn
                || UiHelper.isDescendantOf(f, nextEpCard);
    }

    private boolean hasValidModalFocus() {
        return UiHelper.isValidInteractiveFocus(subModalHost.findFocus(), subModalHost);
    }

    public boolean ensureValidFocus() {
        if (!showing) return false;
        if (activeModal != MODAL_NONE) {
            if (!hasValidModalFocus()) {
                focusCurrentSubModalDefault();
            }
            return true;
        }
        if (hudVisible) {
            if (!hasValidHudFocus()) {
                focusCenterPlayButton();
            }
            return true;
        }
        return false;
    }

    private static boolean isDpadNavOrSelectKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void updateBufferingIndicator(boolean loading) {
        this.isLoadingOrBuffering = loading;
        syncBufferingIndicatorVisibility();
    }

    private void syncBufferingIndicatorVisibility() {
        boolean loading = isLoadingOrBuffering && showing;
        playPauseIcon.setVisibility(loading ? View.GONE : View.VISIBLE);
        boolean showInHud = loading && hudVisible && activeModal == MODAL_NONE;
        centerPlaySpinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        centerPlaySpinner.setSpinning(showInHud);

        boolean showDetached = loading && !hudVisible && activeModal == MODAL_NONE;
        detachedBufferingHost.setVisibility(showDetached ? View.VISIBLE : View.GONE);
        detachedBufferingSpinner.setSpinning(showDetached);
    }

    private void applySubtitleBgSetting() {
        if (subtitleOverlay.getChildCount() <= 1) return;
        View bottomStack = subtitleOverlay.getChildAt(1);
        if (bottomStack == null) return;
        String style = Prefs.getSubBgStyle(context);
        if ("semi".equals(style)) {
            bottomStack.setBackground(UiHelper.createPillDrawable(
                    context, 12, Color.argb(120, 0, 0, 0), Color.TRANSPARENT, 0f));
            bottomStack.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 6),
                    UiHelper.dp(context, 14), UiHelper.dp(context, 6));
        } else if ("dark".equals(style)) {
            bottomStack.setBackground(UiHelper.createPillDrawable(
                    context, 12, Color.argb(195, 0, 0, 0), Color.TRANSPARENT, 0f));
            bottomStack.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 8),
                    UiHelper.dp(context, 16), UiHelper.dp(context, 8));
        } else if ("solid".equals(style)) {
            bottomStack.setBackground(UiHelper.createPillDrawable(
                    context, 10, Color.argb(235, 8, 10, 14), Color.TRANSPARENT, 0f));
            bottomStack.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 8),
                    UiHelper.dp(context, 16), UiHelper.dp(context, 8));
        } else {
            bottomStack.setBackground(null);
            bottomStack.setPadding(0, 0, 0, 0);
        }
    }

    private void resetHudAutoHide() {
        handler.removeCallbacks(hudAutoHideRunnable);
        if (hudVisible && activeModal == MODAL_NONE && isPlayingNow) {
            handler.postDelayed(hudAutoHideRunnable, HUD_AUTO_HIDE_MS);
        }
    }

    private void focusCenterPlayButton() {
        if (centerPlayWrap == null || !showing || !hudVisible || activeModal != MODAL_NONE) return;
        if (hudLayer.getVisibility() != View.VISIBLE) {
            hudLayer.setVisibility(View.VISIBLE);
        }
        syncPlayerFocusLayers();
        UiHelper.requestTvFocus(centerPlayWrap);
        centerPlayWrap.post(new Runnable() {
            @Override
            public void run() {
                if (showing && hudVisible && activeModal == MODAL_NONE && !hasValidHudFocus()) {
                    UiHelper.requestTvFocus(centerPlayWrap);
                }
            }
        });
    }

    private void updateHudMetaText() {
        String durLabel = formatHumanDuration(lastDurSec);
        String ep = getCurrentSubtitle();
        if (activeServerId.isEmpty() && !activeStreamUrl.isEmpty()) {
            activeServerId = resolveServerIdForUrl(activeStreamUrl, "");
        }
        String srvName = !activeServerId.isEmpty() ? findServerLabel(activeServerId) : "Đang chọn máy chủ";
        StringBuilder sb = new StringBuilder();
        if (!durLabel.isEmpty()) sb.append(durLabel);
        else if (!ep.isEmpty()) sb.append(ep);
        if (sb.length() > 0) sb.append(" · ");
        sb.append(activeQualityLabel).append(" · ").append(srvName);
        if (nativePlayer.isPreloadedFor(preloadedNextUrl)) {
            sb.append(" · Sẵn sàng tập kế");
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
        int trackW = seekTrackContainer.getWidth();
        if (trackW <= 0) return;

        float ratio = (lastDurSec > 0) ? (float) Math.max(0.0, Math.min(1.0, lastPosSec / lastDurSec)) : 0f;
        float bufRatio = Math.max(ratio, Math.min(1f, lastBufferedPct / 100f));

        seekBufferedFill.getLayoutParams().width = Math.round(trackW * bufRatio);
        seekBufferedFill.requestLayout();

        int progW = Math.round(trackW * ratio);
        seekProgressFill.getLayoutParams().width = progW;
        seekProgressFill.requestLayout();

        int dotMargin = Math.max(0, Math.min(trackW - UiHelper.dp(context, 12), progW - UiHelper.dp(context, 6)));
        ((FrameLayout.LayoutParams) seekThumbDot.getLayoutParams()).leftMargin = dotMargin;
        seekThumbDot.requestLayout();

        int skipIntro = resolvedData != null ? resolvedData.skipIntroSec : 0;
        int endCredits = resolvedData != null ? resolvedData.endCreditsSec : 0;
        boolean hasIntro = lastDurSec > 20.0 && skipIntro > 0 && skipIntro < lastDurSec;
        boolean hasOutro = lastDurSec > 20.0 && endCredits > 0 && lastDurSec > endCredits;
        double outroAtSec = hasOutro ? (lastDurSec - endCredits) : -1.0;
        boolean hasSeamless = lastDurSec > 60.0 && Prefs.isSeamlessPreload(context) && nextEpisodeTarget != null;
        double seamlessAtSec = hasSeamless ? Math.max(5.0, lastDurSec - endCredits - 120.0) : -1.0;

        if (hasIntro) {
            seekIntroMarker.setVisibility(View.VISIBLE);
            int mLeft = Math.round(trackW * (float) Math.min(0.98, skipIntro / lastDurSec));
            ((FrameLayout.LayoutParams) seekIntroMarker.getLayoutParams()).leftMargin = Math.max(0, Math.min(trackW - UiHelper.dp(context, 3), mLeft));
            seekIntroMarker.requestLayout();
        } else {
            seekIntroMarker.setVisibility(View.GONE);
        }

        if (hasOutro) {
            seekCreditsMarker.setVisibility(View.VISIBLE);
            int cLeft = Math.round(trackW * (float) Math.max(0.02, outroAtSec / lastDurSec));
            ((FrameLayout.LayoutParams) seekCreditsMarker.getLayoutParams()).leftMargin = Math.max(0, Math.min(trackW - UiHelper.dp(context, 3), cLeft));
            seekCreditsMarker.requestLayout();
        } else {
            seekCreditsMarker.setVisibility(View.GONE);
        }

        if (hasSeamless) {
            seekSeamlessMarker.setVisibility(View.VISIBLE);
            int dotColor = nativePlayer.isPreloadedFor(preloadedNextUrl) ? UiHelper.COLOR_GREEN : UiHelper.COLOR_CYAN;
            seekSeamlessMarker.setBackground(UiHelper.createGlassRect(context, 4, dotColor, Color.TRANSPARENT, 0));
            int sLeft = Math.round(trackW * (float) Math.min(0.98, Math.max(0.02, seamlessAtSec / lastDurSec)));
            ((FrameLayout.LayoutParams) seekSeamlessMarker.getLayoutParams()).leftMargin = Math.max(0, Math.min(trackW - UiHelper.dp(context, 6), sLeft));
            seekSeamlessMarker.requestLayout();
        } else {
            seekSeamlessMarker.setVisibility(View.GONE);
        }

        // Timeline Cue Note (synced with iOS PlayerModal: Mở đầu bộ phim / Kết thúc bộ phim / Xem liền mạch)
        if (lastDurSec > 20.0) {
            double threshold = Math.max(8.0, lastDurSec * 0.015);
            String note = "";
            int noteColor = UiHelper.COLOR_GOLD;
            if (hasIntro && Math.abs(lastPosSec - skipIntro) <= threshold) {
                note = "• Mở đầu bộ phim";
                noteColor = Color.WHITE;
            } else if (hasOutro && Math.abs(lastPosSec - outroAtSec) <= threshold) {
                note = "• Kết thúc bộ phim";
                noteColor = UiHelper.COLOR_GOLD;
            } else if (hasSeamless && Math.abs(lastPosSec - seamlessAtSec) <= threshold) {
                note = "• Xem liền mạch";
                noteColor = nativePlayer.isPreloadedFor(preloadedNextUrl) ? UiHelper.COLOR_GREEN : UiHelper.COLOR_CYAN;
            }
            if (!note.isEmpty()) {
                seekCueNoteTv.setText(note);
                seekCueNoteTv.setTextColor(noteColor);
                seekCueNoteTv.setVisibility(View.VISIBLE);
            } else {
                seekCueNoteTv.setVisibility(View.GONE);
            }
        } else {
            seekCueNoteTv.setVisibility(View.GONE);
        }
    }

    private void refreshQuickBadges() {
        boolean fill = Prefs.isVideoFillCover(context);
        dockFillIcon.setImageResource(fill ? R.drawable.ic_fit_screen : R.drawable.ic_fullscreen);
        dockFillIcon.setColorFilter(fill ? Color.parseColor("#FF2E55") : Color.WHITE, PorterDuff.Mode.SRC_IN);

        boolean autoNext = Prefs.isAutoPlayNext(context);
        dockAutoNextIcon.setColorFilter(autoNext ? Color.parseColor("#FF2E55") : Color.WHITE, PorterDuff.Mode.SRC_IN);

        String contrast = Prefs.getContrastLevel(context);
        dockContrastIcon.setColorFilter(!"Mặc định".equals(contrast) ? UiHelper.COLOR_GOLD : Color.WHITE, PorterDuff.Mode.SRC_IN);

        boolean dubOn = Prefs.isSubAiVoiceEnabled(context);
        dubTopIcon.setColorFilter(dubOn ? Color.parseColor("#FF2E55") : Color.WHITE, PorterDuff.Mode.SRC_IN);

        Models.User u = apiClient.getCachedUser();
        String avUrl = (resolvedData != null && resolvedData.member != null && !resolvedData.member.avatarUrl.isEmpty())
                ? resolvedData.member.avatarUrl : (u != null ? u.avatarUrl : "");
        UiHelper.safeLoadAvatar(memberAvatarIv, avUrl);

        wishlistIconBar.setImageResource(wishlistLocalState ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_outline);
        wishlistIconBar.setColorFilter(wishlistLocalState ? Color.parseColor("#FF2E55") : Color.WHITE, PorterDuff.Mode.SRC_IN);

        String key = launchReq != null ? String.valueOf(launchReq.videosId) : "";
        String react = Prefs.getReaction(context, key);
        likeIconBar.setColorFilter("like".equals(react) ? Color.parseColor("#FF2E55") : Color.WHITE, PorterDuff.Mode.SRC_IN);
        dislikeIconBar.setColorFilter("dislike".equals(react) ? Color.parseColor("#FF2E55") : Color.WHITE, PorterDuff.Mode.SRC_IN);

        float r = Prefs.getPlaybackRate(context);
        speedTextBar.setText(formatRate(r));
    }

    private String normalizeEnhancementLevel(String raw) {
        if ("Sáng nhẹ".equals(raw) || "Thấp".equals(raw)) return "Sáng nhẹ";
        if ("Sáng rõ".equals(raw) || "Cao".equals(raw)) return "Sáng rõ";
        if ("Siêu sáng".equals(raw) || "Rất cao".equals(raw)) return "Siêu sáng";
        return "Mặc định";
    }

    private void cycleContrastQuick() {
        String[] levels = {"Mặc định", "Sáng nhẹ", "Sáng rõ", "Siêu sáng"};
        String cur = normalizeEnhancementLevel(Prefs.getContrastLevel(context));
        int idx = 1;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i].equals(cur)) {
                idx = (i + 1) % levels.length;
                break;
            }
        }
        Prefs.setContrastLevel(context, levels[idx]);
        applyContrastSetting();
        refreshQuickBadges();
        toast("Độ sáng & Chi tiết: " + levels[idx], UiHelper.COLOR_GOLD);
        resetHudAutoHide();
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
        String level = normalizeEnhancementLevel(Prefs.getContrastLevel(context));
        contrastOverlay.setAlpha(0f);
        nativePlayer.setVideoEnhancementMode(level);
        if (showing && hostActivity != null && !hostActivity.isFinishing()) {
            try {
                Window w = hostActivity.getWindow();
                if (w != null) {
                    WindowManager.LayoutParams lp = w.getAttributes();
                    if ("Sáng rõ".equals(level)) {
                        lp.screenBrightness = 0.95f;
                    } else if ("Siêu sáng".equals(level)) {
                        lp.screenBrightness = 1.0f;
                    } else {
                        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                    }
                    w.setAttributes(lp);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void restoreWindowBrightness() {
        if (hostActivity == null || hostActivity.isFinishing()) return;
        try {
            Window w = hostActivity.getWindow();
            if (w != null) {
                WindowManager.LayoutParams lp = w.getAttributes();
                if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                    w.setAttributes(lp);
                }
            }
        } catch (Exception ignored) {
        }
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
        syncBufferingIndicatorVisibility();

        FrameLayout.LayoutParams cardLp = (FrameLayout.LayoutParams) subModalCard.getLayoutParams();
        LinearLayout.LayoutParams scrollLp = (LinearLayout.LayoutParams) subModalScroll.getLayoutParams();
        if (modalType == MODAL_SUBTITLES) {
            // Right-docked translucent panel so live video + real subtitles stay unobstructed
            subModalHost.setBackgroundColor(Color.argb(88, 7, 9, 15));
            cardLp.width = Math.min(UiHelper.dp(context, 520), Math.round(screenW * 0.56f));
            cardLp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            cardLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            cardLp.setMargins(0, UiHelper.dp(context, 14), UiHelper.dp(context, 20), UiHelper.dp(context, 14));
            scrollLp.height = Math.round(screenH * 0.86f) - UiHelper.dp(context, 82);
        } else {
            subModalHost.setBackgroundColor(Color.argb(180, 9, 11, 18));
            cardLp.width = Math.min(UiHelper.dp(context, 700), Math.round(screenW * 0.84f));
            cardLp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            cardLp.gravity = Gravity.CENTER;
            cardLp.setMargins(0, 0, 0, 0);
            scrollLp.height = Math.round(screenH * 0.84f) - UiHelper.dp(context, 84);
        }
        subModalCard.setLayoutParams(cardLp);
        subModalScroll.setLayoutParams(scrollLp);

        subModalHost.animate().cancel();
        subModalHost.setAlpha(0f);
        subModalHost.setVisibility(View.VISIBLE);
        syncPlayerFocusLayers();
        subModalHost.animate().alpha(1f).setDuration(180).setListener(null).start();
        subModalScroll.scrollTo(0, 0);
        renderSubModalContent();

        if (modalType == MODAL_EPISODES && cachedRichEpisodes == null) {
            loadRichEpisodesForModal();
        }
        subModalCloseBtn.post(new Runnable() {
            @Override
            public void run() {
                focusCurrentSubModalDefault();
            }
        });
    }

    private void focusCurrentSubModalDefault() {
        if (activeModal == MODAL_NONE || subModalHost.getVisibility() != View.VISIBLE) return;
        View focusTarget = null;
        if (activeModal == MODAL_SERVERS && !activeServerId.isEmpty()) {
            focusTarget = subModalBody.findViewWithTag("srv_card_" + activeServerId);
        } else if (activeModal == MODAL_EPISODES) {
            focusTarget = subModalBody.findViewWithTag("ep_current_row");
        }
        if (focusTarget == null) {
            focusTarget = findFirstFocusableChild(subModalBody);
        }
        if (focusTarget != null) {
            if (focusTarget.getId() == View.NO_ID) focusTarget.setId(View.generateViewId());
            subModalCloseBtn.setNextFocusDownId(focusTarget.getId());
            UiHelper.requestTvFocus(focusTarget);
            if (activeModal == MODAL_EPISODES) {
                final View scrollTarget = focusTarget;
                subModalScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        int top = scrollTarget.getTop();
                        subModalScroll.smoothScrollTo(0, Math.max(0, top - UiHelper.dp(context, 48)));
                    }
                });
            }
        } else {
            UiHelper.requestTvFocus(subModalCloseBtn);
        }
    }

    private void closeSubModal() {
        subModalHost.animate().cancel();
        activeModal = MODAL_NONE;
        syncPlayerFocusLayers();
        subModalHost.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (activeModal == MODAL_NONE) {
                    subModalHost.setVisibility(View.GONE);
                    syncBufferingIndicatorVisibility();
                    syncPlayerFocusLayers();
                }
            }
        }).start();
        if (!hudVisible) {
            setHudVisible(true);
        } else {
            focusCenterPlayButton();
        }
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
                        if (activeModal == MODAL_EPISODES) {
                            renderSubModalContent();
                            subModalBody.post(new Runnable() {
                                @Override
                                public void run() {
                                    View cur = subModalBody.findViewWithTag("ep_current_row");
                                    if (cur == null) cur = findFirstFocusableChild(subModalBody);
                                    if (cur == null) cur = subModalCloseBtn;
                                    UiHelper.requestTvFocus(cur);
                                    if (cur != subModalCloseBtn) {
                                        subModalScroll.smoothScrollTo(0, Math.max(0, cur.getTop() - UiHelper.dp(context, 48)));
                                    }
                                }
                            });
                        }
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
            UiHelper.makeNonFocusable(hsv);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout sRow = new LinearLayout(context);
            sRow.setOrientation(LinearLayout.HORIZONTAL);
            hsv.addView(sRow);
            if (!apiSeasons.isEmpty()) {
                for (final Models.ApiSeasonItem as : apiSeasons) {
                    boolean active = as.seasonsId.equals(selectedSeasonFilter);
                    TextView chip = makeTextPill(context, as.seasonsName, active);
                    chip.setTag("ep_season_" + as.seasonsId);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSeasonFilter = as.seasonsId;
                            cachedRichEpisodes = null;
                            renderSubModalContent();
                            loadRichEpisodesForModal();
                            subModalBody.post(new Runnable() {
                                @Override
                                public void run() {
                                    View t = subModalBody.findViewWithTag("ep_season_" + selectedSeasonFilter);
                                    if (t == null) t = findFirstFocusableChild(subModalBody);
                                    if (t == null) t = subModalCloseBtn;
                                    UiHelper.requestTvFocus(t);
                                }
                            });
                        }
                    });
                    sRow.addView(chip, rightMarginLp(10));
                }
            } else {
                for (final Models.SeasonItem fs : fallbackSeasons) {
                    final String idStr = String.valueOf(fs.seasonsId);
                    boolean active = idStr.equals(selectedSeasonFilter) || (selectedSeasonFilter.isEmpty() && fs == fallbackSeasons.get(0));
                    TextView chip = makeTextPill(context, fs.seasonsName, active);
                    chip.setTag("ep_season_" + idStr);
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            selectedSeasonFilter = idStr;
                            renderSubModalContent();
                            subModalBody.post(new Runnable() {
                                @Override
                                public void run() {
                                    View t = subModalBody.findViewWithTag("ep_season_" + selectedSeasonFilter);
                                    if (t == null) t = findFirstFocusableChild(subModalBody);
                                    if (t == null) t = subModalCloseBtn;
                                    UiHelper.requestTvFocus(t);
                                }
                            });
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

        // Episodes from Rich API first, fallback to resolved episodes — ALWAYS sorted ascending (Tập 01 -> Tập cuối)
        if (cachedRichEpisodes != null && !cachedRichEpisodes.data.isEmpty()) {
            List<Models.ApiEpisodeItem> sortedRich = new ArrayList<Models.ApiEpisodeItem>(cachedRichEpisodes.data);
            Collections.sort(sortedRich, new Comparator<Models.ApiEpisodeItem>() {
                @Override
                public int compare(Models.ApiEpisodeItem a, Models.ApiEpisodeItem b) {
                    int na = extractEpisodeNumber(a != null ? a.episodeName : "", Integer.MAX_VALUE);
                    int nb = extractEpisodeNumber(b != null ? b.episodeName : "", Integer.MAX_VALUE);
                    if (na != nb) return Integer.compare(na, nb);
                    return 0;
                }
            });
            for (int i = 0; i < sortedRich.size(); i++) {
                final Models.ApiEpisodeItem ep = sortedRich.get(i);
                final int num = extractEpisodeNumber(ep.episodeName, i + 1);
                boolean current = ep.isWatching || (launchReq != null && ep.streamKey.equals(launchReq.streamCode));
                LinearLayout row = buildEpisodeRowCard(
                        buildEpisodeThumbUrl(ep.posterUrl, ep.streamKey),
                        resolveEpisodeFallbackThumbUrl(),
                        ep.episodeName.isEmpty() ? ("Tập " + num) : ep.episodeName,
                        ep.watchedStatus + (ep.remainingTime.isEmpty() ? "" : " • " + ep.remainingTime),
                        ep.percentWatched,
                        current);
                if (current) row.setTag("ep_current_row");
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

        List<Models.EpisodeItem> rawEps = (resolvedData != null && !resolvedData.episodes.isEmpty())
                ? resolvedData.episodes : (launchReq != null ? launchReq.episodes : new ArrayList<Models.EpisodeItem>());
        if (rawEps.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Bộ phim này chỉ có 1 tập đầy đủ.");
            empty.setTextColor(UiHelper.COLOR_TEXT_MUTED);
            empty.setTextSize(14f);
            subModalBody.addView(empty);
            return;
        }
        List<Models.EpisodeItem> sortedEps = new ArrayList<Models.EpisodeItem>(rawEps);
        Collections.sort(sortedEps, new Comparator<Models.EpisodeItem>() {
            @Override
            public int compare(Models.EpisodeItem a, Models.EpisodeItem b) {
                int na = extractEpisodeNumber(a != null ? a.episodesName : "", a != null ? a.order : Integer.MAX_VALUE);
                int nb = extractEpisodeNumber(b != null ? b.episodesName : "", b != null ? b.order : Integer.MAX_VALUE);
                if (na != nb) return Integer.compare(na, nb);
                return Integer.compare(a != null ? a.episodesId : 0, b != null ? b.episodesId : 0);
            }
        });
        for (int i = 0; i < sortedEps.size(); i++) {
            final Models.EpisodeItem ep = sortedEps.get(i);
            final int num = extractEpisodeNumber(ep.episodesName, i + 1);
            boolean current = launchReq != null && ep.streamCode.equals(launchReq.streamCode);
            LinearLayout row = buildEpisodeRowCard(
                    buildEpisodeThumbUrl("", ep.streamCode),
                    resolveEpisodeFallbackThumbUrl(),
                    ep.episodesName.isEmpty() ? ("Tập " + num) : ep.episodesName,
                    current ? "Đang phát" : "Nhấn OK để phát",
                    current ? 100 : 0,
                    current);
            if (current) row.setTag("ep_current_row");
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

    private static int extractEpisodeNumber(String name, int fallback) {
        if (name != null && !name.trim().isEmpty()) {
            Matcher m = Pattern.compile("(?:t[ậa]p|episode|ep)\\s*0*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(name);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ignored) {
                }
            }
            Matcher m2 = Pattern.compile("0*(\\d+)").matcher(name);
            if (m2.find()) {
                try {
                    return Integer.parseInt(m2.group(1));
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
    }

    private String resolveEpisodeFallbackThumbUrl() {
        if (resolvedData != null && resolvedData.movie != null) {
            String land = resolvedData.movie.getLandscapeUrl();
            if (land != null && !land.isEmpty()) return land;
            String port = resolvedData.movie.getPortraitUrl();
            if (port != null && !port.isEmpty()) return port;
        }
        if (launchReq != null) {
            String land = launchReq.getLandscapeUrl();
            if (land != null && !land.isEmpty()) return land;
            String port = launchReq.getPortraitUrl();
            if (port != null && !port.isEmpty()) return port;
        }
        return "";
    }

    private LinearLayout buildEpisodeRowCard(String thumbUrl, String fallbackThumbUrl, String title, String statusText, int progressPct, boolean current) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 10),
                UiHelper.dp(context, 16), UiHelper.dp(context, 10));
        applyPlayerFocusButton(row, context, 18, current, 1.03f);

        FrameLayout thumbWrap = new FrameLayout(context);
        thumbWrap.setBackground(UiHelper.createPillDrawable(
                context, 12, Color.parseColor("#141824"), Color.argb(32, 255, 255, 255), 1f));
        thumbWrap.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), 12));
            }
        });
        thumbWrap.setClipToOutline(true);

        ImageView thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        UiHelper.safeLoadImageWithFallback(thumb, thumbUrl, fallbackThumbUrl, R.drawable.default_poster, 0);
        thumbWrap.addView(thumb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 122), UiHelper.dp(context, 70));
        tLp.rightMargin = UiHelper.dp(context, 14);
        row.addView(thumbWrap, tLp);

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

    private void refreshServersModalPreservingFocus() {
        if (activeModal != MODAL_SERVERS) return;
        View focused = subModalHost.findFocus();
        final Object focusTag = focused != null ? focused.getTag() : null;
        renderSubModalContent();
        if (focusTag != null) {
            View target = subModalBody.findViewWithTag(focusTag);
            if (target != null) {
                UiHelper.requestTvFocus(target);
                return;
            }
        }
        if (!hasValidModalFocus()) {
            focusCurrentSubModalDefault();
        }
    }

    private void renderServersModal() {
        if (activeServerId.isEmpty() && !activeStreamUrl.isEmpty()) {
            activeServerId = resolveServerIdForUrl(activeStreamUrl, "");
        }

        // Active server summary banner
        LinearLayout summaryRow = new LinearLayout(context);
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        summaryRow.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 10),
                UiHelper.dp(context, 14), UiHelper.dp(context, 10));
        summaryRow.setBackground(UiHelper.createPillDrawable(
                context, 14, Color.argb(145, 22, 27, 42), Color.argb(46, 255, 255, 255), 1f));

        View liveDot = new View(context);
        liveDot.setBackground(UiHelper.createPillDrawable(
                context, 6, !activeServerId.isEmpty() ? UiHelper.COLOR_GREEN : UiHelper.COLOR_GOLD, Color.TRANSPARENT, 0f));
        LinearLayout.LayoutParams ldLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 9), UiHelper.dp(context, 9));
        ldLp.rightMargin = UiHelper.dp(context, 10);
        summaryRow.addView(liveDot, ldLp);

        TextView summaryTv = new TextView(context);
        summaryTv.setText(!activeServerId.isEmpty()
                ? ("Đang phát: " + findServerLabel(activeServerId))
                : "Đang kết nối máy chủ phát...");
        summaryTv.setTextColor(Color.WHITE);
        summaryTv.setTextSize(13.5f);
        summaryTv.setTypeface(null, Typeface.BOLD);
        summaryRow.addView(summaryTv);
        addSpacedView(summaryRow, 12);

        LinearLayout topTools = new LinearLayout(context);
        topTools.setOrientation(LinearLayout.HORIZONTAL);
        topTools.setClipChildren(false);
        topTools.setClipToPadding(false);
        boolean autoOpt = Prefs.isAutoOptimalServer(context);
        final TextView btnAutoOpt = makeTextPill(context, autoOpt ? "✓ Tự chọn máy chủ tối ưu: BẬT" : "Tự chọn máy chủ tối ưu: TẮT", autoOpt);
        btnAutoOpt.setTag("srv_auto_opt");
        btnAutoOpt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Prefs.isAutoOptimalServer(context);
                Prefs.setAutoOptimalServer(context, next);
                btnAutoOpt.setText(next ? "✓ Tự chọn máy chủ tối ưu: BẬT" : "Tự chọn máy chủ tối ưu: TẮT");
                btnAutoOpt.setBackground(createPlayerFocusSelector(context, 14, next));
            }
        });
        topTools.addView(btnAutoOpt, rightMarginLp(10));

        TextView btnPing = makeTextPill(context, "Đo lại tốc độ", false);
        btnPing.setTag("srv_reping");
        btnPing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast("Đang đo tốc độ máy chủ...", UiHelper.COLOR_CYAN);
                serverOrchestrator.measurePings(new ServerOrchestrator.Callback<ServerOrchestrator.PingSnapshot>() {
                    @Override
                    public void onResult(ServerOrchestrator.PingSnapshot snap) {
                        List<ServerOrchestrator.ServerState> updated = serverOrchestrator.applyPings(serverStates, snap);
                        serverStates.clear();
                        serverStates.addAll(updated);
                        if (activeModal == MODAL_SERVERS) refreshServersModalPreservingFocus();
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
            boolean active = !activeServerId.isEmpty() && srv.id.equals(activeServerId);
            LinearLayout card = new LinearLayout(context);
            card.setTag("srv_card_" + srv.id);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(UiHelper.dp(context, 18), UiHelper.dp(context, 14),
                    UiHelper.dp(context, 18), UiHelper.dp(context, 14));
            applyPlayerFocusButton(card, context, 18, active, 1.03f);

            LinearLayout left = new LinearLayout(context);
            left.setOrientation(LinearLayout.VERTICAL);

            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setText(srv.label);
            title.setTextColor(Color.WHITE);
            title.setTextSize(15.5f);
            title.setTypeface(null, Typeface.BOLD);
            titleRow.addView(title);

            if (active) {
                TextView playingBadge = new TextView(context);
                playingBadge.setText("● Đang phát");
                playingBadge.setTextColor(Color.WHITE);
                playingBadge.setTextSize(11f);
                playingBadge.setTypeface(null, Typeface.BOLD);
                playingBadge.setPadding(UiHelper.dp(context, 9), UiHelper.dp(context, 2),
                        UiHelper.dp(context, 9), UiHelper.dp(context, 2));
                playingBadge.setBackground(UiHelper.createPillDrawable(
                        context, 10, Color.argb(235, 22, 163, 74), Color.TRANSPARENT, 0f));
                LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                pbLp.leftMargin = UiHelper.dp(context, 10);
                titleRow.addView(playingBadge, pbLp);
            }
            left.addView(titleRow);

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
        final List<Runnable> stateUpdaters = new ArrayList<Runnable>();
        final Runnable refreshAllInPlace = new Runnable() {
            @Override
            public void run() {
                subtitleOverlay.renderAt(lastPosSec);
                applySubtitleBgSetting();
                refreshQuickBadges();
                for (int i = 0; i < stateUpdaters.size(); i++) {
                    stateUpdaters.get(i).run();
                }
            }
        };

        // Live Preview Banner (updates in-place alongside real video subtitles behind)
        final LinearLayout previewCard = new LinearLayout(context);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setGravity(Gravity.CENTER);
        previewCard.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 10),
                UiHelper.dp(context, 14), UiHelper.dp(context, 10));
        previewCard.setBackground(UiHelper.createPillDrawable(
                context, 14, Color.argb(145, 8, 10, 16), Color.argb(40, 255, 255, 255), 1f));

        final LinearLayout previewPill = new LinearLayout(context);
        previewPill.setOrientation(LinearLayout.VERTICAL);
        previewPill.setGravity(Gravity.CENTER_HORIZONTAL);
        final TextView previewLine1 = new TextView(context);
        previewLine1.setGravity(Gravity.CENTER);
        previewLine1.setMaxLines(1);
        previewLine1.setEllipsize(TextUtils.TruncateAt.END);
        previewPill.addView(previewLine1);

        final TextView previewLine2 = new TextView(context);
        previewLine2.setGravity(Gravity.CENTER);
        previewLine2.setMaxLines(1);
        previewLine2.setEllipsize(TextUtils.TruncateAt.END);
        previewPill.addView(previewLine2);

        previewCard.addView(previewPill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addSpacedView(previewCard, 12);

        stateUpdaters.add(new Runnable() {
            @Override
            public void run() {
                boolean showVi = Prefs.isSubShowVi(context);
                boolean showEn = Prefs.isSubShowEn(context);
                boolean rev = Prefs.isSubReverse(context);
                SubtitleEngine.ColorOption col = SubtitleEngine.findColor(Prefs.getSubColor(context));
                Typeface tf = SubtitleEngine.resolveTypeface(Prefs.getSubFont(context));
                String outline = Prefs.getSubOutline(context);
                String bgStyle = Prefs.getSubBgStyle(context);
                int step = Prefs.getSubFontStep(context);
                float spMain = Math.max(12f, Math.min(20f, 11f + step * 0.45f));
                float spSub = Math.max(10.5f, spMain * 0.82f);

                if ("semi".equals(bgStyle)) {
                    previewPill.setBackground(UiHelper.createPillDrawable(context, 8, Color.argb(120, 0, 0, 0), Color.TRANSPARENT, 0f));
                    previewPill.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 4), UiHelper.dp(context, 10), UiHelper.dp(context, 4));
                } else if ("dark".equals(bgStyle)) {
                    previewPill.setBackground(UiHelper.createPillDrawable(context, 8, Color.argb(195, 0, 0, 0), Color.TRANSPARENT, 0f));
                    previewPill.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 4), UiHelper.dp(context, 10), UiHelper.dp(context, 4));
                } else if ("solid".equals(bgStyle)) {
                    previewPill.setBackground(UiHelper.createPillDrawable(context, 8, Color.argb(235, 8, 10, 14), Color.TRANSPARENT, 0f));
                    previewPill.setPadding(UiHelper.dp(context, 10), UiHelper.dp(context, 4), UiHelper.dp(context, 10), UiHelper.dp(context, 4));
                } else {
                    previewPill.setBackground(null);
                    previewPill.setPadding(0, 0, 0, 0);
                }

                float shadowR = "none".equals(outline) ? 0f : ("shadow".equals(outline) ? 5f : 3f);
                previewLine1.setTypeface(tf, Typeface.BOLD);
                previewLine2.setTypeface(tf, Typeface.NORMAL);
                previewLine1.setShadowLayer(shadowR, 0f, shadowR > 0 ? 1.5f : 0f, Color.BLACK);
                previewLine2.setShadowLayer(shadowR, 0f, shadowR > 0 ? 1.5f : 0f, Color.BLACK);

                if (!showVi && !showEn) {
                    previewLine1.setVisibility(View.VISIBLE);
                    previewLine2.setVisibility(View.GONE);
                    previewLine1.setText("Phụ đề đang tắt");
                    previewLine1.setTextColor(UiHelper.COLOR_TEXT_MUTED);
                    previewLine1.setTextSize(13f);
                } else if (showVi && showEn) {
                    String viSample = "Chào mừng bạn đến với rạp phim tại gia";
                    String enSample = "Welcome to your home cinema";
                    previewLine1.setVisibility(View.VISIBLE);
                    previewLine2.setVisibility(View.VISIBLE);
                    previewLine1.setText(rev ? enSample : viSample);
                    previewLine2.setText(rev ? viSample : enSample);
                    previewLine1.setTextColor(col.mainColor);
                    previewLine2.setTextColor(col.subColor);
                    previewLine1.setTextSize(spMain);
                    previewLine2.setTextSize(spSub);
                } else {
                    previewLine1.setVisibility(View.VISIBLE);
                    previewLine2.setVisibility(View.GONE);
                    previewLine1.setText(showVi ? "Chào mừng bạn đến với rạp phim tại gia" : "Welcome to your home cinema");
                    previewLine1.setTextColor(col.mainColor);
                    previewLine1.setTextSize(spMain);
                }
            }
        });

        // Section 1: Hiển thị & Bố cục (Clean toggle rows — in-place update)
        addModalSectionLabel("Hiển thị & Bố cục phụ đề");
        LinearLayout rowShowVi = addSettingToggleRow(
                "Phụ đề Tiếng Việt", null,
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isSubShowVi(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubShowVi(context, !Prefs.isSubShowVi(context));
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);
        rowShowVi.setNextFocusUpId(subModalCloseBtn.getId());

        addSettingToggleRow(
                "Phụ đề Tiếng Anh / Dịch", null,
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isSubShowEn(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubShowEn(context, !Prefs.isSubShowEn(context));
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Đảo vị trí 2 dòng", null,
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isSubReverse(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubReverse(context, !Prefs.isSubReverse(context));
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Tự động cỡ chữ & vị trí", null,
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isSubAutoLayout(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, !Prefs.isSubAutoLayout(context));
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        // Section 2: Kích thước, Vị trí & Đồng bộ thời gian (In-place Steppers — ZERO focus reset!)
        addModalSectionLabel("Cỡ chữ, Vị trí & Đồng bộ thời gian");
        addInPlaceStepperRow("Cỡ chữ thủ công",
                new StringSupplierCompat() {
                    @Override
                    public String get() {
                        return String.valueOf(Prefs.getSubFontStep(context));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubFontStep(context, Prefs.getSubFontStep(context) - 1);
                        refreshAllInPlace.run();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubFontStep(context, Prefs.getSubFontStep(context) + 1);
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        addInPlaceStepperRow("Vị trí từ đáy màn hình",
                new StringSupplierCompat() {
                    @Override
                    public String get() {
                        return Prefs.getSubPositionPct(context) + "%";
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubPositionPct(context, Prefs.getSubPositionPct(context) - 2);
                        refreshAllInPlace.run();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAutoLayout(context, false);
                        Prefs.setSubPositionPct(context, Prefs.getSubPositionPct(context) + 2);
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        addInPlaceStepperRow("Độ trễ phụ đề",
                new StringSupplierCompat() {
                    @Override
                    public String get() {
                        return String.format(Locale.US, "%+.1fs", Prefs.getSubDelaySec(context));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubDelaySec(context, Math.max(-15f, Prefs.getSubDelaySec(context) - 0.5f));
                        refreshAllInPlace.run();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubDelaySec(context, Math.min(15f, Prefs.getSubDelaySec(context) + 0.5f));
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        addInPlaceStepperRow("Kéo dài hiển thị",
                new StringSupplierCompat() {
                    @Override
                    public String get() {
                        return String.format(Locale.US, "+%.1fs", Prefs.getSubExtendSec(context));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubExtendSec(context, Math.max(0f, Prefs.getSubExtendSec(context) - 0.5f));
                        refreshAllInPlace.run();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubExtendSec(context, Math.min(10f, Prefs.getSubExtendSec(context) + 0.5f));
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        // Section 3: Phông chữ, Màu sắc, Viền chữ, Nền (Wrapped clean TV chip grids — in-place update)
        addModalSectionLabel("Phông chữ");
        {
            final List<TextView> fontChips = new ArrayList<TextView>();
            final List<String> fontVals = new ArrayList<String>();
            List<View> gridItems = new ArrayList<View>();
            for (final SubtitleEngine.FontOption f : SubtitleEngine.FONT_OPTIONS) {
                TextView tv = makeGridChip(context, f.label, f.value.equals(Prefs.getSubFont(context)));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setSubFont(context, f.value);
                        refreshAllInPlace.run();
                    }
                });
                fontChips.add(tv);
                fontVals.add(f.value);
                gridItems.add(tv);
            }
            addChipGridRows(gridItems, 3, 10);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String cur = Prefs.getSubFont(context);
                    for (int i = 0; i < fontChips.size(); i++) {
                        boolean sel = fontVals.get(i).equals(cur);
                        fontChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        addModalSectionLabel("Màu sắc chữ");
        {
            final List<LinearLayout> colorChips = new ArrayList<LinearLayout>();
            final List<String> colorKeys = new ArrayList<String>();
            List<View> gridItems = new ArrayList<View>();
            for (final SubtitleEngine.ColorOption c : SubtitleEngine.COLOR_OPTIONS) {
                LinearLayout chip = makeColorDotChip(context, c.label, c.mainColor, c.key.equals(Prefs.getSubColor(context)));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setSubColor(context, c.key);
                        refreshAllInPlace.run();
                    }
                });
                colorChips.add(chip);
                colorKeys.add(c.key);
                gridItems.add(chip);
            }
            addChipGridRows(gridItems, 3, 10);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String cur = Prefs.getSubColor(context);
                    for (int i = 0; i < colorChips.size(); i++) {
                        boolean sel = colorKeys.get(i).equals(cur);
                        colorChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        addModalSectionLabel("Viền chữ");
        {
            final List<TextView> outChips = new ArrayList<TextView>();
            final List<String> outKeys = new ArrayList<String>();
            List<View> gridItems = new ArrayList<View>();
            for (final SubtitleEngine.OutlineOption o : SubtitleEngine.OUTLINE_OPTIONS) {
                TextView tv = makeGridChip(context, o.label, o.key.equals(Prefs.getSubOutline(context)));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setSubOutline(context, o.key);
                        refreshAllInPlace.run();
                    }
                });
                outChips.add(tv);
                outKeys.add(o.key);
                gridItems.add(tv);
            }
            addChipGridRows(gridItems, 3, 10);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String cur = Prefs.getSubOutline(context);
                    for (int i = 0; i < outChips.size(); i++) {
                        boolean sel = outKeys.get(i).equals(cur);
                        outChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        addModalSectionLabel("Nền phụ đề");
        {
            final String[] bgKeys = {"none", "semi", "dark", "solid"};
            final String[] bgLabels = {"Trong suốt", "Mờ nhẹ", "Tối đậm", "Đen đặc"};
            final List<TextView> bgChips = new ArrayList<TextView>();
            List<View> gridItems = new ArrayList<View>();
            for (int i = 0; i < bgKeys.length; i++) {
                final String key = bgKeys[i];
                TextView tv = makeGridChip(context, bgLabels[i], key.equals(Prefs.getSubBgStyle(context)));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setSubBgStyle(context, key);
                        refreshAllInPlace.run();
                    }
                });
                bgChips.add(tv);
                gridItems.add(tv);
            }
            addChipGridRows(gridItems, 4, 12);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String cur = Prefs.getSubBgStyle(context);
                    for (int i = 0; i < bgChips.size(); i++) {
                        boolean sel = bgKeys[i].equals(cur);
                        bgChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        // Section 4: Dịch phụ đề trực tiếp (108 ngôn ngữ — in-place)
        addModalSectionLabel("Dịch phụ đề trực tiếp (108 ngôn ngữ)");
        {
            LinearLayout langRow = new LinearLayout(context);
            langRow.setOrientation(LinearLayout.HORIZONTAL);
            langRow.setGravity(Gravity.CENTER_VERTICAL);
            langRow.setClipChildren(false);
            langRow.setClipToPadding(false);
            langRow.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 8),
                    UiHelper.dp(context, 12), UiHelper.dp(context, 8));
            langRow.setBackground(UiHelper.createPillDrawable(
                    context, 14, Color.argb(120, 26, 30, 44), Color.TRANSPARENT, 0f));

            final TextView btnLangOff = makeTextPill(context, "Tắt dịch", Prefs.getSubTranslateLang(context).isEmpty());
            btnLangOff.setId(View.generateViewId());
            btnLangOff.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setSubTranslateLang(context, "");
                    refreshAllInPlace.run();
                }
            });
            langRow.addView(btnLangOff, rightMarginLp(8));

            TextView btnLangPrev = makeTextPill(context, "◀", false);
            btnLangPrev.setId(View.generateViewId());
            btnLangPrev.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleTranslateLanguageInPlace(-1);
                    refreshAllInPlace.run();
                }
            });
            langRow.addView(btnLangPrev, rightMarginLp(6));

            final TextView curLangLabel = new TextView(context);
            curLangLabel.setTextColor(UiHelper.COLOR_CYAN);
            curLangLabel.setTextSize(13f);
            curLangLabel.setTypeface(null, Typeface.BOLD);
            curLangLabel.setGravity(Gravity.CENTER);
            curLangLabel.setMaxLines(1);
            curLangLabel.setEllipsize(TextUtils.TruncateAt.END);
            langRow.addView(curLangLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView btnLangNext = makeTextPill(context, "▶", false);
            btnLangNext.setId(View.generateViewId());
            btnLangNext.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleTranslateLanguageInPlace(1);
                    refreshAllInPlace.run();
                }
            });
            LinearLayout.LayoutParams lnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lnLp.leftMargin = UiHelper.dp(context, 6);
            langRow.addView(btnLangNext, lnLp);

            btnLangOff.setNextFocusRightId(btnLangPrev.getId());
            btnLangPrev.setNextFocusLeftId(btnLangOff.getId());
            btnLangPrev.setNextFocusRightId(btnLangNext.getId());
            btnLangNext.setNextFocusLeftId(btnLangPrev.getId());

            addSpacedView(langRow, 12);

            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String lang = Prefs.getSubTranslateLang(context);
                    curLangLabel.setText(findTranslateLangName(lang));
                    btnLangOff.setBackground(createPlayerFocusSelector(context, 14, lang.isEmpty()));
                }
            });
        }

        // Section 5: Thuyết minh AI (in-place)
        addModalSectionLabel("Thuyết minh AI");
        addSettingToggleRow(
                "Bật Thuyết minh AI", null,
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isSubAiVoiceEnabled(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        boolean next = !Prefs.isSubAiVoiceEnabled(context);
                        Prefs.setSubAiVoiceEnabled(context, next);
                        if (!next) {
                            dubbingEngine.stopAndResetQueue();
                        } else {
                            dubbingEngine.onSubtitlesLoaded(vnCues, lastPosSec);
                        }
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        {
            final String[] vModes = {"adaptive", "hoaimy", "namminh", "google"};
            final String[] vLabels = {"Tự động", "Hoài My", "Nam Minh", "Google"};
            final List<TextView> vChips = new ArrayList<TextView>();
            List<View> gridItems = new ArrayList<View>();
            for (int i = 0; i < vModes.length; i++) {
                final String vm = vModes[i];
                TextView tv = makeGridChip(context, vLabels[i], vm.equals(Prefs.getSubAiVoiceMode(context)));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setSubAiVoiceMode(context, vm);
                        dubbingEngine.stopAndResetQueue();
                        if (Prefs.isSubAiVoiceEnabled(context)) {
                            dubbingEngine.onSubtitlesLoaded(vnCues, lastPosSec);
                        }
                        refreshAllInPlace.run();
                    }
                });
                vChips.add(tv);
                gridItems.add(tv);
            }
            addChipGridRows(gridItems, 4, 8);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String cur = Prefs.getSubAiVoiceMode(context);
                    for (int i = 0; i < vChips.size(); i++) {
                        boolean sel = vModes[i].equals(cur);
                        vChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        addInPlaceStepperRow("Âm lượng Thuyết minh",
                new StringSupplierCompat() {
                    @Override
                    public String get() {
                        return Prefs.getSubAiVoiceVolume(context) + "%";
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAiVoiceVolume(context, Prefs.getSubAiVoiceVolume(context) - 10);
                        dubbingEngine.syncVolumeNow();
                        refreshAllInPlace.run();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSubAiVoiceVolume(context, Prefs.getSubAiVoiceVolume(context) + 10);
                        dubbingEngine.syncVolumeNow();
                        refreshAllInPlace.run();
                    }
                }, stateUpdaters);

        // Section 6: Reload / Reset Actions (in-place)
        LinearLayout utilRow = new LinearLayout(context);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);
        utilRow.setClipChildren(false);
        utilRow.setClipToPadding(false);

        TextView btnReloadSub = makeGridChip(context, "Tải lại phụ đề", false);
        btnReloadSub.setId(View.generateViewId());
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
                        dubbingEngine.onSubtitlesLoaded(vnCues, lastPosSec);
                        toast("Đã tải lại phụ đề mới nhất", UiHelper.COLOR_GREEN);
                    }
                });
            }
        });
        LinearLayout.LayoutParams u1Lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        u1Lp.rightMargin = UiHelper.dp(context, 10);
        utilRow.addView(btnReloadSub, u1Lp);

        TextView btnResetSub = makeGridChip(context, "Khôi phục mặc định", true);
        btnResetSub.setId(View.generateViewId());
        btnResetSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.resetSubtitlePrefs(context);
                dubbingEngine.stopAndResetQueue();
                refreshAllInPlace.run();
                toast("Đã khôi phục cài đặt phụ đề mặc định", UiHelper.COLOR_CYAN);
            }
        });
        utilRow.addView(btnResetSub, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnReloadSub.setNextFocusRightId(btnResetSub.getId());
        btnResetSub.setNextFocusLeftId(btnReloadSub.getId());
        addSpacedView(utilRow, 8);

        // Initial state sync
        for (int i = 0; i < stateUpdaters.size(); i++) {
            stateUpdaters.get(i).run();
        }
    }

    private void renderSettingsModal() {
        final List<Runnable> stateUpdaters = new ArrayList<Runnable>();
        final Runnable refreshSettingsInPlace = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < stateUpdaters.size(); i++) {
                    stateUpdaters.get(i).run();
                }
            }
        };

        // 1. Tốc độ phát (7 equal-width chips in 1 clean row)
        addModalSectionLabel("Tốc độ phát video");
        {
            final float[] rates = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
            final List<TextView> speedChips = new ArrayList<TextView>();
            List<View> items = new ArrayList<View>();
            for (final float r : rates) {
                TextView tv = makeGridChip(context, formatRate(r), Math.abs(Prefs.getPlaybackRate(context) - r) < 0.05f);
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applySpeedSetting(r);
                        refreshSettingsInPlace.run();
                    }
                });
                speedChips.add(tv);
                items.add(tv);
            }
            addChipGridRows(items, 7, 14);
            if (!items.isEmpty()) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setNextFocusUpId(subModalCloseBtn.getId());
                }
            }
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    float cur = Prefs.getPlaybackRate(context);
                    for (int i = 0; i < speedChips.size(); i++) {
                        boolean sel = Math.abs(cur - rates[i]) < 0.05f;
                        speedChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        // 2. Chế độ phát & Chuyển tập (Clean full-width TV toggle rows — zero overflow, in-place toggle)
        addModalSectionLabel("Chế độ phát & Chuyển tập");
        addSettingToggleRow(
                "Tràn viền màn hình", "Phóng to video lấp đầy khung hình TV",
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isVideoFillCover(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        boolean next = !Prefs.isVideoFillCover(context);
                        Prefs.setVideoFillCover(context, next);
                        applyResizeSetting();
                        refreshSettingsInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Tự chuyển tập", "Tự động chuyển sang tập tiếp theo khi xem xong",
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isAutoPlayNext(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setAutoPlayNext(context, !Prefs.isAutoPlayNext(context));
                        refreshQuickBadges();
                        refreshSettingsInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Tải trước tập kế", "Chuẩn bị sẵn tập kế tiếp giúp chuyển tập không khựng",
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isSeamlessPreload(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setSeamlessPreload(context, !Prefs.isSeamlessPreload(context));
                        refreshSettingsInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Hiệu ứng chuyển cảnh", "Chuyển cảnh êm dịu khi sang tập mới",
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isTransitionEffect(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setTransitionEffect(context, !Prefs.isTransitionEffect(context));
                        refreshSettingsInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Tự bỏ qua giới thiệu", "Tự động lướt qua đoạn mở đầu của phim",
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isAutoSkipIntro(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setAutoSkipIntro(context, !Prefs.isAutoSkipIntro(context));
                        refreshSettingsInPlace.run();
                    }
                }, stateUpdaters);

        addSettingToggleRow(
                "Giữ Thuyết minh khi chuyển tập", "Tự động giữ trạng thái Thuyết minh AI ở tập sau",
                new BooleanSupplierCompat() {
                    @Override
                    public boolean get() {
                        return Prefs.isAutoDubNext(context);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        Prefs.setAutoDubNext(context, !Prefs.isAutoDubNext(context));
                        refreshSettingsInPlace.run();
                    }
                }, stateUpdaters);

        // 3. Thời gian đếm ngược chuyển tập
        addModalSectionLabel("Thời gian đếm ngược chuyển tập");
        {
            final int[] delays = {5, 10, 15, 20};
            final List<TextView> delayChips = new ArrayList<TextView>();
            List<View> items = new ArrayList<View>();
            for (final int sec : delays) {
                TextView tv = makeGridChip(context, sec + " giây", sec == Prefs.getNextEpisodeDelaySec(context));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setNextEpisodeDelaySec(context, sec);
                        refreshSettingsInPlace.run();
                    }
                });
                delayChips.add(tv);
                items.add(tv);
            }
            addChipGridRows(items, 4, 14);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    int cur = Prefs.getNextEpisodeDelaySec(context);
                    for (int i = 0; i < delayChips.size(); i++) {
                        boolean sel = delays[i] == cur;
                        delayChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        // 4. Độ sáng & Chi tiết (Máy chiếu)
        addModalSectionLabel("Độ sáng & Chi tiết (Máy chiếu)");
        {
            final String[] contrasts = {"Mặc định", "Sáng nhẹ", "Sáng rõ", "Siêu sáng"};
            final List<TextView> cChips = new ArrayList<TextView>();
            List<View> items = new ArrayList<View>();
            String curInit = normalizeEnhancementLevel(Prefs.getContrastLevel(context));
            for (final String lv : contrasts) {
                TextView tv = makeGridChip(context, lv, lv.equals(curInit));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setContrastLevel(context, lv);
                        applyContrastSetting();
                        refreshQuickBadges();
                        refreshSettingsInPlace.run();
                    }
                });
                cChips.add(tv);
                items.add(tv);
            }
            addChipGridRows(items, 4, 14);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    String cur = normalizeEnhancementLevel(Prefs.getContrastLevel(context));
                    for (int i = 0; i < cChips.size(); i++) {
                        boolean sel = contrasts[i].equals(cur);
                        cChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        // 5. Hẹn giờ tự động tắt phát
        addModalSectionLabel("Hẹn giờ tự động tắt phát");
        {
            final int[] sleepOpts = {0, 15, 30, 60, 90};
            final List<TextView> slChips = new ArrayList<TextView>();
            List<View> items = new ArrayList<View>();
            for (final int min : sleepOpts) {
                TextView tv = makeGridChip(context, min == 0 ? "Tắt" : (min + " phút"), min == sleepMinutesSetting);
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
                        refreshSettingsInPlace.run();
                    }
                });
                slChips.add(tv);
                items.add(tv);
            }
            addChipGridRows(items, 5, 16);
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < slChips.size(); i++) {
                        boolean sel = sleepOpts[i] == sleepMinutesSetting;
                        slChips.get(i).setBackground(createPlayerFocusSelector(context, 12, sel));
                    }
                }
            });
        }

        // 6. Tải lại trình phát hiện tại (Full-width button with generous bottom clearance)
        TextView btnReload = makeGridChip(context, "Tải lại trình phát hiện tại", true);
        btnReload.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 11),
                UiHelper.dp(context, 16), UiHelper.dp(context, 11));
        btnReload.setTextSize(13.5f);
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSubModal();
                reloadCurrentEpisode();
            }
        });
        addSpacedView(btnReload, 12);

        // Initial sync
        refreshSettingsInPlace.run();
    }

    private void renderInfoAndMemberModal() {
        LinearLayout tabs = new LinearLayout(context);
        TextView tInfo = makeTextPill(context, "Thông tin phim", activeModal == MODAL_INFO);
        tInfo.setTag("modal_tab_info");
        tInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeModal = MODAL_INFO;
                renderSubModalContent();
                subModalBody.post(new Runnable() {
                    @Override
                    public void run() {
                        View t = subModalBody.findViewWithTag("modal_tab_info");
                        if (t != null) UiHelper.requestTvFocus(t);
                    }
                });
            }
        });
        TextView tMem = makeTextPill(context, "Hồ sơ thành viên", activeModal == MODAL_MEMBER);
        tMem.setTag("modal_tab_member");
        tMem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeModal = MODAL_MEMBER;
                renderSubModalContent();
                subModalBody.post(new Runnable() {
                    @Override
                    public void run() {
                        View t = subModalBody.findViewWithTag("modal_tab_member");
                        if (t != null) UiHelper.requestTvFocus(t);
                    }
                });
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

            FrameLayout posterWrap = new FrameLayout(context);
            posterWrap.setBackground(UiHelper.createPillDrawable(
                    context, 16, Color.parseColor("#141824"), Color.argb(38, 255, 255, 255), 1f));
            posterWrap.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), 16));
                }
            });
            posterWrap.setClipToOutline(true);

            ImageView poster = new ImageView(context);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Models.StreamMovieContext mc = resolvedData != null ? resolvedData.movie : null;
            String pUrl = mc != null ? mc.getPortraitUrl() : "";
            String fallbackUrl = mc != null ? mc.getLandscapeUrl() : "";
            if ((pUrl == null || pUrl.isEmpty()) && launchReq != null) {
                pUrl = launchReq.getPortraitUrl();
            }
            if ((fallbackUrl == null || fallbackUrl.isEmpty()) && launchReq != null) {
                fallbackUrl = launchReq.getLandscapeUrl();
            }
            UiHelper.safeLoadImageWithFallback(poster, pUrl, fallbackUrl, R.drawable.default_poster, 0);
            posterWrap.addView(poster, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 130), UiHelper.dp(context, 195));
            pLp.rightMargin = UiHelper.dp(context, 18);
            card.addView(posterWrap, pLp);

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(context);
            title.setText(getCurrentTitle());
            title.setTextColor(Color.WHITE);
            title.setTextSize(19f);
            title.setTypeface(null, Typeface.BOLD);
            col.addView(title);

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
            final TextView wBtn = makeTextPill(context, wishlistLocalState ? "✓ Đã trong Danh sách" : "+ Thêm vào Danh sách", wishlistLocalState);
            wBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleWishlist();
                    boolean nextState = !wishlistLocalState;
                    wBtn.setText(nextState ? "✓ Đã trong Danh sách" : "+ Thêm vào Danh sách");
                    wBtn.setBackground(createPlayerFocusSelector(context, 14, nextState));
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

    // ---- Modal Helper Widgets (In-Place State Updates & Crisp White Border TV Focus) ----

    private interface BooleanSupplierCompat {
        boolean get();
    }

    private interface StringSupplierCompat {
        String get();
    }

    private void addModalSectionLabel(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiHelper.dp(context, 4);
        lp.bottomMargin = UiHelper.dp(context, 7);
        subModalBody.addView(tv, lp);
    }

    private void addSpacedView(View v, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(context, bottomDp);
        subModalBody.addView(v, lp);
    }

    private LinearLayout addSettingToggleRow(
            String title,
            String subtitle,
            final BooleanSupplierCompat getter,
            final Runnable onToggle,
            List<Runnable> stateUpdaters) {
        LinearLayout row = new LinearLayout(context);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 10),
                UiHelper.dp(context, 14), UiHelper.dp(context, 10));
        applyPlayerFocusButton(row, context, 14, false, 1.025f);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(13.5f);
        titleTv.setTypeface(null, Typeface.BOLD);
        textCol.addView(titleTv);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
            subTv.setTextSize(11.5f);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = UiHelper.dp(context, 2);
            textCol.addView(subTv, sLp);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView badgeTv = new TextView(context);
        badgeTv.setTextSize(11.5f);
        badgeTv.setTypeface(null, Typeface.BOLD);
        badgeTv.setGravity(Gravity.CENTER);
        badgeTv.setMinWidth(UiHelper.dp(context, 54));
        badgeTv.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 5),
                UiHelper.dp(context, 12), UiHelper.dp(context, 5));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.leftMargin = UiHelper.dp(context, 12);
        row.addView(badgeTv, bLp);

        Runnable updater = new Runnable() {
            @Override
            public void run() {
                boolean on = getter.get();
                badgeTv.setText(on ? "BẬT" : "TẮT");
                badgeTv.setTextColor(on ? Color.WHITE : UiHelper.COLOR_TEXT_MUTED);
                badgeTv.setBackground(UiHelper.createPillDrawable(
                        context, 12,
                        on ? UiHelper.COLOR_GREEN : Color.argb(60, 255, 255, 255),
                        Color.TRANSPARENT, 0f));
            }
        };
        updater.run();
        if (stateUpdaters != null) stateUpdaters.add(updater);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggle.run();
            }
        });
        addSpacedView(row, 8);
        return row;
    }

    private void addInPlaceStepperRow(
            String label,
            final StringSupplierCompat valueSupplier,
            final Runnable onDec,
            final Runnable onInc,
            List<Runnable> stateUpdaters) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(UiHelper.dp(context, 14), UiHelper.dp(context, 8),
                UiHelper.dp(context, 14), UiHelper.dp(context, 8));
        row.setBackground(UiHelper.createPillDrawable(
                context, 14, Color.argb(120, 26, 30, 44), Color.TRANSPARENT, 0f));

        TextView lbl = new TextView(context);
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(13.5f);
        lbl.setTypeface(null, Typeface.BOLD);
        row.addView(lbl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView minus = makeStepperBtn(context, "−");
        minus.setId(View.generateViewId());
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDec.run();
            }
        });
        row.addView(minus, rightMarginLp(8));

        final TextView valTv = new TextView(context);
        valTv.setText(valueSupplier.get());
        valTv.setTextColor(UiHelper.COLOR_CYAN);
        valTv.setTextSize(13.5f);
        valTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valTv.setGravity(Gravity.CENTER);
        valTv.setMinWidth(UiHelper.dp(context, 64));
        row.addView(valTv, rightMarginLp(8));

        TextView plus = makeStepperBtn(context, "+");
        plus.setId(View.generateViewId());
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInc.run();
            }
        });
        row.addView(plus);

        minus.setNextFocusRightId(plus.getId());
        plus.setNextFocusLeftId(minus.getId());

        if (stateUpdaters != null) {
            stateUpdaters.add(new Runnable() {
                @Override
                public void run() {
                    valTv.setText(valueSupplier.get());
                }
            });
        }
        addSpacedView(row, 8);
    }

    private TextView makeStepperBtn(Context ctx, String symbol) {
        TextView tv = new TextView(ctx);
        tv.setText(symbol);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(UiHelper.dp(ctx, 40));
        tv.setMinHeight(UiHelper.dp(ctx, 34));
        tv.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 4));
        applyPlayerFocusButton(tv, ctx, 12, false, 1.08f);
        return tv;
    }

    private TextView makeGridChip(Context ctx, String text, boolean selected) {
        TextView tv = new TextView(ctx);
        tv.setId(View.generateViewId());
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 8));
        applyPlayerFocusButton(tv, ctx, 12, selected, 1.05f);
        return tv;
    }

    private LinearLayout makeColorDotChip(Context ctx, String label, int dotColor, boolean selected) {
        LinearLayout chip = new LinearLayout(ctx);
        chip.setId(View.generateViewId());
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 8), UiHelper.dp(ctx, 10), UiHelper.dp(ctx, 8));
        applyPlayerFocusButton(chip, ctx, 12, selected, 1.05f);

        View dot = new View(ctx);
        dot.setBackground(UiHelper.createPillDrawable(ctx, 6, dotColor, Color.argb(140, 255, 255, 255), 1f));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(UiHelper.dp(ctx, 11), UiHelper.dp(ctx, 11));
        dLp.rightMargin = UiHelper.dp(ctx, 6);
        chip.addView(dot, dLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        chip.addView(tv);
        return chip;
    }

    private void addChipGridRows(List<View> items, int cols, int bottomSpaceDp) {
        if (items == null || items.isEmpty() || cols <= 0) return;
        int total = items.size();
        for (int start = 0; start < total; start += cols) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            int end = Math.min(total, start + cols);
            for (int i = start; i < end; i++) {
                View v = items.get(i);
                if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i < end - 1) lp.rightMargin = UiHelper.dp(context, 8);
                row.addView(v, lp);
            }
            // Wire horizontal D-Pad cleanly inside the row
            for (int i = start; i < end; i++) {
                View cur = items.get(i);
                if (i > start) cur.setNextFocusLeftId(items.get(i - 1).getId());
                if (i < end - 1) cur.setNextFocusRightId(items.get(i + 1).getId());
            }
            int remaining = cols - (end - start);
            for (int r = 0; r < remaining; r++) {
                LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (r < remaining - 1) spLp.rightMargin = UiHelper.dp(context, 8);
                row.addView(new View(context), spLp);
            }
            boolean isLastRow = (end >= total);
            addSpacedView(row, isLastRow ? bottomSpaceDp : 8);
        }
    }

    private void cycleTranslateLanguageInPlace(int step) {
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

    // ---- Unified Crisp White-Border Focus Helpers (Zero Red/Pink Glow) ----

    private android.graphics.drawable.StateListDrawable createPlayerFocusSelector(
            Context ctx, int radiusDp, boolean isActiveAccent) {
        android.graphics.drawable.Drawable normal;
        android.graphics.drawable.Drawable focused;
        int whiteBorder = Color.argb(245, 255, 255, 255);
        int borderPx = Math.max(2, UiHelper.dp(ctx, 2.5f));
        float rPx = UiHelper.dpF(ctx, radiusDp);

        if (isActiveAccent) {
            GradientDrawable n = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#E50914"), Color.parseColor("#C90052")});
            n.setCornerRadius(rPx);
            normal = n;

            GradientDrawable f = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#F01424"), Color.parseColor("#D50055")});
            f.setCornerRadius(rPx);
            f.setStroke(borderPx, whiteBorder);
            focused = f;
        } else {
            normal = UiHelper.createPillDrawable(ctx, radiusDp, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f);
            focused = UiHelper.createPillDrawable(ctx, radiusDp, UiHelper.getGlassFocusedBackgroundColor(), whiteBorder, 2.5f);
        }

        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void applyPlayerFocusButton(
            final View view, final Context ctx, final int radiusDp, boolean isActiveAccent, final float scaleOnFocus) {
        view.setBackground(createPlayerFocusSelector(ctx, radiusDp, isActiveAccent));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), radiusDp));
            }
        });
        view.setClipToOutline(true);
        applyPlayerTvFocusAnimation(view, ctx, scaleOnFocus, 10);
    }

    private void applyPlayerTvFocusAnimation(
            final View view, final Context ctx, final float scaleOnFocus, final int baseShadowDp) {
        if (view == null || ctx == null) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        view.setElevation(UiHelper.dp(ctx, baseShadowDp));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.setOutlineSpotShadowColor(UiHelper.COLOR_GLASS_SHADOW_SPOT);
            view.setOutlineAmbientShadowColor(UiHelper.COLOR_GLASS_SHADOW_AMBIENT);
        }
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyNeutralFocusScale(v, ctx, hasFocus, scaleOnFocus, baseShadowDp);
            }
        });
    }

    private void applyNeutralFocusScale(
            View view, Context ctx, boolean hasFocus, float scaleOnFocus, int baseShadowDp) {
        if (view == null || ctx == null) return;
        float targetScale = hasFocus ? scaleOnFocus : 1.0f;
        float targetZ = hasFocus ? UiHelper.dpF(ctx, 8) : 0f;
        view.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetZ)
                .setDuration(130)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        view.setElevation(hasFocus ? UiHelper.dp(ctx, baseShadowDp + 6) : UiHelper.dp(ctx, baseShadowDp));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.setOutlineSpotShadowColor(UiHelper.COLOR_GLASS_SHADOW_SPOT);
            view.setOutlineAmbientShadowColor(UiHelper.COLOR_GLASS_SHADOW_AMBIENT);
        }
    }

    private TextView makeTextPill(Context ctx, String text, boolean accent) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7));
        applyPlayerFocusButton(tv, ctx, 14, accent, 1.07f);
        return tv;
    }

    private LinearLayout makeIconTextPill(Context ctx, int iconRes, String label) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiHelper.dp(ctx, 12), UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7));
        applyPlayerFocusButton(row, ctx, 14, false, 1.07f);

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

    private FrameLayout makeHudCircleIcon(Context ctx, int iconRes, int sizeDp) {
        FrameLayout box = new FrameLayout(ctx);
        android.graphics.drawable.Drawable normal = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f);
        android.graphics.drawable.Drawable focused = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassFocusedBackgroundColor(), Color.argb(245, 255, 255, 255), 2.5f);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        box.setBackground(states);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), UiHelper.GLASS_RADIUS_DP));
            }
        });
        box.setClipToOutline(true);
        applyPlayerTvFocusAnimation(box, ctx, 1.12f, 14);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        box.addView(iv, new FrameLayout.LayoutParams(UiHelper.dp(ctx, 17), UiHelper.dp(ctx, 17), Gravity.CENTER));
        return box;
    }

    private LinearLayout makeFrostedCapsuleGroup(Context ctx) {
        LinearLayout group = new LinearLayout(ctx);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        group.setPadding(UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 4), UiHelper.dp(ctx, 2));
        UiHelper.applyGlassCardStyle(group, ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.COLOR_SURFACE_GLASS, 20);
        group.setClipToOutline(false);
        group.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiHelper.dp(ctx, 36)));
        return group;
    }

    private FrameLayout makeGroupSlotIcon(Context ctx, int iconRes) {
        FrameLayout slot = new FrameLayout(ctx);
        android.graphics.drawable.Drawable focused = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassFocusedBackgroundColor(), Color.argb(245, 255, 255, 255), 2.5f);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        slot.setBackground(states);
        applyPlayerTvFocusAnimation(slot, ctx, 1.12f, 12);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        slot.addView(iv, new FrameLayout.LayoutParams(UiHelper.dp(ctx, 17), UiHelper.dp(ctx, 17), Gravity.CENTER));
        slot.setLayoutParams(new LinearLayout.LayoutParams(UiHelper.dp(ctx, 34), UiHelper.dp(ctx, 32)));
        return slot;
    }

    private FrameLayout makeClearTransportIcon(Context ctx, int iconRes) {
        FrameLayout box = new FrameLayout(ctx);
        android.graphics.drawable.Drawable normal = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f);
        android.graphics.drawable.Drawable focused = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassFocusedBackgroundColor(), Color.argb(245, 255, 255, 255), 2.5f);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        box.setBackground(states);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), UiHelper.dp(v.getContext(), UiHelper.GLASS_RADIUS_DP));
            }
        });
        box.setClipToOutline(true);
        applyPlayerTvFocusAnimation(box, ctx, 1.12f, 14);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        box.addView(iv, new FrameLayout.LayoutParams(UiHelper.dp(ctx, 32), UiHelper.dp(ctx, 32), Gravity.CENTER));
        return box;
    }

    private android.graphics.drawable.StateListDrawable makeSquircleSelectorDrawable(Context ctx) {
        android.graphics.drawable.Drawable normal = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassBackgroundColor(), Color.TRANSPARENT, 0f);
        android.graphics.drawable.Drawable focused = UiHelper.createPillDrawable(
                ctx, UiHelper.GLASS_RADIUS_DP, UiHelper.getGlassFocusedBackgroundColor(), Color.argb(245, 255, 255, 255), 2.5f);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private LinearLayout.LayoutParams fixedSquareMarginLp(int sizeDp, int rightMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiHelper.dp(context, sizeDp), UiHelper.dp(context, sizeDp));
        lp.rightMargin = UiHelper.dp(context, rightMarginDp);
        return lp;
    }

    private LinearLayout.LayoutParams rightMarginLp(int dpVal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(context, dpVal);
        return lp;
    }

    private void toast(String msg, int color) {
        if (msg == null || msg.trim().isEmpty()) return;
        playerToastMsgTv.setText(msg);
        playerToastIcon.setColorFilter(color != 0 ? color : UiHelper.COLOR_CYAN, PorterDuff.Mode.SRC_IN);
        if (hidePlayerToastRunnable != null) {
            handler.removeCallbacks(hidePlayerToastRunnable);
        }
        playerToastPill.setVisibility(View.VISIBLE);
        playerToastPill.setAlpha(0f);
        playerToastPill.animate().alpha(1f).setDuration(180).start();
        hidePlayerToastRunnable = new Runnable() {
            @Override
            public void run() {
                playerToastPill.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        playerToastPill.setVisibility(View.GONE);
                    }
                }).start();
            }
        };
        handler.postDelayed(hidePlayerToastRunnable, 2200L);
    }

    private static String formatHumanDuration(double durSec) {
        if (durSec < 60.0) return "";
        int totalMin = (int) Math.round(durSec / 60.0);
        int h = totalMin / 60;
        int m = totalMin % 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
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
