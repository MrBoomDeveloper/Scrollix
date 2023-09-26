package com.mrboomdev.scrollix.data;

import com.mrboomdev.scrollix.data.search.GoogleSearch;
import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.util.LinkUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppSettings {
	public static AppSettings globalSettings;
	public List<String> leftActions, rightActions, menuActions;
	public BottomBarMode sideBar;
	public DarkMode darkPages, darkUi;
	public SearchEnginePreset searchEngine, autocompletion;
	public boolean useLayoutColorFromPage;
	public LinkUtil.UrlFormatRules urlFormatRules;

	public AppSettings() {
		leftActions = new ArrayList<>();
		rightActions = new ArrayList<>();
		menuActions = new ArrayList<>();

		applyDefaults();
	}

	public AppSettings(String json) {
		this();
	}

	public void applyDefaults() {
		leftActions.clear();
		rightActions.clear();
		menuActions.clear();

		leftActions.add("home");
		rightActions.addAll(Arrays.asList("back", "next", "tabs", "menu"));
		menuActions.addAll(Arrays.asList("history", "bookmarks", "downloads", "settings"));

		searchEngine = SearchEnginePreset.GOOGLE;

		darkUi = DarkMode.INACTIVE;
		darkPages = DarkMode.INACTIVE;
	}

	public enum SearchEnginePreset {
		GOOGLE(new GoogleSearch()),
		DUCKDUCKGO(null),
		YANDEX(null),
		BING(null),
		STARTPAGE(null);

		private final SearchEngine engine;

		SearchEnginePreset(SearchEngine engine) {
			this.engine = engine;
		}

		public SearchEngine getEngine() {
			return this.engine;
		}
	}

	public enum BottomBarMode {
		ACTIVE,
		INACTIVE,
		SIDE_LANDSCAPE,
		SIDE
	}

	public enum DarkMode {
		ACTIVE,
		INACTIVE
	}
}