package io.freemytv;

import android.content.Context;

/**
 * Day/night, fully offline: today's sunrise and sunset at Config's latitude/longitude (NOAA's standard sunrise/sunset
 * formula), used by the home screen and the screensaver to switch between the light and dark looks and to pick the
 * sky's color. No network, no settings beyond config.properties.
 */
final class HomeData {
    private static HomeData instance;

    static synchronized HomeData get(Context c) {
        if (instance == null) instance = new HomeData();
        return instance;
    }

    private HomeData() {}

    /**
     * Night at your location: from sunset to sunrise (like Apple's "Automatic" appearance: the light design darkens).
     * Computed locally (NOAA's sunrise/sunset formula, from Config's latitude/longitude and today's date): the day
     * ⇄ night switch works fully offline.
     */
    boolean isNight(long now) {
        String theme = Prefs.theme();
        if (theme.equals("dark")) return true;
        if (theme.equals("light")) return false;
        long t = Shaders.timeOfDay(now, 0);
        long[] sun = sunTimes(now);
        long rise = Shaders.timeOfDay(sun[0], 6 * 60 * 60_000L);
        long set = Shaders.timeOfDay(sun[1], 18 * 60 * 60_000L);
        return t < rise || t >= set;
    }

    /**
     * Today's sunrise and sunset (epoch ms, UTC instant) at Config's latitude/longitude, from NOAA's standard
     * sunrise/sunset formula (https://edwilliams.org/sunrise_sunset_algorithm.html). No network: this is what lets
     * the day ⇄ night switch, and the sky's color, work offline. Near the poles, around the solstices, the sun may
     * not rise or set at all that day; cosH is clamped to the nearest edge case (always day, or always night) rather
     * than returning NaN.
     */
    static long[] sunTimes(long now) {
        double lat, lon;
        try {
            lat = Double.parseDouble(Config.LATITUDE);
            lon = Double.parseDouble(Config.LONGITUDE);
        } catch (NumberFormatException e) {
            return new long[] {0, 0};   // isNight() falls back to 6:00/18:00
        }
        java.util.Calendar local = java.util.Calendar.getInstance();
        local.setTimeInMillis(now);
        int n = local.get(java.util.Calendar.DAY_OF_YEAR);
        int year = local.get(java.util.Calendar.YEAR), month = local.get(java.util.Calendar.MONTH), day = local.get(java.util.Calendar.DAY_OF_MONTH);
        return new long[] {sunTime(n, year, month, day, lat, lon, true), sunTime(n, year, month, day, lat, lon, false)};
    }

    /** One of the two NOAA sunrise/sunset instants (UT comes out of the formula directly, so the result is UTC). */
    private static long sunTime(int n, int year, int month, int day, double lat, double lon, boolean rise) {
        double lngHour = lon / 15.0;
        double t = n + ((rise ? 6.0 : 18.0) - lngHour) / 24.0;
        double m = (0.9856 * t) - 3.289;
        double l = norm360(m + 1.916 * Math.sin(Math.toRadians(m)) + 0.020 * Math.sin(Math.toRadians(2 * m)) + 282.634);
        double ra = norm360(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))));
        ra += (Math.floor(l / 90) - Math.floor(ra / 90)) * 90;   // same quadrant as l
        ra /= 15.0;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(l));
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(90.833)) - sinDec * Math.sin(Math.toRadians(lat))) / (cosDec * Math.cos(Math.toRadians(lat)));
        cosH = Math.max(-1, Math.min(1, cosH));   // polar day/night: clamp instead of NaN
        double h = (rise ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH))) / 15.0;
        double ut = norm24(h + ra - (0.06571 * t) - 6.622 - lngHour);
        java.util.Calendar out = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        out.clear();
        out.set(year, month, day, 0, 0, 0);
        out.add(java.util.Calendar.MILLISECOND, Math.round((float) (ut * 3600_000)));
        return out.getTimeInMillis();
    }

    private static double norm360(double v) {
        v = v % 360;
        return v < 0 ? v + 360 : v;
    }

    private static double norm24(double v) {
        v = v % 24;
        return v < 0 ? v + 24 : v;
    }
}
