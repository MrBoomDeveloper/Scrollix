package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public class TabStore {
	private static final List<Tab> tabs = new ArrayList<>();

	public static void addTab(Tab tab, int index) {
		tabs.add(index, tab);
		runModifierListeners();
	}

	public static void addTab(Tab tab) {
		tabs.add(tab);
		runModifierListeners();
	}

	@NonNull
	public static Tab createTab(String url, boolean focus) {
		var tab = new Tab(url);
		addTab(tab);

		if(focus) {
			TabManager.setCurrentTab(tab);
		}

		return tab;
	}

	@NonNull
	public static Tab createTab(boolean focus) {
		return createTab(null, focus);
	}

	public static void removeTab(Tab tab) {
		boolean wasCurrent = TabManager.getCurrentTab() == tab;

		tabs.remove(tab);
		runModifierListeners();

		if(wasCurrent) {
			selectNearestTab(getTabIndex(tab));
		}
	}

	public static void removeTab(int index) {
		boolean wasCurrent = getTabIndex(TabManager.getCurrentTab()) == index;

		tabs.remove(index);
		runModifierListeners();

		if(wasCurrent) {
			selectNearestTab(index);
		}
	}

	private static void selectNearestTab(int index) {
		if(tabs.isEmpty()) {
			createTab(true);
			return;
		}

		var nearestTab = getNearestTab(index);
		if(nearestTab != null) {
			TabManager.setCurrentTab(nearestTab);
			return;
		}

		TabManager.setCurrentTab(tabs.get(0));
	}

	public static Tab getNearestTab(int index) {
		var previousTab = getTab(index - 1);
		if(previousTab != null) return previousTab;

		return getTab(index);
	}

	@Nullable
	@Contract(pure = true)
	public static Tab getTab(int index) {
		try {
			return tabs.get(index);
		} catch(IndexOutOfBoundsException e) {
			return null;
		}
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

	public static void clearTabs() {
		for(var tab : tabs) {
			tab.dispose();
		}

		tabs.clear();
		runModifierListeners();
		selectNearestTab(-1);
	}

	public static void setTabs(List<Tab> newTabs) {
		clearTabs();
		tabs.addAll(newTabs);
		runModifierListeners();
	}

	private static void runModifierListeners() {
		for(var listener : TabManager.getTabListeners()) {
			listener.onTabListModified();
		}
	}
}