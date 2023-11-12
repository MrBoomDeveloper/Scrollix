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
		tab.getSession().setNavigationDelegate(new GeckoSession.NavigationDelegate() {

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