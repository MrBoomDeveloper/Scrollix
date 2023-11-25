package com.mrboomdev.scrollix.data.settings;

import com.mrboomdev.scrollix.data.search.SearchEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppSettings {
	public List<String> leftActions, rightActions, menuActions;
	public BottomBarMode sideBar;
	public SearchEngine.Preset searchEngine, autocompletion;
	public boolean useLayoutColorFromPage;
	public UrlFormatRules urlFormatRules;
	public UrlCleanRules urlCleanRules;

	public AppSettings() {
		leftActions = new ArrayList<>();
		rightActions = new ArrayList<>();
		menuActions = new ArrayList<>();

		urlFormatRules = new UrlFormatRules();
		urlCleanRules = new UrlCleanRules();

		applyDefaults();
	}

	public void applyDefaults() {
		leftActions.clear();
		rightActions.clear();
		menuActions.clear();

		leftActions.add("home");
		rightActions.addAll(Arrays.asList("back", "next", "tabs", "menu"));
		menuActions.addAll(Arrays.asList("history", "bookmarks", "downloads", "settings"));

		searchEngine = SearchEngine.Preset.GOOGLE;

		urlFormatRules.replaceScrollix = true;
		urlFormatRules.parseSearchQuery = true;
		urlFormatRules.removeWww = true;
	}

	public enum BottomBarMode {
		ACTIVE,
		INACTIVE,
		SIDE_LANDSCAPE,
		SIDE
	}

	public static class UrlCleanRules {
		public boolean removeTracking;
	}

	public static class UrlFormatRules {
		public boolean replaceScrollix, parseSearchQuery;
		public boolean removeProtocol, removeWww, removeHash, removeParameters;
	}
}