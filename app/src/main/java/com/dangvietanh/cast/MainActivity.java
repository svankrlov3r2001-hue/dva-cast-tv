package com.dangvietanh.cast;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String CAST_URL = "https://dangvietanh.com/cast/screen";
    private static final long SPLASH_MIN_DURATION_MS = 3000L;
    private static final long LONG_PRESS_MENU_MS = 650L;

    private FrameLayout rootLayout;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private WebChromeClient chromeClient;

    // Splash UX
    private FrameLayout splashOverlay;
    private LinearLayout splashContent;
    private UiHelper.SpinnerView splashSpinner;
    private TextView splashStatusTv;
    private TextView splashRetryBtn;
    private boolean splashMinDone = false;
    private boolean pageLoadDone = false;
    private boolean pageHasError = false;
    private boolean splashDismissed = false;

    // Controllers
    private OverlayMenuController overlayMenu;
    private UpdateManager updateManager;

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
                        evalVideoJs("var v=document.getElementById('dva-video');if(v&&!v.paused)v.pause();");
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

    private final Runnable autoRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (webView != null && pageHasError) {
                reloadCastPage();
            }
        }
    };

    private final Runnable longPressMenuRunnable = new Runnable() {
        @Override
        public void run() {
            if (!splashDismissed || updateManager.isShowing() || overlayMenu.isShowing()) return;
            longPressTriggered = true;
            overlayMenu.show();
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        applyImmersiveFullscreen();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(UiHelper.COLOR_BG_BASE);

        updateManager = new UpdateManager(this);

        // 1. WebView Layer
        webView = new WebView(this);
        webView.setBackgroundColor(UiHelper.COLOR_BG_BASE);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " DVACastTV/" + updateManager.getLocalVersionName());

        webView.addJavascriptInterface(new NativeBridge(), "DVACastNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageHasError = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!pageHasError) {
                    pageLoadDone = true;
                    tryDismissSplash();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    pageHasError = true;
                    onMainFrameLoadFailed();
                }
            }
        });

        chromeClient = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                rootLayout.addView(customView, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                applyImmersiveFullscreen();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                rootLayout.removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                applyImmersiveFullscreen();
            }
        };
        webView.setWebChromeClient(chromeClient);

        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Overlay Menu Layer
        overlayMenu = new OverlayMenuController(this, webView, new OverlayMenuController.Actions() {
            @Override
            public void onReloadPage() {
                Toast.makeText(MainActivity.this, "Đang tải lại trang trình chiếu...", Toast.LENGTH_SHORT).show();
                reloadCastPage();
            }

            @Override
            public void onClearCache() {
                webView.clearCache(true);
                webView.clearHistory();
                WebStorage.getInstance().deleteAllData();
                CookieManager.getInstance().removeAllCookies(null);
                Toast.makeText(MainActivity.this, "Đã xóa bộ nhớ đệm. Đang tải lại...", Toast.LENGTH_SHORT).show();
                reloadCastPage();
            }

            @Override
            public void onCheckUpdates() {
                updateManager.checkForUpdates(true);
            }

            @Override
            public String getVersionName() {
                return updateManager.getLocalVersionName();
            }
        });
        rootLayout.addView(overlayMenu.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. Splash UX Layer
        buildSplashOverlay();
        rootLayout.addView(splashOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. OTA Auto Update Overlay Layer (topmost)
        rootLayout.addView(updateManager.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootLayout);

        initMediaOptimization();
        registerNetworkMonitor();

        startSplashEntrance();
        handler.postDelayed(splashMinTimerRunnable, SPLASH_MIN_DURATION_MS);
        webView.loadUrl(CAST_URL);
    }

    private void buildSplashOverlay() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackground(UiHelper.createCastBackgroundDrawable());
        splashOverlay.setClickable(true);

        splashContent = new LinearLayout(this);
        splashContent.setOrientation(LinearLayout.VERTICAL);
        splashContent.setGravity(Gravity.CENTER_HORIZONTAL);
        splashContent.setPadding(UiHelper.dp(this, 40), UiHelper.dp(this, 36),
                UiHelper.dp(this, 40), UiHelper.dp(this, 36));
        UiHelper.applyGlassCardStyle(splashContent, this, 40, UiHelper.COLOR_SURFACE_GLASS, 24);

        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        contentLp.setMargins(UiHelper.dp(this, 24), UiHelper.dp(this, 24),
                UiHelper.dp(this, 24), UiHelper.dp(this, 24));

        ImageView logoIv = new ImageView(this);
        logoIv.setImageResource(R.drawable.splash_logo);
        logoIv.setAdjustViewBounds(true);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                UiHelper.dp(this, 108), UiHelper.dp(this, 108));
        logoLp.bottomMargin = UiHelper.dp(this, 16);
        splashContent.addView(logoIv, logoLp);

        TextView appTitle = new TextView(this);
        appTitle.setText("Đặng Việt Anh");
        appTitle.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        appTitle.setTextSize(24);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = UiHelper.dp(this, 20);
        splashContent.addView(appTitle, titleLp);

        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER_VERTICAL);
        statusPill.setPadding(UiHelper.dp(this, 20), UiHelper.dp(this, 12),
                UiHelper.dp(this, 22), UiHelper.dp(this, 12));
        UiHelper.applyGlassCardStyle(statusPill, this, 24, UiHelper.COLOR_SURFACE_GLASS, 8);

        splashSpinner = new UiHelper.SpinnerView(this);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                UiHelper.dp(this, 24), UiHelper.dp(this, 24));
        spinLp.rightMargin = UiHelper.dp(this, 12);
        statusPill.addView(splashSpinner, spinLp);

        splashStatusTv = new TextView(this);
        splashStatusTv.setText("Đang kết nối...");
        splashStatusTv.setTextColor(Color.WHITE);
        splashStatusTv.setTextSize(15);
        splashStatusTv.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.addView(splashStatusTv);

        splashContent.addView(statusPill);

        splashRetryBtn = new TextView(this);
        splashRetryBtn.setText("Thử lại ngay");
        splashRetryBtn.setTextColor(Color.WHITE);
        splashRetryBtn.setTextSize(14);
        splashRetryBtn.setTypeface(Typeface.DEFAULT_BOLD);
        splashRetryBtn.setGravity(Gravity.CENTER);
        splashRetryBtn.setPadding(UiHelper.dp(this, 22), UiHelper.dp(this, 12),
                UiHelper.dp(this, 22), UiHelper.dp(this, 12));
        UiHelper.applyFocusableTileStyle(splashRetryBtn, this, 24,
                Color.argb(95, 229, 9, 20), Color.argb(190, 229, 9, 20));
        splashRetryBtn.setVisibility(View.GONE);
        splashRetryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                splashStatusTv.setText("Đang kết nối lại...");
                splashSpinner.setVisibility(View.VISIBLE);
                splashSpinner.setSpinning(true);
                splashRetryBtn.setVisibility(View.GONE);
                reloadCastPage();
            }
        });
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryLp.topMargin = UiHelper.dp(this, 16);
        splashContent.addView(splashRetryBtn, retryLp);

        splashOverlay.addView(splashContent, contentLp);

        TextView hintTv = new TextView(this);
        hintTv.setText("Mẹo: Nhấn phím Menu hoặc giữ OK để mở Tiện ích & Bảo trì");
        hintTv.setTextColor(Color.argb(175, 176, 184, 200));
        hintTv.setTextSize(12);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.bottomMargin = UiHelper.dp(this, 22);
        splashOverlay.addView(hintTv, hintLp);
    }

    private void startSplashEntrance() {
        splashOverlay.setAlpha(0f);
        splashContent.setAlpha(0f);
        splashContent.setScaleX(0.92f);
        splashContent.setScaleY(0.92f);

        splashOverlay.animate()
                .alpha(1f)
                .setDuration(360)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        splashContent.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(460)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void tryDismissSplash() {
        if (splashDismissed || !splashMinDone || !pageLoadDone || pageHasError) {
            return;
        }
        splashDismissed = true;
        splashContent.animate()
                .alpha(0f)
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(380)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        splashOverlay.animate()
                .alpha(0f)
                .setDuration(440)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        splashSpinner.setSpinning(false);
                        splashOverlay.setVisibility(View.GONE);
                        webView.requestFocus();
                        updateManager.checkForUpdates(false);
                    }
                })
                .start();
    }

    private void onMainFrameLoadFailed() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!splashDismissed) {
                    splashStatusTv.setText("Mất kết nối mạng — Đang chờ Wi-Fi...");
                    splashRetryBtn.setVisibility(View.VISIBLE);
                    splashRetryBtn.requestFocus();
                }
                handler.removeCallbacks(autoRetryRunnable);
                handler.postDelayed(autoRetryRunnable, 5000);
            }
        });
    }

    private void reloadCastPage() {
        handler.removeCallbacks(autoRetryRunnable);
        pageHasError = false;
        if (webView != null) {
            webView.loadUrl(CAST_URL);
        }
    }

    private void registerNetworkMonitor() {
        try {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    if (pageHasError) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!splashDismissed) {
                                    splashStatusTv.setText("Đã có mạng! Đang kết nối...");
                                    splashRetryBtn.setVisibility(View.GONE);
                                }
                                reloadCastPage();
                            }
                        });
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest req = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(req, networkCallback);
            }
        } catch (Exception ignored) {
        }
    }

    private void initMediaOptimization() {
        try {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            requestAppAudioFocus();

            mediaSession = new MediaSession(this, "DVACastTV");
            mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    evalVideoJs("var v=document.getElementById('dva-video');if(v&&v.src)v.play().catch(function(){});");
                }

                @Override
                public void onPause() {
                    evalVideoJs("var v=document.getElementById('dva-video');if(v&&!v.paused)v.pause();");
                }

                @Override
                public void onStop() {
                    evalVideoJs("var b=document.getElementById('dva-btn-disconnect');if(b)b.click();");
                }

                @Override
                public void onFastForward() {
                    evalVideoJs("var v=document.getElementById('dva-video');if(v&&v.duration)v.currentTime=Math.min(v.duration,v.currentTime+10);");
                }

                @Override
                public void onRewind() {
                    evalVideoJs("var v=document.getElementById('dva-video');if(v&&v.duration)v.currentTime=Math.max(0,v.currentTime-10);");
                }
            });
            PlaybackState state = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY
                            | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE
                            | PlaybackState.ACTION_STOP
                            | PlaybackState.ACTION_FAST_FORWARD
                            | PlaybackState.ACTION_REWIND)
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                    .build();
            mediaSession.setPlaybackState(state);
            mediaSession.setActive(true);
        } catch (Exception ignored) {
        }
    }

    private void requestAppAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(audioFocusListener)
                        .build();
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(audioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) {
        }
    }

    private void abandonAppAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(audioFocusListener);
            }
        } catch (Exception ignored) {
        }
    }

    private void evalVideoJs(final String js) {
        if (webView == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(js, null);
            }
        });
    }

    private void applyImmersiveFullscreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullscreen();
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
                && ev.getButtonState() == MotionEvent.BUTTON_SECONDARY
                && splashDismissed && !updateManager.isShowing() && !overlayMenu.isShowing()) {
            overlayMenu.show();
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (updateManager.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN && updateManager.handleKeyDown(keyCode, event)) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (overlayMenu.isShowing()) {
            if (action == KeyEvent.ACTION_DOWN && overlayMenu.handleKeyDown(keyCode, event)) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        // Open Overlay Menu via Menu/Settings/F1/F2 button
        if (action == KeyEvent.ACTION_DOWN
                && (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_SETTINGS
                || keyCode == KeyEvent.KEYCODE_F1
                || keyCode == KeyEvent.KEYCODE_F2)) {
            if (splashDismissed) {
                overlayMenu.show();
                return true;
            }
        }

        // Long-press OK/Enter (>= 650ms) opens Overlay Menu
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    longPressTriggered = false;
                    handler.removeCallbacks(longPressMenuRunnable);
                    handler.postDelayed(longPressMenuRunnable, LONG_PRESS_MENU_MS);
                } else if (longPressTriggered) {
                    return true;
                }
            } else if (action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPressMenuRunnable);
                if (longPressTriggered) {
                    longPressTriggered = false;
                    return true;
                }
            }
        }

        // Hardware Media Keys forwarding
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                evalVideoJs("var v=document.getElementById('dva-video');if(v&&v.src){v.paused?v.play().catch(function(){}):v.pause();}");
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                evalVideoJs("var v=document.getElementById('dva-video');if(v&&v.src)v.play().catch(function(){});");
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                evalVideoJs("var v=document.getElementById('dva-video');if(v&&!v.paused)v.pause();");
                return true;
            }
        }

        // Back key handling
        if (keyCode == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_DOWN) {
            if (customView != null && chromeClient != null) {
                chromeClient.onHideCustomView();
                return true;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastBackPressMs < 2000L) {
                finish();
            } else {
                lastBackPressMs = now;
                Toast.makeText(this, "Nhấn Back lần nữa để thoát · Giữ OK để mở Tiện ích", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestAppAudioFocus();
        if (webView != null) {
            webView.onResume();
        }
        if (updateManager != null) {
            updateManager.onHostResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        abandonAppAudioFocus();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private static class NativeBridge {
        @JavascriptInterface
        public boolean isNativeApp() {
            return true;
        }
    }
}
