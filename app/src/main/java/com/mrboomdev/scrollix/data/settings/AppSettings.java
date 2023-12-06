package com.mrboomdev.scrollix.data.settings;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;
import com.squareup.moshi.Json;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.List;

public class AppSettings {
	public static final String DEFAULT_SETTINGS_PATH = "settings-values.json";
	@Json(name = "left_actions")
	public List<String> leftActions;
	@Json(name = "right_actions")
	public List<String> rightActions;
	@Json(name = "side_actions")
	public List<String> sideActions;
	@Json(name = "menu_actions")
	public List<String> menuActions;
	@Json(name = "search_engine")
	public SearchEngine.Preset searchEngine;
	@Json(name = "search_engine_autocompletion")
	public Boolean searchEngineAutocompletion;
	@Json(name = "search_history_autocompletion")
	public Boolean searchHistoryAutocompletion;
	@Json(name = "use_layout_color_from_page")
	public Boolean useLayoutColorFromPage;
	@Json(name = "url_format_rules")
	public UrlFormatRules urlFormatRules;

	public AppSettings() {

	}

	public void fillNullValues() {
		var defaultSettingsJson = FileUtil.readAssetsString(AppSettings.DEFAULT_SETTINGS_PATH);
		var defaultSettings = AppSettings.fromString(defaultSettingsJson, false);

		try {
			for(var field : AppSettings.class.getFields()) {
				if(field.get(this) != null) continue;

				if(field.get(defaultSettings) == null) {
					var exceptionReason = "Value \"" + field.getName() + "\" in default settings is null!";
					throw new UnexpectedBehaviourException(exceptionReason);
				}

				field.set(this, field.get(defaultSettings));
			}
		} catch(IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	@NonNull
	private static AppSettings fromString(String json, boolean fillNull) {
		var moshi = new Moshi.Builder().build();
		var adapter = moshi.adapter(AppSettings.class);

		try {
			var settings = adapter.fromJson(json);

			if(settings == null) {
				throw new UnexpectedBehaviourException("Null settings!");
			}

			if(fillNull) {
				settings.fillNullValues();
			}

			return settings;
		} catch(IOException e) {
			throw new UnexpectedBehaviourException(e);
		}
	}

	@NonNull
	public static AppSettings fromString(String json) {
		return fromString(json, true);
	}

	public static class UrlFormatRules {
		@Json(name = "remove_protocol")
		public boolean removeProtocol;
		@Json(name = "remove_www")
		public boolean removeWww;
		@Json(name = "remove_hash")
		public boolean removeHash;
		@Json(name = "remove_parameters")
		public boolean removeParameters;
	}
}