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

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import pranav.utilities.GPSTracker;
import pranav.utilities.Utilities;

import static android.content.ContentValues.TAG;
import static pranav.utilities.Utilities.getBackup;

public class PlayerService extends Service {

    private MediaPlayer player = new MediaPlayer();
    private SharedPreferences prefs;
    private sms sms;
    private PendingIntent sentPending;
    private PendingIntent deliveredPending;
    private BroadcastReceiver r, d;
    private Vibrator vibrator;

    public static String msgBuilder(Context context) {
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
        DateFormat timeFormat = new SimpleDateFormat("hh:mm:ss", Locale.ENGLISH);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String name = prefs.getString("user_name", "John Smith");
        String msg = prefs.getString("msg_text", context.getString(R.string.pref_default_message)).replace("%name", name)
                .replace("%date", dateFormat.format(new Date()))
                .replace("%time", timeFormat.format(new Date()));
        GPSTracker gpsTracker = new GPSTracker(context);
        if (gpsTracker.getIsGPSTrackingEnabled()) {
            String stringLatitude = String.valueOf(gpsTracker.latitude);
            String stringLongitude = String.valueOf(gpsTracker.longitude);

            // Context c = PlayerService.this;
            /*String country = gpsTracker.getCountryName(c);
              String city = gpsTracker.getLocality(c);
              String postalCode = gpsTracker.getPostalCode(c);*/
            //String addressLine = gpsTracker.getAddressLine(c);
            //msg = msg.replace("%addr", addressLine);

            msg = msg.replace("%lat", stringLatitude);
            msg = msg.replace("%long", stringLongitude);
            msg = msg.replace("%url", "https://www.google.co.in/maps/search/" + stringLatitude + "," + stringLongitude);
        }
        msg = msg.replace("\\n", "\n").replace("\\t", "\t");
        return msg;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        sms = new sms();

        sentPending = PendingIntent.getBroadcast(this,
                0, new Intent("SENT"), 0);
        registerReceiver(r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "onReceive: " + "sent");
                        Toast.makeText(getBaseContext(), "sent", Toast.LENGTH_LONG).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Log.d(TAG, "onReceive: " + "Not Sent: Generic failure.");
                        Toast.makeText(getBaseContext(), "Not Sent: Generic failure.",
                                Toast.LENGTH_LONG).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(getBaseContext(), "Not Sent: No service (possibly, no SIM-card).",
                                Toast.LENGTH_LONG).show();
                        Log.d(TAG, "onReceive: " + "Not Sent: No service (possibly, no SIM-card).");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Log.d(TAG, "onReceive: " + "Not Sent: Null PDU.");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Log.d(TAG, "onReceive: " +
                                "Not Sent: Radio off (possibly, Airplane mode enabled in Settings).");
                        Toast.makeText(getBaseContext(),
                                "Not Sent: Radio off (possibly, Airplane mode enabled in Settings).",
                                Toast.LENGTH_LONG).show();
                        break;
                }
            }
        }, new IntentFilter("SENT"));

        deliveredPending = PendingIntent.getBroadcast(this,
                0, new Intent("DELIVERED"), 0);

        registerReceiver(d = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        Toast.makeText(getBaseContext(), "Delivered.",
                                Toast.LENGTH_LONG).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getBaseContext(), "Not Delivered: Canceled.",
                                Toast.LENGTH_LONG).show();
                        break;
                }
            }
        }, new IntentFilter("DELIVERED"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sms.isRunning = true;
        sms.start();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "Alarm player")
                .setColor(0xffea4334)
                .setAutoCancel(false)
                .setTicker("Protected")
                .setOngoing(true)
                .setContentTitle("Alert")
                .setContentText("Select this notification to stop alarm")
                .setWhen(System.currentTimeMillis())
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSmallIcon(R.drawable.ic_monitor_running);

        builder.setPriority(NotificationCompat.PRIORITY_MIN);

        Utilities.NotificationHandler handler = new Utilities.NotificationHandler(this, builder, "Wifi Monitor");
        handler.removeAll();
        handler.notify(PasswordActivity.class);

        startForeground(handler.getId(), builder.build());
        alert();
        synchronized (HiddenCameraThread.class) {
            new HiddenCameraThread(this).start();
        }
        startActivity(new Intent(this, PasswordActivity.class));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        player.stop();
        player.release();
        sms.isRunning = false;
        sms.interrupt();
        sms = null;
        vibrator.cancel();
        try {
            unregisterReceiver(r);
            unregisterReceiver(d);
        } catch (Exception ignored) {
        }
    }

    private void alert() {
        DevicePolicyManager mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mAdminName = new ComponentName(this, AdminReceiver.class);
        if (mDPM != null && mDPM.isAdminActive(mAdminName)) {
            mDPM.lockNow();
        }
        player.setLooping(true);
        try {
            player.setDataSource(this,
                    Uri.parse(prefs.getString("alarm_on_out_of_range", null)));
        } catch (IOException | NullPointerException e) {
            try {
                player.setDataSource(this, getBackup());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), AudioManager.FLAG_SHOW_UI);
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0)
                player.setAudioStreamType(AudioManager.STREAM_ALARM);
            try {
                player.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            player.start();
        }
        if (vibrator != null && vibrator.hasVibrator() && prefs.getBoolean("vibrate", true)) {
            long[] pattern = {0, 300, 100, 300, 750};
            vibrator.vibrate(pattern, 0);
        }
    }

    private void sendSms(String phone_Num, String msg) {
        SmsManager sms = SmsManager.getDefault();
        Utilities.NotificationHandler.notify(PlayerService.this, "Sending SMS to " + phone_Num, "%l" + msg, 0, false, null);
        Log.i(TAG, "run: sms send! " + msg);
        sms.sendMultipartTextMessage(phone_Num, null, sms.divideMessage(msg), new ArrayList<>(Arrays.asList(sentPending, sentPending)), new ArrayList<>(Arrays.asList(deliveredPending, deliveredPending)));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class sms extends Thread {

        boolean isRunning = true;

        String phone_Num = prefs.getString("phone_number", "+918149920780");

        @Override
        public void run() {
            super.run();
            while (isRunning) {
                if (!prefs.getBoolean("send_sms", false) || phone_Num.isEmpty()) {
                    isRunning = false;
                    break;
                }
                sendSms(phone_Num, msgBuilder(PlayerService.this));
                try {
                    sleep((long) (5 * 60 * 1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
