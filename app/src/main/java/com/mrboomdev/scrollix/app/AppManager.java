package com.mrboomdev.scrollix.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.DataProfile;
import com.mrboomdev.scrollix.data.settings.AppSettings;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.webview.MyDownloadListener;

import java.util.Map;

public class AppManager {
	public static String profileName = "Default";
	public static AppSettings settings;
	private static DataProfile profile;
	private static final String TAG = "AppManager";
	public static ActivityCallbackLauncher activityCallbackLauncher;
	private static AppCompatActivity activityContext;

	public static Context getAppContext() {
		return activityContext.getApplicationContext();
	}

	public static AppCompatActivity getActivityContext() {
		return activityContext;
	}

	public static void postCreate() {
		TabsManager.setCurrent(TabsManager.get(profile.currentTab()));

		var intent = getActivityContext().getIntent();
		var extra = intent.getDataString();

		if(extra != null) {
			TabsManager.create(extra);
			intent.setData(null);
		}
	}

	public static void useCrashHandler() {
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			var context = getActivityContext();

			context.runOnUiThread(() -> {
				new MaterialAlertDialogBuilder(context)
						.setTitle("App just crashed!")
						.setMessage("Stacktrace: \n\n" + Log.getStackTraceString(throwable))
						.setPositiveButton("Exit app", (_dialog, _button) -> {
							context.finishAffinity();
							_dialog.cancel();
						})
						.show();
			});
		});
	}

	public static void startup(AppCompatActivity context) {
		useCrashHandler();
		activityContext = context;

		if(DynamicColors.isDynamicColorAvailable()) {
			DynamicColors.applyToActivitiesIfAvailable(context.getApplication());
		}

		boolean isIncognito = context instanceof IncognitoActivity;

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isIncognito) {
			profileName = "Incognito";
			WebView.setDataDirectorySuffix(profileName);
		} else if(isIncognito) {
			Toast.makeText(context, R.string.error_incognito_unavailable, Toast.LENGTH_LONG).show();
		}

		restoreState();
		activityCallbackLauncher = new ActivityCallbackLauncher(context);
		ThemeSettings.ThemeManager.setContext(getAppContext());

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager.registerNotificationChannels(context);
		} else {
			Log.i(TAG, "Skipping notification channels registration, because device sdk version is lower than required.");
		}
	}

	public static void saveState() {
		var profile = new DataProfile(
				TabsManager.tabs,
				TabsManager.getCurrentIndex(),
				settings);

		profile.save(profileName);
	}

	public static void restoreState() {
		profile = DataProfile.load(profileName);

		settings = profile.settings();
		TabsManager.tabs = profile.tabs();
	}

	public static void dispose() {
		saveState();

		activityCallbackLauncher.dispose();
		activityCallbackLauncher = null;

		activityContext = null;
		TabsManager.tabs.clear();
		ThemeSettings.ThemeManager.setContext(null);

		for(var download : MyDownloadListener.ProgressListener.activeDownloads.values()) {
			download.cancel();
		}
	}

	public static Configuration getConfiguration() {
		return getAppContext().getResources().getConfiguration();
	}

	public static boolean isLandscape() {
		return getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}

	public static class ActivityCallbackLauncher {
		private ActivityResultLauncher<String[]> multiPermissionLauncher;
		private ActivityResultLauncher<String> permissionLauncher;
		private ActivityResultLauncher<Intent> intentLauncher;
		private MultiResultCallback multiPermissionCallback;
		private ResultCallback permissionCallback;
		private Runnable intentCallback;

		public ActivityCallbackLauncher(@NonNull AppCompatActivity activity) {
			permissionLauncher = activity.registerForActivityResult(
					new ActivityResultContracts.RequestPermission(),
					isGranted -> getPermissionCallback().run(isGranted));

			multiPermissionLauncher = activity.registerForActivityResult(
					new ActivityResultContracts.RequestMultiplePermissions(),
					isGranted -> getMultiPermissionCallback().run(isGranted));

			intentLauncher = activity.registerForActivityResult(
					new ActivityResultContracts.StartActivityForResult(),
					result -> getIntentCallback().run());
		}

		public void dispose() {
			multiPermissionLauncher.unregister();
			permissionLauncher.unregister();
			intentLauncher.unregister();

			multiPermissionLauncher = null;
			permissionLauncher = null;
			intentLauncher = null;
		}

		public ResultCallback getPermissionCallback() {
			return permissionCallback;
		}

		public MultiResultCallback getMultiPermissionCallback() {
			return multiPermissionCallback;
		}

		public Runnable getIntentCallback() {
			return intentCallback;
		}

		public void launchPermission(String permission, ResultCallback callback) {
			this.permissionCallback = callback;
			this.permissionLauncher.launch(permission);
		}

		public void launchPermissions(String[] permissions, MultiResultCallback callback) {
			this.multiPermissionCallback = callback;
			this.multiPermissionLauncher.launch(permissions);
		}

		public void launchIntent(Intent input, Runnable callback) {
			this.intentCallback = callback;
			this.intentLauncher.launch(input);
		}
	}

	public interface ResultCallback {
		void run(boolean isSuccess);
	}

	public interface MultiResultCallback {
		void run(Map<String, Boolean> isSuccess);
	}
}