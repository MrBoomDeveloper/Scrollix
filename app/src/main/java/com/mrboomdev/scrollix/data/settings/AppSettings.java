package com.mrboomdev.scrollix.data.settings;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;
import com.squareup.moshi.Json;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AppSettings {
	public static final String DEFAULT_SETTINGS_PATH = "settings-values.json";
	public static final String TAG = "AppSettings";
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
	@Json(name = "collapse_bars")
	public Boolean collapseBars;
	@Json(name = "search_history_autocompletion")
	public Boolean searchHistoryAutocompletion;
	@Json(name = "use_layout_color_from_page")
	public Boolean useLayoutColorFromPage;
	@Json(name = "url_format_rules")
	public UrlFormatRules urlFormatRules;
	@Json(ignore = true)
	private final Map<Field, List<Runnable>> fieldChangeListeners = new HashMap<>();

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

	private void checkFieldChangeListenersListExistence(Field field) {
		fieldChangeListeners.computeIfAbsent(field, k -> new ArrayList<>());
	}

	public void addChangeListener(String fieldName, Runnable callback) {
		Field field;

		try {
			field = AppSettings.class.getField(fieldName);
		} catch(NoSuchFieldException e) {
			throw new UnexpectedBehaviourException("Field doesn't exist: " + fieldName, e);
		}

		checkFieldChangeListenersListExistence(field);
		var listeners = fieldChangeListeners.get(field);

		if(listeners != null) {
			listeners.add(callback);
		}
	}

	public void removeChangeListener(String fieldName, Runnable callback) {
		Field field;

		try {
			field = AppSettings.class.getField(fieldName);
		} catch(NoSuchFieldException e) {
			throw new UnexpectedBehaviourException("Field doesn't exist: " + fieldName, e);
		}

		checkFieldChangeListenersListExistence(field);
		var listeners = fieldChangeListeners.get(field);

		if(listeners != null) {
			listeners.remove(callback);
		}
	}

	public void merge(String json) {
		var moshi = new Moshi.Builder().build();
		var adapter = moshi.adapter(AppSettings.class);

		try {
			merge(adapter.fromJson(json));
		} catch(IOException e) {
			throw new UnexpectedBehaviourException(e);
		}
	}

	public void merge(AppSettings settings) {
		var changedFields = new ArrayList<Field>();

		for(var field : getClass().getFields()) {
			try {
				if(Objects.equals(field.get(this), field.get(settings))) continue;

				if(field.get(settings) == null) {
					throw new NullPointerException("Setting value cannot be null!");
				}

				checkFieldChangeListenersListExistence(field);
				changedFields.add(field);

				field.set(this, field.get(settings));
			} catch(IllegalAccessException e) {
				throw new UnexpectedBehaviourException("Failed to access a field: " + field.getName(), e);
			}
		}

		Log.i(TAG, "Merged fields: " + changedFields);

		for(var field : changedFields) {
			var listeners = fieldChangeListeners.get(field);
			if(listeners == null) continue;

			for(var listener : listeners) {
				listener.run();
			}
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
	@Override
	public String toString() {
		var moshi = new Moshi.Builder().build();
		var adapter = moshi.adapter(AppSettings.class);
		return adapter.toJson(this);
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

		@Override
		public boolean equals(@Nullable Object obj) {
			if(obj instanceof UrlFormatRules rules) {
				return rules.removeWww == removeWww &&
						rules.removeHash == removeHash &&
						rules.removeParameters == removeParameters &&
						rules.removeProtocol == removeProtocol;

			}

			return false;
		}
	}
}