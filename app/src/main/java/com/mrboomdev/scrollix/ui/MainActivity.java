package com.mrboomdev.scrollix.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.splashscreen.SplashScreen;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.app.IntentHandler;
import com.mrboomdev.scrollix.data.Action;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.widgets.SearchBarWidget;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

public class MainActivity extends AppCompatActivity {
	private LinearLayout topbar, bottombar, sidebar;

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		AppManager.saveState();
	}

	@SuppressLint({"ClickableViewAccessibility"})
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SplashScreen.installSplashScreen(this);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);
		AppManager.startup(this);

		ConstraintLayout parent = findViewById(R.id.main_screen_parent);
		topbar = findViewById(R.id.top_bar);
		bottombar = findViewById(R.id.bottom_bar);
		sidebar = findViewById(R.id.sidebar);

		AppUi.parent = parent;
		AppUi.topbar = topbar;
		AppUi.sidebar = sidebar;
		AppUi.bottombar = bottombar;

		AppUi.startup(this);
		AppUi.webHolder = findViewById(R.id.webViewHolder);
		TabManager.setTabHolder(AppUi.webHolder);
		reloadLayout();
	}

	@Override
	protected void onStart() {
		super.onStart();

		BarsAnimator barsAnimator = new BarsAnimator();
		AppUi.barsAnimator = barsAnimator;
		barsAnimator.setBarsFromActivity(this);
		TabManager.setBarsAnimator(barsAnimator);

		AppManager.postCreate();
		registerBackHandler();

		AppUi.searchLayout.setLaunchLinkListener(url -> TabManager.getCurrentTab().loadUrl(url));
		ThemeSettings.ThemeManager.addUpdateListener(() -> runOnUiThread(this::reloadLayout));

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode =
					WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}
	}

	public void updateBarsVisibility() {
		if(AppUi.isFullscreen) {
			topbar.setVisibility(View.GONE);
			bottombar.setVisibility(View.GONE);
			sidebar.setVisibility(View.GONE);
		} else {
			topbar.setVisibility(View.VISIBLE);
			reloadLayout();
		}
	}

	@Override
	protected void onPause() {
		AppUi.wasPausedDuringFullscreen = true;
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

		AppUi.searchLayout.setTheme(theme);

		var color = Color.parseColor(theme.primary);
		AppUi.progressIndicator.setIndicatorColor(color, color, color);
	}

	public void reloadLayout() {
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();
		var barsColor = Color.parseColor(theme.bars);
		int buttonPadding = AppUtils.isLandscape() ? Formats.NORMAL_PADDING : Formats.BIG_PADDING;
		boolean isLandscape = AppUtils.isLandscape();

		topbar.removeAllViews();
		bottombar.removeAllViews();
		sidebar.removeAllViews();

		topbar.setBackgroundColor(barsColor);
		bottombar.setBackgroundColor(barsColor);
		sidebar.setBackgroundColor(barsColor);

		for(var item : AppManager.settings.leftActions) {
			var view = item.getView(this);
			Action.styleAction(view, buttonPadding);

			int size = FormatUtil.getDip(isLandscape ? 34 : 38);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, isLandscape ? size : ViewGroup.LayoutParams.MATCH_PARENT);
			if(!isLandscape) params.weight = 1;
			params.setMargins(isLandscape ? 12 : 0, 0, isLandscape ? 10 : 0, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		if(isLandscape && !AppManager.settings.sideActions.isEmpty()) {
			sidebar.setVisibility(View.VISIBLE);

			for(var item : AppManager.settings.sideActions) {
				var view = item.getView(this);
				Action.styleAction(view, buttonPadding);

				int size = FormatUtil.getDip(34);
				var params = new LinearLayout.LayoutParams(size, size);
				params.setMargins(0, 12, 0, 12);

				sidebar.addView(view, params);
			}
		} else {
			sidebar.setVisibility(View.GONE);
		}

		SearchBarWidget searchBar = new SearchBarWidget(this, theme);
		searchBar.setOnClickListener(view -> AppUi.searchLayout.show());
		topbar.addView(searchBar);
		AppUi.searchBar = searchBar;

		for(var item : AppManager.settings.rightActions) {
			var view = item.getView(this);
			Action.styleAction(view, buttonPadding);

			int size = FormatUtil.getDip(isLandscape ? 34 : 38);
			var params = new LinearLayout.LayoutParams(isLandscape ? size : 0, isLandscape ? size : ViewGroup.LayoutParams.MATCH_PARENT);
			if(!isLandscape) params.weight = 1;
			params.setMargins(isLandscape ? 10 : 0, 0, isLandscape ? 12 : 0, 0);

			(isLandscape ? topbar : bottombar).addView(view, params);
		}

		bottombar.setVisibility(!isLandscape ? View.VISIBLE : View.GONE);
		applyTheme(theme);
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
			if(AppUi.searchLayout.isOpened()) {
				AppUi.searchLayout.hide();
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