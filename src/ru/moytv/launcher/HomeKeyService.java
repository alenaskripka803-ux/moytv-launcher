package ru.moytv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Catches the remote's Home button and opens "Мой ТВ" instead of the stock Yandex home,
 * and brings "Мой ТВ" up when the TV wakes or boots. Reads nothing from the screen.
 */
public class HomeKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver screenOn;

    private final Runnable goHome = new Runnable() {
        @Override
        public void run() {
            Intent i = new Intent(HomeKeyService.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            i.putExtra(MainActivity.EXTRA_HOME, true);
            try {
                startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        screenOn = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                // Let the stock shell finish waking up, then take over.
                handler.removeCallbacks(goHome);
                handler.postDelayed(goHome, 2000);
            }
        };
        try {
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        } catch (Throwable ignored) {
        }
        // Right after the TV boots, open "Мой ТВ". Never at other times, so a movie is not interrupted.
        if (SystemClock.elapsedRealtime() < 4 * 60 * 1000L) handler.postDelayed(goHome, 1500);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (e.getAction() == KeyEvent.ACTION_UP) handler.post(goHome);
            return true;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (screenOn != null) {
            try {
                unregisterReceiver(screenOn);
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }
}
