package com.mrboomdev.scrollix.webview;

import android.annotation.SuppressLint;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.util.LinkUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

@Deprecated
public class MyWebViewClient extends WebViewClient {
	private final Tab tab;

	public MyWebViewClient(Tab tab) {
		this.tab = tab;
	}

	@Override
	public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
		//view.post(() -> Toast.makeText(view.getContext(), "HttpError: " + request.getUrl(), Toast.LENGTH_LONG).show());
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, WebResourceError error) {
		if(request.isForMainFrame()) {
			view.loadUrl(LinkUtil.ScrollixUrls.ERROR.getFullUrl());
		}
	}

	@Override
	public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {

	}

	@Override
	public void onPageFinished(WebView view, String url) {
		if(!Objects.equals(url, LinkUtil.ScrollixUrls.ERROR.getFullUrl())) {
			tab.setUrl(url);
		}

		tab.runCallbacks(tab.onFinishedCallbacks);
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if(!Objects.equals(url, LinkUtil.ScrollixUrls.ERROR.getFullUrl())) {
			tab.setUrl(url);
		}

		tab.favicon = null;
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
					var context = AppManager.getActivityContext();
					Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);

					if(intent == null) {
						Toast.makeText(view.getContext(), "Invalid app link", Toast.LENGTH_LONG).show();
						return true;
					}

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
				} catch(URISyntaxException e) {
					e.printStackTrace();
					Toast.makeText(view.getContext(), "Invalid app link", Toast.LENGTH_LONG).show();
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
	public WebResourceResponse shouldInterceptRequest(@NonNull WebView view, @NonNull WebResourceRequest request) {
		//return getEmptyResponse();

		return super.shouldInterceptRequest(view, request);
	}

	@Nullable
	private WebResourceResponse getEmptyResponse() {
		try(var stream = new ByteArrayInputStream("".getBytes())) {
			return new WebResourceResponse("text/plain", "utf-8", stream);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@SuppressLint("WebViewClientOnReceivedSslError")
	@Override
	public void onReceivedSslError(WebView view, @NonNull SslErrorHandler handler, @NonNull SslError error) {
		var certificate = error.getCertificate();

		var description = "Url: " + error.getUrl() + "\n" +
				"Certificate issued by: " + certificate.getIssuedBy() + "\n" +
				"Certificate valid until: " + certificate.getValidNotAfterDate() + "\n" +
				"Certificate valid from: " + certificate.getValidNotBeforeDate();

		AppManager.getActivityContext().runOnUiThread(() -> {
			new MaterialAlertDialogBuilder(view.getContext())
					.setTitle("Ssl error has happened!")
					.setMessage("You can either continue, or cancel the request. \n\n" + description)
					.setPositiveButton("Proceed", (_dialog, _button) -> {
						handler.proceed();
						_dialog.cancel();
					})
					.setNegativeButton("Cancel", (_dialog, _button) -> {
						handler.cancel();
						_dialog.cancel();
					})
					.show();
		});
	}
}