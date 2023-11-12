package com.mrboomdev.scrollix.app.permission;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;

public class PermissionManager {

	public static void checkAndRequestPermission(@NonNull Permission permission, AppManager.ResultCallback callback) {
		checkAndRequestPermission(permission, callback, true);
	}

	public static void checkAndRequestPermissions(@NonNull Permission[] permissions, AppManager.MultiResultCallback callback) {
		checkAndRequestPermissions(permissions, callback, true);
	}

	protected static void checkAndRequestPermissions(@NonNull Permission[] permissions, AppManager.MultiResultCallback callback, boolean retry) {
		throw new RuntimeException("Stub! TODO");
	}

	protected static void checkAndRequestPermission(@NonNull Permission permission, AppManager.ResultCallback callback, boolean retry) {
		switch(permission) {
			case NOTIFICATION -> PermissionHandler.handleNotificationPermission(callback);
			case STORAGE -> PermissionHandler.handleStoragePermission(permission, callback, retry);
			default -> PermissionHandler.handleDefaultPermission(permission, callback);
		}
	}
}