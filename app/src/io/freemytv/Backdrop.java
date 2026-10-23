package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * The home screen background, built once in the background from the bundled wallpaper (app/assets/wallpapers):
 *  - the frosted version (192×108, heavily blurred): the background of the frosted glass and of the bottom drawer;
 *  - the Liquid Glass version (480×270, lightly blurred): the photo shows through the shelf and the top buttons;
 *  - both blended with white (by day) and with near-black (at night), plus the full-screen background (640×360,
 *    softly blurred) in both.
 * Sizes follow the TV's UI size (1920×1080; 1280×720 on TVs that draw their UI at 720p, where W×H is 2/3 as wide).
 */
final class Backdrop {
    /** The UI's size against 1920×1080 (at most 1): nothing is built bigger than the screen can show. */
    private static final float UI_SCALE = uiScale();
    static final int W = ui(1920), H = ui(1080), FW = 192, FH = 108, GW = 480, GH = 270;
    /**
     * The full-screen background, separate from the heavier FW×FH/GW×GH blurs (those stay for the frosted glass and
     * the liquid-glass shelf/buttons): bigger and only lightly blurred, so the photo's shapes stay recognisable
     * through it ("soft focus", not a flat gradient).
     */
    static final int BGW = 640, BGH = 360;
    private static float uiScale() {
        android.util.DisplayMetrics m = android.content.res.Resources.getSystem().getDisplayMetrics();
        int longest = Math.max(m.widthPixels, m.heightPixels);
        return longest > 0 ? Math.min(1f, longest / 1920f) : 1f;
    }

    private static int ui(int px) {
        return Math.round(px * UI_SCALE);
    }
    final int[] frost;      // heavily blurred (frosted glass, bottom drawer)
    final int[] lightGlass;             // by day: the glass version, lightened
    final Bitmap lightImage;            // by day: the full-screen background (BGW × BGH, softly blurred)
    final int[] nightFrost, nightGlass; // at night: frosted (the bottom drawer's frosted glass) and glass, darkened
    final Bitmap nightImage;            // at night: the full-screen background (BGW × BGH, softly blurred)

    private Backdrop(int[] frost, int[] lightGlass, int[] nightFrost, int[] nightGlass,
                     Bitmap lightImage, Bitmap nightImage) {
        this.frost = frost;
        this.lightGlass = lightGlass;
        this.nightFrost = nightFrost;
        this.nightGlass = nightGlass;
        this.lightImage = lightImage;
        this.nightImage = nightImage;
    }

    static Backdrop make(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint photo = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
        float sw = src.getWidth() * scale, sh = src.getHeight() * scale;
        c.drawBitmap(src, null, new RectF((W - sw) / 2, (H - sh) / 2, (W + sw) / 2, (H + sh) / 2), photo);
        src.recycle();
        // Light design: everything comes from the photo without scrims. (The focused-card photos, "wide"/"tall",
        // were built here for the hero cards; dropped with them — nothing read those fields any more.)
        // Heavy blur, kept only for the frosted glass (drawer, toasts) and the liquid-glass shelf/buttons: those
        // still need to read as frosted, not as a sharp photo.
        int[] frost = reduce(out, FW, FH, 3, 3);
        // Dock/shelf and top-button glass: radius 2 -> 3 (passes unchanged) for a moderately blurrier Liquid
        // Glass, per feedback that the dock read too sharp.
        int[] glass = reduce(out, GW, GH, 3, 2);
        int[] lightGlass = whiten(glass.clone(), 0.18f);
        int[] nightFrost = darken(frost.clone(), 0.76f);
        int[] nightGlass = darken(glass, 0.56f);
        // The full-screen background: bigger and only softly blurred (soft-focus, not a flat gradient), and only
        // lightly washed, so the photo's own colour and shapes come through behind the glass.
        int[] bg = reduce(out, BGW, BGH, 1, 2);
        int[] lightBg = whiten(bg.clone(), 0.12f);
        int[] nightBg = darken(bg, 0.4f);
        // RGB_565 (no alpha needed; the cross-fade and the drawer's veil are paint-level, not per-pixel): two of
        // these at 640×360 are ~900 KB total, well under the ~1.5 MB budget.
        Bitmap lightImage = Bitmap.createBitmap(lightBg, BGW, BGH, Bitmap.Config.RGB_565);
        Bitmap nightImage = Bitmap.createBitmap(nightBg, BGW, BGH, Bitmap.Config.RGB_565);
        out.recycle();
        return new Backdrop(frost, lightGlass, nightFrost, nightGlass, lightImage, nightImage);
    }

    /** How many times smaller the screensaver photo's frosted version is (the glass math uses the same number). */
    static final int PHOTO_FROST = 6;

    /** app/assets/wallpapers/wall1.jpg … wall5.jpg: the offline backdrop, rotated daily. */
    static final int WALLPAPER_COUNT = 5;

    /**
     * Reads app/assets/wallpapers/wall{index+1}.jpg (960×540), downsampled no further than this screen needs.
     * RGB_565: the photo is always blurred before it's shown, so the extra bit depth of ARGB buys nothing.
     * Called off the UI thread (asset decoding is disk I/O plus CPU work).
     */
    static Bitmap wallpaper(android.content.Context c, int index) throws java.io.IOException {
        String path = "wallpapers/wall" + (index + 1) + ".jpg";
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = c.getAssets().open(path)) {
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new java.io.IOException("unreadable wallpaper");
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= W) opts.inSampleSize *= 2;
        Bitmap b;
        try (java.io.InputStream in = c.getAssets().open(path)) {
            b = android.graphics.BitmapFactory.decodeStream(in, null, opts);
        }
        if (b == null) throw new java.io.IOException("unreadable wallpaper");
        return b;
    }

    /** Copies it to GPU memory (cheap to draw; the process doesn't keep the pixels). */
    static Bitmap gpu(Bitmap b) {
        Bitmap g = b.copy(Bitmap.Config.HARDWARE, false);
        if (g == null) return b;
        b.recycle();
        return g;
    }

    /** Blends with a near-black (the darkest gray of Apple's dark-mode backgrounds), keeping the photo's tint. */
    private static int[] darken(int[] px, float amount) {
        float keep = 1 - amount, base = 11 * amount;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Math.round(((p >> 16) & 255) * keep + base);
            int g = Math.round(((p >> 8) & 255) * keep + base);
            int b = Math.round((p & 255) * keep + base + 2 * amount);
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return px;
    }

    private static int[] whiten(int[] px, float amount) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Math.round(((p >> 16) & 255) * (1 - amount) + 255 * amount);
            int g = Math.round(((p >> 8) & 255) * (1 - amount) + 255 * amount);
            int b = Math.round((p & 255) * (1 - amount) + 255 * amount);
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return px;
    }

    /** Halved at each step (a true average, no aliasing), then blurred (box passes of radius r). */
    private static int[] reduce(Bitmap full, int w, int h, int radius, int passes) {
        Bitmap b = full;
        while (b.getWidth() / 2 >= w * 1.5f) {
            Bitmap half = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            if (b != full) b.recycle();
            b = half;
        }
        Bitmap small = Bitmap.createScaledBitmap(b, w, h, true);
        if (b != full) b.recycle();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        small.recycle();
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < passes; pass++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
        return px;
    }

    /** Moving average of radius r in one direction, repeating the edge pixels. */
    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w, step = horizontal ? 1 : w, lineStep = horizontal ? w : 1;
        float k = 1f / (2 * r + 1);
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep;
            float sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[base + Math.max(0, Math.min(n - 1, i)) * step];
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int i = 0; i < n; i++) {
                out[base + i * step] = 0xFF000000 | (Math.round(sr * k) << 16) | (Math.round(sg * k) << 8) | Math.round(sb * k);
                int add = in[base + Math.min(n - 1, i + r + 1) * step], sub = in[base + Math.max(0, i - r) * step];
                sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
                sb += (add & 255) - (sub & 255);
            }
        }
    }
}
