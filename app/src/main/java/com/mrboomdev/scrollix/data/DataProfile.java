package com.mrboomdev.scrollix.data;

import android.widget.Toast;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.AppSettings;
import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.util.FileUtil;
import com.squareup.moshi.FromJson;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record DataProfile(
		List<Tab> tabs,
		@Json(name = "current_tab") int currentTab,
		AppSettings settings
) {
	private static final Moshi moshi = new Moshi.Builder().add(new Adapter()).build();
	private static final JsonAdapter<DataProfile> adapter = moshi.adapter(DataProfile.class).lenient();

	@NonNull
	public String serialize() {
		return adapter.toJson(this);
	}

	public static DataProfile deserialize(String json) {
		try {
			return adapter.fromJson(json);
		} catch(IOException e) {
			Toast.makeText(AppManager.getAppContext(), "Failed to restore your previous sessions!", Toast.LENGTH_LONG).show();
			return createDefault();
		}
	}

	public static DataProfile load(String profileName) {
		var file = FileUtil.getFile("profiles/" + profileName + ".txt");

		if(!file.exists()) {
			return createDefault();
		}

		return deserialize(FileUtil.readFileString(file));
	}

	@NonNull
	@Contract(" -> new")
	public static DataProfile createDefault() {
		List<Tab> tabs = new ArrayList<>();
		tabs.add(new Tab(true));

		return new DataProfile(tabs, 0, new AppSettings());
	}

	public void save(String profileName) {
		var file = FileUtil.getFile("profiles/" + profileName + ".txt");
		FileUtil.writeFile(serialize(), file);
	}

	public static class Adapter {

		@ToJson
		public SerializableTab tabToJson(@NonNull Tab tab) {
			return new SerializableTab(tab.getUrl(), tab.getTitle());
		}

		@FromJson
		public Tab tabFromJson(@NonNull SerializableTab serializableTab) {
			var tab = new Tab(serializableTab.url, true);
			tab.setTitle(serializableTab.title);
			return tab;
		}
	}

	public record SerializableTab(String url, String title) {}
}