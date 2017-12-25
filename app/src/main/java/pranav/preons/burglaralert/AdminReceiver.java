package pranav.preons.burglaralert;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import static pranav.utilities.Log.TAG;

/**
 * Created on 22-12-2017 at 13:43 by Pranav Raut.
 * For BurglarAlert
 */

public class AdminReceiver extends DeviceAdminReceiver {

    static String PREF_PASSWORD_QUALITY = "password_quality";
    static String PREF_PASSWORD_LENGTH = "password_length";
    static String PREF_MAX_FAILED_PW = "max_failed_pw";
    Handler handler = new Handler();
    private Context context;
    Runnable runCamera = () -> {
        synchronized (HiddenCameraThread.class) {
            new HiddenCameraThread(context).start();
        }
    };

    static SharedPreferences getSamplePreferences(Context context) {
        return context.getSharedPreferences(DeviceAdminReceiver.class.getName(), 0);
    }

    void showToast(Context context, CharSequence msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        this.context = context;
        showToast(context, "Burglar Alert: Device Admin: enabled");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        this.context = context;
        return "The Device information can be obtained without this permission";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        this.context = context;
        showToast(context, "Burglar Alert: Device Admin: disabled");
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent, UserHandle user) {
        super.onPasswordSucceeded(context, intent, user);
        handler.removeCallbacks(runCamera);
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        this.context = context;
        Log.d(TAG, "onPasswordFailed: " + intent);
        //todo
        handler.postDelayed(runCamera, 1000);
        showToast(context, "Burglar Alert: password failed");
    }
}