package com.thomashofmann.xposed.mediascanneroptimizer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.text.format.DateUtils;

import com.thomashofmann.xposed.lib.AfterMethodHook;
import com.thomashofmann.xposed.lib.BeforeMethodHook;
import com.thomashofmann.xposed.lib.Logger;
import com.thomashofmann.xposed.lib.MethodHook;
import com.thomashofmann.xposed.lib.Paypal;
import com.thomashofmann.xposed.lib.Procedure1;
import com.thomashofmann.xposed.lib.UnexpectedException;
import com.thomashofmann.xposed.lib.XposedModule;
import com.thomashofmann.xposed.mediascanneroptimizer.cm11.FakeContextForRetrievingTopLevelResources;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MsoXposedMod extends XposedModule {
    private static final String[] TARGET_PACKAGE_NAMES = new String[]{"com.android.providers.media"};
    private static final String SCAN_MEDIA_MARKER = ".scanMedia";
    private static final String SCAN_MUSIC_MARKER = ".scanMusic";
    private static final String SCAN_PICTURES_MARKER = ".scanPictures";
    private static final String SCAN_VIDEO_MARKER = ".scanVideo";
    private static final String USER_INITIATED_SCAN = "userInitiatedScan";
    private static final int FOREGROUND_NOTIFICATION = 1;

    private Map<String, Intent> intentsByVolume = new HashMap<String, Intent>();
    private Map<Integer, String> volumesByStartId = new HashMap<Integer, String>();
    private int numberOfStartCommandsForExternalVolume;
    private int numberOfSuccessfulRunsForExternalVolume;
    private long processDirectoryStartTime;
    private long processDirectoryEndTime;
    private long prescanStartTime;
    private long prescanEndTime;
    private long scanTime;
    private long postscanStartTime;
    private long postscanEndTime;
    private long totalStartTime;
    private long totalEndTime;
    private static Handler handler;
    private Context mediaScannerContext;

    private enum MediaFileTypeEnum {
        music, video, picture
    }

    private enum TreatAsMediaFileEnum {
        media_file_true, media_file_false, media_file_default
    }

    private static Map<String, List<MediaFileTypeEnum>> mediaFilesToConsiderByDirectory = new HashMap<String, List<MediaFileTypeEnum>>();

    public MsoXposedMod() {
        Logger.init("XMSO", getSettings());
    }


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
        return PreferencesFragment.PREF_CHANGE_ACTION;
    }

    @Override
    protected int getNotificationIconResourceId() {
        return R.drawable.ic_notification;
    }


    @Override
    protected void doInitZygote(StartupParam startupParam) {
        try {
            /*
            Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            Logger.d("resourcesManagerClass is {0}", resourcesManagerClass);
            if (resourcesManagerClass != null) {
                Method getTopLevelResourcesMethod = null;
                for (Method method : resourcesManagerClass.getDeclaredMethods()) {
                    if(method.getName().equals("getTopLevelResources"))  {
                        getTopLevelResourcesMethod = method;
                        break;
                    }
                }
                Logger.d("getTopLevelResourcesMethod is {0}", getTopLevelResourcesMethod);
                if(getTopLevelResourcesMethod != null) {
                    Map<Member, XposedBridge.CopyOnWriteSortedSet<XC_MethodHook>> sHookedMethodCallbacks = (Map<Member, XposedBridge.CopyOnWriteSortedSet<XC_MethodHook>>) XposedHelpers.getStaticObjectField(XposedBridge.class, "sHookedMethodCallbacks");
                    XposedBridge.CopyOnWriteSortedSet<XC_MethodHook> hooksForMethod = sHookedMethodCallbacks.get(getTopLevelResourcesMethod);
                    Logger.d("hooksForMethod is {0}", hooksForMethod);
                    Object[] hooksForMethodSnapshot = hooksForMethod.getSnapshot();
                    Logger.d("hooksForMethodSnapshot size is {0}", hooksForMethodSnapshot.length);
                    XC_MethodHook hook = (XC_MethodHook) hooksForMethodSnapshot[0];
                    Logger.d("hook is {0}", hook);
                    XposedBridge.hookAllMethods(hook.getClass(),"beforeHookedMethod",new LogMethodInvocationHook());
                    XposedBridge.hookAllMethods(hook.getClass(),"afterHookedMethod",new AfterMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                        @Override
                        public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                            Logger.d("Result for afterHookedMethod is {0}",methodHookParam.getResult());
                        }
                    }));
                    XposedBridge.hookAllMethods(resourcesManagerClass, "getTopLevelThemedResources", new LogMethodInvocationHook());
                    XposedBridge.hookAllMethods(resourcesManagerClass, "getTopLevelThemedResources", hook);
                }
            }*/
            Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            Logger.d("resourcesManagerClass is {0}", resourcesManagerClass);
            if (resourcesManagerClass != null) {
                Method getTopLevelResourcesMethod = null;
                for (Method method : resourcesManagerClass.getDeclaredMethods()) {
                    if (method.getName().equals("getTopLevelResources")) {
                        getTopLevelResourcesMethod = method;
                        break;
                    }
                }
                Logger.d("getTopLevelResourcesMethod is {0}", getTopLevelResourcesMethod);
                Method getTopLevelThemedResourcesMethod = null;
                for (Method method : resourcesManagerClass.getDeclaredMethods()) {
                    if (method.getName().equals("getTopLevelThemedResources")) {
                        getTopLevelThemedResourcesMethod = method;
                        break;
                    }
                }
                Logger.d("getTopLevelThemedResourcesMethod is {0}", getTopLevelThemedResourcesMethod);

                if (getTopLevelResourcesMethod != null && getTopLevelThemedResourcesMethod != null) {
                    //XposedBridge.hookAllMethods(resourcesManagerClass, "getTopLevelThemedResources", new LogMethodInvocationHook());
                    XposedBridge.hookAllMethods(resourcesManagerClass, "getTopLevelThemedResources",
                            new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                                @Override
                                public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                                    Object resourcesManager = methodHookParam.thisObject;
                                    /*
                                    public Resources getTopLevelResources(String resDir, String[] overlayDirs, int displayId, String packageName,
                                                Configuration overrideConfiguration, CompatibilityInfo compatInfo, IBinder token, Context context)
                                     */
                                    /*
                                    public Resources getTopLevelThemedResources(String resDir, int displayId,
                                                        String packageName,
                                                        String themePackageName,
                                                        CompatibilityInfo compatInfo, IBinder token)
                                     */
                                    String packageName = (String) methodHookParam.args[2];
                                    if (getTargetPackageNames().contains(packageName)) {
                                        Logger.d("Calling getTopLevelResources instead of getTopLevelThemedResources for {0}", packageName);
                                        Object unthemedTopLevelResources = XposedHelpers.callMethod(resourcesManager, "getTopLevelResources",
                                                methodHookParam.args[0],
                                                new String[]{},
                                                methodHookParam.args[1],
                                                methodHookParam.args[2],
                                                null,
                                                methodHookParam.args[4],
                                                methodHookParam.args[5],
                                                new FakeContextForRetrievingTopLevelResources());
                                        methodHookParam.setResult(unthemedTopLevelResources);
                                    }
                                }
                            }));
                }
            }
        } catch (Throwable t) {
            Logger.e(t, "Problem hooking getTopLevelThemedResources");
        }
    }


//    @Override
//    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
//        if (loadPackageParam.packageName.equals("com.android.systemui")) {
//            Logger.i("loadPackage for {0}", loadPackageParam.packageName);
//            Class statusBarIconClass = XposedHelpers.findClass("com.android.internal.statusbar.StatusBarIcon", loadPackageParam.classLoader);
//            Logger.i("statusBarIconClass is {0}", statusBarIconClass);
//
//            hookMethod("com.android.systemui.statusbar.StatusBarIconView", loadPackageParam.classLoader, "getIcon",
//                    Context.class, statusBarIconClass, new AfterMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
//                        @Override
//                        public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
//                            Object result = methodHookParam.getResult();
//                            if (result == null) {
//                                Context context = (Context) methodHookParam.args[0];
//                                Object statusBarIcon = methodHookParam.args[1];
//                                Logger.d("statusBarIcon is {0}", statusBarIcon);
//                                    /*
//                                    int userId = icon.user.getIdentifier();
//                                    if (userId == UserHandle.USER_ALL) {
//                                        userId = UserHandle.USER_OWNER;
//                                    }
//                                    r = context.getPackageManager().getResourcesForApplicationAsUser(icon.iconPackage, userId);
//                                     */
//
//                                String iconPackage = (String) XposedHelpers.getObjectField(statusBarIcon, "iconPackage");
//                                int iconId = (Integer) XposedHelpers.getObjectField(statusBarIcon, "iconId");
//
//                                /*
//                                if(moduleResources != null && iconPackage.equals(TARGET_PACKAGE_NAMES[0])) {
//                                    Drawable icon = moduleResources.getDrawable(iconId);
//                                    if(icon != null) {
//                                        methodHookParam.setResult(icon);
//                                    }
//                                }
//                                */
//                                Object user = XposedHelpers.getObjectField(statusBarIcon, "user");
//                                int userId = (Integer) XposedHelpers.callMethod(user, "getIdentifier");
//                                if (userId == -1) {
//                                    userId = 0;
//                                }
//                                PackageManager packageManager = context.getPackageManager();
//                                Resources resources = (Resources) XposedHelpers.callMethod(packageManager, "getResourcesForApplicationAsUser", iconPackage, userId);
//                                Drawable icon = resources.getDrawable(iconId);
//                                Logger.i("Notification icon for {0} is {1}", iconId, icon);
//                                methodHookParam.setResult(icon);
//                            }
//                        }
//                    }));
//        }
//
//        super.handleLoadPackage(loadPackageParam);
//    }


    @Override
    public void doHandleLoadPackage() {
        /*
         * Internal service helper that no-one should use directly.
         *
         * The way the scan currently works is:
         * - The Java MediaScannerService creates a MediaScanner (this class), and calls
         *   MediaScanner.scanDirectories on it.
         * - scanDirectories() calls the native processDirectory() for each of the specified directories.
         * - the processDirectory() JNI method wraps the provided mediascanner client in a native
         *   'MyMediaScannerClient' class, then calls processDirectory() on the native MediaScanner
         *   object (which got created when the Java MediaScanner was created).
         * - native MediaScanner.processDirectory() calls
         *   doProcessDirectory(), which recurses over the folder, and calls
         *   native MyMediaScannerClient.scanFile() for every file whose extension matches.
         * - native MyMediaScannerClient.scanFile() calls back on Java MediaScannerClient.scanFile,
         *   which calls doScanFile, which after some setup calls back down to native code, calling
         *   MediaScanner.processFile().
         * - MediaScanner.processFile() calls one of several methods, depending on the type of the
         *   file: parseMP3, parseMP4, parseMidi, parseOgg or parseWMA.
         * - each of these methods gets metadata key/value pairs from the file, and repeatedly
         *   calls native MyMediaScannerClient.handleStringTag, which calls back up to its Java
         *   counterparts in this file.
         * - Java handleStringTag() gathers the key/value pairs that it's interested in.
         * - once processFile returns and we're back in Java code in doScanFile(), it calls
         *   Java MyMediaScannerClient.endFile(), which takes all the data that's been
         *   gathered and inserts an entry in to the database.
         *
         * In summary:
         * Java MediaScannerService calls
         * Java MediaScanner scanDirectories, which calls
         * Java MediaScanner processDirectory (native method), which calls
         * native MediaScanner processDirectory, which calls
         * native MyMediaScannerClient scanFile, which calls
         * Java MyMediaScannerClient scanFile, which calls
         * Java MediaScannerClient doScanFile, which calls
         * Java MediaScanner processFile (native method), which calls
         * native MediaScanner processFile, which calls
         * native parseMP3, parseMP4, parseMidi, parseOgg or parseWMA, which calls
         * native MyMediaScanner handleStringTag, which calls
         * Java MyMediaScanner handleStringTag.
         * Once MediaScanner processFile returns, an entry is inserted in to the database.
         *
         * The MediaScanner class is not thread-safe, so it should only be used in a single threaded manner.
         */

        hookMethod("com.android.providers.media.MtpReceiver",
                getLoadPackageParam().classLoader, "onReceive", Context.class, Intent.class,
                new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {

                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        Logger.d("MtpReceiver onReceive called.");
                        methodHookParam.setResult(null);
                    }
                }));

        hookMethod("com.android.providers.media.MediaScannerReceiver",
                getLoadPackageParam().classLoader, "onReceive", Context.class, Intent.class,
                new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        Logger.i("onReceive is called in MediaScannerReceiver");
                        Intent intent = (Intent) methodHookParam.args[1];
                        Logger.i("Intent {0} received.", intent);
                        Bundle extras = intent.getExtras();
                        Logger.i("Extras are {0}", extras);
                        Context context = (Context) methodHookParam.args[0];

                        getSettings().reload();

                        if (intent.getAction().equals(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)) {
                            /*
                             * piggyback on this event because it can be triggered easily
                             */
                            if (extras != null && extras.getBoolean(PreferencesFragment.ACTION_SCAN_EXTERNAL)) {
                                Logger.i("User initiated request to rescan external volume.");

                                if (intentsByVolume.containsKey("external")) {
                                    displayToastUsingHandler(context, "A scan for the external volume is already in progress. Please try again later.");
                                } else {
                                    Bundle serviceExtras = new Bundle();
                                    serviceExtras.putString("volume", "external");
                                    serviceExtras.putBoolean(USER_INITIATED_SCAN, true);
                                    Intent serviceIntent = new Intent();
                                    String serviceClassName = "com.android.providers.media.MediaScannerService";
                                    try {
                                        /*
                                         * call HTC specific service
                                         */
                                        getLoadPackageParam().classLoader.loadClass("com.android.providers.media.MediaScannerServiceEx");
                                        serviceClassName = "com.android.providers.media.MediaScannerServiceEx";
                                    } catch (ClassNotFoundException e) {
                                    }
                                    ComponentName componentName = new ComponentName("com.android.providers.media", serviceClassName);
                                    serviceIntent.setComponent(componentName);
                                    serviceIntent.putExtras(serviceExtras);
                                    context.startService(serviceIntent);
                                }
                                methodHookParam.setResult(null);
                            } else if (extras != null && extras.getBoolean(PreferencesFragment.ACTION_DELETE_MEDIA_STORE_CONTENTS)) {
                                Logger.i("Request to delete MediaStore content");
                                if (intentsByVolume.containsKey("external")) {
                                    displayToastUsingHandler(context, "Could not delete media store contents for external volume because a scan is in progress. Please try again later.");
                                } else {
                                    try {
                                        deleteMediaStoreContent(context);
                                        createSimpleNotification(context, "Deleted media store content.", null, null);
                                    } catch (Exception e) {
                                        UnexpectedException unexpectedException = new UnexpectedException(e, "Failed to delete media store contents");
                                        createAndShowNotification(context, "Media Scanner Optimizer", unexpectedException);
                                    }
                                }
                                methodHookParam.setResult(null);
                            }
                        }
                    }
                }));

        Procedure1<XC_MethodHook.MethodHookParam> hookBeforeOnStartCommandInMediaScannerServiceCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                Service service = (Service) methodHookParam.thisObject;

                handler = new Handler();
                Intent intent = (Intent) methodHookParam.args[0];
                Bundle extras = intent.getExtras();
                String filePathInIntentExtra = extras != null ? extras.getString("filepath") : null;
                String mimeTypeInIntentExtra = extras != null ? extras.getString("mimetype") : null;
                String volumeInIntentExtra = extras != null ? extras.getString("volume") : null;

                int startId = (Integer) methodHookParam.args[2];
                Logger.i("onStartCommand is called with Intent {0}, startId {1}, filepath {2}, mimetype {3} and volume {4} on {5}",
                        intent, startId, filePathInIntentExtra, mimeTypeInIntentExtra, volumeInIntentExtra, service);

                if (volumeInIntentExtra != null) {
                    if ("external".equals(volumeInIntentExtra)) {
                        if (!getSettings().getPreferences().getBoolean("pref_run_automatically_state", true)
                                && !(extras != null && extras.getBoolean(USER_INITIATED_SCAN))) {
                            Logger.i("Not running media scanner for external volume. It is configured to not run automatically");
                            methodHookParam.setResult(Service.START_NOT_STICKY);
                            return;
                        }
                        numberOfStartCommandsForExternalVolume++;
                    }

                    if (intentsByVolume.containsKey(volumeInIntentExtra)) {
                        if (getSettings().getPreferences().getBoolean("pref_prevent_repetitive_scans_state", true)) {
                            /*
                             * don't queue another run for the volume that will already be scanned
                             */
                            Logger.i("Early return from onStartCommand because scan for volumen {0} is already scheduled",
                                    volumeInIntentExtra);
                            methodHookParam.setResult(Service.START_NOT_STICKY);
                            return;
                        }
                    } else {
                        intentsByVolume.put(volumeInIntentExtra, intent);
                        volumesByStartId.put(startId, volumeInIntentExtra);
                    }

                    Logger.i("Number of start commands for external volume is {0}",
                            numberOfStartCommandsForExternalVolume);
                    Logger.i("Number of successful runs for external volume is {0}",
                            numberOfSuccessfulRunsForExternalVolume);
                }
            }
        };

        try {
            /*
             * HTC specific, hook both services
             */
            getLoadPackageParam().classLoader.loadClass("com.android.providers.media.MediaScannerServiceEx");
            Logger.i("Installing HTC specific hooks.");
            hookMethod("com.android.providers.media.MediaScannerService",
                    getLoadPackageParam().classLoader, "onStartCommand", Intent.class, int.class, int.class,
                    new BeforeMethodHook(hookBeforeOnStartCommandInMediaScannerServiceCode));
            hookMethod("com.android.providers.media.MediaScannerServiceEx",
                    getLoadPackageParam().classLoader, "onStartCommand", Intent.class, int.class, int.class,
                    new BeforeMethodHook(hookBeforeOnStartCommandInMediaScannerServiceCode));
        } catch (ClassNotFoundException e) {
            hookMethod("com.android.providers.media.MediaScannerService",
                    getLoadPackageParam().classLoader, "onStartCommand", Intent.class, int.class, int.class,
                    new BeforeMethodHook(hookBeforeOnStartCommandInMediaScannerServiceCode));
        }


        Procedure1<XC_MethodHook.MethodHookParam> hookBeforeScanInMediaScannerServiceCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                Service service = (Service) methodHookParam.thisObject;

                String[] directories = (String[]) methodHookParam.args[0];
                String volumeName = (String) methodHookParam.args[1];
                Logger.i("scan is called for directories {0} on volume {1}", Arrays.toString(directories), volumeName);

                if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
                    Logger.i("Set service to foreground");
                    Notification.Builder notification = createSimpleNotification(service, "Media Scanner Optimizer", "Processing volume " + volumeName, null);
                    service.startForeground(FOREGROUND_NOTIFICATION, notification.build());
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
                    if (directoriesToScan.size() > 0) {
                        Logger.i("The following directories will be scanned by MediaScanner: {0}",
                                directoriesToScan);
                        methodHookParam.args[0] = directoriesToScan.toArray(new String[directoriesToScan.size()]);
                    } else {
                        Logger.i("No directories will be scanned by MediaScanner");
                        methodHookParam.setResult(null);
                    }
                } else {
                    Logger.i("The following directories will be scanned by MediaScanner: {0}",
                            Arrays.toString(directories));
                }
            }
        };

        Procedure1<XC_MethodHook.MethodHookParam> hookAfterScanInMediaScannerServiceCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
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
        };


        try {
            /*
             * HTC specific
             */
            getLoadPackageParam().classLoader.loadClass("com.android.providers.media.MediaScannerServiceEx");
            Logger.i("Installing HTC specific hook.");
            hookMethod("com.android.providers.media.MediaScannerServiceEx",
                    getLoadPackageParam().classLoader, "scan", String[].class, String.class, boolean.class, int.class, String[].class,
                    new MethodHook(hookBeforeScanInMediaScannerServiceCode, hookAfterScanInMediaScannerServiceCode));
        } catch (ClassNotFoundException e) {
            hookMethod("com.android.providers.media.MediaScannerService",
                    getLoadPackageParam().classLoader, "scan", String[].class, String.class,
                    new MethodHook(hookBeforeScanInMediaScannerServiceCode, hookAfterScanInMediaScannerServiceCode));
        }


        Procedure1<XC_MethodHook.MethodHookParam> hookScanFileInMediaScannerServiceCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                String path = (String) methodHookParam.args[0];
                String mimetype = (String) methodHookParam.args[1];
                Logger.i("scanFile is called for file {0} and mime type {1}", path, mimetype);
                File file = new File(path);
                if (file.exists() && shouldScanFileBasedOnDirectory(file)) {
                    Logger.d("File with path {0} will be scanned", path);
                } else {
                    Logger.d("File with path {0} should not be scanned", path);
                    methodHookParam.setResult(null);
                }
            }
        };

        boolean success = hookMethod(false, "com.android.providers.media.MediaScannerService",
                getLoadPackageParam().classLoader, "scanFile", String.class, String.class,
                new BeforeMethodHook(hookScanFileInMediaScannerServiceCode));
        if (!success) {
            Logger.i("Cannot find method scanFile will now try to hook scanFileOrDirectory.");
            // try Cyanogenmod method
            hookMethod("com.android.providers.media.MediaScannerService",
                    getLoadPackageParam().classLoader, "scanFileOrDirectory", String.class, String.class,
                    new BeforeMethodHook(hookScanFileInMediaScannerServiceCode));
        }

        hookMethod("android.app.Service", getLoadPackageParam().classLoader, "stopSelf", int.class,
                new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        Service service = (Service) methodHookParam.thisObject;
                        if (service.getClass().getPackage().getName().startsWith("com.android.providers.media")) {
                            Logger.i("stopSelf is called on {0}", service.getClass().toString());
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
                }), new AfterMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        Service service = (Service) methodHookParam.thisObject;
                    }
                }));


        hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "isNoMediaFile",
                String.class, new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        String pathToFile = (String) methodHookParam.args[0];
                        if (pathToFile.startsWith("/system")) {
                            return;
                        }

                        if (!getSettings().getPreferences().getBoolean("pref_restrict_to_certain_media_types_state", false)) {
                            Logger.i("Not restricting scan to certain media types");
                            return;
                        }

                        File file = new File(pathToFile);
                        if (!file.isDirectory()) {
                            TreatAsMediaFileEnum consideredAsMediaFile = shouldBeConsideredAsMediaFile(file);
                            if (consideredAsMediaFile != TreatAsMediaFileEnum.media_file_default) {
                                switch (consideredAsMediaFile) {
                                    case media_file_true:
                                        methodHookParam.setResult(false);
                                        Logger.v("The file {0} should be considered as a media file", file.getAbsolutePath());
                                        break;
                                    case media_file_false:
                                        Logger.v("The file {0} should not be considered as a media file", file.getAbsolutePath());
                                        methodHookParam.setResult(true);
                                        break;
                                }
                            }
                        }
                    }
                }));

        hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "scanMtpFile",
                String.class, String.class, int.class, int.class, new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        String pathToFile = (String) methodHookParam.args[0];
                        Logger.i("scanMtpFile is called for {0}", pathToFile);
                    }
                }));

        hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "scanSingleFile",
                String.class, String.class, String.class, new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        String pathToFile = (String) methodHookParam.args[0];
                        Logger.i("scanSingleFile is called for {0}", pathToFile);
                    }
                }));


        XposedHelpers.findAndHookConstructor("android.media.MediaScanner", getLoadPackageParam().classLoader, Context.class,
                new BeforeMethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        mediaScannerContext = (Context) methodHookParam.args[0];
                        if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_with_background_thread_priority_state", true)) {
                            Logger.d("Changing thread priority in MediaScanner constructor");
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        }
                    }
                }));

        hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "scanDirectories",
                String[].class, String.class, new MethodHook(
                        new Procedure1<XC_MethodHook.MethodHookParam>() {
                            @Override
                            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                                totalStartTime = System.currentTimeMillis();
                                scanTime = 0;
                            }
                        },
                        new Procedure1<XC_MethodHook.MethodHookParam>() {
                            @Override
                            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                                totalEndTime = System.currentTimeMillis();
                            }
                        }));


        Procedure1<XC_MethodHook.MethodHookParam> hookBeforePrescanInInMediaScannerCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                prescanStartTime = System.currentTimeMillis();
            }
        };

        Procedure1<XC_MethodHook.MethodHookParam> hookAfterPrescanInInMediaScannerCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                prescanEndTime = System.currentTimeMillis();
            }
        };


        success = hookMethod(false, "android.media.MediaScanner", getLoadPackageParam().classLoader, "prescan",
                String.class, boolean.class, new MethodHook(hookBeforePrescanInInMediaScannerCode, hookAfterPrescanInInMediaScannerCode));
        if (!success) {
            /*
             * try HTC variant
             */
            hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "prescan",
                    String.class, boolean.class, int.class, boolean.class, new MethodHook(hookBeforePrescanInInMediaScannerCode, hookAfterPrescanInInMediaScannerCode));
        }


        Procedure1<XC_MethodHook.MethodHookParam> hookBeforeProcessDirectoryMediaScannerCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
                    String path = (String) methodHookParam.args[0];
                    Notification.Builder notification = createSimpleNotification(mediaScannerContext, "Media Scanner Optimizer", "Scanning " + path, null);
                    getNotificationManager(mediaScannerContext).notify(FOREGROUND_NOTIFICATION, notification.build());
                }
                processDirectoryStartTime = System.currentTimeMillis();
            }
        };

        Procedure1<XC_MethodHook.MethodHookParam> hookAfterProcessDirectoryMediaScannerCode = new Procedure1<XC_MethodHook.MethodHookParam>() {
            @Override
            public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                processDirectoryEndTime = System.currentTimeMillis();
                scanTime = scanTime + (processDirectoryEndTime - processDirectoryStartTime);
            }
        };

        Class mediaScannerClientClass = null;
        try {
            mediaScannerClientClass = getLoadPackageParam().classLoader.loadClass("android.media.MediaScannerClient");
            success = hookMethod(false, "android.media.MediaScanner", getLoadPackageParam().classLoader, "processDirectory",
                    String.class, mediaScannerClientClass,
                    new MethodHook(hookBeforeProcessDirectoryMediaScannerCode, hookAfterProcessDirectoryMediaScannerCode));
            if (!success) {
                /*
                 * try HTC variant
                 */
                hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "processDirectory",
                        String.class, mediaScannerClientClass, boolean.class, boolean.class, int.class,
                        new MethodHook(hookBeforeProcessDirectoryMediaScannerCode, hookAfterProcessDirectoryMediaScannerCode));
            }
        } catch (ClassNotFoundException e) {
            throw new UnexpectedException(e, "Failed to load a class");
        }

        hookMethod("android.media.MediaScanner", getLoadPackageParam().classLoader, "postscan",
                String[].class, new MethodHook(new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (getSettings().getPreferences().getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
                            Notification.Builder notification = createSimpleNotification(mediaScannerContext, "Media Scanner Optimizer", "Postscan", null);
                            getNotificationManager(mediaScannerContext).notify(FOREGROUND_NOTIFICATION, notification.build());
                        }
                        postscanStartTime = System.currentTimeMillis();
                    }
                }, new Procedure1<XC_MethodHook.MethodHookParam>() {
                    @Override
                    public void apply(XC_MethodHook.MethodHookParam methodHookParam) {
                        postscanEndTime = System.currentTimeMillis();
                    }
                }));

    }

    private void reportDurations(Context context, String volumeName) {
        long prescanTime = prescanEndTime - prescanStartTime;
        long postscanTime = postscanEndTime - postscanStartTime;

        String prescanDuration = formatDuration(prescanTime);
        String scanDuration = formatDuration(scanTime);
        String postscanDuration = formatDuration(postscanTime);

        String scanDirectoriesDuration = formatDuration(totalEndTime - totalStartTime);
        Notification.Builder notification = createInboxStyleNotification(context, "Media Scanner Optimizer",
                "Scan directories time " + scanDirectoriesDuration,
                "Volume " + volumeName + " (Expand for details)",
                "Volume " + volumeName,
                "Pre-scan: " + prescanDuration,
                "Scan: " + scanDuration,
                "Post-scan: " + postscanDuration
        );

        Intent donateIntent = Paypal.createDonationIntent(context, "email@thomashofmann.com", "XMSO", "EUR");
        PendingIntent piDonate = PendingIntent.getActivity(context, 0, donateIntent, 0);

        if (!getSettings().getPreferences().getBoolean("pref_hide_donation_actions_state", false)) {
            notification.addAction(android.R.drawable.ic_menu_add, "Donate", piDonate);
        }

        PendingIntent piSend = buildActionSendPendingIntent(context, "Volume " + volumeName + " results", "Pre-scan: " + prescanDuration, "Scan: " + scanDuration, "Post-scan: " + postscanDuration);
        notification.addAction(android.R.drawable.ic_menu_send, "Send", piSend);
        showNotification(context, notification);
    }

    private String formatDuration(long ms) {
        return DateUtils.formatElapsedTime(ms / 1000);
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
            throw new UnexpectedException(e, "Failed to load a class");
        }
        return TreatAsMediaFileEnum.media_file_false;
    }

    private List<MediaFileTypeEnum> getMediaFileTypeRestrictions(File directory) {
        List<MediaFileTypeEnum> mediaFileTypeRestrictions = mediaFilesToConsiderByDirectory.get(directory
                .getAbsolutePath());
        if (mediaFileTypeRestrictions != null) {
            Logger.v("Media file type marker files for directory {0}: {1}", directory, mediaFileTypeRestrictions);
            return mediaFileTypeRestrictions;
        } else {
            mediaFileTypeRestrictions = new ArrayList<MsoXposedMod.MediaFileTypeEnum>();
        }

        File musicFileTypeMarker = new File(directory, SCAN_MUSIC_MARKER);
        File videoFileTypeMarker = new File(directory, SCAN_VIDEO_MARKER);
        File picturesFileTypeMarker = new File(directory, SCAN_PICTURES_MARKER);
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
            File parentDirectory = directory.getParentFile();
            if (parentDirectory != null) {
                mediaFileTypeRestrictions = getMediaFileTypeRestrictions(parentDirectory);
            }
        } else {
            Logger.v("Media file type marker files found in directory {0}: ", directory,
                    mediaFileTypeRestrictions);
        }
        mediaFilesToConsiderByDirectory.put(directory.getAbsolutePath(), mediaFileTypeRestrictions);
        return mediaFileTypeRestrictions;
    }

    private boolean shouldScanFileBasedOnDirectory(File file) {
        boolean restrictDirectoriesToScan = getSettings().getPreferences().getBoolean("pref_restrict_directories_to_scan_state", false);
        if (!restrictDirectoriesToScan) {
            return true;
        }
        File directory = null;
        if (file.isFile()) {
            directory = file.getParentFile();
        } else {
            directory = file;
        }
        File markerFile = new File(directory, SCAN_MEDIA_MARKER);
        if (markerFile.exists()) {
            return true;
        }
        File parentDirectory = directory.getParentFile();
        if (parentDirectory != null) {
            return shouldScanFileBasedOnDirectory(parentDirectory);
        } else {
            return false;
        }
    }

    private void getDirectoriesMarkedForScanning(List<String> directoriesToScan, File directoryToStartFrom) {
        int searchDepth = 1;
        if (getSettings().getPreferences().getBoolean("pref_restrict_directories_to_scan_scan_two_levels_deep_state", false)) {
            searchDepth = 2;
        }
        Logger.i("Searching for .scanMedia files {0} level(s) deep in file system.", searchDepth);
        getDirectoriesMarkedForScanning(directoriesToScan, directoryToStartFrom, searchDepth, 0);
    }

    private void getDirectoriesMarkedForScanning(List<String> directoriesToScan, File directoryToStartFrom, int searchDepth, int currentDepth) {
        currentDepth++;
        if (currentDepth > searchDepth) {
            Logger.d("SearchDepth of {0} reached at {1}. Will not descend further down the directory tree to find marker files.", searchDepth, directoryToStartFrom);
            return;
        }

        File[] allFiles = directoryToStartFrom.listFiles();
        if (allFiles == null) {
            return;
        }
        for (File file : allFiles) {
            if (file.isDirectory()) {
                File directory = file;
                Logger.d("CurrentDepth {0} for {1}", currentDepth, directory);
                File markerFile = new File(directory, SCAN_MEDIA_MARKER);
                if (markerFile.exists()) {
                    Logger.v("Marker file exists in directory {0}", directory);
                    directoriesToScan.add(directory.getAbsolutePath());
                } else {
                    Logger.v("Marker file does not exist in directory {0}", directory);
                    getDirectoriesMarkedForScanning(directoriesToScan, directory, searchDepth, currentDepth);
                }
            }
        }
    }

    private void deleteMediaStoreContent(Context context) throws Exception {
        ContentProviderClient contentProviderClient = context.getContentResolver().acquireContentProviderClient("media");
        android.net.Uri.Builder builder = android.provider.MediaStore.Files.getContentUri("external").buildUpon();
        Uri uri = builder.appendQueryParameter("deletedata", "false").build();
        Logger.d("Modified MediaStore.Files external volume URI is {0}", uri.toString());
        int deletedEntries = contentProviderClient.delete(uri, null, null);
        Logger.i("Deleted {0} rows from file entries", deletedEntries);

        Notification.Builder notification = createSimpleNotification(context, "Media Scanner Optimizer", "Deleted " + deletedEntries + " entries", null);
        PendingIntent piSend = buildActionSendPendingIntent(context, "Deleted media store content", "Deleted " + deletedEntries + " entries.");
        notification.addAction(android.R.drawable.ic_menu_send, "Send", piSend);
        showNotification(context, notification);
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
                displayToast(context, text);
            }
        });
    }
}
