package ru.moytv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String EXTRA_HOME = "from_home_button";


    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppRepo repo;

    private FrameLayout root, home;
    private TextView netText, dateText, weatherText, sectionTitle, noteLeft, noteRight;
    private Icons.IconView netIcon;
    private ClockView clock;
    private GlassFrame weatherPill, dock;
    private HorizontalScrollView tileScroll;
    private LinearLayout tileRow, editBar;
    private AppsOverlay overlay;

    private final List<String> pinned = new ArrayList<>();
    private List<String> backup;
    private boolean editMode;
    private TileView picked, lastTile;
    private long weatherFetchedAt;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };
    private final BroadcastReceiver netReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateNetwork();
            if (System.currentTimeMillis() - weatherFetchedAt > 10 * 60 * 1000L) fetchWeather();
        }
    };
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refreshApps();
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - weatherFetchedAt > 30 * 60 * 1000L) fetchWeather();
            handler.postDelayed(this, 10 * 60 * 1000L);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Ui.init(this);
        Wallpaper.init(Ui.W, Ui.H);
        repo = new AppRepo(this);
        buildUi();
        setContentView(root);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgReceiver, pf);

        String cachedWeather = Weather.cached(this);
        if (cachedWeather != null) showWeather(cachedWeather);
        refreshApps();
        focusFirstTile();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, tf);
        registerReceiver(netReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateClock();
        updateNetwork();
        if (System.currentTimeMillis() - weatherFetchedAt > 30 * 60 * 1000L) fetchWeather();
        handler.removeCallbacks(weatherTick);
        handler.postDelayed(weatherTick, 10 * 60 * 1000L);
        if (getCurrentFocus() == null && !overlay.isShowing()) focusFirstTile();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(timeReceiver); } catch (Throwable ignored) {}
        try { unregisterReceiver(netReceiver); } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Throwable ignored) {}
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Home pressed: always land on a clean home screen.
        if (overlay.isShowing()) hideOverlay();
        if (editMode) finishEdit(false);
        tileScroll.smoothScrollTo(0, 0);
        focusFirstTile();
    }

    @Override
    public void onBackPressed() {
        if (overlay.isShowing()) {
            hideOverlay();
            restoreFocus();
        } else if (editMode) {
            if (picked != null) dropPicked();
            else finishEdit(false);
        }
        // On the plain home screen Back does nothing: this is home.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (editMode && picked != null && !overlay.isShowing() && e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { movePicked(-1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { movePicked(1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
        }
        if (!editMode && !overlay.isShowing() && e.getAction() == KeyEvent.ACTION_UP
                && e.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
            openAllApps(true);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        BitmapDrawable wp = new BitmapDrawable(getResources(), Wallpaper.base);
        wp.setFilterBitmap(true);
        wp.setDither(true);
        root.setBackground(wp);
        home = new FrameLayout(this);
        home.setClipChildren(false);
        root.addView(home, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // Top bar
        GlassFrame bar = new GlassFrame(this, 28);
        LinearLayout barRow = new LinearLayout(this);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        barRow.setPadding(Ui.px(28), 0, Ui.px(28), 0);
        barRow.addView(new Icons.IconView(this, Icons.TV, 22), new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        TextView brand = Ui.text(this, "Мой ТВ", 20, Ui.medium, 0xFFFFFFFF);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        blp.leftMargin = Ui.px(16);
        barRow.addView(brand, blp);
        barRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        netIcon = new Icons.IconView(this, Icons.WIFI, 22);
        netIcon.color = 0xD9FFFFFF;
        barRow.addView(netIcon, new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        netText = Ui.text(this, "", 20, Ui.medium, 0xD9FFFFFF);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        nlp.leftMargin = Ui.px(12);
        barRow.addView(netText, nlp);
        bar.addView(barRow, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        home.addView(bar, Ui.at(Ui.px(1792), Ui.px(56), Ui.TOP_CENTER, 0, 28, 0, 0));

        // Date + clock + weather
        dateText = Ui.text(this, "", 34, Ui.semibold, 0xEBFFFFFF);
        dateText.setGravity(Gravity.CENTER);
        dateText.setShadowLayer(14 * Ui.s, 0, 2 * Ui.s, 0x33000020);
        home.addView(dateText, Ui.at(Ui.MATCH, Ui.WRAP, Gravity.TOP, 0, 152, 0, 0));

        clock = new ClockView(this);
        home.addView(clock, Ui.at(Ui.MATCH, Ui.px(240), Gravity.TOP, 0, 186, 0, 0));

        weatherPill = new GlassFrame(this, 24);
        weatherPill.setPadding(Ui.px(24), Ui.px(11), Ui.px(24), Ui.px(11));
        weatherText = Ui.text(this, "", 22, Ui.medium, 0xFFFFFFFF);
        weatherPill.addView(weatherText);
        weatherPill.setVisibility(View.INVISIBLE);
        home.addView(weatherPill, Ui.at(Ui.WRAP, Ui.WRAP, Ui.TOP_CENTER, 0, 425, 0, 0));

        // Apps row
        sectionTitle = Ui.text(this, "Приложения", 24, Ui.semibold, 0xE6FFFFFF);
        sectionTitle.setGravity(Gravity.CENTER);
        home.addView(sectionTitle, Ui.at(Ui.MATCH, Ui.WRAP, Gravity.TOP, 0, 600, 0, 0));

        tileScroll = new HorizontalScrollView(this);
        tileScroll.setFillViewport(true);
        tileScroll.setHorizontalScrollBarEnabled(false);
        tileScroll.setClipChildren(false);
        tileScroll.setClipToPadding(false);
        tileScroll.setPadding(Ui.px(90), 0, Ui.px(90), 0);
        tileScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                for (int i = 0; i < tileRow.getChildCount(); i++) tileRow.getChildAt(i).invalidate();
            }
        });
        tileRow = new LinearLayout(this);
        tileRow.setGravity(Gravity.CENTER);
        tileRow.setClipChildren(false);
        tileScroll.addView(tileRow, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        home.addView(tileScroll, Ui.at(Ui.MATCH, Ui.px(270), Gravity.TOP, 0, 615, 0, 0));

        // Dock
        dock = new GlassFrame(this, 34);
        LinearLayout dockRow = new LinearLayout(this);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setClipChildren(false);
        dockRow.setPadding(Ui.px(15), 0, Ui.px(15), 0);
        dockRow.addView(dockButton(Icons.GRID, new Runnable() { public void run() { openAllApps(false); } }));
        dockRow.addView(dockButton(Icons.SEARCH, new Runnable() { public void run() { openAllApps(true); } }));
        dockRow.addView(dockButton(Icons.INPUT, new Runnable() { public void run() { openInputs(); } }));
        dockRow.addView(dockButton(Icons.PENCIL, new Runnable() { public void run() { startEdit(); } }));
        View sep = new View(this);
        sep.setBackgroundColor(0x4DFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.max(1, Ui.px(1)), Ui.px(60));
        slp.leftMargin = slp.rightMargin = Ui.px(12);
        dockRow.addView(sep, slp);
        dockRow.addView(dockButton(Icons.WIFI, new Runnable() { public void run() { openSettings(Settings.ACTION_WIFI_SETTINGS); } }));
        dockRow.addView(dockButton(Icons.GEAR, new Runnable() { public void run() { openSettings(Settings.ACTION_SETTINGS); } }));
        dock.addView(dockRow, new FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH));
        home.addView(dock, Ui.at(Ui.WRAP, Ui.px(104), Ui.BOTTOM_CENTER, 0, 0, 0, 34));

        noteLeft = Ui.text(this, "Стрелки — выбор · ОК — открыть · Назад — домой", 18, Ui.medium, 0xBFFFFFFF);
        home.addView(noteLeft, Ui.at(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.LEFT, 64, 0, 0, 58));
        noteRight = Ui.text(this, "Карандаш в доке — изменить плитки", 18, Ui.medium, 0xBFFFFFFF);
        home.addView(noteRight, Ui.at(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 64, 58));

        // Edit-mode buttons
        editBar = new LinearLayout(this);
        editBar.setGravity(Gravity.CENTER);
        editBar.setClipChildren(false);
        editBar.addView(pillButton("＋  Добавить приложение", false, new Runnable() { public void run() { openAddApps(); } }));
        editBar.addView(pillButton("Убрать с экрана", false, new Runnable() { public void run() { removeTile(); } }));
        editBar.addView(pillButton("Готово", true, new Runnable() { public void run() { finishEdit(true); } }));
        editBar.setVisibility(View.GONE);
        home.addView(editBar, Ui.at(Ui.WRAP, Ui.WRAP, Ui.BOTTOM_CENTER, 0, 0, 0, 88));

        overlay = new AppsOverlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    private View dockButton(int icon, final Runnable action) {
        GlassFrame b = new GlassFrame(this, 20);
        b.setRoom(10, 7);
        b.setFocusable(true);
        b.setClickable(true);
        b.addView(new Icons.IconView(this, icon, 38), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(92), Ui.px(92));
        lp.leftMargin = lp.rightMargin = Ui.px(1);
        b.setLayoutParams(lp);
        return b;
    }

    private View pillButton(String label, boolean primary, final Runnable action) {
        GlassFrame b = new GlassFrame(this, 24);
        b.solidWhite = primary;
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(Ui.px(30), Ui.px(16), Ui.px(30), Ui.px(16));
        b.setRoom(8, 5);
        b.addView(Ui.text(this, label, 22, Ui.semibold, primary ? 0xFF1B2240 : 0xFFFFFFFF));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.leftMargin = lp.rightMargin = Ui.px(2);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- tiles

    private void refreshApps() {
        repo.reload();
        List<String> fresh = repo.loadPinned();
        if (editMode) {
            // Keep the order being edited, drop anything uninstalled meanwhile.
            List<String> keep = new ArrayList<>();
            for (String p : pinned) if (repo.get(p) != null) keep.add(p);
            pinned.clear();
            pinned.addAll(keep);
        } else {
            pinned.clear();
            pinned.addAll(fresh);
        }
        rebuildTiles(-1);
    }

    private void rebuildTiles(int focusIndex) {
        tileRow.removeAllViews();
        lastTile = null;
        for (String pkg : pinned) {
            AppRepo.App a = repo.get(pkg);
            if (a == null) continue;
            addTile(new TileView(this, a.pkg, a.label, repo.icon(a), Icons.GRID, 230, 200, 96));
        }
        if (tileRow.getChildCount() == 0) {
            TileView add = new TileView(this, "", "Добавить", null, Icons.PLUS, 230, 200, 96);
            addTile(add);
        }
        if (focusIndex >= 0 && tileRow.getChildCount() > 0) {
            final View v = tileRow.getChildAt(Math.min(focusIndex, tileRow.getChildCount() - 1));
            v.requestFocus();
        }
    }

    private void addTile(final TileView t) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTileClick(t);
            }
        });
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                if (f) lastTile = t;
            }
        });
        tileRow.addView(t, lp);
    }

    private void onTileClick(TileView t) {
        if (t.key.length() == 0) {
            if (!editMode) startEdit();
            openAddApps();
            return;
        }
        if (!editMode) {
            launch(t.key);
            return;
        }
        if (picked == t) {
            dropPicked();
        } else {
            if (picked != null) picked.setDashed(false);
            picked = t;
            t.setDashed(true);
            sectionTitle.setText("Двигай стрелками ← →, потом ОК — положить");
        }
    }

    private void movePicked(int dir) {
        int idx = pinned.indexOf(picked.key);
        int to = idx + dir;
        if (idx < 0 || to < 0 || to >= pinned.size()) return;
        Collections.swap(pinned, idx, to);
        String key = picked.key;
        rebuildTiles(to);
        TileView t = (TileView) tileRow.getChildAt(to);
        if (t != null && t.key.equals(key)) {
            picked = t;
            t.setDashed(true);
            t.requestFocus();
        } else {
            picked = null;
        }
    }

    private void dropPicked() {
        if (picked != null) picked.setDashed(false);
        picked = null;
        sectionTitle.setText(EDIT_TITLE);
    }

    private static final String EDIT_TITLE = "Изменение: ОК на плитке — взять, ← → — двигать, ОК — положить";

    private void startEdit() {
        if (editMode) return;
        editMode = true;
        backup = new ArrayList<>(pinned);
        picked = null;
        dock.setVisibility(View.GONE);
        noteLeft.setVisibility(View.GONE);
        noteRight.setVisibility(View.GONE);
        editBar.setVisibility(View.VISIBLE);
        sectionTitle.setText(EDIT_TITLE);
        focusFirstTile();
    }

    private void finishEdit(boolean save) {
        if (!editMode) return;
        if (picked != null) picked.setDashed(false);
        picked = null;
        if (save) {
            repo.savePinned(pinned);
        } else if (backup != null) {
            pinned.clear();
            pinned.addAll(backup);
        }
        editMode = false;
        editBar.setVisibility(View.GONE);
        dock.setVisibility(View.VISIBLE);
        noteLeft.setVisibility(View.VISIBLE);
        noteRight.setVisibility(View.VISIBLE);
        sectionTitle.setText("Приложения");
        rebuildTiles(0);
    }

    private void removeTile() {
        TileView t = picked != null ? picked : lastTile;
        if (t == null || t.key.length() == 0) {
            Toast.makeText(this, "Сначала выбери плитку стрелками", Toast.LENGTH_SHORT).show();
            return;
        }
        int idx = pinned.indexOf(t.key);
        pinned.remove(t.key);
        picked = null;
        sectionTitle.setText(EDIT_TITLE);
        rebuildTiles(Math.max(0, idx));
    }

    private void focusFirstTile() {
        if (tileRow.getChildCount() > 0) tileRow.getChildAt(0).requestFocus();
    }

    private void restoreFocus() {
        if (lastTile != null && lastTile.getParent() != null) lastTile.requestFocus();
        else focusFirstTile();
    }

    // ---------------------------------------------------------------- overlays

    private void openAllApps(boolean focusSearch) {
        List<AppsOverlay.Item> items = new ArrayList<>();
        for (final AppRepo.App a : repo.all()) {
            items.add(new AppsOverlay.Item(a.pkg, a.label, repo.icon(a), Icons.GRID, new Runnable() {
                @Override
                public void run() {
                    hideOverlay();
                    launch(a.pkg);
                }
            }));
        }
        showOverlay("Все приложения", items, true, focusSearch);
    }

    private void openAddApps() {
        List<AppsOverlay.Item> items = new ArrayList<>();
        for (final AppRepo.App a : repo.all()) {
            if (pinned.contains(a.pkg)) continue;
            items.add(new AppsOverlay.Item(a.pkg, a.label, repo.icon(a), Icons.GRID, new Runnable() {
                @Override
                public void run() {
                    hideOverlay();
                    pinned.add(a.pkg);
                    rebuildTiles(pinned.size() - 1);
                }
            }));
        }
        showOverlay("Добавить на главный экран", items, true, false);
    }

    private void openInputs() {
        List<AppsOverlay.Item> items = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (final TvInputInfo info : tim.getTvInputList()) {
                    if (!info.isPassthroughInput()) continue;
                    CharSequence l = info.loadLabel(this);
                    final String label = l == null ? info.getId() : l.toString();
                    items.add(new AppsOverlay.Item(info.getId(), label, null, Icons.INPUT, new Runnable() {
                        @Override
                        public void run() {
                            hideOverlay();
                            Intent i = new Intent(Intent.ACTION_VIEW,
                                    TvContract.buildChannelUriForPassthroughInput(info.getId()));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                startActivity(i);
                            } catch (Throwable t) {
                                Toast.makeText(MainActivity.this, "Не получилось открыть " + label, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }));
                }
            }
        } catch (Throwable ignored) {
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "Телевизор не сообщил о входах HDMI", Toast.LENGTH_SHORT).show();
            return;
        }
        showOverlay("Входы", items, false, false);
    }

    private void showOverlay(String heading, List<AppsOverlay.Item> items, boolean withSearch, boolean focusSearch) {
        home.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        overlay.show(heading, items, withSearch, focusSearch);
    }

    private void hideOverlay() {
        overlay.hide();
        home.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    // ---------------------------------------------------------------- actions

    private void launch(String pkg) {
        Intent i = repo.launchIntent(pkg);
        if (i == null) {
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {
                Toast.makeText(this, "Настройки недоступны", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------------------------------------------------------- status: clock, network, weather

    private void updateClock() {
        Date now = new Date();
        Locale ru = new Locale("ru");
        clock.setTime(new SimpleDateFormat("HH:mm", ru).format(now));
        dateText.setText(new SimpleDateFormat("EEEE, d MMMM", ru).format(now));
    }

    private void updateNetwork() {
        String label = "Нет сети";
        int icon = Icons.NO_WIFI;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    label = "Кабель подключён";
                    icon = Icons.TV;
                } else {
                    label = "Wi-Fi подключён";
                    icon = Icons.WIFI;
                }
            }
        } catch (Throwable ignored) {
        }
        netText.setText(label);
        netIcon.setType(icon);
    }

    private boolean weatherLoading;

    private void fetchWeather() {
        if (weatherLoading) return;
        weatherLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String text = Weather.fetch(MainActivity.this);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        weatherLoading = false;
                        if (text != null) {
                            weatherFetchedAt = System.currentTimeMillis();
                            showWeather(text);
                        }
                    }
                });
            }
        }).start();
    }

    private void showWeather(String text) {
        weatherText.setText(text);
        weatherPill.setVisibility(View.VISIBLE);
    }
}
