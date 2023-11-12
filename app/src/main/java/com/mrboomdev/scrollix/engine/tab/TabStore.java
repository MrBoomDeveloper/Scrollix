package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public class TabStore {
	private static final List<Tab> tabs = new ArrayList<>();

	public static void addTab(Tab tab, int index) {
		tabs.add(index, tab);
	}

	public static void addTab(Tab tab) {
		tabs.add(tab);
	}

	public static void removeTab(Tab tab) {
		tabs.remove(tab);
	}

	public static void removeTab(int index) {
		tabs.remove(index);
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
	}

	public static void setTabs(List<Tab> newTabs) {
		clearTabs();
		tabs.addAll(newTabs);
	}
}