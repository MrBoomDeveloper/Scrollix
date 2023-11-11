package com.mrboomdev.scrollix.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.DynamicColors;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.webview.MyDownloadListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AppManager {
	private static final String TAG = "AppManager";
	private static ActivityCallbackLauncher activityCallbackLauncher;
	private static Method getContextMethod;

	@RequiresApi(api = Build.VERSION_CODES.O)
	@SuppressLint("WrongConstant")
	public static void registerNotificationChannels(@NonNull Context context) {
		var manager = context.getSystemService(NotificationManager.class);
		var createdGroups = new ArrayList<NotificationChannelGroup>();

		for(var channel : List.of(
				NotificationChannel.BROWSER_UPDATE_AVAILABLE,
				NotificationChannel.DOWNLOAD_PROGRESS,
				NotificationChannel.DOWNLOAD_FINISHED,
				NotificationChannel.DOWNLOAD_ERROR)
		) {
			var androidChannel = new android.app.NotificationChannel(channel.getAndroidId(), channel.getAndroidName(), channel.getAndroidImportance());
			androidChannel.setDescription(channel.getAndroidDescription());

			var group = channel.getAndroidGroup();
			androidChannel.setGroup(group.getAndroidId());

			if(!createdGroups.contains(group)) {
				createdGroups.add(group);

				var androidGroup = new android.app.NotificationChannelGroup(group.getAndroidId(), group.getAndroidName());
				manager.createNotificationChannelGroup(androidGroup);
			}

			manager.createNotificationChannel(androidChannel);
		}
	}

	public static void checkAndRequestPermission(@NonNull Context context, @NonNull Permission permission, ResultCallback callback) {
		checkAndRequestPermission(context, permission, callback, true);
	}

	private static void checkAndRequestPermission(@NonNull Context context, @NonNull Permission permission, ResultCallback callback, boolean retry) {
		switch(permission) {
			case NOTIFICATION -> {
				if(Build.VERSION.SDK_INT < 33) {
					callback.run(true);
					return;
				}

				var status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS);

				if(status == PackageManager.PERMISSION_GRANTED) {
					callback.run(true);
					return;
				}

				activityCallbackLauncher.launchPermission(Manifest.permission.POST_NOTIFICATIONS, callback);
			}

			case STORAGE -> {
				var requiredPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
					? Manifest.permission.MANAGE_EXTERNAL_STORAGE
					: Manifest.permission.WRITE_EXTERNAL_STORAGE;

				var status = ContextCompat.checkSelfPermission(context, requiredPermission);

				if(status == PackageManager.PERMISSION_GRANTED) {
					callback.run(true);
					return;
				}

				if(!retry) {
					callback.run(false);
					return;
				}

				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					var uri = Uri.fromParts("package", context.getPackageName(), null);
					var intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, uri);

					activityCallbackLauncher.launchIntent(intent,
							() -> checkAndRequestPermission(context, permission, callback, false));

					return;
				}

				activityCallbackLauncher.launchPermission(requiredPermission, callback);
			}
		}
	}

	public enum Permission {
		NOTIFICATION,
		STORAGE
	}

	@SuppressLint("PrivateApi")
	public static Context getAppContext() {
		try {
			if(getContextMethod == null) {
				var globals = Class.forName("android.app.AppGlobals");
				getContextMethod = globals.getMethod("getInitialApplication");
			}

			return (Context)getContextMethod.invoke(null);
		} catch(ClassNotFoundException e) {
			throw new RuntimeException("Cannot find a AppGlobals class!", e);
		} catch(NoSuchMethodException e) {
			throw new RuntimeException("Cannot find a getInitialApplication method!", e);
		} catch(InvocationTargetException e) {
			throw new RuntimeException("Failed to invoke a system api!", e);
		} catch(IllegalAccessException e) {
			throw new RuntimeException("Forbidden access for system api!", e);
		}
	}

	public static void startup(AppCompatActivity context) {
		if(DynamicColors.isDynamicColorAvailable()) {
			DynamicColors.applyToActivitiesIfAvailable(context.getApplication());
		}

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			registerNotificationChannels(context);
		} else {
			Log.i(TAG, "Skipping notification channels registration, because device sdk version is lower than required.");
		}

		if(context instanceof IncognitoActivity) {
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				WebView.setDataDirectorySuffix("incognito");
			} else {
				Toast.makeText(context, "Incognito mode isn't supported on your device", Toast.LENGTH_LONG).show();
			}
		}

		activityCallbackLauncher = new ActivityCallbackLauncher(context);
		ThemeSettings.ThemeManager.setContext(getAppContext());
	}

	public static void dispose() {
		activityCallbackLauncher = null;
		TabsManager.tabs.clear();
		ThemeSettings.ThemeManager.setContext(null);

		for(var download : MyDownloadListener.ProgressListener.activeDownloads.values()) {
			download.cancel();
		}
	}

	public static Configuration getConfiguration() {
		return getAppContext().getResources().getConfiguration();
	}

	public enum NotificationChannelGroup {
		DOWNLOAD("download", "Download"),
		UPDATE("update", "Update");

		private final String id, name;

		NotificationChannelGroup(String id, String name) {
			this.id = id;
			this.name = name;
		}

		public String getAndroidId() {
			return id;
		}

		public String getAndroidName() {
			return name;
		}
	}

	public enum NotificationChannel {
		DOWNLOAD_PROGRESS("download_progress", "Download progress",
				NotificationManager.IMPORTANCE_LOW, NotificationChannelGroup.DOWNLOAD),
		BROWSER_UPDATE_AVAILABLE("browser_update_available", "Scrollix update available",
				"New update means new features, bug fixes and more!",
				NotificationManager.IMPORTANCE_DEFAULT, NotificationChannelGroup.UPDATE),
		DOWNLOAD_FINISHED("download_finished", "Download finished",
				NotificationManager.IMPORTANCE_HIGH, NotificationChannelGroup.DOWNLOAD),
		DOWNLOAD_ERROR("download_error", "Download error",
				NotificationManager.IMPORTANCE_HIGH, NotificationChannelGroup.DOWNLOAD);

		private final NotificationChannelGroup group;
		private final String id, name, description;
		private final int importance;

		NotificationChannel(String id, String name, String description, int importance, NotificationChannelGroup group) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.importance = importance;
			this.group = group;
		}

		NotificationChannel(String id, String name, int importance, NotificationChannelGroup group) {
			this(id, name, null, importance, group);
		}

		public String getAndroidId() {
			return id;
		}

		public NotificationChannelGroup getAndroidGroup() {
			return group;
		}

		public String getAndroidName() {
			return name;
		}

		public String getAndroidDescription() {
			return description;
		}

		public int getAndroidImportance() {
			return importance;
		}
	}

	private static class ActivityCallbackLauncher {
		private final ActivityResultLauncher<String> permissionLauncher;
		private final ActivityResultLauncher<Intent> intentLauncher;
		private ResultCallback permissionCallback;
		private Runnable intentCallback;

		public ActivityCallbackLauncher(@NonNull AppCompatActivity activity) {
			permissionLauncher = activity.registerForActivityResult(
					new ActivityResultContracts.RequestPermission(),
					isGranted -> getPermissionCallback().run(isGranted));

			intentLauncher = activity.registerForActivityResult(
					new ActivityResultContracts.StartActivityForResult(),
					result -> getIntentCallback().run());
		}

		public ResultCallback getPermissionCallback() {
			return permissionCallback;
		}

		public Runnable getIntentCallback() {
			return intentCallback;
		}

		public void launchPermission(String input, ResultCallback callback) {
			this.permissionCallback = callback;
			permissionLauncher.launch(input);
		}

		public void launchIntent(Intent input, Runnable callback) {
			this.intentCallback = callback;
			intentLauncher.launch(input);
		}
	}

	public interface ResultCallback {
		void run(boolean isSuccess);
	}
}