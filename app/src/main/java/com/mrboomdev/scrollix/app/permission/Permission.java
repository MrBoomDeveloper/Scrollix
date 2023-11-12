package com.mrboomdev.scrollix.app.permission;

import android.Manifest;
import android.os.Build;

public enum Permission {
	NOTIFICATION(Build.VERSION.SDK_INT < 33 ? null : Manifest.permission.POST_NOTIFICATIONS),
	CAMERA(Manifest.permission.CAMERA),
	STORAGE(Build.VERSION.SDK_INT < 30 ? Manifest.permission.WRITE_EXTERNAL_STORAGE : Manifest.permission.MANAGE_EXTERNAL_STORAGE),
	MICROPHONE(Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.RECORD_AUDIO);

	private final String[] androidNames;

	Permission(String ...androidNames) {
		this.androidNames = androidNames;
	}

	public boolean isMultiple() {
		return getAndroidNames().length > 1;
	}

	public String[] getAndroidNames() {
		return androidNames;
	}

	public String getAndroidName() {
		return androidNames[0];
	}
}