package com.mrboomdev.scrollix.data.tabs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TabsManager {
	public static List<Tab> tabs = new ArrayList<>();
	public static List<RemoveCallback> removeTabCallbacks = new ArrayList<>();
	public static List<Callback> selectTabCallbacks = new ArrayList<>(),
			createTabCallbacks = new ArrayList<>();
	private static Tab currentTab;

	@NonNull
	public static Tab create() {
		return create(true);
	}

	public static void setCurrent(Tab tab) {
		setCurrent(tab, true);
	}

	public static int getIndex(Tab tab) {
		for(int i = 0; i < getCount(); i++) {
			var nextTab = tabs.get(i);
			if(nextTab == tab) return i;
		}

		return -1;
	}

	public static void setCurrent(Tab tab, boolean runCallbacks) {
		currentTab = tab;

		if(runCallbacks) {
			for(var callback : selectTabCallbacks) {
				callback.run(tab);
			}
		}
	}

	public static Tab getCurrent() {
		return currentTab;
	}

	public static void add(Tab tab, int index) {
		tabs.add(index, tab);
	}

	public static void move(int fromIndex, int toIndex) {
		if(fromIndex == toIndex) return;

		var tab = get(fromIndex);

		tabs.add(toIndex, tab);
		tabs.remove(fromIndex);
	}

	@NonNull
	public static Tab create(boolean focus) {
		return create("file:///android_asset/pages/home.html", focus);
	}

	@NonNull
	public static Tab create(String url, boolean focus) {
		var tab = new Tab(AppManager.getAppContext());

		tab.webView.loadUrl(url);
		tab.reloadSettings();

		tabs.add(tab);

		if(focus) setCurrent(tab);

		for(var callback : createTabCallbacks) {
			callback.run(tab);
		}

		return tab;
	}

	@Nullable
	public static Tab get(int index) {
		try {
			return tabs.get(index);
		} catch(IndexOutOfBoundsException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Nullable
	public static Tab getNearestTab(int index) {
		var previousTab = get(index - 1);
		if(previousTab != null) return previousTab;

		return get(index);
	}

	public static void remove(int index) {
		var tab = get(index);

		tabs.remove(index);

		for(var callback : removeTabCallbacks) {
			callback.removed(tab, index);
		}

		if(tabs.isEmpty()) {
			create(true);
			return;
		}

		var nearestTab = getNearestTab(index);
		if(nearestTab != null) {
			setCurrent(nearestTab);
			return;
		}

		setCurrent(get(0));
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

	public interface RemoveCallback {
		void removed(Tab tab, int wasIndex);
	}
}