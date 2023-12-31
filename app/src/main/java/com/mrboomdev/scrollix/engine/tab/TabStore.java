package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.ui.AppUi;

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

	public static boolean hasTab(Tab tab) {
		for(var _tab : tabs) {
			if(tab == _tab) return true;
		}

		return false;
	}

	public static void move(int from, int to) {
		if(from == to) return;
		var tab = getTab(from);

		removeTab(tab, false);
		addTab(tab, to);
	}

	@NonNull
	public static Tab createTab(boolean focus) {
		return createTab(null, focus);
	}

	@NonNull
	public static Tab createTab() {
		return createTab(true);
	}

	public static void removeTab(int index, boolean sideEffects) {
		if(sideEffects) {
			boolean wasCurrent = getTabIndex(TabManager.getCurrentTab()) == index;

			if(wasCurrent) {
				selectNearestTab(index);
			}
		}

		tabs.remove(index);
		runModifierListeners();

		if(sideEffects) {
			//TODO: Use value from settings if user want to create a new tab or close app
			doActionIfEmpty(true);
		}
	}

	public static void removeTab(int index) {
		removeTab(index, true);
	}

	public static void removeTab(Tab tab, boolean sideEffects) {
		var index = getTabIndex(tab);

		if(index != -1) {
			removeTab(index, sideEffects);
		}
	}

	public static void removeTab(Tab tab) {
		removeTab(tab, true);
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

		if(!newTabs.isEmpty()) {
			TabManager.setCurrentTab(newTabs.get(0));
		}

		runModifierListeners();
	}

	private static void runModifierListeners() {
		AppUi.updateTabsListState();
	}
}