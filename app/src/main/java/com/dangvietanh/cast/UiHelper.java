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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
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
    public static final int COLOR_BORDER_GLASS = Color.TRANSPARENT;
    public static final int GLASS_RADIUS_DP = 40;
    public static final int COLOR_GLASS_BLUR_FILL = Color.argb(51, 20, 20, 20);
    public static final int COLOR_GLASS_FALLBACK_FILL = Color.argb(102, 20, 20, 20);
    public static final int COLOR_GLASS_SHADOW_SPOT = Color.argb(128, 0, 0, 0);
    public static final int COLOR_GLASS_SHADOW_AMBIENT = Color.argb(96, 0, 0, 0);
    public static final int COLOR_BTN_FOCUSED = Color.argb(46, 255, 255, 255);
    public static final int COLOR_ACCENT_RED = Color.parseColor("#E50914");
    public static final int COLOR_ACCENT_PINK = Color.parseColor("#D50055");
    public static final int COLOR_FOCUS_BORDER = Color.parseColor("#F5FFFFFF");
    public static final float FOCUS_BORDER_DP = 2.5f;
    public static final float FOCUS_SCALE_DEFAULT = 1.06f;
    public static final int COLOR_FOCUS_SHADOW_SPOT = Color.argb(185, 0, 0, 0);
    public static final int COLOR_FOCUS_SHADOW_AMBIENT = Color.argb(130, 0, 0, 0);
    public static final int COLOR_FOCUS_GLOW = COLOR_FOCUS_BORDER;
    public static final int COLOR_FOCUS_WHITE_GLOW = COLOR_FOCUS_BORDER;
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

    public static int getGlassBackgroundColor() {
        return supportsNativeBlur() ? COLOR_GLASS_BLUR_FILL : COLOR_GLASS_FALLBACK_FILL;
    }

    public static int getGlassFocusedBackgroundColor() {
        return supportsNativeBlur() ? Color.argb(96, 56, 56, 70) : Color.argb(148, 52, 52, 66);
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
    /**
     * Borderless Liquid Glass drawable:
     * - 40dp corner radius
     * - Zero border strokes
     * - rgba(20, 20, 20, 0.2) on API >= 31, rgba(20, 20, 20, 0.4) fallback on older TVs
     */
    public static Drawable createLiquidGlassDrawable(
            final Context ctx, int radiusDp, final boolean isModalCard, final boolean isFocused, final boolean isActiveAccent) {
        final float radiusPx = radiusDp <= 0 ? 0f : dpF(ctx, radiusDp);
        final float strokePx = Math.max(2f, dpF(ctx, FOCUS_BORDER_DP));
        return new Drawable() {
            private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path clipPath = new Path();
            private final RectF rectF = new RectF();
            private final RectF borderRect = new RectF();
            private int lastW = -1;
            private int lastH = -1;

            {
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(strokePx);
                borderPaint.setColor(COLOR_FOCUS_BORDER);
            }

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
                    float effRadius = radiusPx > 0f ? Math.min(radiusPx, Math.min(w, h) * 0.5f) : 0f;
                    clipPath.reset();
                    if (effRadius > 0f) {
                        clipPath.addRoundRect(rectF, effRadius, effRadius, Path.Direction.CW);
                    } else {
                        clipPath.addRect(rectF, Path.Direction.CW);
                    }
                    float halfStroke = strokePx * 0.5f;
                    borderRect.set(
                            rectF.left + halfStroke,
                            rectF.top + halfStroke,
                            rectF.right - halfStroke,
                            rectF.bottom - halfStroke);

                    if (isActiveAccent) {
                        basePaint.setShader(new LinearGradient(
                                0, 0, w, h,
                                isFocused ? Color.parseColor("#FF2A42") : Color.parseColor("#E50914"),
                                isFocused ? Color.parseColor("#FF1F7A") : Color.parseColor("#C90052"),
                                Shader.TileMode.CLAMP));
                    } else if (isFocused) {
                        basePaint.setShader(null);
                        basePaint.setColor(getGlassFocusedBackgroundColor());
                    } else {
                        basePaint.setShader(null);
                        basePaint.setColor(getGlassBackgroundColor());
                    }

                    if (isFocused) {
                        sheenPaint.setShader(new LinearGradient(
                                0, 0, 0, Math.max(1f, h),
                                Color.argb(44, 255, 255, 255),
                                Color.argb(8, 255, 255, 255),
                                Shader.TileMode.CLAMP));
                    } else {
                        sheenPaint.setShader(new LinearGradient(
                                0, 0, 0, Math.max(1f, h * 0.26f),
                                Color.argb(16, 255, 255, 255),
                                Color.TRANSPARENT,
                                Shader.TileMode.CLAMP));
                    }
                }

                canvas.save();
                canvas.clipPath(clipPath);
                canvas.drawRect(rectF, basePaint);
                float sheenBottom = isFocused ? rectF.bottom : (rectF.top + (h * 0.26f));
                canvas.drawRect(rectF.left, rectF.top, rectF.right, sheenBottom, sheenPaint);
                canvas.restore();

                if (isFocused) {
                    float effRadius = radiusPx > 0f ? Math.min(radiusPx, Math.min(w, h) * 0.5f) : 0f;
                    if (effRadius > 0f) {
                        float innerRadius = Math.max(0f, effRadius - strokePx * 0.5f);
                        canvas.drawRoundRect(borderRect, innerRadius, innerRadius, borderPaint);
                    } else {
                        canvas.drawRect(borderRect, borderPaint);
                    }
                }
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
        d.setCornerRadius(radiusDp > 0 ? dpF(ctx, radiusDp) : 0f);
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(2, dp(ctx, strokeDp)), strokeColor);
        return d;
    }

    /**
     * Crisp white TV focus border (~2.5dp, zero pink/red glow), compatible across API 21-34.
     */
    public static Drawable createWhiteFocusBorderDrawable(Context ctx, int radiusDp, float strokeDp) {
        float effStroke = strokeDp > 0f ? strokeDp : FOCUS_BORDER_DP;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp > 0 ? dpF(ctx, radiusDp) : 0f);
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(2, dp(ctx, effStroke)), COLOR_FOCUS_BORDER);
        return d;
    }

    /**
     * Backward-compatible focus ring helper — now renders crisp white border with zero colored glow.
     */
    public static Drawable createGlowingFocusRingDrawable(Context ctx, int radiusDp, int glowColor, float strokeDp) {
        return createWhiteFocusBorderDrawable(ctx, radiusDp, strokeDp > 0f ? strokeDp : FOCUS_BORDER_DP);
    }

    public static Drawable createPillDrawable(Context ctx, int radiusDp, int fillColor, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusDp > 0 ? dpF(ctx, radiusDp) : 0f);
        d.setColor(fillColor);
        if (strokeDp > 0f && strokeColor != Color.TRANSPARENT) {
            d.setStroke(Math.max(1, dp(ctx, strokeDp)), strokeColor);
        }
        return d;
    }

    public static Drawable createSidebarItemStateDrawable(Context ctx, int radiusDp, boolean active) {
        float effRadius = radiusDp > 0 ? dpF(ctx, radiusDp) : dpF(ctx, 10);
        Drawable normal;
        if (active) {
            GradientDrawable act = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#E50914"), Color.parseColor("#C90052")});
            act.setCornerRadius(effRadius);
            normal = act;
        } else {
            normal = createGlassRect(ctx, radiusDp > 0 ? radiusDp : 10, Color.TRANSPARENT, Color.TRANSPARENT, 0);
        }

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(effRadius);
        if (active) {
            focused.setColors(new int[]{Color.parseColor("#FF2E50"), Color.parseColor("#FF0066")});
            focused.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        } else {
            focused.setColor(Color.argb(64, 255, 255, 255));
        }
        focused.setStroke(Math.max(2, dp(ctx, FOCUS_BORDER_DP)), COLOR_FOCUS_BORDER);

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
        if (fillColor == COLOR_SURFACE_GLASS || fillColor == COLOR_SURFACE_CARD) {
            view.setBackground(createLiquidGlassDrawable(ctx, GLASS_RADIUS_DP, true, false, false));
        } else {
            view.setBackground(createGlassRect(ctx, GLASS_RADIUS_DP, fillColor, Color.TRANSPARENT, 0));
        }
        applyElevationAndOutline(view, ctx, GLASS_RADIUS_DP, Math.max(shadowDp, 20));
    }

    public static void applySubtleGlassPill(final View view, final Context ctx, final int radiusDp) {
        view.setBackground(createPillDrawable(ctx, GLASS_RADIUS_DP,
                getGlassBackgroundColor(), Color.TRANSPARENT, 0f));
        applyElevationAndOutline(view, ctx, GLASS_RADIUS_DP, 20);
    }

    public static void applyFocusableTileStyle(final View view, final Context ctx, final int radiusDp, int normalFill, int focusedFill) {
        Drawable normal = (normalFill == COLOR_SURFACE_GLASS || normalFill == COLOR_SURFACE_CARD)
                ? createLiquidGlassDrawable(ctx, GLASS_RADIUS_DP, false, false, false)
                : createGlassRect(ctx, GLASS_RADIUS_DP, normalFill, Color.TRANSPARENT, 0);
        Drawable focused = createLiquidGlassDrawable(ctx, GLASS_RADIUS_DP, false, true, false);
        Drawable pressed = createLiquidGlassDrawable(ctx, GLASS_RADIUS_DP, false, true, false);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, GLASS_RADIUS_DP, FOCUS_SCALE_DEFAULT, 16);
    }

    public static void applyFocusableGlassButton(final View view, final Context ctx, final int radiusDp, boolean isPrimaryAccent) {
        Drawable normal = createLiquidGlassDrawable(ctx, GLASS_RADIUS_DP, false, false, isPrimaryAccent);
        Drawable focused = createLiquidGlassDrawable(ctx, GLASS_RADIUS_DP, false, true, isPrimaryAccent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, GLASS_RADIUS_DP, FOCUS_SCALE_DEFAULT, 16);
    }

    public static void applyCircularIconButtonStyle(final View view, final Context ctx, final int sizeDp) {
        Drawable normal = createPillDrawable(ctx, GLASS_RADIUS_DP, getGlassBackgroundColor(), Color.TRANSPARENT, 0f);
        Drawable focused = createPillDrawable(ctx, GLASS_RADIUS_DP, getGlassFocusedBackgroundColor(), COLOR_FOCUS_BORDER, FOCUS_BORDER_DP);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, GLASS_RADIUS_DP, 1.07f, 16);
    }

    public static void applyCompactChipStyle(final View view, final Context ctx, final int radiusDp, boolean selected) {
        Drawable normal;
        if (selected) {
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#E50914"), Color.parseColor("#C90052")});
            g.setCornerRadius(dpF(ctx, GLASS_RADIUS_DP));
            normal = g;
        } else {
            normal = createPillDrawable(ctx, GLASS_RADIUS_DP, getGlassBackgroundColor(), Color.TRANSPARENT, 0f);
        }
        Drawable focused;
        if (selected) {
            GradientDrawable fg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#FF2A42"), Color.parseColor("#FF1F7A")});
            fg.setCornerRadius(dpF(ctx, GLASS_RADIUS_DP));
            fg.setStroke(Math.max(2, dp(ctx, FOCUS_BORDER_DP)), COLOR_FOCUS_BORDER);
            focused = fg;
        } else {
            focused = createPillDrawable(ctx, GLASS_RADIUS_DP, getGlassFocusedBackgroundColor(), COLOR_FOCUS_BORDER, FOCUS_BORDER_DP);
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        configureTvFocus(view, ctx, GLASS_RADIUS_DP, FOCUS_SCALE_DEFAULT, 16);
    }

    public static void applyPrimaryGradientButton(final View view, final Context ctx, final int radiusDp) {
        applyFocusableGlassButton(view, ctx, GLASS_RADIUS_DP, true);
    }

    public static void setButtonSelectedVisual(final View view, final Context ctx, final int radiusDp, boolean selected) {
        boolean wasFocused = view != null && view.hasFocus();
        applyFocusableGlassButton(view, ctx, GLASS_RADIUS_DP, selected);
        if (wasFocused && view != null) {
            view.refreshDrawableState();
            applyTvFocusEffect(view, ctx, true, FOCUS_SCALE_DEFAULT, 16);
        }
    }

    public static void makeNonFocusable(View view) {
        if (view == null) return;
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        if (view instanceof RecyclerView) {
            ((RecyclerView) view).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    public static void setFocusBlocked(ViewGroup group, boolean blocked) {
        if (group == null) return;
        int target = blocked ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        if (group.getDescendantFocusability() != target) {
            group.setDescendantFocusability(target);
        }
        if (blocked && group.hasFocus()) {
            group.clearFocus();
        }
    }

    public static boolean requestTvFocus(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        if (view.isInTouchMode()) {
            if (view.requestFocusFromTouch()) return true;
        }
        if (view.requestFocus()) return true;
        return view.requestFocusFromTouch();
    }

    public static void postTvFocus(final View view) {
        if (view == null) return;
        view.post(new Runnable() {
            @Override
            public void run() {
                if (view.getVisibility() == View.VISIBLE) {
                    requestTvFocus(view);
                }
            }
        });
    }

    public static boolean isDescendantOf(View child, View ancestor) {
        if (child == null || ancestor == null) return false;
        if (child == ancestor) return true;
        ViewParent p = child.getParent();
        while (p != null) {
            if (p == ancestor) return true;
            p = p.getParent();
        }
        return false;
    }

    public static boolean isValidInteractiveFocus(View focused, View activeContainer) {
        if (focused == null || activeContainer == null) return false;
        if (focused == activeContainer) return false;
        if (!focused.isAttachedToWindow() || !focused.isShown() || !focused.isFocusable()) return false;
        if (focused instanceof ScrollView || focused instanceof HorizontalScrollView || focused instanceof RecyclerView) {
            return false;
        }
        return isDescendantOf(focused, activeContainer);
    }

    private static void applyElevationAndOutline(final View view, final Context ctx, final int radiusDp, int shadowDp) {
        view.setElevation(dp(ctx, shadowDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setOutlineAmbientShadowColor(COLOR_GLASS_SHADOW_AMBIENT);
            view.setOutlineSpotShadowColor(COLOR_GLASS_SHADOW_SPOT);
        }
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(ctx, radiusDp));
            }
        });
        view.setClipToOutline(true);
    }

    public static void applyTvFocusEffect(
            View view, Context ctx, boolean hasFocus, float scaleOnFocus, int baseShadowDp) {
        applyFocusVisualEffects(view, ctx, hasFocus, scaleOnFocus, baseShadowDp, COLOR_FOCUS_BORDER);
    }

    public static void applyFocusVisualEffects(
            View view, Context ctx, boolean hasFocus, float scaleOnFocus, int baseShadowDp, int glowColor) {
        if (view == null || ctx == null) return;
        float targetScale = hasFocus ? scaleOnFocus : 1.0f;
        float targetZ = hasFocus ? dpF(ctx, 10) : 0f;
        view.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetZ)
                .setDuration(130)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        view.setElevation(hasFocus ? dp(ctx, baseShadowDp + 8) : dp(ctx, baseShadowDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (hasFocus) {
                view.setOutlineSpotShadowColor(COLOR_FOCUS_SHADOW_SPOT);
                view.setOutlineAmbientShadowColor(COLOR_FOCUS_SHADOW_AMBIENT);
            } else {
                view.setOutlineSpotShadowColor(COLOR_GLASS_SHADOW_SPOT);
                view.setOutlineAmbientShadowColor(COLOR_GLASS_SHADOW_AMBIENT);
            }
        }
    }

    public static void applyTvFocusZoomGlow(
            final View view, final Context ctx, final float scaleOnFocus, final int baseShadowDp, final int glowColor) {
        if (view == null || ctx == null) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        view.setElevation(dp(ctx, baseShadowDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setOutlineSpotShadowColor(COLOR_GLASS_SHADOW_SPOT);
            view.setOutlineAmbientShadowColor(COLOR_GLASS_SHADOW_AMBIENT);
        }
        final float effScale = scaleOnFocus <= 1.0f ? FOCUS_SCALE_DEFAULT : Math.min(scaleOnFocus, 1.07f);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyFocusVisualEffects(v, ctx, hasFocus, effScale, baseShadowDp, COLOR_FOCUS_BORDER);
            }
        });
    }

    private static void configureTvFocus(final View view, final Context ctx, final int radiusDp, final float scaleOnFocus, final int baseShadowDp) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        applyElevationAndOutline(view, ctx, radiusDp, baseShadowDp);
        final float effScale = scaleOnFocus <= 1.0f ? FOCUS_SCALE_DEFAULT : Math.min(scaleOnFocus, 1.07f);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyFocusVisualEffects(v, ctx, hasFocus, effScale, baseShadowDp, COLOR_FOCUS_BORDER);
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
        safeLoadImageWithFallback(target, url, "", placeholderRes, cornerRadiusDp);
    }

    public static void safeLoadImageWithFallback(
            ImageView target, String primaryUrl, String fallbackUrl, int placeholderRes, int cornerRadiusDp) {
        if (target == null) return;
        Context ctx = target.getContext();
        if (!isContextValidForGlide(ctx)) return;
        String p = primaryUrl != null ? primaryUrl.trim() : "";
        String fb = fallbackUrl != null ? fallbackUrl.trim() : "";
        if (p.isEmpty()) {
            p = fb;
            fb = "";
        }
        if (p.isEmpty()) {
            Glide.with(ctx).clear(target);
            if (placeholderRes != 0) target.setImageResource(placeholderRes);
            return;
        }
        RequestOptions opts = new RequestOptions();
        if (cornerRadiusDp > 0) {
            opts = opts.transform(new CenterCrop(), new RoundedCorners(dp(ctx, cornerRadiusDp)));
        } else {
            opts = opts.centerCrop();
        }
        opts = opts.placeholder(new ColorDrawable(Color.parseColor("#141824")));
        if (placeholderRes != 0) {
            opts = opts.error(placeholderRes);
        }
        if (!fb.isEmpty() && !fb.equals(p)) {
            Glide.with(ctx)
                    .load(p)
                    .apply(opts)
                    .error(Glide.with(ctx).load(fb).apply(opts))
                    .into(target);
        } else {
            Glide.with(ctx)
                    .load(p)
                    .apply(opts)
                    .into(target);
        }
    }

    public static void safeLoadBackdrop(ImageView target, String url) {
        if (target == null) return;
        Context ctx = target.getContext();
        if (!isContextValidForGlide(ctx)) return;
        if (url == null || url.trim().isEmpty()) {
            target.setImageDrawable(new ColorDrawable(Color.parseColor("#090B12")));
            return;
        }
        Glide.with(ctx)
                .load(url.trim())
                .apply(new RequestOptions()
                        .centerCrop()
                        .placeholder(new ColorDrawable(Color.parseColor("#090B12")))
                        .error(R.drawable.default_poster))
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
        applyGlassCardStyle(toastPill, ctx, GLASS_RADIUS_DP, COLOR_SURFACE_GLASS, 20);

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
