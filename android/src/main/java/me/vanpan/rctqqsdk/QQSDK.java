package me.vanpan.rctqqsdk;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper;
import com.tencent.connect.common.Constants;
import com.tencent.connect.share.QQShare;
import com.tencent.connect.share.QzonePublish;
import com.tencent.connect.share.QzoneShare;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static android.content.ContentValues.TAG;

class ShareScene {
    public static final int QQ = 0;
    public static final int QQZone = 1;
}

public class QQSDK extends ReactContextBaseJavaModule {

    private static Tencent mTencent;
    private String appId;
    private String appName;
    private Promise mPromise;
    private static final String ACTIVITY_DOES_NOT_EXIST = "activity not found";
    private static final String QQ_Client_NOT_INSYALLED_ERROR = "QQ client is not installed";
    private static final String QQ_RESPONSE_ERROR = "QQ response is error";
    private static final String QQ_CANCEL_BY_USER = "cancelled by user";
    private static final String QZONE_SHARE_CANCEL = "QZone share is cancelled";


    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {

        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
            if (resultCode == Constants.ACTIVITY_OK) {
                if (requestCode == Constants.REQUEST_LOGIN) {
                    Tencent.onActivityResultData(requestCode, resultCode, intent, loginListener);
                }
                if (requestCode == Constants.REQUEST_QQ_SHARE) {
                    Tencent.onActivityResultData(requestCode, resultCode, intent, qqShareListener);
                }
            } else {
                if (requestCode == Constants.REQUEST_LOGIN) {
                    mPromise.reject("400", QQ_CANCEL_BY_USER);
                }
                if (requestCode == Constants.REQUEST_QQ_SHARE) {
                    mPromise.reject("400", QQ_CANCEL_BY_USER);
                }
            }
        }
    };

    public QQSDK(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(mActivityEventListener);
        appId = this.getAppID(reactContext);
        appName = this.getAppName(reactContext);
    }

    @Override
    public void initialize() {
        super.initialize();
    }

    @Override
    public String getName() {
        return "QQSDK";
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        if (mTencent != null) {
            mTencent = null;
        }
        appId = null;
        appName = null;
        mPromise = null;
    }

    @ReactMethod
    public void checkClientInstalled(Promise promise) {
        Tencent tencent = getTencent();
        Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Boolean installed = tencent.isSupportSSOLogin(currentActivity);
        if (installed) {
            promise.resolve(true);
        } else {
            promise.reject("404", QQ_Client_NOT_INSYALLED_ERROR);
        }
    }

    @ReactMethod
    public void logout(Promise promise) {
        Tencent tencent = getTencent();
        Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        tencent.logout(currentActivity);
        promise.resolve(true);
    }

    @ReactMethod
    public void ssoLogin(final Promise promise) {
        Tencent tencent = getTencent();
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Runnable runnable = () -> {
            mPromise = promise;
            tencent.login(currentActivity, "all", loginListener);
        };
        UiThreadUtil.runOnUiThread(runnable);
    }

    @ReactMethod
    public void shareText(String text, int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Tencent tencent = getTencent();
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                promise.reject("500", "Android 不支持分享文字到 QQ");
                break;
            case ShareScene.QQZone:
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzonePublish.PUBLISH_TO_QZONE_TYPE_PUBLISHMOOD);
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, text);
                Runnable zoneRunnable = new Runnable() {
                    @Override
                    public void run() {
                        tencent.publishToQzone(currentActivity, params, qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }

    }

    @ReactMethod
    public void shareImage(String image, String title, String description, int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        Log.d("图片地址", image);
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Tencent tencent = getTencent();
        mPromise = promise;
        image = processImage(image);
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE);
                params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL, image);
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                Runnable qqRunnable = () -> tencent.shareToQQ(currentActivity, params, qqShareListener);
                UiThreadUtil.runOnUiThread(qqRunnable);
                break;
            case ShareScene.QQZone:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE);
                params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL, image);
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                params.putInt(QQShare.SHARE_TO_QQ_EXT_INT, QQShare.SHARE_TO_QQ_FLAG_QZONE_AUTO_OPEN);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        tencent.shareToQQ(currentActivity, params, qqShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }
    }

    @ReactMethod
    public void shareNews(String url, String image, String title, String description, int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Tencent tencent = getTencent();
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
                if (URLUtil.isNetworkUrl(image)) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, image);
                } else {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL, processImage(image));
                }
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, url);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                Runnable qqRunnable = new Runnable() {

                    @Override
                    public void run() {
                        tencent.shareToQQ(currentActivity, params, qqShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(qqRunnable);
                break;
            case ShareScene.QQZone:
                image = processImage(image);
                ArrayList<String> imageUrls = new ArrayList<String>();
                imageUrls.add(image);
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzoneShare.SHARE_TO_QZONE_TYPE_IMAGE_TEXT);
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QzoneShare.SHARE_TO_QQ_SUMMARY, description);
                params.putString(QzoneShare.SHARE_TO_QQ_TARGET_URL, url);
                params.putStringArrayList(QzoneShare.SHARE_TO_QQ_IMAGE_URL, imageUrls);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        tencent.shareToQzone(currentActivity, params, qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }

    }

    @ReactMethod
    public void shareAudio(String url, String flashUrl, String image, String title, String description, int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Tencent tencent = getTencent();
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
                if (URLUtil.isNetworkUrl(image)) {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, image);
                } else {
                    params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL, processImage(image));
                }
                params.putString(QQShare.SHARE_TO_QQ_AUDIO_URL, flashUrl);
                params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QQShare.SHARE_TO_QQ_TARGET_URL, url);
                params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);
                Runnable qqRunnable = new Runnable() {

                    @Override
                    public void run() {
                        tencent.shareToQQ(currentActivity, params, qqShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(qqRunnable);
                break;
            case ShareScene.QQZone:
                image = processImage(image);
                ArrayList<String> imageUrls = new ArrayList<String>();
                imageUrls.add(image);
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzoneShare.SHARE_TO_QZONE_TYPE_IMAGE_TEXT);
                params.putString(QzoneShare.SHARE_TO_QQ_TITLE, title);
                params.putString(QzoneShare.SHARE_TO_QQ_SUMMARY, description);
                params.putString(QzoneShare.SHARE_TO_QQ_TARGET_URL, url);
                params.putString(QzoneShare.SHARE_TO_QQ_AUDIO_URL, flashUrl);
                params.putStringArrayList(QzoneShare.SHARE_TO_QQ_IMAGE_URL, imageUrls);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        tencent.shareToQzone(currentActivity, params, qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }
    }

    @ReactMethod
    public void shareVideo(String url, String flashUrl, String image, String title, String description, int shareScene, final Promise promise) {
        final Activity currentActivity = getCurrentActivity();
        if (null == currentActivity) {
            promise.reject("405", ACTIVITY_DOES_NOT_EXIST);
            return;
        }
        Tencent tencent = getTencent();
        mPromise = promise;
        final Bundle params = new Bundle();
        switch (shareScene) {
            case ShareScene.QQ:
                promise.reject("500", "Android 不支持分享视频到 QQ");
                break;
            case ShareScene.QQZone:
                ArrayList<String> imageUrls = new ArrayList<String>();
                imageUrls.add(image);
                params.putInt(QzoneShare.SHARE_TO_QZONE_KEY_TYPE, QzonePublish.PUBLISH_TO_QZONE_TYPE_PUBLISHVIDEO);
                params.putString(QzonePublish.PUBLISH_TO_QZONE_IMAGE_URL, image);
                params.putString(QzonePublish.PUBLISH_TO_QZONE_SUMMARY, description);
                params.putString(QzonePublish.PUBLISH_TO_QZONE_VIDEO_PATH, flashUrl);
                Runnable zoneRunnable = new Runnable() {

                    @Override
                    public void run() {
                        tencent.shareToQzone(currentActivity, params, qZoneShareListener);
                    }
                };
                UiThreadUtil.runOnUiThread(zoneRunnable);
                break;
            default:
                break;

        }
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("QQ", ShareScene.QQ);
        constants.put("QQZone", ShareScene.QQZone);
        return constants;
    }

    /**
     * 获取Tencent SDK App ID
     *
     * @param reactContext
     * @return
     */
    private String getAppID(ReactApplicationContext reactContext) {
        try {
            ApplicationInfo appInfo = reactContext.getPackageManager()
                    .getApplicationInfo(reactContext.getPackageName(),
                            PackageManager.GET_META_DATA);
            String key = appInfo.metaData.get("QQ_APP_ID").toString();
            return key;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取应用的名称
     *
     * @param reactContext
     * @return
     */
    private String getAppName(ReactApplicationContext reactContext) {
        PackageManager packageManager = reactContext.getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(reactContext.getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
        }
        final String AppName = (String) ((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : "AppName");
        return AppName;
    }

    /**
     * 图片处理
     *
     * @param image
     * @return
     */
    private String processImage(String image) {
        if (TextUtils.isEmpty(image)) {
            return "";
        }
        if (URLUtil.isHttpUrl(image) || URLUtil.isHttpsUrl(image)) {
            return saveBytesToFile(getBytesFromURL(image), getExtension(image));
        } else if (isBase64(image)) {
            return saveBitmapToFile(decodeBase64ToBitmap(image));
        } else if (URLUtil.isFileUrl(image) || image.startsWith("/")) {
            File file = new File(image);
            return file.getAbsolutePath();
        } else if (URLUtil.isContentUrl(image)) {
            return saveBitmapToFile(getBitmapFromUri(Uri.parse(image)));
        } else {
            return saveBitmapToFile(BitmapFactory.decodeResource(getReactApplicationContext().getResources(), getDrawableFileID(image)));
        }
    }

    /**
     * 检查图片字符串是不是Base64
     *
     * @param image
     * @return
     */
    private boolean isBase64(String image) {
        try {
            byte[] decodedString = Base64.decode(image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (bitmap == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取Drawble资源的文件ID
     *
     * @param imageName
     * @return
     */
    private int getDrawableFileID(String imageName) {
        ResourceDrawableIdHelper sResourceDrawableIdHelper = ResourceDrawableIdHelper.getInstance();
        int id = sResourceDrawableIdHelper.getResourceDrawableId(getReactApplicationContext(), imageName);
        return id;
    }

    /**
     * 根据图片的URL转化层Bitmap
     *
     * @param src
     * @return
     */
    private static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            return bitmap;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 根据图片的URL转化成 byte[]
     *
     * @param src
     * @return
     */
    private static byte[] getBytesFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            byte[] b = getBytes(input);
            return b;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取链接指向文件后缀
     *
     * @param src
     * @return
     */
    public static String getExtension(String src) {
        String extension = null;
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            String contentType = connection.getContentType();
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return extension;
    }

    /**
     * 将Base64解码成Bitmap
     *
     * @param Base64String
     * @return
     */
    private Bitmap decodeBase64ToBitmap(String Base64String) {
        byte[] decode = Base64.decode(Base64String, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decode, 0, decode.length);
        return bitmap;
    }

    /**
     * 根据uri生成Bitmap
     *
     * @param uri
     * @return
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            InputStream inStream = this.getCurrentActivity().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inStream);
            return bitmap;
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        }
        return null;
    }

    /**
     * 将bitmap 保存成文件
     *
     * @param bitmap
     * @return
     */
    private String saveBitmapToFile(Bitmap bitmap) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            return null;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        return pictureFile.getAbsolutePath();
    }

    /**
     * 将 byte[] 保存成文件
     *
     * @param bytes 图片内容
     * @return
     */
    private String saveBytesToFile(byte[] bytes) {
        return saveBytesToFile(bytes, "jpg");
    }

    /**
     * 将 byte[] 保存成文件
     *
     * @param bytes 图片内容
     * @param ext   扩展名
     * @return
     */
    private String saveBytesToFile(byte[] bytes, String ext) {
        File pictureFile = getOutputMediaFile(ext);
        if (pictureFile == null) {
            return null;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(bytes);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        return pictureFile.getAbsolutePath();
    }

    /**
     * 生成文件用来存储图片
     *
     * @return
     */
    private File getOutputMediaFile() {
        return getOutputMediaFile("jpg");
    }

    private File getOutputMediaFile(String ext) {
        ext = ext != null ? ext : "jpg";
        File mediaStorageDir = getCurrentActivity().getExternalCacheDir();
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmm").format(new Date());
        File mediaFile;
        String mImageName = "RN_" + timeStamp + "." + ext;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        Log.d("path is", mediaFile.getPath());
        return mediaFile;
    }


    /**
     * 保存token 和 openid
     *
     * @param jsonObject
     */
    public boolean initOpenidAndToken(JSONObject jsonObject) {
        try {
            String token = jsonObject.getString(Constants.PARAM_ACCESS_TOKEN);
            String expires = jsonObject.getString(Constants.PARAM_EXPIRES_IN);
            String openId = jsonObject.getString(Constants.PARAM_OPEN_ID);
            if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(expires)
                    && !TextUtils.isEmpty(openId)) {
                Tencent tencent = getTencent();
                tencent.setAccessToken(token, expires);
                tencent.setOpenId(openId);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 登录监听
     */
    IUiListener loginListener = new IUiListener() {
        @Override
        public void onComplete(Object response) {
            if (null == response) {
                mPromise.reject("600", QQ_RESPONSE_ERROR);
                return;
            }
            JSONObject jsonResponse = (JSONObject) response;
            if (jsonResponse.length() == 0) {
                mPromise.reject("600", QQ_RESPONSE_ERROR);
                return;
            }
            if (initOpenidAndToken(jsonResponse)) {
                Tencent tencent = getTencent();
                WritableMap map = Arguments.createMap();
                map.putString("userid", tencent.getOpenId());
                map.putString("access_token", tencent.getAccessToken());
                map.putDouble("expires_time", tencent.getExpiresIn());
                mPromise.resolve(map);
            } else {
                mPromise.reject("600", QQ_RESPONSE_ERROR);
            }
        }

        @Override
        public void onWarning(int code) {
            if (code == Constants.ERROR_NO_AUTHORITY) {
                Log.d(TAG, "onWarning: 请授权手Q访问分享的文件的读取权限!");
            }
        }

        @Override
        public void onError(UiError e) {
            mPromise.reject("600", e.errorMessage);
        }

        @Override
        public void onCancel() {
            mPromise.reject("603", QQ_CANCEL_BY_USER);
        }
    };

    /**
     * QQ分享监听
     */
    IUiListener qqShareListener = new IUiListener() {
        @Override
        public void onCancel() {
            mPromise.reject("503", QQ_CANCEL_BY_USER);
        }

        @Override
        public void onWarning(int code) {
            if (code == Constants.ERROR_NO_AUTHORITY) {
                Log.d(TAG, "onWarning: 请授权手Q访问分享的文件的读取权限!");
            }
        }

        @Override
        public void onComplete(Object response) {
            mPromise.resolve(true);
        }

        @Override
        public void onError(UiError e) {
            mPromise.reject("500", e.errorMessage);
        }

    };
    /**
     * QQZONE 分享监听
     */
    IUiListener qZoneShareListener = new IUiListener() {

        @Override
        public void onCancel() {
            mPromise.reject("503", QZONE_SHARE_CANCEL);
        }

        @Override
        public void onWarning(int code) {
            if (code == Constants.ERROR_NO_AUTHORITY) {
                Log.d(TAG, "onWarning: 请授权手Q访问分享的文件的读取权限!");
            }
        }

        @Override
        public void onError(UiError e) {
            mPromise.reject("500", e.errorMessage);
        }

        @Override
        public void onComplete(Object response) {
            mPromise.resolve(true);
        }

    };

    private static byte[] getBytes(InputStream inputStream) throws Exception {
        byte[] b = new byte[1024];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int len = -1;
        while ((len = inputStream.read(b)) != -1) {
            byteArrayOutputStream.write(b, 0, len);
        }
        byteArrayOutputStream.close();
        inputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    private Tencent getTencent() {
        if (null == mTencent) {
            Tencent.setIsPermissionGranted(true);
            mTencent = Tencent.createInstance(appId, getReactApplicationContext());
        }
        return mTencent;
    }
}
