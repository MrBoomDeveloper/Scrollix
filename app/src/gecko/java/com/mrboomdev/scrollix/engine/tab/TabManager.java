package com.mrboomdev.scrollix.engine.tab;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.mrboomdev.scrollix.BuildConfig;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.extenison.ExtensionDelegator;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.ui.AppUi;
import com.mrboomdev.scrollix.ui.BarsAnimator;
import com.mrboomdev.scrollix.util.format.Formats;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoView;

@SuppressLint("StaticFieldLeak")
public class TabManager {
	protected static GeckoRuntime runtime;
	private static GeckoView geckoView;
	private static Tab currentTab;
	private static View overlay;
	private static LinearLayout replacer;

	@SuppressLint("ClickableViewAccessibility")
	public static void setBarsAnimator(@NonNull BarsAnimator animator) {
		geckoView.setOnTouchListener(animator.getOnTouchListener());
	}

	public static Tab getCurrentTab() {
		return currentTab;
	}

	public static void setCurrentTab(@NonNull Tab tab) {
		setCurrentTab(tab, true);
	}

	public static void setCurrentTab(Tab tab, boolean tryToInit) {
		if(tab == null) return;
		currentTab = tab;

		if(tryToInit) tab.init();
		if(geckoView == null) return;

		initForTab(tab);
		replacer.setVisibility(View.GONE);

		AppUi.updateBackForwardState();
		AppUi.updateTabLoading(tab);
		AppUi.searchBar.setTitle(tab.getTitle());

		if(tab.isCrash()) {
			AppUi.setTabCrashed(tab);
		}
	}

	private static void initForTab(@NonNull Tab tab) {
		geckoView.setSession(tab.getSession());
		AppUi.focusTab(tab);

		ExtensionDelegator.update();
		var extensionController = runtime.getWebExtensionController();

		for(var _tab : TabStore.getAllTabs()) {
			if(_tab.didInit()) continue;

			if(_tab == tab) extensionController.setTabActive(tab.getSession(), true);
			else extensionController.setTabActive(_tab.getSession(), false);
		}
	}

	public static LinearLayout getReplacer() {
		return replacer;
	}

	public static void setTabHolder(@NonNull ViewGroup view) {
		var context = AppManager.getActivityContext();
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		geckoView = new GeckoView(context);
		geckoView.setFitsSystemWindows(true);
		geckoView.coverUntilFirstPaint(Color.parseColor(theme.background));

		view.removeAllViews();
		view.addView(geckoView, Formats.MATCH_PARENT, Formats.MATCH_PARENT);

		if(currentTab != null) {
			initForTab(currentTab);
		}

		var overlayParams = new ConstraintLayout.LayoutParams(0, 0);
		overlayParams.topToBottom = R.id.top_bar;
		overlayParams.bottomToTop = R.id.bottom_bar;
		overlayParams.leftToRight = R.id.sidebar;
		overlayParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;

		overlay = new View(context);
		overlay.setAlpha(0);
		overlay.setBackgroundColor(Color.BLACK);
		AppUi.parent.addView(overlay, overlayParams);

		replacer = new LinearLayout(context);
		replacer.setVisibility(View.GONE);
		replacer.setBackgroundColor(Color.BLACK);
		AppUi.parent.addView(replacer, overlayParams);
	}

	public static void setWebViewIsActive(boolean isActive) {
		overlay.setClickable(!isActive);
		overlay.setAlpha(isActive ? 0 : .5f);
	}

	public static void startup() {
		var context = AppManager.getActivityContext();

		var runtimeSettings = new GeckoRuntimeSettings.Builder()
				.aboutConfigEnabled(true)
				.allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
				.consoleOutput(BuildConfig.DEBUG)
				.extensionsWebAPIEnabled(true)
				.remoteDebuggingEnabled(BuildConfig.DEBUG)
				.preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
				.forceUserScalableEnabled(true)
				.extensionsProcessEnabled(true)
				.build();

		runtime = GeckoRuntime.create(context, runtimeSettings);
		ExtensionManager.startup(runtime);
	}

	public static void dispose() {
		runtime.shutdown();

		runtime = null;
		overlay = null;
	}
}