package com.mrboomdev.scrollix;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.data.AppSettings;
import com.mrboomdev.scrollix.data.TabsManager;
import com.mrboomdev.scrollix.ui.layout.SearchLayout;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
	public SearchLayout searchLayout;
	private TextView tabsCounter;
	private AppSettings appSettings;
	private LinearProgressIndicator progressIndicator;
	private WebView webView;
	private MyWebChromeClient chromeClient;
	private MyWebViewClient webViewClient;
	private SearchBarWidget searchBar;
	private LinearLayout topbar, bottombar, sidebar;

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		reloadLayout();
	}

	@SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		appSettings = new AppSettings();
		applyAppSettings(appSettings);

		ConstraintLayout parent = findViewById(R.id.main_screen_parent);
		topbar = findViewById(R.id.top_bar);
		bottombar = findViewById(R.id.bottom_bar);
		sidebar = findViewById(R.id.sidebar);

		webView = findViewById(R.id.webview);
		webView.loadUrl("file:///android_asset/pages/home.html");

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

		SwipeRefreshLayout swipeRefresher = findViewById(R.id.swipeRefresher);
		swipeRefresher.setOnRefreshListener(() -> {
			var url = Objects.requireNonNull(webView.getUrl());

			if(url.startsWith("file://")) {
				webView.loadUrl(url);
				return;
			}

			webView.reload();
		});

		progressIndicator = findViewById(R.id.progressIndicator);

		chromeClient = new MyWebChromeClient();
		chromeClient.setProgressIndicator(progressIndicator);

		webViewClient = new MyWebViewClient(this);
		webViewClient.setProgressIndicator(progressIndicator);
		webViewClient.setRefreshLayout(swipeRefresher);

		webView.setWebViewClient(webViewClient);
		webView.setWebChromeClient(chromeClient);

		reloadLayout();
		applyTheme();

		ScrollListener scrollListener = new ScrollListener();
		swipeRefresher.setOnTouchListener(scrollListener);
		webView.setOnTouchListener(scrollListener);

		searchLayout = new SearchLayout(this, appSettings);
		searchLayout.setVisibility(View.GONE, false);
		searchLayout.setLaunchLinkListener(webView::loadUrl);

		var searchLayoutParams = new ConstraintLayout.LayoutParams(0, 0);
		searchLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;

		parent.addView(searchLayout, searchLayoutParams);
	}

	public void applyTheme() {
		var window = getWindow();

		window.setStatusBarColor(Color.parseColor("#222222"));
		window.setNavigationBarColor(Color.parseColor("#222222"));

		window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#222222")));

		if(Build.VERSION.SDK_INT >= 28) {
			var attrs = window.getAttributes();
			attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			window.setAttributes(attrs);
		}
	}

	private int getSizeForButton(boolean isSmall) {
		return (int)TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP,
				isSmall ? 34 : 38,
				getResources().getDisplayMetrics());
	}

	public void reloadLayout() {
		topbar.removeAllViews();
		bottombar.removeAllViews();
		sidebar.removeAllViews();

		var config = getResources().getConfiguration();
		boolean isLandscape = (config.orientation == Configuration.ORIENTATION_LANDSCAPE);

		for(var item : appSettings.leftActions) {
			var view = createActionButton(item);

			int size = getSizeForButton(isLandscape);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, size);
			if(!isLandscape) params.weight = 1;
			params.setMargins(12, 0, 10, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		if(isLandscape && !appSettings.menuActions.isEmpty()) {
			sidebar.setVisibility(View.VISIBLE);

			for(var item : appSettings.menuActions) {
				var view = createActionButton(item);

				int size = getSizeForButton(true);
				var params = new LinearLayout.LayoutParams(size, size);
				params.setMargins(0, 12, 0, 12);

				sidebar.addView(view, params);
			}
		} else {
			sidebar.setVisibility(View.GONE);
		}

		searchBar = new SearchBarWidget(this, webView);
		searchBar.setTitle(webView.getTitle());
		topbar.addView(searchBar);

		searchBar.setOnClickListener(view -> searchLayout.show());
		if(!isLandscape) searchBar.setPadding(16, 0, 16, 0);

		for(var item : appSettings.rightActions) {
			var view = createActionButton(item);

			int size = getSizeForButton(isLandscape);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, size);
			if(!isLandscape) params.weight = 1;
			params.setMargins(10, 0, 12, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		webViewClient.setSearchBar(searchBar);
		chromeClient.setSearchBar(searchBar);

		bottombar.setVisibility(!isLandscape ? View.VISIBLE : View.GONE);
	}

	@NonNull
	private View createActionButton(@NonNull String name) {
		int icon = R.drawable.ic_close_black, primaryColor = Color.parseColor("#ccccdd");
		var button = new ImageView(this);
		var circleRipple = ResourcesCompat.getDrawable(getResources(), R.drawable.ripple_circle, getTheme());

		switch(name) {
			case "home" -> {
				button.setOnClickListener(view -> webView.loadUrl("file:///android_asset/pages/home.html"));
				icon = R.drawable.ic_home_black;
			}

			case "settings" -> {
				icon = R.drawable.ic_settings_black;
				button.setOnClickListener(view -> webView.loadUrl("file:///android_asset/pages/settings.html"));
			}

			case "downloads" -> {
				icon = R.drawable.ic_download_black;
			}

			case "history" -> {
				icon = R.drawable.ic_history_black;
			}

			case "bookmarks" -> {
				icon = R.drawable.ic_star_black;
			}

			case "menu" -> {
				icon = R.drawable.ic_menu_black;
			}

			case "back" -> {
				icon = R.drawable.ic_back_black;
				button.setScaleX(.8f);
				button.setScaleY(.8f);
				button.setOnClickListener(view -> webView.goBack());
			}

			case "next" -> {
				icon = R.drawable.ic_back_black;
				button.setScaleY(.8f);
				button.setScaleX(-.8f);
				button.setOnClickListener(view -> webView.goForward());
			}

			case "tabs" -> {
				icon = R.drawable.ic_tabs_black;
			}

			default -> button.setOnClickListener(view -> {
				String message = "Unknown action, please check your settings!";
				Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
			});
		}

		button.setScaleType(ImageView.ScaleType.FIT_CENTER);
		button.setBackground(circleRipple);
		button.setClickable(true);
		button.setFocusable(true);
		button.setPadding(8, 8, 8, 8);
		
		var buttonIcon = ResourcesCompat.getDrawable(getResources(), icon, getTheme());
		DrawableCompat.setTint(Objects.requireNonNull(buttonIcon), primaryColor);
		button.setImageDrawable(buttonIcon);

		if(name.equals("tabs")) {
			var parent = new FrameLayout(this);
			button.setScaleX(1.1f);
			button.setScaleY(1.1f);
			parent.addView(button);

			tabsCounter = new TextView(this);
			tabsCounter.setText(String.valueOf(TabsManager.getCount()));
			tabsCounter.setTextSize(13);
			tabsCounter.setGravity(Gravity.CENTER);
			parent.addView(tabsCounter, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

			return parent;
		}

		return button;
	}

	public void applyAppSettings(AppSettings settings) {

	}

	private void restartLoadingIndicator() {
		progressIndicator.setVisibility(View.VISIBLE);
		progressIndicator.setProgress(0);
	}

	@Override
	public void onBackPressed() {
		if(searchLayout.isOpened()) {
			searchLayout.hide();
			return;
		}

		if(webView.canGoBack()) {
			webView.goBack();
		} else {
			finishAffinity();
		}
	}

	private class ScrollListener implements View.OnTouchListener {
		private final BarAnimation topBarAnimation, bottomBarAnimation;
		private boolean isExpanded = true;
		private float startY;

		public ScrollListener() {
			topBarAnimation = new BarAnimation(topbar, Gravity.TOP);
			bottomBarAnimation = new BarAnimation(bottombar, Gravity.BOTTOM);
		}

		@Override
		public boolean onTouch(View view, @NonNull MotionEvent event) {
			switch(event.getAction()) {
				case MotionEvent.ACTION_MOVE -> {
					boolean shouldExpand = event.getY() > startY;

					if(shouldExpand != isExpanded && Math.abs(startY - event.getY()) > 50) {
						isExpanded = shouldExpand;

						topBarAnimation.cancel();
						bottomBarAnimation.cancel();

						topBarAnimation.startAnimation(isExpanded);
						bottomBarAnimation.startAnimation(isExpanded);
					}
				}

				case MotionEvent.ACTION_UP -> view.performClick();

				case MotionEvent.ACTION_DOWN -> startY = event.getY();
			}

			return false;
		}
	}

	private static class BarAnimation extends Animation {
		private final View view;
		private final int gravity;
		private boolean show;

		public BarAnimation(View view, int gravity) {
			this.view = view;
			this.gravity = gravity;

			setDuration(150);
			setInterpolator(new AccelerateDecelerateInterpolator());
		}

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			float hideOffset = -view.getHeight();

			var currentTranslation = show
					? (hideOffset + interpolatedTime * -hideOffset)
					: -hideOffset * interpolatedTime * -1;

			var params = (ConstraintLayout.LayoutParams)view.getLayoutParams();

			if(gravity == Gravity.TOP) {
				params.topMargin = Math.round(currentTranslation);
			} else {
				params.bottomMargin = Math.round(currentTranslation);
			}

			view.setLayoutParams(params);
		}

		public void startAnimation(boolean show) {
			this.show = show;
			view.startAnimation(this);
		}
	}
}