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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ReceiverCallNotAllowedException;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pranav.utilities.GMailHandler;
import pranav.utilities.Log;
import pranav.utilities.Utilities;

import static pranav.preons.burglaralert.GMailAuth.PREF_ACCOUNT_NAME;
import static pranav.preons.burglaralert.GMailAuth.SCOPES;
import static pranav.preons.burglaralert.PlayerService.msgBuilder;
import static pranav.utilities.Utilities.getBackup;
import static pranav.utilities.Utilities.hasPermissions;
import static pranav.utilities.Utilities.storeImage;

/**
 * Created on 25-12-2017 at 10:27 by Pranav Raut.
 * For BurglarAlert
 */
@SuppressWarnings("WeakerAccess")
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
public class HiddenCameraThread extends Thread {

    Context context;
    Log log = new Log("HiddenCameraThread", true);
    @Nullable
    PictureTakenListener pictureTakenListener;
    private Camera camera;
    private int cameraId;

    public HiddenCameraThread(Context context) {
        super();
        this.context = context;
    }

    @Override
    public void run() {

        if (!hasPermissions(context, Manifest.permission.CAMERA)) {
            log.d("run: " + "no camera permission");
            //(context, "Don't have Camera Permission", Toast.LENGTH_SHORT).show();
            return;
        }
        int numberOfCameras = Camera.getNumberOfCameras();
        SurfaceTexture surfaceTexture = new SurfaceTexture(0);
        if (numberOfCameras == 0)
            log.d("run: Camera Not Available");
        for (int i = 0, j = 0; i < numberOfCameras; i++, j = 0) {
            cameraId = i;
            if (camera != null) {
                camera.release();
                camera = null;
            }

            while (j++ < 3 && camera == null)
                try {
                    camera = Camera.open(i);
                    if (camera == null)
                        SystemClock.sleep(1000);
                } catch (Exception e) {
                    log.e("run: camera#open(" + i + ") failed\n", e);
                }
            if (camera != null) {
                Camera.Parameters parameters = camera.getParameters();
                List list = parameters.getSupportedFlashModes();
                if (list != null && list.contains("off")) parameters.setFlashMode("off");
                list = parameters.getSupportedFocusModes();
                if (list != null && list.contains("continuous-picture"))
                    parameters.setFocusMode("continuous-picture");
                parameters.setJpegQuality(80);
                Camera.Size a = getSize(parameters);
                if (a != null) parameters.setPreviewSize(a.width, a.height);
                camera.setParameters(parameters);
                camera.enableShutterSound(false);
                try {
                    this.camera.setPreviewTexture(surfaceTexture);
                    camera.startPreview();
                    SystemClock.sleep(500);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                camera.takePicture(null, null, new cameraCallBack());
                SystemClock.sleep(1000);
            }
        }
    }

    private Camera.Size getSize(Camera.Parameters parameters) {
        for (Camera.Size size : parameters.getSupportedPictureSizes()) {
            if (size.width >= 480 && size.width <= 1024 && size.height >= 480 && size.height <= 1024) {
                return size;
            }
        }
        return null;
    }

    public void setPictureTakenListener(@Nullable PictureTakenListener pictureTakenListener) {
        this.pictureTakenListener = pictureTakenListener;
    }

    interface PictureTakenListener {
        void onPictureTaken(File file, String path);
    }

    private class cameraCallBack implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] bytes, Camera camera) {

            File pictureFileDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Burglars");
            if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) return;
            String date = new SimpleDateFormat("yyyyMMdd_hh:mm:ss", Locale.ENGLISH).format(new Date());
            String photoFile = "Burglar_" + date + "_" + cameraId + ".jpg";
            String filename = pictureFileDir.getPath() + File.separator + photoFile;
            File mainPicture = new File(filename);
            try {
                FileOutputStream fo = new FileOutputStream(mainPicture);
                fo.write(bytes);
                fo.close();
                System.out.println("image saved");
            } catch (FileNotFoundException e) {
                log.e("FileNotFoundException", e);
            } catch (IOException e) {
                log.e("fo.write::PictureTaken", e);
            }
            try {
                Utilities.Resources resources = new Utilities.Resources(context);
                Bitmap bmp = BitmapFactory.decodeFile(mainPicture.getAbsolutePath());
                Point point = new Point();
                point.x = resources.getDeviceWidth();
                point.y = point.x * 9 / 16;
                bmp = Bitmap.createScaledBitmap(bmp, point.x, point.y, false);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "Default_" + cameraId)
                        .setContentTitle("Caught using camera " + cameraId)
                        .setColor(resources.getColor(R.color.colorAccent))
                        .setPriority(2)
                        .setAutoCancel(true)
                        .setLights(Color.RED, 200, 100)
                        .setSmallIcon(R.mipmap.ic_launcher_round);
                GoogleAccountCredential mCredential = GoogleAccountCredential.usingOAuth2(
                        context.getApplicationContext(), Arrays.asList(SCOPES)).setBackOff(new ExponentialBackOff());

                if (Build.VERSION.SDK_INT < 19) {
                    builder.setContentIntent(PendingIntent.getActivity(context, 0,
                            new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(mainPicture.getAbsolutePath()),
                                    "image/*"), 0))
                            .setContentText("Stored in this location: " + mainPicture.getAbsolutePath())
                            .setStyle(new NotificationCompat.BigPictureStyle()
                                    .bigPicture(bmp).setSummaryText("Stored in this location: " + mainPicture.getAbsolutePath()));
                    context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED,
                            Uri.parse("file://" + Environment.getExternalStorageDirectory())));
                    Utilities.NotificationHandler.notify(context, builder, null, true, getBackup());
                } else {
                    Bitmap finalBmp = bmp;
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                    String accountName = sharedPreferences.getString(PREF_ACCOUNT_NAME, null);
                    if (sharedPreferences.getBoolean("auto_mail", false) && accountName != null) {
                        String s = sharedPreferences.getString("back_up_mail", null);
                        String s1 = storeImage(bmp);
                        if (s1 == null)
                            log.d("onPictureTaken: error creating temp file sending main picture");
                        GMailHandler handler = new GMailHandler(mCredential, "Burglar Alert", context);
                        mCredential.setSelectedAccountName(accountName);
                        handler.sendMail(new GMailHandler.EmailData(s, accountName, "Burglar Captured",
                                "The picture was taken from your phone. Check if you know the person.\n" + msgBuilder(context) +
                                        "\n\t Send from Burglar Alert (https://play.google.com/store/apps/details?id=pranav.preons.burglaralert)",
                                s1 == null ? mainPicture : new File(s1)));
                    }
                    try {
                        MediaScannerConnection.scanFile(context, new String[]{mainPicture.toString()},
                                null, (path, uri) -> {
                                    log.d("ExternalStorage" + "Scanned " + path);
                                    builder.setContentIntent(PendingIntent.getActivity(context, 0,
                                            new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*"), 0))
                                            .setContentText("Stored in this location: " + path)
                                            .setStyle(new NotificationCompat.BigPictureStyle()
                                                    .bigPicture(finalBmp).setSummaryText("Stored in this location: " + path));
                                    Utilities.NotificationHandler.notify(context, builder, null, true, getBackup());
                                });
                    } catch (ReceiverCallNotAllowedException ignored) {
                    }
                }
            } catch (Exception e) {
                log.e("Picture Take Failed: ", e);
            }
        }
    }
}
