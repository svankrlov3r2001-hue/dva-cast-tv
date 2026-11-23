package com.dangvietanh.cast.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.dangvietanh.cast.Prefs;
import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.Models;

import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Android TV dual-subtitle parser, custom stroked renderer,
 * and sliding-window live translator matching DVA Play iOS & subtitleST.
 */
public final class SubtitleEngine {

    public static final class Cue {
        public final double start;
        public final double end;
        public final String text;
        public final boolean isTop;

        public Cue(double start, double end, String text, boolean isTop) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.isTop = isTop;
        }
    }

    public static final class LoadResult {
        public final List<Cue> vnCues = new ArrayList<>();
        public final List<Cue> enCues = new ArrayList<>();
        public String vnUrl = "";
        public String enUrl = "";
    }

    public static final class LangOption {
        public final String code;
        public final String name;

        public LangOption(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    public static final class FontOption {
        public final String value;
        public final String label;

        public FontOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public static final class ColorOption {
        public final String key;
        public final String label;
        public final int mainColor;
        public final int subColor;

        public ColorOption(String key, String label, int mainColor, int subColor) {
            this.key = key;
            this.label = label;
            this.mainColor = mainColor;
            this.subColor = subColor;
        }
    }

    public static final class OutlineOption {
        public final String key;
        public final String label;

        public OutlineOption(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static final List<FontOption> FONT_OPTIONS = Collections.unmodifiableList(createFontOptions());
    public static final List<ColorOption> COLOR_OPTIONS = Collections.unmodifiableList(createColorOptions());
    public static final List<OutlineOption> OUTLINE_OPTIONS = Collections.unmodifiableList(createOutlineOptions());
    public static final List<LangOption> TRANSLATE_LANGUAGES = Collections.unmodifiableList(createTranslateLanguages());

    private static List<FontOption> createFontOptions() {
        List<FontOption> l = new ArrayList<>();
        l.add(new FontOption("Arial,Helvetica,sans-serif", "Ahri (Mặc định)"));
        l.add(new FontOption("Noto Sans", "Noto Sans"));
        l.add(new FontOption("Roboto", "Roboto"));
        l.add(new FontOption("Inter", "Inter"));
        l.add(new FontOption("Literata", "Literata (Serif)"));
        return l;
    }

    private static List<ColorOption> createColorOptions() {
        List<ColorOption> l = new ArrayList<>();
        l.add(new ColorOption("white", "Trắng", Color.parseColor("#FFFFFF"), Color.argb(210, 255, 255, 255)));
        l.add(new ColorOption("beige", "Kem (Be)", Color.parseColor("#F5F5DC"), Color.argb(210, 245, 245, 220)));
        l.add(new ColorOption("lightblue", "Xanh nhạt", Color.parseColor("#4ECDF7"), Color.argb(210, 78, 205, 247)));
        l.add(new ColorOption("red", "Đỏ", Color.parseColor("#FF3B30"), Color.argb(210, 255, 59, 48)));
        l.add(new ColorOption("yellow", "Vàng", Color.parseColor("#FFD60A"), Color.argb(210, 255, 214, 10)));
        l.add(new ColorOption("pink", "Hồng", Color.parseColor("#FF80ED"), Color.argb(210, 255, 128, 237)));
        return l;
    }

    private static List<OutlineOption> createOutlineOptions() {
        List<OutlineOption> l = new ArrayList<>();
        l.add(new OutlineOption("uniform2", "Đồng nhất 2"));
        l.add(new OutlineOption("uniform", "Đồng nhất"));
        l.add(new OutlineOption("shadow", "Đổ bóng"));
        l.add(new OutlineOption("raise", "Nâng lên"));
        l.add(new OutlineOption("lower", "Nén xuống"));
        l.add(new OutlineOption("none", "Không viền"));
        return l;
    }

    private static List<LangOption> createTranslateLanguages() {
        String[][] pairs = {
                {"af", "Tiếng Afrikaans"}, {"sq", "Tiếng Albania"}, {"am", "Tiếng Amharic"}, {"ar", "Tiếng Ả Rập"},
                {"hy", "Tiếng Armenia"}, {"az", "Tiếng Azerbaijan"}, {"eu", "Tiếng Basque"}, {"be", "Tiếng Belarus"},
                {"bn", "Tiếng Bengal"}, {"bs", "Tiếng Bosnia"}, {"bg", "Tiếng Bulgaria"}, {"ca", "Tiếng Catalan"},
                {"ceb", "Tiếng Cebuano"}, {"zh-CN", "Tiếng Trung (Giản thể)"}, {"zh-TW", "Tiếng Trung (Phồn thể)"},
                {"co", "Tiếng Corsica"}, {"hr", "Tiếng Croatia"}, {"cs", "Tiếng Séc"}, {"da", "Tiếng Đan Mạch"},
                {"nl", "Tiếng Hà Lan"}, {"en", "Tiếng Anh"}, {"eo", "Tiếng Esperanto"}, {"et", "Tiếng Estonia"},
                {"fi", "Tiếng Phần Lan"}, {"fr", "Tiếng Pháp"}, {"fy", "Tiếng Frisia"}, {"gl", "Tiếng Galicia"},
                {"ka", "Tiếng Gruzia"}, {"de", "Tiếng Đức"}, {"el", "Tiếng Hy Lạp"}, {"gu", "Tiếng Gujarat"},
                {"ht", "Tiếng Creole Haiti"}, {"ha", "Tiếng Hausa"}, {"haw", "Tiếng Hawaii"}, {"he", "Tiếng Do Thái"},
                {"hi", "Tiếng Hindi"}, {"hmn", "Tiếng Hmong"}, {"hu", "Tiếng Hungary"}, {"is", "Tiếng Iceland"},
                {"ig", "Tiếng Igbo"}, {"id", "Tiếng Indonesia"}, {"ga", "Tiếng Ireland"}, {"it", "Tiếng Ý"},
                {"ja", "Tiếng Nhật"}, {"jw", "Tiếng Java"}, {"kn", "Tiếng Kannada"}, {"kk", "Tiếng Kazakh"},
                {"km", "Tiếng Khmer"}, {"ko", "Tiếng Hàn"}, {"ku", "Tiếng Kurd"}, {"ky", "Tiếng Kyrgyz"},
                {"lo", "Tiếng Lào"}, {"la", "Tiếng Latinh"}, {"lv", "Tiếng Latvia"}, {"lt", "Tiếng Litva"},
                {"lb", "Tiếng Luxembourg"}, {"mk", "Tiếng Macedonia"}, {"mg", "Tiếng Malagasy"}, {"ms", "Tiếng Mã Lai"},
                {"ml", "Tiếng Malayalam"}, {"mt", "Tiếng Malta"}, {"mi", "Tiếng Maori"}, {"mr", "Tiếng Marathi"},
                {"mn", "Tiếng Mông Cổ"}, {"my", "Tiếng Myanmar"}, {"ne", "Tiếng Nepal"}, {"no", "Tiếng Na Uy"},
                {"ny", "Tiếng Nyanja"}, {"or", "Tiếng Odia"}, {"ps", "Tiếng Pashto"}, {"fa", "Tiếng Ba Tư"},
                {"pl", "Tiếng Ba Lan"}, {"pt", "Tiếng Bồ Đào Nha"}, {"pa", "Tiếng Punjab"}, {"ro", "Tiếng Rumani"},
                {"ru", "Tiếng Nga"}, {"sm", "Tiếng Samoa"}, {"gd", "Tiếng Gaelic Scotland"}, {"sr", "Tiếng Serbia"},
                {"st", "Tiếng Sesotho"}, {"sn", "Tiếng Shona"}, {"sd", "Tiếng Sindhi"}, {"si", "Tiếng Sinhala"},
                {"sk", "Tiếng Slovak"}, {"sl", "Tiếng Slovenia"}, {"so", "Tiếng Somali"}, {"es", "Tiếng Tây Ban Nha"},
                {"su", "Tiếng Sunda"}, {"sw", "Tiếng Swahili"}, {"sv", "Tiếng Thụy Điển"}, {"tl", "Tiếng Tagalog"},
                {"tg", "Tiếng Tajik"}, {"ta", "Tiếng Tamil"}, {"tt", "Tiếng Tatar"}, {"te", "Tiếng Telugu"},
                {"th", "Tiếng Thái"}, {"tr", "Tiếng Thổ Nhĩ Kỳ"}, {"tk", "Tiếng Turkmen"}, {"uk", "Tiếng Ukraina"},
                {"ur", "Tiếng Urdu"}, {"ug", "Tiếng Uyghur"}, {"uz", "Tiếng Uzbek"}, {"vi", "Tiếng Việt"},
                {"cy", "Tiếng Wales"}, {"xh", "Tiếng Xhosa"}, {"yi", "Tiếng Yiddish"}, {"yo", "Tiếng Yoruba"},
                {"zu", "Tiếng Zulu"}
        };
        List<LangOption> l = new ArrayList<>(pairs.length);
        for (String[] p : pairs) {
            l.add(new LangOption(p[0], p[1]));
        }
        return l;
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> translationCache = Collections.synchronizedMap(new HashMap<String, String>());
    private final Set<String> inFlightTrans = Collections.synchronizedSet(new HashSet<String>());

    public interface LoadCallback {
        void onLoaded(LoadResult result);
    }

    public static ColorOption findColor(String key) {
        for (ColorOption c : COLOR_OPTIONS) {
            if (c.key.equals(key)) return c;
        }
        return COLOR_OPTIONS.get(0);
    }

    public static Typeface resolveTypeface(String fontValue) {
        if ("Literata".equals(fontValue)) {
            return Typeface.create(Typeface.SERIF, Typeface.NORMAL);
        }
        return Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    }

    public static String encodeSubtitlePathUrl(String rawUrl) {
        String clean = rawUrl != null ? rawUrl.trim() : "";
        if (clean.isEmpty()) return "";
        int sslIdx = clean.indexOf("ssl=");
        if (sslIdx >= 0) {
            String before = clean.substring(0, sslIdx + 4);
            String after = clean.substring(sslIdx + 4);
            int ampIdx = after.indexOf('&');
            String rawPath = ampIdx >= 0 ? after.substring(0, ampIdx) : after;
            String suffix = ampIdx >= 0 ? after.substring(ampIdx) : "";
            String[] segs = rawPath.split("/", -1);
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                if (i > 0) encoded.append('/');
                try {
                    encoded.append(URLEncoder.encode(segs[i], "UTF-8").replace("+", "%20"));
                } catch (Exception e) {
                    encoded.append(segs[i]);
                }
            }
            return before + encoded + suffix;
        }
        return clean.replace(" ", "%20");
    }

    public static String[] buildSubtitleUrls(String serverFileKey) {
        String cleanKey = serverFileKey != null
                ? serverFileKey.replaceAll("(?i)\\.(mp4|m3u8)$", "").trim()
                : "";
        if (cleanKey.isEmpty()) return new String[]{"", ""};

        boolean isSeries = cleanKey.contains(".Tập.") || cleanKey.matches("(?i).*[.\\s]T[ậâa]p.*");
        String theloai = isSeries ? "Phim.Bộ" : "Phim.Lẻ";
        String baseCode = cleanKey.replaceAll("(?i)[.\\s]T[ậâa]p.*$", "");
        if (baseCode.isEmpty()) baseCode = cleanKey;

        String vnUrl = encodeSubtitlePathUrl(
                "https://sub.dangvietanh.com/dashboard?ssl=" + theloai + "/" + baseCode + "/Subtitle/VN/" + cleanKey + ".vtt&dtpd=0&vtpd=80&vtpdtt=0");
        String enUrl = encodeSubtitlePathUrl(
                "https://sub.dangvietanh.com/dashboard?ssl=" + theloai + "/" + baseCode + "/Subtitle/EN/" + cleanKey + ".vtt&dtpd=0&vtpd=80&vtpdtt=0");
        return new String[]{vnUrl, enUrl};
    }

    public void loadDualSubtitles(
            final String serverFileKey,
            final List<Models.StreamSubtitle> resolvedSubs,
            final boolean bustCache,
            final LoadCallback cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                LoadResult res = new LoadResult();
                String[] constructed = buildSubtitleUrls(serverFileKey);
                String resolvedVn = "";
                String resolvedEn = "";
                if (resolvedSubs != null) {
                    for (Models.StreamSubtitle s : resolvedSubs) {
                        if ("vi".equalsIgnoreCase(s.lang) && resolvedVn.isEmpty()) resolvedVn = s.url;
                        else if ("en".equalsIgnoreCase(s.lang) && resolvedEn.isEmpty()) resolvedEn = s.url;
                    }
                    if (resolvedVn.isEmpty() && !resolvedSubs.isEmpty()) {
                        resolvedVn = resolvedSubs.get(0).url;
                    }
                }
                res.vnUrl = !constructed[0].isEmpty() ? constructed[0] : resolvedVn;
                res.enUrl = !constructed[1].isEmpty() ? constructed[1] : resolvedEn;

                String vnRaw = fetchTextSafe(res.vnUrl, bustCache);
                String enRaw = fetchTextSafe(res.enUrl, bustCache);
                res.vnCues.addAll(parseVttCues(vnRaw));
                res.enCues.addAll(parseVttCues(enRaw));

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.onLoaded(res);
                    }
                });
            }
        });
    }

    public void clearTranslationCache() {
        translationCache.clear();
        inFlightTrans.clear();
    }

    public String getCachedTranslation(String toLang, String text) {
        if (toLang == null || toLang.isEmpty() || text == null || text.isEmpty()) return "";
        String val = translationCache.get(toLang + "::" + text);
        return val != null ? val : "";
    }

    public void prefetchTranslations(final String toLang, final List<Cue> cues, final int activeIdx, final Runnable onTranslated) {
        if (toLang == null || toLang.isEmpty() || cues == null || activeIdx < 0) return;
        int maxIdx = Math.min(cues.size() - 1, activeIdx + 16);
        for (int i = activeIdx; i <= maxIdx; i++) {
            final Cue c = cues.get(i);
            if (c == null || c.text.isEmpty() || c.isTop) continue;
            final String key = toLang + "::" + c.text;
            if (translationCache.containsKey(key) || inFlightTrans.contains(key)) continue;
            inFlightTrans.add(key);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String urlStr = "https://api.datpmt.com/api/v2/dictionary/translate?string="
                                + URLEncoder.encode(c.text, "UTF-8")
                                + "&from_lang=vi&to_lang="
                                + URLEncoder.encode(toLang, "UTF-8");
                        String raw = fetchTextSafe(urlStr, false);
                        if (raw != null && !raw.trim().isEmpty()) {
                            Object parsed = new JSONTokener(raw.trim()).nextValue();
                            if (parsed instanceof String && !((String) parsed).trim().isEmpty()) {
                                translationCache.put(key, ((String) parsed).trim());
                                if (onTranslated != null) mainHandler.post(onTranslated);
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        inFlightTrans.remove(key);
                    }
                }
            });
        }
    }

    public static int[] computeAutoLayout(int screenHeightPx, String subHighRaw) {
        int h = screenHeightPx > 0 ? screenHeightPx : 1080;
        int offset = 0;
        boolean hasOffset = false;
        if (subHighRaw != null && !subHighRaw.trim().isEmpty()) {
            try {
                int v = Integer.parseInt(subHighRaw.trim());
                offset = (90 - v) / 2;
                hasOffset = true;
            } catch (Exception ignored) {
            }
        }
        int fontStep = 10;
        int posPct = hasOffset ? (10 + offset) : 10;
        if (h >= 1000) {
            fontStep = 9;
            posPct = hasOffset ? (11 + offset) : 11;
        }
        fontStep = Math.max(1, Math.min(20, fontStep));
        posPct = Math.max(-10, Math.min(40, posPct));
        return new int[]{fontStep, posPct};
    }

    public static String splitSubtitleText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text != null ? text : "";
        String[] words = text.split(" ");
        if (words.length <= 1) return text;
        double half = text.length() / 2.0;
        int curLen = 0;
        int bestIdx = -1;
        double minDiff = Double.MAX_VALUE;
        for (int i = 0; i < words.length - 1; i++) {
            curLen += words[i].length() + 1;
            double diff = Math.abs(curLen - half);
            if (words[i].matches(".*[.,!?]$")) {
                diff -= 15.0;
            }
            if (diff < minDiff) {
                minDiff = diff;
                bestIdx = i;
            }
        }
        if (bestIdx != -1) {
            StringBuilder a = new StringBuilder();
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i <= bestIdx) {
                    if (a.length() > 0) a.append(' ');
                    a.append(words[i]);
                } else {
                    if (b.length() > 0) b.append(' ');
                    b.append(words[i]);
                }
            }
            return a.toString() + "\n" + b.toString();
        }
        return text;
    }

    public static List<Cue> parseVttCues(String vttText) {
        List<Cue> cues = new ArrayList<>();
        if (vttText == null || vttText.trim().isEmpty()) return cues;
        String clean = vttText.replace("\uFEFF", "").replace("WEBVTT", "").replace("\r", "");
        String[] lines = clean.split("\n");
        Pattern timePattern = Pattern.compile(
                "(\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,3})?)\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,3})?)");

        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("NOTE")) {
                i++;
                continue;
            }
            Matcher m = timePattern.matcher(line);
            if (m.find()) {
                boolean isTopLine = line.contains("line:2%");
                double start = parseTimestamp(m.group(1));
                double end = parseTimestamp(m.group(2));
                i++;
                StringBuilder buf = new StringBuilder();
                while (i < lines.length && !lines[i].trim().isEmpty() && !lines[i].contains("-->")) {
                    String stripped = lines[i].replaceAll("<[^>]*>", "").trim();
                    if (!stripped.isEmpty()) {
                        if (buf.length() > 0) buf.append('\n');
                        buf.append(splitSubtitleText(stripped, 43));
                    }
                    i++;
                }
                String text = buf.toString();
                if (!text.isEmpty() && end > start) {
                    boolean isTopText = text.contains("♪") || text.contains("=");
                    cues.add(new Cue(start, end, text, isTopLine || isTopText));
                }
            } else {
                i++;
            }
        }
        Collections.sort(cues, new Comparator<Cue>() {
            @Override
            public int compare(Cue a, Cue b) {
                return Double.compare(a.start, b.start);
            }
        });
        return cues;
    }

    private static double parseTimestamp(String raw) {
        try {
            String c = raw.trim().replace(',', '.');
            String[] parts = c.split(":");
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600.0
                        + Integer.parseInt(parts[1]) * 60.0
                        + Double.parseDouble(parts[2]);
            }
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60.0
                        + Double.parseDouble(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private static String fetchTextSafe(String urlStr, boolean bustCache) {
        if (urlStr == null || urlStr.trim().isEmpty()) return "";
        HttpURLConnection conn = null;
        try {
            String target = urlStr.trim();
            if (bustCache && target.startsWith("http")) {
                target += (target.contains("?") ? "&" : "?") + "_r=" + System.currentTimeMillis();
            }
            conn = (HttpURLConnection) new URL(target).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setUseCaches(!bustCache);
            conn.setRequestProperty("User-Agent", "DVACastTV/4.0");
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) return "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Custom Canvas stroked text view so TV subtitles stay razor-sharp across API 21-34
     * with zero layout jitter across all outline modes (uniform2, uniform, shadow, raise, lower, none).
     */
    public static final class OutlinedSubtitleView extends View {
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint strokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";
        private int textColor = Color.WHITE;
        private float textSizePx;
        private Typeface typeface = Typeface.DEFAULT;
        private String outline = "uniform2";
        private StaticLayout fillLayout;
        private StaticLayout strokeLayout;
        private int lastLayoutWidth = -1;

        public OutlinedSubtitleView(Context context) {
            super(context);
            textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24f, context.getResources().getDisplayMetrics());
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void update(String nextText, int color, float sizeSp, Typeface tf, String outlineMode) {
            String safeText = nextText != null ? nextText : "";
            String safeOut = outlineMode != null ? outlineMode : "uniform2";
            Typeface safeTf = tf != null ? tf : Typeface.DEFAULT;
            float sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, getResources().getDisplayMetrics());

            boolean changed = !safeText.equals(this.text)
                    || color != this.textColor
                    || Math.abs(sizePx - this.textSizePx) > 0.4f
                    || !safeOut.equals(this.outline)
                    || !safeTf.equals(this.typeface);

            if (!changed) return;
            this.text = safeText;
            this.textColor = color;
            this.textSizePx = sizePx;
            this.typeface = safeTf;
            this.outline = safeOut;
            this.lastLayoutWidth = -1;
            requestLayout();
            invalidate();
        }

        private void prepareLayouts(int availWidth) {
            int w = Math.max(100, availWidth);
            if (w == lastLayoutWidth && fillLayout != null) return;
            lastLayoutWidth = w;

            textPaint.setColor(textColor);
            textPaint.setTextSize(textSizePx);
            textPaint.setTypeface(typeface);
            textPaint.clearShadowLayer();

            strokePaint.setTextSize(textSizePx);
            strokePaint.setTypeface(typeface);
            strokePaint.setColor(Color.BLACK);

            if ("shadow".equals(outline)) {
                textPaint.setShadowLayer(4.5f, 2f, 2f, Color.argb(230, 0, 0, 0));
            } else if ("raise".equals(outline)) {
                textPaint.setShadowLayer(2f, 2f, -2f, Color.BLACK);
            } else if ("lower".equals(outline)) {
                textPaint.setShadowLayer(2f, -2f, 2f, Color.BLACK);
            } else if ("uniform".equals(outline)) {
                strokePaint.setStrokeWidth(Math.max(2.4f, textSizePx * 0.105f));
            } else if ("uniform2".equals(outline)) {
                strokePaint.setStrokeWidth(Math.max(1.8f, textSizePx * 0.08f));
            }

            fillLayout = buildLayout(text, textPaint, w);
            strokeLayout = ("uniform".equals(outline) || "uniform2".equals(outline))
                    ? buildLayout(text, strokePaint, w)
                    : null;
        }

        @SuppressWarnings("deprecation")
        private static StaticLayout buildLayout(String src, TextPaint tp, int w) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return StaticLayout.Builder.obtain(src, 0, src.length(), tp, w)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.15f)
                        .setIncludePad(false)
                        .build();
            }
            return new StaticLayout(src, tp, w, Layout.Alignment.ALIGN_CENTER, 1.15f, 0f, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (text.isEmpty()) {
                setMeasuredDimension(0, 0);
                return;
            }
            int maxW = MeasureSpec.getSize(widthMeasureSpec);
            if (maxW <= 0) {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                maxW = Math.round(dm.widthPixels * 0.86f);
            }
            int padX = UiHelper.dp(getContext(), 12);
            prepareLayouts(Math.max(120, maxW - padX * 2));
            int h = (fillLayout != null ? fillLayout.getHeight() : 0) + UiHelper.dp(getContext(), 6);
            setMeasuredDimension(maxW, Math.max(1, h));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (text.isEmpty() || fillLayout == null) return;
            int padX = UiHelper.dp(getContext(), 12);
            canvas.save();
            canvas.translate(padX, UiHelper.dp(getContext(), 2));
            if (strokeLayout != null) {
                strokeLayout.draw(canvas);
            }
            fillLayout.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * Full-screen overlay rendering top lyric/caption cues and bottom dual-language cues.
     */
    public static final class SubtitleOverlayView extends FrameLayout {
        private final SubtitleEngine engine;
        private final OutlinedSubtitleView topView;
        private final LinearLayout bottomStack;
        private final OutlinedSubtitleView lineOneView;
        private final View dividerBar;
        private final OutlinedSubtitleView lineTwoView;

        private List<Cue> vnCues = new ArrayList<>();
        private List<Cue> enCues = new ArrayList<>();
        private String subHighRaw = "";
        private double lastTimeSec = 0.0;

        public SubtitleOverlayView(Context context, SubtitleEngine engine) {
            super(context);
            this.engine = engine;
            setClickable(false);
            setFocusable(false);

            topView = new OutlinedSubtitleView(context);
            topView.setVisibility(View.GONE);
            LayoutParams topLp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            topLp.topMargin = UiHelper.dp(context, 28);
            topLp.leftMargin = UiHelper.dp(context, 48);
            topLp.rightMargin = UiHelper.dp(context, 48);
            addView(topView, topLp);

            bottomStack = new LinearLayout(context);
            bottomStack.setOrientation(LinearLayout.VERTICAL);
            bottomStack.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams botLp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            botLp.bottomMargin = UiHelper.dp(context, 62);
            botLp.leftMargin = UiHelper.dp(context, 48);
            botLp.rightMargin = UiHelper.dp(context, 48);

            lineOneView = new OutlinedSubtitleView(context);
            lineOneView.setVisibility(View.GONE);
            bottomStack.addView(lineOneView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            dividerBar = new View(context);
            dividerBar.setBackgroundColor(Color.argb(64, 255, 255, 255));
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    UiHelper.dp(context, 140), UiHelper.dp(context, 2));
            divLp.topMargin = UiHelper.dp(context, 4);
            divLp.bottomMargin = UiHelper.dp(context, 4);
            dividerBar.setVisibility(View.GONE);
            bottomStack.addView(dividerBar, divLp);

            lineTwoView = new OutlinedSubtitleView(context);
            lineTwoView.setVisibility(View.GONE);
            bottomStack.addView(lineTwoView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            addView(bottomStack, botLp);
        }

        public void setCues(List<Cue> vn, List<Cue> en, String subHigh) {
            this.vnCues = vn != null ? vn : new ArrayList<Cue>();
            this.enCues = en != null ? en : new ArrayList<Cue>();
            this.subHighRaw = subHigh != null ? subHigh : "";
            renderAt(lastTimeSec);
        }

        public Cue getActiveMainVnCue(double currentTimeSec) {
            double eff = currentTimeSec + Prefs.getSubDelaySec(getContext());
            double ext = Prefs.getSubExtendSec(getContext());
            for (Cue c : vnCues) {
                if (!c.isTop && c.start <= eff && eff <= c.end + ext) {
                    return c;
                }
            }
            for (Cue c : vnCues) {
                if (c.isTop && c.start <= eff && eff <= c.end + ext) {
                    return c;
                }
            }
            return null;
        }

        public int getActiveMainVnIndex(double currentTimeSec) {
            double eff = currentTimeSec + Prefs.getSubDelaySec(getContext());
            double ext = Prefs.getSubExtendSec(getContext());
            for (int i = 0; i < vnCues.size(); i++) {
                Cue c = vnCues.get(i);
                if (!c.isTop && c.start <= eff && eff <= c.end + ext) {
                    return i;
                }
            }
            return -1;
        }

        public void renderAt(final double currentTimeSec) {
            this.lastTimeSec = currentTimeSec;
            Context ctx = getContext();
            boolean showVi = Prefs.isSubShowVi(ctx);
            boolean showEn = Prefs.isSubShowEn(ctx);

            if (!showVi && !showEn) {
                topView.setVisibility(View.GONE);
                bottomStack.setVisibility(View.GONE);
                return;
            }

            double eff = currentTimeSec + Prefs.getSubDelaySec(ctx);
            double ext = Prefs.getSubExtendSec(ctx);

            Cue topCue = null;
            Cue mainVnCue = null;
            int mainVnIdx = -1;
            if (showVi || showEn) {
                for (int i = 0; i < vnCues.size(); i++) {
                    Cue c = vnCues.get(i);
                    if (c.start <= eff && eff <= c.end + ext) {
                        if (c.isTop && topCue == null && showVi) {
                            topCue = c;
                        } else if (!c.isTop && mainVnCue == null) {
                            mainVnCue = c;
                            mainVnIdx = i;
                        }
                    }
                }
            }

            String transLang = Prefs.getSubTranslateLang(ctx);
            Cue mainEnCue = null;
            if (showEn && transLang.isEmpty()) {
                for (Cue c : enCues) {
                    if (!c.isTop && c.start <= eff && eff <= c.end + ext) {
                        mainEnCue = c;
                        break;
                    }
                }
            }

            if (showEn && !transLang.isEmpty() && mainVnIdx >= 0) {
                engine.prefetchTranslations(transLang, vnCues, mainVnIdx, new Runnable() {
                    @Override
                    public void run() {
                        renderAt(lastTimeSec);
                    }
                });
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels > 0 ? dm.heightPixels : 1080;
            int[] auto = computeAutoLayout(screenH, subHighRaw);
            boolean autoLayout = Prefs.isSubAutoLayout(ctx);
            int fontStep = autoLayout ? auto[0] : Prefs.getSubFontStep(ctx);
            int posPct = autoLayout ? auto[1] : Prefs.getSubPositionPct(ctx);

            // Calibrated crisp TV readability (1..20 -> ~14sp..36sp)
            float primarySp = Math.max(14f, Math.min(40f, 12f + fontStep * 1.25f));
            float secondarySp = Math.max(12f, primarySp * 0.78f);
            ColorOption colorOpt = findColor(Prefs.getSubColor(ctx));
            Typeface tf = resolveTypeface(Prefs.getSubFont(ctx));
            String outline = Prefs.getSubOutline(ctx);
            boolean reversed = Prefs.isSubReverse(ctx);

            // Top position margin calculation
            LayoutParams botLp = (LayoutParams) bottomStack.getLayoutParams();
            int newMargin = Math.max(UiHelper.dp(ctx, 12), Math.round((screenH * posPct) / 100f));
            if (botLp.bottomMargin != newMargin) {
                botLp.bottomMargin = newMargin;
                bottomStack.setLayoutParams(botLp);
            }

            if (topCue != null && !topCue.text.isEmpty()) {
                topView.setVisibility(View.VISIBLE);
                topView.update(topCue.text, colorOpt.mainColor, primarySp, tf, outline);
            } else {
                topView.setVisibility(View.GONE);
            }

            String vnText = (showVi && mainVnCue != null) ? mainVnCue.text : "";
            String secText = "";
            if (showEn) {
                if (!transLang.isEmpty() && mainVnCue != null) {
                    secText = engine.getCachedTranslation(transLang, mainVnCue.text);
                } else if (mainEnCue != null) {
                    secText = mainEnCue.text;
                }
            }

            boolean hasVn = !vnText.isEmpty();
            boolean hasSec = !secText.isEmpty();
            if (!hasVn && !hasSec) {
                bottomStack.setVisibility(View.GONE);
                return;
            }
            bottomStack.setVisibility(View.VISIBLE);

            if (hasVn && hasSec) {
                dividerBar.setVisibility(View.VISIBLE);
                lineOneView.setVisibility(View.VISIBLE);
                lineTwoView.setVisibility(View.VISIBLE);
                if (reversed) {
                    lineOneView.update(secText, colorOpt.mainColor, primarySp, tf, outline);
                    lineTwoView.update(vnText, colorOpt.subColor, secondarySp, tf, outline);
                } else {
                    lineOneView.update(vnText, colorOpt.mainColor, primarySp, tf, outline);
                    lineTwoView.update(secText, colorOpt.subColor, secondarySp, tf, outline);
                }
            } else if (hasVn) {
                dividerBar.setVisibility(View.GONE);
                lineOneView.setVisibility(View.VISIBLE);
                lineTwoView.setVisibility(View.GONE);
                lineOneView.update(vnText, colorOpt.mainColor, primarySp, tf, outline);
            } else {
                dividerBar.setVisibility(View.GONE);
                lineOneView.setVisibility(View.VISIBLE);
                lineTwoView.setVisibility(View.GONE);
                lineOneView.update(secText, colorOpt.mainColor, primarySp, tf, outline);
            }
        }
    }
}
