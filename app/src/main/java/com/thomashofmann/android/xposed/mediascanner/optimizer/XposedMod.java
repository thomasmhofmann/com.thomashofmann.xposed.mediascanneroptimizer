package com.thomashofmann.android.xposed.mediascanner.optimizer;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.res.XModuleResources;
import android.media.MediaFile;
import android.media.MediaFile.MediaFileType;
import android.media.MediaScanner;
import android.media.MediaScannerClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import external.org.apache.commons.lang3.time.DurationFormatUtils;

public class XposedMod implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
	private static final String PACKAGE = "com.android.providers.media";

	public final static String SETTINGS_FILE_NAME = "xposedSettings";
	private final static String TAG = "TMHMediaScanner";
	private final static int FOREGROUND_NOTIFICATION = 1;
	private final static int SCAN_FINISHED_NOTIFICATION = 10;

	private String MODULE_PATH;
	private XSharedPreferences preferences;

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
	private boolean deleteMediaStoreContents;
	private boolean userInitiatedScan;

	private static Handler handler;

	private enum MediaFileTypeEnum {
		music, video, picture
	}

	private enum TreatAsMediaFileEnum {
		media_file_true, media_file_false, media_file_default
	}

	private static Map<String, List<MediaFileTypeEnum>> mediaFilesToConsiderByDirectory = new HashMap<String, List<MediaFileTypeEnum>>();

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		MODULE_PATH = startupParam.modulePath;
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (resparam.packageName.equals(PACKAGE)) {
			XModuleResources moduleResources = XModuleResources.createInstance(MODULE_PATH, resparam.res);
			resparam.res.setReplacement(R.drawable.ic_notification, moduleResources.fwd(R.drawable.ic_notification));
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam loadPackageParam) throws Throwable {
		if (!loadPackageParam.packageName.equals(PACKAGE)) {
			return;
		}

		log(Log.INFO, "Going to hook application {0}", loadPackageParam.packageName);

		if (preferences == null) {
			preferences = new XSharedPreferences("com.thomashofmann.android.xposed.mediascanner.optimizer",
					SETTINGS_FILE_NAME);
			log(Log.DEBUG, "XSharedPreferences are: {0}", preferences.getAll());
		}

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "isNoMediaFile",
				String.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						String pathToFile = (String) methodHookParam.args[0];
						if (pathToFile.startsWith("/system")) {
							return;
						}

						if (!preferences.getBoolean("pref_restrict_to_certain_media_types_state", false)) {
							log(Log.DEBUG, "Not restricting scan to certain media types");
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
									log(Log.DEBUG, "The file {0} should not be considered as a media file",
											file.getAbsolutePath());
									methodHookParam.setResult(true);
									break;
								}
							}
						}
					}

				});

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "scanMtpFile",
				String.class, String.class, int.class, int.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						String pathToFile = (String) methodHookParam.args[0];
						log(Log.INFO, "scanMtpFile is called for {0}", pathToFile);
					}
				});

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "scanSingleFile",
				String.class, String.class, String.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						String pathToFile = (String) methodHookParam.args[0];
						log(Log.INFO, "scanSingleFile is called for {0}", pathToFile);
					}
				});

		XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerService",
				loadPackageParam.classLoader, "onStartCommand", Intent.class, int.class, int.class,
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
						Service service = (Service) thisObject;
						int startId = (Integer) methodHookParam.args[2];
						log(Log.INFO,
								"onStartCommand is called with Intent {0}, startId {1}, filepath {2}, mimetype {3} and volume {4} on {5}",
								intent, startId, filePathInIntentExtra, mimeTypeInIntentExtra, volumeInIntentExtra,
								thisObject);

						startIdsByPid.put(Process.myPid(), startId);
						log(Log.DEBUG, "startIdsByPid is {0}", startIdsByPid);

						preferences.reload();

						if (volumeInIntentExtra != null) {
							if ("external".equals(volumeInIntentExtra)) {
								numberOfStartCommandsForExternalVolume++;
							}

							if (intentsByVolume.containsKey(volumeInIntentExtra)) {
								if (deleteMediaStoreContents) {
									log(Log.INFO,
											"Early return from onStartCommand because deletion of media store content for external volume requested while scan in progress for volumen {0}",
											volumeInIntentExtra);
									displayToast(service,
											"Could not delete media store contents for external volume because a scan is in progress. Please try again later.");
								} else if (preferences.getBoolean("pref_prevent_repetetive_scans_state", true)) {
									/*
									 * don't queue another run for the volume
									 * that will already be scanned
									 */
									log(Log.INFO,
											"Early return from onStartCommand because scan for volumen {0} is already scheduled",
											volumeInIntentExtra);
									if (userInitiatedScan) {
										displayToast(service,
												"A scan for the external volume is already in progress. Please try again later.");
									}
								}
								methodHookParam.setResult(Service.START_NOT_STICKY);
							} else {
								intentsByVolume.put(volumeInIntentExtra, intent);
								volumesByStartId.put(startId, volumeInIntentExtra);
								if (deleteMediaStoreContents) {
									log(Log.INFO, "deleteMediaStore is true");
									try {
										deleteMediaStoreContent(service);
										log(Log.INFO, "Deleted media store content.");
									} catch (Exception e) {
										StringWriter stringWriter = new StringWriter();
										PrintWriter writer = new PrintWriter(stringWriter);
										e.printStackTrace(writer);
										log(Log.INFO, "Failed to delete media store contents due to {0}",
												stringWriter.toString());
										displayToast(service,
												"Could not delete media store content for external volumne. Please try again.");
									} finally {
										deleteMediaStoreContents = false;
									}
								}
							}
						}
						log(Log.INFO, "Number of start commands for external volume is {0}",
								numberOfStartCommandsForExternalVolume);
						log(Log.INFO, "Number of successful runs for external volume is {0}",
								numberOfSuccessfulRunsForExternalVolume);
					}
				});

		XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerService",
				loadPackageParam.classLoader, "scan", String[].class, String.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						String[] directories = (String[]) methodHookParam.args[0];
						String volumeName = (String) methodHookParam.args[1];
						Object thisObject = methodHookParam.thisObject;
						Service service = (Service) thisObject;
						log(Log.INFO, "scan is called for directories {0} on volume {1}", Arrays.toString(directories),
								volumeName);

						if (preferences.getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
							log(Log.INFO, "Set service to foreground");
							Notification notification = getNotification(service, "MediaScanner",
									"Processing volume " + volumeName).build();
							service.startForeground(FOREGROUND_NOTIFICATION, notification);
						}

						if (volumeName.equals("external")
								&& preferences.getBoolean("pref_restrict_directories_to_scan_state", false)) {
							List<String> directoriesToScan = new ArrayList<String>();
							for (String originalDirectoryString : directories) {
								File originalDirectory = new File(originalDirectoryString);
								if (originalDirectory.exists() && originalDirectory.isDirectory()) {
									long start = System.currentTimeMillis();
									log(Log.INFO, "Searching directories to scan in {0}", originalDirectoryString);
									getDirectoriesMarkedForScanning(directoriesToScan, originalDirectory);
									String duration = DurationFormatUtils.formatDuration(System.currentTimeMillis()
											- start, "mm:ss");
									log(Log.INFO, "Finished searching directories to scan in {0}. Time: {1}",
											originalDirectoryString, duration);
								}
							}
							log(Log.INFO, "The following directories will be scanned by MediaScanner: {0}",
									directoriesToScan);
							methodHookParam.args[0] = directoriesToScan.toArray(new String[directoriesToScan.size()]);
						} else {
							log(Log.INFO, "The following directories will be scanned by MediaScanner: {0}",
									Arrays.toString(directories));
						}
					}

					protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						String volumeName = (String) methodHookParam.args[1];
						Object thisObject = methodHookParam.thisObject;
						Service service = (Service) thisObject;
						log(Log.INFO, "scan(String[] directories, String volumeName) has finished scanning {0}",
								volumeName);
						service.stopForeground(true);
						if (preferences.getBoolean("pref_show_results_state", true)) {
							reportDurations(service, volumeName);
						}
					}

					private void reportDurations(Context context, String volumeName) {
						long prescanTime = prescanEndTime - prescanStartTime;
						long postscanTime = postscanEndTime - postscanStartTime;

						String prescanDuration = DurationFormatUtils.formatDuration(prescanTime, "mm:ss");
						String scanDuration = DurationFormatUtils.formatDuration(scanTime, "mm:ss");
						String postscanDuration = DurationFormatUtils.formatDuration(postscanTime, "mm:ss");

						NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
						inboxStyle.setBigContentTitle("MediaScanner details:");
						inboxStyle.addLine("Pre-scan: " + prescanDuration);
						inboxStyle.addLine("Scan: " + scanDuration);
						inboxStyle.addLine("Post-scan: " + postscanDuration);

						String scanDirectoriesDuration = DurationFormatUtils.formatDuration(totalEndTime
								- totalStartTime, "mm:ss");
						NotificationCompat.Builder notificationBuilder = getNotification(context, "MediaScanner "
								+ volumeName, "Scan directories time " + scanDirectoriesDuration);
						notificationBuilder.setStyle(inboxStyle);
						getNotificationManager(context).notify(SCAN_FINISHED_NOTIFICATION + scanFinishedCounter++,
								notificationBuilder.build());
					}

				});

		XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerService",
				loadPackageParam.classLoader, "scanFile", String.class, String.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						String path = (String) methodHookParam.args[0];
						String mimetype = (String) methodHookParam.args[1];

						log(Log.INFO, "scanFile is called for file {0} and mime type {1}", path, mimetype);
					}

				});

		XposedHelpers.findAndHookMethod("com.android.providers.media.MediaScannerReceiver",
				loadPackageParam.classLoader, "onReceive", Context.class, Intent.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						log(Log.INFO, "onReceive is called in MediaScannerReceiver");
						Intent intent = (Intent) methodHookParam.args[1];
						Bundle extras = intent.getExtras();
						deleteMediaStoreContents = extras == null ? false : extras
								.getBoolean("deleteMediaStore", false);
						userInitiatedScan = extras == null ? false : extras.getBoolean("userInitiatedScan", false);
						log(Log.DEBUG, "deleteMediaStore is set to {0} in Intent received", deleteMediaStoreContents);
					}
				});

		XposedHelpers.findAndHookMethod("android.app.Service", loadPackageParam.classLoader, "stopSelf", int.class,
				new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						Object thisObject = methodHookParam.thisObject;
						if (thisObject.getClass().getCanonicalName()
								.equals("com.android.providers.media.MediaScannerService")) {
							log(Log.INFO, "stopSelf is called on MediaScannerService");

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

		XposedBridge.hookAllConstructors(MediaScanner.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
				if (preferences.getBoolean("pref_run_media_scanner_with_background_thread_priority_state", true)) {
					log(Log.INFO, "Changing thread priority in MediaScanner constructor");
					Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
					// + Process.THREAD_PRIORITY_MORE_FAVORABLE);
				}
			}
		});

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "scanDirectories",
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

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "prescan",
				String.class, boolean.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						log(Log.DEBUG, "In android.media.MediaScanner#processDirectory prescan");
						prescanStartTime = System.currentTimeMillis();
					}

					@Override
					protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						prescanEndTime = System.currentTimeMillis();
					}
				});

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "processDirectory",
				String.class, MediaScannerClient.class, new XC_MethodHook() {
					private long start;
					private long end;

					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						log(Log.DEBUG, "In android.media.MediaScanner#processDirectory beforeHookedMethod");
						if (preferences.getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
							String path = (String) methodHookParam.args[0];
							Object field = getField(methodHookParam.thisObject, "mContext");
							if (field instanceof Context) {
								log(Log.INFO, "Updating notification with path {0}", path);
								Context context = (Context) field;
								Notification notification = getNotification(context, "MediaScanner", "Scanning " + path)
										.build();
								getNotificationManager(context).notify(FOREGROUND_NOTIFICATION, notification);
							} else {
								log(Log.INFO, "Could not retrieve context to update notification with path {0}", path);
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

		XposedHelpers.findAndHookMethod("android.media.MediaScanner", loadPackageParam.classLoader, "postscan",
				String[].class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						log(Log.DEBUG, "In android.media.MediaScanner#processDirectory prescan");
						if (preferences.getBoolean("pref_run_media_scanner_as_foreground_service_state", true)) {
							Object field = getField(methodHookParam.thisObject, "mContext");
							if (field instanceof Context) {
								log(Log.INFO, "Updating notification with postscan");
								Context context = (Context) field;
								Notification notification = getNotification(context, "MediaScanner", "Postscan")
										.build();
								getNotificationManager(context).notify(FOREGROUND_NOTIFICATION, notification);
							} else {
								log(Log.INFO, "Could not retrieve context to update notification for postscan");
							}
						}
						postscanStartTime = System.currentTimeMillis();
					}

					@Override
					protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
						postscanEndTime = System.currentTimeMillis();
					}
				});

	}

	protected Object getField(Object instance, String fieldName) {
		try {
			return XposedHelpers.getObjectField(instance, fieldName);
		} catch (NoSuchFieldError e) {
			return null;
		}
	}

	protected NotificationCompat.Builder getNotification(Context context, String title, String details) {
		Builder notificationBuilder = new NotificationCompat.Builder(context);
		return notificationBuilder.setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
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

		MediaFileType mimeTypeForFile = MediaFile.getFileType(file.getAbsolutePath());
		if (mimeTypeForFile != null) {
			for (MediaFileTypeEnum mediaFileType : mediaFileTypeRestrictions) {
				switch (mediaFileType) {
				case music:
					if (MediaFile.isAudioFileType(mimeTypeForFile.fileType)) {
						return TreatAsMediaFileEnum.media_file_true;
					}
					break;
				case video:
					if (MediaFile.isVideoFileType(mimeTypeForFile.fileType)) {
						return TreatAsMediaFileEnum.media_file_true;
					}
					break;
				case picture:
					if (MediaFile.isImageFileType(mimeTypeForFile.fileType)) {
						return TreatAsMediaFileEnum.media_file_true;
					}
					break;
				default:
					break;
				}
			}
		}
		return TreatAsMediaFileEnum.media_file_false;
	}

	private List<MediaFileTypeEnum> getMediaFileTypeRestrictions(File directory) {
		List<MediaFileTypeEnum> mediaFileTypeRestrictions = mediaFilesToConsiderByDirectory.get(directory
				.getAbsolutePath());
		if (mediaFileTypeRestrictions != null) {
			// log(Log.DEBUG,
			// "Media file type marker files for directory {0}: {1}", directory,
			// mediaFileTypeRestrictions);
			return mediaFileTypeRestrictions;
		} else {
			mediaFileTypeRestrictions = new ArrayList<XposedMod.MediaFileTypeEnum>();
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
			// log(Log.DEBUG,
			// "No media file type marker files found in directory {0}",
			// directory);
			File parentDirectory = directory.getParentFile();
			if (parentDirectory != null) {
				mediaFileTypeRestrictions = getMediaFileTypeRestrictions(parentDirectory);
			}
		} else {
			log(Log.DEBUG, "Media file type marker files found in directory {0}: ", directory,
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
					log(Log.DEBUG, "Marker file exists in directory {0}", directory);
					directoriesToScan.add(directory.getAbsolutePath());
				}
				// else {
				// getDirectoriesMarkedForScanning(directoriesToScan,
				// directory);
				// }
			}
		}
	}

	private void log(int level, String message, Object... params) {
		String string = MessageFormat.format(message, params);
		switch (level) {
		case Log.DEBUG:
			Log.d(TAG, string);
			break;
		case Log.ERROR:
			Log.e(TAG, string);
			break;
		case Log.INFO:
			Log.i(TAG, string);
			break;
		case Log.VERBOSE:
			Log.v(TAG, string);
			break;
		case Log.WARN:
			Log.w(TAG, string);
			break;
		}
	}

	private void deleteMediaStoreContent(Context context) throws Exception {
		IContentProvider provider = context.getContentResolver().acquireProvider("media");

		android.net.Uri.Builder builder = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon();
		Uri uri = builder.appendQueryParameter("deletedata", "false").build();
		log(Log.DEBUG, "Modified MediaStore.Audio.Media.EXTERNAL_CONTENT_URI is {0}", uri.toString());
		int deleted = provider.delete(uri, null, null);
		log(Log.INFO, "Deleted {0} rows from External Audio Content", deleted);

		builder = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon();
		uri = builder.appendQueryParameter("deletedata", "false").build();
		log(Log.DEBUG, "Modified MediaStore.Images.Media.EXTERNAL_CONTENT_URI is {0}", uri.toString());
		deleted = provider.delete(uri, null, null);
		log(Log.INFO, "Deleted {0} rows from External Image Content", deleted);

		builder = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon();
		uri = builder.appendQueryParameter("deletedata", "false").build();
		log(Log.DEBUG, "Modified MediaStore.Video.Media.EXTERNAL_CONTENT_URI is {0}", uri.toString());
		deleted = provider.delete(uri, null, null);
		log(Log.INFO, "Deleted {0} rows from External Video Content", deleted);
	}

	private void triggerMediaScanner(Context context) {
		Intent intent = new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://"
				+ Environment.getExternalStorageDirectory()));
		intent.setPackage(PACKAGE);
		context.sendBroadcast(intent);
	}

	private void displayToast(final Context context, final String text) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, text, Toast.LENGTH_LONG).show();
			}
		});
	}
}
