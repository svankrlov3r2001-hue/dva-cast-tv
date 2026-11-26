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

/**
 * AI Voice Dubbing / Thuyết minh engine using handle.dangvietanh.com/tts_proxy
 * mixed cleanly over movie audio via a secondary audio-only ExoPlayer.
 */
public final class DubbingTtsEngine {

    private static final class QueuedTtsTask {
        final String cueSig;
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
    private final Map<String, String> localCache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Object> urlLocks = new ConcurrentHashMap<>();

    private ExoPlayer ttsPlayer;
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

    private void ensureTtsPlayer() {
        if (ttsPlayer != null) return;
        ttsPlayer = new ExoPlayer.Builder(context).build();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();
        // handleAudioFocus = false so it mixes seamlessly with movie audio
        ttsPlayer.setAudioAttributes(attrs, false);
        ttsPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    if (isSpeaking && currentTask != null && ttsPlayer != null) {
                        long durMs = ttsPlayer.getDuration();
                        if (durMs != C.TIME_UNSET && durMs > 200L) {
                            calibrateRateAndTimer(durMs / 1000.0, currentTask, playGen);
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    completeAndFlushPending();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                clearFinishWatchdog();
                isSpeaking = false;
                pausedMidUtterance = false;
                currentTask = null;
                pendingCue = null;
                lastSpokenSig = "";
                currentFinishEstMs = 0L;
            }
        });
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

    private void calibrateRateAndTimer(double actualAudioDurSec, QueuedTtsTask task, int expectedGen) {
        if (actualAudioDurSec <= 0.2 || ttsPlayer == null || playGen != expectedGen) return;
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
            ttsPlayer.setPlaybackParameters(new PlaybackParameters((float) refinedRate, 1.0f));
        } catch (Exception ignored) {
        }

        double playerPosSec = 0.0;
        try {
            long posMs = ttsPlayer.getCurrentPosition();
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
        stopAndResetQueue();
    }

    public void clearCacheAndReset() {
        hasPrevVideoTime = false;
        prevVideoTimeSec = 0.0;
        localCache.clear();
        inFlight.clear();
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
        if (ttsPlayer != null) {
            try {
                ttsPlayer.pause();
            } catch (Exception ignored) {
            }
        }
    }

    public void stopAndResetQueue() {
        playGen++;
        clearFinishWatchdog();
        isSpeaking = false;
        pausedMidUtterance = false;
        currentTask = null;
        pendingCue = null;
        lastSpokenSig = "";
        currentFinishEstMs = 0L;
        if (ttsPlayer != null) {
            try {
                ttsPlayer.pause();
                ttsPlayer.stop();
            } catch (Exception ignored) {
            }
        }
    }

    public void syncVolumeNow() {
        if (ttsPlayer != null) {
            try {
                float vol = Math.max(0f, Math.min(1f, Prefs.getSubAiVoiceVolume(context) / 100f));
                ttsPlayer.setVolume(vol);
            } catch (Exception ignored) {
            }
        }
    }

    public void updateTick(
            double currentTimeSec,
            boolean isPlaying,
            boolean isMuted,
            List<SubtitleEngine.Cue> vnCues,
            SubtitleEngine.Cue activeCue,
            int activeIdx) {
        if (hasPrevVideoTime && Math.abs(currentTimeSec - prevVideoTimeSec) > 1.8) {
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
            return;
        }

        if (ttsPlayer != null && isSpeaking) {
            syncVolumeNow();
        }

        if (pausedMidUtterance && isSpeaking && ttsPlayer != null) {
            pausedMidUtterance = false;
            try {
                syncVolumeNow();
                ttsPlayer.setPlayWhenReady(true);
                ttsPlayer.play();
                long durMs = ttsPlayer.getDuration();
                long posMs = Math.max(0L, ttsPlayer.getCurrentPosition());
                float speed = 1.0f;
                try {
                    speed = Math.max(0.5f, ttsPlayer.getPlaybackParameters().speed);
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

        // Silent gap between cues: never cut off an utterance already speaking
        if (activeCue == null) {
            return;
        }

        String transLang = Prefs.getSubTranslateLang(context);
        String cachedTrans = !transLang.isEmpty()
                ? subtitleEngine.getCachedTranslation(transLang, activeCue.text)
                : "";
        String spokenText = (!cachedTrans.isEmpty() ? cachedTrans : activeCue.text).trim();
        String spokenLang = (!cachedTrans.isEmpty() && !transLang.isEmpty()) ? transLang : "vi";

        if (spokenText.isEmpty() || spokenText.contains("♪")) {
            return;
        }

        final String sig = episodeSig + "|" + lastVoiceMode + "|" + activeCue.start + "|" + spokenText;
        if (sig.equals(lastSpokenSig)) {
            return;
        }
        lastSpokenSig = sig;

        String effVoice = getAdaptiveVoiceForTime(voiceMode, activeCue.start);
        SubtitleEngine.Cue nextCue = findNextSpeakableCue(vnCues, activeIdx, activeCue.start);
        double nextCueStart = nextCue != null ? nextCue.start : -1.0;
        double speechWindowSec = computeCueSpeechWindowSec(
                activeCue.start,
                activeCue.end,
                lastExtendSec,
                nextCueStart,
                lastVideoPlaybackRate
        );
        double serverSpeed = calculateTtsSpeed(spokenText, speechWindowSec, effVoice);
        String remoteUrl = buildTtsProxyUrl(spokenText, spokenLang, serverSpeed, effVoice);

        QueuedTtsTask task = new QueuedTtsTask(
                sig,
                spokenText,
                spokenLang,
                effVoice,
                serverSpeed,
                remoteUrl,
                activeCue.start,
                activeCue.end,
                nextCueStart,
                speechWindowSec
        );

        // Warm active cue and preload next 6 speakable cues
        preloadUrl(remoteUrl);
        if (vnCues != null && !vnCues.isEmpty()) {
            int startScan = activeIdx >= 0 ? activeIdx + 1 : 0;
            int preloaded = 0;
            for (int i = startScan; i < vnCues.size() && preloaded < 6; i++) {
                SubtitleEngine.Cue nxt = vnCues.get(i);
                if (nxt == null || nxt.isTop || nxt.text == null) continue;
                if (activeIdx < 0 && nxt.start <= activeCue.start) continue;
                String nxtRaw = nxt.text.trim();
                if (nxtRaw.isEmpty() || nxtRaw.contains("♪")) continue;
                String nxtTrans = !transLang.isEmpty()
                        ? subtitleEngine.getCachedTranslation(transLang, nxt.text)
                        : "";
                String nxtText = (!nxtTrans.isEmpty() ? nxtTrans : nxt.text).trim();
                if (nxtText.isEmpty()) continue;
                String nxtLang = (!nxtTrans.isEmpty() && !transLang.isEmpty()) ? transLang : "vi";
                String nxtVoice = getAdaptiveVoiceForTime(voiceMode, nxt.start);
                SubtitleEngine.Cue afterNxt = findNextSpeakableCue(vnCues, i, nxt.start);
                double afterNxtStart = afterNxt != null ? afterNxt.start : -1.0;
                double nxtWindow = computeCueSpeechWindowSec(
                        nxt.start,
                        nxt.end,
                        lastExtendSec,
                        afterNxtStart,
                        lastVideoPlaybackRate
                );
                double nxtSpeed = calculateTtsSpeed(nxtText, nxtWindow, nxtVoice);
                String nxtUrl = buildTtsProxyUrl(nxtText, nxtLang, nxtSpeed, nxtVoice);
                preloadUrl(nxtUrl);
                preloaded++;
            }
        }

        if (!isSpeaking) {
            startTaskNow(task);
        } else {
            long remainingMs = Math.max(0L, currentFinishEstMs - System.currentTimeMillis());
            if (ttsPlayer != null) {
                try {
                    long durMs = ttsPlayer.getDuration();
                    long posMs = Math.max(0L, ttsPlayer.getCurrentPosition());
                    float spd = Math.max(0.5f, ttsPlayer.getPlaybackParameters().speed);
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

        String cached = localCache.get(task.remoteUrl);
        if (cached != null) {
            File cachedFile = new File(cached);
            if (cachedFile.exists() && cachedFile.length() > 128L) {
                playUriForTask(Uri.fromFile(cachedFile), task, myGen, initialLocalRate, estWallMs);
                return;
            }
            localCache.remove(task.remoteUrl);
        }

        inFlight.add(task.remoteUrl);
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String path = downloadToCache(task.remoteUrl);
                    final Uri targetUri = (path != null && new File(path).exists())
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
                            playUriForTask(targetUri, task, myGen, initialLocalRate, estWallMs);
                        }
                    });
                } finally {
                    inFlight.remove(task.remoteUrl);
                }
            }
        });
    }

    private void playUriForTask(
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
            ensureTtsPlayer();
            float vol = Math.max(0f, Math.min(1f, Prefs.getSubAiVoiceVolume(context) / 100f));
            ttsPlayer.stop();
            ttsPlayer.setMediaSource(new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(uri)));
            ttsPlayer.setPlaybackParameters(new PlaybackParameters((float) initialLocalRate, 1.0f));
            ttsPlayer.setVolume(vol);
            ttsPlayer.setPlayWhenReady(true);
            ttsPlayer.prepare();

            long syncDurMs = ttsPlayer.getDuration();
            if (syncDurMs != C.TIME_UNSET && syncDurMs > 200L) {
                calibrateRateAndTimer(syncDurMs / 1000.0, task, expectedGen);
            } else {
                currentFinishEstMs = System.currentTimeMillis() + estWallMs;
                scheduleFinishWatchdog(estWallMs + 350L, expectedGen);
            }
        } catch (Exception e) {
            if (playGen == expectedGen) {
                completeAndFlushPending();
            }
        }
    }

    private void preloadUrl(final String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isEmpty()) return;
        String cached = localCache.get(remoteUrl);
        if (cached != null) {
            if (new File(cached).exists()) return;
            localCache.remove(remoteUrl);
        }
        if (inFlight.contains(remoteUrl)) return;
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
                conn.setRequestProperty("User-Agent", "DVACastTV/4.0");
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
            if (files == null || files.length < 120) return;
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(a.lastModified(), b.lastModified());
                }
            });
            for (int i = 0; i < files.length - 90; i++) {
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
            String trimmed = c.text.trim();
            if (!trimmed.isEmpty() && !trimmed.contains("♪")) {
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
        try {
            String speedStr = (speed == Math.floor(speed))
                    ? String.valueOf((int) speed)
                    : String.format(Locale.US, "%s", speed);
            return "https://handle.dangvietanh.com/tts_proxy?text="
                    + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
                    + "&tl=" + URLEncoder.encode(lang != null && !lang.isEmpty() ? lang : "vi", "UTF-8").replace("+", "%20")
                    + "&speed=" + speedStr
                    + "&voice_gd=" + URLEncoder.encode(voiceGd, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    public void release() {
        playGen++;
        clearFinishWatchdog();
        isSpeaking = false;
        pausedMidUtterance = false;
        currentTask = null;
        pendingCue = null;
        lastSpokenSig = "";
        currentFinishEstMs = 0L;
        if (ttsPlayer != null) {
            try {
                ttsPlayer.stop();
                ttsPlayer.release();
            } catch (Exception ignored) {
            }
            ttsPlayer = null;
        }
    }
}
