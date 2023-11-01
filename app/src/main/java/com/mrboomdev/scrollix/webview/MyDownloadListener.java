package com.mrboomdev.scrollix.webview;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.webkit.DownloadListener;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo;
import com.liulishuo.okdownload.core.cause.EndCause;
import com.liulishuo.okdownload.core.cause.ResumeFailedCause;
import com.mrboomdev.scrollix.app.AppManager;

import java.util.List;
import java.util.Map;

public class MyDownloadListener implements DownloadListener {
	private static final String TAG = "MyDownloadListener";
	private final Context context;

	public MyDownloadListener(Context context) {
		this.context = context;
	}

	@Override
	public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
		Log.i(TAG, "Download: " + url
				+ " ; " + userAgent
				+ " ; " + contentDisposition
				+ " ; " + mimetype
				+ " ; " + contentLength);

		var directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
		if(directory == null) directory = context.getDir("downloads", 0);

		var task = new DownloadTask.Builder(url, directory)
				.setMinIntervalMillisCallbackProcess(30)
				.setPassIfAlreadyCompleted(false)
				.build();

		AppManager.checkAndRequestPermission(context, AppManager.Permission.NOTIFICATION,
				() -> task.enqueue(new ProgressListener(true)),
				() -> task.enqueue(new ProgressListener(false)));
	}

	private class ProgressListener implements com.liulishuo.okdownload.DownloadListener {
		private static final String TAG = "ProgressListener";
		private boolean createNotifications;

		public ProgressListener(boolean createNotifications) {
			this.createNotifications = createNotifications;
		}

		@Override
		public void taskStart(@NonNull DownloadTask task) {
			Log.i(TAG, "Start: " + task.getUrl());

			var a = new NotificationCompat.Builder(context, "");
		}

		@Override
		public void connectTrialStart(@NonNull DownloadTask task, @NonNull Map<String, List<String>> requestHeaderFields) {
			Log.i(TAG, "ConnectTrialStart : " + task.getUrl());
		}

		@Override
		public void connectTrialEnd(@NonNull DownloadTask task, int responseCode, @NonNull Map<String, List<String>> responseHeaderFields) {
			Log.i(TAG, "ConnectTrialEnd : " + task.getUrl() + " ; " + responseCode);
		}

		@Override
		public void downloadFromBeginning(@NonNull DownloadTask task, @NonNull BreakpointInfo info, @NonNull ResumeFailedCause cause) {
			Log.i(TAG, "DownloadFromBeginning : " + task.getUrl() + " ; " + cause.name());
		}

		@Override
		public void downloadFromBreakpoint(@NonNull DownloadTask task, @NonNull BreakpointInfo info) {
			Log.i(TAG, "DownloadFromBreakpoint : " + task.getUrl());
		}

		@Override
		public void connectStart(@NonNull DownloadTask task, int blockIndex, @NonNull Map<String, List<String>> requestHeaderFields) {
			Log.i(TAG, "ConnectStart : " + task.getUrl() + " ; " + blockIndex);
		}

		@Override
		public void connectEnd(@NonNull DownloadTask task, int blockIndex, int responseCode, @NonNull Map<String, List<String>> responseHeaderFields) {
			Log.i(TAG, "ConnectEnd : " + task.getUrl() + " ; " + blockIndex + " ; " + responseCode);
		}

		@Override
		public void fetchStart(@NonNull DownloadTask task, int blockIndex, long contentLength) {
			Log.i(TAG, "FetchStart : " + task.getUrl() + " ; " + blockIndex + " ; ");
		}

		@Override
		public void fetchProgress(@NonNull DownloadTask task, int blockIndex, long increaseBytes) {
			Log.i(TAG, "FetchProgress : " + task.getUrl() + " ; " + blockIndex + " ; " + increaseBytes);
		}

		@Override
		public void fetchEnd(@NonNull DownloadTask task, int blockIndex, long contentLength) {
			Log.i(TAG, "FetchEnd : " + task.getUrl() + " ; " + blockIndex + " ; " + contentLength);
		}

		@Override
		public void taskEnd(@NonNull DownloadTask task, @NonNull EndCause cause, Exception realCause) {
			Log.i(TAG, "TaskEnd : " + task.getUrl() + " ; " + cause.name());
			Log.e(TAG, Log.getStackTraceString(realCause));
		}
	}
}