package com.mrboomdev.scrollix.app.permission;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.mrboomdev.scrollix.app.AppManager;

import java.util.ArrayList;

public class PermissionHandler {

	protected static void handleStoragePermission(@NonNull Permission permission, AppManager.ResultCallback callback, boolean retry) {
		var context = AppManager.getAppContext();

		var requiredPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
				? Manifest.permission.MANAGE_EXTERNAL_STORAGE
				: Manifest.permission.WRITE_EXTERNAL_STORAGE;

		var status = ContextCompat.checkSelfPermission(context, requiredPermission);

		if(status == PackageManager.PERMISSION_GRANTED) {
			callback.run(true);
			return;
		}

		if(!retry) {
			callback.run(false);
			return;
		}

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			var uri = Uri.fromParts("package", context.getPackageName(), null);
			var intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, uri);

			AppManager.activityCallbackLauncher.launchIntent(intent, () ->
					PermissionManager.checkAndRequestPermission(permission, callback, false));

			return;
		}

		AppManager.activityCallbackLauncher.launchPermission(requiredPermission, callback);
	}

	protected static void handleNotificationPermission(AppManager.ResultCallback callback) {
		if(Build.VERSION.SDK_INT < 33) {
			callback.run(true);
			return;
		}

		handleDefaultPermission(Permission.NOTIFICATION, callback);
	}

	protected static void handleDefaultPermission(@NonNull Permission permission, AppManager.ResultCallback callback) {
		if(permission.isMultiple()) {
			handleMultiplePermissions(permission, callback);
			return;
		}

		var status = ContextCompat.checkSelfPermission(AppManager.getAppContext(), permission.getAndroidName());

		if(status == PackageManager.PERMISSION_GRANTED) {
			callback.run(true);
			return;
		}

		AppManager.activityCallbackLauncher.launchPermission(permission.getAndroidName(), callback);
	}

	protected static void handleMultiplePermissions(@NonNull Permission permission, AppManager.ResultCallback callback) {
		var requiredPermissions = new ArrayList<String>();

		for(var permissionName : permission.getAndroidNames()) {
			var status = ContextCompat.checkSelfPermission(AppManager.getAppContext(), permissionName);

			if(status != PackageManager.PERMISSION_GRANTED) {
				requiredPermissions.add(permissionName);
			}
		}

		if(requiredPermissions.isEmpty()) {
			callback.run(true);
			return;
		}

		AppManager.activityCallbackLauncher.launchPermissions(requiredPermissions.toArray(new String[0]), results -> {
			for(var result : results.values()) {
				if(!result) {
					callback.run(false);
					return;
				}
			}

			callback.run(true);
		});
	}
}