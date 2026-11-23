package com.dangvietanh.cast;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UiHelper {

    public static final int COLOR_BG_BASE = Color.parseColor("#07090F");
    // Exact borderless Liquid Glass base: rgba(20, 20, 20, 0.2)
    public static final int COLOR_SURFACE_GLASS = Color.argb(51, 20, 20, 20);
    public static final int COLOR_SURFACE_CARD = Color.argb(66, 20, 20, 20);
    public static final int COLOR_BORDER_GLASS = Color.TRANSPARENT;
    public static final int COLOR_BTN_FOCUSED = Color.argb(72, 255, 255, 255);
    public static final int COLOR_ACCENT_RED = Color.parseColor("#E50914");
    public static final int COLOR_ACCENT_PINK = Color.parseColor("#D50055");
    public static final int COLOR_TEXT_PRIMARY = Color.parseColor("#F5F6FA");
    public static final int COLOR_TEXT_SECONDARY = Color.parseColor("#D4DBE8");
    public static final int COLOR_TEXT_MUTED = Color.parseColor("#B0B8C8");
    public static final int COLOR_GREEN = Color.parseColor("#30D158");
    public static final int COLOR_GOLD = Color.parseColor("#F5C518");
    public static final int COLOR_CYAN = Color.parseColor("#38BDF8");

    private UiHelper() {
    }

    public static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    public static boolean supportsNativeBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void setBackdropBlur(View view, boolean enabled) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                view.setRenderEffect(RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP));
            } else {
                view.setRenderEffect(null);
            }
        }
    }

    public static Drawable createCastBackgroundDrawable() {
        return new Drawable() {
            private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint g1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint g2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int lastW = -1;
            private int lastH = -1;

            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                int w = b.width();
                int h = b.height();
                if (w <= 0 || h <= 0) return;
                if (w != lastW || h != lastH) {
                    lastW = w;
                    lastH = h;
                    basePaint.setColor(COLOR_BG_BASE);
                    float radius = Math.max(w, h) * 0.52f;
                    g1Paint.setShader(new RadialGradient(
                            w * 0.20f, h * 0.20f, radius,
                            Color.argb(42, 229, 9, 20), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    g2Paint.setShader(new RadialGradient(
                            w * 0.80f, h * 0.80f, radius,
                            Color.argb(32, 213, 0, 85), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                }
                canvas.drawRect(b, basePaint);
                canvas.drawRect(b, g1Paint);
                canvas.drawRect(b, g2Paint);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

    public static Drawable createOverlayBackdropDrawable() {
        return new Drawable() {
            private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint g1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint g2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int lastW = -1;
            private int lastH = -1;

            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                int w = b.width();
                int h = b.height();
                if (w <= 0 || h <= 0) return;
                if (w != lastW || h != lastH) {
                    lastW = w;
                    lastH = h;
                    basePaint.setColor(Color.argb(214, 7, 9, 15));
                    float radius = Math.max(w, h) * 0.56f;
                    g1Paint.setShader(new RadialGradient(
                            w * 0.20f, h * 0.20f, radius,
                            Color.argb(46, 229, 9, 20), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    g2Paint.setShader(new RadialGradient(
                            w * 0.80f, h * 0.80f, radius,
                            Color.argb(34, 213, 0, 85), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                }
                canvas.drawRect(b, basePaint);
                canvas.drawRect(b, g1Paint);
                canvas.drawRect(b, g2Paint);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    /**
     * Borderless Liquid Glass surface drawable:
     * Translucent rgba(20, 20, 20, 0.2) base + optical faux-glass refraction gradients inside rounded clip
     * so both modern Android 12+ TVs and older Android TVs without backdrop blur look smooth and borderless.
     */
    public static Drawable createLiquidGlassDrawable(
            Context ctx, int radiusDp, final boolean isModalCard, final boolean isFocused, final boolean isActiveAccent) {
        final float radiusPx = dp(ctx, radiusDp);
        return new Drawable() {
            private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path clipPath = new Path();
            private final RectF rectF = new RectF();
            private int lastW = -1;
            private int lastH = -1;

            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                int w = b.width();
                int h = b.height();
                if (w <= 0 || h <= 0) return;

                if (w != lastW || h != lastH) {
                    lastW = w;
                    lastH = h;
                    rectF.set(b);
                    clipPath.reset();
                    clipPath.addRoundRect(rectF, radiusPx, radiusPx, Path.Direction.CW);

                    if (isActiveAccent) {
                        basePaint.setShader(new LinearGradient(
                                0, 0, w, h,
                                isFocused ? Color.argb(245, 240, 24, 38) : Color.argb(225, 229, 9, 20),
                                isFocused ? Color.argb(245, 225, 16, 102) : Color.argb(220, 213, 0, 85),
                                Shader.TileMode.CLAMP));
                    } else if (isFocused) {
                        basePaint.setShader(new LinearGradient(
                                0, 0, 0, h,
                                Color.argb(92, 255, 255, 255),
                                Color.argb(58, 220, 226, 240),
                                Shader.TileMode.CLAMP));
                    } else if (isModalCard) {
                        // Modal Liquid Glass: rgba(20, 20, 20, 0.2) layered with faux ambient depth
                        basePaint.setShader(new LinearGradient(
                                0, 0, w, h,
                                supportsNativeBlur() ? Color.argb(72, 20, 20, 20) : Color.argb(138, 20, 20, 24),
                                supportsNativeBlur() ? Color.argb(58, 16, 16, 20) : Color.argb(122, 14, 15, 20),
                                Shader.TileMode.CLAMP));
                    } else {
                        basePaint.setShader(new LinearGradient(
                                0, 0, 0, h,
                                supportsNativeBlur() ? Color.argb(62, 24, 24, 28) : Color.argb(104, 22, 22, 26),
                                supportsNativeBlur() ? Color.argb(46, 18, 18, 22) : Color.argb(88, 16, 16, 20),
                                Shader.TileMode.CLAMP));
                    }

                    // Internal specular refraction sheen (strictly inside clip, zero perimeter border)
                    int topAlpha = isFocused ? 54 : (isModalCard ? 24 : 20);
                    int midAlpha = isFocused ? 14 : 5;
                    int botAlpha = isFocused ? 22 : 8;
                    sheenPaint.setShader(new LinearGradient(
                            0, 0, 0, h,
                            new int[]{
                                    Color.argb(topAlpha, 255, 255, 255),
                                    Color.argb(midAlpha, 255, 255, 255),
                                    Color.TRANSPARENT,
                                    Color.argb(botAlpha, 255, 255, 255)
                            },
                            new float[]{0f, 0.22f, 0.65f, 1f},
                            Shader.TileMode.CLAMP));

                    specPaint.setShader(new RadialGradient(
                            w * 0.22f, h * 0.14f, Math.max(w, h) * 0.62f,
                            Color.argb(isFocused ? 40 : (isModalCard ? 22 : 16), 255, 255, 255),
                            Color.TRANSPARENT,
                            Shader.TileMode.CLAMP));
                }

                canvas.save();
                canvas.clipPath(clipPath);
                canvas.drawRect(rectF, basePaint);
                canvas.drawRect(rectF, sheenPaint);
                canvas.drawRect(rectF, specPaint);
                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }

    public static GradientDrawable createGlassRect(Context ctx, int radiusDp, int fillColor, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(ctx, radiusDp));
        d.setColor(fillColor);
        return d;
    }

    public static void applyGlassCardStyle(final View view, final Context ctx, final int radiusDp, int fillColor, int shadowDp) {
        if (fillColor == COLOR_SURFACE_GLASS || fillColor == COLOR_SURFACE_CARD) {
            view.setBackground(createLiquidGlassDrawable(ctx, radiusDp, radiusDp >= 32, false, false));
        } else {
            view.setBackground(createGlassRect(ctx, radiusDp, fillColor, Color.TRANSPARENT, 0));
        }
        applyElevationAndOutline(view, ctx, radiusDp, Math.max(shadowDp, 14));
    }

    public static void applySubtleGlassPill(final View view, final Context ctx, final int radiusDp) {
        view.setBackground(createLiquidGlassDrawable(ctx, radiusDp, false, false, false));
        applyElevationAndOutline(view, ctx, radiusDp, 8);
    }

    public static void applyFocusableTileStyle(final View view, final Context ctx, final int radiusDp, int normalFill, int focusedFill) {
        Drawable normal = (normalFill == COLOR_SURFACE_GLASS || normalFill == COLOR_SURFACE_CARD)
                ? createLiquidGlassDrawable(ctx, radiusDp, false, false, false)
                : createGlassRect(ctx, radiusDp, normalFill, Color.TRANSPARENT, 0);
        Drawable focused = createLiquidGlassDrawable(ctx, radiusDp, false, true, false);
        Drawable pressed = createLiquidGlassDrawable(ctx, radiusDp, false, true, false);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, radiusDp, 1.04f, 12);
    }

    public static void applyFocusableGlassButton(final View view, final Context ctx, final int radiusDp, boolean isPrimaryAccent) {
        Drawable normal = createLiquidGlassDrawable(ctx, radiusDp, false, false, isPrimaryAccent);
        Drawable focused = createLiquidGlassDrawable(ctx, radiusDp, false, true, isPrimaryAccent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, radiusDp, 1.06f, 14);
    }

    public static void applyPrimaryGradientButton(final View view, final Context ctx, final int radiusDp) {
        applyFocusableGlassButton(view, ctx, radiusDp, true);
    }

    public static void setButtonSelectedVisual(final View view, final Context ctx, final int radiusDp, boolean selected) {
        applyFocusableGlassButton(view, ctx, radiusDp, selected);
    }

    private static void applyElevationAndOutline(final View view, final Context ctx, final int radiusDp, int shadowDp) {
        view.setElevation(dp(ctx, shadowDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setOutlineAmbientShadowColor(Color.argb(165, 0, 0, 0));
            view.setOutlineSpotShadowColor(Color.argb(210, 0, 0, 0));
        }
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(ctx, radiusDp));
            }
        });
        view.setClipToOutline(true);
    }

    private static void configureTvFocus(final View view, final Context ctx, final int radiusDp, final float scaleOnFocus, final int baseShadowDp) {
        view.setFocusable(true);
        view.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        applyElevationAndOutline(view, ctx, radiusDp, baseShadowDp);
        final float normalElev = dp(ctx, baseShadowDp);
        final float focusElev = dp(ctx, baseShadowDp + 10);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float targetScale = hasFocus ? scaleOnFocus : 1.0f;
                v.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(140)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                v.setElevation(hasFocus ? focusElev : normalElev);
            }
        });
    }

    // ---- Glide Image Loading ----

    private static boolean isContextValidForGlide(Context ctx) {
        if (ctx == null) return false;
        if (ctx instanceof Activity) {
            Activity act = (Activity) ctx;
            return !act.isFinishing() && !act.isDestroyed();
        }
        return true;
    }

    public static void safeLoadImage(ImageView target, String url, int placeholderRes, int cornerRadiusDp) {
        if (target == null) return;
        Context ctx = target.getContext();
        if (!isContextValidForGlide(ctx)) return;
        if (url == null || url.trim().isEmpty()) {
            Glide.with(ctx).clear(target);
            target.setImageResource(placeholderRes);
            return;
        }
        RequestOptions opts = new RequestOptions();
        if (cornerRadiusDp > 0) {
            opts = opts.transform(new CenterCrop(), new RoundedCorners(dp(ctx, cornerRadiusDp)));
        } else {
            opts = opts.centerCrop();
        }
        if (placeholderRes != 0) {
            opts = opts.placeholder(placeholderRes).error(placeholderRes);
        }
        Glide.with(ctx)
                .load(url.trim())
                .apply(opts)
                .transition(DrawableTransitionOptions.withCrossFade(220))
                .into(target);
    }

    public static void safeLoadBackdrop(ImageView target, String url) {
        if (target == null) return;
        Context ctx = target.getContext();
        if (!isContextValidForGlide(ctx)) return;
        if (url == null || url.trim().isEmpty()) {
            target.setImageResource(R.drawable.default_poster);
            return;
        }
        Glide.with(ctx)
                .load(url.trim())
                .apply(new RequestOptions()
                        .centerCrop()
                        .placeholder(R.drawable.default_poster)
                        .error(R.drawable.default_poster))
                .transition(DrawableTransitionOptions.withCrossFade(350))
                .into(target);
    }

    public static void safeLoadAvatar(ImageView target, String url) {
        if (target == null) return;
        Context ctx = target.getContext();
        if (!isContextValidForGlide(ctx)) return;
        if (url == null || url.trim().isEmpty()) {
            target.setImageResource(R.drawable.ic_person);
            return;
        }
        Glide.with(ctx)
                .load(url.trim())
                .apply(new RequestOptions()
                        .circleCrop()
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person))
                .into(target);
    }

    // ---- ZXing QR Code Generator ----

    public static Bitmap generateQrBitmap(String content, int sizePx) {
        if (content == null || content.trim().isEmpty() || sizePx <= 0) return null;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(
                    content.trim(), BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? 0xFF0A0D14 : 0xFFFFFFFF;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Floating Liquid Glass Toast ----

    public static void showGlassToast(final FrameLayout host, Context ctx, String message, int accentColor) {
        if (host == null || message == null || message.trim().isEmpty()) return;
        final LinearLayout toastPill = new LinearLayout(ctx);
        toastPill.setOrientation(LinearLayout.HORIZONTAL);
        toastPill.setGravity(Gravity.CENTER_VERTICAL);
        toastPill.setPadding(dp(ctx, 20), dp(ctx, 11), dp(ctx, 22), dp(ctx, 11));
        applyGlassCardStyle(toastPill, ctx, 24, COLOR_SURFACE_GLASS, 18);

        View dot = new View(ctx);
        dot.setBackground(createGlassRect(ctx, 8, accentColor != 0 ? accentColor : COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 9), dp(ctx, 9));
        dotLp.rightMargin = dp(ctx, 10);
        toastPill.addView(dot, dotLp);

        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextColor(COLOR_TEXT_PRIMARY);
        tv.setTextSize(14f);
        toastPill.addView(tv);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(ctx, 36);
        toastPill.setAlpha(0f);
        toastPill.setTranslationY(dp(ctx, 14));
        host.addView(toastPill, lp);

        toastPill.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        toastPill.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                toastPill.animate()
                                        .alpha(0f)
                                        .translationY(dp(toastPill.getContext(), 10))
                                        .setDuration(240)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                host.removeView(toastPill);
                                            }
                                        })
                                        .start();
                            }
                        }, 2600);
                    }
                })
                .start();
    }

    public static String getWifiIpAddress(Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            String fallbackIp = null;
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                String name = intf.getName().toLowerCase(Locale.ROOT);
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (name.startsWith("wlan") || name.startsWith("eth")) {
                            return ip;
                        }
                        if (fallbackIp == null) {
                            fallbackIp = ip;
                        }
                    }
                }
            }
            if (fallbackIp != null) return fallbackIp;
        } catch (Exception ignored) {
        }

        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            (ipInt & 0xff), (ipInt >> 8 & 0xff),
                            (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) {
        }
        return "Chưa kết nối Wi-Fi";
    }

    /**
     * Smooth animated iOS/Apple-style rotating arc spinner without 0/360 SweepGradient cap seam.
     */
    public static class SpinnerView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private final float safeMarginPx;
        private float angle = 0f;
        private boolean spinning = true;

        public SpinnerView(Context context) {
            super(context);
            float stroke = dp(context, 2.5f);
            safeMarginPx = stroke / 2f + dp(context, 2f);

            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(stroke);
            trackPaint.setColor(Color.argb(34, 255, 255, 255));

            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(stroke);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setSpinning(boolean active) {
            this.spinning = active;
            if (active) postInvalidateOnAnimation();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            arcRect.set(safeMarginPx, safeMarginPx, w - safeMarginPx, h - safeMarginPx);
            arcPaint.setShader(new SweepGradient(
                    w / 2f, h / 2f,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                            COLOR_ACCENT_PINK,
                            COLOR_ACCENT_RED,
                            Color.WHITE,
                            Color.WHITE,
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.06f, 0.35f, 0.65f, 0.80f, 0.90f, 1f}));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (arcRect.width() <= 0 || arcRect.height() <= 0) return;
            canvas.drawOval(arcRect, trackPaint);
            canvas.save();
            canvas.rotate(angle, getWidth() / 2f, getHeight() / 2f);
            canvas.drawArc(arcRect, 22f, 255f, false, arcPaint);
            canvas.restore();
            if (spinning && getVisibility() == View.VISIBLE) {
                angle = (angle + 7.2f) % 360f;
                postInvalidateOnAnimation();
            }
        }
    }
}
