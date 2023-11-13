package com.mrboomdev.scrollix.data.settings;

import java.util.ArrayList;
import java.util.List;

public class SettingsStore {
	private static List<SettingsItem<?>> items = new ArrayList<>();

	public static void setSettings(List<SettingsItem<?>> _items) {
		items = _items;
	}

	public static List<SettingsItem<?>> listSettings(String path) {
		return List.of();
	}
}