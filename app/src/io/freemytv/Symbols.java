package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Material Symbols Rounded icons (Apache 2.0), rendered by tools/make_symbols.py as white PNGs in assets/symbols. Drawn
 * like text: `pointSize` is the size of the font next to the icon, and the icon is centered on that font's cap height.
 *
 * A few connectivity icons ("wifi", "wifi_off", "ethernet": the Wi-Fi top button's states) are drawn as vector shapes
 * instead, see drawVector(): make_symbols.py needs the Material Symbols font and a codepoints file, neither bundled in
 * this repo, so adding them as PNGs isn't possible offline. The vector versions are small (no bitmap memory at all)
 * and are drawn the same way (centered at cx, cy, sized to pointSize) so every caller stays the same.
 */
final class Symbols {
    private Symbols() {}

    /**
     * PNG pixels per em of `pointSize` (a 96 pt × 1.5 px/pt reference scale). tools/make_symbols.py renders a 172 px
     * em, so an icon's em is drawn at ~1.2 × `pointSize`.
     */
    private static final float PX_PER_POINT = 96 * 1.5f;
    private static final Map<String, Bitmap> CACHE = new HashMap<>();
    static String memDebug() { synchronized (CACHE) { return HomeApps.memDebug(CACHE.values()); } } // MEMDEBUG
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final RectF RECT = new RectF();
    private static final Set<String> VECTOR = new HashSet<>(java.util.Arrays.asList("wifi", "wifi_off", "ethernet"));
    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
    }

    static Bitmap get(Context c, String name) {
        synchronized (CACHE) {
            if (CACHE.containsKey(name)) return CACHE.get(name);
            Bitmap b = null;
            try (InputStream in = c.getAssets().open("symbols/" + name + ".png")) {
                b = BitmapFactory.decodeStream(in);
                if (b != null) {
                    b = b.extractAlpha();
                    b.setHasMipMap(true);
                }
            } catch (IOException ignored) {
            }
            CACHE.put(name, b);
            return b;
        }
    }

    static float width(Context c, String name, float pointSize) {
        if (VECTOR.contains(name)) return pointSize;   // square-ish, like the others at rest
        Bitmap b = get(c, name);
        return b == null ? 0 : b.getWidth() * pointSize / PX_PER_POINT;
    }

    /** Draws centered at (cx, cy) in the given color (lighter, translucent parts of the symbol stay lighter). */
    static void draw(Context c, Canvas canvas, String name, float cx, float cy, float pointSize, int color) {
        if (VECTOR.contains(name)) {
            synchronized (PAINT) {   // card loading (another thread) also draws symbols
                drawVectorLocked(canvas, name, cx, cy, pointSize, color);
            }
            return;
        }
        Bitmap b = get(c, name);
        if (b == null) return;
        synchronized (PAINT) {   // card loading (another thread) also draws symbols
            drawLocked(canvas, b, cx, cy, pointSize, color);
        }
    }

    private static void drawLocked(Canvas canvas, Bitmap b, float cx, float cy, float pointSize, int color) {
        float w = b.getWidth() * pointSize / PX_PER_POINT, h = b.getHeight() * pointSize / PX_PER_POINT;
        PAINT.setColor(color);
        RECT.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawBitmap(b, null, RECT, PAINT);
    }

    /** "wifi" (signal arcs over a dot), "wifi_off" (the same, struck through) and "ethernet" (a port with its pins). */
    private static void drawVectorLocked(Canvas canvas, String name, float cx, float cy, float pointSize, int color) {
        STROKE.setColor(color);
        STROKE.setStrokeWidth(pointSize * 0.1f);
        if (name.equals("ethernet")) {
            float bw = pointSize * 0.74f, bh = pointSize * 0.42f, by = cy - pointSize * 0.14f;
            RECT.set(cx - bw / 2, by - bh / 2, cx + bw / 2, by + bh / 2);
            canvas.drawRoundRect(RECT, pointSize * 0.08f, pointSize * 0.08f, STROKE);
            float pinTop = by + bh / 2, pinBottom = cy + pointSize * 0.34f;
            for (float dx : new float[] {-pointSize * 0.22f, 0, pointSize * 0.22f}) {
                canvas.drawLine(cx + dx, pinTop, cx + dx, pinBottom, STROKE);
            }
            return;
        }
        // The dot and the arcs share a center a little below cy (the glyph optically centers like the others').
        float dotY = cy + pointSize * 0.22f;
        PAINT.setColor(color);
        canvas.drawCircle(cx, dotY, pointSize * 0.07f, PAINT);
        for (int i = 0; i < 3; i++) {
            float r = pointSize * (0.22f + i * 0.21f);
            RECT.set(cx - r, dotY - r, cx + r, dotY + r);
            canvas.drawArc(RECT, 215, 110, false, STROKE);
        }
        if (name.equals("wifi_off")) {
            float r = pointSize * 0.5f;
            canvas.drawLine(cx - r * 0.78f, dotY - r * 0.78f, cx + r * 0.78f, dotY + r * 0.78f, STROKE);
        }
    }

    /** Weather symbol for an Open-Meteo WMO weather code. */
    static String weather(int code, boolean day) {
        if (code <= 1) return day ? "sunny" : "clear_night";
        if (code == 2) return day ? "partly_cloudy_day" : "partly_cloudy_night";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "foggy";
        if (code >= 51 && code <= 57) return "rainy";
        if (code == 65 || code == 67 || code == 82) return "rainy";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "snowflake";
        if (code >= 95) return "thunderstorm";
        return "rainy";
    }
}
