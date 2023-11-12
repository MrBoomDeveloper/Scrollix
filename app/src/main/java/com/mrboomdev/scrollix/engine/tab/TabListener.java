package com.mrboomdev.scrollix.engine.tab;

public interface TabListener {

	default void onTabLoaded(Tab tab) {}

	default void onTabFocused(Tab tab) {}

	default void onTabListModified() {}
}