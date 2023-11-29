package com.mrboomdev.scrollix.engine.tab;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.ui.BarsAnimator;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.ArrayList;
import java.util.List;

public class TabManager {
	private static final List<TabListener> listeners = new ArrayList<>();
	@SuppressLint("StaticFieldLeak")
	protected static BarsAnimator barsAnimator;
	private static List<Tab> tabs;
	@SuppressLint("StaticFieldLeak")
	private static LinearLayout tabHolder;
	@SuppressLint("StaticFieldLeak")
	private static Tab currentTab;

	public static void setTabHolder(LinearLayout view) {
		tabHolder = view;
	}

	public static void addListener(TabListener listener) {
		listeners.add(listener);
	}

	public static void removeListener(TabListener listener) {
		listeners.remove(listener);
	}

	public static List<TabListener> getTabListeners() {
		return listeners;
	}

	public static Tab getCurrentTab() {
		return currentTab;
	}

	@SuppressLint("ClickableViewAccessibility")
	public static void setBarsAnimator(BarsAnimator animator) {
		barsAnimator = animator;

		for(var tab : TabStore.getAllTabs()) {
			if(!tab.didInit()) continue;
			tab.getWebView().setOnTouchListener(animator.getOnTouchListener());
		}
	}

	public static void setCurrentTab(@NonNull Tab tab) {
		currentTab = tab;
		tab.init();

		for(var _tab : tabs) {
			var webView = _tab.getWebView();
			if(webView == null) continue;

			webView.setVisibility(View.GONE);
		}

		if(!tabs.contains(tab)) {
			tabs.add(tab);

			var parent = tab.getWebView().getParent();
			if(parent instanceof LinearLayout linear) {
				linear.removeView(linear);
			}

			var webView = tab.getWebView();
			tabHolder.addView(webView, Formats.MATCH_PARENT, Formats.MATCH_PARENT);
		}

		tab.getWebView().setVisibility(View.VISIBLE);
	}

	public static void startup() {
		tabs = new ArrayList<>();

		TabStore.addTab(new Tab(EngineInternal.Link.HOME.getRealUrl()));
	}

	public static void dispose() {
		TabStore.clearTabs();
		tabs.clear();

		tabHolder = null;
		barsAnimator = null;
		currentTab = null;
	}
}