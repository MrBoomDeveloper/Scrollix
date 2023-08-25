package com.mrboomdev.scrollix;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;
import com.mrboomdev.scrollix.util.FormatUtil;

public class MyWebViewClient extends WebViewClient {
	private SwipeRefreshLayout swipeRefresher;
	private LinearProgressIndicator progressIndicator;
	private SearchBarWidget searchBar;
	private final FormatUtil.UrlFormatRules formatRules;

	public MyWebViewClient() {
		formatRules = new FormatUtil.UrlFormatRules();
		formatRules.removeHash = true;
		formatRules.removeWww = true;
		formatRules.removeProtocol = true;
	}

	public void setRefreshLayout(SwipeRefreshLayout layout) {
		this.swipeRefresher = layout;
	}

	public void setSearchBar(SearchBarWidget searchBar) {
		this.searchBar = searchBar;
	}

	public void setProgressIndicator(LinearProgressIndicator indicator) {
		this.progressIndicator = indicator;
	}

	@Override
	public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
		super.onReceivedHttpError(view, request, errorResponse);
	}

	@Override
	public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
		super.onReceivedError(view, request, error);
	}

	@Override
	public void onLoadResource(WebView view, String url) {
		super.onLoadResource(view, url);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		if(progressIndicator != null) {
			progressIndicator.setVisibility(View.GONE);
		}

		if(swipeRefresher != null && swipeRefresher.isRefreshing()) {
			swipeRefresher.setRefreshing(false);
		}
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if(progressIndicator != null) {
			progressIndicator.setVisibility(View.VISIBLE);
		}

		if(searchBar != null) {
			searchBar.setTitle(FormatUtil.formatUrl(url, formatRules));
			searchBar.setUrl(url);
		}
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
		return super.shouldOverrideUrlLoading(view, request);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		super.onReceivedSslError(view, handler, error);
	}
}