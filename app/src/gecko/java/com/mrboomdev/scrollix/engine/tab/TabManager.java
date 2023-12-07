package com.mrboomdev.scrollix.engine.tab;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.BuildConfig;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.extenison.ExtensionDelegator;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.ui.BarsAnimator;
import com.mrboomdev.scrollix.util.format.Formats;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoView;

import java.util.ArrayList;
import java.util.List;

public class TabManager {
	protected static GeckoRuntime runtime;
	@SuppressLint("StaticFieldLeak")
	protected static BarsAnimator barsAnimator;
	private static GeckoView geckoView;
	private static Tab currentTab;
	private static List<TabListener> listeners;

	@SuppressLint("ClickableViewAccessibility")
	public static void setBarsAnimator(@NonNull BarsAnimator animator) {
		geckoView.setOnTouchListener(animator.getOnTouchListener());
		barsAnimator = animator;
	}

	public static void setBarsAreExpanded(boolean isExpanded) {
		if(barsAnimator == null) return;
		barsAnimator.setBarsAreExpanded(isExpanded);
	}

	public static Tab getCurrentTab() {
		return currentTab;
	}

	public static void addListener(TabListener listener) {
		listeners.add(listener);
	}

	public static void removeListener(TabListener listener) {
		listeners.remove(listener);
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
	}

	private static void initForTab(@NonNull Tab tab) {
		geckoView.setSession(tab.getSession());

		for(var listener : listeners) {
			listener.onTabFocused(tab);
		}

		ExtensionDelegator.update();
		var extensionController = runtime.getWebExtensionController();

		for(var _tab : TabStore.getAllTabs()) {
			if(_tab.didInit()) continue;

			if(_tab == tab) extensionController.setTabActive(tab.getSession(), true);
			else extensionController.setTabActive(_tab.getSession(), false);
		}
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
	}

	public static void startup() {
		var context = AppManager.getActivityContext();
		listeners = new ArrayList<>();

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
		listeners.clear();
		runtime.shutdown();

		runtime = null;
		barsAnimator = null;
	}

	protected static List<TabListener> getTabListeners() {
		return listeners;
	}
}