package com.mrboomdev.scrollix.webview;

import android.webkit.JavascriptInterface;

import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.squareup.moshi.Moshi;

import java.io.IOException;

public class ScrollixJsBridge {
	private final Tab tab;

	public ScrollixJsBridge(Tab tab) {
		this.tab = tab;
	}

	@JavascriptInterface
	public String getCurrentThemeName() {
		return ThemeSettings.ThemeManager.getCurrentThemeName();
	}

	@JavascriptInterface
	public String getCurrentTheme() {
		return getTheme(getCurrentThemeName());
	}

	@JavascriptInterface
	public String getTheme(String name) {
		return ThemeSettings.ThemeManager.getThemeJson(name);
	}

	@JavascriptInterface
	public void setThemeData(String name, String json) {
		try {
			var moshi = new Moshi.Builder().build();
			var adapter = moshi.adapter(ThemeSettings.class);
			var theme = adapter.fromJson(json);

			var themePrefs = tab.webView.getContext().getSharedPreferences("Themes", 0);
			themePrefs.edit().putString("data_" + name, json).apply();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	@JavascriptInterface
	public void setCurrentTheme(String name) {
		ThemeSettings.ThemeManager.setCurrentTheme(name);
	}
}