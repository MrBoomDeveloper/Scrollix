package com.mrboomdev.scrollix.engine.tab;

import android.annotation.SuppressLint;
import android.webkit.WebView;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.util.LinkUtil;

import java.util.Objects;

public class Tab {
	private WebView webView;
	private String url;
	private boolean didInit;

	public Tab(String url, boolean lateInit) {
		this.url = url;

		if(!lateInit) init();
	}

	public Tab(String url) {
		this(url, false);
	}

	public Tab() {
		this(null);
	}

	@SuppressLint("SetJavaScriptEnabled")
	public void applySettings() {
		var settings = webView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDatabaseEnabled(true);
		settings.setDomStorageEnabled(true);
		//settings.setSupportMultipleWindows(true);
		settings.setMediaPlaybackRequiresUserGesture(false);
		settings.setUserAgentString(LinkUtil.UserAgent.CHROME_MOBILE.getUserAgentText());
		settings.setSupportZoom(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setAllowFileAccess(true);
	}

	public void init() {
		if(didInit) return;
		didInit = true;

		var context = AppManager.getActivityContext();

		webView = new WebView(context);
		applySettings();
		if(url != null) loadUrl(url);
	}

	public void loadUrl(String url) {
		this.url = url;
		webView.loadUrl(url);
	}

	public String getUrl() {
		return Objects.requireNonNullElse(webView.getUrl(), url);
	}

	public void dispose() {
		webView = null;
	}

	protected WebView getWebView() {
		return webView;
	}
}