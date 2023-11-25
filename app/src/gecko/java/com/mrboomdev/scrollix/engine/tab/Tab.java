package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.util.LinkUtil;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.util.Objects;

public class Tab {
	private final GeckoSession session;
	protected boolean canGoBack, canGoForward;
	protected String url, title;
	private boolean didInit;

	public Tab(String url, boolean lateInit) {
		this.url = url;

		session = new GeckoSession();
		applySettings();
		applyDelegators();

		if(!lateInit) init();
	}

	public void setTitle(String title) {
		this.title = title;
	}

	protected void setUrl(@NonNull String url) {
		if(!url.equals(this.url)) {
			this.title = url;
		}

		this.url = url;
	}

	public void applySettings() {
		var settings = session.getSettings();

		settings.setAllowJavascript(true);
		settings.setDisplayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER);
		settings.setSuspendMediaWhenInactive(true);
		settings.setUseTrackingProtection(true);

		settings.setUserAgentOverride(LinkUtil.UserAgent.CHROME_MOBILE.getUserAgentText());
	}

	public void applyDelegators() {
		var delegator = new TabDelegator(this);
		delegator.register();
	}

	public Tab(String url) {
		this(url, false);
	}

	public Tab(boolean lateInit) {
		this(null, lateInit);
	}

	public Tab() {
		this(null);
	}

	public void init() {
		if(didInit) return;
		didInit = true;

		if(session.isOpen()) return;

		session.open(TabManager.runtime);
		loadUrl(Objects.requireNonNullElse(url, EngineInternal.Link.HOME.getRealUrl()));
	}

	public void loadUrl(String url) {
		session.loadUri(url);
		this.url = url;
	}

	public String getUrl() {
		return url;
	}

	public String getTitle() {
		return title;
	}

	public void goBack() {
		session.goBack();
	}

	public void reload() {
		session.reload();
	}

	public boolean canGoBack() {
		return canGoBack;
	}

	public boolean canGoForward() {
		return canGoForward;
	}

	public void stopLoading() {
		session.stop();
	}

	public void goForward() {
		session.goForward();
	}

	public void dispose() {
		session.close();
	}

	protected GeckoSession getSession() {
		return session;
	}
}