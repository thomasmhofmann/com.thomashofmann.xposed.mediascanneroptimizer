package com.thomashofmann.xposed.mediascanneroptimizer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.text.format.DateUtils;

import com.thomashofmann.xposed.lib.Logger;
import com.thomashofmann.xposed.lib.UnexpectedException;
import com.thomashofmann.xposed.lib.XposedModule;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class MsoXposedMod extends XposedModule {
    private static final String[] TARGET_PACKAGE_NAMES = new String[]{"com.android.providers.media"};
    private static final String PREF_CHANGE_ACTION = "pref-xmso";
    private final static String TAG = "xmso";
    private final static int FOREGROUND_NOTIFICATION = 1;
    private final static int SCAN_FINISHED_NOTIFICATION = 10;

    private Map<Integer, Integer> startIdsByPid = new HashMap<Integer, Integer>();
    private Map<String, Intent> intentsByVolume = new HashMap<String, Intent>();
    private Map<Integer, String> volumesByStartId = new HashMap<Integer, String>();

    private int numberOfStartCommandsForExternalVolume;
    private int numberOfSuccessfulRunsForExternalVolume;

    private long prescanStartTime;
    private long prescanEndTime;
    private long scanTime;
    private long postscanStartTime;
    private long postscanEndTime;
    private long totalStartTime;
    private long totalEndTime;

    private int scanFinishedCounter = 0;

    private static Handler handler;

    private enum MediaFileTypeEnum {
        music, video, picture
    }

    private enum TreatAsMediaFileEnum {
        media_file_true, media_file_false, media_file_default
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.i("Intent {0} received.", intent);
            if (intent.getAction().equals(PreferencesFragment.ACTION_SCAN_EXTERNAL)) {
                Logger.i("Request to rescan external volume.");
                if (intentsByVolume.containsKey("external")) {
                    displayToastUsingHandler(context, "A scan for the external volume is already in progress. Please try again later.");
                } else {
                    Bundle serviceExtras = new Bundle();
                    serviceExtras.putString("volume", "external");
                    serviceExtras.putBoolean("userInitiatedScan", true);
                    Intent serviceIntent = new Intent();
                    ComponentName componentName = new ComponentName("com.android.providers.media", "com.android.providers.media.MediaScannerService");
                    serviceIntent.setComponent(componentName);
                    serviceIntent.putExtras(serviceExtras);
                    getApplicationContext().startService(serviceIntent);
                }
            } else if (intent.getAction().equals(PreferencesFragment.ACTION_DELETE_MEDIA_STORE_CONTENTS)) {
                Logger.i("Request to delete MediaStore content");
                if (intentsByVolume.containsKey("external")) {
                    displayToastUsingHandler(context, "Could not delete media store contents for external volume because a scan is in progress. Please try again later.");
                } else {
                    try {
                        deleteMediaStoreContent(context);
                        Logger.i("Deleted media store content.");
                    } catch (Exception e) {
                        UnexpectedException unexpectedException = new UnexpectedException("Failed to delete media store contents", e);
                        createNotification("Problem", unexpectedException);
                    }
                }
            }
        }
    };

    public MsoXposedMod() {
        Logger.init("XMSO", getSettings());
    }

    private static Map<String, List<MediaFileTypeEnum>> mediaFilesToConsiderByDirectory = new HashMap<String, List<MediaFileTypeEnum>>();

    @Override
    protected List<String> getTargetPackageNames() {
        return Arrays.asList(TARGET_PACKAGE_NAMES);
    }

    @Override
    protected String getModuleDisplayName() {
        return "Xposed Media Scanner Optimizer";
    }

    @Override
    protected String getModulePackageName() {
        return MsoXposedMod.class.getPackage().getName();
    }

    @Override
    protected String getPreferencesChangedAction() {
        return PREF_CHANGE_ACTION;
    }

    @Override
    public int getNotificationIconResourceId() {
        return R.drawable.ic_notification;
    }

    @Override
    protected void applicationContextAvailable(Context context) {
        IntentFilter filter = new IntentFilter(PreferencesFragment.ACTION_SCAN_EXTERNAL);
        filter.addAction(PreferencesFragment.ACTION_DELETE_MEDIA_STORE_CONTENTS);
        context.registerReceiver(broadcastReceiver, filter, "com.thomashofmann.xposed.mediascanneroptimizer.permission.MEDIA_STORE_OPERATIONS", null);
    }

    @Override
    protected void applicationTerminating(int pid) {
        getApplicationContext().unregisterReceiver(broadcastReceiver);
        super.applicationTerminating(pid);
    }

    @Override
    public void doHandleLoadPackage() {
        XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "isNoMediaFile",
                String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        String pathToFile = (String) methodHookParam.args[0];
                        if (pathToFile.startsWith("/system")) {
                            return;
                        }

                        if (!getSettings().getPreferences().getBoolean("pref_restrict_to_certain_media_types_state", false)) {
                            Logger.d("Not restricting scan to certain media types");
                            return;
                        }

                        File file = new File(pathToFile);
                        if (!file.isDirectory()) {
                            TreatAsMediaFileEnum consideredAsMediaFile = shouldBeConsideredAsMediaFile(file);
                            if (consideredAsMediaFile != TreatAsMediaFileEnum.media_file_default) {
                                switch (consideredAsMediaFile) {
                                    case media_file_true:
                                        methodHookParam.setResult(false);
                                        break;
                                    case media_file_false:
                                        Logger.d("The file {0} should not be considered as a media file",
                                                file.getAbsolutePath());
                                        methodHookParam.setResult(true);
                                        break;
                                }
                            }
                        }
                    }

                });

        XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "scanMtpFile",
                String.class, String.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        String pathToFile = (String) methodHookParam.args[0];
                        Logger.i("scanMtpFile is called for {0}", pathToFile);
                    }
                });

        XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "scanSingleFile",
                String.class, String.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        String pathToFile = (String) methodHookParam.args[0];
                        Logger.i("scanSingleFile is called for {0}", pathToFile);
                    }
                });

        XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerService",
                getLoadPackageParam().classLoader, "onStartCommand", Intent.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        handler = new Handler();
                        Intent intent = (Intent) methodHookParam.args[0];
                        Bundle extras = intent.getExtras();
                        String filePathInIntentExtra = extras != null ? extras.getString("filepath") : null;
                        String mimeTypeInIntentExtra = extras != null ? extras.getString("mimetype") : null;
                        String volumeInIntentExtra = extras != null ? extras.getString("volume") : null;
                        Object thisObject = methodHookParam.thisObject;
                        int startId = (Integer) methodHookParam.args[2];
                        Logger.i("onStartCommand is called with Intent {0}, startId {1}, filepath {2}, mimetype {3} and volume {4} on {5}",
                                intent, startId, filePathInIntentExtra, mimeTypeInIntentExtra, volumeInIntentExtra,
                                thisObject);

                        startIdsByPid.put(Process.myPid(), startId);
                        Logger.d("startIdsByPid is {0}", startIdsByPid);

                        getSettings().reload();

                        if (volumeInIntentExtra != null) {
                            if ("external".equals(volumeInIntentExtra)) {
                                numberOfStartCommandsForExternalVolume++;
                            }

                            if (intentsByVolume.containsKey(volumeInIntentExtra)) {
                                if (getSettings().getPreferences().getBoolean("pref_prevent_repetetive_scans_state", true)) {
                                    /*
                                     * don't queue another run for the volume
									 * that will already be scanned
									 */
                                    Logger.i("Early return from onStartCommand because scan for volumen {0} is already scheduled",
                                            volumeInIntentExtra);
                                }
                                methodHookParam.setResult(Service.START_NOT_STICKY);
                            } else {
                                intentsByVolume.put(volumeInIntentExtra, intent);
                                volumesByStartId.put(startId, volumeInIntentExtra);
                            }
                        }
                        Logger.i("Number of start commands for external volume is {0}",
                                numberOfStartCommandsForExternalVolume);
                        Logger.i("Number of successful runs for external volume is {0}",
                                numberOfSuccessfulRunsForExternalVolume);
                    }
                });

        XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerService",
                getLoadPackageParam().classLoader, "scan", String[].class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        String[] directories = (String[]) methodHookParam.args[0];
                        String volumeName = (String) methodHookParam.args[1];
                        Object thisObject = methodHookParam.thisObject;
                        Service service = (Service) thisObject;
                        Logger.i("scan is called for directories {0} on volume {1}", Arrays.toString(directories),
                                volumeName);

                        if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
                            Logger.i("Set service to foreground");
                            Notification notification = getNotification(service, "MediaScanner",
                                    "Processing volume " + volumeName).build();
                            service.startForeground(FOREGROUND_NOTIFICATION, notification);
                        }

                        if (volumeName.equals("external")
                                && getSettings().getPreferences().getBoolean("pref_restrict_directories_to_scan_state", false)) {
                            List<String> directoriesToScan = new ArrayList<String>();
                            for (String originalDirectoryString : directories) {
                                File originalDirectory = new File(originalDirectoryString);
                                if (originalDirectory.exists() && originalDirectory.isDirectory()) {
                                    long start = System.currentTimeMillis();
                                    Logger.i("Searching directories to scan in {0}", originalDirectoryString);
                                    getDirectoriesMarkedForScanning(directoriesToScan, originalDirectory);
                                    long s = System.currentTimeMillis() - start;
                                    String duration = formatDuration(s);
                                    Logger.i("Finished searching directories to scan in {0}. Time: {1}",
                                            originalDirectoryString, duration);
                                }
                            }
                            Logger.i("The following directories will be scanned by MediaScanner: {0}",
                                    directoriesToScan);
                            methodHookParam.args[0] = directoriesToScan.toArray(new String[directoriesToScan.size()]);
                        } else {
                            Logger.i("The following directories will be scanned by MediaScanner: {0}",
                                    Arrays.toString(directories));
                        }
                    }

                    protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        String volumeName = (String) methodHookParam.args[1];
                        Object thisObject = methodHookParam.thisObject;
                        Service service = (Service) thisObject;
                        Logger.i("scan(String[] directories, String volumeName) has finished scanning {0}",
                                volumeName);
                        service.stopForeground(true);
                        if (getSettings().getPreferences().getBoolean("pref_show_results_state", true)) {
                            reportDurations(service, volumeName);
                        }
                    }

                    private void reportDurations(Context context, String volumeName) {
                        long prescanTime = prescanEndTime - prescanStartTime;
                        long postscanTime = postscanEndTime - postscanStartTime;

                        String prescanDuration = formatDuration(prescanTime);
                        String scanDuration = formatDuration(scanTime);
                        String postscanDuration = formatDuration(postscanTime);

                        Notification.InboxStyle inboxStyle = new Notification.InboxStyle();
                        inboxStyle.setBigContentTitle("MediaScanner details:");
                        inboxStyle.addLine("Pre-scan: " + prescanDuration);
                        inboxStyle.addLine("Scan: " + scanDuration);
                        inboxStyle.addLine("Post-scan: " + postscanDuration);

                        String scanDirectoriesDuration = formatDuration(totalEndTime - totalStartTime);
                        Notification.Builder notificationBuilder = getNotification(context, "MediaScanner "
                                + volumeName, "Scan directories time " + scanDirectoriesDuration);
                        notificationBuilder.setStyle(inboxStyle);
                        getNotificationManager(context).notify(SCAN_FINISHED_NOTIFICATION + scanFinishedCounter++,
                                notificationBuilder.build());
                    }

                });

        XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerService",
                getLoadPackageParam().classLoader, "scanFile", String.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        String path = (String) methodHookParam.args[0];
                        String mimetype = (String) methodHookParam.args[1];

                        Logger.i("scanFile is called for file {0} and mime type {1}", path, mimetype);
                    }

                });

        XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerReceiver",
                getLoadPackageParam().classLoader, "onReceive", Context.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        Logger.i("onReceive is called in MediaScannerReceiver");
                        Intent intent = (Intent) methodHookParam.args[1];
                        Logger.i("Intent {0} received.", intent);
                        Bundle extras = intent.getExtras();
                        Logger.i("Extras are {0}", extras);
                    }
                });

        XposedHelpers.findAndHookMethod("android.app.Service", getLoadPackageParam().classLoader, "stopSelf", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        Object thisObject = methodHookParam.thisObject;
                        if (thisObject.getClass().getCanonicalName()
                                .equals("com.android.providers.media.MediaScannerService")) {
                            Logger.i("stopSelf is called on MediaScannerService");

                            Integer startId = (Integer) methodHookParam.args[0];
                            String volumeName = volumesByStartId.get(startId);
                            if (volumeName != null) {
                                intentsByVolume.remove(volumeName);
                                if (volumeName.equals("external")) {
                                    numberOfSuccessfulRunsForExternalVolume++;
                                }
                            }
                            volumesByStartId.remove(startId);
                        }
                    }
                });

        XposedHelpers.findAndHookConstructor("android.media.MediaScanner", getLoadPackageParam().classLoader, Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_with_background_thread_priority_state", true)) {
                            Logger.i("Changing thread priority in MediaScanner constructor");
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            // + Process.THREAD_PRIORITY_MORE_FAVORABLE);
                        }
                    }
                });

        XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "scanDirectories",
                String[].class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        totalStartTime = System.currentTimeMillis();
                        scanTime = 0;
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        totalEndTime = System.currentTimeMillis();
                    }
                });

        XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "prescan",
                String.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        Logger.d("In android.media.MediaScanner#processDirectory prescan");
                        prescanStartTime = System.currentTimeMillis();
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        prescanEndTime = System.currentTimeMillis();
                    }
                });

        Class mediaScannerClientClass = null;
        try {
            mediaScannerClientClass = getLoadPackageParam().classLoader.loadClass("android.media.MediaScannerClient");
            XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "processDirectory",
                    String.class, mediaScannerClientClass, new XC_MethodHook() {
                        private long start;
                        private long end;

                        @Override
                        protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            Logger.d("In android.media.MediaScanner#processDirectory beforeHookedMethod");
                            if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
                                String path = (String) methodHookParam.args[0];
                                Object field = getField(methodHookParam.thisObject, "mContext");
                                if (field instanceof Context) {
                                    Logger.i("Updating notification with path {0}", path);
                                    Context context = (Context) field;
                                    Notification notification = getNotification(context, "MediaScanner", "Scanning " + path)
                                            .build();
                                    getNotificationManager(context).notify(FOREGROUND_NOTIFICATION, notification);
                                } else {
                                    Logger.i("Could not retrieve context to update notification with path {0}", path);
                                }
                            }
                            start = System.currentTimeMillis();
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            end = System.currentTimeMillis();
                            scanTime = scanTime + (end - start);
                        }
                    });

            XposedHelpers.findAndHookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "postscan",
                    String[].class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            Logger.d("In android.media.MediaScanner#processDirectory prescan");
                            if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
                                Object field = getField(methodHookParam.thisObject, "mContext");
                                if (field instanceof Context) {
                                    Logger.i("Updating notification with postscan");
                                    Context context = (Context) field;
                                    Notification notification = getNotification(context, "MediaScanner", "Postscan")
                                            .build();
                                    getNotificationManager(context).notify(FOREGROUND_NOTIFICATION, notification);
                                } else {
                                    Logger.i("Could not retrieve context to update notification for postscan");
                                }
                            }
                            postscanStartTime = System.currentTimeMillis();
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            postscanEndTime = System.currentTimeMillis();
                        }
                    });
        } catch (ClassNotFoundException e) {
            throw new UnexpectedException("Failed to load a class", e);
        }
    }

    private String formatDuration(long ms) {
        return DateUtils.formatElapsedTime(ms / 1000);
    }

    protected Object getField(Object instance, String fieldName) {
        try {
            return XposedHelpers.getObjectField(instance, fieldName);
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    protected Notification.Builder getNotification(Context context, String title, String details) {
        Notification.Builder notificationBuilder = new Notification.Builder(context);
        return notificationBuilder.setSmallIcon(MODULE_DRAWABLE_NOTIFICATION_ICON).setContentTitle(title)
                .setContentText(details);
    }

    private NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Service.NOTIFICATION_SERVICE);
    }

    protected TreatAsMediaFileEnum shouldBeConsideredAsMediaFile(File file) {
        List<MediaFileTypeEnum> mediaFileTypeRestrictions = getMediaFileTypeRestrictions(file.getParentFile());

        if (mediaFileTypeRestrictions.isEmpty()) {
            return TreatAsMediaFileEnum.media_file_default;
        }

        try {
            Class<?> mediaFileClass = getLoadPackageParam().classLoader.loadClass("android.media.MediaFile");
            Object mediaFileTypeForFile = XposedHelpers.callStaticMethod(mediaFileClass, "getFileType", file.getAbsolutePath());
            if (mediaFileTypeForFile != null) {
                int fileType = XposedHelpers.getIntField(mediaFileTypeForFile, "fileType");
                for (MediaFileTypeEnum mediaFileType : mediaFileTypeRestrictions) {
                    switch (mediaFileType) {
                        case music:
                            if ((Boolean) XposedHelpers.callStaticMethod(mediaFileClass, "isAudioFileType", fileType)) {
                                return TreatAsMediaFileEnum.media_file_true;
                            }
                            break;
                        case video:
                            if ((Boolean) XposedHelpers.callStaticMethod(mediaFileClass, "isVideoFileType", fileType)) {
                                return TreatAsMediaFileEnum.media_file_true;
                            }

                            break;
                        case picture:
                            if ((Boolean) XposedHelpers.callStaticMethod(mediaFileClass, "isImageFileType", fileType)) {
                                return TreatAsMediaFileEnum.media_file_true;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            throw new UnexpectedException("Class \"android.media.MediaFile\" not found");
        }
        return TreatAsMediaFileEnum.media_file_false;
    }

    private List<MediaFileTypeEnum> getMediaFileTypeRestrictions(File directory) {
        List<MediaFileTypeEnum> mediaFileTypeRestrictions = mediaFilesToConsiderByDirectory.get(directory
                .getAbsolutePath());
        if (mediaFileTypeRestrictions != null) {
            // Logger.log(Log.DEBUG,
            // "Media file type marker files for directory {0}: {1}", directory,
            // mediaFileTypeRestrictions);
            return mediaFileTypeRestrictions;
        } else {
            mediaFileTypeRestrictions = new ArrayList<MsoXposedMod.MediaFileTypeEnum>();
        }

        File musicFileTypeMarker = new File(directory, ".scanMusic");
        File videoFileTypeMarker = new File(directory, ".scanVideo");
        File picturesFileTypeMarker = new File(directory, ".scanPictures");
        if (musicFileTypeMarker.exists()) {
            mediaFileTypeRestrictions.add(MediaFileTypeEnum.music);
        }
        if (videoFileTypeMarker.exists()) {
            mediaFileTypeRestrictions.add(MediaFileTypeEnum.video);
        }
        if (picturesFileTypeMarker.exists()) {
            mediaFileTypeRestrictions.add(MediaFileTypeEnum.picture);
        }
        if (mediaFileTypeRestrictions.isEmpty()) {
            // Logger.log(Log.DEBUG,
            // "No media file type marker files found in directory {0}",
            // directory);
            File parentDirectory = directory.getParentFile();
            if (parentDirectory != null) {
                mediaFileTypeRestrictions = getMediaFileTypeRestrictions(parentDirectory);
            }
        } else {
            Logger.d("Media file type marker files found in directory {0}: ", directory,
                    mediaFileTypeRestrictions);
        }
        mediaFilesToConsiderByDirectory.put(directory.getAbsolutePath(), mediaFileTypeRestrictions);
        return mediaFileTypeRestrictions;
    }

    private void getDirectoriesMarkedForScanning(List<String> directoriesToScan, File directoryToStartFrom) {
        File[] allFiles = directoryToStartFrom.listFiles();
        if (allFiles == null) {
            return;
        }
        for (File file : allFiles) {
            if (file.isDirectory()) {
                File directory = file;
                File markerFile = new File(directory, ".scanMedia");
                if (markerFile.exists()) {
                    Logger.d("Marker file exists in directory {0}", directory);
                    directoriesToScan.add(directory.getAbsolutePath());
                }
                // else {
                // getDirectoriesMarkedForScanning(directoriesToScan,
                // directory);
                // }
            }
        }
    }

    private void deleteMediaStoreContent(Context context) throws Exception {
        ContentProviderClient contentProviderClient = context.getContentResolver().acquireContentProviderClient("media");
        android.net.Uri.Builder builder = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon();
        Uri uri = builder.appendQueryParameter("deletedata", "false").build();
        Logger.d("Modified MediaStore.Audio.Media.EXTERNAL_CONTENT_URI is {0}", uri.toString());
        int deleted = contentProviderClient.delete(uri, null, null);
        Logger.i("Deleted {0} rows from External Audio Content", deleted);

        builder = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon();
        uri = builder.appendQueryParameter("deletedata", "false").build();
        Logger.d("Modified MediaStore.Images.Media.EXTERNAL_CONTENT_URI is {0}", uri.toString());
        deleted = contentProviderClient.delete(uri, null, null);
        Logger.i("Deleted {0} rows from External Image Content", deleted);

        builder = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon();
        uri = builder.appendQueryParameter("deletedata", "false").build();
        Logger.d("Modified MediaStore.Video.Media.EXTERNAL_CONTENT_URI is {0}", uri.toString());
        deleted = contentProviderClient.delete(uri, null, null);
        Logger.i("Deleted {0} rows from External Video Content", deleted);
    }

    private void triggerMediaScanner(Context context) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://"
                + Environment.getExternalStorageDirectory()));
        intent.setPackage(TARGET_PACKAGE_NAMES[0]);
        context.sendBroadcast(intent);
    }

    private void displayToastUsingHandler(final Context context, final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                displayToast(text);
            }
        });
    }
}
