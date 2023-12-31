package com.mrboomdev.scrollix.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.data.download.DownloadManager;
import com.mrboomdev.scrollix.data.download.UserMadeDownload;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.AppUi;
import com.mrboomdev.scrollix.ui.MainActivity;
import com.mrboomdev.scrollix.ui.popup.dialog.DialogMenu;
import com.mrboomdev.scrollix.util.AppUtils;

import java.net.URISyntaxException;

public class IntentHandler {

	public static void handleIntent(@Nullable Intent intent) {
		if(intent == null) return;

		var url = intent.getDataString();
		if(url != null) {
			TabStore.createTab(url, true);
			return;
		}

		var type = intent.getStringExtra("type");
		if(type == null) return;

		handleCustomIntent(type, intent);
	}

	private static void handleCustomIntent(@NonNull String type, Intent intent) {
		var context = AppManager.getMainActivityContext();

		switch(type) {
			case "update_theme" -> {
				var newIntent = new Intent(context, MainActivity.class);
				context.startActivity(newIntent);
				context.finish();
			}

			case "cancel_download" -> {
				int id = intent.getIntExtra("id", 0);
				DownloadManager.cancel(id);
			}

			case "open_search" -> {
				var timeoutCallback = new Runnable() {
					@Override
					public void run() {
						if(AppUi.searchLayout != null) {
							AppUi.searchLayout.show();
							AppUi.searchLayout.editText.setText("");
							return;
						}

						AppUtils.setTimeout(this, 100);
					}
				};

				timeoutCallback.run();
			}

			case "open_download" -> openDownload(intent.getIntExtra("id", 0));
		}

		intent.putExtra("type", "");
	}

	private static void openDownload(int id) {
		var content = AppManager.getActivityContext();
		var download = DownloadManager.getDownloadById(id);
		if(download == null) return;

		if(download.getException() == null) {
			new DialogMenu(content)
					.setTitle("Currently unavailable")
					.setDescription("Please open a \"/sdcard/Downloads/\" director by your hands in your File Manager.")
					.addAction("Ok")
					.show();

			return;
		}

		var description = new StringBuilder();
		var exception = download.getException();

		if(exception.getMessage() != null) {
			description.append("Custom message: ").append(exception.getMessage()).append("\"");
		}

		if(exception.getResponseCode() != -1) {
			description.append("Response code: ").append(exception.getResponseCode()).append("\"");
		}

		if(exception.getResponseMessage() != null) {
			description.append("Response message: ").append(exception.getResponseMessage()).append("\"");
		}

		new DialogMenu(content)
				.setTitle("Failed to download a file!")
				.setDescription(description.toString())
				.addAction("Retry", () -> new UserMadeDownload(download.getUrl().toString()).start())
				.addAction("Ok")
				.show();
	}

	public static void launchInExternal(String uri) {
		var context = AppManager.getActivityContext();

		try {
			var intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);

			if(intent == null) {
				Toast.makeText(context, "Invalid app link", Toast.LENGTH_LONG).show();
				return;
			}

			var packageManager = context.getPackageManager();
			var info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

			if(info != null) {
				try {
					context.startActivity(intent);
				} catch(ActivityNotFoundException e) {
					Toast.makeText(context, "No apps were found to handle action", Toast.LENGTH_LONG).show();
				}
			}
		} catch(URISyntaxException e) {
			e.printStackTrace();
			Toast.makeText(context, "Invalid app link", Toast.LENGTH_LONG).show();
		}
	}
}