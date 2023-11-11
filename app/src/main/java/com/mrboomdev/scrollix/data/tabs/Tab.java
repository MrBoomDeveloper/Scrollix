package com.mrboomdev.scrollix.data.tabs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.util.LinkUtil;
import com.mrboomdev.scrollix.webview.MyDownloadListener;
import com.mrboomdev.scrollix.webview.MyWebChromeClient;
import com.mrboomdev.scrollix.webview.MyWebViewClient;
import com.mrboomdev.scrollix.webview.ScrollixJsBridge;

import java.util.ArrayList;
import java.util.List;

public class Tab {
	public static final String SCROLLIX_HOME = "file:///android_asset/pages/home.html";
	public String url, title, previousUrl;
	public Bitmap favicon;
	public int progress;
	public WebView webView;
	public final List<TabCallback> onStartedCallbacks, onProgressCallbacks, onFinishedCallbacks, onDisposeCallbacks;
	public final List<TabCallback> onFaviconCallbacks, onTitleCallbacks;

	public Tab(Context context) {
		webView = new WebView(context);
		webView.setTag(webView.hashCode());
		webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

		var downloadListener = new MyDownloadListener(context);
		webView.setDownloadListener(downloadListener);

		var webViewClient = new MyWebViewClient(this);
		webView.setWebViewClient(webViewClient);

		var webChromeClient = new MyWebChromeClient(this);
		webView.setWebChromeClient(webChromeClient);

		var scrollixJsBridge = new ScrollixJsBridge(this);
		webView.addJavascriptInterface(scrollixJsBridge, "scrollix");

		onStartedCallbacks = new ArrayList<>();
		onProgressCallbacks = new ArrayList<>();
		onFinishedCallbacks = new ArrayList<>();
		onDisposeCallbacks = new ArrayList<>();

		onFaviconCallbacks = new ArrayList<>();
		onTitleCallbacks = new ArrayList<>();
	}

	public void dispose() {
		runCallbacks(onDisposeCallbacks);

		var parent = (LinearLayout)webView.getParent();
		parent.removeView(webView);

		onStartedCallbacks.clear();
		onProgressCallbacks.clear();
		onFinishedCallbacks.clear();

		onFaviconCallbacks.clear();
		onTitleCallbacks.clear();

		webView = null;
		favicon = null;
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

		if(!LinkUtil.isSameLink(previousUrl, url)) {
			setTitle(LinkUtil.formatUrl(url, AppManager.settings.urlFormatRules));
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