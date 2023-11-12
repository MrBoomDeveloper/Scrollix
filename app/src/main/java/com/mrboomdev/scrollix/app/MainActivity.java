package com.mrboomdev.scrollix.app;

import static android.webkit.WebView.HitTestResult;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
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
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.layout.SearchLayout;
import com.mrboomdev.scrollix.ui.popup.ContextMenu;
import com.mrboomdev.scrollix.ui.popup.TabsMenu;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;
import com.mrboomdev.scrollix.util.AndroidUtil;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;
import com.mrboomdev.scrollix.util.LinkUtil;
import com.mrboomdev.scrollix.webview.MyDownloadListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
	private final List<Tab> tabs = new ArrayList<>();
	private TextView tabsCounter;
	public SearchLayout searchLayout;
	private ScrollListener scrollListener;
	private Tab currentTab;
	private LinearProgressIndicator progressIndicator;
	private WebView webView;
	private SearchBarWidget searchBar;
	private LinearLayout topbar, bottombar, sidebar;

	@SuppressLint({"ClickableViewAccessibility"})
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SplashScreen.installSplashScreen(this);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		AppManager.startup(this);
		ThemeSettings.ThemeManager.addUpdateListener(() -> runOnUiThread(this::reloadLayout));

		ConstraintLayout parent = findViewById(R.id.main_screen_parent);
		topbar = findViewById(R.id.top_bar);
		bottombar = findViewById(R.id.bottom_bar);
		sidebar = findViewById(R.id.sidebar);
		progressIndicator = findViewById(R.id.progressIndicator);

		searchLayout = new SearchLayout(this);
		searchLayout.setVisibility(View.GONE, false);

		searchLayout.setLaunchLinkListener(url -> {
			webView.loadUrl(url);
			currentTab.runCallbacks(currentTab.onStartedCallbacks);
		});

		var searchLayoutParams = new ConstraintLayout.LayoutParams(0, 0);
		searchLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
		parent.addView(searchLayout, searchLayoutParams);

		LinearLayout webViewHolder = findViewById(R.id.webViewHolder);
		TabManager.setTabHolder(webViewHolder);

		scrollListener = new ScrollListener();
		reloadLayout();

		TabsManager.selectTabCallbacks.add(this::setCurrentTab);
		TabsManager.createTabCallbacks.add(tab -> updateTabCounter());

		TabsManager.removeTabCallbacks.add((tab, wasIndex) -> {
			updateTabCounter();

			if(tab == currentTab) {
				var nearestTab = TabsManager.getNearestTab(wasIndex);
				if(nearestTab != null) setCurrentTab(nearestTab);
			}
		});

		AppManager.postCreate();
	}

	private void updateTabCounter() {
		if(tabsCounter != null) {
			var count = String.valueOf(TabsManager.getCount());
			tabsCounter.setText(count);
		}
	}

	private void initTabCallbacks(@NonNull Tab tab) {
		tab.onStartedCallbacks.add(_tab -> {
			if(tab != currentTab) return;

			searchBar.setIsLoading(true);
			searchBar.setTitle(tab.url);
			searchLayout.setUrl(tab.url);

			progressIndicator.setVisibility(View.VISIBLE);
		});

		tab.onTitleCallbacks.add(_tab -> {
			if(tab != currentTab) return;

			searchBar.setTitle(tab.title);
		});

		tab.onFinishedCallbacks.add(_tab -> {
			if(tab != currentTab) return;

			finishedLoading();
		});

		tab.onProgressCallbacks.add(_tab -> {
			if(tab != currentTab) return;

			progressIndicator.setProgress(tab.progress, true);
			if(tab.progress == 100) finishedLoading();
		});

		tab.onFaviconCallbacks.add(_tab -> {
			if(tab != currentTab) return;

			searchBar.setFavicon(tab.favicon);
		});

		tab.onDisposeCallbacks.add(tabs::remove);
	}

	private void finishedLoading() {
		progressIndicator.setVisibility(View.GONE);
		searchBar.setIsLoading(false);
	}

	@Override
	protected void onPause() {
		AppManager.saveState();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		AppManager.dispose();
		super.onDestroy();
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

		searchLayout.setTheme(theme);
		progressIndicator.setIndicatorColor(Color.parseColor(theme.primary), Color.parseColor(theme.primary), Color.parseColor(theme.primary));
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

		boolean isLandscape = AppManager.isLandscape();

		for(var item : AppManager.settings.leftActions) {
			var view = createActionButton(item, theme);

			int size = getSizeForButton(isLandscape);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, isLandscape ? size : ViewGroup.LayoutParams.MATCH_PARENT);
			if(!isLandscape) params.weight = 1;
			params.setMargins(isLandscape ? 12 : 0, 0, isLandscape ? 10 : 0, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		if(isLandscape && !AppManager.settings.menuActions.isEmpty()) {
			sidebar.setVisibility(View.VISIBLE);

			for(var item : AppManager.settings.menuActions) {
				var view = createActionButton(item, theme);

				int size = getSizeForButton(true);
				var params = new LinearLayout.LayoutParams(size, size);
				params.setMargins(0, 12, 0, 12);

				sidebar.addView(view, params);
			}
		} else {
			sidebar.setVisibility(View.GONE);
		}

		searchBar = new SearchBarWidget(this, theme);
		if(webView != null) searchBar.setTitle(webView.getTitle());
		topbar.addView(searchBar);

		searchBar.setOnClickListener(view -> searchLayout.show());

		for(var item : AppManager.settings.rightActions) {
			var view = createActionButton(item, theme);

			int size = getSizeForButton(isLandscape);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, isLandscape ? size : ViewGroup.LayoutParams.MATCH_PARENT);
			if(!isLandscape) params.weight = 1;
			params.setMargins(isLandscape ? 10 : 0, 0, isLandscape ? 12 : 0, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		//webViewClient.setSearchBar(searchBar);
		//chromeClient.setSearchBar(searchBar);

		bottombar.setVisibility(!isLandscape ? View.VISIBLE : View.GONE);

		applyTheme(theme);
	}

	@NonNull
	private View createActionButton(@NonNull String name, @NonNull ThemeSettings theme) {
		int icon = R.drawable.ic_close_black, primaryColor = Color.parseColor(theme.barsOverlay);
		var button = new ImageView(this);

		var buttonRipple = AppManager.isLandscape()
				? FileUtil.getDrawable(R.drawable.ripple_circle)
				: FileUtil.getDrawable(R.drawable.ripple_square);

		button.setOnClickListener(view -> {
			String message = "Unknown action, please check your settings!";
			Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
		});

		int buttonPadding = FormatUtil.getDip(AppManager.isLandscape() ? 6 : 12);
		button.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);

		switch(name) {
			case "home" -> {
				icon = R.drawable.ic_home_black;
				button.setOnClickListener(view -> webView.loadUrl(LinkUtil.ScrollixUrls.HOME.getFullUrl()));
			}

			case "settings" -> {
				icon = R.drawable.ic_settings_black;
				button.setOnClickListener(view -> webView.loadUrl(LinkUtil.ScrollixUrls.SETTINGS.getFullUrl()));
			}

			case "downloads" -> {
				icon = R.drawable.ic_download_black;
				button.setOnClickListener(view -> webView.loadUrl(LinkUtil.ScrollixUrls.DOWNLOADS.getFullUrl()));
			}

			case "history" -> {
				icon = R.drawable.ic_history_black;
				button.setOnClickListener(view -> webView.loadUrl(LinkUtil.ScrollixUrls.HISTORY.getFullUrl()));
			}

			case "bookmarks" -> {
				icon = R.drawable.ic_star_black;
				button.setOnClickListener(view -> webView.loadUrl(LinkUtil.ScrollixUrls.BOOKMARKS.getFullUrl()));
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
				button.setOnClickListener(view -> webView.goBack());
			}

			case "next" -> {
				icon = R.drawable.ic_back_black;
				button.setScaleX(-1);
				button.setOnClickListener(view -> webView.goForward());
			}

			case "tabs" -> {
				icon = R.drawable.ic_tabs_black;

				button.setOnClickListener(view -> {
					var menu = new TabsMenu(this, theme);
					menu.showAt(button);
				});
			}
		}

		if(name.equals("next") || name.equals("back")) {
			int padding = Math.round(button.getPaddingTop() * 1.2f);
			button.setPadding(padding, padding, padding, padding);
		}

		button.setScaleType(ImageView.ScaleType.FIT_CENTER);
		button.setBackground(buttonRipple);
		button.setClickable(true);
		button.setFocusable(true);
		
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
			tabsCounter.setTextColor(primaryColor);
			tabsCounter.setTextSize(13);
			tabsCounter.setGravity(Gravity.CENTER);
			parent.addView(tabsCounter, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

			return parent;
		}

		return button;
	}

	@SuppressLint("ClickableViewAccessibility")
	public void setCurrentTab(@NonNull Tab tab) {
		TabManager.setCurrentTab(Objects.requireNonNull(TabStore.getTab(0)));



		//TO NOT CRASH APP USE THIS SEGMENT
		this.webView = tab.webView;
		this.currentTab = tab;

		//PREVENT OLD BEHAVIOR WITHOUT WARNINGS
		if(Integer.parseInt("1") == 1) return;
		LinearLayout webViewHolder = findViewById(R.id.webViewHolder);

		TabsManager.setCurrent(tab, false);
		updateTabCounter();

		if(!tabs.contains(tab)) {
			initTabCallbacks(tab);
			tabs.add(tab);

			var parent = tab.webView.getParent();
			if(parent instanceof LinearLayout linear) {
				linear.removeView(tab.webView);
			}

			webViewHolder.addView(tab.webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		}

		for(var _tab : tabs) {
			_tab.webView.setVisibility(View.GONE);
		}

		webView.setVisibility(View.VISIBLE);
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
				case HitTestResult.IMAGE_TYPE -> webView.requestImageRef(imageMessage);

				case HitTestResult.SRC_ANCHOR_TYPE -> webView.requestFocusNodeHref(linkMessage);

				case HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
					webView.requestFocusNodeHref(linkMessage);
					webView.requestImageRef(imageMessage);
				}
			}

			var link = linkMessage.getData().getString("url");
			if(link != null) {
				menu.addAction("Open link in new tab", () -> TabsManager.create(link, true));
				menu.addAction("Open link in background", () -> TabsManager.create(link, false));
				menu.addAction("Open link in new incognito tab", () -> {
					var intent = new Intent(this, IncognitoActivity.class);
					intent.setData(Uri.parse(link));
					startActivity(intent);
				});
				menu.addAction("Share link", () -> AndroidUtil.share("Share link", link));
				menu.addAction("Copy link to clipboard", () -> AndroidUtil.copyToClipboard(link));
			}

			var image = imageMessage.getData().getString("url");
			if(image != null) {
				menu.addAction("Open image in new tab", () -> TabsManager.create(image, true));
				menu.addAction("Open image in background", () -> TabsManager.create(image, false));
				menu.addAction("Open image in new incognito tab", () -> {
					var intent = new Intent(this, IncognitoActivity.class);
					intent.setData(Uri.parse(image));
					startActivity(intent);
				});
				//menu.addAction("Download image", () -> {});
				menu.addAction("Share image", () -> AndroidUtil.share("Share image link", image));
				menu.addAction("Copy image link to clipboard", () -> AndroidUtil.copyToClipboard(image));
			}

			menu.setUrl(link);
			menu.setImage(image);
			menu.build();

			return false;
		});
	}

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