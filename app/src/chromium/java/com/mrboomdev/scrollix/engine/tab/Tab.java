package com.mrboomdev.scrollix.engine.tab;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.webkit.WebView;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.download.UserMadeDownload;
import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.engine.tab.client.MyDownloadListener;
import com.mrboomdev.scrollix.engine.tab.client.MyWebChromeClient;
import com.mrboomdev.scrollix.engine.tab.client.MyWebViewClient;
import com.mrboomdev.scrollix.ui.IncognitoActivity;
import com.mrboomdev.scrollix.ui.popup.ContextMenu;
import com.mrboomdev.scrollix.util.AndroidUtil;
import com.mrboomdev.scrollix.util.LinkUtil;

import java.util.Objects;

public class Tab {
	private WebView webView;
	private String url, title;
	private boolean didInit;

	public Tab(String url, boolean lateInit) {
		this.url = url;

		if(!lateInit) init();
	}

	public Tab(String url) {
		this(url, false);
	}

	public Tab(boolean lateInit) {
		this(null, lateInit);
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setUrl(String url) {
		this.title = url;
		this.url = url;
	}

	public boolean didInit() {
		return didInit;
	}

	@SuppressLint("ClickableViewAccessibility")
	public void init() {
		if(didInit) return;
		didInit = true;

		var context = AppManager.getActivityContext();
		var barsAnimator = TabManager.barsAnimator;

		webView = new WebView(context);
		webView.setWebViewClient(new MyWebViewClient(this));
		webView.setWebChromeClient(new MyWebChromeClient(this));
		webView.setDownloadListener(new MyDownloadListener());
		applySettings();

		if(barsAnimator != null) {
			webView.setOnTouchListener(barsAnimator.getOnTouchListener());
		}

		webView.setOnLongClickListener(view -> {
			var test = webView.getHitTestResult();
			int type = test.getType();

			if(type != WebView.HitTestResult.SRC_ANCHOR_TYPE
					&& type != WebView.HitTestResult.IMAGE_TYPE
					&& type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false;

			var handler = new Handler();
			var linkMessage = handler.obtainMessage();
			var imageMessage = handler.obtainMessage();

			var menu = new ContextMenu.Builder(AppManager.getActivityContext())
					.setDismissOnSelect(true);

			switch(type) {
				case WebView.HitTestResult.IMAGE_TYPE -> webView.requestImageRef(imageMessage);

				case WebView.HitTestResult.SRC_ANCHOR_TYPE -> webView.requestFocusNodeHref(linkMessage);

				case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
					webView.requestFocusNodeHref(linkMessage);
					webView.requestImageRef(imageMessage);
				}
			}

			var link = linkMessage.getData().getString("url");
			if(link != null) {
				menu.setLink(link);

				menu.addAction("Open link in new tab", () -> TabStore.createTab(link, true));
				menu.addAction("Open link in background", () -> TabStore.createTab(link, false));
				menu.addAction("Open link in new incognito tab", () -> {
					var intent = new Intent(AppManager.getActivityContext(), IncognitoActivity.class);
					intent.setData(Uri.parse(link));
					AppManager.getActivityContext().startActivity(intent);
				});
				menu.addAction("Share link", () -> AndroidUtil.share("Share link", link));
				menu.addAction("Copy link to clipboard", () -> AndroidUtil.copyToClipboard(link));
			}

			var image = imageMessage.getData().getString("url");
			if(image != null) {
				menu.setImage(image);

				menu.addAction("Open image in new tab", () -> TabStore.createTab(image, true));
				menu.addAction("Open image in background", () -> TabStore.createTab(image, false));
				menu.addAction("Open image in new incognito tab", () -> {
					var intent = new Intent(AppManager.getActivityContext(), IncognitoActivity.class);
					intent.setData(Uri.parse(image));
					AppManager.getActivityContext().startActivity(intent);
				});
				menu.addAction("Download image", () -> new UserMadeDownload(image).start());
				menu.addAction("Share image", () -> AndroidUtil.share("Share image link", image));
				menu.addAction("Copy image link to clipboard", () -> AndroidUtil.copyToClipboard(image));
			}

			menu.build();

			return false;
		});

		if(url == null) {
			url = EngineInternal.Link.HOME.getRealUrl();
		}

		loadUrl(url);
	}

	public void loadUrl(String url) {
		this.url = url;
		webView.loadUrl(url);
	}

	public boolean canGoBack() {
		return getWebView().canGoBack();
	}

	public boolean canGoForward() {
		return getWebView().canGoForward();
	}

	public void goBack() {
		getWebView().goBack();
	}

	public void goForward() {
		getWebView().goForward();
	}

	public void stopLoading() {
		getWebView().stopLoading();
	}

	public void reload() {
		getWebView().reload();
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