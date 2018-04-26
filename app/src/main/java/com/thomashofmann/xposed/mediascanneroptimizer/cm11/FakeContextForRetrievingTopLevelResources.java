package com.thomashofmann.xposed.mediascanneroptimizer.cm11;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.FeatureInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Display;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by thofmann on 25.12.2014.
 */
public class FakeContextForRetrievingTopLevelResources extends Context {
    @Override
    public AssetManager getAssets() {
        return null;
    }

    @Override
    public Resources getResources() {
        return null;
    }

    @Override
    public PackageManager getPackageManager() {
        return new PackageManager() {
            @Override
            public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public PackageInfo getPackageInfo(VersionedPackage versionedPackage, int i) throws NameNotFoundException {
                return null;
            }

            @Override
            public String[] currentToCanonicalPackageNames(String[] names) {
                return new String[0];
            }

            @Override
            public String[] canonicalToCurrentPackageNames(String[] names) {
                return new String[0];
            }

            @Override
            public Intent getLaunchIntentForPackage(String packageName) {
                return null;
            }

            @Nullable
            @Override
            public Intent getLeanbackLaunchIntentForPackage(@NonNull String s) {
                return null;
            }

            @Override
            public int[] getPackageGids(String packageName) throws NameNotFoundException {
                return new int[0];
            }

            @Override
            public int[] getPackageGids(String s, int i) throws NameNotFoundException {
                return new int[0];
            }

            @Override
            public int getPackageUid(String s, int i) throws NameNotFoundException {
                return 0;
            }

            @Override
            public PermissionInfo getPermissionInfo(String name, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public List<PermissionInfo> queryPermissionsByGroup(String group, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public PermissionGroupInfo getPermissionGroupInfo(String name, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
                return null;
            }

            @Override
            public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public ActivityInfo getReceiverInfo(ComponentName component, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public List<PackageInfo> getInstalledPackages(int flags) {
                return null;
            }

            @Override
            public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, int flags) {
                return null;
            }

            @Override
            public int checkPermission(String permName, String pkgName) {
                return PackageManager.PERMISSION_GRANTED;
            }

            @Override
            public boolean isPermissionRevokedByPolicy(@NonNull String s, @NonNull String s1) {
                return false;
            }

            @Override
            public boolean addPermission(PermissionInfo info) {
                return false;
            }

            @Override
            public boolean addPermissionAsync(PermissionInfo info) {
                return false;
            }

            @Override
            public void removePermission(String name) {

            }

            @Override
            public int checkSignatures(String pkg1, String pkg2) {
                return PackageManager.SIGNATURE_MATCH;
            }

            @Override
            public int checkSignatures(int uid1, int uid2) {
                return PackageManager.SIGNATURE_MATCH;
            }

            @Override
            public String[] getPackagesForUid(int uid) {
                return new String[0];
            }

            @Override
            public String getNameForUid(int uid) {
                return null;
            }

            @Override
            public List<ApplicationInfo> getInstalledApplications(int flags) {
                return null;
            }

            @Override
            public boolean isInstantApp() {
                return false;
            }

            @Override
            public boolean isInstantApp(String s) {
                return false;
            }

            @Override
            public int getInstantAppCookieMaxBytes() {
                return 0;
            }

            @NonNull
            @Override
            public byte[] getInstantAppCookie() {
                return new byte[0];
            }

            @Override
            public void clearInstantAppCookie() {

            }

            @Override
            public void updateInstantAppCookie(@Nullable byte[] bytes) {

            }

            @Override
            public String[] getSystemSharedLibraryNames() {
                return new String[0];
            }

            @NonNull
            @Override
            public List<SharedLibraryInfo> getSharedLibraries(int i) {
                return null;
            }

            @Nullable
            @Override
            public ChangedPackages getChangedPackages(int i) {
                return null;
            }

            @Override
            public FeatureInfo[] getSystemAvailableFeatures() {
                return new FeatureInfo[0];
            }

            @Override
            public boolean hasSystemFeature(String name) {
                return false;
            }

            @Override
            public boolean hasSystemFeature(String s, int i) {
                return false;
            }

            @Override
            public ResolveInfo resolveActivity(Intent intent, int flags) {
                return null;
            }

            @Override
            public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
                return null;
            }

            @Override
            public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics, Intent intent, int flags) {
                return null;
            }

            @Override
            public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
                return null;
            }

            @Override
            public ResolveInfo resolveService(Intent intent, int flags) {
                return null;
            }

            @Override
            public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
                return null;
            }

            @Override
            public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
                return null;
            }

            @Override
            public ProviderInfo resolveContentProvider(String name, int flags) {
                return null;
            }

            @Override
            public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
                return null;
            }

            @Override
            public InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags) throws NameNotFoundException {
                return null;
            }

            @Override
            public List<InstrumentationInfo> queryInstrumentation(String targetPackage, int flags) {
                return null;
            }

            @Override
            public Drawable getDrawable(String packageName, int resid, ApplicationInfo appInfo) {
                return null;
            }

            @Override
            public Drawable getActivityIcon(ComponentName activityName) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getActivityIcon(Intent intent) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getActivityBanner(ComponentName componentName) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getActivityBanner(Intent intent) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getDefaultActivityIcon() {
                return null;
            }

            @Override
            public Drawable getApplicationIcon(ApplicationInfo info) {
                return null;
            }

            @Override
            public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getApplicationBanner(ApplicationInfo applicationInfo) {
                return null;
            }

            @Override
            public Drawable getApplicationBanner(String s) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getActivityLogo(ComponentName activityName) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getActivityLogo(Intent intent) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getApplicationLogo(ApplicationInfo info) {
                return null;
            }

            @Override
            public Drawable getApplicationLogo(String packageName) throws NameNotFoundException {
                return null;
            }

            @Override
            public Drawable getUserBadgedIcon(Drawable drawable, UserHandle userHandle) {
                return null;
            }

            @Override
            public Drawable getUserBadgedDrawableForDensity(Drawable drawable, UserHandle userHandle, Rect rect, int i) {
                return null;
            }

            @Override
            public CharSequence getUserBadgedLabel(CharSequence charSequence, UserHandle userHandle) {
                return null;
            }

            @Override
            public CharSequence getText(String packageName, int resid, ApplicationInfo appInfo) {
                return null;
            }

            @Override
            public XmlResourceParser getXml(String packageName, int resid, ApplicationInfo appInfo) {
                return null;
            }

            @Override
            public CharSequence getApplicationLabel(ApplicationInfo info) {
                return null;
            }

            @Override
            public Resources getResourcesForActivity(ComponentName activityName) throws NameNotFoundException {
                return null;
            }

            @Override
            public Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException {
                return null;
            }

            @Override
            public Resources getResourcesForApplication(String appPackageName) throws NameNotFoundException {
                return null;
            }

            @Override
            public void verifyPendingInstall(int id, int verificationCode) {

            }

            @Override
            public void extendVerificationTimeout(int id, int verificationCodeAtTimeout, long millisecondsToDelay) {

            }

            @Override
            public void setInstallerPackageName(String targetPackage, String installerPackageName) {

            }

            @Override
            public String getInstallerPackageName(String packageName) {
                return null;
            }

            @Override
            public void addPackageToPreferred(String packageName) {

            }

            @Override
            public void removePackageFromPreferred(String packageName) {

            }

            @Override
            public List<PackageInfo> getPreferredPackages(int flags) {
                return null;
            }

            @Override
            public void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set, ComponentName activity) {

            }

            @Override
            public void clearPackagePreferredActivities(String packageName) {

            }

            @Override
            public int getPreferredActivities(List<IntentFilter> outFilters, List<ComponentName> outActivities, String packageName) {
                return 0;
            }

            @Override
            public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {

            }

            @Override
            public int getComponentEnabledSetting(ComponentName componentName) {
                return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            }

            @Override
            public void setApplicationEnabledSetting(String packageName, int newState, int flags) {

            }

            @Override
            public int getApplicationEnabledSetting(String packageName) {
                return PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            }

            @Override
            public boolean isSafeMode() {
                return true;
            }

            @Override
            public void setApplicationCategoryHint(@NonNull String s, int i) {

            }

            @NonNull
            @Override
            public PackageInstaller getPackageInstaller() {
                return null;
            }

            @Override
            public boolean canRequestPackageInstalls() {
                return false;
            }
        };
    }

    @Override
    public ContentResolver getContentResolver() {
        return null;
    }

    @Override
    public Looper getMainLooper() {
        return null;
    }

    @Override
    public Context getApplicationContext() {
        return null;
    }

    @Override
    public void setTheme(int resid) {

    }

    @Override
    public Resources.Theme getTheme() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public String getPackageName() {
        return null;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return null;
    }

    @Override
    public String getPackageResourcePath() {
        return null;
    }

    @Override
    public String getPackageCodePath() {
        return null;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return null;
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context context, String s) {
        return false;
    }

    @Override
    public boolean deleteSharedPreferences(String s) {
        return false;
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return null;
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return null;
    }

    @Override
    public boolean deleteFile(String name) {
        return false;
    }

    @Override
    public File getFileStreamPath(String name) {
        return null;
    }

    @Override
    public File getDataDir() {
        return null;
    }

    @Override
    public File getFilesDir() {
        return null;
    }

    @Override
    public File getNoBackupFilesDir() {
        return null;
    }

    @Override
    public File getExternalFilesDir(String type) {
        return null;
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        return new File[0];
    }

    @Override
    public File getObbDir() {
        return null;
    }

    @Override
    public File[] getObbDirs() {
        return new File[0];
    }

    @Override
    public File getCacheDir() {
        return null;
    }

    @Override
    public File getCodeCacheDir() {
        return null;
    }

    @Override
    public File getExternalCacheDir() {
        return null;
    }

    @Override
    public File[] getExternalCacheDirs() {
        return new File[0];
    }

    @Override
    public File[] getExternalMediaDirs() {
        return new File[0];
    }

    @Override
    public String[] fileList() {
        return new String[0];
    }

    @Override
    public File getDir(String name, int mode) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
        return null;
    }

    @Override
    public boolean moveDatabaseFrom(Context context, String s) {
        return false;
    }

    @Override
    public boolean deleteDatabase(String name) {
        return false;
    }

    @Override
    public File getDatabasePath(String name) {
        return null;
    }

    @Override
    public String[] databaseList() {
        return new String[0];
    }

    @Override
    public Drawable getWallpaper() {
        return null;
    }

    @Override
    public Drawable peekWallpaper() {
        return null;
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        return 0;
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        return 0;
    }

    @Override
    public void setWallpaper(Bitmap bitmap) throws IOException {

    }

    @Override
    public void setWallpaper(InputStream data) throws IOException {

    }

    @Override
    public void clearWallpaper() throws IOException {

    }

    @Override
    public void startActivity(Intent intent) {

    }

    @Override
    public void startActivity(Intent intent, Bundle options) {

    }

    @Override
    public void startActivities(Intent[] intents) {

    }

    @Override
    public void startActivities(Intent[] intents, Bundle options) {

    }

    @Override
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) throws IntentSender.SendIntentException {

    }

    @Override
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {

    }

    @Override
    public void sendBroadcast(Intent intent) {

    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle user, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver broadcastReceiver, IntentFilter intentFilter, int i) {
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(BroadcastReceiver broadcastReceiver, IntentFilter intentFilter, @Nullable String s, @Nullable Handler handler, int i) {
        return null;
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {

    }

    @Override
    public ComponentName startService(Intent service) {
        return null;
    }

    @Nullable
    @Override
    public ComponentName startForegroundService(Intent intent) {
        return null;
    }

    @Override
    public boolean stopService(Intent service) {
        return false;
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return false;
    }

    @Override
    public void unbindService(ServiceConnection conn) {

    }

    @Override
    public boolean startInstrumentation(ComponentName className, String profileFile, Bundle arguments) {
        return false;
    }

    @Override
    public Object getSystemService(String name) {
        return null;
    }

    @Nullable
    @Override
    public String getSystemServiceName(@NonNull Class<?> aClass) {
        return null;
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) {
        return PackageManager.PERMISSION_DENIED;
    }

    @Override
    public int checkCallingPermission(String permission) {
        return PackageManager.PERMISSION_DENIED;
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        return PackageManager.PERMISSION_DENIED;
    }

    @Override
    public int checkSelfPermission(@NonNull String s) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforcePermission(String permission, int pid, int uid, String message) {

    }

    @Override
    public void enforceCallingPermission(String permission, String message) {

    }

    @Override
    public void enforceCallingOrSelfPermission(String permission, String message) {

    }

    @Override
    public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(String s, Uri uri, int i) {

    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags, String message) {

    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createContextForSplit(String s) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        return null;
    }

    @Override
    public Context createDisplayContext(Display display) {
        return null;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        return null;
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        return false;
    }
}
