package com.mrboomdev.scrollix.engine.tab;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.ui.layout.BarsAnimator;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoView;

import java.util.ArrayList;
import java.util.List;

public class TabManager {
	protected static GeckoRuntime runtime;
	protected static BarsAnimator barsAnimator;
	private static GeckoView geckoView;
	private static Tab currentTab;
	private static List<TabListener> listeners;

	public static void setBarsAnimator(BarsAnimator animator) {
		barsAnimator = animator;
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

	public static void setCurrentTab(@NonNull Tab tab, boolean tryToinit) {
		currentTab = tab;

		if(tryToinit) tab.init();
		geckoView.setSession(tab.getSession());

		for(var listener : listeners) {
			listener.onTabFocused(tab);
		}
	}

	public static void setTabHolder(@NonNull ViewGroup view) {
		var context = AppManager.getActivityContext();

		geckoView = new GeckoView(context);
		geckoView.setFitsSystemWindows(true);

		view.addView(geckoView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
	}

	public static void startup() {
		var context = AppManager.getActivityContext();
		listeners = new ArrayList<>();

		var runtimeSettings = new GeckoRuntimeSettings.Builder()
				.aboutConfigEnabled(true)
				.allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
				.consoleOutput(false)
				.extensionsWebAPIEnabled(false)
				.remoteDebuggingEnabled(false)
				.preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
				.forceUserScalableEnabled(true)
				.extensionsProcessEnabled(false)
				.build();

		runtime = GeckoRuntime.create(context, runtimeSettings);

		TabStore.addTab(new Tab(EngineInternal.Link.HOME.getRealUrl()));
	}

	public static void dispose() {
		listeners.clear();
		runtime.shutdown();
		runtime = null;
	}

	protected static List<TabListener> getTabListeners() {
		return listeners;
	}
}