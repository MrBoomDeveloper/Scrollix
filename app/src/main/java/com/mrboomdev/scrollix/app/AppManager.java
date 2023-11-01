package com.mrboomdev.scrollix.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mrboomdev.scrollix.data.tabs.TabsManager;

import java.util.ArrayList;
import java.util.List;

public class AppManager {
	private static final String TAG = "AppManager";
	private static ActivityCallbackLauncher activityCallbackLauncher;

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

	public static void checkAndRequestPermission(@NonNull Context context, @NonNull Permission permission, Runnable success, Runnable failure) {
		checkAndRequestPermission(context, permission, success, failure, true);
	}

	private static void checkAndRequestPermission(@NonNull Context context, @NonNull Permission permission, Runnable success, Runnable failure, boolean retry) {
		var activity = ((AppCompatActivity)context);

		switch(permission) {
			case NOTIFICATION -> {
				if(Build.VERSION.SDK_INT < 33) {
					success.run();
					return;
				}

				var status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS);

				if(status == PackageManager.PERMISSION_GRANTED) {
					success.run();
					return;
				}

				activityCallbackLauncher.launchPermission(Manifest.permission.POST_NOTIFICATIONS, isSuccess -> {
					if(isSuccess)
						success.run();
					else
						failure.run();
				});
			}

			case STORAGE -> {
				var requiredPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
					? Manifest.permission.MANAGE_EXTERNAL_STORAGE
					: Manifest.permission.WRITE_EXTERNAL_STORAGE;

				var status = ContextCompat.checkSelfPermission(context, requiredPermission);

				if(status == PackageManager.PERMISSION_GRANTED) {
					success.run();
					return;
				}

				if(!retry) {
					failure.run();
					return;
				}

				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					var uri = Uri.fromParts("package", context.getPackageName(), null);
					var intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, uri);

					activityCallbackLauncher.launchIntent(intent,
							() -> checkAndRequestPermission(context, permission, success, failure, false));

					return;
				}

				activityCallbackLauncher.launchPermission(requiredPermission, isSuccess -> {
					if(isSuccess)
						success.run();
					else
						failure.run();
				});
			}
		}
	}

	public enum Permission {
		NOTIFICATION,
		STORAGE
	}

	public static void startup(AppCompatActivity context) {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			registerNotificationChannels(context);
		} else {
			Log.i(TAG, "Skipping notification channels registration, because device sdk version is lower than required.");
		}

		activityCallbackLauncher = new ActivityCallbackLauncher(context);
	}

	public static void dispose() {
		activityCallbackLauncher = null;
		TabsManager.tabs.clear();
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

		NotificationChannel(String id, String name, String description, int importance) {
			this(id, name, description, importance, null);
		}

		NotificationChannel(String id, String name, int importance, NotificationChannelGroup group) {
			this(id, name, null, importance, group);
		}

		NotificationChannel(String id, String name, int importance) {
			this(id, name, null, importance, null);
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

		public interface ResultCallback {
			void run(boolean isSuccess);
		}
	}
}