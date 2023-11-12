package com.mrboomdev.scrollix.engine.tab;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.engine.EngineInternal;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoView;

public class TabManager {
	protected static GeckoRuntime runtime;
	private static GeckoView geckoView;

	public static void setCurrentTab(@NonNull Tab tab) {
		tab.init();
		geckoView.setSession(tab.getSession());
	}

	public static void setTabHolder(@NonNull ViewGroup view) {
		var context = AppManager.getActivityContext();

		geckoView = new GeckoView(context);
		view.addView(geckoView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
	}

	public static void startup() {
		var context = AppManager.getActivityContext();

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
		runtime.shutdown();
		runtime = null;
	}
}