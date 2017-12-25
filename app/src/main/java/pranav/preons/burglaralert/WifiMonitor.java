/*
 * Copyright 2018  Pranav Raut
 *
 *         Permission is hereby granted, free of charge, to any person obtaining a copy of this
 *         software and associated documentation files (the "Software"), to deal in the Software
 *         without restriction, including without limitation the rights to use, copy, modify, merge,
 *         publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 *         to whom the Software is furnished to do so, subject to the following conditions:
 *
 *         The above copyright notice and this permission notice shall be included in all copies
 *         or substantial portions of the Software.
 *
 *         THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 *         INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 *         PURPOSE AND NONINFINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *         FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 *         OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 *         DEALINGS IN THE SOFTWARE.
 */

package pranav.preons.burglaralert;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import pranav.utilities.Utilities;

import static pranav.utilities.DataBaseHelper.SQLiteQuery.TAG;
import static pranav.utilities.Utilities.getBackup;

public class WifiMonitor extends Service {
    public static final String BROADCAST_ACTION = "wifi.data";
    static boolean toStop = false;
    private Monitor monitor;
    private Intent intent;
    private WifiInfo wifiInfo;
    private Utilities.NotificationHandler handler;
    private avg a = new avg();
    private SharedPreferences prefs;
    private WifiManager wifiManager;


    @Override
    public void onCreate() {
        super.onCreate();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "Wifi Monitor")
                .setColor(0xff2962FF)
                .setAutoCancel(false)
                .setTicker("Protected")
                .setOngoing(true)
                .setWhen(System.currentTimeMillis())
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSmallIcon(R.drawable.ic_monitor_running);

        monitor = new Monitor();
        intent = new Intent(BROADCAST_ACTION);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        handler = new Utilities.NotificationHandler(this, builder, "Wifi Monitor");
        handler.removeAll();
        handler.notify(MainActivity.class);

        startForeground(handler.getId(), builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        monitor.isRunning = true;
        monitor.start();
        return START_STICKY;
    }

    private void sendData(int dbm, int per) {
        String name = wifiInfo.getSSID().replace("\"", "");
        a.add(dbm);

        intent.putExtra("strength", dbm);
        intent.putExtra("percentage", per);
        intent.putExtra("name", name);
        intent.putExtra("avg", a.getA());

        handler.updateNotification("Connected to: " + name + " (" + per + "%)",
                "RSSI: " + dbm + "dbm BSSID: " + wifiInfo.getBSSID());

        if (prefs.getBoolean("notifications_on_bag_open", true) &&
                wifiManager.isWifiEnabled() && wifiInfo.getNetworkId() == -1 && a.getA() != 0) {
            Uri uri;
            final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_RING),
                        AudioManager.FLAG_VIBRATE);
            }
            try {
                uri = Uri.parse(prefs.getString("notifications_on_bag_open_ringtone", null));
            } catch (NullPointerException e) {
                Log.e(TAG, "onCreate: ", e);
                uri = getBackup();
            }
            Utilities.NotificationHandler.notify(this, "Smart Bag Compromised!",
                    "%lSomeone is trying to open your smart bag. Check immediately!",
                    NotificationCompat.PRIORITY_MAX, prefs.getBoolean("vibrate", true), uri);
            stopSelf();
        } else if (dbm < -58) {
            startService(new Intent(this, PlayerService.class));
            stopSelf();
        }
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (monitor.isRunning) {
            monitor.isRunning = false;
            Utilities.NotificationHandler.notify(this, "Service Stopped", null, MainActivity.class);
            stopForeground(true);
            monitor.interrupt();
            sendBroadcast(intent.putExtra("percentage", 0));
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class Monitor extends Thread {
        boolean isRunning;
        private long UpdateDuration = 500;

        public void run() {
            while (isRunning) {
                if (wifiManager != null) {
                    wifiInfo = wifiManager.getConnectionInfo();
                }
                int d = wifiInfo.getRssi();
                sendData(d, WifiManager.calculateSignalLevel(d, 100));
                try {
                    Thread.sleep(UpdateDuration);
                } catch (InterruptedException e) {
                    isRunning = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class avg {
        long a = 0, n = 0;

        void add(long a) {
            this.a += a;
            n++;
        }

        long getA() {
            return a / n;
        }
    }
}
