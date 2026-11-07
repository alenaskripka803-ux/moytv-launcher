package ru.moytv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Big lock-screen style clock: heavy rounded digits with a soft white-to-silver gradient. */
class ClockView extends View {
    private String text = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private LinearGradient grad;

    ClockView(Context c) {
        super(c);
        p.setTypeface(Ui.display);
        p.setTextSize(210f * Ui.s);
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(-0.03f);
        p.setFontFeatureSettings("tnum");
        p.setShadowLayer(30f * Ui.s, 0, 6f * Ui.s, 0x40000028);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    void setTime(String t) {
        if (!t.equals(text)) {
            text = t;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        if (grad == null) {
            float top = base - p.getTextSize() * 0.73f;
            grad = new LinearGradient(0, top, 0, base, 0xFAFFFFFF, 0xB8FFFFFF, Shader.TileMode.CLAMP);
            p.setShader(grad);
        }
        c.drawText(text, getWidth() / 2f, base, p);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        grad = null;
    }
}
