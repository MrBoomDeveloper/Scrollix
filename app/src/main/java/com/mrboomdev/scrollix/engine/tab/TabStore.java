package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public class TabStore {
	private static final List<Tab> tabs = new ArrayList<>();

	public static void addTab(Tab tab, int index) {
		if(tab == null) return;

		tabs.add(index, tab);
		runModifierListeners();
	}

	public static List<Tab> getAllTabs() {
		return tabs;
	}

	public static void addTab(Tab tab) {
		if(tab == null) return;

		tabs.add(tab);
		runModifierListeners();
	}

	@NonNull
	public static Tab createTab(String url, boolean focus) {
		var tab = new Tab(url);
		addTab(tab);

		if(focus) {
			TabManager.setCurrentTab(tab, true);
		}

		return tab;
	}

	@NonNull
	public static Tab createTab(boolean focus) {
		return createTab(null, focus);
	}

	public static void removeTab(int index) {
		boolean wasCurrent = getTabIndex(TabManager.getCurrentTab()) == index;

		if(wasCurrent) {
			selectNearestTab(index);
		}

		tabs.remove(index);
		runModifierListeners();

		//TODO: Use value from settings if user want to create a new tab or close app
		doActionIfEmpty(true);
	}

	public static void removeTab(Tab tab) {
		var index = getTabIndex(tab);

		if(index != -1) {
			removeTab(index);
		}
	}

	public static void doActionIfEmpty(boolean exitIfEmpty) {
		if(isEmpty()) {
			if(exitIfEmpty) AppManager.closeApp();
			else createTab(true);
		}
	}

	private static void selectNearestTab(int index) {
		var nearestTab = getNearestTab(index);
		if(nearestTab == null) nearestTab = getTab(0);
		if(nearestTab == null) return;

		TabManager.setCurrentTab(nearestTab);
	}

	public static Tab getNearestTab(int index) {
		var previousTab = getTab(index - 1);
		if(previousTab != null) return previousTab;

		return getTab(index);
	}

	@Nullable
	@Contract(pure = true)
	public static Tab getTab(int index) {
		if(index >= tabs.size() || index < 0) return null;
		return tabs.get(index);
	}

	public static int getTabIndex(Tab tab) {
		for(int i = 0; i < getTabCount(); i++) {
			var _tab = getTab(i);
			if(_tab == tab) return i;
		}

		return -1;
	}

	public static int getTabCount() {
		return tabs.size();
	}

	public static boolean isEmpty() {
		return tabs.isEmpty();
	}

	public static void clearTabs() {
		for(var tab : tabs) {
			tab.dispose();
		}

		tabs.clear();
		runModifierListeners();
	}

	public static void setTabs(List<Tab> newTabs) {
		clearTabs();
		tabs.addAll(newTabs);

		if(newTabs.isEmpty()) createTab(true);
		else TabManager.setCurrentTab(newTabs.get(0));

		runModifierListeners();
		doActionIfEmpty(false);
	}

	private static void runModifierListeners() {
		for(var listener : TabManager.getTabListeners()) {
			listener.onTabListModified();
		}
	}
}