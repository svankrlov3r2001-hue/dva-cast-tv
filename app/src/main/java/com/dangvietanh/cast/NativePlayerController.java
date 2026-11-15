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

import org.json.JSONObject;

import java.util.Locale;

/**
 * Hardware-accelerated native Media3 ExoPlayer layer rendered onto a SurfaceView
 * behind the transparent WebView HUD so 4K / 4K 60fps / HDR streams decode directly
 * through Android TV / Projector MediaCodec hardware composer planes.
 */
public class NativePlayerController {

    public interface Callback {
        void onNativeEvent(String snapshotJson);
        void onNativeVisibilityChanged(boolean active);
    }

    private static final long TICK_INTERVAL_MS = 280L;
    private static final long UNSUPPORTED_VIDEO_TIMEOUT_MS = 3200L;

    private final Context context;
    private final Callback callback;
    private final String userAgent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final FrameLayout container;
    private final AspectRatioFrameLayout aspectFrame;
    private final SurfaceView surfaceView;

    private ExoPlayer exoPlayer;
    private DefaultDataSource.Factory dataSourceFactory;

    private String currentUrl = "";
    private boolean isActive = false;
    private boolean firstFrameRendered = false;
    private boolean hasVideoTrackGroup = false;
    private boolean videoUnsupported = false;
    private boolean isCodecError = false;
    private boolean muted = false;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private String lastErrorMsg = "";
    private long readyPlayingAccumMs = 0L;
    private long lastTickElapsedMs = 0L;
    private boolean ticking = false;

    private volatile String cachedSnapshotJson = "{\"active\":false,\"pos\":0,\"dur\":0,\"playing\":false,\"paused\":true,\"buffering\":false,\"ended\":false,\"ready\":false,\"firstFrame\":false,\"vw\":0,\"vh\":0,\"muted\":false,\"err\":\"\",\"unsupportedVideo\":false,\"codecError\":false,\"event\":\"idle\"}";

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
                    isCodecError = true;
                    lastErrorMsg = "black_video_no_frames";
                    publishState("videoUnsupported", true);
                }
            } else {
                lastTickElapsedMs = 0L;
            }

            publishState("tick", false);

            if (isPlayingNow || state == Player.STATE_BUFFERING) {
                handler.postDelayed(this, TICK_INTERVAL_MS);
            } else {
                ticking = false;
            }
        }
    };

    public NativePlayerController(Context context, String userAgent, Callback callback) {
        this.context = context;
        this.userAgent = userAgent;
        this.callback = callback;

        container = new FrameLayout(context);
        container.setVisibility(View.GONE);
        container.setFocusable(false);
        container.setClickable(false);

        aspectFrame = new AspectRatioFrameLayout(context);
        aspectFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        aspectFrame.setFocusable(false);
        aspectFrame.setClickable(false);

        surfaceView = new SurfaceView(context);
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

    public String getSnapshotJson() {
        return cachedSnapshotJson;
    }

    private void ensurePlayer() {
        if (exoPlayer != null) return;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000)
                .setUserAgent(userAgent != null && !userAgent.isEmpty() ? userAgent : "DVACastTV/3.2");
        dataSourceFactory = new DefaultDataSource.Factory(context, httpFactory);

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true);

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build());

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .setBufferDurationsMs(15_000, 45_000, 2_000, 4_000)
                .build();

        exoPlayer = new ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        exoPlayer.setAudioAttributes(audioAttributes, false);
        exoPlayer.setVideoSurfaceView(surfaceView);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (!isActive) return;
                if (playbackState == Player.STATE_READY) {
                    startTickingIfNeeded();
                    publishState("ready", true);
                } else if (playbackState == Player.STATE_BUFFERING) {
                    startTickingIfNeeded();
                    publishState("buffering", true);
                } else if (playbackState == Player.STATE_ENDED) {
                    stopTicking();
                    publishState("ended", true);
                } else {
                    publishState("idle", true);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (!isActive) return;
                if (isPlaying) {
                    lastTickElapsedMs = SystemClock.elapsedRealtime();
                    startTickingIfNeeded();
                    publishState("playing", true);
                } else {
                    lastTickElapsedMs = 0L;
                    publishState("paused", true);
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                if (!isActive) return;
                firstFrameRendered = true;
                videoUnsupported = false;
                isCodecError = false;
                if ("black_video_no_frames".equals(lastErrorMsg) || "codec_unsupported".equals(lastErrorMsg)) {
                    lastErrorMsg = "";
                }
                publishState("firstFrame", true);
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
                    if (!firstFrameRendered) {
                        // Do not clear firstFrame flag prematurely; onRenderedFirstFrame will confirm frame output
                        publishState("videoSize", true);
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
                    isCodecError = true;
                    lastErrorMsg = "codec_unsupported";
                    publishState("videoUnsupported", true);
                }
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                Player.PositionInfo newPosition,
                                                int reason) {
                if (!isActive) return;
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    publishState("seeked", true);
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
                isCodecError = codecErr;
                if (codecErr) {
                    videoUnsupported = true;
                }
                lastErrorMsg = (codecErr ? "native_codec_" : "native_err_") + error.getErrorCodeName();
                publishState("error", true);
            }
        });
    }

    public void loadMedia(String url, String streamType, double startPosSec, boolean paused, boolean mutedState, float rate) {
        String cleanUrl = url != null ? url.trim() : "";
        if (cleanUrl.isEmpty()) {
            stopMedia();
            return;
        }

        ensurePlayer();

        currentUrl = cleanUrl;
        isActive = true;
        firstFrameRendered = false;
        hasVideoTrackGroup = false;
        videoUnsupported = false;
        isCodecError = false;
        lastErrorMsg = "";
        videoWidth = 0;
        videoHeight = 0;
        readyPlayingAccumMs = 0L;
        lastTickElapsedMs = 0L;
        muted = mutedState;

        if (container.getVisibility() != View.VISIBLE) {
            container.setVisibility(View.VISIBLE);
        }
        if (callback != null) {
            callback.onNativeVisibilityChanged(true);
        }

        String lowerUrl = cleanUrl.toLowerCase(Locale.ROOT);
        boolean isHls = "hls".equalsIgnoreCase(streamType)
                || lowerUrl.contains(".m3u8")
                || lowerUrl.contains("hls-proxy");

        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(Uri.parse(cleanUrl));
        if (isHls) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        MediaItem item = itemBuilder.build();
        MediaSource mediaSource = isHls
                ? new HlsMediaSource.Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(item)
                : new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(item);

        long startMs = startPosSec > 0.5 ? Math.round(startPosSec * 1000.0) : 0L;
        exoPlayer.stop();
        exoPlayer.setMediaSource(mediaSource, startMs);
        exoPlayer.setVolume(mutedState ? 0f : 1f);
        exoPlayer.setPlaybackSpeed(clampRate(rate));
        exoPlayer.setPlayWhenReady(!paused);
        exoPlayer.prepare();

        startTickingIfNeeded();
        publishState("loading", true);
    }

    public void stopMedia() {
        stopTicking();
        boolean wasActive = isActive;
        isActive = false;
        currentUrl = "";
        firstFrameRendered = false;
        hasVideoTrackGroup = false;
        videoUnsupported = false;
        isCodecError = false;
        lastErrorMsg = "";
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
        if (wasActive && callback != null) {
            callback.onNativeVisibilityChanged(false);
        }
        publishState("stopped", false);
    }

    public void play() {
        if (exoPlayer == null || !isActive) return;
        if (exoPlayer.getPlaybackState() == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L);
        }
        exoPlayer.setPlayWhenReady(true);
        startTickingIfNeeded();
        publishState("playing", true);
    }

    public void pause() {
        if (exoPlayer == null || !isActive) return;
        exoPlayer.setPlayWhenReady(false);
        publishState("paused", true);
    }

    public void seekTo(double posSec) {
        if (exoPlayer == null || !isActive) return;
        long targetMs = Math.max(0L, Math.round(posSec * 1000.0));
        long durMs = exoPlayer.getDuration();
        if (durMs != C.TIME_UNSET && durMs > 0L) {
            targetMs = Math.min(durMs, targetMs);
        }
        exoPlayer.seekTo(targetMs);
        publishState("seeked", true);
    }

    public void setMuted(boolean m) {
        muted = m;
        if (exoPlayer != null) {
            exoPlayer.setVolume(m ? 0f : 1f);
        }
        publishState("mute", true);
    }

    public void setPlaybackRate(float rate) {
        if (exoPlayer != null) {
            exoPlayer.setPlaybackSpeed(clampRate(rate));
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
            publishState("paused", true);
        }
        stopTicking();
    }

    public void release() {
        stopTicking();
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

    private void publishState(String eventName, boolean notifyJsCallback) {
        try {
            long posMs = 0L;
            long durMs = 0L;
            int pbState = Player.STATE_IDLE;
            boolean playWhenReady = false;
            boolean isPlayingNow = false;

            if (exoPlayer != null && isActive) {
                posMs = Math.max(0L, exoPlayer.getCurrentPosition());
                long rawDur = exoPlayer.getDuration();
                durMs = (rawDur != C.TIME_UNSET && rawDur > 0L) ? rawDur : 0L;
                pbState = exoPlayer.getPlaybackState();
                playWhenReady = exoPlayer.getPlayWhenReady();
                isPlayingNow = exoPlayer.isPlaying();
            }

            JSONObject obj = new JSONObject();
            obj.put("active", isActive);
            obj.put("url", currentUrl);
            obj.put("pos", posMs / 1000.0);
            obj.put("dur", durMs / 1000.0);
            obj.put("playing", isPlayingNow);
            obj.put("paused", !playWhenReady);
            obj.put("buffering", isActive && pbState == Player.STATE_BUFFERING);
            obj.put("ended", isActive && pbState == Player.STATE_ENDED);
            obj.put("ready", isActive && (pbState == Player.STATE_READY || pbState == Player.STATE_ENDED));
            obj.put("firstFrame", firstFrameRendered);
            obj.put("vw", videoWidth);
            obj.put("vh", videoHeight);
            obj.put("muted", muted);
            obj.put("err", lastErrorMsg);
            obj.put("unsupportedVideo", videoUnsupported);
            obj.put("codecError", isCodecError);
            obj.put("event", eventName);

            String json = obj.toString();
            cachedSnapshotJson = json;
            if (notifyJsCallback && callback != null) {
                callback.onNativeEvent(json);
            }
        } catch (Exception ignored) {
        }
    }
}
