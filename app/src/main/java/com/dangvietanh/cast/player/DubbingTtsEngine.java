package com.dangvietanh.cast.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.dangvietanh.cast.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Zero-delay AI Voice Dubbing / Thuyết minh engine using handle.dangvietanh.com/tts_proxy
 * with dual-ExoPlayer ping-pong pre-staging, local prefetch cache, and server cache_tts_proxy pre-warming.
 */
public final class DubbingTtsEngine {

    public static final double TTS_START_LEAD_SEC = 0.22;
    private static final int TTS_LOCAL_PREFETCH_COUNT = 12;
    private static final int TTS_LOCAL_CACHE_MAX = 200;
    private static final int TTS_SERVER_PREWARM_COUNT = 30;
    private static final int TTS_SERVER_PREWARM_TRACK_MAX = 1000;

    public static boolean isSpeakableSubtitleText(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.contains("♪")) return false;
        if ("[Đặng Việt Anh]".equals(trimmed) || "Đặng Việt Anh".equals(trimmed)) return false;
        return true;
    }

    private static final class CueAudioSpec {
        final int cueIndex;
        final SubtitleEngine.Cue cue;
        final String spokenText;
        final String spokenLang;
        final String effVoice;
        final double serverSpeed;
        final String remoteUrl;
        final String prewarmUrl;
        final double nextCueStart;
        final double speechWindowSec;

        CueAudioSpec(
                int cueIndex,
                SubtitleEngine.Cue cue,
                String spokenText,
                String spokenLang,
                String effVoice,
                double serverSpeed,
                String remoteUrl,
                String prewarmUrl,
                double nextCueStart,
                double speechWindowSec) {
            this.cueIndex = cueIndex;
            this.cue = cue;
            this.spokenText = spokenText;
            this.spokenLang = spokenLang;
            this.effVoice = effVoice;
            this.serverSpeed = serverSpeed;
            this.remoteUrl = remoteUrl;
            this.prewarmUrl = prewarmUrl;
            this.nextCueStart = nextCueStart;
            this.speechWindowSec = speechWindowSec;
        }
    }

    private static final class StagedSlot {
        final String remoteUrl;
        final String localPath;
        boolean ready;

        StagedSlot(String remoteUrl, String localPath) {
            this.remoteUrl = remoteUrl;
            this.localPath = localPath;
            this.ready = false;
        }
    }

    private static final class QueuedTtsTask {
        final String cueSig;
        final int cueIndex;
        final String spokenText;
        final String spokenLang;
        final String effVoice;
        final double serverSpeed;
        final String remoteUrl;
        final double cueStart;
        final double cueEnd;
        final double nextCueStart;
        final double speechWindowSec;

        QueuedTtsTask(
                String cueSig,
                int cueIndex,
                String spokenText,
                String spokenLang,
                String effVoice,
                double serverSpeed,
                String remoteUrl,
                double cueStart,
                double cueEnd,
                double nextCueStart,
                double speechWindowSec) {
            this.cueSig = cueSig;
            this.cueIndex = cueIndex;
            this.spokenText = spokenText;
            this.spokenLang = spokenLang;
            this.effVoice = effVoice;
            this.serverSpeed = serverSpeed;
            this.remoteUrl = remoteUrl;
            this.cueStart = cueStart;
            this.cueEnd = cueEnd;
            this.nextCueStart = nextCueStart;
            this.speechWindowSec = speechWindowSec;
        }
    }

    private final Context context;
    private final SubtitleEngine subtitleEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dlExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService prewarmExecutor = Executors.newFixedThreadPool(2);
    private final Map<String, String> localCache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> serverPrewarmed = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Object> urlLocks = new ConcurrentHashMap<>();

    // Dual ping-pong audio players: standby pre-loads next line so cue onset has 0ms prepare latency
    private final ExoPlayer[] ttsPlayers = new ExoPlayer[2];
    private final StagedSlot[] stagedSlots = new StagedSlot[2];
    private int activeSlot = 0;

    private final AtomicInteger prefetchWaveId = new AtomicInteger(0);
    private List<SubtitleEngine.Cue> latestVnCues = null;
    private int lastLookaheadAnchor = -2;
    private int lastLookaheadCuesCount = -1;
    private String lastLookaheadTransLang = "";

    private String lastSpokenSig = "";
    private String episodeSig = "";

    private boolean isSpeaking = false;
    private boolean pausedMidUtterance = false;
    private QueuedTtsTask currentTask = null;
    private QueuedTtsTask pendingCue = null;
    private long currentFinishEstMs = 0L;
    private int playGen = 0;

    private boolean hasPrevVideoTime = false;
    private double prevVideoTimeSec = 0.0;
    private double lastVideoTimeSec = 0.0;
    private double lastExtendSec = 0.0;
    private double lastVideoPlaybackRate = 1.0;
    private String lastVoiceMode = "";
    private boolean canPlayAudioNow = false;
    private Runnable finishWatchdogRunnable = null;

    public DubbingTtsEngine(Context context, SubtitleEngine subtitleEngine) {
        this.context = context.getApplicationContext();
        this.subtitleEngine = subtitleEngine;
    }

    private void ensureTtsPlayers() {
        for (int i = 0; i < 2; i++) {
            if (ttsPlayers[i] != null) continue;
            final int slotIdx = i;
            ExoPlayer p = new ExoPlayer.Builder(context).build();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build();
            // handleAudioFocus = false so it mixes seamlessly with movie audio
            p.setAudioAttributes(attrs, false);
            p.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        StagedSlot staged = stagedSlots[slotIdx];
                        if (staged != null) {
                            staged.ready = true;
                        }
                        if (activeSlot == slotIdx && isSpeaking && currentTask != null && ttsPlayers[slotIdx] != null) {
                            long durMs = ttsPlayers[slotIdx].getDuration();
                            if (durMs != C.TIME_UNSET && durMs > 200L) {
                                calibrateRateAndTimer(ttsPlayers[slotIdx], durMs / 1000.0, currentTask, playGen);
                            }
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        if (activeSlot == slotIdx) {
                            completeAndFlushPending();
                        }
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (activeSlot != slotIdx) {
                        stagedSlots[slotIdx] = null;
                        return;
                    }
                    clearFinishWatchdog();
                    if (currentTask != null) {
                        localCache.remove(currentTask.remoteUrl);
                    }
                    isSpeaking = false;
                    pausedMidUtterance = false;
                    currentTask = null;
                    pendingCue = null;
                    lastSpokenSig = "";
                    currentFinishEstMs = 0L;
                }
            });
            ttsPlayers[i] = p;
        }
    }

    private void clearFinishWatchdog() {
        if (finishWatchdogRunnable != null) {
            mainHandler.removeCallbacks(finishWatchdogRunnable);
            finishWatchdogRunnable = null;
        }
    }

    private void scheduleFinishWatchdog(long delayMs, final int expectedGen) {
        clearFinishWatchdog();
        final long safeDelay = Math.max(250L, delayMs);
        finishWatchdogRunnable = new Runnable() {
            @Override
            public void run() {
                finishWatchdogRunnable = null;
                if (playGen == expectedGen && isSpeaking && !pausedMidUtterance) {
                    completeAndFlushPending();
                }
            }
        };
        mainHandler.postDelayed(finishWatchdogRunnable, safeDelay);
    }

    private double computeAvailableWallSecForTask(QueuedTtsTask task) {
        double effectiveStart = Math.max(task.cueStart, lastVideoTimeSec);
        double effectiveEnd = Math.max(task.cueEnd, effectiveStart + 0.5);
        return computeCueSpeechWindowSec(
                effectiveStart,
                effectiveEnd,
                lastExtendSec,
                task.nextCueStart,
                lastVideoPlaybackRate
        );
    }

    private void calibrateRateAndTimer(ExoPlayer player, double actualAudioDurSec, QueuedTtsTask task, int expectedGen) {
        if (actualAudioDurSec <= 0.2 || player == null || playGen != expectedGen) return;
        double availableWallSec = computeAvailableWallSecForTask(task);
        double refinedRate = computeLocalTtsPlaybackRate(
                actualAudioDurSec,
                availableWallSec,
                task.effVoice,
                task.serverSpeed,
                lastVideoPlaybackRate
        );
        try {
            // Keep pitch = 1.0f so SonicAudioProcessor preserves natural voice timbre
            player.setPlaybackParameters(new PlaybackParameters((float) refinedRate, 1.0f));
        } catch (Exception ignored) {
        }

        double playerPosSec = 0.0;
        try {
            long posMs = player.getCurrentPosition();
            if (posMs > 0L) {
                playerPosSec = posMs / 1000.0;
            }
        } catch (Exception ignored) {
        }
        double remainingAudioSec = Math.max(0.2, actualAudioDurSec - playerPosSec);
        long remainingWallMs = Math.max(250L, Math.round((remainingAudioSec / Math.max(0.5, refinedRate)) * 1000.0));
        currentFinishEstMs = System.currentTimeMillis() + remainingWallMs;
        scheduleFinishWatchdog(remainingWallMs + 250L, expectedGen);
    }

    private void completeAndFlushPending() {
        clearFinishWatchdog();
        isSpeaking = false;
        pausedMidUtterance = false;
        currentTask = null;
        currentFinishEstMs = 0L;
        QueuedTtsTask queued = pendingCue;
        pendingCue = null;
        if (queued != null && canPlayAudioNow) {
            startTaskNow(queued);
        }
    }

    public void setEpisodeSignature(String sig) {
        this.episodeSig = sig != null ? sig : "";
        this.hasPrevVideoTime = false;
        this.prevVideoTimeSec = 0.0;
        this.lastLookaheadAnchor = -2;
        this.lastLookaheadCuesCount = -1;
        this.latestVnCues = null;
        stopAndResetQueue();
    }

    public void onSubtitlesLoaded(List<SubtitleEngine.Cue> vnCues, double currentTimeSec) {
        this.latestVnCues = vnCues;
        this.lastLookaheadAnchor = -2;
        this.lastLookaheadCuesCount = -1;
        if (vnCues != null && !vnCues.isEmpty() && Prefs.isSubAiVoiceEnabled(context)) {
            triggerLookaheadIfNeeded(vnCues, currentTimeSec, -1);
        }
    }

    public void clearCacheAndReset() {
        hasPrevVideoTime = false;
        prevVideoTimeSec = 0.0;
        lastLookaheadAnchor = -2;
        lastLookaheadCuesCount = -1;
        localCache.clear();
        inFlight.clear();
        serverPrewarmed.clear();
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File dir = new File(context.getCacheDir(), "dva_tts");
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete();
                        }
                    }
                }
            }
        });
        stopAndResetQueue();
    }

    public void pauseNow() {
        clearFinishWatchdog();
        if (isSpeaking && Prefs.isSubAiVoiceEnabled(context)) {
            pausedMidUtterance = true;
        }
        for (int i = 0; i < 2; i++) {
            if (ttsPlayers[i] != null) {
                try {
                    ttsPlayers[i].pause();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void stopAndResetQueue() {
        playGen++;
        prefetchWaveId.incrementAndGet();
        clearFinishWatchdog();
        isSpeaking = false;
        pausedMidUtterance = false;
        currentTask = null;
        pendingCue = null;
        lastSpokenSig = "";
        currentFinishEstMs = 0L;
        stagedSlots[0] = null;
        stagedSlots[1] = null;
        for (int i = 0; i < 2; i++) {
            if (ttsPlayers[i] != null) {
                try {
                    ttsPlayers[i].pause();
                    ttsPlayers[i].stop();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void syncVolumeNow() {
        float vol = Math.max(0f, Math.min(1f, Prefs.getSubAiVoiceVolume(context) / 100f));
        for (int i = 0; i < 2; i++) {
            if (ttsPlayers[i] != null) {
                try {
                    ttsPlayers[i].setVolume(vol);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private CueAudioSpec buildSpecForCueIndex(
            List<SubtitleEngine.Cue> vnCues,
            int idx,
            String voiceMode,
            String transLang,
            double extendSec,
            double videoRate) {
        if (vnCues == null || idx < 0 || idx >= vnCues.size()) return null;
        SubtitleEngine.Cue cue = vnCues.get(idx);
        if (cue == null || cue.isTop || !isSpeakableSubtitleText(cue.text)) return null;
        String trans = (transLang != null && !transLang.isEmpty())
                ? subtitleEngine.getCachedTranslation(transLang, cue.text)
                : "";
        String text = (!trans.isEmpty() ? trans : cue.text).trim();
        if (!isSpeakableSubtitleText(text)) return null;
        String spokenLang = (!trans.isEmpty() && transLang != null && !transLang.isEmpty()) ? transLang : "vi";
        String effVoice = getAdaptiveVoiceForTime(voiceMode, cue.start);
        SubtitleEngine.Cue after = findNextSpeakableCue(vnCues, idx, cue.start);
        double afterStart = after != null ? after.start : -1.0;
        double winSec = computeCueSpeechWindowSec(
                cue.start,
                cue.end,
                extendSec,
                afterStart,
                videoRate
        );
        double srvSpeed = calculateTtsSpeed(text, winSec, effVoice);
        String remoteUrl = buildTtsProxyUrl(text, spokenLang, srvSpeed, effVoice, false);
        String prewarmUrl = buildTtsProxyUrl(text, spokenLang, srvSpeed, effVoice, true);
        if (remoteUrl.isEmpty()) return null;
        return new CueAudioSpec(
                idx, cue, text, spokenLang, effVoice, srvSpeed,
                remoteUrl, prewarmUrl, afterStart, winSec
        );
    }

    private void stageUrlOnStandby(final String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isEmpty() || !Prefs.isSubAiVoiceEnabled(context)) return;
        int standby = activeSlot == 0 ? 1 : 0;
        StagedSlot existing = stagedSlots[standby];
        if (existing != null && remoteUrl.equals(existing.remoteUrl)) {
            return;
        }

        final String cachedPath = localCache.get(remoteUrl);
        if (cachedPath != null) {
            postStageToPlayer(remoteUrl, cachedPath);
            return;
        }

        if (inFlight.contains(remoteUrl)) {
            return;
        }
        inFlight.add(remoteUrl);
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String downloadedPath = downloadToCache(remoteUrl);
                    if (downloadedPath != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                postStageToPlayer(remoteUrl, downloadedPath);
                            }
                        });
                    }
                } finally {
                    inFlight.remove(remoteUrl);
                }
            }
        });
    }

    private void postStageToPlayer(final String remoteUrl, final String localPath) {
        if (localPath == null || localPath.isEmpty() || !Prefs.isSubAiVoiceEnabled(context)) return;
        int targetSlot = activeSlot == 0 ? 1 : 0;
        StagedSlot current = stagedSlots[targetSlot];
        if (current != null && remoteUrl.equals(current.remoteUrl)) return;

        ensureTtsPlayers();
        ExoPlayer player = ttsPlayers[targetSlot];
        if (player == null) return;
        StagedSlot entry = new StagedSlot(remoteUrl, localPath);
        stagedSlots[targetSlot] = entry;
        try {
            player.pause();
            player.stop();
            player.setPlayWhenReady(false);
            player.setMediaSource(new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(localPath)))));
            player.prepare();
        } catch (Exception e) {
            if (stagedSlots[targetSlot] == entry) {
                stagedSlots[targetSlot] = null;
            }
        }
    }

    private void triggerLookaheadIfNeeded(List<SubtitleEngine.Cue> vnCues, double currentTimeSec, int activeAnchorIdx) {
        if (vnCues == null || vnCues.isEmpty() || !Prefs.isSubAiVoiceEnabled(context)) return;
        double ext = Prefs.getSubExtendSec(context);
        double eff = currentTimeSec + Prefs.getSubDelaySec(context);
        int anchorIdx = activeAnchorIdx;
        if (anchorIdx < 0) {
            for (int i = 0; i < vnCues.size(); i++) {
                SubtitleEngine.Cue c = vnCues.get(i);
                if (c != null && !c.isTop && (c.end + ext) >= eff && isSpeakableSubtitleText(c.text)) {
                    anchorIdx = i;
                    break;
                }
            }
        }
        if (anchorIdx < 0) return;

        String transLang = Prefs.getSubTranslateLang(context);
        if (transLang == null) transLang = "";
        int cuesCount = vnCues.size();
        if (anchorIdx == lastLookaheadAnchor
                && cuesCount == lastLookaheadCuesCount
                && transLang.equals(lastLookaheadTransLang)) {
            return;
        }
        lastLookaheadAnchor = anchorIdx;
        lastLookaheadCuesCount = cuesCount;
        lastLookaheadTransLang = transLang;
        scheduleLookahead(vnCues, anchorIdx, transLang);
    }

    private void scheduleLookahead(List<SubtitleEngine.Cue> vnCues, int anchorIdx, String transLang) {
        final int waveId = prefetchWaveId.incrementAndGet();
        final String vMode = Prefs.getSubAiVoiceMode(context);
        final double ext = Prefs.getSubExtendSec(context);
        float rawRate = Prefs.getPlaybackRate(context);
        final double vRate = Math.max(0.5, Math.min(2.0, rawRate > 0f ? rawRate : 1.0));

        int totalCollect = TTS_LOCAL_PREFETCH_COUNT + TTS_SERVER_PREWARM_COUNT;
        final List<CueAudioSpec> specs = new ArrayList<>();
        for (int i = anchorIdx; i < vnCues.size() && specs.size() < totalCollect; i++) {
            CueAudioSpec spec = buildSpecForCueIndex(vnCues, i, vMode, transLang, ext, vRate);
            if (spec != null) {
                specs.add(spec);
            }
        }
        if (specs.isEmpty()) return;

        int localSplit = Math.min(TTS_LOCAL_PREFETCH_COUNT, specs.size());
        final List<CueAudioSpec> localBatch = new ArrayList<>(specs.subList(0, localSplit));
        final List<CueAudioSpec> serverBatch = localSplit < specs.size()
                ? new ArrayList<>(specs.subList(localSplit, specs.size()))
                : new ArrayList<CueAudioSpec>();

        // Tier 1: Urgently download nearest cues and pre-stage next unplayed cue onto standby player
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean stagedFirst = false;
                for (int i = 0; i < localBatch.size(); i++) {
                    if (prefetchWaveId.get() != waveId || !Prefs.isSubAiVoiceEnabled(context)) return;
                    final CueAudioSpec spec = localBatch.get(i);
                    String path = localCache.get(spec.remoteUrl);
                    if (path == null) {
                        path = downloadToCache(spec.remoteUrl);
                    }
                    if (prefetchWaveId.get() != waveId || !Prefs.isSubAiVoiceEnabled(context)) return;
                    if (path != null && !stagedFirst) {
                        QueuedTtsTask cur = currentTask;
                        boolean isActive = cur != null && spec.remoteUrl.equals(cur.remoteUrl);
                        if (!isActive) {
                            stagedFirst = true;
                            final String readyPath = path;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (prefetchWaveId.get() == waveId) {
                                        postStageToPlayer(spec.remoteUrl, readyPath);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        });

        // Tier 2: Gently pre-warm server cache_tts_proxy for upcoming cues (#13..#42) via ?prewarm=1 (204 No Content)
        if (!serverBatch.isEmpty()) {
            prewarmExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < serverBatch.size(); i++) {
                        if (prefetchWaveId.get() != waveId || !Prefs.isSubAiVoiceEnabled(context)) return;
                        CueAudioSpec spec = serverBatch.get(i);
                        if (localCache.containsKey(spec.remoteUrl) || serverPrewarmed.contains(spec.remoteUrl)) {
                            continue;
                        }
                        if (serverPrewarmed.size() > TTS_SERVER_PREWARM_TRACK_MAX) {
                            Iterator<String> it = serverPrewarmed.iterator();
                            int rem = 0;
                            while (it.hasNext() && rem < 200) {
                                it.next();
                                it.remove();
                                rem++;
                            }
                        }
                        serverPrewarmed.add(spec.remoteUrl);
                        HttpURLConnection conn = null;
                        try {
                            conn = (HttpURLConnection) new URL(spec.prewarmUrl).openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(6000);
                            conn.setReadTimeout(8000);
                            conn.setRequestProperty("User-Agent", "DVACastTV/4.7");
                            int code = conn.getResponseCode();
                            if (code == 429) {
                                serverPrewarmed.remove(spec.remoteUrl);
                                return;
                            }
                            if (code < 200 || code >= 300) {
                                serverPrewarmed.remove(spec.remoteUrl);
                            }
                        } catch (Exception e) {
                            serverPrewarmed.remove(spec.remoteUrl);
                        } finally {
                            if (conn != null) conn.disconnect();
                        }
                        try {
                            Thread.sleep(90L);
                        } catch (InterruptedException ignored) {
                            return;
                        }
                    }
                }
            });
        }
    }

    public void updateTick(
            double currentTimeSec,
            boolean isPlaying,
            boolean isMuted,
            List<SubtitleEngine.Cue> vnCues,
            SubtitleEngine.Cue activeCue,
            int activeIdx) {
        this.latestVnCues = vnCues;
        if (hasPrevVideoTime && Math.abs(currentTimeSec - prevVideoTimeSec) > 1.8) {
            lastLookaheadAnchor = -2;
            stopAndResetQueue();
        }
        prevVideoTimeSec = currentTimeSec;
        hasPrevVideoTime = true;
        lastVideoTimeSec = currentTimeSec;

        lastExtendSec = Prefs.getSubExtendSec(context);
        float rawRate = Prefs.getPlaybackRate(context);
        lastVideoPlaybackRate = Math.max(0.5, Math.min(2.0, rawRate > 0f ? rawRate : 1.0));

        String voiceMode = Prefs.getSubAiVoiceMode(context);
        if (!lastVoiceMode.isEmpty() && !lastVoiceMode.equals(voiceMode)) {
            lastLookaheadAnchor = -2;
            stopAndResetQueue();
        }
        lastVoiceMode = voiceMode != null ? voiceMode : "";

        boolean enabled = Prefs.isSubAiVoiceEnabled(context);
        canPlayAudioNow = enabled && isPlaying && !isMuted;

        if (!enabled || isMuted) {
            stopAndResetQueue();
            return;
        }
        if (!isPlaying) {
            pauseNow();
            triggerLookaheadIfNeeded(vnCues, currentTimeSec, -1);
            return;
        }

        if (isSpeaking) {
            syncVolumeNow();
        }

        if (pausedMidUtterance && isSpeaking) {
            pausedMidUtterance = false;
            ExoPlayer activePlayer = ttsPlayers[activeSlot];
            if (activePlayer != null) {
                try {
                    syncVolumeNow();
                    activePlayer.setPlayWhenReady(true);
                    activePlayer.play();
                    long durMs = activePlayer.getDuration();
                    long posMs = Math.max(0L, activePlayer.getCurrentPosition());
                    float speed = 1.0f;
                    try {
                        speed = Math.max(0.5f, activePlayer.getPlaybackParameters().speed);
                    } catch (Exception ignored) {
                    }
                    long remMs = (durMs != C.TIME_UNSET && durMs > 200L)
                            ? Math.max(250L, Math.round(((Math.max(0L, durMs - posMs)) / (double) speed)))
                            : Math.max(350L, currentFinishEstMs - System.currentTimeMillis());
                    currentFinishEstMs = System.currentTimeMillis() + remMs;
                    scheduleFinishWatchdog(remMs + 250L, playGen);
                } catch (Exception e) {
                    completeAndFlushPending();
                }
            }
        }

        // Lookahead 0.22s for narration start so first spoken syllable aligns smoothly with visual subtitle
        double voiceEff = currentTimeSec + Prefs.getSubDelaySec(context) + TTS_START_LEAD_SEC;
        SubtitleEngine.Cue voiceCue = null;
        int voiceCueIdx = -1;
        if (vnCues != null && !vnCues.isEmpty()) {
            for (int i = 0; i < vnCues.size(); i++) {
                SubtitleEngine.Cue c = vnCues.get(i);
                if (c != null && !c.isTop && c.start <= voiceEff && voiceEff <= (c.end + lastExtendSec) && isSpeakableSubtitleText(c.text)) {
                    voiceCue = c;
                    voiceCueIdx = i;
                    break;
                }
            }
            if (voiceCue == null) {
                for (int i = 0; i < vnCues.size(); i++) {
                    SubtitleEngine.Cue c = vnCues.get(i);
                    if (c != null && c.isTop && c.start <= voiceEff && voiceEff <= (c.end + lastExtendSec) && isSpeakableSubtitleText(c.text)) {
                        voiceCue = c;
                        voiceCueIdx = i;
                        break;
                    }
                }
            }
        } else if (activeCue != null && isSpeakableSubtitleText(activeCue.text)) {
            voiceCue = activeCue;
            voiceCueIdx = activeIdx;
        }

        triggerLookaheadIfNeeded(vnCues, currentTimeSec, voiceCueIdx);

        // Silent gap between cues: never cut off an utterance already speaking
        if (voiceCue == null) {
            return;
        }

        String transLang = Prefs.getSubTranslateLang(context);
        String cachedTrans = !transLang.isEmpty()
                ? subtitleEngine.getCachedTranslation(transLang, voiceCue.text)
                : "";
        String spokenText = (!cachedTrans.isEmpty() ? cachedTrans : voiceCue.text).trim();
        String spokenLang = (!cachedTrans.isEmpty() && !transLang.isEmpty()) ? transLang : "vi";

        if (!isSpeakableSubtitleText(spokenText)) {
            return;
        }

        final String sig = episodeSig + "|" + lastVoiceMode + "|" + voiceCue.start + "|" + spokenText;
        if (sig.equals(lastSpokenSig)) {
            return;
        }
        lastSpokenSig = sig;

        String effVoice = getAdaptiveVoiceForTime(voiceMode, voiceCue.start);
        SubtitleEngine.Cue nextCue = findNextSpeakableCue(vnCues, voiceCueIdx, voiceCue.start);
        double nextCueStart = nextCue != null ? nextCue.start : -1.0;
        double speechWindowSec = computeCueSpeechWindowSec(
                voiceCue.start,
                voiceCue.end,
                lastExtendSec,
                nextCueStart,
                lastVideoPlaybackRate
        );
        double serverSpeed = calculateTtsSpeed(spokenText, speechWindowSec, effVoice);
        String remoteUrl = buildTtsProxyUrl(spokenText, spokenLang, serverSpeed, effVoice, false);

        QueuedTtsTask task = new QueuedTtsTask(
                sig,
                voiceCueIdx,
                spokenText,
                spokenLang,
                effVoice,
                serverSpeed,
                remoteUrl,
                voiceCue.start,
                voiceCue.end,
                nextCueStart,
                speechWindowSec
        );

        // Warm active cue locally if not yet cached
        preloadUrl(remoteUrl);

        if (!isSpeaking) {
            startTaskNow(task);
        } else {
            ExoPlayer activePlayer = ttsPlayers[activeSlot];
            long remainingMs = Math.max(0L, currentFinishEstMs - System.currentTimeMillis());
            if (activePlayer != null) {
                try {
                    long durMs = activePlayer.getDuration();
                    long posMs = Math.max(0L, activePlayer.getCurrentPosition());
                    float spd = Math.max(0.5f, activePlayer.getPlaybackParameters().speed);
                    if (durMs != C.TIME_UNSET && durMs > 200L) {
                        remainingMs = Math.max(0L, Math.round(((Math.max(0L, durMs - posMs)) / (double) spd)));
                    }
                } catch (Exception ignored) {
                }
            }

            if (remainingMs <= 1800L) {
                // Let current sentence finish cleanly without cutoff, then speak this cue immediately
                pendingCue = task;
            } else {
                // Extreme lag guard: jump directly to keep narration synchronized
                pendingCue = null;
                startTaskNow(task);
            }
        }
    }

    private void startTaskNow(final QueuedTtsTask task) {
        final int myGen = ++playGen;
        clearFinishWatchdog();
        isSpeaking = true;
        pausedMidUtterance = false;
        currentTask = task;

        double estAudioDurSec = Math.max(
                0.55,
                estimateNaturalTtsDurationSec(task.spokenText) / Math.max(0.5, task.serverSpeed)
        );
        double initialWallSec = computeAvailableWallSecForTask(task);
        final double initialLocalRate = computeLocalTtsPlaybackRate(
                estAudioDurSec,
                initialWallSec,
                task.effVoice,
                task.serverSpeed,
                lastVideoPlaybackRate
        );
        final long estWallMs = Math.round((estAudioDurSec / Math.max(0.5, initialLocalRate)) * 1000.0);
        currentFinishEstMs = System.currentTimeMillis() + estWallMs;

        ensureTtsPlayers();
        int standbySlot = activeSlot == 0 ? 1 : 0;
        int[] checkSlots = new int[]{standbySlot, activeSlot};
        for (int slot : checkSlots) {
            StagedSlot staged = stagedSlots[slot];
            if (staged != null && task.remoteUrl.equals(staged.remoteUrl) && ttsPlayers[slot] != null) {
                stagedSlots[slot] = null;
                playPrestagedSlot(slot, task, myGen, initialLocalRate, estWallMs);
                return;
            }
        }

        // Fast memory cache hit (avoid synchronous File.length I/O latency)
        String cached = localCache.get(task.remoteUrl);
        if (cached != null) {
            playUriForTask(standbySlot, Uri.fromFile(new File(cached)), task, myGen, initialLocalRate, estWallMs);
            return;
        }

        inFlight.add(task.remoteUrl);
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String path = downloadToCache(task.remoteUrl);
                    final Uri targetUri = path != null
                            ? Uri.fromFile(new File(path))
                            : Uri.parse(task.remoteUrl);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (playGen != myGen || !canPlayAudioNow) {
                                if (playGen == myGen) {
                                    isSpeaking = false;
                                    currentTask = null;
                                }
                                return;
                            }
                            int target = activeSlot == 0 ? 1 : 0;
                            playUriForTask(target, targetUri, task, myGen, initialLocalRate, estWallMs);
                        }
                    });
                } finally {
                    inFlight.remove(task.remoteUrl);
                }
            }
        });
    }

    private void preStageNextCueAfter(QueuedTtsTask task) {
        if (task == null || task.cueIndex < 0 || latestVnCues == null) return;
        String transLang = Prefs.getSubTranslateLang(context);
        if (transLang == null) transLang = "";
        for (int i = task.cueIndex + 1; i < latestVnCues.size(); i++) {
            CueAudioSpec nextSpec = buildSpecForCueIndex(
                    latestVnCues, i, lastVoiceMode, transLang, lastExtendSec, lastVideoPlaybackRate);
            if (nextSpec != null) {
                stageUrlOnStandby(nextSpec.remoteUrl);
                break;
            }
        }
    }

    private void playPrestagedSlot(
            int targetSlot,
            QueuedTtsTask task,
            int expectedGen,
            double initialLocalRate,
            long estWallMs) {
        if (playGen != expectedGen || !canPlayAudioNow) {
            if (playGen == expectedGen) {
                isSpeaking = false;
                currentTask = null;
            }
            return;
        }
        try {
            int otherSlot = targetSlot == 0 ? 1 : 0;
            if (ttsPlayers[otherSlot] != null) {
                try {
                    ttsPlayers[otherSlot].pause();
                } catch (Exception ignored) {
                }
            }
            activeSlot = targetSlot;
            ExoPlayer player = ttsPlayers[targetSlot];
            float vol = Math.max(0f, Math.min(1f, Prefs.getSubAiVoiceVolume(context) / 100f));
            player.setPlaybackParameters(new PlaybackParameters((float) initialLocalRate, 1.0f));
            player.setVolume(vol);
            player.setPlayWhenReady(true);
            player.play();

            long syncDurMs = player.getDuration();
            if (syncDurMs != C.TIME_UNSET && syncDurMs > 200L) {
                calibrateRateAndTimer(player, syncDurMs / 1000.0, task, expectedGen);
            } else {
                currentFinishEstMs = System.currentTimeMillis() + estWallMs;
                scheduleFinishWatchdog(estWallMs + 350L, expectedGen);
            }
            preStageNextCueAfter(task);
        } catch (Exception e) {
            if (playGen == expectedGen) {
                completeAndFlushPending();
            }
        }
    }

    private void playUriForTask(
            int targetSlot,
            Uri uri,
            QueuedTtsTask task,
            int expectedGen,
            double initialLocalRate,
            long estWallMs) {
        if (playGen != expectedGen || !canPlayAudioNow) {
            if (playGen == expectedGen) {
                isSpeaking = false;
                currentTask = null;
            }
            return;
        }
        try {
            ensureTtsPlayers();
            int otherSlot = targetSlot == 0 ? 1 : 0;
            if (ttsPlayers[otherSlot] != null) {
                try {
                    ttsPlayers[otherSlot].pause();
                } catch (Exception ignored) {
                }
            }
            stagedSlots[targetSlot] = null;
            activeSlot = targetSlot;
            ExoPlayer player = ttsPlayers[targetSlot];
            float vol = Math.max(0f, Math.min(1f, Prefs.getSubAiVoiceVolume(context) / 100f));
            player.stop();
            player.setMediaSource(new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(uri)));
            player.setPlaybackParameters(new PlaybackParameters((float) initialLocalRate, 1.0f));
            player.setVolume(vol);
            player.setPlayWhenReady(true);
            player.prepare();

            long syncDurMs = player.getDuration();
            if (syncDurMs != C.TIME_UNSET && syncDurMs > 200L) {
                calibrateRateAndTimer(player, syncDurMs / 1000.0, task, expectedGen);
            } else {
                currentFinishEstMs = System.currentTimeMillis() + estWallMs;
                scheduleFinishWatchdog(estWallMs + 350L, expectedGen);
            }
            preStageNextCueAfter(task);
        } catch (Exception e) {
            if (playGen == expectedGen) {
                completeAndFlushPending();
            }
        }
    }

    private void preloadUrl(final String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isEmpty()) return;
        if (localCache.containsKey(remoteUrl) || inFlight.contains(remoteUrl)) return;
        inFlight.add(remoteUrl);
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadToCache(remoteUrl);
                } finally {
                    inFlight.remove(remoteUrl);
                }
            }
        });
    }

    private String downloadToCache(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isEmpty()) return null;
        Object lock = urlLocks.get(remoteUrl);
        if (lock == null) {
            Object created = new Object();
            Object prev = urlLocks.putIfAbsent(remoteUrl, created);
            lock = prev != null ? prev : created;
        }
        synchronized (lock) {
            String existing = localCache.get(remoteUrl);
            if (existing != null) {
                File f = new File(existing);
                if (f.exists() && f.length() > 128L) {
                    return existing;
                }
                localCache.remove(remoteUrl);
            }

            HttpURLConnection conn = null;
            try {
                File dir = new File(context.getCacheDir(), "dva_tts");
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
                pruneCacheIfNeeded(dir);

                conn = (HttpURLConnection) new URL(remoteUrl).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "DVACastTV/4.7");
                if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return null;

                File outFile = new File(dir, "tts_" + Math.abs(remoteUrl.hashCode()) + "_" + System.currentTimeMillis() + ".mp3");
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                int r;
                long total = 0L;
                while ((r = is.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                    total += r;
                }
                fos.flush();
                fos.close();
                is.close();
                if (total <= 128L) {
                    //noinspection ResultOfMethodCallIgnored
                    outFile.delete();
                    return null;
                }
                String absPath = outFile.getAbsolutePath();
                localCache.put(remoteUrl, absPath);
                return absPath;
            } catch (Exception e) {
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private void pruneCacheIfNeeded(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null || files.length < TTS_LOCAL_CACHE_MAX) return;
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(a.lastModified(), b.lastModified());
                }
            });
            for (int i = 0; i < files.length - 160; i++) {
                String delPath = files[i].getAbsolutePath();
                //noinspection ResultOfMethodCallIgnored
                files[i].delete();
                Iterator<Map.Entry<String, String>> it = localCache.entrySet().iterator();
                while (it.hasNext()) {
                    if (delPath.equals(it.next().getValue())) {
                        it.remove();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static String getAdaptiveVoiceForTime(String mode, double timeSec) {
        if (!"adaptive".equals(mode) && mode != null && !mode.isEmpty()) {
            return mode;
        }
        int cycle = ((int) Math.floor(Math.max(0.0, timeSec) / 600.0)) % 3;
        if (cycle == 0) return "hoaimy";
        if (cycle == 1) return "namminh";
        return "google";
    }

    public static double estimateNaturalTtsDurationSec(String cleanText) {
        if (cleanText == null) return 0.75;
        String normalized = cleanText.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return 0.75;

        String[] parts = normalized.split(" ");
        int words = 0;
        for (String p : parts) {
            if (!p.isEmpty()) words++;
        }
        int chars = normalized.length();
        int punctuationPauses = 0;
        for (int i = 0; i < chars; i++) {
            char ch = normalized.charAt(i);
            if (ch == ',' || ch == ';' || ch == ':' || ch == '.' || ch == '!' || ch == '?' || ch == '\u2026') {
                punctuationPauses++;
            }
        }

        double byWords = words * 0.285;
        double byChars = chars / 14.5;
        return Math.max(
                0.75,
                Math.max(byWords, byChars) * 0.92 + Math.min(punctuationPauses, 4) * 0.14 + 0.25
        );
    }

    public static SubtitleEngine.Cue findNextSpeakableCue(
            List<SubtitleEngine.Cue> cues,
            int currentIndex,
            double afterStartSec) {
        if (cues == null || cues.isEmpty()) return null;
        int startIdx = currentIndex >= 0 ? currentIndex + 1 : 0;
        for (int i = startIdx; i < cues.size(); i++) {
            SubtitleEngine.Cue c = cues.get(i);
            if (c == null || c.isTop || c.text == null) continue;
            if (currentIndex < 0 && c.start <= afterStartSec) continue;
            if (isSpeakableSubtitleText(c.text)) {
                return c;
            }
        }
        return null;
    }

    public static double computeCueSpeechWindowSec(
            double cueStart,
            double cueEnd,
            double extendSec,
            double nextCueStart,
            double videoPlaybackRate) {
        double cueDur = Math.max(0.4, (cueEnd + extendSec) - cueStart);
        double maxBorrowSec = 1.8;
        double breathMarginSec = 0.12;
        double usableVideoSec;
        if (nextCueStart > cueStart) {
            double roomToNext = Math.max(0.4, nextCueStart - cueStart - breathMarginSec);
            usableVideoSec = Math.min(cueDur + maxBorrowSec, Math.max(cueDur * 0.85, roomToNext));
        } else {
            usableVideoSec = cueDur + maxBorrowSec;
        }
        double videoRate = Math.max(0.5, Math.min(2.0, videoPlaybackRate > 0.0 ? videoPlaybackRate : 1.0));
        return usableVideoSec / videoRate;
    }

    public static double calculateTtsSpeed(String cleanText, double speechWindowSec, String effVoice) {
        if (cleanText == null || cleanText.trim().isEmpty() || speechWindowSec <= 0.0) return 1.0;

        // Google voice returns clean native high-bitrate MP3 only at speed=1;
        // gentle speed matching is performed locally with pitch preserved.
        if ("google".equals(effVoice)) {
            return 1.0;
        }

        double safeWindow = Math.max(1.4, speechWindowSec);
        double estDur = estimateNaturalTtsDurationSec(cleanText);
        double req = estDur / safeWindow;

        if (cleanText.trim().length() <= 18) {
            return req > 1.22 ? 1.1 : 1.0;
        }

        if (req <= 1.06) return 1.0;
        if (req <= 1.18) return 1.1;
        if (req <= 1.32) return 1.2;
        return 1.25;
    }

    public static double computeLocalTtsPlaybackRate(
            double actualAudioDurSec,
            double availableWallSec,
            String effVoice,
            double serverSpeed,
            double videoPlaybackRate) {
        double speechDur = Math.max(0.45, actualAudioDurSec - 0.16);
        double safeWindow = Math.max(1.3, availableWallSec);
        if (speechDur <= safeWindow) return 1.0;

        double rawRatio = speechDur / safeWindow;
        double videoRate = Math.max(0.5, Math.min(2.0, videoPlaybackRate > 0.0 ? videoPlaybackRate : 1.0));
        double videoScale = videoRate > 1.05 ? Math.min(1.18, 1.0 + (videoRate - 1.0) * 0.45) : 1.0;
        boolean isGoogle = "google".equals(effVoice);
        double baseMax = isGoogle ? 1.35 : 1.20;
        double hardCeiling = isGoogle ? 1.50 : 1.35;
        double maxLocal = Math.min(hardCeiling, baseMax * videoScale);

        return Math.round(Math.max(1.0, Math.min(maxLocal, rawRatio)) * 100.0) / 100.0;
    }

    public static String buildTtsProxyUrl(String text, String lang, double speed, String voiceGd) {
        return buildTtsProxyUrl(text, lang, speed, voiceGd, false);
    }

    public static String buildTtsProxyUrl(String text, String lang, double speed, String voiceGd, boolean prewarm) {
        try {
            String speedStr = (speed == Math.floor(speed))
                    ? String.valueOf((int) speed)
                    : String.format(Locale.US, "%s", speed);
            String url = "https://handle.dangvietanh.com/tts_proxy?text="
                    + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
                    + "&tl=" + URLEncoder.encode(lang != null && !lang.isEmpty() ? lang : "vi", "UTF-8").replace("+", "%20")
                    + "&speed=" + speedStr
                    + "&voice_gd=" + URLEncoder.encode(voiceGd, "UTF-8").replace("+", "%20");
            return prewarm ? (url + "&prewarm=1") : url;
        } catch (Exception e) {
            return "";
        }
    }

    public void release() {
        playGen++;
        prefetchWaveId.incrementAndGet();
        clearFinishWatchdog();
        isSpeaking = false;
        pausedMidUtterance = false;
        currentTask = null;
        pendingCue = null;
        lastSpokenSig = "";
        currentFinishEstMs = 0L;
        stagedSlots[0] = null;
        stagedSlots[1] = null;
        for (int i = 0; i < 2; i++) {
            if (ttsPlayers[i] != null) {
                try {
                    ttsPlayers[i].stop();
                    ttsPlayers[i].release();
                } catch (Exception ignored) {
                }
                ttsPlayers[i] = null;
            }
        }
    }
}
