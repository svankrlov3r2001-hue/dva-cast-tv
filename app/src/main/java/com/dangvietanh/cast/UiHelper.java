package com.dangvietanh.cast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiManager;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class UiHelper {

    public static final int COLOR_BG_BASE = Color.parseColor("#07090F");
    public static final int COLOR_SURFACE_GLASS = Color.argb(64, 0, 0, 0);        // rgba(0, 0, 0, 0.25)
    public static final int COLOR_SURFACE_CARD = Color.argb(140, 10, 12, 18);     // readable glass backing
    public static final int COLOR_BORDER_GLASS = Color.argb(46, 255, 255, 255);   // rgba(255, 255, 255, 0.18)
    public static final int COLOR_ACCENT_RED = Color.parseColor("#E50914");
    public static final int COLOR_ACCENT_PINK = Color.parseColor("#D50055");
    public static final int COLOR_TEXT_PRIMARY = Color.parseColor("#F5F6FA");
    public static final int COLOR_TEXT_MUTED = Color.parseColor("#B0B8C8");
    public static final int COLOR_GREEN = Color.parseColor("#30D158");

    private UiHelper() {
    }

    public static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    /**
     * Exact match for CSS:
     * radial-gradient(circle at 20% 20%, rgba(229, 9, 20, 0.16), transparent 46%),
     * radial-gradient(circle at 80% 80%, rgba(213, 0, 85, 0.12), transparent 46%),
     * #07090f;
     */
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
                            Color.argb(41, 229, 9, 20), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    g2Paint.setShader(new RadialGradient(
                            w * 0.80f, h * 0.80f, radius,
                            Color.argb(31, 213, 0, 85), Color.TRANSPARENT, Shader.TileMode.CLAMP));
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

    public static GradientDrawable createGlassRect(Context ctx, int radiusDp, int fillColor, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(ctx, radiusDp));
        d.setColor(fillColor);
        d.setStroke(dp(ctx, strokeDp), strokeColor);
        return d;
    }

    public static void applyGlassCardStyle(final View view, final Context ctx, final int radiusDp, int fillColor, int shadowDp) {
        view.setBackground(createGlassRect(ctx, radiusDp, fillColor, COLOR_BORDER_GLASS, 1));
        view.setElevation(dp(ctx, shadowDp));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(ctx, radiusDp));
            }
        });
        view.setClipToOutline(true);
    }

    public static void applyFocusableTileStyle(final View view, final Context ctx, final int radiusDp, int normalFill, int focusedFill) {
        GradientDrawable normal = createGlassRect(ctx, radiusDp, normalFill, COLOR_BORDER_GLASS, 1);
        GradientDrawable focused = createGlassRect(ctx, radiusDp, focusedFill, Color.WHITE, 2);
        GradientDrawable pressed = createGlassRect(ctx, radiusDp, focusedFill, Color.WHITE, 2);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        view.setBackground(states);
        view.setFocusable(true);
        view.setClickable(true);
        view.setElevation(dp(ctx, 8));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(ctx, radiusDp));
            }
        });

        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float targetScale = hasFocus ? 1.035f : 1.0f;
                v.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(150)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        });
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
     * Smooth animated iOS/Apple-style rotating arc spinner.
     */
    public static class SpinnerView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private float angle = 0f;
        private boolean spinning = true;

        public SpinnerView(Context context) {
            super(context);
            float stroke = dp(context, 3.2f);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(stroke);
            trackPaint.setColor(Color.argb(38, 255, 255, 255));

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
            float pad = trackPaint.getStrokeWidth();
            arcRect.set(pad, pad, w - pad, h - pad);
            arcPaint.setShader(new SweepGradient(
                    w / 2f, h / 2f,
                    new int[]{Color.TRANSPARENT, COLOR_ACCENT_PINK, COLOR_ACCENT_RED, Color.WHITE},
                    new float[]{0f, 0.35f, 0.75f, 1f}));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawOval(arcRect, trackPaint);
            canvas.save();
            canvas.rotate(angle, getWidth() / 2f, getHeight() / 2f);
            canvas.drawArc(arcRect, 0, 290, false, arcPaint);
            canvas.restore();
            if (spinning && getVisibility() == View.VISIBLE) {
                angle = (angle + 7.5f) % 360f;
                postInvalidateOnAnimation();
            }
        }
    }
}
