package com.mrboomdev.scrollix;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.util.LinkUtil;

import org.jetbrains.annotations.Contract;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import java.util.Objects;

public class MyWebViewClient extends WebViewClient {
	private Tab tab;
	public final LinkUtil.UrlFormatRules formatRules;

	public MyWebViewClient(Tab tab) {
		this.tab = tab;

		formatRules = new LinkUtil.UrlFormatRules();
		formatRules.removeHash = true;
		formatRules.removeWww = true;
		formatRules.removeProtocol = true;
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
	public void onPageFinished(WebView view, String url) {
		tab.runCallbacks(tab.onFinishedCallbacks);
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		tab.favicon = null;
		tab.setUrl(url);

		tab.runCallbacks(tab.onStartedCallbacks);
		tab.runCallbacks(tab.onFaviconCallbacks);
	}

	@Override
	public boolean shouldOverrideUrlLoading(WebView view, @NonNull WebResourceRequest request) {
		var uri = request.getUrl();
		var scheme = Objects.requireNonNull(uri.getScheme());

		switch(scheme) {
			case "http", "https", "file" -> {
				return false;
			}

			case "intent" -> {
				try {
					var context = view.getContext();
					Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);

					if(intent != null) {
						var packageManager = context.getPackageManager();
						var info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

						if(info != null) {
							context.startActivity(intent);
							return true;
						}

						String fallbackUrl = intent.getStringExtra("browser_fallback_url");

						if(fallbackUrl != null) {
							view.loadUrl(fallbackUrl);
						} else {
							Toast.makeText(view.getContext(), "Required app was not found", Toast.LENGTH_LONG).show();
						}
					} else {
						Toast.makeText(view.getContext(), "Invalid app link", Toast.LENGTH_LONG).show();
					}
				} catch(URISyntaxException e) {
					Toast.makeText(view.getContext(), "Required app was not found", Toast.LENGTH_LONG).show();
				}

				return true;
			}

			default -> {
				Toast.makeText(view.getContext(), "Unknown link protocol", Toast.LENGTH_LONG).show();
				return true;
			}
		}
	}

	@Nullable
	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
		//return getEmptyResponse();

		return super.shouldInterceptRequest(view, request);
	}

	@NonNull
	@Contract(" -> new")
	private WebResourceResponse getEmptyResponse() {
		var stream = new ByteArrayInputStream("".getBytes());
		return new WebResourceResponse("text/plain", "utf-8", stream);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		super.onReceivedSslError(view, handler, error);
	}
}