package com.mrboomdev.scrollix.data;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.data.settings.AppSettings;
import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabAdapter;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;
import com.squareup.moshi.Json;
import com.squareup.moshi.Moshi;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record DataProfile(
		List<Tab> tabs,
		@Json(name = "current_tab") int currentTab,
		AppSettings settings
) {

	@NonNull
	public static DataProfile restoreAsLocal(String json) throws UnexpectedBehaviourException {
		var moshi = new Moshi.Builder().add(new TabAdapter.ExportAdapter()).build();
		var adapter = moshi.adapter(DataProfile.class).lenient();

		try {
			var profile = adapter.fromJson(json);

			if(profile == null) {
				throw new UnexpectedBehaviourException("Profile is null!");
			}

			profile.settings.fillNullValues();
			return profile;
		} catch(IOException e) {
			throw new UnexpectedBehaviourException("Failed to load a profile!", e);
		}
	}

	@NonNull
	public String saveAsLocal() {
		var moshi = new Moshi.Builder().add(new TabAdapter()).build();
		var adapter = moshi.adapter(DataProfile.class).lenient();
		return adapter.toJson(this);
	}

	@NonNull
	public String saveAsExternal() {
		var moshi = new Moshi.Builder().add(new TabAdapter.ExportAdapter()).build();
		var adapter = moshi.adapter(DataProfile.class).lenient();
		return adapter.toJson(this);
	}

	@NonNull
	public static DataProfile restoreAsExternal(String json) throws UnexpectedBehaviourException {
		var moshi = new Moshi.Builder().add(new TabAdapter.ExportAdapter()).build();
		var adapter = moshi.adapter(DataProfile.class).lenient();

		try {
			var profile = adapter.fromJson(json);

			if(profile == null) {
				throw new UnexpectedBehaviourException("Profile is null!");
			}

			profile.settings.fillNullValues();
			return profile;
		} catch(IOException e) {
			throw new UnexpectedBehaviourException("Failed to import a profile!", e);
		}
	}

	@NonNull
	@Contract(" -> new")
	public static DataProfile createDefault() {
		List<Tab> tabs = new ArrayList<>();
		tabs.add(new Tab(true));

		var settingsJson = FileUtil.readAssetsString(AppSettings.DEFAULT_SETTINGS_PATH);
		return new DataProfile(tabs, 0, AppSettings.fromString(settingsJson));
	}

	public record SavedTab(List<SavedHistoryItem> history, int currentHistoryItem, String extra) {}

	public record SavedHistoryItem(String url, String title) {}
}