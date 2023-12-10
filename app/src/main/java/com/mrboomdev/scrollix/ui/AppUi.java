package com.mrboomdev.scrollix.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.layout.SearchLayout;
import com.mrboomdev.scrollix.ui.popup.ActionsMenu;
import com.mrboomdev.scrollix.ui.popup.TabsMenu;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;

@SuppressLint("StaticFieldLeak")
public class AppUi {
	public static LinearLayout topbar, sidebar, bottombar;
	public static ConstraintLayout parent;
	public static TabsMenu tabsMenu;
	public static ActionsMenu actionsMenu;
	public static BarsAnimator barsAnimator;
	public static SearchBarWidget searchBar;
	public static SearchLayout searchLayout;
	public static View backButton, forwardButton;
	public static TextView tabsCounter;
	public static LinearProgressIndicator progressIndicator;
	public static boolean isFullscreen, wasPausedDuringFullscreen, barsWereExpanded, barsWereExpandable;

	public static void updateBackForwardState() {
		var currentTab = TabManager.getCurrentTab();
		if(currentTab == null) return;

		if(backButton != null) {
			backButton.setAlpha(currentTab.canGoBack() ? 1 : .6f);
		}

		if(forwardButton != null) {
			forwardButton.setAlpha(currentTab.canGoForward() ? 1 : .6f);
		}
	}

	public static void toggleFullscreen(Tab tab, boolean isFullscreen) {
		if(tab != TabManager.getCurrentTab()) return;
		AppUi.isFullscreen = isFullscreen;

		var activity = AppManager.getActivityContext();
		var insets = new WindowInsetsControllerCompat(activity.getWindow(), activity.findViewById(R.id.main_screen_parent));
		var toggleableInsets = WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars();

		if(!isFullscreen) {
			insets.show(toggleableInsets);

			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
			}

			if(wasPausedDuringFullscreen) {
				wasPausedDuringFullscreen = false;

				toggleFullscreen(tab, true);
				toggleFullscreen(tab, false);
			}
		} else {
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				activity.getWindow().setFlags(
						WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
						WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
			}

			insets.hide(toggleableInsets);
		}

		AppManager.getMainActivityContext().updateBarsVisibility();

		if(isFullscreen) {
			barsWereExpanded = AppUi.barsAnimator.isExpanded();
			barsWereExpandable = AppUi.barsAnimator.isExpandable();

			AppUi.barsAnimator.setIsExpandedImmediately(false);
			AppUi.barsAnimator.setIsExpandable(false);
		} else {
			AppUi.barsAnimator.setIsExpandable(barsWereExpandable);
			AppUi.barsAnimator.setIsExpandedImmediately(barsWereExpanded);
		}
	}

	public static void focusTab(Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;

		if(searchBar != null) searchBar.setUrl(tab.getUrl());
		if(searchLayout != null) searchLayout.setUrl(tab.getUrl());
	}

	public static void updateTabLoading(Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;

		if(tab.getProgress() == 0) {
			startTabLoading(tab);
			return;
		}

		if(tab.getProgress() == 100) {
			finishTabLoading(tab);
			return;
		}

		showLoadingUi();

		if(progressIndicator != null) {
			progressIndicator.setProgress(tab.getProgress(), true);
		}
	}

	private static void showLoadingUi() {
		if(searchBar != null) searchBar.setIsLoading(true);
		if(progressIndicator != null) progressIndicator.setVisibility(View.VISIBLE);
	}

	public static void startTabLoading(@NonNull Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;

		if(progressIndicator != null) progressIndicator.setProgress(0);
		if(searchLayout != null) searchLayout.setUrl(tab.getUrl());
		if(searchBar != null) searchBar.setUrl(tab.getUrl());

		showLoadingUi();
	}

	public static void finishTabLoading(@NonNull Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;

		if(progressIndicator != null) progressIndicator.setVisibility(View.GONE);
		if(searchLayout != null) searchLayout.setUrl(tab.getUrl());

		if(searchBar != null) {
			searchBar.setIsLoading(false);
			searchBar.setUrl(tab.getUrl());
		}
	}

	public static void updateTabsListState() {
		if(tabsCounter == null) return;

		var count = String.valueOf(TabStore.getTabCount());
		tabsCounter.setText(count);
	}

	public static void dispose() {
		if(tabsMenu != null) tabsMenu.close();
		if(actionsMenu != null) actionsMenu.close();

		tabsMenu = null;
		actionsMenu = null;

		barsAnimator = null;

		progressIndicator = null;
		searchBar = null;
		searchLayout = null;

		backButton = null;
		forwardButton = null;
		tabsCounter = null;

		topbar = null;
		sidebar = null;
		bottombar = null;
		parent = null;
	}
}