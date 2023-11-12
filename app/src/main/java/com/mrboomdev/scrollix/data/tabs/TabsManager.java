package com.mrboomdev.scrollix.data.tabs;

import android.annotation.SuppressLint;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Deprecated
public class TabsManager {
	public static List<Tab> tabs = new ArrayList<>();
	public static List<RemoveCallback> removeTabCallbacks = new ArrayList<>();
	public static List<Callback> selectTabCallbacks = new ArrayList<>(),
			createTabCallbacks = new ArrayList<>();
	@SuppressLint("StaticFieldLeak")
	private static Tab currentTab;


	@Deprecated
	public static int getIndex(Tab tab) {
		for(int i = 0; i < getCount(); i++) {
			var nextTab = tabs.get(i);
			if(nextTab == tab) return i;
		}

		return -1;
	}

	@Deprecated
	public static int getCurrentIndex() {
		return getIndex(getCurrent());
	}


	@Deprecated
	public static void setCurrent(Tab tab, boolean runCallbacks) {
		currentTab = tab;

		if(runCallbacks) {
			for(var callback : selectTabCallbacks) {
				callback.run(tab);
			}
		}
	}

	@Deprecated
	public static void setCurrent(Tab tab) {
		setCurrent(tab, true);
	}

	@Deprecated
	public static Tab getCurrent() {
		return currentTab;
	}

	@Deprecated
	public static void add(Tab tab, int index) {
		tabs.add(index, tab);
	}

	@Deprecated
	public static void move(int fromIndex, int toIndex) {
		if(fromIndex == toIndex) return;

		var tab = get(fromIndex);

		tabs.add(toIndex, tab);
		tabs.remove(fromIndex);
	}

	@Deprecated
	@NonNull
	public static Tab create() {
		return create(true);
	}

	@Deprecated
	@NonNull
	public static Tab create(boolean focus) {
		return create(Tab.SCROLLIX_HOME, focus);
	}

	@Deprecated
	@NonNull
	public static Tab create(String url) {
		return create(url, true);
	}

	@NonNull
	@Deprecated
	public static Tab create(String url, boolean focus) {
		return create(url, focus, true);
	}

	@NonNull
	@Deprecated
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

	@Deprecated
	@Nullable
	public static Tab get(int index) {
		try {
			return tabs.get(index);
		} catch(IndexOutOfBoundsException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Deprecated
	@Nullable
	public static Tab get(WebView webView) {
		for(var tab : tabs) {
			if(tab.webView == webView) return tab;
		}

		return null;
	}

	@Deprecated
	@Nullable
	public static Tab getNearestTab(int index) {
		var previousTab = get(index - 1);
		if(previousTab != null) return previousTab;

		return get(index);
	}

	@Deprecated
	public static void remove(Tab tab) {
		remove(getIndex(tab));
	}

	@Deprecated
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

	@Deprecated
	public static int getCount() {
		return tabs.size();
	}

	@Deprecated
	public interface Callback {
		void run(Tab tab);
	}

	@Deprecated
	public interface RemoveCallback {
		void removed(Tab tab, int wasIndex);
	}
}