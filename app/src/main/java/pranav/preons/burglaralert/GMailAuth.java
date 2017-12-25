package pranav.preons.burglaralert;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

import java.util.Arrays;
import java.util.List;

import pranav.utilities.GMailHandler;
import pranav.utilities.Log;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static pranav.utilities.GMailHandler.REQUEST_AUTHORIZATION;

public class GMailAuth extends Activity implements EasyPermissions.PermissionCallbacks {


    static final String PREF_ACCOUNT_NAME = "accountName";
    static final String[] SCOPES = {GmailScopes.GMAIL_SEND, GmailScopes.GMAIL_COMPOSE, GmailScopes.MAIL_GOOGLE_COM};

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final String PREF_AUTO_MAIL = "auto_mail";

    private static int C = 0;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private GoogleAccountCredential mCredential;
    private Log log = new Log("GMailAuth", true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        editor = sharedPreferences.edit();
        editor.apply();

        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES)).setBackOff(new ExponentialBackOff());
        initializeGMailApi();
    }

    private void initializeGMailApi() {
        if (!isGooglePlayServicesAvailable()) acquireGooglePlayServices();
        else if (mCredential.getSelectedAccountName() == null) chooseAccount();
        else if (!isDeviceOnline())
            Toast.makeText(this, R.string.no_internet, Toast.LENGTH_SHORT).show();
        else {
            Toast.makeText(this, "Sending test email... ", Toast.LENGTH_SHORT).show();
            GMailHandler handler = new GMailHandler(mCredential, "Burglar Alert", this);
            handler.sendMail(new GMailHandler.EmailData("preon.inc@gmail.com",
                    sharedPreferences.getString(PREF_ACCOUNT_NAME, "pranavraut033@gmail.com"), "test", "test", null));
            handler.setPostSendListener(new GMailHandler.PostSendListener() {
                @Override
                public void onSent(Message message) {
                    editor.putBoolean(PREF_AUTO_MAIL, true);
                    editor.apply();
                    finish();
                    C = 0;
                }

                @Override
                public void onFailed(Exception e) {
                    Toast.makeText(GMailAuth.this, "Sending Failed: trying " + C++ + " times", Toast.LENGTH_LONG).show();
                    recreate();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    new AlertDialog.Builder(this)
                            .setTitle("Google Play Services required")
                            .setPositiveButton("Play Store", (dialog, which) -> {
                                try {
                                    startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("market://details?id=com.google.android.gms")));
                                } catch (ActivityNotFoundException ignored) {
                                    startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")));
                                }
                            })
                            .setNeutralButton("Apk Mirror", (dialog, which) ->
                                    startActivity(new Intent("android.intent.action.VIEW",
                                            Uri.parse("https://www.apkmirror.com/apk/google-inc/google-play-services/"))))
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                Toast.makeText(this, "Automated Mail is disabled", Toast.LENGTH_SHORT).show();
                                editor.putBoolean(PREF_AUTO_MAIL, false);
                                editor.apply();
                            }).setMessage(R.string.require_google_play_service).show();
                } else {
                    initializeGMailApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    log.d("onActivityResult: " + accountName);
                    if (accountName != null) {
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        initializeGMailApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                editor.putBoolean(PREF_AUTO_MAIL, resultCode == RESULT_OK);
                editor.apply();
                finish();
                break;
        }
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = sharedPreferences.getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                editor.putString(PREF_ACCOUNT_NAME, accountName);
                editor.apply();
                initializeGMailApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS, Manifest.permission.GET_ACCOUNTS);
        }
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (connMgr != null) networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.
                isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode))
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
    }

    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(this,
                connectionStatusCode, REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }

}
