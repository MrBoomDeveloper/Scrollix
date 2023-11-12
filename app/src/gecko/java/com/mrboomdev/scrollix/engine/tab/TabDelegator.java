package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.engine.EngineInternal;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
public class TabDelegator {
	private final Tab tab;

	public TabDelegator(Tab tab) {
		this.tab = tab;
	}

	public void register() {
		tab.getSession().setProgressDelegate(new GeckoSession.ProgressDelegate() {
			@Override
			public void onPageStop(@NonNull GeckoSession session, boolean success) {
				for(var listener : TabManager.getTabListeners()) {
					listener.onTabLoadingFinished(tab);
				}
			}

			@Override
			public void onProgressChange(@NonNull GeckoSession session, int progress) {
				for(var listener : TabManager.getTabListeners()) {
					listener.onTabLoadingProgress(tab, progress);
				}
			}

			@Override
			public void onPageStart(@NonNull GeckoSession session, @NonNull String url) {
				tab.url = url;

				for(var listener : TabManager.getTabListeners()) {
					listener.onTabLoadingStarted(tab);
				}
			}
		});

		tab.getSession().setNavigationDelegate(new GeckoSession.NavigationDelegate() {

			@Override
			public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession session, @NonNull String uri) {
				var tab = TabStore.createTab(uri, true);
				return GeckoResult.fromValue(tab.getSession());
			}

			@Override
			public GeckoResult<String> onLoadError(@NonNull GeckoSession session, @Nullable String uri, @NonNull WebRequestError error) {
				//TODO: provide additional data to the error page
				return GeckoResult.fromValue(EngineInternal.Link.ERROR.getRealUrl());
			}

			@Override
			public void onCanGoForward(@NonNull GeckoSession session, boolean canGoForward) {
				tab.canGoForward = canGoForward;
			}

			@Override
			public void onCanGoBack(@NonNull GeckoSession session, boolean canGoBack) {
				tab.canGoBack = canGoBack;
			}
		});
	}
}