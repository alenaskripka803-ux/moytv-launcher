package ru.moytv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A glass tile with an app icon (or a line icon) and a caption. */
class TileView extends GlassFrame {
    static final float ROOM = 24;
    final String key;
    final IconBox icon;
    final TextView name;

    TileView(Context c, String key, String label, Drawable appIcon, int lineIcon, float wDesign, float hDesign, float iconDesign) {
        super(c, 34);
        this.key = key;
        setFocusable(true);
        setClickable(true);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        icon = new IconBox(c, appIcon, lineIcon);
        col.addView(icon, new LinearLayout.LayoutParams(Ui.px(iconDesign), Ui.px(iconDesign)));

        name = Ui.text(c, label, 21, Ui.semibold, 0xFFFFFFFF);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setShadowLayer(8 * Ui.s, 0, 2 * Ui.s, 0x33000020);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(wDesign - 24), Ui.WRAP);
        lp.topMargin = Ui.px(18);
        col.addView(name, lp);

        setRoom(ROOM, 14);
        setLayoutParams(new LinearLayout.LayoutParams(Ui.px(wDesign + 2 * ROOM), Ui.px(hDesign + 2 * ROOM)));
    }

    /** Rounded app icon, or a white line icon on a soft glass square. */
    static class IconBox extends View {
        private final Drawable drawable;
        private final int lineIcon;
        private Paint paint;
        private final RectF r = new RectF();
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconBox(Context c, Drawable d, int lineIcon) {
            super(c);
            this.drawable = d;
            this.lineIcon = lineIcon;
            bg.setColor(0x33FFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            r.set(0, 0, w, h);
            paint = null;
            if (drawable != null && w > 0 && h > 0) {
                Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b);
                drawable.setBounds(0, 0, w, h);
                drawable.draw(cv);
                paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                paint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float rad = getWidth() * 0.25f;
            if (paint != null) {
                c.drawRoundRect(r, rad, rad, paint);
            } else {
                c.drawRoundRect(r, rad, rad, bg);
                Icons.draw(c, lineIcon, getWidth() / 2f, getHeight() / 2f, getWidth() * 0.5f, 0xFFFFFFFF);
            }
        }
    }
}
