package com.mrboomdev.scrollix.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;

import com.mrboomdev.scrollix.app.AppManager;
public class AndroidUtil {

	public static void copyToClipboard(String text) {
		var context = AppManager.getAppContext();
		var manager = context.getSystemService(ClipboardManager.class);

		var clipData = ClipData.newPlainText(text, text);
		manager.setPrimaryClip(clipData);
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