package com.mrboomdev.scrollix.webview;

import android.webkit.JavascriptInterface;

import com.mrboomdev.scrollix.data.settings.ThemeSettings;

@Deprecated
public class ScrollixJsBridge {

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
		ThemeSettings.ThemeManager.setThemeJson(name, json);
	}

	@JavascriptInterface
	public void setCurrentTheme(String name) {
		ThemeSettings.ThemeManager.setCurrentTheme(name);
	}
}