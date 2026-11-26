package com.dangvietanh.cast.player;

import android.os.Handler;
import android.os.Looper;

import com.dangvietanh.cast.data.Models;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direct Java port of iOS playerServerService.ts + PlayerModal server orchestration:
 * handles Phiên 11 / 36 / 40 / Hàn Quốc / Nguồn gốc discovery, parallel ping telemetry,
 * check_file polling, preview time-safety limits, auto-prepare, and tiered optimal selection.
 */
public final class ServerOrchestrator {

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_CHECKING = "checking";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_PREVIEW_10P = "preview_10p";
    public static final String STATUS_PREVIEW_30P = "preview_30p";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_UNSUPPORTED = "unsupported";

    // Preview time-safety limits synced with iOS playerServerService.ts
    public static final int PREVIEW_10P_SAFE_LIMIT_SEC = 480;   // 10 phút trừ hao 2 phút còn 8 phút
    public static final int PREVIEW_30P_SAFE_LIMIT_SEC = 1680;  // 30 phút trừ hao 2 phút còn 28 phút
    public static final int PREVIEW_10P_HARD_LIMIT_SEC = 590;
    public static final int PREVIEW_30P_HARD_LIMIT_SEC = 1790;
    public static final long AUTO_SWITCH_COOLDOWN_MS = 35000L;

    private static final String CHECK_FILE_PRIMARY = "https://dangvietanh.io.vn/check_file";
    private static final String CHECK_FILE_FALLBACK = "https://handle.dangvietanh.com/check_file";
    private static final String TRIGGER_GDRIVE_PRIMARY = "https://download.s1dangvietanh.io.vn/download_file_gdrive";
    private static final String TRIGGER_GDRIVE_FALLBACK = "https://dangvietanh.io.vn/download_file_gdrive";
    private static final String CHECK_GGDRIVER_API = "https://api.dangvietanh.com/api/check_ggdriver";
    private static final String PING_11_URL = "https://dangvietanh.io.vn/delay_ms.jpg";
    private static final String PING_36_URL = "https://handle.dangvietanh.com/delay_ms.jpg";
    private static final String QUALITY_KTCL_DEFAULT = "https://ktcl.s1dangvietanh.io.vn/kiem_tra_chat_luong_video";
    private static final String QUALITY_KTCL_36 = "https://handle.dangvietanh.com/kiem_tra_chat_luong_video";
    private static final String KOREA_WORKERS_BASE = "https://dangvietanh.svankrlov3r2001.workers.dev/0:/movie";
    private static final String KOREA_WORKERS_BASE_2 = "https://dangvietanh2.svankrlov3r2001.workers.dev/0:/movie";

    private static final long AUTO_PREPARE_COOLDOWN_MS = 60000L;
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final ThreadPoolExecutor executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object callbackToken = new Object();
    private final AtomicInteger generation = new AtomicInteger(0);
    private final Set<Future<?>> activeTasks = Collections.newSetFromMap(new ConcurrentHashMap<Future<?>, Boolean>());
    private final ConcurrentHashMap<String, Long> prepareTriggeredAtMs = new ConcurrentHashMap<>();

    public ServerOrchestrator() {
        final AtomicInteger threadSeq = new AtomicInteger(1);
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "DvaServerOrch-" + threadSeq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                4,
                10,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                factory
        );
        pool.allowCoreThreadTimeOut(true);
        this.executor = pool;
    }

    public static final class ServerState {
        public String id = "";
        public String codeName = "";
        public String label = "";
        public String subtitle = "";
        public boolean supported;
        public String status = STATUS_CHECKING;
        public int progress;
        public String progressLabel = "";
        public String etaText = "";
        public String format = "hls";
        public boolean preview10pReady;
        public boolean preview30pReady;
        public long totalSeconds;
        public long pingMs = -1L;
        public boolean pingFailed;
        public String streamUrl = "";
        public String previewTag = "";
        public boolean errored;

        public boolean isPlayable() {
            return supported && !errored && streamUrl != null && !streamUrl.isEmpty()
                    && (STATUS_READY.equals(status) || STATUS_PREVIEW_30P.equals(status) || STATUS_PREVIEW_10P.equals(status));
        }

        public ServerState copy() {
            ServerState s = new ServerState();
            s.id = id;
            s.codeName = codeName;
            s.label = label;
            s.subtitle = subtitle;
            s.supported = supported;
            s.status = status;
            s.progress = progress;
            s.progressLabel = progressLabel;
            s.etaText = etaText;
            s.format = format;
            s.preview10pReady = preview10pReady;
            s.preview30pReady = preview30pReady;
            s.totalSeconds = totalSeconds;
            s.pingMs = pingMs;
            s.pingFailed = pingFailed;
            s.streamUrl = streamUrl;
            s.previewTag = previewTag;
            s.errored = errored;
            return s;
        }
    }

    public static final class CheckFileResult {
        public boolean ok;
        public String status = "error";
        public String message = "";
        public String progress = "";
        public int progressNum;
        public String timeEnd = "";
        public String format = "hls";
        public String ext = "m3u8";
        public boolean preview10p;
        public boolean preview30p;
        public long totalSeconds;
    }

    public static final class GgdFlags {
        public boolean ggdriver1;
        public boolean ggdriver2;
        public boolean ggdriver3;

        public boolean hasAny() {
            return ggdriver1 || ggdriver2 || ggdriver3;
        }
    }

    public static final class PingSnapshot {
        public long ping11 = -1L;
        public long ping36 = -1L;
        public long ping40 = 450L;
        public long ping3 = 800L;
    }

    public static final class ProbeAllResult {
        public PingSnapshot pings = new PingSnapshot();
        public CheckFileResult checkFile;
        public GgdFlags ggd = new GgdFlags();
        public List<ServerState> servers = new ArrayList<>();
    }

    public interface Callback<T> {
        void onResult(T value);
    }

    /**
     * Cancel all in-flight network probes and drop pending UI callbacks.
     * Call when switching episodes or closing the player.
     */
    public void cancelPendingTasks() {
        generation.incrementAndGet();
        mainHandler.removeCallbacksAndMessages(callbackToken);
        for (Future<?> f : new ArrayList<>(activeTasks)) {
            f.cancel(true);
            activeTasks.remove(f);
        }
    }

    /**
     * Permanently release worker threads when the player controller is torn down.
     */
    public void shutdown() {
        cancelPendingTasks();
        executor.shutdownNow();
    }

    public List<ServerState> buildInitialServers(
            String serverFileKey,
            Models.StreamResolvedData resolved,
            GgdFlags ggd,
            Set<String> erroredIds) {
        return buildInitialServers(serverFileKey, resolved, ggd, null, erroredIds);
    }

    public List<ServerState> buildInitialServers(
            String serverFileKey,
            Models.StreamResolvedData resolved,
            GgdFlags ggd,
            PingSnapshot pings,
            Set<String> erroredIds) {
        List<ServerState> list = new ArrayList<>();
        String sfk = serverFileKey != null ? serverFileKey.trim() : "";

        boolean hasGgd1 = (ggd != null && ggd.ggdriver1) || (resolved != null && resolved.ggdriver1);
        boolean hasGgd2 = (ggd != null && ggd.ggdriver2) || (resolved != null && resolved.ggdriver2);
        boolean hasGgd3 = (ggd != null && ggd.ggdriver3) || (resolved != null && resolved.ggdriver3);
        boolean hasAnyGgd = hasGgd1 || hasGgd2 || hasGgd3;
        boolean hasKorea = hasGgd1 || hasGgd2 || (resolved != null && !resolved.hanquocUrl.isEmpty());

        GgdFlags mergedGgd = new GgdFlags();
        mergedGgd.ggdriver1 = hasGgd1;
        mergedGgd.ggdriver2 = hasGgd2;
        mergedGgd.ggdriver3 = hasGgd3;

        ServerState s11 = new ServerState();
        s11.id = "11";
        s11.codeName = "Phiên 11";
        s11.label = "Phiên 11";
        s11.subtitle = "Máy chủ tốc độ cao trực tiếp";
        s11.supported = !sfk.isEmpty();
        s11.errored = erroredIds != null && erroredIds.contains("11");
        s11.status = sfk.isEmpty() ? STATUS_UNSUPPORTED : (s11.errored ? STATUS_ERROR : STATUS_CHECKING);
        s11.progressLabel = sfk.isEmpty() ? "Chưa hỗ trợ" : (s11.errored ? "Lỗi phiên" : "Đang kiểm tra...");
        s11.format = "hls";
        if (pings != null) {
            s11.pingMs = pings.ping11;
            s11.pingFailed = pings.ping11 < 0;
        }
        list.add(s11);

        ServerState s36 = new ServerState();
        s36.id = "36";
        s36.codeName = "Phiên 36";
        s36.label = "Phiên 36";
        s36.subtitle = "Máy chủ dự phòng tăng tốc";
        s36.supported = !sfk.isEmpty();
        s36.errored = erroredIds != null && erroredIds.contains("36");
        s36.status = sfk.isEmpty() ? STATUS_UNSUPPORTED : (s36.errored ? STATUS_ERROR : STATUS_CHECKING);
        s36.progressLabel = sfk.isEmpty() ? "Chưa hỗ trợ" : (s36.errored ? "Lỗi phiên" : "Đang kiểm tra...");
        s36.format = "hls";
        if (pings != null) {
            s36.pingMs = pings.ping36;
            s36.pingFailed = pings.ping36 < 0;
        }
        list.add(s36);

        String p40Url = hasAnyGgd ? buildPhien40Url(sfk, resolved) : "";
        ServerState s40 = new ServerState();
        s40.id = "40";
        s40.codeName = "Phiên 40";
        s40.label = "Phiên 40";
        s40.subtitle = hasAnyGgd ? "Luồng phát gốc trực tiếp" : "Chưa hỗ trợ phim này";
        s40.supported = hasAnyGgd && !p40Url.isEmpty();
        s40.errored = erroredIds != null && erroredIds.contains("40");
        s40.status = (!s40.supported) ? STATUS_UNSUPPORTED : (s40.errored ? STATUS_ERROR : STATUS_READY);
        s40.progress = hasAnyGgd ? 100 : 0;
        s40.progressLabel = !s40.supported ? "Chưa hỗ trợ" : (s40.errored ? "Lỗi phiên" : "Sẵn sàng");
        s40.format = "mp4";
        s40.pingMs = pings != null ? pings.ping40 : 450L;
        s40.streamUrl = p40Url;
        list.add(s40);

        String hqUrl = hasKorea ? buildKoreaWorkerUrl(sfk, resolved, mergedGgd) : "";
        ServerState s3 = new ServerState();
        s3.id = "3";
        s3.codeName = "Hàn Quốc";
        s3.label = "Hàn Quốc";
        s3.subtitle = hasKorea ? "Máy chủ quốc tế ổn định" : "Chưa hỗ trợ phim này";
        s3.supported = hasKorea && !hqUrl.isEmpty();
        s3.errored = erroredIds != null && erroredIds.contains("3");
        s3.status = (!s3.supported) ? STATUS_UNSUPPORTED : (s3.errored ? STATUS_ERROR : STATUS_READY);
        s3.progress = hasKorea ? 100 : 0;
        s3.progressLabel = !s3.supported ? "Chưa hỗ trợ" : (s3.errored ? "Lỗi phiên" : "Sẵn sàng");
        s3.format = "mp4";
        s3.pingMs = pings != null ? pings.ping3 : 800L;
        s3.streamUrl = hqUrl;
        list.add(s3);

        if (resolved != null) {
            Models.StreamQuality directQ = null;
            for (Models.StreamQuality q : resolved.qualities) {
                if (q.url != null && !q.url.isEmpty()
                        && !q.url.contains("workers.dev")
                        && !q.url.contains("dangvietanh.io.vn/?code=")) {
                    directQ = q;
                    break;
                }
            }
            if (directQ != null) {
                ServerState fb = new ServerState();
                fb.id = "fallback";
                fb.codeName = "Nguồn gốc";
                fb.label = !directQ.label.isEmpty() ? directQ.label : "Máy chủ gốc";
                fb.subtitle = "Nguồn phát trực tiếp thay thế";
                fb.supported = true;
                fb.errored = erroredIds != null && erroredIds.contains("fallback");
                fb.status = fb.errored ? STATUS_ERROR : STATUS_READY;
                fb.progress = 100;
                fb.progressLabel = fb.errored ? "Lỗi phiên" : "Sẵn sàng";
                fb.format = "hls".equalsIgnoreCase(directQ.type) ? "hls" : "mp4";
                fb.pingMs = 320L;
                fb.streamUrl = directQ.url;
                list.add(fb);
            }
        }
        return list;
    }

    /**
     * Non-destructively apply measured pings onto an existing server list.
     */
    public List<ServerState> applyPings(List<ServerState> prev, PingSnapshot snap) {
        List<ServerState> out = new ArrayList<>(prev != null ? prev.size() : 0);
        if (prev == null) return out;
        for (ServerState src : prev) {
            ServerState s = src.copy();
            if (snap != null) {
                if ("11".equals(s.id)) {
                    s.pingMs = snap.ping11;
                    s.pingFailed = snap.ping11 < 0;
                } else if ("36".equals(s.id)) {
                    s.pingMs = snap.ping36;
                    s.pingFailed = snap.ping36 < 0;
                } else if ("40".equals(s.id)) {
                    s.pingMs = snap.ping40;
                    s.pingFailed = false;
                } else if ("3".equals(s.id)) {
                    s.pingMs = snap.ping3;
                    s.pingFailed = false;
                }
            }
            out.add(s);
        }
        return out;
    }

    /**
     * Non-destructively apply check_ggdriver flags onto Phiên 40 & Hàn Quốc
     * without wiping Phiên 11/36 check_file progress or measured pings.
     */
    public List<ServerState> applyGgdriver(
            List<ServerState> prev,
            String serverFileKey,
            Models.StreamResolvedData resolved,
            GgdFlags ggd) {
        List<ServerState> out = new ArrayList<>(prev != null ? prev.size() : 0);
        if (prev == null) return out;
        String sfk = serverFileKey != null ? serverFileKey.trim() : "";

        boolean hasGgd1 = (ggd != null && ggd.ggdriver1) || (resolved != null && resolved.ggdriver1);
        boolean hasGgd2 = (ggd != null && ggd.ggdriver2) || (resolved != null && resolved.ggdriver2);
        boolean hasGgd3 = (ggd != null && ggd.ggdriver3) || (resolved != null && resolved.ggdriver3);
        boolean hasAnyGgd = hasGgd1 || hasGgd2 || hasGgd3;
        boolean hasKorea = hasGgd1 || hasGgd2 || (resolved != null && !resolved.hanquocUrl.isEmpty());

        GgdFlags mergedGgd = new GgdFlags();
        mergedGgd.ggdriver1 = hasGgd1;
        mergedGgd.ggdriver2 = hasGgd2;
        mergedGgd.ggdriver3 = hasGgd3;

        for (ServerState src : prev) {
            ServerState s = src.copy();
            if ("40".equals(s.id)) {
                String p40Url = hasAnyGgd ? buildPhien40Url(sfk, resolved) : "";
                s.supported = hasAnyGgd && !p40Url.isEmpty();
                s.subtitle = hasAnyGgd ? "Luồng phát gốc trực tiếp" : "Chưa hỗ trợ phim này";
                s.streamUrl = p40Url;
                s.progress = s.supported ? 100 : 0;
                if (!s.supported) {
                    s.status = STATUS_UNSUPPORTED;
                    s.progressLabel = "Chưa hỗ trợ";
                } else if (s.errored) {
                    s.status = STATUS_ERROR;
                    s.progressLabel = "Lỗi phiên";
                } else {
                    s.status = STATUS_READY;
                    s.progressLabel = "Sẵn sàng";
                }
            } else if ("3".equals(s.id)) {
                String hqUrl = hasKorea ? buildKoreaWorkerUrl(sfk, resolved, mergedGgd) : "";
                s.supported = hasKorea && !hqUrl.isEmpty();
                s.subtitle = hasKorea ? "Máy chủ quốc tế ổn định" : "Chưa hỗ trợ phim này";
                s.streamUrl = hqUrl;
                s.progress = s.supported ? 100 : 0;
                if (!s.supported) {
                    s.status = STATUS_UNSUPPORTED;
                    s.progressLabel = "Chưa hỗ trợ";
                } else if (s.errored) {
                    s.status = STATUS_ERROR;
                    s.progressLabel = "Lỗi phiên";
                } else {
                    s.status = STATUS_READY;
                    s.progressLabel = "Sẵn sàng";
                }
            }
            out.add(s);
        }
        return out;
    }

    public List<ServerState> applyCheckFile(List<ServerState> prev, String serverFileKey, CheckFileResult res) {
        return applyCheckFile(prev, serverFileKey, res, 0L);
    }

    public List<ServerState> applyCheckFile(
            List<ServerState> prev,
            String serverFileKey,
            CheckFileResult res,
            long knownDurationSec) {
        List<ServerState> out = new ArrayList<>(prev != null ? prev.size() : 0);
        if (prev == null) return out;
        for (ServerState src : prev) {
            ServerState s = src.copy();
            if (!"11".equals(s.id) && !"36".equals(s.id)) {
                out.add(s);
                continue;
            }
            if (serverFileKey == null || serverFileKey.trim().isEmpty()) {
                s.status = STATUS_UNSUPPORTED;
                s.supported = false;
                s.progressLabel = "Chưa hỗ trợ";
                out.add(s);
                continue;
            }
            if (s.errored) {
                s.status = STATUS_ERROR;
                s.progressLabel = "Lỗi phiên";
                out.add(s);
                continue;
            }
            if (res == null || !res.ok) {
                s.status = STATUS_ERROR;
                s.progressLabel = "Mất kết nối";
                out.add(s);
                continue;
            }

            long effectiveTotalSec = Math.max(
                    Math.max(res.totalSeconds, knownDurationSec),
                    src.totalSeconds
            );
            String[] built = buildServer11Or36MediaUrl(s.id, serverFileKey, res, effectiveTotalSec);
            String mediaUrl = built[0];
            String previewTag = built[1];

            s.supported = true;
            s.progress = res.progressNum;
            s.etaText = res.timeEnd;
            s.format = res.format;
            s.preview10pReady = res.preview10p;
            s.preview30pReady = res.preview30p;
            s.totalSeconds = effectiveTotalSec;
            s.streamUrl = mediaUrl;
            s.previewTag = previewTag;

            if ("success".equals(res.status) && !mediaUrl.isEmpty()) {
                s.status = STATUS_READY;
                s.progressLabel = "100%";
            } else if (res.preview30p && effectiveTotalSec >= 3600L && !"mp4".equals(res.format) && !mediaUrl.isEmpty()) {
                s.status = STATUS_PREVIEW_30P;
                s.progressLabel = res.progressNum + "% (Xem trước 30p)";
            } else if (res.preview10p && !mediaUrl.isEmpty()) {
                s.status = STATUS_PREVIEW_10P;
                s.progressLabel = res.progressNum + "% (Xem trước 10p)";
            } else if ("error_ffmpeg".equals(res.status)) {
                // Synced with iOS: ffmpeg queue is non-fatal, keep polling
                s.status = STATUS_DOWNLOADING;
                s.progressLabel = !res.message.isEmpty() ? res.message : "Hàng đợi...";
            } else if ("error".equals(res.status) || "error_disk_quota".equals(res.status)) {
                s.status = STATUS_ERROR;
                s.progressLabel = !res.message.isEmpty() ? res.message : "Tạm bận";
            } else if ("error_file".equals(res.status) || "error_file_log".equals(res.status)) {
                s.status = STATUS_DOWNLOADING;
                s.progressLabel = "Đang chuẩn bị...";
            } else {
                s.status = STATUS_DOWNLOADING;
                if (res.progress != null && !res.progress.trim().isEmpty()) {
                    s.progressLabel = res.progress.trim();
                } else {
                    s.progressLabel = res.progressNum > 0 ? (res.progressNum + "%") : "Đang chuẩn bị...";
                }
            }
            out.add(s);
        }
        return out;
    }

    public static boolean shouldTriggerPrepare(CheckFileResult res) {
        if (res == null || !res.ok) return false;
        return "error_file".equals(res.status) || "error_file_log".equals(res.status);
    }

    public static boolean isServerEligibleForWatchTime(ServerState srv, double watchPosSec) {
        if (srv == null || !srv.isPlayable()) return false;
        double pos = Math.max(0.0, watchPosSec);
        if (STATUS_PREVIEW_10P.equals(srv.status) || "10p".equals(srv.previewTag)) {
            return pos < PREVIEW_10P_SAFE_LIMIT_SEC;
        }
        if (STATUS_PREVIEW_30P.equals(srv.status) || "30p".equals(srv.previewTag)) {
            return pos < PREVIEW_30P_SAFE_LIMIT_SEC;
        }
        return true;
    }

    public static boolean isPreviewLimitReached(ServerState srv, double watchPosSec) {
        if (srv == null) return false;
        double pos = Math.max(0.0, watchPosSec);
        if ("10p".equals(srv.previewTag) || STATUS_PREVIEW_10P.equals(srv.status)) {
            return pos >= PREVIEW_10P_HARD_LIMIT_SEC;
        }
        if ("30p".equals(srv.previewTag) || STATUS_PREVIEW_30P.equals(srv.status)) {
            return pos >= PREVIEW_30P_HARD_LIMIT_SEC;
        }
        return false;
    }

    public ServerState selectOptimalServer(List<ServerState> servers) {
        return selectOptimalServer(servers, 0.0);
    }

    /**
     * Priority tiers synced 1:1 with iOS playerServerService.ts selectOptimalServer:
     * 1. Tier 1: Phiên 11 & Phiên 36 (prioritizing 11, combined with readiness, latency >= 25ms & preview safety)
     * 2. Tier 2: Phiên 40 & Hàn Quốc (prioritizing 40, combined with latency >= 25ms)
     * 3. Fallback: direct source ('fallback')
     */
    public ServerState selectOptimalServer(List<ServerState> servers, double watchPosSec) {
        if (servers == null || servers.isEmpty()) return null;
        List<ServerState> eligible = new ArrayList<>();
        for (ServerState s : servers) {
            if (isServerEligibleForWatchTime(s, watchPosSec)) {
                eligible.add(s);
            }
        }
        if (eligible.isEmpty()) return null;

        // Tier 1: Phiên 11 & Phiên 36 (ưu tiên 11, kết hợp ping ms)
        ServerState tier1 = pickBestFromPair(eligible, Arrays.asList("11", "36"));
        if (tier1 != null) return tier1;

        // Tier 2: Phiên 40 & Hàn Quốc (ưu tiên 40, kết hợp ping ms)
        ServerState tier2 = pickBestFromPair(eligible, Arrays.asList("40", "3"));
        if (tier2 != null) return tier2;

        // Tier 3: Nguồn gốc trực tiếp
        for (ServerState s : eligible) {
            if ("fallback".equals(s.id)) return s;
        }
        return eligible.get(0);
    }

    /**
     * Select best alternative server excluding the currently failing or preview-exhausted server.
     */
    public ServerState selectFailoverServer(List<ServerState> servers, String excludeServerId, double watchPosSec) {
        if (servers == null || servers.isEmpty()) return null;
        List<ServerState> candidates = new ArrayList<>();
        for (ServerState s : servers) {
            if (excludeServerId != null && excludeServerId.equals(s.id)) continue;
            candidates.add(s);
        }
        return selectOptimalServer(candidates, watchPosSec);
    }

    private static ServerState pickBestFromPair(List<ServerState> eligible, final List<String> ids) {
        List<ServerState> pair = new ArrayList<>();
        for (ServerState s : eligible) {
            if (ids.contains(s.id)) {
                pair.add(s);
            }
        }
        if (pair.isEmpty()) return null;
        Collections.sort(pair, new Comparator<ServerState>() {
            @Override
            public int compare(ServerState a, ServerState b) {
                int rDiff = tierScore(b) - tierScore(a);
                if (rDiff != 0) return rDiff;
                long pingA = (a.pingMs >= 0 && !a.pingFailed) ? a.pingMs : 9999L;
                long pingB = (b.pingMs >= 0 && !b.pingFailed) ? b.pingMs : 9999L;
                if (Math.abs(pingA - pingB) >= 25L) {
                    return Long.compare(pingA, pingB);
                }
                return Integer.compare(ids.indexOf(a.id), ids.indexOf(b.id));
            }
        });
        return pair.get(0);
    }

    private static int tierScore(ServerState s) {
        if (STATUS_READY.equals(s.status)) return 3;
        if (STATUS_PREVIEW_30P.equals(s.status)) return 2;
        if (STATUS_PREVIEW_10P.equals(s.status)) return 1;
        return 0;
    }

    /**
     * Parallel initial probe mirroring iOS Promise.all([measurePings, checkFile, checkGgdriver]).
     * Automatically skips check_ggdriver when stream_resolve already provided GGD flags.
     */
    public void probeAll(
            final String serverFileKey,
            final Models.StreamResolvedData resolved,
            final Set<String> erroredIds,
            final Callback<ProbeAllResult> cb) {
        final int gen = generation.get();
        final String clean = serverFileKey != null ? serverFileKey.trim() : "";
        final String movieTitle = resolved != null ? resolved.serverMovieTitle : "";
        final boolean alreadyHasGgd = resolved != null && (resolved.ggdriver1 || resolved.ggdriver2 || resolved.ggdriver3);

        submitTracked(new Runnable() {
            @Override
            public void run() {
                final PingSnapshot pings = new PingSnapshot();
                final AtomicReference<CheckFileResult> cfRef = new AtomicReference<>();
                final AtomicReference<GgdFlags> ggdRef = new AtomicReference<>();

                final boolean runCheckFile = !clean.isEmpty();
                final boolean runGgd = !clean.isEmpty() && !alreadyHasGgd;
                int latchCount = 2 + (runCheckFile ? 1 : 0) + (runGgd ? 1 : 0);
                final CountDownLatch latch = new CountDownLatch(latchCount);

                final AtomicLong p11Ref = new AtomicLong(-1L);
                final AtomicLong p36Ref = new AtomicLong(-1L);

                submitTracked(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            p11Ref.set(probeLatency(PING_11_URL, 3200));
                        } finally {
                            latch.countDown();
                        }
                    }
                });

                submitTracked(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            p36Ref.set(probeLatency(PING_36_URL, 3200));
                        } finally {
                            latch.countDown();
                        }
                    }
                });

                if (runCheckFile) {
                    submitTracked(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                cfRef.set(fetchCheckFileSync(clean));
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }

                if (runGgd) {
                    submitTracked(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ggdRef.set(fetchGgdriverSync(clean, movieTitle, 0));
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                } else {
                    GgdFlags existing = new GgdFlags();
                    if (resolved != null) {
                        existing.ggdriver1 = resolved.ggdriver1;
                        existing.ggdriver2 = resolved.ggdriver2;
                        existing.ggdriver3 = resolved.ggdriver3;
                    }
                    ggdRef.set(existing);
                }

                try {
                    latch.await(7500L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (gen != generation.get() || Thread.currentThread().isInterrupted()) return;

                long p11 = p11Ref.get();
                long p36Raw = p36Ref.get();
                pings.ping11 = p11;
                pings.ping36 = p36Raw >= 0 ? (p36Raw + 100L) : -1L;
                pings.ping40 = p11 >= 0 ? (p11 + 200L) : 500L;
                pings.ping3 = 800L;

                CheckFileResult cf = cfRef.get();
                GgdFlags ggd = ggdRef.get();
                if (ggd == null) ggd = new GgdFlags();

                List<ServerState> built = buildInitialServers(clean, resolved, ggd, pings, erroredIds);
                if (cf != null) {
                    built = applyCheckFile(built, clean, cf, 0L);
                    if (shouldTriggerPrepare(cf)) {
                        maybeAutoTriggerPrepare(clean);
                    }
                }

                ProbeAllResult result = new ProbeAllResult();
                result.pings = pings;
                result.checkFile = cf;
                result.ggd = ggd;
                result.servers = built;
                postIfCurrent(gen, cb, result);
            }
        });
    }

    public void checkFileStatus(final String serverFileKey, final Callback<CheckFileResult> cb) {
        final int gen = generation.get();
        submitTracked(new Runnable() {
            @Override
            public void run() {
                String clean = serverFileKey != null ? serverFileKey.trim() : "";
                CheckFileResult out = fetchCheckFileSync(clean);
                if (gen != generation.get() || Thread.currentThread().isInterrupted()) return;
                if (shouldTriggerPrepare(out)) {
                    maybeAutoTriggerPrepare(clean);
                }
                postIfCurrent(gen, cb, out);
            }
        });
    }

    public void checkGgdriver(final String serverFileKey, final String movieName, final Callback<GgdFlags> cb) {
        checkGgdriver(serverFileKey, movieName, 0, cb);
    }

    public void checkGgdriver(
            final String serverFileKey,
            final String movieName,
            final int nextEpisodeNum,
            final Callback<GgdFlags> cb) {
        final int gen = generation.get();
        submitTracked(new Runnable() {
            @Override
            public void run() {
                GgdFlags flags = fetchGgdriverSync(serverFileKey, movieName, nextEpisodeNum);
                if (gen != generation.get() || Thread.currentThread().isInterrupted()) return;
                postIfCurrent(gen, cb, flags);
            }
        });
    }

    public void triggerPrepare(final String serverFileKey) {
        final String clean = serverFileKey != null ? serverFileKey.trim() : "";
        if (clean.isEmpty()) return;
        prepareTriggeredAtMs.put(clean, System.currentTimeMillis());
        submitTracked(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = "name_movies=" + encodeComp(clean);
                    if (!postFormOk(TRIGGER_GDRIVE_PRIMARY, body, 6000)) {
                        postFormOk(TRIGGER_GDRIVE_FALLBACK, body, 6000);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * Measure Phiên 11 & Phiên 36 latency in parallel (matching iOS Promise.all).
     */
    public void measurePings(final Callback<PingSnapshot> cb) {
        final int gen = generation.get();
        submitTracked(new Runnable() {
            @Override
            public void run() {
                final AtomicLong p11Ref = new AtomicLong(-1L);
                final AtomicLong p36Ref = new AtomicLong(-1L);
                final CountDownLatch latch = new CountDownLatch(2);

                submitTracked(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            p11Ref.set(probeLatency(PING_11_URL, 3200));
                        } finally {
                            latch.countDown();
                        }
                    }
                });

                submitTracked(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            p36Ref.set(probeLatency(PING_36_URL, 3200));
                        } finally {
                            latch.countDown();
                        }
                    }
                });

                try {
                    latch.await(4000L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (gen != generation.get() || Thread.currentThread().isInterrupted()) return;

                PingSnapshot snap = new PingSnapshot();
                long p11 = p11Ref.get();
                long p36Raw = p36Ref.get();
                snap.ping11 = p11;
                snap.ping36 = p36Raw >= 0 ? (p36Raw + 100L) : -1L;
                snap.ping40 = p11 >= 0 ? (p11 + 200L) : 500L;
                snap.ping3 = 800L;
                postIfCurrent(gen, cb, snap);
            }
        });
    }

    public void probeVideoQualityLabel(
            final String serverFileKey,
            final String activeServerId,
            final String fallback,
            final Callback<String> cb) {
        final int gen = generation.get();
        submitTracked(new Runnable() {
            @Override
            public void run() {
                String clean = serverFileKey != null ? serverFileKey.trim() : "";
                String def = (fallback != null && !fallback.trim().isEmpty()) ? fallback : "1080pHD";
                if (clean.isEmpty()) {
                    postIfCurrent(gen, cb, def);
                    return;
                }
                String url = "36".equals(activeServerId) ? QUALITY_KTCL_36 : QUALITY_KTCL_DEFAULT;
                try {
                    JSONObject json = postFormJson(url, "name_movies=" + encodeComp(clean), 4500);
                    if (gen != generation.get() || Thread.currentThread().isInterrupted()) return;
                    if (json == null || json.has("error")) {
                        postIfCurrent(gen, cb, def);
                        return;
                    }
                    int w = parseIntSafe(json.optString("width", "0"));
                    double fps = parseDoubleSafe(json.optString("fps", "0"));
                    String hdr = json.optString("hdr", "");
                    boolean isHdr = hdr.contains("HDR");
                    String fpsSuffix = fps > 30.0 ? "p60" : "p";
                    String badge = "";
                    if (isHdr) badge = " HDR";
                    else if (w >= 3840) badge = " 4K";
                    else if (w >= 2560) badge = " 2K";
                    else if (w >= 1280) badge = "HD";

                    String label;
                    if (w >= 3840) label = "2160" + fpsSuffix + badge;
                    else if (w >= 2560) label = "1440" + fpsSuffix + badge;
                    else if (w >= 1920) label = "1080" + fpsSuffix + badge;
                    else if (w >= 1280) label = "720" + fpsSuffix + badge;
                    else if (w >= 854) label = "480p";
                    else if (w >= 640) label = "360p";
                    else label = def;
                    postIfCurrent(gen, cb, label);
                    return;
                } catch (Exception ignored) {
                }
                postIfCurrent(gen, cb, def);
            }
        });
    }

    public static String[] buildServer11Or36MediaUrl(String serverId, String serverFileKey, CheckFileResult check) {
        return buildServer11Or36MediaUrl(serverId, serverFileKey, check, 0L);
    }

    public static String[] buildServer11Or36MediaUrl(
            String serverId,
            String serverFileKey,
            CheckFileResult check,
            long knownDurationSec) {
        String cleanKey = serverFileKey != null ? serverFileKey.trim() : "";
        if (cleanKey.isEmpty() || check == null) return new String[]{"", ""};
        String ext = "mp4".equals(check.format) ? "mp4" : "m3u8";
        long effectiveTotalSec = Math.max(check.totalSeconds, knownDurationSec);
        String suffix;
        String previewTag = "";

        if ("success".equals(check.status)) {
            suffix = "";
        } else if (check.preview30p && effectiveTotalSec >= 3600L && "m3u8".equals(ext)) {
            suffix = "_30p";
            previewTag = "30p";
        } else if (check.preview10p) {
            suffix = "_10p";
            previewTag = "10p";
        } else {
            return new String[]{"", ""};
        }

        String fileName = cleanKey + suffix + "." + ext;
        if ("11".equals(serverId)) {
            return new String[]{
                    "https://dangvietanh.io.vn/uploads/" + encodeComp(cleanKey) + "/" + encodeComp(fileName),
                    previewTag
            };
        }
        return new String[]{
                "https://handle.dangvietanh.com/stream_h?code=" + encodeComp(cleanKey) + "/" + encodeComp(fileName),
                previewTag
        };
    }

    public static String buildKoreaWorkerUrl(String serverFileKey, Models.StreamResolvedData resolved) {
        return buildKoreaWorkerUrl(serverFileKey, resolved, null);
    }

    public static String buildKoreaWorkerUrl(
            String serverFileKey,
            Models.StreamResolvedData resolved,
            GgdFlags ggd) {
        if (resolved != null && resolved.hanquocUrl != null && !resolved.hanquocUrl.isEmpty()) {
            return resolved.hanquocUrl;
        }
        String clean = serverFileKey != null ? serverFileKey.trim() : "";
        if (clean.isEmpty()) return "";
        boolean hasGgd1 = (ggd != null && ggd.ggdriver1) || (resolved != null && resolved.ggdriver1);
        boolean hasGgd2 = (ggd != null && ggd.ggdriver2) || (resolved != null && resolved.ggdriver2);
        String workerBase = (!hasGgd1 && hasGgd2) ? KOREA_WORKERS_BASE_2 : KOREA_WORKERS_BASE;
        boolean isSeries = clean.contains(".Tập.")
                || (resolved != null && !resolved.serverTapPhim.isEmpty())
                || (resolved != null && resolved.movie != null && resolved.movie.isTvSeries == 1);
        return workerBase + "/" + (isSeries ? "B" : "L") + "/" + encodeComp(clean) + ".mp4";
    }

    public static String buildPhien40Url(String serverFileKey, Models.StreamResolvedData resolved) {
        if (resolved != null && resolved.phien40Url != null && !resolved.phien40Url.isEmpty()) {
            return resolved.phien40Url;
        }
        String clean = serverFileKey != null ? serverFileKey.trim() : "";
        if (clean.isEmpty()) return "";
        return "https://dangvietanh.io.vn/?code=" + encodeComp(clean);
    }

    public static String formatVietnameseEta(String rawEta) {
        String trimmed = rawEta != null ? rawEta.trim() : "";
        if (trimmed.isEmpty()) return "";
        if (trimmed.toLowerCase(Locale.ROOT).matches(".*(phút|giây|giờ).*")) return trimmed;
        String[] parts = trimmed.split(":");
        try {
            if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                if (h > 0) return h + " giờ " + m + " phút";
                if (m > 0) return m + " phút " + s + " giây";
                return s + " giây";
            }
            if (parts.length == 2) {
                int m = Integer.parseInt(parts[0]);
                int s = Integer.parseInt(parts[1]);
                if (m > 0) return m + " phút " + s + " giây";
                return s + " giây";
            }
        } catch (Exception ignored) {
        }
        return trimmed;
    }

    private CheckFileResult fetchCheckFileSync(String cleanKey) {
        CheckFileResult out = new CheckFileResult();
        if (cleanKey == null || cleanKey.isEmpty()) return out;
        try {
            String body = "name_movies=" + encodeComp(cleanKey);
            JSONObject data = postFormJson(CHECK_FILE_PRIMARY, body, 5500);
            if (data == null && !Thread.currentThread().isInterrupted()) {
                data = postFormJson(CHECK_FILE_FALLBACK, body, 5500);
            }
            if (data != null) {
                out.ok = true;
                out.status = data.optString("status", "");
                out.message = data.optString("message", "");
                String rawFmt = data.optString("format", data.optString("ext", "hls")).toLowerCase(Locale.ROOT);
                out.format = "mp4".equals(rawFmt) ? "mp4" : "hls";
                out.ext = "mp4".equals(out.format) ? "mp4" : "m3u8";
                String progStr = data.optString("progress", "");
                out.progress = progStr;
                out.progressNum = parseProgressNum(progStr, out.status);
                out.timeEnd = data.optString("time_end", "");
                out.preview10p = "success".equals(data.optString("preview_10p"));
                out.preview30p = "success".equals(data.optString("preview_30p"));
                out.totalSeconds = data.optLong("total_seconds", 0L);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private GgdFlags fetchGgdriverSync(String serverFileKey, String movieName, int nextEpisodeNum) {
        GgdFlags flags = new GgdFlags();
        String clean = serverFileKey != null ? serverFileKey.trim() : "";
        if (clean.isEmpty()) return flags;
        try {
            String body = "filename=" + encodeComp(clean)
                    + "&movie_name=" + encodeComp(movieName != null ? movieName : "")
                    + "&check_next=" + (nextEpisodeNum > 0 ? "1" : "0")
                    + "&next_episode=" + (nextEpisodeNum > 0 ? String.valueOf(nextEpisodeNum) : "");
            JSONObject json = postFormJson(CHECK_GGDRIVER_API, body, 4500);
            if (json != null) {
                JSONObject d = json.optJSONObject("data");
                if (d == null) d = json.optJSONObject("results");
                if (d == null) d = json;
                flags.ggdriver1 = d.optBoolean("ggdriver_1", false) || d.optBoolean("ggdriver", false) || d.optInt("check_ggd_1", 0) == 1;
                flags.ggdriver2 = d.optBoolean("ggdriver_2", false) || d.optInt("check_ggd_2", 0) == 1;
                flags.ggdriver3 = d.optBoolean("ggdriver_3", false) || d.optInt("check_ggd_3", 0) == 1;
            }
        } catch (Exception ignored) {
        }
        return flags;
    }

    private void maybeAutoTriggerPrepare(String cleanKey) {
        if (cleanKey == null || cleanKey.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = prepareTriggeredAtMs.get(cleanKey);
        if (last != null && (now - last) < AUTO_PREPARE_COOLDOWN_MS) {
            return;
        }
        triggerPrepare(cleanKey);
    }

    private void submitTracked(final Runnable task) {
        if (executor.isShutdown()) return;
        final AtomicReference<Future<?>> ref = new AtomicReference<>();
        Future<?> future = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } finally {
                    Future<?> f = ref.get();
                    if (f != null) activeTasks.remove(f);
                }
            }
        });
        ref.set(future);
        activeTasks.add(future);
    }

    private static int parseProgressNum(String raw, String status) {
        if ("success".equals(status)) return 100;
        if (raw == null) return 0;
        Matcher m = PROGRESS_PATTERN.matcher(raw);
        if (m.find()) {
            try {
                int val = (int) Math.round(Double.parseDouble(m.group(1)));
                return Math.max(0, Math.min(99, val));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String encodeComp(String s) {
        try {
            return URLEncoder.encode(s != null ? s : "", "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s != null ? s : "";
        }
    }

    private static JSONObject postFormJson(String urlStr, String formBody, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("User-Agent", "DVACastTV/4.0");
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean postFormOk(String urlStr, String formBody, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("User-Agent", "DVACastTV/4.0");
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static long probeLatency(String urlStr, int timeoutMs) {
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            String sep = urlStr.contains("?") ? "&" : "?";
            conn = (HttpURLConnection) new URL(urlStr + sep + "t=" + start).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("User-Agent", "DVACastTV/4.0");
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return -1L;
            InputStream is = conn.getInputStream();
            byte[] buf = new byte[4096];
            while (is.read(buf) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    is.close();
                    return -1L;
                }
            }
            is.close();
            return Math.max(8L, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return -1L;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private <T> void postIfCurrent(final int expectedGen, final Callback<T> cb, final T val) {
        if (cb == null || expectedGen != generation.get()) return;
        mainHandler.postAtTime(new Runnable() {
            @Override
            public void run() {
                if (expectedGen == generation.get()) {
                    cb.onResult(val);
                }
            }
        }, callbackToken, android.os.SystemClock.uptimeMillis());
    }
}
