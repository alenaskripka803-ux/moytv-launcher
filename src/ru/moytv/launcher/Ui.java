package ru.moytv.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Layout helpers. Everything is designed on a 1920x1080 canvas and scaled to the real screen. */
final class Ui {
    static int W = 1920, H = 1080;
    static float s = 1f;
    static Typeface medium, semibold, display;

    static void init(Activity a) {
        DisplayMetrics dm = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        W = Math.max(dm.widthPixels, dm.heightPixels);
        H = Math.min(dm.widthPixels, dm.heightPixels);
        s = W / 1920f;
        if (medium == null) {
            AssetManager am = a.getAssets();
            medium = load(am, "fonts/Inter-Medium.otf", Typeface.DEFAULT);
            semibold = load(am, "fonts/Inter-SemiBold.otf", Typeface.DEFAULT_BOLD);
            display = load(am, "fonts/InterDisplay-SemiBold.otf", semibold);
        }
    }

    private static Typeface load(AssetManager am, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(am, path);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static int px(float v) {
        return Math.round(v * s);
    }

    static TextView text(Context c, String t, float size, Typeface tf, int color) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s);
        v.setTypeface(tf);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        return v;
    }

    /** Absolute placement inside a FrameLayout, margins in design units. */
    static FrameLayout.LayoutParams at(int w, int h, int gravity, float left, float top, float right, float bottom) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, gravity);
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        lp.rightMargin = px(right);
        lp.bottomMargin = px(bottom);
        return lp;
    }

    static final int WRAP = FrameLayout.LayoutParams.WRAP_CONTENT;
    static final int MATCH = FrameLayout.LayoutParams.MATCH_PARENT;
    static final int TOP_CENTER = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    static final int BOTTOM_CENTER = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

    private Ui() {}
}
