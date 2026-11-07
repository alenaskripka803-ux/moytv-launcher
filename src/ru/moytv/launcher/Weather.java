package ru.moytv.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Weather for wherever the TV is. A TV has no GPS, so the city comes from the internet connection
 * (like weather websites do). Two location services and two weather services, each with a fallback;
 * the last good place and forecast are remembered so something shows right away after power-on.
 */
final class Weather {
    static final class Place {
        String city;
        double lat, lon;
    }

    private static final long PLACE_TTL = 6 * 60 * 60 * 1000L;

    /** Last forecast line saved on the device, or null. */
    static String cached(Context c) {
        return prefs(c).getString("weather_text", null);
    }

    /** Blocking: runs on a background thread. Returns the text for the pill, or null. */
    static String fetch(Context c) {
        Place p = place(c);
        if (p == null) return null;
        String w = openMeteo(p);
        if (w == null) w = wttr(p);
        if (w == null) return null;
        String text = w.replace("{city}", p.city);
        prefs(c).edit().putString("weather_text", text).apply();
        return text;
    }

    // ------------------------------------------------------------ location

    private static Place place(Context c) {
        SharedPreferences sp = prefs(c);
        long at = sp.getLong("place_at", 0);
        Place cached = null;
        if (sp.contains("place_city")) {
            cached = new Place();
            cached.city = sp.getString("place_city", "");
            cached.lat = Double.longBitsToDouble(sp.getLong("place_lat", 0));
            cached.lon = Double.longBitsToDouble(sp.getLong("place_lon", 0));
            if (System.currentTimeMillis() - at < PLACE_TTL) return cached;
        }
        Place p = ipwho();
        if (p == null) p = geojs();
        if (p == null) return cached;
        sp.edit().putString("place_city", p.city)
                .putLong("place_lat", Double.doubleToRawLongBits(p.lat))
                .putLong("place_lon", Double.doubleToRawLongBits(p.lon))
                .putLong("place_at", System.currentTimeMillis()).apply();
        return p;
    }

    private static Place ipwho() {
        try {
            JSONObject o = new JSONObject(get("https://ipwho.is/?lang=ru&fields=success,city,latitude,longitude"));
            if (!o.optBoolean("success", false)) return null;
            Place p = new Place();
            p.city = o.optString("city", "");
            p.lat = o.getDouble("latitude");
            p.lon = o.getDouble("longitude");
            return p.city.length() > 0 ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Place geojs() {
        try {
            JSONObject o = new JSONObject(get("https://get.geojs.io/v1/ip/geo.json"));
            Place p = new Place();
            p.city = ruCity(o.optString("city", ""));
            p.lat = Double.parseDouble(o.getString("latitude"));
            p.lon = Double.parseDouble(o.getString("longitude"));
            return p.city.length() > 0 ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The backup service answers in English; translate the most likely cities. */
    private static String ruCity(String en) {
        String[][] map = {
                {"Rostov-on-Don", "Ростов-на-Дону"}, {"Moscow", "Москва"}, {"Saint Petersburg", "Санкт-Петербург"},
                {"Krasnodar", "Краснодар"}, {"Taganrog", "Таганрог"}, {"Bataysk", "Батайск"},
                {"Novocherkassk", "Новочеркасск"}, {"Aksay", "Аксай"}, {"Shakhty", "Шахты"},
                {"Volgograd", "Волгоград"}, {"Voronezh", "Воронеж"}, {"Sochi", "Сочи"}};
        for (String[] m : map) if (m[0].equalsIgnoreCase(en)) return m[1];
        return en;
    }

    // ------------------------------------------------------------ weather

    private static String openMeteo(Place p) {
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                    p.lat, p.lon);
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            long t = Math.round(cur.getDouble("temperature_2m"));
            int code = cur.optInt("weather_code", 0);
            return line(symbol(code), t, describe(code));
        } catch (Throwable e) {
            return null;
        }
    }

    private static String wttr(Place p) {
        try {
            String url = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1&lang=ru", p.lat, p.lon);
            JSONObject cur = new JSONObject(get(url)).getJSONArray("current_condition").getJSONObject(0);
            long t = Math.round(Double.parseDouble(cur.getString("temp_C")));
            int wwo = Integer.parseInt(cur.optString("weatherCode", "116"));
            String desc = "";
            JSONArray ru = cur.optJSONArray("lang_ru");
            if (ru != null && ru.length() > 0) desc = ru.getJSONObject(0).optString("value", "");
            return line(wwoSymbol(wwo), t, desc);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String line(String sym, long t, String desc) {
        StringBuilder sb = new StringBuilder();
        sb.append(sym).append("  {city} · ").append(t > 0 ? "+" : "").append(t).append('°');
        if (desc != null && desc.length() > 0) {
            String d = desc.trim();
            sb.append(" · ").append(d.substring(0, 1).toUpperCase(new Locale("ru"))).append(d.substring(1).toLowerCase(new Locale("ru")));
        }
        return sb.toString();
    }

    static String symbol(int code) {
        if (code == 0) return "☀";
        if (code <= 2) return "⛅";
        if (code == 3) return "☁";
        if (code == 45 || code == 48) return "≋";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄";
        if (code >= 95) return "⚡";
        return "☂";
    }

    static String describe(int code) {
        if (code == 0) return "Ясно";
        if (code == 1) return "Преимущественно ясно";
        if (code == 2) return "Переменная облачность";
        if (code == 3) return "Пасмурно";
        if (code == 45 || code == 48) return "Туман";
        if (code >= 51 && code <= 57) return "Морось";
        if (code >= 61 && code <= 67) return "Дождь";
        if (code >= 71 && code <= 77) return "Снег";
        if (code >= 80 && code <= 82) return "Ливень";
        if (code == 85 || code == 86) return "Снегопад";
        if (code >= 95) return "Гроза";
        return "";
    }

    private static String wwoSymbol(int c) {
        if (c == 113) return "☀";
        if (c == 116) return "⛅";
        if (c == 119 || c == 122) return "☁";
        if (c == 143 || c == 248 || c == 260) return "≋";
        if (c == 200 || c >= 386 && c <= 395) return "⚡";
        if (c == 179 || c == 227 || c == 230 || (c >= 323 && c <= 338) || c == 368 || c == 371) return "❄";
        return "☂";
    }

    // ------------------------------------------------------------ http

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "MoyTV/1.2 (Android TV launcher)");
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    private Weather() {}
}
