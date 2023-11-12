package com.mrboomdev.scrollix.engine.tab;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.engine.EngineInternal;

import java.util.ArrayList;
import java.util.List;

public class TabManager {
	private static List<Tab> tabs;
	@SuppressLint("StaticFieldLeak")
	private static LinearLayout tabHolder;

	public static void setTabHolder(LinearLayout view) {
		tabHolder = view;
	}

	public static void setCurrentTab(@NonNull Tab tab) {
		tab.init();

		for(var _tab : tabs) {
			_tab.getWebView().setVisibility(View.GONE);
		}

		if(!tabs.contains(tab)) {
			tabs.add(tab);

			var parent = tab.getWebView().getParent();
			if(parent instanceof LinearLayout linear) {
				linear.removeView(linear);
			}

			var webView = tab.getWebView();
			tabHolder.addView(webView);
		}
	}

	public static void startup() {
		tabs = new ArrayList<>();

		TabStore.addTab(new Tab(EngineInternal.Link.HOME.getRealUrl()));
	}

	public static void dispose() {
		TabStore.clearTabs();
		tabs.clear();
		tabHolder = null;
	}
}