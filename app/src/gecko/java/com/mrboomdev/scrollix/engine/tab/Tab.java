package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.util.LinkUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

import java.util.Objects;

public class Tab {
	private final GeckoSession session;
	protected boolean canGoBack, canGoForward, didRestoreState;
	protected String url, title;
	private boolean didInit, isError, isCrash;
	protected int progress;

	public Tab(String url, boolean lateInit) {
		this.url = url;
		this.session = new GeckoSession();

		applySettings();
		applyDelegators();

		if(!lateInit) init();
		if(url != null) loadUrl(url);
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

		openDefaultPage();
	}

	public int getProgress() {
		return progress;
	}

	private void openDefaultPage(String url) {
		if((this.didRestoreState || this.url != null) && !Objects.equals("about:blank", this.url)) return;

		if(url != null) {
			loadUrl(url);
			return;
		}

		ExtensionManager.getUiExtensionPageUrl("pages/home.html", this::openDefaultPage);
	}

	private void openDefaultPage() {
		openDefaultPage(null);
	}

	public void setIsError(boolean isError) {
		this.isError = isError;
	}

	public boolean isError() {
		return isError;
	}

	public void setIsCrash(boolean isCrash) {
		this.isCrash = isCrash;
	}

	public boolean isCrash() {
		return isCrash;
	}

	public void setTitle(String title) {
		if(title == null || title.isBlank()) return;
		this.title = title;
	}

	protected void setUrl(@NonNull String url) {
		if(url.startsWith("intent://")) {
			if(this.url == null) {
				this.title = url;
				this.url = url;
			}

			return;
		}

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

	public GeckoSession.SessionState getState() {
		try {
			var sessionField = GeckoSession.class.getDeclaredField("mStateCache");
			sessionField.setAccessible(true);
			return  (GeckoSession.SessionState)sessionField.get(session);
		} catch(NoSuchFieldException | IllegalAccessException e) {
			throw new UnexpectedBehaviourException("Required field is missing or inaccessible!", e);
		}
	}

	public void restoreState(GeckoSession.SessionState state) {
		if(state == null) return;

		session.restoreState(state);
		didRestoreState = true;

		try {
			var current = state.get(state.getCurrentIndex());
			setUrl(current.getUri());
			setTitle(current.getTitle());
		} catch(IllegalStateException e) {
			e.printStackTrace();

			if(url != null) {
				loadUrl(url);
			}
		}
	}

	public void loadUrl(String url) {
		setUrl(url);
		session.loadUri(url);
	}

	public String getUrl() {
		return url;
	}

	public boolean didInit() {
		return didInit;
	}

	public String getTitle() {
		return title;
	}

	public void goBack() {
		session.goBack();
	}

	public void reload() {
		if(isError) {
			loadUrl(url);
			return;
		}

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

	public GeckoSession getSession() {
		return session;
	}
}