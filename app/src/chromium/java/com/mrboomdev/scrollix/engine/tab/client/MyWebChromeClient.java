package com.mrboomdev.scrollix.engine.tab.client;

import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;

public class MyWebChromeClient extends WebChromeClient {
	private final Tab tab;

	public MyWebChromeClient(Tab tab) {
		this.tab = tab;
	}

	@Override
	public void onRequestFocus(WebView view) {
		TabManager.setCurrentTab(tab);
	}

	@Override
	public void onCloseWindow(WebView window) {
		TabStore.removeTab(tab);
	}

	@Override
	public void onReceivedTitle(WebView view, String title) {
		for(var listener : TabManager.getTabListeners()) {
			tab.setTitle(title);
			listener.onTabGotTitle(tab, title);
		}
	}

	@Override
	public void onProgressChanged(WebView view, int newProgress) {
		for(var listener : TabManager.getTabListeners()) {
			listener.onTabLoadingProgress(tab, newProgress);
		}
	}
}