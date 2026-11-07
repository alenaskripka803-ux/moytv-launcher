package ru.moytv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Installed apps and the user's pinned list. */
final class AppRepo {
    static final class App {
        final String pkg;
        final String label;
        final ResolveInfo info;

        App(String pkg, String label, ResolveInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    /** Favourite apps, tried in this order the first time the launcher starts. */
    private static final String[] DEFAULTS = {
            "ru.ivi.client", "com.vk.vkvideo", "com.vk.tv", "com.google.android.youtube.tv",
            "com.google.android.youtube", "top.rootu.lampa", "ru.rutube.app", "ru.rutube.tv",
            "com.brouken.player", "ru.yourok.torrserve", "ru.kinopoisk.tv", "ru.kinopoisk"};

    private static final String[] NOT_PINNED_BY_DEFAULT = {
            "com.android.tv.settings", "com.android.settings", "com.spocky.projengmenu"};

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences prefs;
    private final Map<String, App> apps = new LinkedHashMap<>();
    private final Map<String, Drawable> icons = new HashMap<>();

    AppRepo(Context c) {
        ctx = c;
        pm = c.getPackageManager();
        prefs = c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    void reload() {
        apps.clear();
        List<App> list = new ArrayList<>();
        addAll(list, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addAll(list, Intent.CATEGORY_LAUNCHER);
        final Collator col = Collator.getInstance(new Locale("ru"));
        Collections.sort(list, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return col.compare(a.label, b.label);
            }
        });
        for (App a : list) apps.put(a.pkg, a);
    }

    private void addAll(List<App> out, String category) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(i, 0);
        } catch (Throwable t) {
            return;
        }
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName())) continue;
            boolean seen = false;
            for (App a : out) if (a.pkg.equals(pkg)) { seen = true; break; }
            if (seen) continue;
            CharSequence l = ri.loadLabel(pm);
            out.add(new App(pkg, l == null ? pkg : l.toString(), ri));
        }
    }

    List<App> all() {
        return new ArrayList<>(apps.values());
    }

    App get(String pkg) {
        return apps.get(pkg);
    }

    Drawable icon(App a) {
        Drawable d = icons.get(a.pkg);
        if (d == null) {
            try {
                d = a.info.activityInfo.loadIcon(pm);
            } catch (Throwable t) {
                d = pm.getDefaultActivityIcon();
            }
            icons.put(a.pkg, d);
        }
        return d;
    }

    Intent launchIntent(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    List<String> loadPinned() {
        List<String> out = new ArrayList<>();
        String saved = prefs.getString("pinned", null);
        if (saved == null) {
            for (String p : DEFAULTS) if (apps.containsKey(p) && !out.contains(p)) out.add(p);
            if (out.size() < 6) {
                for (App a : apps.values()) {
                    if (out.size() >= 6) break;
                    boolean skip = out.contains(a.pkg);
                    for (String n : NOT_PINNED_BY_DEFAULT) if (n.equals(a.pkg)) skip = true;
                    if (!skip) out.add(a.pkg);
                }
            }
            savePinned(out);
        } else if (saved.length() > 0) {
            for (String p : saved.split(",")) if (apps.containsKey(p) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    void savePinned(List<String> list) {
        prefs.edit().putString("pinned", TextUtils.join(",", list)).apply();
    }
}
