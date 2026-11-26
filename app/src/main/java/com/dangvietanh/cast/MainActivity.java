package com.dangvietanh.cast;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;
import com.dangvietanh.cast.player.PlayerScreenController;
import com.dangvietanh.cast.ui.AccountProfileModal;
import com.dangvietanh.cast.ui.ExploreScreenView;
import com.dangvietanh.cast.ui.HomeScreenView;
import com.dangvietanh.cast.ui.LibraryScreenView;
import com.dangvietanh.cast.ui.LoginScreenView;
import com.dangvietanh.cast.ui.MovieDetailScreenView;
import com.dangvietanh.cast.ui.SearchScreenView;
import com.dangvietanh.cast.ui.TvNavigationRail;

import java.io.File;

public class MainActivity extends Activity {

    private static final long SPLASH_MIN_DURATION_MS = 3000L;
    private static final long LONG_PRESS_MENU_MS = 650L;

    private ApiClient apiClient;

    private FrameLayout rootLayout;
    private FrameLayout appContainer;

    // Native Screens
    private LoginScreenView loginScreen;
    private FrameLayout mainShell;
    private FrameLayout contentHost;
    private TvNavigationRail navRail;
    private HomeScreenView homeScreen;
    private SearchScreenView searchScreen;
    private ExploreScreenView exploreScreen;
    private LibraryScreenView libraryScreen;
    private MovieDetailScreenView detailScreen;
    private PlayerScreenController playerScreen;

    // Overlays
    private AccountProfileModal accountModal;
    private OverlayMenuController overlayMenu;
    private UpdateManager updateManager;

    // Startup Splash UX (Preserved)
    private FrameLayout splashOverlay;
    private LinearLayout splashContent;
    private UiHelper.SpinnerView splashSpinner;
    private TextView splashStatusTv;
    private TextView splashRetryBtn;
    private boolean splashMinDone = false;
    private boolean bootReady = false;
    private boolean bootHasError = false;
    private boolean splashDismissed = false;

    // Media & Network
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private MediaSession mediaSession;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBackPressMs = 0L;
    private boolean longPressTriggered = false;

    private final AudioManager.OnAudioFocusChangeListener audioFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (playerScreen != null && playerScreen.isShowing() && playerScreen.isPlaying()) {
                            playerScreen.pause();
                        }
                    }
                }
            };

    private final Runnable splashMinTimerRunnable = new Runnable() {
        @Override
        public void run() {
            splashMinDone = true;
            tryDismissSplash();
        }
    };

    private final Runnable longBackMenuRunnable = new Runnable() {
        @Override
        public void run() {
            longPressTriggered = true;
            if (overlayMenu != null && !overlayMenu.isShowing()
                    && (updateManager == null || !updateManager.isShowing())
                    && (playerScreen == null || !playerScreen.isShowing())) {
                overlayMenu.show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        apiClient = new ApiClient(this);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackground(UiHelper.createCastBackgroundDrawable());
        setContentView(rootLayout);

        // 1. Native App Layer (Passed to glass blur overlays)
        appContainer = new FrameLayout(this);
        rootLayout.addView(appContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildAppScreens();

        // 2. Account Profile Modal
        accountModal = new AccountProfileModal(this, appContainer, new AccountProfileModal.Actions() {
            @Override
            public void onOpenTvSettings() {
                if (overlayMenu != null) overlayMenu.show();
            }

            @Override
            public void onCheckUpdates() {
                if (updateManager != null) updateManager.checkForUpdates(true);
            }

            @Override
            public void onLogout() {
                performLogout();
            }
        });
        rootLayout.addView(accountModal.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. Settings & Version History Overlay Layer
        overlayMenu = new OverlayMenuController(this, appContainer, new OverlayMenuController.Actions() {
            @Override
            public void onRefreshHome() {
                refreshActiveScreen();
            }

            @Override
            public void onClearCache() {
                clearAppCaches();
            }

            @Override
            public void onCheckUpdates() {
                if (updateManager != null) updateManager.checkForUpdates(true);
            }

            @Override
            public void onLogout() {
                performLogout();
            }

            @Override
            public boolean isLoggedIn() {
                return !apiClient.getAuthToken().isEmpty();
            }

            @Override
            public String getCurrentUserLabel() {
                Models.User u = apiClient.getCachedUser();
                if (u == null) return "";
                if (u.name != null && !u.name.isEmpty()) return u.name;
                return u.username != null ? u.username : "";
            }

            @Override
            public void onFetchHistory(Runnable onComplete) {
                if (updateManager != null) {
                    updateManager.fetchHistoryAsync(onComplete);
                } else if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public String getVersionName() {
                return updateManager != null ? updateManager.getLocalVersionName() : "4.0";
            }

            @Override
            public long getVersionCode() {
                return updateManager != null ? updateManager.getLocalVersionCode() : 9L;
            }
        });
        rootLayout.addView(overlayMenu.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. Update Overlay Layer
        updateManager = new UpdateManager(this);
        rootLayout.addView(updateManager.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 5. Startup Splash Layer (Topmost during initial boot)
        buildSplashOverlay();
        rootLayout.addView(splashOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Runnable onOverlayVisibilityChanged = new Runnable() {
            @Override
            public void run() {
                syncFocusLayers();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!hasValidTopFocus()) {
                            ensureActiveScreenFocus();
                        }
                    }
                });
            }
        };
        accountModal.setOnVisibilityChangedListener(onOverlayVisibilityChanged);
        overlayMenu.setOnVisibilityChangedListener(onOverlayVisibilityChanged);
        updateManager.setOnVisibilityChangedListener(onOverlayVisibilityChanged);

        applyImmersiveFullscreen();
        initMediaOptimization();
        registerNetworkMonitor();

        startSplashEntrance();
        handler.postDelayed(splashMinTimerRunnable, SPLASH_MIN_DURATION_MS);

        startBootFlow();
        syncFocusLayers();
    }

    private void buildAppScreens() {
        // Login Screen
        loginScreen = new LoginScreenView(this, apiClient, new LoginScreenView.Listener() {
            @Override
            public void onLoginSuccess(Models.User user, String token) {
                enterMainShell(user);
            }

            @Override
            public void onOpenSettingsMenu() {
                if (overlayMenu != null) overlayMenu.show();
            }
        });
        loginScreen.setVisibility(View.GONE);
        appContainer.addView(loginScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Main Netflix/Apple TV Shell (Content + Left Floating Rail)
        mainShell = new FrameLayout(this);
        mainShell.setVisibility(View.GONE);
        mainShell.setClipChildren(false);
        appContainer.addView(mainShell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentHost = new FrameLayout(this);
        contentHost.setClipChildren(false);
        FrameLayout.LayoutParams chLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        chLp.leftMargin = UiHelper.dp(this, TvNavigationRail.COLLAPSED_WIDTH_DP);
        mainShell.addView(contentHost, chLp);

        homeScreen = new HomeScreenView(this, apiClient, new HomeScreenView.Listener() {
            @Override
            public void onOpenMovieDetail(Models.MovieItem item) {
                openMovieDetail(item);
            }

            @Override
            public void onPlayMovie(Models.MovieItem item) {
                launchPlayerFromMovieItem(item);
            }

            @Override
            public void onShowToast(String message, int accentColor) {
                UiHelper.showGlassToast(rootLayout, MainActivity.this, message, accentColor);
            }

            @Override
            public void onOpenSearch() {
                switchMainTab("search");
            }

            @Override
            public void onOpenLibrary() {
                switchMainTab("library");
            }

            @Override
            public void onOpenSettings() {
                if (overlayMenu != null) overlayMenu.show();
            }

            @Override
            public void onOpenAccount() {
                if (accountModal != null) accountModal.show(apiClient.getCachedUser());
            }

            @Override
            public void onOpenExploreCategory(String sectionTitle) {
                switchMainTab("explore");
            }

            @Override
            public void onMoveFocusToRail() {
                if (navRail != null) {
                    navRail.focusActiveTab();
                }
            }
        });
        contentHost.addView(homeScreen);

        searchScreen = new SearchScreenView(this, apiClient, new SearchScreenView.Listener() {
            @Override
            public void onOpenMovieDetail(Models.MovieItem item) {
                openMovieDetail(item);
            }
        });
        searchScreen.setVisibility(View.GONE);
        contentHost.addView(searchScreen);

        exploreScreen = new ExploreScreenView(this, apiClient, new ExploreScreenView.Listener() {
            @Override
            public void onOpenMovieDetail(Models.MovieItem item) {
                openMovieDetail(item);
            }
        });
        exploreScreen.setVisibility(View.GONE);
        contentHost.addView(exploreScreen);

        libraryScreen = new LibraryScreenView(this, apiClient, new LibraryScreenView.Listener() {
            @Override
            public void onOpenMovieDetail(Models.MovieItem item) {
                openMovieDetail(item);
            }

            @Override
            public void onShowToast(String msg, int accentColor) {
                UiHelper.showGlassToast(rootLayout, MainActivity.this, msg, accentColor);
            }
        });
        libraryScreen.setVisibility(View.GONE);
        contentHost.addView(libraryScreen);

        // Movie Detail Screen Overlay (inside mainShell below navRail so rail is shared seamlessly)
        detailScreen = new MovieDetailScreenView(this, apiClient, new MovieDetailScreenView.Listener() {
            @Override
            public void onCloseDetail() {
                detailScreen.clearHistory();
                detailScreen.setVisibility(View.GONE);
                syncFocusLayers();
                focusCurrentMainTab();
            }

            @Override
            public void onLaunchPlayer(Models.PlayerLaunchRequest request) {
                playerScreen.open(request);
                syncFocusLayers();
            }

            @Override
            public void onShowToast(String message, int accentColor) {
                UiHelper.showGlassToast(rootLayout, MainActivity.this, message, accentColor);
            }

            @Override
            public void onNavigateTab(String tabId) {
                detailScreen.clearHistory();
                detailScreen.setVisibility(View.GONE);
                switchMainTab(tabId);
            }

            @Override
            public void onOpenSettings() {
                if (overlayMenu != null) overlayMenu.show();
            }
        });
        detailScreen.setVisibility(View.GONE);
        FrameLayout.LayoutParams detailLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        detailLp.leftMargin = UiHelper.dp(this, TvNavigationRail.COLLAPSED_WIDTH_DP);
        mainShell.addView(detailScreen, detailLp);

        // Shared Collapsible Left Rail (always above contentHost and detailScreen)
        navRail = new TvNavigationRail(this, new TvNavigationRail.Listener() {
            @Override
            public void onSelectTab(String tabId) {
                switchMainTab(tabId);
            }

            @Override
            public void onOpenAccountProfile() {
                if (accountModal != null) {
                    accountModal.show(apiClient.getCachedUser());
                }
            }

            @Override
            public void onOpenSettingsMenu() {
                if (overlayMenu != null) {
                    overlayMenu.show();
                }
            }

            @Override
            public void onMoveFocusToContent() {
                focusCurrentMainTab();
            }
        });
        navRail.setElevation(UiHelper.dp(this, 36));
        mainShell.addView(navRail);

        // Native Player Screen Overlay
        playerScreen = new PlayerScreenController(this, apiClient, new PlayerScreenController.Listener() {
            @Override
            public void onPlayerClosed(boolean shouldRefreshHome) {
                syncFocusLayers();
                if (homeScreen != null && shouldRefreshHome) {
                    homeScreen.silentRefresh();
                }
                if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
                    libraryScreen.refresh();
                }
                if (detailScreen != null && detailScreen.getVisibility() == View.VISIBLE) {
                    UiHelper.requestTvFocus(detailScreen);
                } else {
                    focusCurrentMainTab();
                }
                updateMediaPlaybackState(PlaybackState.STATE_PAUSED, 0L);
            }

            @Override
            public void onAudioFocusRequested(boolean active) {
                if (active) {
                    requestAppAudioFocus();
                    updateMediaPlaybackState(PlaybackState.STATE_PLAYING, 0L);
                } else {
                    abandonAppAudioFocus();
                    updateMediaPlaybackState(PlaybackState.STATE_STOPPED, 0L);
                }
            }

            @Override
            public void onShowToast(String message, int accentColor) {
                UiHelper.showGlassToast(rootLayout, MainActivity.this, message, accentColor);
            }
        });
        appContainer.addView(playerScreen.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void startBootFlow() {
        bootHasError = false;
        if (splashStatusTv != null) {
            splashStatusTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
            splashStatusTv.setText("Đang kết nối...");
        }
        if (splashSpinner != null) {
            splashSpinner.setVisibility(View.VISIBLE);
            splashSpinner.setSpinning(true);
        }
        if (splashRetryBtn != null) {
            splashRetryBtn.setVisibility(View.GONE);
        }

        String token = apiClient.getAuthToken();
        if (token != null && !token.isEmpty()) {
            apiClient.checkAuthStatus(new ApiClient.Callback<Models.User>() {
                @Override
                public void onSuccess(Models.User user) {
                    if (user != null && user.userId > 0) {
                        apiClient.saveUser(user);
                        enterMainShell(user);
                    } else {
                        apiClient.clearSession();
                        showLoginScreen();
                    }
                    bootReady = true;
                    tryDismissSplash();
                }

                @Override
                public void onError(String message, int httpCode) {
                    Models.User cached = apiClient.getCachedUser();
                    if (httpCode == 0 && cached != null && cached.userId > 0) {
                        enterMainShell(cached);
                        bootReady = true;
                        tryDismissSplash();
                    } else if (httpCode >= 400) {
                        apiClient.clearSession();
                        showLoginScreen();
                        bootReady = true;
                        tryDismissSplash();
                    } else {
                        onBootNetworkFailure();
                    }
                }
            });
            return;
        }

        apiClient.pingTvAuth(new ApiClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean ok) {
                showLoginScreen();
                bootReady = true;
                tryDismissSplash();
            }

            @Override
            public void onError(String message, int httpCode) {
                onBootNetworkFailure();
            }
        });
    }

    private void onBootNetworkFailure() {
        bootHasError = true;
        if (splashSpinner != null) {
            splashSpinner.setSpinning(false);
            splashSpinner.setVisibility(View.GONE);
        }
        if (splashStatusTv != null) {
            splashStatusTv.setTextColor(Color.parseColor("#FF7A85"));
            splashStatusTv.setText("Chưa có kết nối mạng. Vui lòng kiểm tra Wi-Fi");
        }
        if (splashRetryBtn != null) {
            splashRetryBtn.setVisibility(View.VISIBLE);
            UiHelper.postTvFocus(splashRetryBtn);
        }
    }

    private void showLoginScreen() {
        homeScreen.stopAutoSlide();
        detailScreen.setVisibility(View.GONE);
        mainShell.setVisibility(View.GONE);
        loginScreen.setVisibility(View.VISIBLE);
        syncFocusLayers();
        loginScreen.start();
    }

    private void enterMainShell(Models.User user) {
        if (user != null) apiClient.saveUser(user);
        loginScreen.stop();
        loginScreen.setVisibility(View.GONE);
        detailScreen.setVisibility(View.GONE);
        mainShell.setVisibility(View.VISIBLE);
        Models.User effUser = user != null ? user : apiClient.getCachedUser();
        navRail.setUser(effUser);
        if (homeScreen != null) homeScreen.setUser(effUser);
        switchMainTab("home");
    }

    private void performLogout() {
        apiClient.logout(new Runnable() {
            @Override
            public void run() {
                if (playerScreen != null && playerScreen.isShowing()) {
                    playerScreen.close();
                }
                showLoginScreen();
                UiHelper.showGlassToast(rootLayout, MainActivity.this, "Đã đăng xuất tài khoản", UiHelper.COLOR_TEXT_MUTED);
            }
        });
    }

    private void switchMainTab(String tabId) {
        navRail.setActiveTab(tabId);
        detailScreen.clearHistory();
        detailScreen.setVisibility(View.GONE);

        homeScreen.setVisibility(View.GONE);
        homeScreen.stopAutoSlide();
        searchScreen.setVisibility(View.GONE);
        exploreScreen.setVisibility(View.GONE);
        libraryScreen.setVisibility(View.GONE);

        if ("search".equals(tabId)) {
            searchScreen.setVisibility(View.VISIBLE);
            syncFocusLayers();
            searchScreen.onOpened();
        } else if ("explore".equals(tabId)) {
            exploreScreen.setVisibility(View.VISIBLE);
            syncFocusLayers();
            exploreScreen.onOpened();
        } else if ("library".equals(tabId)) {
            libraryScreen.setVisibility(View.VISIBLE);
            syncFocusLayers();
            libraryScreen.refresh();
            libraryScreen.requestInitialFocus();
        } else {
            homeScreen.setVisibility(View.VISIBLE);
            syncFocusLayers();
            homeScreen.loadHome(tabId, "", false);
            homeScreen.requestInitialFocus();
        }
    }

    private void refreshActiveScreen() {
        if (loginScreen.getVisibility() == View.VISIBLE) {
            loginScreen.start();
            return;
        }
        String tab = navRail != null ? navRail.getActiveTabId() : "home";
        if ("library".equals(tab)) libraryScreen.refresh();
        else if ("explore".equals(tab)) exploreScreen.onOpened();
        else if ("search".equals(tab)) searchScreen.onOpened();
        else homeScreen.loadHome(tab, "", false);
    }

    private void focusCurrentMainTab() {
        if (detailScreen != null && detailScreen.getVisibility() == View.VISIBLE) {
            UiHelper.requestTvFocus(detailScreen);
        } else if (searchScreen.getVisibility() == View.VISIBLE) {
            UiHelper.requestTvFocus(searchScreen);
        } else if (exploreScreen.getVisibility() == View.VISIBLE) {
            UiHelper.requestTvFocus(exploreScreen);
        } else if (libraryScreen.getVisibility() == View.VISIBLE) {
            UiHelper.requestTvFocus(libraryScreen);
        } else if (homeScreen.getVisibility() == View.VISIBLE) {
            homeScreen.requestInitialFocus();
        }
    }

    private void openMovieDetail(Models.MovieItem item) {
        if (item == null || item.videosId <= 0) return;
        homeScreen.stopAutoSlide();
        detailScreen.setVisibility(View.VISIBLE);
        syncFocusLayers();
        detailScreen.openMovie(item.videosId, false);
    }

    private void launchPlayerFromMovieItem(Models.MovieItem item) {
        if (item == null) return;
        Models.PlayerLaunchRequest req = new Models.PlayerLaunchRequest();
        req.videosId = item.videosId;
        req.streamCode = !item.streamCode.isEmpty() ? item.streamCode : String.valueOf(item.videosId);
        req.title = item.getDisplayTitle();
        req.posterUrl = item.getLandscapeUrl();
        req.thumbnailUrl = item.getPortraitUrl();
        req.initialResumeSec = item.resumeSeconds > 5 ? item.resumeSeconds : -1;
        playerScreen.open(req);
        syncFocusLayers();
    }

    private void clearAppCaches() {
        try {
            Glide.get(this).clearMemory();
        } catch (Exception ignored) {
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Glide.get(getApplicationContext()).clearDiskCache();
                } catch (Exception ignored) {
                }
                File ttsDir = new File(getCacheDir(), "dva_tts");
                if (ttsDir.exists() && ttsDir.isDirectory()) {
                    File[] files = ttsDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                }
            }
        }).start();
        refreshActiveScreen();
        UiHelper.showGlassToast(rootLayout, this, "Đã dọn bộ nhớ đệm hình ảnh & phụ đề", UiHelper.COLOR_GREEN);
    }

    // ---- Preserved Startup Splash Overlay ----

    private void buildSplashOverlay() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackground(UiHelper.createCastBackgroundDrawable());
        splashOverlay.setClickable(true);
        UiHelper.makeNonFocusable(splashOverlay);

        splashContent = new LinearLayout(this);
        splashContent.setOrientation(LinearLayout.VERTICAL);
        splashContent.setGravity(Gravity.CENTER_HORIZONTAL);
        splashContent.setMinimumWidth(UiHelper.dp(this, 360));
        splashContent.setPadding(UiHelper.dp(this, 32), UiHelper.dp(this, 30),
                UiHelper.dp(this, 32), UiHelper.dp(this, 30));
        UiHelper.applyGlassCardStyle(splashContent, this, 40, Color.argb(102, 0, 0, 0), 24);
        splashContent.setAlpha(0f);
        splashContent.setScaleX(0.95f);
        splashContent.setScaleY(0.95f);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        contentLp.setMargins(UiHelper.dp(this, 24), UiHelper.dp(this, 24),
                UiHelper.dp(this, 24), UiHelper.dp(this, 24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.splash_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                UiHelper.dp(this, 108), UiHelper.dp(this, 108));
        logoLp.bottomMargin = UiHelper.dp(this, 16);
        splashContent.addView(logo, logoLp);

        TextView appTitle = new TextView(this);
        appTitle.setText("Đặng Việt Anh");
        appTitle.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        appTitle.setTextSize(26);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appTitle.setLetterSpacing(0.02f);
        appTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = UiHelper.dp(this, 22);
        splashContent.addView(appTitle, titleLp);

        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setMinimumWidth(UiHelper.dp(this, 240));
        statusPill.setPadding(UiHelper.dp(this, 24), UiHelper.dp(this, 12),
                UiHelper.dp(this, 26), UiHelper.dp(this, 12));
        UiHelper.applyGlassCardStyle(statusPill, this, 24, UiHelper.COLOR_SURFACE_GLASS, 12);

        splashSpinner = new UiHelper.SpinnerView(this);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                UiHelper.dp(this, 26), UiHelper.dp(this, 26));
        spinLp.rightMargin = UiHelper.dp(this, 12);
        statusPill.addView(splashSpinner, spinLp);

        splashStatusTv = new TextView(this);
        splashStatusTv.setText("Đang kết nối...");
        splashStatusTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        splashStatusTv.setTextSize(15);
        splashStatusTv.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.addView(splashStatusTv);
        splashContent.addView(statusPill);

        splashRetryBtn = new TextView(this);
        splashRetryBtn.setText("Thử lại");
        splashRetryBtn.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        splashRetryBtn.setTextSize(15);
        splashRetryBtn.setTypeface(Typeface.DEFAULT_BOLD);
        splashRetryBtn.setPadding(UiHelper.dp(this, 26), UiHelper.dp(this, 12),
                UiHelper.dp(this, 26), UiHelper.dp(this, 12));
        UiHelper.applyFocusableTileStyle(splashRetryBtn, this, 24,
                UiHelper.COLOR_SURFACE_GLASS, UiHelper.COLOR_BTN_FOCUSED);
        splashRetryBtn.setVisibility(View.GONE);
        splashRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBootFlow();
            }
        });
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryLp.topMargin = UiHelper.dp(this, 16);
        splashContent.addView(splashRetryBtn, retryLp);

        splashOverlay.addView(splashContent, contentLp);
    }

    private void startSplashEntrance() {
        splashContent.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(480)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void tryDismissSplash() {
        if (splashDismissed || !splashMinDone || !bootReady || bootHasError) return;
        splashDismissed = true;
        splashOverlay.animate()
                .alpha(0f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(420)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        splashSpinner.setSpinning(false);
                        splashOverlay.setVisibility(View.GONE);
                        syncFocusLayers();
                        if (loginScreen.getVisibility() == View.VISIBLE) {
                            loginScreen.requestInitialFocus();
                        } else if (mainShell.getVisibility() == View.VISIBLE) {
                            homeScreen.requestInitialFocus();
                        }
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (updateManager != null) {
                                    updateManager.checkForUpdates(false);
                                }
                            }
                        }, 3500L);
                    }
                })
                .start();
    }

    // ---- Network, Audio & MediaSession ----

    private void registerNetworkMonitor() {
        try {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (bootHasError && !splashDismissed) {
                                startBootFlow();
                            }
                        }
                    });
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) {
        }
    }

    private void initMediaOptimization() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build();
        }

        try {
            mediaSession = new MediaSession(this, "DVACastTVSession");
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    if (playerScreen != null && playerScreen.isShowing()) playerScreen.play();
                }

                @Override
                public void onPause() {
                    if (playerScreen != null && playerScreen.isShowing()) playerScreen.pause();
                }

                @Override
                public void onStop() {
                    if (playerScreen != null && playerScreen.isShowing()) playerScreen.close();
                }

                @Override
                public void onSkipToNext() {
                    if (playerScreen != null && playerScreen.isShowing()) playerScreen.playNextEpisodeIfAvailable();
                }

                @Override
                public void onFastForward() {
                    if (playerScreen != null && playerScreen.isShowing()) playerScreen.seekBySec(10);
                }

                @Override
                public void onRewind() {
                    if (playerScreen != null && playerScreen.isShowing()) playerScreen.seekBySec(-10);
                }
            });
            updateMediaPlaybackState(PlaybackState.STATE_PAUSED, 0L);
            mediaSession.setActive(true);
        } catch (Exception ignored) {
        }
    }

    private void updateMediaPlaybackState(int state, long positionMs) {
        if (mediaSession == null) return;
        try {
            PlaybackState ps = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY
                            | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE
                            | PlaybackState.ACTION_STOP
                            | PlaybackState.ACTION_FAST_FORWARD
                            | PlaybackState.ACTION_REWIND
                            | PlaybackState.ACTION_SKIP_TO_NEXT)
                    .setState(state, positionMs, 1.0f)
                    .build();
            mediaSession.setPlaybackState(ps);

            if (playerScreen != null && playerScreen.isShowing()) {
                MediaMetadata meta = new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, playerScreen.getCurrentTitle())
                        .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, playerScreen.getCurrentSubtitle())
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, playerScreen.getDurationMs())
                        .build();
                mediaSession.setMetadata(meta);
            }
        } catch (Exception ignored) {
        }
    }

    private void requestAppAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(audioFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAppAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusListener);
        }
    }

    @SuppressWarnings("deprecation")
    private void applyImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullscreen();
            if (splashDismissed) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!hasValidTopFocus()) {
                            ensureActiveScreenFocus();
                        }
                    }
                });
            }
        }
    }

    private void syncFocusLayers() {
        boolean splashActive = !splashDismissed && splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE;
        boolean updateActive = updateManager != null && updateManager.isShowing();
        boolean menuActive = overlayMenu != null && overlayMenu.isShowing();
        boolean accountActive = accountModal != null && accountModal.isShowing();
        boolean playerActive = playerScreen != null && playerScreen.isShowing();
        boolean detailActive = detailScreen != null && detailScreen.getVisibility() == View.VISIBLE;

        boolean anyTopOverlay = splashActive || updateActive || menuActive || accountActive;

        UiHelper.setFocusBlocked(appContainer, anyTopOverlay);
        if (accountModal != null && accountModal.getView() instanceof ViewGroup) {
            UiHelper.setFocusBlocked((ViewGroup) accountModal.getView(),
                    splashActive || updateActive || menuActive || !accountActive);
        }
        if (overlayMenu != null && overlayMenu.getView() instanceof ViewGroup) {
            UiHelper.setFocusBlocked((ViewGroup) overlayMenu.getView(),
                    splashActive || updateActive || !menuActive);
        }
        if (updateManager != null && updateManager.getView() instanceof ViewGroup) {
            UiHelper.setFocusBlocked((ViewGroup) updateManager.getView(),
                    splashActive || !updateActive);
        }
        if (loginScreen != null) {
            UiHelper.setFocusBlocked(loginScreen, anyTopOverlay || loginScreen.getVisibility() != View.VISIBLE);
        }
        if (mainShell != null) {
            UiHelper.setFocusBlocked(mainShell, anyTopOverlay || playerActive || mainShell.getVisibility() != View.VISIBLE);
        }
        if (contentHost != null) {
            UiHelper.setFocusBlocked(contentHost,
                    anyTopOverlay || playerActive || detailActive || mainShell.getVisibility() != View.VISIBLE);
        }
        if (detailScreen != null) {
            UiHelper.setFocusBlocked(detailScreen, anyTopOverlay || playerActive || !detailActive);
        }
        if (navRail != null) {
            UiHelper.setFocusBlocked(navRail, anyTopOverlay || playerActive || mainShell.getVisibility() != View.VISIBLE);
        }
        if (playerScreen != null && playerScreen.getView() instanceof ViewGroup) {
            UiHelper.setFocusBlocked((ViewGroup) playerScreen.getView(), anyTopOverlay || !playerActive);
        }
    }

    private boolean hasValidTopFocus() {
        View cur = getCurrentFocus();
        if (!splashDismissed && splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE) {
            return UiHelper.isValidInteractiveFocus(cur, splashOverlay);
        }
        if (updateManager != null && updateManager.isShowing()) {
            return updateManager.hasValidFocus();
        }
        if (overlayMenu != null && overlayMenu.isShowing()) {
            return overlayMenu.hasValidFocus();
        }
        if (accountModal != null && accountModal.isShowing()) {
            return accountModal.hasValidFocus();
        }
        if (playerScreen != null && playerScreen.isShowing()) {
            return UiHelper.isValidInteractiveFocus(cur, playerScreen.getView());
        }
        if (loginScreen != null && loginScreen.getVisibility() == View.VISIBLE) {
            return loginScreen.hasValidFocus();
        }
        if (mainShell != null && mainShell.getVisibility() == View.VISIBLE) {
            if (navRail != null && UiHelper.isValidInteractiveFocus(cur, navRail)) {
                return true;
            }
            if (detailScreen != null && detailScreen.getVisibility() == View.VISIBLE) {
                return detailScreen.hasValidFocus();
            }
            if (searchScreen != null && searchScreen.getVisibility() == View.VISIBLE) {
                return searchScreen.hasValidFocus();
            }
            if (exploreScreen != null && exploreScreen.getVisibility() == View.VISIBLE) {
                return exploreScreen.hasValidFocus();
            }
            if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
                return libraryScreen.hasValidFocus();
            }
            if (homeScreen != null && homeScreen.getVisibility() == View.VISIBLE) {
                return homeScreen.hasValidFocus();
            }
        }
        return false;
    }

    private boolean ensureActiveScreenFocus() {
        if (!splashDismissed && splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE) {
            if (splashRetryBtn != null && splashRetryBtn.getVisibility() == View.VISIBLE) {
                return UiHelper.requestTvFocus(splashRetryBtn);
            }
            return false;
        }
        if (updateManager != null && updateManager.isShowing()) {
            return updateManager.ensureValidFocus();
        }
        if (overlayMenu != null && overlayMenu.isShowing()) {
            return overlayMenu.ensureValidFocus();
        }
        if (accountModal != null && accountModal.isShowing()) {
            return accountModal.ensureValidFocus();
        }
        if (playerScreen != null && playerScreen.isShowing()) {
            return playerScreen.ensureValidFocus();
        }
        if (loginScreen != null && loginScreen.getVisibility() == View.VISIBLE) {
            return loginScreen.ensureValidFocus();
        }
        if (mainShell != null && mainShell.getVisibility() == View.VISIBLE) {
            if (detailScreen != null && detailScreen.getVisibility() == View.VISIBLE) {
                return detailScreen.ensureValidFocus();
            }
            if (searchScreen != null && searchScreen.getVisibility() == View.VISIBLE) {
                return searchScreen.ensureValidFocus();
            }
            if (exploreScreen != null && exploreScreen.getVisibility() == View.VISIBLE) {
                return exploreScreen.ensureValidFocus();
            }
            if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
                return libraryScreen.ensureValidFocus();
            }
            if (homeScreen != null && homeScreen.getVisibility() == View.VISIBLE) {
                return homeScreen.ensureValidFocus();
            }
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

    // ---- Global TV Remote Key Handling ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (updateManager != null && updateManager.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (updateManager.handleKeyDown(keyCode, event)) {
                    return true;
                }
                if (isDpadNavOrSelectKey(keyCode) && !updateManager.hasValidFocus()) {
                    updateManager.ensureValidFocus();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (overlayMenu != null && overlayMenu.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (overlayMenu.handleKeyDown(keyCode, event)) {
                    return true;
                }
                if (isDpadNavOrSelectKey(keyCode) && !overlayMenu.hasValidFocus()) {
                    overlayMenu.ensureValidFocus();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (accountModal != null && accountModal.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    accountModal.hide();
                    return true;
                }
                if (isDpadNavOrSelectKey(keyCode) && !accountModal.hasValidFocus()) {
                    accountModal.ensureValidFocus();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (playerScreen != null && playerScreen.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN && playerScreen.handleKeyDown(keyCode, event)) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_MENU && action == KeyEvent.ACTION_DOWN) {
            if (overlayMenu != null) overlayMenu.show();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    longPressTriggered = false;
                    handler.removeCallbacks(longBackMenuRunnable);
                    handler.postDelayed(longBackMenuRunnable, LONG_PRESS_MENU_MS);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longBackMenuRunnable);
                if (!longPressTriggered) {
                    handleShortBack();
                }
                return true;
            }
        }

        if (action == KeyEvent.ACTION_DOWN && isDpadNavOrSelectKey(keyCode) && !hasValidTopFocus()) {
            if (ensureActiveScreenFocus()) {
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void handleShortBack() {
        if (detailScreen != null && detailScreen.getVisibility() == View.VISIBLE) {
            if (!detailScreen.handleBack()) {
                detailScreen.clearHistory();
                detailScreen.setVisibility(View.GONE);
                syncFocusLayers();
                focusCurrentMainTab();
            }
            return;
        }

        if (mainShell != null && mainShell.getVisibility() == View.VISIBLE
                && navRail != null && !"home".equals(navRail.getActiveTabId())) {
            switchMainTab("home");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 2000L) {
            finish();
        } else {
            lastBackPressMs = now;
            UiHelper.showGlassToast(rootLayout, this, "Nhấn Quay lại lần nữa để thoát ứng dụng", UiHelper.COLOR_TEXT_MUTED);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveFullscreen();
        if (updateManager != null) {
            updateManager.onHostResume();
        }
        if (mainShell != null && mainShell.getVisibility() == View.VISIBLE
                && homeScreen != null && homeScreen.getVisibility() == View.VISIBLE) {
            homeScreen.startAutoSlide();
        }
    }

    @Override
    protected void onPause() {
        if (playerScreen != null) {
            playerScreen.onHostPause();
        }
        if (homeScreen != null) {
            homeScreen.stopAutoSlide();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
        abandonAppAudioFocus();
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {
            }
        }
        if (loginScreen != null) loginScreen.stop();
        if (homeScreen != null) homeScreen.stopAutoSlide();
        if (playerScreen != null) playerScreen.release();
        super.onDestroy();
    }
}
