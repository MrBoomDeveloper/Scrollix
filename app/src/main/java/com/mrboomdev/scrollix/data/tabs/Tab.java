package com.mrboomdev.scrollix.data.tabs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.MyWebChromeClient;
import com.mrboomdev.scrollix.MyWebViewClient;
import com.mrboomdev.scrollix.data.AppSettings;
import com.mrboomdev.scrollix.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

public class Tab {
	public String url, title, previousUrl;
	public Bitmap favicon;
	public int progress;
	public WebView webView;
	public final List<TabCallback> onStartedCallbacks, onProgressCallbacks, onFinishedCallbacks;
	public final List<TabCallback> onFaviconCallbacks, onTitleCallbacks;

	public Tab(Context context) {
		webView = new WebView(context);

		var webViewClient = new MyWebViewClient(this);
		webView.setWebViewClient(webViewClient);

		var webChromeClient = new MyWebChromeClient(this);
		webView.setWebChromeClient(webChromeClient);

		onStartedCallbacks = new ArrayList<>();
		onProgressCallbacks = new ArrayList<>();
		onFinishedCallbacks = new ArrayList<>();

		onFaviconCallbacks = new ArrayList<>();
		onTitleCallbacks = new ArrayList<>();
	}

	@SuppressLint("SetJavaScriptEnabled")
	public void reloadSettings() {
		var settings = webView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDatabaseEnabled(true);
		settings.setDomStorageEnabled(true);
		//settings.setSupportMultipleWindows(true);
		settings.setMediaPlaybackRequiresUserGesture(false);
		settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.114 Mobile Safari/537.36");
		settings.setSupportZoom(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setAllowFileAccess(true);
	}

	public void setUrl(String url) {
		this.url = url;

		if(!FormatUtil.isSameLink(previousUrl, url)) {
			setTitle(FormatUtil.formatUrl(url, AppSettings.globalSettings.urlFormatRules));
			previousUrl = url;
		}
	}

	public void setTitle(String title) {
		this.title = title;

		runCallbacks(onTitleCallbacks);
	}

	public void runCallbacks(@NonNull List<TabCallback> callbacks) {
		if(callbacks.isEmpty()) return;

		callbacks.forEach(callback -> callback.run(this));
	}

	public interface TabCallback {
		void run(Tab tab);
	}
}