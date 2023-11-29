package com.mrboomdev.scrollix.engine.tab.client;

import android.graphics.Bitmap;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabManager;

public class MyWebViewClient extends WebViewClient {
	private final Tab tab;

	public MyWebViewClient(Tab tab) {
		this.tab = tab;
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		tab.setUrl(url);

		for(var listener : TabManager.getTabListeners()) {
			listener.onTabLoadingStarted(tab);
		}
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		tab.setUrl(url);

		for(var listener : TabManager.getTabListeners()) {
			listener.onTabLoadingFinished(tab);
		}
	}
}