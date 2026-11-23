package com.dangvietanh.cast;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.ui.AspectRatioFrameLayout;

import java.util.Locale;

/**
 * Hardware-accelerated native Media3 ExoPlayer controller with dual-player preload
 * support for seamless zero-gap episode transitions on Android TV & Projectors.
 */
public class NativePlayerController {

    public interface PlayerEventListener {
        void onPlaybackState(boolean isPlaying, boolean isBuffering, boolean isEnded, boolean isReady);
        void onProgress(double posSec, double durSec, int bufferedPct);
        void onFirstFrame();
        void onVideoSize(int width, int height);
        void onError(String errorMsg, boolean isCodecError);
        void onVisibilityChanged(boolean active);
    }

    private static final long TICK_INTERVAL_MS = 280L;
    private static final long UNSUPPORTED_VIDEO_TIMEOUT_MS = 4500L;

    private final Context context;
    private final PlayerEventListener listener;
    private final String userAgent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FrameLayout container;
    private final AspectRatioFrameLayout aspectFrame;
    private final SurfaceView surfaceView;

    private DefaultDataSource.Factory dataSourceFactory;
    private ExoPlayer exoPlayer;
    private Player.Listener mainPlayerListener;

    // Secondary player for seamless next-episode preload
    private ExoPlayer preloadPlayer;
    private String preloadUrl = "";
    private boolean preloadReady = false;

    private String currentUrl = "";
    private boolean isActive = false;
    private boolean firstFrameRendered = false;
    private boolean hasVideoTrackGroup = false;
    private boolean videoUnsupported = false;
    private boolean muted = false;
    private float currentRate = 1.0f;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private long readyPlayingAccumMs = 0L;
    private long lastTickElapsedMs = 0L;
    private boolean ticking = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!ticking || exoPlayer == null || !isActive) return;
            long now = SystemClock.elapsedRealtime();
            boolean isPlayingNow = exoPlayer.isPlaying();
            int state = exoPlayer.getPlaybackState();

            if (isPlayingNow && state == Player.STATE_READY) {
                if (lastTickElapsedMs > 0L) {
                    long delta = now - lastTickElapsedMs;
                    if (delta > 0L && delta < 2000L) {
                        readyPlayingAccumMs += delta;
                    }
                }
                lastTickElapsedMs = now;

                if (!videoUnsupported
                        && hasVideoTrackGroup
                        && readyPlayingAccumMs >= UNSUPPORTED_VIDEO_TIMEOUT_MS
                        && (!firstFrameRendered || videoWidth <= 0 || videoHeight <= 0)) {
                    videoUnsupported = true;
                    if (listener != null) {
                        listener.onError("black_video_no_frames", true);
                    }
                }
            } else {
                lastTickElapsedMs = 0L;
            }

            dispatchProgress();

            if (isPlayingNow || state == Player.STATE_BUFFERING || exoPlayer.getPlayWhenReady()) {
                handler.postDelayed(this, TICK_INTERVAL_MS);
            } else {
                ticking = false;
            }
        }
    };

    public NativePlayerController(Context context, String userAgent, PlayerEventListener listener) {
        this.context = context.getApplicationContext();
        this.userAgent = (userAgent != null && !userAgent.trim().isEmpty()) ? userAgent : "DVACastTV/4.0";
        this.listener = listener;

        container = new FrameLayout(this.context);
        container.setVisibility(View.GONE);
        container.setFocusable(false);
        container.setClickable(false);

        aspectFrame = new AspectRatioFrameLayout(this.context);
        aspectFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        aspectFrame.setFocusable(false);
        aspectFrame.setClickable(false);

        surfaceView = new SurfaceView(this.context);
        surfaceView.setFocusable(false);
        surfaceView.setClickable(false);

        aspectFrame.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        container.addView(aspectFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
    }

    public View getView() {
        return container;
    }

    public boolean isActive() {
        return isActive && !currentUrl.isEmpty();
    }

    public boolean isPlaying() {
        return exoPlayer != null && isActive && exoPlayer.isPlaying();
    }

    public boolean isBuffering() {
        return exoPlayer != null && isActive && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING;
    }

    public double getCurrentPositionSec() {
        if (exoPlayer == null || !isActive) return 0.0;
        return Math.max(0L, exoPlayer.getCurrentPosition()) / 1000.0;
    }

    public double getDurationSec() {
        if (exoPlayer == null || !isActive) return 0.0;
        long raw = exoPlayer.getDuration();
        return (raw != C.TIME_UNSET && raw > 0L) ? (raw / 1000.0) : 0.0;
    }

    private void ensureDataSourceFactory() {
        if (dataSourceFactory != null) return;
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000)
                .setUserAgent(userAgent);
        dataSourceFactory = new DefaultDataSource.Factory(context, httpFactory);
    }

    private ExoPlayer createExoInstance(boolean isPreload) {
        ensureDataSourceFactory();
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true);

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build());

        DefaultLoadControl loadControl = isPreload
                ? new DefaultLoadControl.Builder()
                        .setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                        .setBufferDurationsMs(8_000, 20_000, 1_500, 3_000)
                        .build()
                : new DefaultLoadControl.Builder()
                        .setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                        .setBufferDurationsMs(15_000, 45_000, 2_000, 4_000)
                        .build();

        ExoPlayer player = new ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        player.setAudioAttributes(audioAttributes, false);
        return player;
    }

    private MediaSource buildMediaSource(String url, String streamType) {
        ensureDataSourceFactory();
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        boolean isHls = "hls".equalsIgnoreCase(streamType)
                || lowerUrl.contains(".m3u8")
                || lowerUrl.contains("hls-proxy");

        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(Uri.parse(url));
        if (isHls) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        MediaItem item = itemBuilder.build();
        return isHls
                ? new HlsMediaSource.Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(item)
                : new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(item);
    }

    private Player.Listener createMainListener() {
        return new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (!isActive || exoPlayer == null) return;
                boolean playing = exoPlayer.isPlaying();
                boolean buffering = playbackState == Player.STATE_BUFFERING;
                boolean ended = playbackState == Player.STATE_ENDED;
                boolean ready = playbackState == Player.STATE_READY;
                if (buffering || ready) {
                    startTickingIfNeeded();
                } else if (ended) {
                    stopTicking();
                }
                if (listener != null) {
                    listener.onPlaybackState(playing, buffering, ended, ready);
                }
                dispatchProgress();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlayingNow) {
                if (!isActive || exoPlayer == null) return;
                if (isPlayingNow) {
                    lastTickElapsedMs = SystemClock.elapsedRealtime();
                    startTickingIfNeeded();
                } else {
                    lastTickElapsedMs = 0L;
                }
                int pb = exoPlayer.getPlaybackState();
                if (listener != null) {
                    listener.onPlaybackState(
                            isPlayingNow,
                            pb == Player.STATE_BUFFERING,
                            pb == Player.STATE_ENDED,
                            pb == Player.STATE_READY);
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                if (!isActive) return;
                firstFrameRendered = true;
                videoUnsupported = false;
                if (listener != null) {
                    listener.onFirstFrame();
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                videoWidth = videoSize.width;
                videoHeight = videoSize.height;
                if (videoSize.width > 0 && videoSize.height > 0) {
                    float ratio = (videoSize.width * videoSize.pixelWidthHeightRatio) / (float) videoSize.height;
                    if (ratio > 0f) {
                        aspectFrame.setAspectRatio(ratio);
                    }
                    if (listener != null) {
                        listener.onVideoSize(videoWidth, videoHeight);
                    }
                }
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                if (!isActive) return;
                boolean foundVideoGroup = false;
                boolean anyVideoSupportedOrSelected = false;
                for (Tracks.Group group : tracks.getGroups()) {
                    if (group.getType() == C.TRACK_TYPE_VIDEO) {
                        foundVideoGroup = true;
                        for (int i = 0; i < group.length; i++) {
                            if (group.isTrackSelected(i) || group.isTrackSupported(i, true)) {
                                anyVideoSupportedOrSelected = true;
                                break;
                            }
                        }
                    }
                }
                hasVideoTrackGroup = foundVideoGroup;
                if (foundVideoGroup && !anyVideoSupportedOrSelected && !videoUnsupported) {
                    videoUnsupported = true;
                    if (listener != null) {
                        listener.onError("codec_unsupported", true);
                    }
                }
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                Player.PositionInfo newPosition,
                                                int reason) {
                if (isActive && reason == Player.DISCONTINUITY_REASON_SEEK) {
                    dispatchProgress();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!isActive) return;
                stopTicking();
                int code = error.errorCode;
                boolean codecErr = (code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                        || code == PlaybackException.ERROR_CODE_DECODING_FAILED
                        || code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
                        || code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                        || code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                        || code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED);
                if (codecErr) {
                    videoUnsupported = true;
                }
                if (listener != null) {
                    listener.onError(error.getErrorCodeName(), codecErr);
                }
            }
        };
    }

    private void ensureMainPlayer() {
        if (exoPlayer != null) return;
        exoPlayer = createExoInstance(false);
        exoPlayer.setVideoSurfaceView(surfaceView);
        mainPlayerListener = createMainListener();
        exoPlayer.addListener(mainPlayerListener);
    }

    public void loadMedia(String url, String streamType, double startPosSec, boolean paused, boolean mutedState, float rate) {
        String cleanUrl = url != null ? url.trim() : "";
        if (cleanUrl.isEmpty()) {
            stopMedia();
            return;
        }

        if (isPreloadedFor(cleanUrl) && !paused) {
            swapToPreloaded(startPosSec, rate, mutedState);
            return;
        }

        ensureMainPlayer();

        currentUrl = cleanUrl;
        isActive = true;
        firstFrameRendered = false;
        hasVideoTrackGroup = false;
        videoUnsupported = false;
        videoWidth = 0;
        videoHeight = 0;
        readyPlayingAccumMs = 0L;
        lastTickElapsedMs = 0L;
        muted = mutedState;
        currentRate = clampRate(rate);

        if (container.getVisibility() != View.VISIBLE) {
            container.setVisibility(View.VISIBLE);
        }
        if (listener != null) {
            listener.onVisibilityChanged(true);
            listener.onPlaybackState(false, true, false, false);
        }

        MediaSource mediaSource = buildMediaSource(cleanUrl, streamType);
        long startMs = startPosSec > 0.5 ? Math.round(startPosSec * 1000.0) : 0L;

        exoPlayer.stop();
        exoPlayer.setMediaSource(mediaSource, startMs);
        exoPlayer.setVolume(mutedState ? 0f : 1f);
        exoPlayer.setPlaybackSpeed(currentRate);
        exoPlayer.setPlayWhenReady(!paused);
        exoPlayer.prepare();

        startTickingIfNeeded();
    }

    public void preloadNext(final String url, String streamType, double startPosSec) {
        String cleanUrl = url != null ? url.trim() : "";
        if (cleanUrl.isEmpty() || cleanUrl.equals(currentUrl)) return;
        if (cleanUrl.equals(preloadUrl) && preloadPlayer != null) return;

        clearPreload();
        try {
            preloadUrl = cleanUrl;
            preloadReady = false;
            preloadPlayer = createExoInstance(true);
            preloadPlayer.setVolume(0f);
            preloadPlayer.setPlayWhenReady(false);
            preloadPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        preloadReady = true;
                    }
                }
            });
            MediaSource src = buildMediaSource(cleanUrl, streamType);
            long startMs = startPosSec > 0.5 ? Math.round(startPosSec * 1000.0) : 0L;
            preloadPlayer.setMediaSource(src, startMs);
            preloadPlayer.prepare();
        } catch (Exception ignored) {
            clearPreload();
        }
    }

    public boolean isPreloadedFor(String url) {
        return preloadPlayer != null
                && preloadReady
                && url != null
                && url.trim().equals(preloadUrl);
    }

    public boolean swapToPreloaded(double startPosSec, float rate, boolean mutedState) {
        if (preloadPlayer == null || preloadUrl.isEmpty()) return false;

        stopTicking();
        ExoPlayer oldMain = exoPlayer;
        if (oldMain != null) {
            try {
                if (mainPlayerListener != null) oldMain.removeListener(mainPlayerListener);
                oldMain.stop();
                oldMain.clearVideoSurfaceView(surfaceView);
                oldMain.release();
            } catch (Exception ignored) {
            }
        }

        exoPlayer = preloadPlayer;
        currentUrl = preloadUrl;
        preloadPlayer = null;
        preloadUrl = "";
        preloadReady = false;

        isActive = true;
        firstFrameRendered = false;
        hasVideoTrackGroup = false;
        videoUnsupported = false;
        videoWidth = 0;
        videoHeight = 0;
        readyPlayingAccumMs = 0L;
        lastTickElapsedMs = 0L;
        muted = mutedState;
        currentRate = clampRate(rate);

        if (container.getVisibility() != View.VISIBLE) {
            container.setVisibility(View.VISIBLE);
        }

        mainPlayerListener = createMainListener();
        exoPlayer.addListener(mainPlayerListener);
        exoPlayer.setVideoSurfaceView(surfaceView);
        if (startPosSec > 0.5) {
            exoPlayer.seekTo(Math.round(startPosSec * 1000.0));
        }
        exoPlayer.setVolume(mutedState ? 0f : 1f);
        exoPlayer.setPlaybackSpeed(currentRate);
        exoPlayer.setPlayWhenReady(true);

        if (listener != null) {
            listener.onVisibilityChanged(true);
            listener.onPlaybackState(true, exoPlayer.getPlaybackState() == Player.STATE_BUFFERING, false, true);
        }
        startTickingIfNeeded();
        return true;
    }

    public void clearPreload() {
        preloadUrl = "";
        preloadReady = false;
        if (preloadPlayer != null) {
            try {
                preloadPlayer.stop();
                preloadPlayer.release();
            } catch (Exception ignored) {
            }
            preloadPlayer = null;
        }
    }

    public void stopMedia() {
        stopTicking();
        clearPreload();
        boolean wasActive = isActive;
        isActive = false;
        currentUrl = "";
        firstFrameRendered = false;
        hasVideoTrackGroup = false;
        videoUnsupported = false;
        videoWidth = 0;
        videoHeight = 0;
        readyPlayingAccumMs = 0L;
        lastTickElapsedMs = 0L;

        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.clearMediaItems();
            } catch (Exception ignored) {
            }
        }
        if (container.getVisibility() != View.GONE) {
            container.setVisibility(View.GONE);
        }
        if (wasActive && listener != null) {
            listener.onVisibilityChanged(false);
        }
    }

    public void play() {
        if (exoPlayer == null || !isActive) return;
        if (exoPlayer.getPlaybackState() == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L);
        }
        exoPlayer.setPlayWhenReady(true);
        startTickingIfNeeded();
    }

    public void pause() {
        if (exoPlayer == null || !isActive) return;
        exoPlayer.setPlayWhenReady(false);
    }

    public void seekTo(double posSec) {
        if (exoPlayer == null || !isActive) return;
        long targetMs = Math.max(0L, Math.round(posSec * 1000.0));
        long durMs = exoPlayer.getDuration();
        if (durMs != C.TIME_UNSET && durMs > 0L) {
            targetMs = Math.min(durMs, targetMs);
        }
        exoPlayer.seekTo(targetMs);
        dispatchProgress();
    }

    public void setMuted(boolean m) {
        muted = m;
        if (exoPlayer != null) {
            exoPlayer.setVolume(m ? 0f : 1f);
        }
    }

    public void setPlaybackRate(float rate) {
        currentRate = clampRate(rate);
        if (exoPlayer != null) {
            exoPlayer.setPlaybackSpeed(currentRate);
        }
    }

    public void setResizeMode(boolean cover) {
        aspectFrame.setResizeMode(cover
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    public void onHostPause() {
        if (exoPlayer != null && isActive && exoPlayer.getPlayWhenReady()) {
            exoPlayer.setPlayWhenReady(false);
        }
        stopTicking();
    }

    public void release() {
        stopTicking();
        clearPreload();
        isActive = false;
        currentUrl = "";
        if (exoPlayer != null) {
            try {
                exoPlayer.clearVideoSurfaceView(surfaceView);
                exoPlayer.release();
            } catch (Exception ignored) {
            }
            exoPlayer = null;
        }
    }

    private float clampRate(float rate) {
        if (!Float.isFinite(rate) || rate < 0.25f || rate > 2.5f) return 1.0f;
        return rate;
    }

    private void startTickingIfNeeded() {
        if (!ticking && isActive && exoPlayer != null) {
            ticking = true;
            handler.removeCallbacks(tickRunnable);
            handler.postDelayed(tickRunnable, TICK_INTERVAL_MS);
        }
    }

    private void stopTicking() {
        ticking = false;
        lastTickElapsedMs = 0L;
        handler.removeCallbacks(tickRunnable);
    }

    private void dispatchProgress() {
        if (listener == null || exoPlayer == null || !isActive) return;
        long posMs = Math.max(0L, exoPlayer.getCurrentPosition());
        long rawDur = exoPlayer.getDuration();
        long durMs = (rawDur != C.TIME_UNSET && rawDur > 0L) ? rawDur : 0L;
        int bufPct = Math.max(0, Math.min(100, exoPlayer.getBufferedPercentage()));
        listener.onProgress(posMs / 1000.0, durMs / 1000.0, bufPct);
    }
}
