package com.mrboomdev.scrollix.data.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThemeSettings {
	public String bars, barsOverlay, barsInner;
	public String primary, background;

	public ThemeSettings() {
		reset();
	}

	public void reset() {
		this.bars = "#110000";
		this.barsOverlay = "#ccccdd";
		this.barsInner = "#11ffffff";

		this.primary = "#ff0000";
		this.background = "#000000";
	}

	public boolean isInvalid() {
		try {
			Color.parseColor(bars);
			Color.parseColor(barsOverlay);
			Color.parseColor(barsInner);

			Color.parseColor(primary);
			Color.parseColor(background);
		} catch(IllegalArgumentException e) {
			e.printStackTrace();
			return true;
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
			if(_context == null) return;

			prefs = _context.getSharedPreferences("Themes", 0);
			setCurrentTheme(prefs.getString("current", "default"));
		}

		public static String getThemeJson(String name) {
			return resetIfInvalidTheme(prefs.getString("data_" + name, ""));
		}

		public static ThemeSettings getTheme(@NonNull String name) {
			if(name.equals(currentName)) return currentData;

			var json = resetIfInvalidTheme(prefs.getString("data_" + name, ""));
			var moshi = new Moshi.Builder().build();
			var adapter = moshi.adapter(ThemeSettings.class);

			try {
				return adapter.fromJson(json);
			} catch(IOException e) {
				throw new IllegalArgumentException("Invalid theme json!", e);
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
				throw new IllegalArgumentException("Invalid theme json!", e);
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

		public static ThemeSettings getCurrentTheme() {
			return getTheme(getCurrentThemeName());
		}
	}
}