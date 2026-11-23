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
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    public static final int COLOR_BG_BASE = Color.parseColor("#090B12");
    public static final int COLOR_SURFACE_GLASS = Color.argb(214, 15, 17, 26);
    public static final int COLOR_SURFACE_CARD = Color.argb(185, 22, 25, 38);
    public static final int COLOR_BORDER_GLASS = Color.argb(24, 255, 255, 255);
    public static final int COLOR_BTN_FOCUSED = Color.argb(46, 255, 255, 255);
    public static final int COLOR_ACCENT_RED = Color.parseColor("#E50914");
    public static final int COLOR_ACCENT_PINK = Color.parseColor("#D50055");
    public static final int COLOR_FOCUS_GLOW = Color.parseColor("#FF2E63");
    public static final int COLOR_FOCUS_WHITE_GLOW = Color.argb(235, 255, 255, 255);
    public static final int COLOR_TEXT_PRIMARY = Color.parseColor("#F5F6FA");
    public static final int COLOR_TEXT_SECONDARY = Color.parseColor("#CBD3E4");
    public static final int COLOR_TEXT_MUTED = Color.parseColor("#8E98AE");
    public static final int COLOR_GREEN = Color.parseColor("#30D158");
    public static final int COLOR_GOLD = Color.parseColor("#F5C518");
    public static final int COLOR_CYAN = Color.parseColor("#38BDF8");

    private UiHelper() {
    }

    public static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    public static float dpF(Context ctx, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    public static boolean supportsNativeBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void setBackdropBlur(View view, boolean enabled) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                view.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP));
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
                    float radius = Math.max(w, h) * 0.56f;
                    g1Paint.setShader(new RadialGradient(
                            w * 0.16f, h * 0.18f, radius,
                            Color.argb(22, 229, 9, 20), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    g2Paint.setShader(new RadialGradient(
                            w * 0.84f, h * 0.82f, radius,
                            Color.argb(18, 92, 60, 180), Color.TRANSPARENT, Shader.TileMode.CLAMP));
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
                    basePaint.setColor(Color.argb(218, 9, 11, 18));
                    float radius = Math.max(w, h) * 0.58f;
                    g1Paint.setShader(new RadialGradient(
                            w * 0.18f, h * 0.18f, radius,
                            Color.argb(24, 229, 9, 20), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    g2Paint.setShader(new RadialGradient(
                            w * 0.82f, h * 0.82f, radius,
                            Color.argb(20, 110, 68, 210), Color.TRANSPARENT, Shader.TileMode.CLAMP));
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
     * Refined Frosted Obsidian Glass drawable (slim radii <= 20dp, delicate hairline rim,
     * crisp TV focus outline, zero cloudy white fog).
     */
    public static Drawable createLiquidGlassDrawable(
            Context ctx, int radiusDp, final boolean isModalCard, final boolean isFocused, final boolean isActiveAccent) {
        final int effRadius = isModalCard ? Math.min(radiusDp, 20) : Math.min(radiusDp, 18);
        final float radiusPx = dpF(ctx, effRadius);
        final float strokePx = dpF(ctx, isFocused ? 2.2f : 1.0f);
        final float glowStrokePx = dpF(ctx, isFocused ? 5.0f : 0f);
        return new Drawable() {
            private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path clipPath = new Path();
            private final RectF rectF = new RectF();
            private final RectF strokeRect = new RectF();
            private final RectF glowRect = new RectF();
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

                    float inset = strokePx * 0.5f;
                    strokeRect.set(rectF.left + inset, rectF.top + inset, rectF.right - inset, rectF.bottom - inset);
                    float glowInset = glowStrokePx * 0.5f;
                    glowRect.set(rectF.left + glowInset, rectF.top + glowInset, rectF.right - glowInset, rectF.bottom - glowInset);

                    if (isActiveAccent) {
                        basePaint.setShader(new LinearGradient(
                                0, 0, w, h,
                                isFocused ? Color.parseColor("#FF2A42") : Color.parseColor("#E50914"),
                                isFocused ? Color.parseColor("#FF1F7A") : Color.parseColor("#C90052"),
                                Shader.TileMode.CLAMP));
                    } else if (isFocused) {
                        basePaint.setShader(new LinearGradient(
                                0, 0, 0, h,
                                Color.argb(244, 46, 52, 76),
                                Color.argb(236, 28, 32, 48),
                                Shader.TileMode.CLAMP));
                    } else if (isModalCard) {
                        basePaint.setShader(new LinearGradient(
                                0, 0, 0, h,
                                supportsNativeBlur() ? Color.argb(198, 17, 19, 30) : Color.argb(232, 16, 18, 28),
                                supportsNativeBlur() ? Color.argb(190, 12, 14, 22) : Color.argb(226, 11, 13, 20),
                                Shader.TileMode.CLAMP));
                    } else {
                        basePaint.setShader(new LinearGradient(
                                0, 0, 0, h,
                                supportsNativeBlur() ? Color.argb(166, 22, 25, 38) : Color.argb(204, 20, 23, 35),
                                supportsNativeBlur() ? Color.argb(154, 15, 17, 27) : Color.argb(192, 14, 16, 25),
                                Shader.TileMode.CLAMP));
                    }

                    sheenPaint.setShader(new LinearGradient(
                            0, 0, 0, Math.max(1f, h * 0.24f),
                            Color.argb(isFocused ? 46 : 14, 255, 255, 255),
                            Color.TRANSPARENT,
                            Shader.TileMode.CLAMP));

                    if (isFocused) {
                        glowPaint.setStyle(Paint.Style.STROKE);
                        glowPaint.setStrokeWidth(glowStrokePx);
                        glowPaint.setColor(isActiveAccent
                                ? Color.argb(145, 255, 160, 195)
                                : Color.argb(120, 255, 46, 99));
                    }

                    rimPaint.setStyle(Paint.Style.STROKE);
                    rimPaint.setStrokeWidth(strokePx);
                    if (isFocused) {
                        rimPaint.setColor(Color.WHITE);
                    } else if (isActiveAccent) {
                        rimPaint.setColor(Color.argb(72, 255, 175, 195));
                    } else {
                        rimPaint.setColor(Color.argb(24, 255, 255, 255));
                    }
                }

                canvas.save();
                canvas.clipPath(clipPath);
                canvas.drawRect(rectF, basePaint);
                canvas.drawRect(rectF.left, rectF.top, rectF.right, rectF.top + (h * 0.26f), sheenPaint);
                if (isFocused) {
                    canvas.drawRoundRect(glowRect, Math.max(0f, radiusPx - glowStrokePx * 0.5f),
                            Math.max(0f, radiusPx - glowStrokePx * 0.5f), glowPaint);
                }
                canvas.drawRoundRect(strokeRect, Math.max(0f, radiusPx - strokePx * 0.5f),
                        Math.max(0f, radiusPx - strokePx * 0.5f), rimPaint);
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

    public static Drawable createFocusRingDrawable(Context ctx, int radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dpF(ctx, radiusDp));
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(2, dp(ctx, strokeDp)), strokeColor);
        return d;
    }

    /**
     * Luminous double-layer focus ring (soft outer neon halo + bright core rim),
     * compatible across Android TV API 21-34.
     */
    public static Drawable createGlowingFocusRingDrawable(Context ctx, int radiusDp, int glowColor, float strokeDp) {
        int r = Color.red(glowColor);
        int g = Color.green(glowColor);
        int b = Color.blue(glowColor);

        GradientDrawable outerHalo = new GradientDrawable();
        outerHalo.setShape(GradientDrawable.RECTANGLE);
        outerHalo.setCornerRadius(dpF(ctx, radiusDp + 2));
        outerHalo.setColor(Color.argb(26, r, g, b));
        outerHalo.setStroke(Math.max(5, dp(ctx, strokeDp * 2.5f)), Color.argb(120, r, g, b));

        int coreColor = Color.argb(
                250,
                Math.min(255, (r * 55 + 255 * 45) / 100),
                Math.min(255, (g * 55 + 255 * 45) / 100),
                Math.min(255, (b * 55 + 255 * 45) / 100));
        GradientDrawable innerRim = new GradientDrawable();
        innerRim.setShape(GradientDrawable.RECTANGLE);
        innerRim.setCornerRadius(dpF(ctx, radiusDp));
        innerRim.setColor(Color.TRANSPARENT);
        innerRim.setStroke(Math.max(2, dp(ctx, strokeDp)), coreColor);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{outerHalo, innerRim});
        int inset = Math.max(2, dp(ctx, 1.5f));
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    public static Drawable createPillDrawable(Context ctx, int radiusDp, int fillColor, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dpF(ctx, radiusDp));
        d.setColor(fillColor);
        if (strokeDp > 0f && strokeColor != Color.TRANSPARENT) {
            d.setStroke(Math.max(1, dp(ctx, strokeDp)), strokeColor);
        }
        return d;
    }

    public static Drawable createSidebarItemStateDrawable(Context ctx, int radiusDp, boolean active) {
        Drawable normal;
        if (active) {
            GradientDrawable act = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#E50914"), Color.parseColor("#C90052")});
            act.setCornerRadius(dpF(ctx, radiusDp));
            normal = act;
        } else {
            normal = createGlassRect(ctx, radiusDp, Color.TRANSPARENT, Color.TRANSPARENT, 0);
        }

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(dpF(ctx, radiusDp));
        if (active) {
            focused.setColors(new int[]{Color.parseColor("#FF2E50"), Color.parseColor("#FF0066")});
            focused.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        } else {
            focused.setColor(Color.argb(58, 255, 255, 255));
        }
        focused.setStroke(Math.max(2, dp(ctx, 2.0f)), Color.WHITE);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    public static GradientDrawable createGlassRect(Context ctx, int radiusDp, int fillColor, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(ctx, radiusDp));
        d.setColor(fillColor);
        if (strokeDp > 0 && strokeColor != Color.TRANSPARENT) {
            d.setStroke(dp(ctx, strokeDp), strokeColor);
        }
        return d;
    }

    public static void applyGlassCardStyle(final View view, final Context ctx, final int radiusDp, int fillColor, int shadowDp) {
        int effRadius = Math.min(radiusDp, 20);
        if (fillColor == COLOR_SURFACE_GLASS || fillColor == COLOR_SURFACE_CARD) {
            view.setBackground(createLiquidGlassDrawable(ctx, effRadius, true, false, false));
        } else {
            view.setBackground(createGlassRect(ctx, effRadius, fillColor, COLOR_BORDER_GLASS, 1));
        }
        applyElevationAndOutline(view, ctx, effRadius, Math.min(shadowDp, 16));
    }

    public static void applySubtleGlassPill(final View view, final Context ctx, final int radiusDp) {
        int effRadius = Math.min(radiusDp, 18);
        view.setBackground(createPillDrawable(ctx, effRadius,
                Color.argb(170, 18, 20, 32), Color.argb(26, 255, 255, 255), 1.0f));
        applyElevationAndOutline(view, ctx, effRadius, 6);
    }

    public static void applyFocusableTileStyle(final View view, final Context ctx, final int radiusDp, int normalFill, int focusedFill) {
        int effRadius = Math.min(radiusDp, 16);
        Drawable normal = (normalFill == COLOR_SURFACE_GLASS || normalFill == COLOR_SURFACE_CARD)
                ? createLiquidGlassDrawable(ctx, effRadius, false, false, false)
                : createGlassRect(ctx, effRadius, normalFill, COLOR_BORDER_GLASS, 1);
        Drawable focused = createLiquidGlassDrawable(ctx, effRadius, false, true, false);
        Drawable pressed = createLiquidGlassDrawable(ctx, effRadius, false, true, false);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, effRadius, 1.08f, 8);
    }

    public static void applyFocusableGlassButton(final View view, final Context ctx, final int radiusDp, boolean isPrimaryAccent) {
        int effRadius = Math.min(radiusDp, 18);
        Drawable normal = createLiquidGlassDrawable(ctx, effRadius, false, false, isPrimaryAccent);
        Drawable focused = createLiquidGlassDrawable(ctx, effRadius, false, true, isPrimaryAccent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, effRadius, 1.09f, 8);
    }

    public static void applyCircularIconButtonStyle(final View view, final Context ctx, final int sizeDp) {
        int r = Math.max(12, sizeDp / 2);
        Drawable normal = createPillDrawable(ctx, r, Color.argb(170, 20, 22, 34), Color.argb(32, 255, 255, 255), 1.0f);
        Drawable focused = createPillDrawable(ctx, r, Color.argb(242, 48, 54, 82), Color.WHITE, 2.0f);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, r, 1.14f, 6);
    }

    public static void applyCompactChipStyle(final View view, final Context ctx, final int radiusDp, boolean selected) {
        int r = Math.min(radiusDp, 14);
        Drawable normal;
        if (selected) {
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#E50914"), Color.parseColor("#C90052")});
            g.setCornerRadius(dpF(ctx, r));
            normal = g;
        } else {
            normal = createPillDrawable(ctx, r, Color.argb(175, 22, 25, 38), Color.argb(28, 255, 255, 255), 1.0f);
        }
        Drawable focused;
        if (selected) {
            GradientDrawable fg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#FF2A42"), Color.parseColor("#FF1F7A")});
            fg.setCornerRadius(dpF(ctx, r));
            fg.setStroke(Math.max(2, dp(ctx, 2.0f)), Color.WHITE);
            focused = fg;
        } else {
            focused = createPillDrawable(ctx, r, Color.argb(240, 50, 56, 84), Color.WHITE, 2.0f);
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, r, 1.08f, 6);
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

    public static void applyFocusVisualEffects(
            View view, Context ctx, boolean hasFocus, float scaleOnFocus, int baseShadowDp, int glowColor) {
        if (view == null || ctx == null) return;
        float targetScale = hasFocus ? scaleOnFocus : 1.0f;
        float targetZ = hasFocus ? dpF(ctx, 14) : 0f;
        view.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetZ)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        view.setElevation(hasFocus ? dp(ctx, baseShadowDp + 10) : dp(ctx, baseShadowDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (hasFocus) {
                view.setOutlineSpotShadowColor(glowColor);
                view.setOutlineAmbientShadowColor(Color.argb(
                        195, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)));
            } else {
                view.setOutlineSpotShadowColor(Color.argb(210, 0, 0, 0));
                view.setOutlineAmbientShadowColor(Color.argb(165, 0, 0, 0));
            }
        }
    }

    public static void applyTvFocusZoomGlow(
            final View view, final Context ctx, final float scaleOnFocus, final int baseShadowDp, final int glowColor) {
        if (view == null || ctx == null) return;
        view.setFocusable(true);
        view.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        view.setElevation(dp(ctx, baseShadowDp));
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyFocusVisualEffects(v, ctx, hasFocus, scaleOnFocus, baseShadowDp, glowColor);
            }
        });
    }

    private static void configureTvFocus(final View view, final Context ctx, final int radiusDp, final float scaleOnFocus, final int baseShadowDp) {
        view.setFocusable(true);
        view.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        applyElevationAndOutline(view, ctx, radiusDp, baseShadowDp);
        final float effScale = scaleOnFocus <= 1.045f ? 1.08f : scaleOnFocus;
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyFocusVisualEffects(v, ctx, hasFocus, effScale, baseShadowDp, COLOR_FOCUS_GLOW);
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
                .transition(DrawableTransitionOptions.withCrossFade(320))
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

    // ---- Floating Frosted Toast ----

    public static void showGlassToast(final FrameLayout host, Context ctx, String message, int accentColor) {
        if (host == null || message == null || message.trim().isEmpty()) return;
        final LinearLayout toastPill = new LinearLayout(ctx);
        toastPill.setOrientation(LinearLayout.HORIZONTAL);
        toastPill.setGravity(Gravity.CENTER_VERTICAL);
        toastPill.setPadding(dp(ctx, 18), dp(ctx, 9), dp(ctx, 20), dp(ctx, 9));
        applyGlassCardStyle(toastPill, ctx, 18, COLOR_SURFACE_GLASS, 14);

        View dot = new View(ctx);
        dot.setBackground(createGlassRect(ctx, 8, accentColor != 0 ? accentColor : COLOR_ACCENT_RED, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8));
        dotLp.rightMargin = dp(ctx, 10);
        toastPill.addView(dot, dotLp);

        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextColor(COLOR_TEXT_PRIMARY);
        tv.setTextSize(13f);
        toastPill.addView(tv);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(ctx, 28);
        toastPill.setAlpha(0f);
        toastPill.setTranslationY(dp(ctx, 12));
        host.addView(toastPill, lp);

        toastPill.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        toastPill.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                toastPill.animate()
                                        .alpha(0f)
                                        .translationY(dp(toastPill.getContext(), 8))
                                        .setDuration(220)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                host.removeView(toastPill);
                                            }
                                        })
                                        .start();
                            }
                        }, 2400);
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
