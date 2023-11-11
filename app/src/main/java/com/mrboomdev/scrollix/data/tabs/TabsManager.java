package com.mrboomdev.scrollix.data.tabs;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TabsManager {
	public static List<Tab> tabs = new ArrayList<>();
	public static List<Callback> selectTabCallbacks = new ArrayList<>(), createTabCallbacks = new ArrayList<>();

	@NonNull
	public static Tab create() {
		return create(true);
	}

	public static void setCurrent(Tab tab) {
		for(var callback : selectTabCallbacks) {
			callback.run(tab);
		}
	}

	@NonNull
	public static Tab create(boolean focus) {
		var tab = new Tab(AppManager.getAppContext());

		tab.webView.loadUrl("file:///android_asset/pages/home.html");
		tab.reloadSettings();

		tabs.add(tab);

		if(focus) setCurrent(tab);

		for(var callback : createTabCallbacks) {
			callback.run(tab);
		}

		return tab;
	}

	public static Tab get(int index) {
		return tabs.get(index);
	}

	public static void remove(Tab tab) {
		tabs.remove(tab);
	}

	public static void restoreFromFile(File file) {
		tabs = new ArrayList<>();
	}

	public static int getCount() {
		return tabs.size();
	}

	public interface Callback {
		void run(Tab tab);
	}
}