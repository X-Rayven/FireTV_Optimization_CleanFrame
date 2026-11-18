package com.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

/**
 * After a Home press Android holds back activity starts from ordinary apps for five seconds (APP_SWITCH_DELAY), so
 * HomeActivity only came up ~5 s after Amazon's home. Overlay windows are not held back: this puts a picture of the
 * home screen over Amazon's at once, with a live clock on top, and takes it away when HomeActivity has drawn.
 * It is focusable, so key presses in those seconds go nowhere instead of into Amazon's home.
 */
final class Cover {
    private static final long MAX_SHOWN_MS = 8000L;   // if HomeActivity never comes, don't leave a dead screen
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Bitmap snapshot;
    private static ClockStyle time;
    private static ClockStyle date;
    private static View shown;
    private static final Runnable EXPIRE = Cover::hide;

    private Cover() {
    }

    /** Called by HomeActivity once it is on screen: the picture, minus the clock, which the cover draws live. */
    static void capture(View root, TextClock timeView, TextClock dateView) {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        Bitmap bmp = snapshot != null && snapshot.getWidth() == w && snapshot.getHeight() == h
                ? snapshot : Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        // drawn in one go on the UI thread, so hiding the clocks never reaches the screen
        timeView.setVisibility(View.INVISIBLE);
        dateView.setVisibility(View.INVISIBLE);
        root.draw(new Canvas(bmp));
        timeView.setVisibility(View.VISIBLE);
        dateView.setVisibility(View.VISIBLE);
        snapshot = bmp;
        time = new ClockStyle(timeView);
        date = new ClockStyle(dateView);
    }

    /** From any thread. Does nothing before HomeActivity has been seen once (e.g. right after boot). */
    static void show(Context context) {
        MAIN.post(() -> {
            if (snapshot == null || shown != null) {
                return;
            }
            Context app = context.getApplicationContext();
            FrameLayout layer = new FrameLayout(app);
            ImageView picture = new ImageView(app);
            picture.setImageBitmap(snapshot);
            layer.addView(picture, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            time.addTo(layer, app);
            date.addTo(layer, app);
            layer.setFocusable(true);
            layer.setFocusableInTouchMode(true);
            layer.setOnKeyListener((v, keyCode, event) -> true);   // swallow keys until the real screen is up

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.OPAQUE);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.windowAnimations = 0;
            try {
                ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(layer, lp);
                layer.requestFocus();
                shown = layer;
                MAIN.postDelayed(EXPIRE, MAX_SHOWN_MS);
            } catch (RuntimeException e) {
                // no overlay permission on this firmware: fall back to the plain start
            }
        });
    }

    /** From the UI thread, once HomeActivity has drawn underneath. */
    static void hide() {
        MAIN.removeCallbacks(EXPIRE);
        if (shown == null) {
            return;
        }
        try {
            ((WindowManager) shown.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(shown);
        } catch (RuntimeException e) {
            // already gone
        }
        shown = null;
    }

    /** Where and how HomeActivity drew a clock, so the cover can draw a live one in the same place. */
    private static final class ClockStyle {
        final int x;
        final int y;
        final float sizePx;
        final int color;
        final Typeface typeface;
        final CharSequence format;
        final float shadowRadius;
        final float shadowDx;
        final float shadowDy;
        final int shadowColor;

        ClockStyle(TextClock v) {
            int[] at = new int[2];
            v.getLocationInWindow(at);
            x = at[0];
            y = at[1];
            sizePx = v.getTextSize();
            color = v.getCurrentTextColor();
            typeface = v.getTypeface();
            format = v.getFormat24Hour();
            shadowRadius = v.getShadowRadius();
            shadowDx = v.getShadowDx();
            shadowDy = v.getShadowDy();
            shadowColor = v.getShadowColor();
        }

        void addTo(FrameLayout layer, Context context) {
            TextClock clock = new TextClock(context);
            clock.setFormat24Hour(format);
            clock.setFormat12Hour(format);
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
            clock.setTextColor(color);
            clock.setTypeface(typeface);
            clock.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor);
            clock.setSingleLine(true);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            lp.leftMargin = x;
            lp.topMargin = y;
            layer.addView(clock, lp);
        }
    }
}
