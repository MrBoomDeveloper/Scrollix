package com.mrboomdev.scrollix.data.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.util.exception.InvalidThemeException;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ThemeSettings {
	public String bars, barsOverlay, barsInner;
	public String primary, primaryRipple, background;
	public String popupBackground, popupTitle, popupDescription, popupSeparator;
	private static final ThemeSettings DEFAULT_THEME = new ThemeSettings();

	public ThemeSettings() {
		reset();
	}

	public void reset() {
		this.bars = "#111111";
		this.barsOverlay = "#ccccdd";
		this.barsInner = "#11ffffff";

		this.primary = "#373b3d";
		this.primaryRipple = "#cc555555";
		this.background = "#111111";

		this.popupBackground = "#141315";
		this.popupTitle = "#ffffff";
		this.popupDescription = "#ccccdd";
		this.popupSeparator = "#55cccccc";
	}

	public boolean isInvalid() {
		for(var item : List.of(
				bars, barsOverlay, barsInner,
				primary, primaryRipple, background,
				popupBackground, popupTitle, popupDescription
		)) {
			try {
				Color.parseColor(item);
			} catch(Exception e) {
				e.printStackTrace();
				return true;
			}
		}

		return false;
	}

	@SuppressLint("StaticFieldLeak")
	public static class ThemeManager {
		private static String currentName;
		private static SharedPreferences prefs;
		private static ThemeSettings currentData;
		private static final List<Runnable> updateCallbacks = new ArrayList<>();

		public static void addUpdateListener(Runnable listener) {
			updateCallbacks.add(listener);
		}

		public static void setContext(@Nullable Context _context) {
			if(_context == null) {
				currentData = null;
				updateCallbacks.clear();
				prefs = null;
				return;
			}

			prefs = _context.getSharedPreferences("Themes", 0);
			setCurrentTheme(prefs.getString("current", "default"));
		}

		public static String getThemeJson(String name) {
			return resetIfInvalidTheme(prefs.getString("data_" + name, ""));
		}

		public static ThemeSettings getTheme(@NonNull String name) throws InvalidThemeException {
			if(name.equals(currentName)) return currentData;

			var json = resetIfInvalidTheme(prefs.getString("data_" + name, ""));
			var moshi = new Moshi.Builder().build();
			var adapter = moshi.adapter(ThemeSettings.class);

			try {
				var theme = Objects.requireNonNull(adapter.fromJson(json));

				if(theme.isInvalid()) {
					throw new InvalidThemeException("Invalid theme values! " + json);
				}

				return theme;
			} catch(IOException e) {
				throw new InvalidThemeException("Invalid theme json!", e);
			}
		}

		private static String resetIfInvalidTheme(@NonNull String json) {
			boolean isInvalid;

			var moshi = new Moshi.Builder().build();
			var adapter = moshi.adapter(ThemeSettings.class);

			try {
				isInvalid = json.isBlank() || adapter.fromJson(json) == null;
			} catch(IOException e) {
				e.printStackTrace();
				isInvalid = true;
			}

			if(isInvalid) {
				return adapter.toJson(new ThemeSettings());
			}

			return json;
		}

		public static void setCurrentTheme(String name) {
			var json = prefs.getString("data_" + name, "");
			var fixedJson = resetIfInvalidTheme(json);

			if(!json.equals(fixedJson)) {
				prefs.edit().putString("data_" + name, fixedJson).apply();
			}

			var moshi = new Moshi.Builder().build();
			var adapter = moshi.adapter(ThemeSettings.class);

			try {
				currentData = adapter.fromJson(fixedJson);
			} catch(IOException e) {
				throw new InvalidThemeException("Invalid theme json!", e);
			}

			prefs.edit().putString("current", name).apply();
		}

		public static void setThemeJson(@NonNull String name, String json) {
			try {
				var moshi = new Moshi.Builder().build();
				var adapter = moshi.adapter(ThemeSettings.class);
				prefs.edit().putString("data_" + name, json).apply();

				if(name.equals(currentName)) {
					currentData = adapter.fromJson(json);
					updateCallbacks.forEach(Runnable::run);
				}
			} catch(IOException e) {
				e.printStackTrace();
			}
		}

		public static String getCurrentThemeName() {
			if(currentName == null) {
				currentName = prefs.getString("current", "default");
			}

			return currentName;
		}

		public static ThemeSettings getCurrentTheme() throws InvalidThemeException {
			return getTheme(getCurrentThemeName());
		}

		public static ThemeSettings getCurrentValidTheme() {
			try {
				var theme = getCurrentTheme();
				return theme.isInvalid() ? DEFAULT_THEME : theme;
			} catch(InvalidThemeException e) {
				e.printStackTrace();
				return DEFAULT_THEME;
			}
		}
	}
}