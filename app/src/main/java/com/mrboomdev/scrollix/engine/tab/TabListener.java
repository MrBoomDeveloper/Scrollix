package com.mrboomdev.scrollix.engine.tab;

public interface TabListener {

	default void onTabLoadingStarted(Tab tab) {}

	default void onTabLoadingFinished(Tab tab) {}

	default void onTabFullscreenToggle(Tab tab, boolean isFullscreen) {}

	default void onTabGotTitle(Tab tab, String title) {}

	default void onTabLoadingProgress(Tab tab, int progress) {}

	default void onTabFocused(Tab tab) {}

	default void onTabListModified() {}
}