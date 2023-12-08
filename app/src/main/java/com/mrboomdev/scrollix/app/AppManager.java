package com.mrboomdev.scrollix.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.DataProfile;
import com.mrboomdev.scrollix.data.settings.AppSettings;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.IncognitoActivity;
import com.mrboomdev.scrollix.ui.MainActivity;
import com.mrboomdev.scrollix.ui.popup.DialogMenu;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import java.util.Map;

public class AppManager {
	public static String profileName = "Default";
	public static AppSettings settings;
	private static DataProfile profile;
	private static final String TAG = "AppManager";
	public static ActivityCallbackLauncher activityCallbackLauncher;
	@SuppressLint("StaticFieldLeak")
	private static MainActivity mainActivityContext;

	public static Context getAppContext() {
		return mainActivityContext.getApplicationContext();
	}

	public static AppCompatActivity getActivityContext() {
		return mainActivityContext;
	}

	public static MainActivity getMainActivityContext() {
		return mainActivityContext;
	}

	public static void postCreate() {
		if(TabManager.getCurrentTab() == null) {
			var tab = TabStore.getTab(profile != null ? profile.currentTab() : 0);

			if(tab != null) TabManager.setCurrentTab(tab);
			else TabStore.createTab();
		}
	}

	public static void startup(MainActivity context) {
		setupCrashHandler();
		mainActivityContext = context;

		var displayImageOptions = new DisplayImageOptions.Builder()
				.showImageOnFail(R.drawable.ic_error_black)
				.showImageForEmptyUri(R.drawable.ic_error_black);

		var imageLoaderConfig = new ImageLoaderConfiguration.Builder(context)
				.defaultDisplayImageOptions(displayImageOptions.build())
				.diskCacheSize(FormatUtil.getBytesFromMegabytes(50));

		var imageLoader = ImageLoader.getInstance();
		imageLoader.init(imageLoaderConfig.build());

		Formats.init();
		TabManager.startup();

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
				TabStore.getAllTabs(),
				TabStore.getTabIndex(TabManager.getCurrentTab()),
				settings);

		var file = FileUtil.getFile("profiles/" + profileName + ".txt");
		FileUtil.writeFile(profile.saveAsLocal(), file);
	}

	public static void restoreState() {
		var file = FileUtil.getFile("profiles/" + profileName + ".txt");

		profile = file.exists()
				? DataProfile.restoreAsLocal(FileUtil.readFileString(file))
				: DataProfile.createDefault();

		settings = profile.settings();
		TabStore.setTabs(profile.tabs());

		var context = getActivityContext();
		var intent = context.getIntent();

		IntentHandler.handleIntent(intent);
		context.setIntent(null);
	}

	public static void dispose() {
		if(getActivityContext() == null) return;
		saveState();

		activityCallbackLauncher.dispose();
		activityCallbackLauncher = null;

		mainActivityContext = null;
		TabStore.clearTabs();
		TabManager.dispose();
		ThemeSettings.ThemeManager.setContext(null);
	}

	public static void closeApp() {
		var context = getActivityContext();
		if(context == null) return;

		context.finishAffinity();
	}

	public static void handleException(Throwable throwable) {
		var context = getActivityContext();

		AppUtils.runOnUiThread(() -> new DialogMenu(context)
				.setCancelable(false)
				.setTitle("App just crashed!")
				.setDescription("Stacktrace: \n\n" + Log.getStackTraceString(throwable))
				.addAction("Exit app", AppManager::closeApp)
				.show());
	}

	public static void setupCrashHandler() {
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> handleException(throwable));
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