package com.dangvietanh.cast.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
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
import java.util.List;
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

    private final Context context;
    private final SubtitleEngine subtitleEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService dlExecutor = Executors.newFixedThreadPool(2);
    private final Map<String, String> localCache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private ExoPlayer ttsPlayer;
    private String lastSpokenSig = "";
    private String episodeSig = "";

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
            public void onPlayerError(PlaybackException error) {
                lastSpokenSig = "";
            }
        });
    }

    public void setEpisodeSignature(String sig) {
        this.episodeSig = sig != null ? sig : "";
        this.lastSpokenSig = "";
        pauseNow();
    }

    public void clearCacheAndReset() {
        lastSpokenSig = "";
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
        pauseNow();
    }

    public void pauseNow() {
        if (ttsPlayer != null) {
            try {
                ttsPlayer.pause();
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
        boolean enabled = Prefs.isSubAiVoiceEnabled(context);
        if (!enabled || !isPlaying || isMuted || activeCue == null) {
            pauseNow();
            if (!enabled || !isPlaying || isMuted || activeCue == null) {
                lastSpokenSig = "";
            }
            return;
        }

        String transLang = Prefs.getSubTranslateLang(context);
        String cachedTrans = !transLang.isEmpty()
                ? subtitleEngine.getCachedTranslation(transLang, activeCue.text)
                : "";
        String spokenText = (!cachedTrans.isEmpty() ? cachedTrans : activeCue.text).trim();
        String spokenLang = (!cachedTrans.isEmpty() && !transLang.isEmpty()) ? transLang : "vi";

        if (spokenText.isEmpty() || spokenText.contains("♪")) {
            pauseNow();
            return;
        }

        final String sig = episodeSig + "|" + activeCue.start + "|" + spokenText;
        if (sig.equals(lastSpokenSig)) {
            return;
        }
        lastSpokenSig = sig;

        String voiceMode = Prefs.getSubAiVoiceMode(context);
        String effVoice = getAdaptiveVoiceForTime(voiceMode, activeCue.start);
        double durSec = Math.max(0.4, activeCue.end - activeCue.start);
        double speed = calculateTtsSpeed(spokenText, durSec, effVoice);
        final String remoteUrl = buildTtsProxyUrl(spokenText, spokenLang, speed, effVoice);

        ensureAndPlay(remoteUrl, sig);

        // Preload next 4 cues quietly in background
        if (vnCues != null && activeIdx >= 0) {
            int max = Math.min(vnCues.size() - 1, activeIdx + 4);
            for (int i = activeIdx + 1; i <= max; i++) {
                SubtitleEngine.Cue nxt = vnCues.get(i);
                if (nxt == null || nxt.text.isEmpty() || nxt.text.contains("♪")) continue;
                String nxtTrans = !transLang.isEmpty()
                        ? subtitleEngine.getCachedTranslation(transLang, nxt.text)
                        : "";
                String nxtText = (!nxtTrans.isEmpty() ? nxtTrans : nxt.text).trim();
                String nxtLang = (!nxtTrans.isEmpty() && !transLang.isEmpty()) ? transLang : "vi";
                String nxtVoice = getAdaptiveVoiceForTime(voiceMode, nxt.start);
                double nxtSpeed = calculateTtsSpeed(nxtText, Math.max(0.4, nxt.end - nxt.start), nxtVoice);
                String nxtUrl = buildTtsProxyUrl(nxtText, nxtLang, nxtSpeed, nxtVoice);
                preloadUrl(nxtUrl);
            }
        }
    }

    private void preloadUrl(final String remoteUrl) {
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

    private void ensureAndPlay(final String remoteUrl, final String expectedSig) {
        String cached = localCache.get(remoteUrl);
        if (cached != null && new File(cached).exists()) {
            playUriOnMain(Uri.fromFile(new File(cached)), expectedSig);
            return;
        }
        inFlight.add(remoteUrl);
        dlExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String path = downloadToCache(remoteUrl);
                    final Uri targetUri = (path != null && new File(path).exists())
                            ? Uri.fromFile(new File(path))
                            : Uri.parse(remoteUrl);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            playUriOnMain(targetUri, expectedSig);
                        }
                    });
                } finally {
                    inFlight.remove(remoteUrl);
                }
            }
        });
    }

    private void playUriOnMain(Uri uri, String expectedSig) {
        if (!expectedSig.equals(lastSpokenSig)) return;
        if (!Prefs.isSubAiVoiceEnabled(context)) return;
        try {
            ensureTtsPlayer();
            float vol = Math.max(0f, Math.min(1f, Prefs.getSubAiVoiceVolume(context) / 100f));
            ttsPlayer.stop();
            ttsPlayer.setMediaSource(new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(uri)));
            ttsPlayer.setVolume(vol);
            ttsPlayer.setPlayWhenReady(true);
            ttsPlayer.prepare();
        } catch (Exception ignored) {
        }
    }

    private String downloadToCache(String remoteUrl) {
        String existing = localCache.get(remoteUrl);
        if (existing != null && new File(existing).exists()) return existing;

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
            localCache.put(remoteUrl, outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void pruneCacheIfNeeded(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null || files.length < 85) return;
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(a.lastModified(), b.lastModified());
                }
            });
            for (int i = 0; i < files.length - 60; i++) {
                //noinspection ResultOfMethodCallIgnored
                files[i].delete();
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

    public static double calculateTtsSpeed(String cleanText, double subDurationSec, String effVoice) {
        double expected = cleanText.length() / 15.0;
        if (expected <= 0.0 || subDurationSec <= 0.0) return 1.0;
        double safeDur = Math.max(0.1, subDurationSec - 0.1);
        double req = expected / safeDur;

        if ("google".equals(effVoice)) {
            if (req <= 0.75) return 1.0;
            if (req <= 1.25) return 1.1;
            if (req <= 1.75) return 1.15;
            if (req <= 2.25) return 2.1;
            if (req <= 2.75) return 2.15;
            return 3.1;
        }
        if (req <= 1.05) return 1.0;
        if (req <= 1.25) return 1.15;
        if (req <= 1.5) return 1.35;
        if (req <= 1.8) return 1.6;
        return 1.85;
    }

    public static String buildTtsProxyUrl(String text, String lang, double speed, String voiceGd) {
        try {
            return "https://handle.dangvietanh.com/tts_proxy?text="
                    + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
                    + "&tl=" + URLEncoder.encode(lang != null && !lang.isEmpty() ? lang : "vi", "UTF-8")
                    + "&speed=" + speed
                    + "&voice_gd=" + URLEncoder.encode(voiceGd, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public void release() {
        lastSpokenSig = "";
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
