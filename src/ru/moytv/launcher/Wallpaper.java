package ru.moytv.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Apple-style wallpaper drawn in code: soft blue, violet and coral light blobs on deep navy.
 * {@link #base} is the wallpaper itself, {@link #blur} a tiny copy that frosted-glass panels sample from.
 */
final class Wallpaper {
    static Bitmap base, blur;

    static void init(int screenW, int screenH) {
        if (base != null) return;
        int bw = 640;
        int bh = Math.max(1, Math.round(bw * (float) screenH / screenW));
        base = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(base);
        float k = bw / 1920f;
        float ky = bh / 1080f;
        c.drawColor(0xFF0B1630);

        blob(c, 420 * k, 190 * ky, 700 * k, new int[]{0xFF3B6CF6, 0xCC2343B8, 0x002343B8});
        blob(c, 1640 * k, 290 * ky, 640 * k, new int[]{0xFFC04FD8, 0xCC7A2BB8, 0x007A2BB8});
        blob(c, 1000 * k, 1180 * ky, 760 * k, new int[]{0xFFFF8A5C, 0xCCE2436F, 0x00E2436F});
        blob(c, 1170 * k, 500 * ky, 360 * k, new int[]{0x8C62D3FF, 0x3062D3FF, 0x0062D3FF});

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(0.30f * bw, 1.10f * bh, 0.70f * bw,
                new int[]{0x1AFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, bh, p);
        p.setShader(new LinearGradient(0, 0, 0, bh, 0x0D050A1E, 0x59050A1E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bw, bh, p);

        // Soften like CSS blur(90px): shrink and grow back.
        Bitmap small = Bitmap.createScaledBitmap(base, bw / 8, Math.max(1, bh / 8), true);
        Bitmap soft = Bitmap.createScaledBitmap(small, bw, bh, true);
        base = soft;
        blur = Bitmap.createScaledBitmap(small, Math.max(1, bw / 16), Math.max(1, bh / 16), true);
    }

    private static void blob(Canvas c, float cx, float cy, float r, int[] colors) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(cx, cy, r, colors, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
    }

    private Wallpaper() {}
}
