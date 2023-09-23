package com.mrboomdev.scrollix;

import android.graphics.Bitmap;
import android.os.Message;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;

public class MyWebChromeClient extends WebChromeClient {
	private LinearProgressIndicator progressIndicator;
	private SearchBarWidget searchBar;

	public void setProgressIndicator(LinearProgressIndicator indicator) {
		this.progressIndicator = indicator;
	}

	public void setSearchBar(SearchBarWidget searchBar) {
		this.searchBar = searchBar;
	}

	@Override
	public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
		return super.onJsPrompt(view, url, message, defaultValue, result);
	}

	@Override
	public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
		return super.onJsAlert(view, url, message, result);
	}

	@Override
	public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
		return super.onConsoleMessage(consoleMessage);
	}

	@Override
	public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
		return super.onJsConfirm(view, url, message, result);
	}

	@Override
	public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
		return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
	}

	@Override
	public void onReceivedTitle(WebView view, String title) {
		if(searchBar != null) {
			searchBar.setTitle(title);
		}
	}

	@Override
	public void onReceivedIcon(WebView view, Bitmap icon) {
		super.onReceivedIcon(view, icon);
	}

	@Override
	public void onProgressChanged(WebView view, int newProgress) {
		if(progressIndicator != null) {
			progressIndicator.setProgress(newProgress, true);
		}
	}
}