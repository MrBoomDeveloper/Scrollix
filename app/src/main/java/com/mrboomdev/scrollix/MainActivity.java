package com.mrboomdev.scrollix;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;
import com.mrboomdev.scrollix.util.FormatUtil;

public class MainActivity extends AppCompatActivity {
	private LinearProgressIndicator progressIndicator;
	private WebView webView;
	private MyWebChromeClient chromeClient;
	private MyWebViewClient webViewClient;
	private SearchBarWidget searchBar;

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		reloadLayout();
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		webView = findViewById(R.id.webview);
		webView.loadUrl("file:///android_asset/pages/home.html");

		var settings = webView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDatabaseEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setMediaPlaybackRequiresUserGesture(false);
		settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.114 Mobile Safari/537.36");
		settings.setSupportZoom(true);
		settings.setBuiltInZoomControls(true);
		settings.setDisplayZoomControls(false);
		settings.setAllowFileAccess(true);

		SwipeRefreshLayout swipeRefresher = findViewById(R.id.swipeRefresher);
		swipeRefresher.setOnRefreshListener(() -> webView.reload());

		progressIndicator = findViewById(R.id.progressIndicator);

		chromeClient = new MyWebChromeClient();
		chromeClient.setProgressIndicator(progressIndicator);

		webViewClient = new MyWebViewClient();
		webViewClient.setProgressIndicator(progressIndicator);
		webViewClient.setRefreshLayout(swipeRefresher);

		webView.setWebViewClient(webViewClient);
		webView.setWebChromeClient(chromeClient);

		webView.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
			//Toast.makeText(this, y + " : " + oldY, Toast.LENGTH_SHORT).show();
		});

		reloadLayout();
	}

	public void reloadLayout() {
		LinearLayout topbar = findViewById(R.id.top_bar);
		LinearLayout bottombar = findViewById(R.id.bottom_bar);
		LinearLayout sidebar = findViewById(R.id.sidebar);

		topbar.removeAllViews();
		bottombar.removeAllViews();
		sidebar.removeAllViews();

		var config = getResources().getConfiguration();
		boolean isLandscape = (config.orientation == Configuration.ORIENTATION_LANDSCAPE);

		searchBar = new SearchBarWidget(this);
		searchBar.setOnEnterListener(request -> {
			if(FormatUtil.isUrlValid(request)) {
				webView.loadUrl(request);
				return;
			}

			webView.loadUrl("https://startpage.com/do/search?query=" + request);
		});

		webViewClient.setSearchBar(searchBar);
		topbar.addView(searchBar);

		sidebar.setVisibility(isLandscape ? View.VISIBLE : View.GONE);
		bottombar.setVisibility(!isLandscape ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onBackPressed() {
		if(webView != null) {
			if(webView.canGoBack()) {
				webView.goBack();
			} else {
				finishAffinity();
			}
		}
	}
}