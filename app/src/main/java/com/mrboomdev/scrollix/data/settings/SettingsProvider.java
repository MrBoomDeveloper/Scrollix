package com.mrboomdev.scrollix.data.settings;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.List;

public class SettingsProvider {

	@Nullable
	@Contract(pure = true)
	public static List<SettingsItem<?>> listSettings(String path) {
		return null;
	}
}