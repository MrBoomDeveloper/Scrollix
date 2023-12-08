package com.mrboomdev.scrollix.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.widget.Toast;

import com.mrboomdev.scrollix.app.AppManager;

public class AppUtils {

	public static Resources getResources() {
		return AppManager.getActivityContext().getResources();
	}

	public static void runOnUiThread(Runnable runnable) {
		AppManager.getActivityContext().runOnUiThread(runnable);
	}

	public static Configuration getConfiguration() {
		return AppManager.getAppContext().getResources().getConfiguration();
	}

	public static boolean isLandscape() {
		return getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}

	public static void copyToClipboard(String text) {
		var context = AppManager.getAppContext();
		var manager = context.getSystemService(ClipboardManager.class);

		var clipData = ClipData.newPlainText(text, text);
		manager.setPrimaryClip(clipData);
	}

	public static void toast(String text, boolean isLong) {
		var context = AppManager.getActivityContext();

		context.runOnUiThread(() -> {
			var length = isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
			Toast.makeText(context, text, length).show();
		});
	}

	public static void setTimeout(Runnable runnable, long duration) {
		new Handler().postDelayed(runnable, duration);
	}

	public static void share(String title, String text) {
		var context = AppManager.getAppContext();
		var intent = new Intent(Intent.ACTION_SEND);

		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, title);
		intent.putExtra(Intent.EXTRA_TEXT, text);

		var chooser = Intent.createChooser(intent, title);
		chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		context.startActivity(chooser);
	}
}