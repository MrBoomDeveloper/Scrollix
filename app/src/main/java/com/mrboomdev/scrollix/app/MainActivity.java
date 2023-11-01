package com.mrboomdev.scrollix.app;

import static android.webkit.WebView.HitTestResult;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.settings.AppSettings;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.ui.layout.SearchLayout;
import com.mrboomdev.scrollix.ui.popup.ContextMenu;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;
import com.mrboomdev.scrollix.util.AndroidUtil;
import com.mrboomdev.scrollix.util.LinkUtil;
import com.mrboomdev.scrollix.webview.MyDownloadListener;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
	public SearchLayout searchLayout;
	private SwipeRefreshLayout swipeRefreshLayout;
	private ScrollListener scrollListener;
	private Tab currentTab;
	private AppSettings appSettings;
	private LinearProgressIndicator progressIndicator;
	private WebView webView;
	private SearchBarWidget searchBar;
	private LinearLayout topbar, bottombar, sidebar;

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		var type = intent.getStringExtra("type");
		if(type == null) return;

		switch(type) {
			case "update_theme" -> reloadLayout();

			case "cancel_download" -> MyDownloadListener.ProgressListener.cancel(
					intent.getIntExtra("id", 0));

			case "error" -> {
				var title = intent.getStringExtra("title");
				var message = intent.getStringExtra("message");

				new MaterialAlertDialogBuilder(this)
					.setTitle(title)
					.setMessage(message)
					.setPositiveButton("Ok", (_dialog, _button) -> _dialog.cancel())
					.show();
			}
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		reloadLayout();
	}

	@SuppressLint({"ClickableViewAccessibility"})
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		AppManager.startup(this);
		ThemeSettings.ThemeManager.setContext(this);
		ThemeSettings.ThemeManager.addUpdateListener(() -> runOnUiThread(this::reloadLayout));

		ConstraintLayout parent = findViewById(R.id.main_screen_parent);
		topbar = findViewById(R.id.top_bar);
		bottombar = findViewById(R.id.bottom_bar);
		sidebar = findViewById(R.id.sidebar);

		progressIndicator = findViewById(R.id.progressIndicator);

		swipeRefreshLayout = findViewById(R.id.swipeRefresher);

		scrollListener = new ScrollListener();
		swipeRefreshLayout.setOnTouchListener(scrollListener);

		appSettings = new AppSettings();

		var formatRules = new LinkUtil.UrlFormatRules();
		formatRules.removeHash = true;
		formatRules.removeWww = true;
		formatRules.removeProtocol = true;

		appSettings.urlFormatRules = formatRules;
		applyAppSettings(appSettings);
		createTab();

		swipeRefreshLayout.setOnRefreshListener(() -> {
			var url = Objects.requireNonNull(webView.getUrl());

			if(url.startsWith("file://")) {
				webView.loadUrl(url);
				return;
			}

			webView.reload();
		});

		searchLayout = new SearchLayout(this, appSettings);
		searchLayout.setVisibility(View.GONE, false);
		searchLayout.setLaunchLinkListener(webView::loadUrl);

		var searchLayoutParams = new ConstraintLayout.LayoutParams(0, 0);
		searchLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;

		parent.addView(searchLayout, searchLayoutParams);

		reloadLayout();
	}

	public void createTab() {
		currentTab = TabsManager.create(this);
		setCurrentWebView(currentTab.webView);

		currentTab.onStartedCallbacks.add(tab -> {
			if(tab != currentTab) return;

			searchBar.setIsLoading(true);
			searchLayout.setUrl(tab.url);

			progressIndicator.setVisibility(View.VISIBLE);
		});

		currentTab.onTitleCallbacks.add(tab -> {
			if(tab != currentTab) return;

			searchBar.setTitle(tab.title);
		});

		currentTab.onFinishedCallbacks.add(tab -> {
			if(tab != currentTab) return;

			finishedLoading();
		});

		currentTab.onProgressCallbacks.add(tab -> {
			if(tab != currentTab) return;

			progressIndicator.setProgress(tab.progress, true);
			if(tab.progress == 100) finishedLoading();
		});

		currentTab.onFaviconCallbacks.add(tab -> {
			searchBar.setFavicon(tab.favicon);
		});
	}

	private void finishedLoading() {
		progressIndicator.setVisibility(View.GONE);
		swipeRefreshLayout.setRefreshing(false);
		searchBar.setIsLoading(false);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		ThemeSettings.ThemeManager.setContext(null);
		AppManager.dispose();

		//TODO: SAVE STATE TO STORAGE AND RESTORE IT ON NEXT SESSION
		//var bundle = new Bundle();
		//var state = webView.saveState(bundle);

		//var history = webView.copyBackForwardList();
	}

	public void applyTheme(@NonNull ThemeSettings theme) {
		var window = getWindow();

		window.setStatusBarColor(Color.parseColor(theme.bars));
		window.setNavigationBarColor(Color.parseColor(theme.bars));

		window.setBackgroundDrawable(new ColorDrawable(Color.parseColor(theme.bars)));

		if(Build.VERSION.SDK_INT >= 28) {
			var attrs = window.getAttributes();
			attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			window.setAttributes(attrs);
		}

		swipeRefreshLayout.setColorSchemeColors(Color.parseColor(theme.barsOverlay));
		swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor(theme.bars));

		searchLayout.setTheme(theme);
	}

	private int getSizeForButton(boolean isSmall) {
		return (int)TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP,
				isSmall ? 34 : 38,
				getResources().getDisplayMetrics());
	}

	public void reloadLayout() {
		var theme = ThemeSettings.ThemeManager.getCurrentTheme();
		if(theme.isInvalid()) theme = new ThemeSettings();

		var barsColor = Color.parseColor(theme.bars);

		topbar.removeAllViews();
		bottombar.removeAllViews();
		sidebar.removeAllViews();

		topbar.setBackgroundColor(barsColor);
		bottombar.setBackgroundColor(barsColor);
		sidebar.setBackgroundColor(barsColor);

		var config = getResources().getConfiguration();
		boolean isLandscape = (config.orientation == Configuration.ORIENTATION_LANDSCAPE);

		for(var item : appSettings.leftActions) {
			var view = createActionButton(item, theme);

			int size = getSizeForButton(isLandscape);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, size);
			if(!isLandscape) params.weight = 1;
			params.setMargins(12, 0, 10, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		if(isLandscape && !appSettings.menuActions.isEmpty()) {
			sidebar.setVisibility(View.VISIBLE);

			for(var item : appSettings.menuActions) {
				var view = createActionButton(item, theme);

				int size = getSizeForButton(true);
				var params = new LinearLayout.LayoutParams(size, size);
				params.setMargins(0, 12, 0, 12);

				sidebar.addView(view, params);
			}
		} else {
			sidebar.setVisibility(View.GONE);
		}

		searchBar = new SearchBarWidget(this, webView, theme);
		searchBar.setTitle(webView.getTitle());
		topbar.addView(searchBar);

		searchBar.setOnClickListener(view -> searchLayout.show());
		if(!isLandscape) searchBar.setPadding(16, 0, 16, 0);

		for(var item : appSettings.rightActions) {
			var view = createActionButton(item, theme);

			int size = getSizeForButton(isLandscape);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, size);
			if(!isLandscape) params.weight = 1;
			params.setMargins(10, 0, 12, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		//webViewClient.setSearchBar(searchBar);
		//chromeClient.setSearchBar(searchBar);

		bottombar.setVisibility(!isLandscape ? View.VISIBLE : View.GONE);

		applyTheme(theme);
	}

	@NonNull
	private View createActionButton(@NonNull String name, ThemeSettings theme) {
		int icon = R.drawable.ic_close_black, primaryColor = Color.parseColor(theme.barsOverlay);
		var button = new ImageView(this);
		var circleRipple = ResourcesCompat.getDrawable(getResources(), R.drawable.ripple_circle, getTheme());

		button.setOnClickListener(view -> {
			String message = "Unknown action, please check your settings!";
			Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
		});

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

				button.setOnClickListener(view -> {
					var a = new ImageView(this);
					a.setImageResource(R.drawable.ic_google_colorful);
					a.setOnClickListener(_view -> {
						var intent = new Intent(this, IncognitoActivity.class);
						startActivity(intent);
					});

					var popup = new PopupWindow(a, 100, 100);
					popup.setFocusable(true);
					popup.showAsDropDown(button);
				});
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

				button.setOnClickListener(view -> {
					var a = new ImageView(this);
					a.setImageResource(R.drawable.ic_google_colorful);

					var popup = new PopupWindow(a, 100, 100);
					popup.setFocusable(true);
					popup.showAsDropDown(button);
				});
			}
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

			TextView tabsCounter = new TextView(this);
			tabsCounter.setText(String.valueOf(TabsManager.getCount()));
			tabsCounter.setTextColor(primaryColor);
			tabsCounter.setTextSize(13);
			tabsCounter.setGravity(Gravity.CENTER);
			parent.addView(tabsCounter, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

			return parent;
		}

		return button;
	}

	public void applyAppSettings(AppSettings settings) {
		AppSettings.globalSettings = settings;
	}

	@SuppressLint("ClickableViewAccessibility")
	public void setCurrentWebView(@NonNull WebView webView) {
		SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefresher);

		if(this.webView != null) {
			var params = this.webView.getLayoutParams();
			swipeRefreshLayout.removeView(this.webView);
			swipeRefreshLayout.addView(webView, params);
		} else {
			swipeRefreshLayout.addView(webView, 0, 0);
		}

		this.webView = webView;
		webView.setOnTouchListener(scrollListener);

		webView.setOnLongClickListener(view -> {
			var test = webView.getHitTestResult();
			int type = test.getType();

			if(type != HitTestResult.SRC_ANCHOR_TYPE
					&& type != HitTestResult.IMAGE_TYPE
					&& type != HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false;

			var handler = new Handler();
			var linkMessage = handler.obtainMessage();
			var imageMessage = handler.obtainMessage();

			var menu = new ContextMenu.Builder(this)
					.setDismissOnSelect(true);

			switch(type) {
				case HitTestResult.IMAGE_TYPE -> {
					webView.requestImageRef(imageMessage);
				}

				case HitTestResult.SRC_ANCHOR_TYPE -> {
					webView.requestFocusNodeHref(linkMessage);
				}

				case HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
					webView.requestFocusNodeHref(linkMessage);
					webView.requestImageRef(imageMessage);
				}
			}

			var link = linkMessage.getData().getString("url");
			if(link != null) {
				menu.addAction("Open link in new tab", () -> webView.loadUrl(link));
				menu.addAction("Open link in new incognito tab", () -> {});
				menu.addAction("Share link", () -> AndroidUtil.share("Share link", link));
				menu.addAction("Copy link to clipboard", () -> AndroidUtil.copyToClipboard(link));
			}

			var image = imageMessage.getData().getString("url");
			if(image != null) {
				menu.addAction("Open image in new tab", () -> webView.loadUrl(image));
				menu.addAction("Open image in new incognito tab", () -> {});
				menu.addAction("Download image", () -> {});
				menu.addAction("Share image", () -> AndroidUtil.share("Share image link", image));
				menu.addAction("Copy image link to clipboard", () -> AndroidUtil.copyToClipboard(image));
			}

			menu.build();

			return false;
		});
	}

	@SuppressLint({"MissingSuperCall"})
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