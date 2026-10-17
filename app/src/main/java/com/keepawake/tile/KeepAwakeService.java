package com.keepawake.tile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.quicksettings.TileService;

/**
 * Foreground service that holds a screen wake lock so the display never
 * times out. Runs until the user turns it off (tile or notification button).
 */
public class KeepAwakeService extends Service {

    public static final String ACTION_STOP = "com.keepawake.tile.STOP";
    private static final String CHANNEL_ID = "keep_awake";
    private static final int NOTIFICATION_ID = 1;

    private static volatile boolean running = false;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() {
        return running;
    }

    /** True once the user has set battery usage to Unlimited / unrestricted for this app. */
    public static boolean hasUnlimitedBackground(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) ctx.getSystemService(POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
    }

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, KeepAwakeService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, KeepAwakeService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            // Deprecated, but still the only way a background component can keep
            // the screen on without drawing an overlay window.
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "KeepAwake::Screen");
            wl.setReferenceCounted(false);
            wl.acquire(); // no timeout: held until disabled
            wakeLock = wl;
        }

        running = true;
        refreshTile();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        running = false;
        refreshTile();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshTile() {
        TileService.requestListeningState(this,
                new ComponentName(this, KeepAwakeTileService.class));
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Keep Awake", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }

        Intent stopIntent = new Intent(this, KeepAwakeService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return b.setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Keep Awake is on")
                .setContentText("Screen won't turn off until you disable it")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Turn off", stopPi).build())
                .build();
    }
}
