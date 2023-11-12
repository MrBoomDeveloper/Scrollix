package com.mrboomdev.scrollix.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
public class NotificationManager {

	@RequiresApi(api = Build.VERSION_CODES.O)
	@SuppressLint("WrongConstant")
	public static void registerNotificationChannels(@NonNull Context context) {
		var manager = context.getSystemService(android.app.NotificationManager.class);
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
				android.app.NotificationManager.IMPORTANCE_LOW, NotificationChannelGroup.DOWNLOAD),
		BROWSER_UPDATE_AVAILABLE("browser_update_available", "Scrollix update available",
				"New update means new features, bug fixes and more!",
				android.app.NotificationManager.IMPORTANCE_DEFAULT, NotificationChannelGroup.UPDATE),
		DOWNLOAD_FINISHED("download_finished", "Download finished",
				android.app.NotificationManager.IMPORTANCE_HIGH, NotificationChannelGroup.DOWNLOAD),
		DOWNLOAD_ERROR("download_error", "Download error",
				android.app.NotificationManager.IMPORTANCE_HIGH, NotificationChannelGroup.DOWNLOAD);

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
}