package com.mrboomdev.scrollix.data.download;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.app.MainActivity;
import com.mrboomdev.scrollix.app.NotificationManager;
import com.mrboomdev.scrollix.app.permission.Permission;
import com.mrboomdev.scrollix.app.permission.PermissionManager;
import com.mrboomdev.scrollix.util.exception.DownloadException;

public class UserMadeDownload extends Download {
	private PendingIntent openIntent, cancelIntent;
	private NotificationCompat.Builder notification;
	private boolean hasNotificationPermission;
	private int notificationId;
	private long didDownloaded;
	private boolean didFinished;

	public UserMadeDownload(String url) {
		super(url);
	}

	private void createIntents() {
		var context = AppManager.getActivityContext();

		int id = getId();
		var flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

		var openIntent = new Intent(context, MainActivity.class);
		openIntent.putExtra("type", "open_download");
		openIntent.putExtra("id", id);
		this.openIntent = PendingIntent.getActivity(context, 0, openIntent, flags);

		var cancelIntent = new Intent(context, MainActivity.class);
		cancelIntent.putExtra("type", "cancel_download");
		cancelIntent.putExtra("id", id);
		this.cancelIntent = PendingIntent.getActivity(context, 1, cancelIntent, flags);
	}

	@Override
	public void onSucceed() {
		if(!hasNotificationPermission) return;
		done(true);
	}

	@Override
	public void onFailed(@NonNull DownloadException e) {
		e.printStackTrace();

		if(!hasNotificationPermission) return;
		done(false);
	}

	@Override
	public void onFinish() {
		didFinished = true;

		var context = AppManager.getActivityContext();
		var notificationManager = NotificationManagerCompat.from(context);
		notificationManager.cancel(notificationId);
	}

	@SuppressLint("MissingPermission")
	private void done(boolean isOk) {
		if(didFinished) return;

		var context = AppManager.getActivityContext();
		var notificationManager = NotificationManagerCompat.from(context);

		createIntents();

		var channel = isOk
				? NotificationManager.NotificationChannel.DOWNLOAD_FINISHED
				: NotificationManager.NotificationChannel.DOWNLOAD_ERROR;

		notification = new NotificationCompat.Builder(AppManager.getActivityContext(), channel.getAndroidId())
				.setContentTitle((isOk ? "Successfully downloaded \"" : "Failed to download \"") + getFile().getName() + "\"")
				.setSmallIcon(android.R.drawable.stat_sys_download_done)
				.setContentIntent(openIntent)
				.setAutoCancel(true);

		notificationManager.notify(notificationId, notification.build());
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onProgress(int bytesRead) {
		if(!hasNotificationPermission || didFinished) return;
		didDownloaded += bytesRead;

		var context = AppManager.getActivityContext();
		var notificationManager = NotificationManagerCompat.from(context);

		notification.setContentTitle("Downloading \"" + getFile().getName() + "\"");

		if(getContentSize() != -1) {
			int progress = (int)(didDownloaded * (100 / (double)getContentSize()));
			notification.setProgress(100, progress, false);
		}

		notificationManager.notify(notificationId, notification.build());
	}

	public void start() {
		PermissionManager.checkAndRequestPermission(Permission.NOTIFICATION, isSuccess -> {
			this.hasNotificationPermission = isSuccess;
			super.start();

			if(!hasNotificationPermission) return;
			notificationId = NotificationManager.getUniqueNotificationId();
			createIntents();

			var context = AppManager.getActivityContext();
			var notificationManager = NotificationManagerCompat.from(context);
			var channel = NotificationManager.NotificationChannel.DOWNLOAD_PROGRESS;

			notification = new NotificationCompat.Builder(context, channel.getAndroidId())
					.setContentTitle("Starting download of \"" + getFile().getName() + "\"")
					.setProgress(0, 0, true)
					.addAction(R.mipmap.ic_launcher, "Cancel", cancelIntent)
					.setSmallIcon(android.R.drawable.stat_sys_download)
					.setContentIntent(openIntent);

			notificationManager.notify(notificationId, notification.build());
		});
	}
}