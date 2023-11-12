package com.mrboomdev.scrollix.data.tabs;

import android.annotation.SuppressLint;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TabsManager {
	public static List<Tab> tabs = new ArrayList<>();
	public static List<RemoveCallback> removeTabCallbacks = new ArrayList<>();
	public static List<Callback> selectTabCallbacks = new ArrayList<>(),
			createTabCallbacks = new ArrayList<>();
	@SuppressLint("StaticFieldLeak")
	private static Tab currentTab;


	public static int getIndex(Tab tab) {
		for(int i = 0; i < getCount(); i++) {
			var nextTab = tabs.get(i);
			if(nextTab == tab) return i;
		}

		return -1;
	}

	public static int getCurrentIndex() {
		return getIndex(getCurrent());
	}


	public static void setCurrent(Tab tab, boolean runCallbacks) {
		currentTab = tab;

		if(runCallbacks) {
			for(var callback : selectTabCallbacks) {
				callback.run(tab);
			}
		}
	}

	public static void setCurrent(Tab tab) {
		setCurrent(tab, true);
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
	public static Tab create() {
		return create(true);
	}

	@NonNull
	public static Tab create(boolean focus) {
		return create(Tab.SCROLLIX_HOME, focus);
	}

	@NonNull
	public static Tab create(String url) {
		return create(url, true);
	}

	@NonNull
	public static Tab create(String url, boolean focus) {
		return create(url, focus, true);
	}

	@NonNull
	public static Tab create(String url, boolean focus, boolean sideEffects) {
		var tab = new Tab(AppManager.getAppContext());

		tab.webView.loadUrl(Objects.requireNonNullElse(url, Tab.SCROLLIX_HOME));

		if(sideEffects) tabs.add(tab);

		if(focus) setCurrent(tab);

		if(sideEffects) {
			for(var callback : createTabCallbacks) {
				callback.run(tab);
			}
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
	public static Tab get(WebView webView) {
		for(var tab : tabs) {
			if(tab.webView == webView) return tab;
		}

		return null;
	}

	@Nullable
	public static Tab getNearestTab(int index) {
		var previousTab = get(index - 1);
		if(previousTab != null) return previousTab;

		return get(index);
	}

	public static void remove(Tab tab) {
		remove(getIndex(tab));
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