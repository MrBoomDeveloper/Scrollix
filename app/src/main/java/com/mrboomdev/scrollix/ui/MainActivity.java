package com.mrboomdev.scrollix.ui;

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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.app.IntentHandler;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.engine.tab.TabListener;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.layout.SearchLayout;
import com.mrboomdev.scrollix.ui.popup.ContextMenu;
import com.mrboomdev.scrollix.ui.popup.TabsMenu;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;
import com.mrboomdev.scrollix.util.AndroidUtil;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import org.jetbrains.annotations.Contract;

public class MainActivity extends AppCompatActivity implements TabListener {
	private BarsAnimator barsAnimator;
	private TextView tabsCounter;
	public SearchLayout searchLayout;
	private LinearProgressIndicator progressIndicator;
	private WebView webView;
	private SearchBarWidget searchBar;
	private LinearLayout topbar, bottombar, sidebar;
	private View backButton, forwardButton;
	private boolean isFullscreen;

	@SuppressLint({"ClickableViewAccessibility"})
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SplashScreen.installSplashScreen(this);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);

		AppManager.startup(this);
		TabManager.addListener(this);
		ThemeSettings.ThemeManager.addUpdateListener(() -> runOnUiThread(this::reloadLayout));

		ConstraintLayout parent = findViewById(R.id.main_screen_parent);
		topbar = findViewById(R.id.top_bar);
		bottombar = findViewById(R.id.bottom_bar);
		sidebar = findViewById(R.id.sidebar);
		progressIndicator = findViewById(R.id.progressIndicator);

		searchLayout = new SearchLayout(this);
		searchLayout.setVisibility(View.GONE, false);
		searchLayout.setLaunchLinkListener(url -> TabManager.getCurrentTab().loadUrl(url));

		var searchLayoutParams = new ConstraintLayout.LayoutParams(0, 0);
		searchLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
		searchLayoutParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
		parent.addView(searchLayout, searchLayoutParams);

		LinearLayout webViewHolder = findViewById(R.id.webViewHolder);
		barsAnimator = new BarsAnimator();

		TabManager.setTabHolder(webViewHolder);
		TabManager.setBarsAnimator(barsAnimator);

		reloadLayout();
		AppManager.postCreate();
		registerBackHandler();
	}

	@Override
	public void onTabListModified() {
		if(tabsCounter != null) {
			var count = String.valueOf(TabStore.getTabCount());
			tabsCounter.setText(count);
		}
	}

	@Override
	public void onTabFocused(@NonNull com.mrboomdev.scrollix.engine.tab.Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;

		searchBar.setUrl(tab.getUrl());
		searchLayout.setUrl(tab.getUrl());
	}

	@Override
	public void onTabLoadingFinished(@NonNull com.mrboomdev.scrollix.engine.tab.Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;
		updateBackForwardButtons();

		searchBar.setUrl(tab.getUrl());
		searchLayout.setUrl(tab.getUrl());
		finishedLoading();
	}

	@Override
	public void onTabLoadingStarted(com.mrboomdev.scrollix.engine.tab.Tab tab) {
		if(tab != TabManager.getCurrentTab()) return;
		updateBackForwardButtons();

		searchBar.setIsLoading(true);
		searchBar.setUrl(tab.getUrl());
		searchLayout.setUrl(tab.getUrl());

		progressIndicator.setProgress(0);
		progressIndicator.setVisibility(View.VISIBLE);
	}

	@Override
	public void onTabGotTitle(com.mrboomdev.scrollix.engine.tab.Tab tab, String title) {
		if(tab != TabManager.getCurrentTab()) return;

		searchBar.setTitle(title);
	}

	@Override
	public void onTabFullscreenToggle(com.mrboomdev.scrollix.engine.tab.Tab tab, boolean isFullscreen) {
		if(tab != TabManager.getCurrentTab()) return;
		this.isFullscreen = isFullscreen;

		ConstraintLayout parent = findViewById(R.id.main_screen_parent);
		parent.setFitsSystemWindows(isFullscreen);

		//TODO: Fix notch still being shown...

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			var insets = getWindow().getInsetsController();
			var type = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();

			if(insets != null) {
				if(isFullscreen) insets.hide(type);
				else insets.show(type);
			}

			getWindow().getAttributes().layoutInDisplayCutoutMode = isFullscreen
					? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
					: WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;

			getWindow().setDecorFitsSystemWindows(isFullscreen);


		} else {
			var fullscreenFlag = WindowManager.LayoutParams.FLAG_FULLSCREEN;// | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
			var window = getWindow();

			if(isFullscreen) window.setFlags(fullscreenFlag, fullscreenFlag);
			else window.clearFlags(fullscreenFlag);
		}

		updateBarsVisibility();
	}

	private void updateBarsVisibility() {
		if(isFullscreen) {
			topbar.setVisibility(View.GONE);
			bottombar.setVisibility(View.GONE);
			sidebar.setVisibility(View.GONE);
		} else {
			topbar.setVisibility(View.VISIBLE);
			reloadLayout();
		}
	}

	private void updateBackForwardButtons() {
		var tab = TabManager.getCurrentTab();

		if(backButton != null) backButton.setAlpha(tab.canGoBack() ? 1 : .6f);
		if(forwardButton != null) forwardButton.setAlpha(tab.canGoForward() ? 1 : .6f);
	}

	@Override
	public void onTabLoadingProgress(com.mrboomdev.scrollix.engine.tab.Tab tab, int progress) {
		if(tab != TabManager.getCurrentTab()) return;

		progressIndicator.setProgress(progress, true);
		if(progress == 100) finishedLoading();
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

	public void reloadLayout() {
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();
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

			int size = FormatUtil.getDip(isLandscape ? 34 : 38);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, isLandscape ? size : ViewGroup.LayoutParams.MATCH_PARENT);
			if(!isLandscape) params.weight = 1;
			params.setMargins(isLandscape ? 12 : 0, 0, isLandscape ? 10 : 0, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		if(isLandscape && !AppManager.settings.menuActions.isEmpty()) {
			sidebar.setVisibility(View.VISIBLE);

			for(var item : AppManager.settings.menuActions) {
				var view = createActionButton(item, theme);

				int size = FormatUtil.getDip(34);
				var params = new LinearLayout.LayoutParams(size, size);
				params.setMargins(0, 12, 0, 12);

				sidebar.addView(view, params);
			}
		} else {
			sidebar.setVisibility(View.GONE);
		}

		searchBar = new SearchBarWidget(this, theme);
		searchBar.setOnClickListener(view -> searchLayout.show());
		topbar.addView(searchBar);

		for(var item : AppManager.settings.rightActions) {
			var view = createActionButton(item, theme);

			int size = FormatUtil.getDip(isLandscape ? 34 : 38);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, isLandscape ? size : ViewGroup.LayoutParams.MATCH_PARENT);
			if(!isLandscape) params.weight = 1;
			params.setMargins(isLandscape ? 10 : 0, 0, isLandscape ? 12 : 0, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		barsAnimator.setBarsExpandable(BarsAnimator.TOPBAR | BarsAnimator.BOTTOMBAR);

		bottombar.setVisibility(!isLandscape ? View.VISIBLE : View.GONE);
		barsAnimator.setBarsFromActivity(this);
		applyTheme(theme);
	}

	@Contract("_, _, _ -> param2")
	private int setUrlAction(@NonNull ImageView button, @DrawableRes int icon, EngineInternal.Link link) {
		button.setOnClickListener(view -> {
			var tab = TabManager.getCurrentTab();
			tab.loadUrl(link.getRealUrl());
		});

		return icon;
	}

	@NonNull
	private View createActionButton(@NonNull String name, @NonNull ThemeSettings theme) {
		int icon = R.drawable.ic_close_black, primaryColor = Color.parseColor(theme.barsOverlay);
		var button = new ImageView(this);

		var buttonRipple = DrawableUtil.getDrawable(
				AppManager.isLandscape() ? R.drawable.ripple_circle : R.drawable.ripple_square);

		button.setOnClickListener(view -> {
			String message = "Unknown action, please check your settings!";
			Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
		});

		int buttonPadding = FormatUtil.getDip(AppManager.isLandscape() ? 6 : 12);
		button.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);

		switch(name) {
			case "home" -> icon = setUrlAction(button, R.drawable.ic_home_black, EngineInternal.Link.HOME);
			case "settings" -> icon = setUrlAction(button, R.drawable.ic_settings_black, EngineInternal.Link.SETTINGS);
			case "downloads" -> icon = setUrlAction(button, R.drawable.ic_download_black, EngineInternal.Link.DOWNLOADS);
			case "history" -> icon = setUrlAction(button, R.drawable.ic_history_black, EngineInternal.Link.HISTORY);
			case "bookmarks" -> icon = setUrlAction(button, R.drawable.ic_star_black, EngineInternal.Link.BOOKMARKS);

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
				button.setOnClickListener(view -> TabManager.getCurrentTab().goBack());
				backButton = button;
			}

			case "next" -> {
				icon = R.drawable.ic_back_black;
				button.setScaleX(-1);
				button.setOnClickListener(view -> TabManager.getCurrentTab().goForward());
				forwardButton = button;
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

		var buttonIcon = DrawableUtil.getDrawable(icon, primaryColor);
		button.setImageDrawable(buttonIcon);

		if(name.equals("tabs")) {
			var parent = new FrameLayout(this);
			button.setScaleX(1.1f);
			button.setScaleY(1.1f);
			parent.addView(button);

			tabsCounter = new TextView(this);
			tabsCounter.setText(String.valueOf(TabStore.getTabCount()));
			tabsCounter.setTextColor(primaryColor);
			tabsCounter.setTextSize(13);
			tabsCounter.setGravity(Gravity.CENTER);
			parent.addView(tabsCounter, Formats.MATCH_PARENT, Formats.MATCH_PARENT);

			return parent;
		}

		return button;
	}

	@SuppressLint("ClickableViewAccessibility")
	public void setCurrentTab(@NonNull Tab tab) {
		//webView.setOnTouchListener(scrollListener);

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
				menu.addAction("Open link in new tab", () -> TabStore.createTab(link, true));
				menu.addAction("Open link in background", () -> TabStore.createTab(link, false));
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
				menu.addAction("Open image in new tab", () -> TabStore.createTab(image, true));
				menu.addAction("Open image in background", () -> TabStore.createTab(image, false));
				menu.addAction("Open image in new incognito tab", () -> {
					var intent = new Intent(this, IncognitoActivity.class);
					intent.setData(Uri.parse(image));
					startActivity(intent);
				});
				//menu.addAction("Download image", () -> {});
				menu.addAction("Share image", () -> AndroidUtil.share("Share image link", image));
				menu.addAction("Copy image link to clipboard", () -> AndroidUtil.copyToClipboard(image));
			}

			menu.build();

			return false;
		});
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		IntentHandler.handleIntent(intent);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		reloadLayout();
	}

	private void registerBackHandler() {
		Runnable callback = () -> {
			if(searchLayout.isOpened()) {
				searchLayout.hide();
				return;
			}

			var tab = TabManager.getCurrentTab();

			if(tab.canGoBack()) {
				tab.goBack();
			} else {
				TabStore.removeTab(tab);
			}
		};

		if(Build.VERSION.SDK_INT > 32) {
			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback::run);
		} else {
			getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
				@Override
				public void handleOnBackPressed() {
					callback.run();
				}
			});
		}
	}
}