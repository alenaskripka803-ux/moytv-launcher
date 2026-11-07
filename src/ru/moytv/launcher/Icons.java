package ru.moytv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn on a 24x24 grid, in the spirit of SF Symbols. */
final class Icons {
    static final int TV = 0, WIFI = 1, GRID = 2, SEARCH = 3, PENCIL = 4, GEAR = 5, PLUS = 6, INPUT = 7, NO_WIFI = 8;

    private static final float[] GEAR_PTS = {
            10.3f, 2.5f, 13.7f, 2.5f, 14.2f, 5.1f, 16.0f, 5.9f, 18.2f, 4.4f, 20.6f, 6.8f, 19.1f, 9.0f,
            19.9f, 10.8f, 22.5f, 11.3f, 22.5f, 14.7f, 19.9f, 15.2f, 19.1f, 17.0f, 20.6f, 19.2f, 18.2f, 21.6f,
            16.0f, 20.1f, 14.2f, 20.9f, 13.7f, 23.5f, 10.3f, 23.5f, 9.8f, 20.9f, 8.0f, 20.1f, 5.8f, 21.6f,
            3.4f, 19.2f, 4.9f, 17.0f, 4.1f, 15.2f, 1.5f, 14.7f, 1.5f, 11.3f, 4.1f, 10.8f, 4.9f, 9.0f,
            3.4f, 6.8f, 5.8f, 4.4f, 8.0f, 5.9f, 9.8f, 5.1f};

    static void draw(Canvas c, int type, float cx, float cy, float size, int color) {
        float u = size / 24f;
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(u, u);
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(2f);
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setStrokeJoin(Paint.Join.ROUND);
        st.setColor(color);
        Paint fl = new Paint(Paint.ANTI_ALIAS_FLAG);
        fl.setColor(color);
        switch (type) {
            case TV:
                c.drawRoundRect(3, 5, 21, 17, 2.5f, 2.5f, st);
                c.drawLine(8, 20.5f, 16, 20.5f, st);
                break;
            case INPUT:
                c.drawRoundRect(3, 5, 21, 17, 2.5f, 2.5f, st);
                c.drawLine(7.5f, 11, 14, 11, st);
                c.drawLine(11.5f, 8.5f, 14, 11, st);
                c.drawLine(11.5f, 13.5f, 14, 11, st);
                c.drawLine(8, 20.5f, 16, 20.5f, st);
                break;
            case WIFI:
            case NO_WIFI:
                for (float rr : new float[]{11f, 7.5f, 4f}) {
                    c.drawArc(new RectF(12 - rr, 20 - rr, 12 + rr, 20 + rr), 222, 96, false, st);
                }
                c.drawCircle(12, 19.6f, 1.2f, fl);
                if (type == NO_WIFI) c.drawLine(4, 4, 20, 20, st);
                break;
            case GRID:
                c.drawRoundRect(3, 3, 10.5f, 10.5f, 2, 2, fl);
                c.drawRoundRect(13.5f, 3, 21, 10.5f, 2, 2, fl);
                c.drawRoundRect(3, 13.5f, 10.5f, 21, 2, 2, fl);
                c.drawRoundRect(13.5f, 13.5f, 21, 21, 2, 2, fl);
                break;
            case SEARCH:
                c.drawCircle(11, 11, 6.5f, st);
                c.drawLine(16, 16, 20.5f, 20.5f, st);
                break;
            case PENCIL: {
                st.setStrokeWidth(1.9f);
                Path p = new Path();
                p.moveTo(4, 20);
                p.lineTo(5, 15.5f);
                p.lineTo(15.5f, 5);
                p.lineTo(18.6f, 8.1f);
                p.lineTo(8.1f, 18.6f);
                p.close();
                c.drawPath(p, st);
                c.drawLine(13.4f, 7.1f, 16.5f, 10.2f, st);
                break;
            }
            case GEAR: {
                st.setStrokeWidth(1.8f);
                Path p = new Path();
                p.moveTo(GEAR_PTS[0], GEAR_PTS[1] - 1);
                for (int i = 2; i < GEAR_PTS.length; i += 2) p.lineTo(GEAR_PTS[i], GEAR_PTS[i + 1] - 1);
                p.close();
                c.drawPath(p, st);
                c.drawCircle(12, 12, 3.2f, st);
                break;
            }
            case PLUS:
                c.drawLine(12, 5, 12, 19, st);
                c.drawLine(5, 12, 19, 12, st);
                break;
        }
        c.restore();
    }

    /** A view that just draws one icon centred. */
    static class IconView extends View {
        int type;
        float size;
        int color = 0xFFFFFFFF;

        IconView(Context c, int type, float sizeDesign) {
            super(c);
            this.type = type;
            this.size = sizeDesign * Ui.s;
        }

        void setType(int t) {
            type = t;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            Icons.draw(c, type, getWidth() / 2f, getHeight() / 2f, size, color);
        }
    }

    private Icons() {}
}
