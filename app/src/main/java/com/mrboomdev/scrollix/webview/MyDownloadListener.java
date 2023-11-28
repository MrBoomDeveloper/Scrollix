package com.mrboomdev.scrollix.webview;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.webkit.DownloadListener;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo;
import com.liulishuo.okdownload.core.cause.EndCause;
import com.liulishuo.okdownload.core.cause.ResumeFailedCause;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.MainActivity;
import com.mrboomdev.scrollix.app.NotificationManager;
import com.mrboomdev.scrollix.app.permission.Permission;
import com.mrboomdev.scrollix.app.permission.PermissionManager;
import com.mrboomdev.scrollix.util.LinkUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Deprecated
public class MyDownloadListener implements DownloadListener {
	private static final String TAG = "MyDownloadListener";
	private final Context context;

	public MyDownloadListener(Context context) {
		this.context = context;
	}

	@Override
	public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
		Log.i(TAG, "Download: { "
					+ " \"UserAgent\": " + userAgent + " ; "
					+ " \"ContentDisposition\": " + contentDisposition + " ; "
					+ " \"MimeType\": " + mimetype + " ; "
					+ " \"ContentLength\": " + contentLength + " }");

		var directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		var name = LinkUtil.generateFileName(url, contentDisposition);

		var task = new DownloadTask.Builder(url, directory.getAbsolutePath(), name)
				.setMinIntervalMillisCallbackProcess(30)
				.setPassIfAlreadyCompleted(false)
				.build();

		PermissionManager.checkAndRequestPermission(Permission.NOTIFICATION, isSuccess -> {
			var listener = new ProgressListener(context, isSuccess, name);
			listener.setTotalSize(contentLength);
			task.enqueue(listener);
		});
	}

	@SuppressLint("MissingPermission")
	public static class ProgressListener implements com.liulishuo.okdownload.DownloadListener {
		public static final Map<Integer, ProgressListener> activeDownloads = new HashMap<>();
		private final PendingIntent intent;
		private static final String TAG = "ProgressListener";
		private final boolean createNotifications;
		private static final List<Integer> usedIds = new ArrayList<>();
		private static final Random random = new Random();
		private long totalSize, currentSize;
		private final String name;
		private final AtomicBoolean didFinished = new AtomicBoolean(false);
		private NotificationCompat.Builder progressNotification;
		private final int progressNotificationId = getUniqueNotificationId();
		private final Context context;
		private DownloadTask task;

		public ProgressListener(Context context, boolean createNotifications, String name) {
			this.createNotifications = createNotifications;
			this.name = name;
			this.context = context;

			activeDownloads.put(progressNotificationId, this);

			if(!createNotifications) {
				intent = null;
				return;
			}

			var intent = new Intent(context, MainActivity.class);
			this.intent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
		}

		public void cancel() {
			DownloadTask.cancel(new DownloadTask[]{ task });
		}

		public static void cancel(int id) {
			var listener = activeDownloads.get(id);
			if(listener == null) return;

			listener.cancel();
		}

		public void setTotalSize(long size) {
			this.totalSize = size;
		}

		@Override
		public void taskStart(@NonNull DownloadTask task) {
			this.task = task;
			Log.i(TAG, "Start: " + name);

			if(!createNotifications) return;

			var cancelIntent = new Intent(context, MainActivity.class);
			cancelIntent.putExtra("type", "cancel_download");
			cancelIntent.putExtra("id", progressNotificationId);
			var cancelIntentPending = PendingIntent.getActivity(context, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

			progressNotification = new NotificationCompat.Builder(context, NotificationManager.NotificationChannel.DOWNLOAD_PROGRESS.getAndroidId())
					.setContentTitle("Starting download of \"" + name + "\"")
					.setProgress(0, 0, true)
					.addAction(R.mipmap.ic_launcher, "Cancel", cancelIntentPending)
					.setSmallIcon(android.R.drawable.stat_sys_download)
					.setContentIntent(intent);

			var manager = NotificationManagerCompat.from(context);
			manager.notify(progressNotificationId, progressNotification.build());
		}

		@Override
		public void connectTrialStart(@NonNull DownloadTask task, @NonNull Map<String, List<String>> requestHeaderFields) {
			Log.i(TAG, "ConnectTrialStart : " + name);
		}

		@Override
		public void connectTrialEnd(@NonNull DownloadTask task, int responseCode, @NonNull Map<String, List<String>> responseHeaderFields) {
			Log.i(TAG, "ConnectTrialEnd : " + name + " ; Response code: " + responseCode);
		}

		@Override
		public void downloadFromBeginning(@NonNull DownloadTask task, @NonNull BreakpointInfo info, @NonNull ResumeFailedCause cause) {
			Log.i(TAG, "DownloadFromBeginning : " + name + " ; Cause: " + cause.name());
		}

		@Override
		public void downloadFromBreakpoint(@NonNull DownloadTask task, @NonNull BreakpointInfo info) {
			Log.i(TAG, "DownloadFromBreakpoint : " + name);
		}

		@Override
		public void connectStart(@NonNull DownloadTask task, int blockIndex, @NonNull Map<String, List<String>> requestHeaderFields) {
			Log.i(TAG, "ConnectStart : " + name + " ; BlockIndex: " + blockIndex);
		}

		@Override
		public void connectEnd(@NonNull DownloadTask task, int blockIndex, int responseCode, @NonNull Map<String, List<String>> responseHeaderFields) {
			Log.i(TAG, "ConnectEnd : " + name + " ; BlockIndex: " + blockIndex + " ; ResponseCode:  " + responseCode);
		}

		@Override
		public void fetchStart(@NonNull DownloadTask task, int blockIndex, long contentLength) {
			Log.i(TAG, "FetchStart : " + name + " ; BlockIndex " + blockIndex + " ; ContentLength: " + contentLength);

			if(!createNotifications) return;

			progressNotification.setContentTitle("Downloading \"" + name + "\"")
					.setProgress((int)totalSize, 0, false);

			var manager = NotificationManagerCompat.from(context);
			manager.notify(progressNotificationId, progressNotification.build());
		}

		@Override
		public void fetchProgress(@NonNull DownloadTask task, int blockIndex, long increaseBytes) {
			Log.i(TAG, "FetchProgress : " + task.getUrl() + " ; " + blockIndex + " ; " + increaseBytes);

			if(!createNotifications || didFinished.get()) return;

			currentSize += increaseBytes;
			progressNotification.setProgress((int)totalSize, (int)currentSize, false);

			var manager = NotificationManagerCompat.from(context);
			manager.notify(progressNotificationId, progressNotification.build());
		}

		@Override
		public void fetchEnd(@NonNull DownloadTask task, int blockIndex, long contentLength) {
			Log.i(TAG, "FetchEnd : " + task.getUrl() + " ; " + blockIndex + " ; " + contentLength);
			success();
		}

		@Override
		public void taskEnd(@NonNull DownloadTask task, @NonNull EndCause cause, Exception realCause) {
			Log.i(TAG, "TaskEnd : " + task.getUrl() + " ; " + cause.name());

			if(realCause != null) {
				Log.e(TAG, Log.getStackTraceString(realCause));
			}

			switch(cause) {
				case COMPLETED -> success();
				case ERROR -> error("Unknown error", Log.getStackTraceString(realCause));
				case FILE_BUSY -> error("File is busy", Log.getStackTraceString(realCause));
				case SAME_TASK_BUSY -> error("Same task is busy", Log.getStackTraceString(realCause));
				case PRE_ALLOCATE_FAILED -> error("Pre allocate did failed", Log.getStackTraceString(realCause));
				case CANCELED -> done();
			}
		}

		private static int getUniqueNotificationId() {
			boolean contains = true;
			int id = 0;

			while(contains) {
				id = random.nextInt(Integer.MAX_VALUE);
				contains = usedIds.contains(id);
			}

			usedIds.add(id);

			return id;
		}

		private void error(String title, String message) {
			title = "Failed to download a file! " + title;

			var intent = new Intent(context, MainActivity.class);
			intent.putExtra("title", title);
			intent.putExtra("message", message);
			intent.putExtra("type", "error");
			var pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);

			done(title, message, false, pendingIntent);
		}

		private void success() {
			var intent = new Intent(context, MainActivity.class);
			var pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

			done("Finished \"" + name + "\" successfully!", null, true, pendingIntent);
		}

		private void done() {
			done(null, null, true, null);
		}

		private void done(String title, String message, boolean isOk, PendingIntent intent) {
			Log.d(TAG, "Done: " + name);
			activeDownloads.remove(progressNotificationId);

			if(!createNotifications || didFinished.getAndSet(true)) return;

			var manager = NotificationManagerCompat.from(context);
			manager.cancel(progressNotificationId);

			if(message != null) {
				Log.i(TAG, "Done with message: " + message);
			}

			if(title == null) return;

			var channel = isOk
					? NotificationManager.NotificationChannel.DOWNLOAD_FINISHED
					: NotificationManager.NotificationChannel.DOWNLOAD_ERROR;

			var notification = new NotificationCompat.Builder(context, channel.getAndroidId())
					.setContentTitle(title)
					.setContentText(message)
					.setSmallIcon(android.R.drawable.stat_sys_download_done)
					.setContentIntent(intent)
					.setAutoCancel(true);

			manager.notify(getUniqueNotificationId(), notification.build());
		}
	}
}