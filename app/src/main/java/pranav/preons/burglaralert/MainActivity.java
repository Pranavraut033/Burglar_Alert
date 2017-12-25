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

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import pranav.utilities.GPSTracker;
import pranav.utilities.Utilities;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static pranav.preons.burglaralert.GMailAuth.PREF_AUTO_MAIL;
import static pranav.preons.burglaralert.WifiMonitor.toStop;
import static pranav.utilities.Utilities.hasPermissions;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE = 0x7b;
    private static final int PERMISSION = 0x4d2;
    SharedPreferences settings;
    SharedPreferences.Editor editor;
    AlertDialog dialog;
    int lastX = 0;
    private Toolbar toolbar;
    private ToggleButton mainBtn;
    private @Nullable
    Intent wifiMonitor;
    private BroadcastReceiver receiver;
    private GPSTracker gpsTracker;
    private LineGraphSeries<DataPoint> series;
    private LineGraphSeries<DataPoint> avg;
    private String[] permissionList = new String[]{
            Manifest.permission.GET_ACCOUNTS, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.SEND_SMS, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //startService(new Intent(this, PlayerService.class));
        /*synchronized (HiddenCameraThread.class) {
            new HiddenCameraThread(this).start();
        }*/
        //startActivity(new Intent(this, PasswordActivity.class));

        setContentView(R.layout.activity_main);
        gpsTracker = new GPSTracker(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        editor = settings.edit();
        editor.apply();

        if (Utilities.isServiceRunning(this, PlayerService.class))
            startActivity(new Intent(this, PasswordActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        mainBtn = findViewById(R.id.main_btn);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mainBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (wifiManager == null) {
                    Toast.makeText(this, "Wifi not support", Toast.LENGTH_LONG).show();
                    mainBtn.setChecked(false);
                    return;
                }
                if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED) {
                    new AlertDialog.Builder(this)
                            .setTitle("Wifi Disabled")
                            .setMessage("The device is not enabled. Device should be connected to a valid Wifi router / Hotspot in order to work")
                            .setPositiveButton("Enable", (dialogInterface, i) -> wifiManager.setWifiEnabled(true))
                            .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss()).show();
                    toolbar.setTitle("Tap here to enable");
                    mainBtn.setChecked(false);
                    return;
                }
                if (wifiManager.isWifiEnabled() && wifiManager.getConnectionInfo().getNetworkId() == -1) {
                    toolbar.setTitle("Tap here to connect");
                    mainBtn.setChecked(false);
                    new AlertDialog.Builder(this)
                            .setTitle("Wifi not connected")
                            .setMessage("The device is not connected to a valid wifi. Device should be connected to a valid Wifi router / Hotspot in order to work")
                            .setPositiveButton("Settings", (dialogInterface, i) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                            .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss()).show();
                    return;
                }
                toStop = false;

                if (!Utilities.isServiceRunning(this, WifiMonitor.class))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(wifiMonitor);
                    else startService(wifiMonitor);
            } else {
                toStop = true;
                stopService(wifiMonitor);
            }
        });
        toolbar.setOnClickListener(v -> {
            if (wifiManager != null)
                if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED)
                    wifiManager.setWifiEnabled(true);
                else if (wifiManager.getConnectionInfo().getNetworkId() == -1)
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Utilities.NotificationHandler.createChannel(getApplicationContext(),
                    NotificationManager.IMPORTANCE_MIN, "Wifi Monitor",
                    R.string.wifi_channel, R.string.wifi_channel_description);
        }

        series = new LineGraphSeries<>(new DataPoint[]{});
        series.setAnimated(true);
        series.setColor(getResources().getColor(R.color.colorPrimary));
        series.setTitle("cur:-127dbm");

        avg = new LineGraphSeries<>(new DataPoint[]{});
        avg.setThickness(3);
        avg.setColor(getResources().getColor(R.color.colorAccent));
        avg.setTitle("avg:-127dbm");

        GraphView graph = findViewById(R.id.graph);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-90);
        graph.getViewport().setMaxY(-30);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(200);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScrollable(true);

        graph.addSeries(series);
        graph.addSeries(avg);
        graph.getLegendRenderer().setBackgroundColor(0x50212121);
        graph.getLegendRenderer().setTextColor(-1);
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);

        DevicePolicyManager mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mAdminName = new ComponentName(this, AdminReceiver.class);
        dialog = new AlertDialog.Builder(this).setTitle("Permission Required")
                .setMessage("Some permission(s) are required in order to run app properly. Show them?")
                .setNegativeButton("Decline", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    if (mDPM != null && !mDPM.isAdminActive(mAdminName)) {
                        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminName);
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description));
                        startActivityForResult(intent, REQUEST_ENABLE);
                    } else if (!hasPermissions(this, permissionList))
                        ActivityCompat.requestPermissions(this, permissionList, PERMISSION);
                    else if (!gpsTracker.isGPSEnabled) gpsTracker.showSettingsAlert();
                    else if (!settings.getBoolean("PREF_AUTO_MAIL", false))
                        startActivity(new Intent(this, GMailAuth.class));
                }).setCancelable(false).create();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE:
                if (resultCode == Activity.RESULT_OK && !hasPermissions(this, permissionList))
                    ActivityCompat.requestPermissions(this, permissionList, PERMISSION);
                break;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean b = true;
        for (int i : grantResults)
            if (!(b = i == PERMISSION_GRANTED))
                break;
        if (b) {
            if (!gpsTracker.isGPSEnabled) gpsTracker.showSettingsAlert();
        } else Toast.makeText(this, "Permission Required!", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }

    private void init() {
        wifiMonitor = new Intent(this, WifiMonitor.class);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getIntExtra("percentage", 0) == 0) {
                    mainBtn.setChecked(false);
                    toolbar.setTitle(R.string.app_name);

                    if (intent.getIntExtra("strength", -127) < -120) {
                        toolbar.setTitle("Tap to connect");
                        Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                toolbar.setTitle(intent.getStringExtra("name") + "(" +
                        intent.getIntExtra("percentage", 0) + "%)");
                if ((lastX++ & 1) == 0) {
                    int i = intent.getIntExtra("strength", 0);
                    long l = intent.getLongExtra("avg", 0);
                    avg.setTitle("avg: " + l + "dbm");
                    series.setTitle("cur: " + i + "dbm");
                    series.appendData(new DataPoint(lastX, i), true, 200);
                    avg.appendData(new DataPoint(lastX, l), true, 200);
                }
            }
        };
        registerReceiver(receiver, new IntentFilter(WifiMonitor.BROADCAST_ACTION));
        if (Utilities.isServiceRunning(this, WifiMonitor.class))
            mainBtn.setChecked(true);

        if (toShowDialog()) {
            if (!dialog.isShowing())
                dialog.show();
        }
    }

    private boolean toShowDialog() {
        DevicePolicyManager mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mAdminName = new ComponentName(this, AdminReceiver.class);
        return (mDPM != null && !mDPM.isAdminActive(mAdminName)) ||
                !hasPermissions(this, permissionList) ||
                !gpsTracker.isGPSEnabled || !settings.getBoolean(PREF_AUTO_MAIL, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (toStop && Utilities.isServiceRunning(this, WifiMonitor.class)) stopService(wifiMonitor);
    }

}
