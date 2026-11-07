package ru.moytv.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Frosted glass panel: samples the blurred wallpaper behind itself, adds a white sheen and a light rim.
 *
 * Focusable panels reserve empty "room" around the glass inside their own bounds. When focused the glass
 * grows into that room and the halo / dashed outline are drawn there too, so nothing is ever drawn
 * outside the view and nothing can be clipped or covered by neighbours.
 */
class GlassFrame extends FrameLayout {
    float radius;
    boolean solidWhite;
    boolean dashed;

    private float room;   // px reserved on each side
    private float grow;   // px the glass expands by when focused
    private float expand; // 0..1 animated
    private ValueAnimator anim;

    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BitmapShader shader;
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final RectF tmp = new RectF();
    private final int[] loc = new int[2];
    private LinearGradient sheenNormal, sheenFocus, rimNormal;

    GlassFrame(Context c, float radiusDesign) {
        super(c);
        radius = Ui.s * radiusDesign;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        shader = new BitmapShader(Wallpaper.blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        glass.setShader(shader);
        rim.setStyle(Paint.Style.STROKE);
        extra.setStyle(Paint.Style.STROKE);
    }

    /** Reserve room (design px) on each side; the glass grows by growDesign when focused. Call after setPadding. */
    void setRoom(float roomDesign, float growDesign) {
        room = Ui.px(roomDesign);
        grow = Ui.px(growDesign);
        int ri = (int) room;
        setPadding(getPaddingLeft() + ri, getPaddingTop() + ri, getPaddingRight() + ri, getPaddingBottom() + ri);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sheenNormal = new LinearGradient(0, 0, w, h, 0x38FFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP);
        sheenFocus = new LinearGradient(0, 0, w, h, 0x61FFFFFF, 0x24FFFFFF, Shader.TileMode.CLAMP);
        rimNormal = new LinearGradient(0, 0, 0, h, 0x80FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP);
    }

    private boolean active() {
        return isFocused() || isPressed() || isSelected() || isHovered();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        boolean f = active();
        float inset = room - grow * expand;
        r.set(inset, inset, w - inset, h - inset);
        float rad = Math.min(radius + grow * expand * 0.5f, Math.min(r.width(), r.height()) / 2f);

        if (solidWhite) {
            sheen.setShader(null);
            sheen.setColor(f ? 0xFFFFFFFF : 0xD9FFFFFF);
            c.drawRoundRect(r, rad, rad, sheen);
        } else {
            getLocationOnScreen(loc);
            float kx = (float) Ui.W / Wallpaper.blur.getWidth();
            float ky = (float) Ui.H / Wallpaper.blur.getHeight();
            m.setScale(kx, ky);
            m.postTranslate(-loc[0], -loc[1]);
            shader.setLocalMatrix(m);
            c.drawRoundRect(r, rad, rad, glass);
            sheen.setShader(f ? sheenFocus : sheenNormal);
            c.drawRoundRect(r, rad, rad, sheen);
        }

        float sw = f ? Math.max(2f, 2.4f * Ui.s) : Math.max(1f, 1.3f * Ui.s);
        rim.setStrokeWidth(sw);
        if (f) {
            rim.setShader(null);
            rim.setColor(solidWhite ? 0xFFFFFFFF : 0xDDFFFFFF);
        } else {
            rim.setShader(rimNormal);
        }
        float in = sw / 2f;
        tmp.set(r.left + in, r.top + in, r.right - in, r.bottom - in);
        c.drawRoundRect(tmp, rad - in, rad - in, rim);

        // Soft halo, only inside the reserved room.
        float space = inset;
        if (f && space >= 3) {
            float hw = Math.min(6f * Ui.s, space * 2f);
            extra.setPathEffect(null);
            extra.setStrokeWidth(hw);
            extra.setColor(0x26FFFFFF);
            tmp.set(r.left - hw / 2, r.top - hw / 2, r.right + hw / 2, r.bottom + hw / 2);
            c.drawRoundRect(tmp, rad + hw / 2, rad + hw / 2, extra);
        }
        if (dashed) {
            float sw2 = Math.max(2f, 3f * Ui.s);
            float edge = sw2 / 2f + 1;
            extra.setStrokeWidth(sw2);
            extra.setColor(0xF2FFFFFF);
            extra.setPathEffect(new DashPathEffect(new float[]{9f * Ui.s, 6f * Ui.s}, 0));
            tmp.set(edge, edge, w - edge, h - edge);
            float dr = rad + (r.left - edge);
            c.drawRoundRect(tmp, dr, dr, extra);
        }
    }

    private void animateTo(float target) {
        if (grow <= 0) {
            invalidate();
            return;
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(expand, target);
        anim.setDuration(160);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                expand = (Float) a.getAnimatedValue();
                float base = Math.max(1f, getWidth() - 2 * room);
                float k = 1f + (2 * grow * expand) / base;
                for (int i = 0; i < getChildCount(); i++) {
                    View ch = getChildAt(i);
                    ch.setScaleX(k);
                    ch.setScaleY(k);
                }
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        animateTo(active() ? 1f : 0f);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        animateTo(active() ? 1f : 0f);
    }

    void setDashed(boolean d) {
        dashed = d;
        invalidate();
    }
}
