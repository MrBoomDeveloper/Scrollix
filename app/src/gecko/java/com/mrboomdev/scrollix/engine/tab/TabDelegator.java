package com.mrboomdev.scrollix.engine.tab;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.app.IncognitoActivity;
import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.ui.popup.ContextMenu;
import com.mrboomdev.scrollix.ui.popup.DialogMenu;
import com.mrboomdev.scrollix.util.AndroidUtil;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;

public class TabDelegator {
	private final Tab tab;

	public TabDelegator(Tab tab) {
		this.tab = tab;
	}

	public void register() {
		tab.getSession().setPromptDelegate(new GeckoSession.PromptDelegate() {

			@Override
			public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
				new DialogMenu(AppManager.getActivityContext())
						.setTitle(prompt.title)
						.setDescription(prompt.message)
						.addAction("Continue")
						.setOnCloseCallback(prompt::dismiss)
						.show();

				return null;
			}
		});

		tab.getSession().setContentDelegate(new GeckoSession.ContentDelegate() {
			@Override
			public void onFullScreen(@NonNull GeckoSession session, boolean fullScreen) {
				for(var listener : TabManager.getTabListeners()) {
					listener.onTabFullscreenToggle(tab, fullScreen);
				}
			}

			@Override
			public void onCloseRequest(@NonNull GeckoSession session) {
				TabStore.removeTab(tab);
			}

			@Override
			public void onFocusRequest(@NonNull GeckoSession session) {
				TabManager.setCurrentTab(tab);
			}

			@Override
			public void onContextMenu(@NonNull GeckoSession session, int screenX, int screenY, @NonNull ContextElement element) {
				var context = AppManager.getActivityContext();

				var menu = new ContextMenu.Builder(context)
						.setTitle(element.title + " : " + element.altText)
						.setDismissOnSelect(true);

				if(element.linkUri != null) {
					menu.addAction("Open link in new tab", () -> TabStore.createTab(element.linkUri, true));
					menu.addAction("Open link in background", () -> TabStore.createTab(element.linkUri, false));
					menu.addAction("Open link in new incognito tab", () -> {
						var intent = new Intent(context, IncognitoActivity.class);
						intent.setData(Uri.parse(element.linkUri));
						context.startActivity(intent);
					});
					//menu.addAction("Download image", () -> {});
					menu.addAction("Share link", () -> AndroidUtil.share("Share image link", element.linkUri));
					menu.addAction("Copy link to clipboard", () -> AndroidUtil.copyToClipboard(element.linkUri));
				}

				switch(element.type) {
					case ContextElement.TYPE_IMAGE -> {
						menu.addAction("Open image in new tab", () -> TabStore.createTab(element.srcUri, true));
						menu.addAction("Open image in background", () -> TabStore.createTab(element.srcUri, false));
						menu.addAction("Open image in new incognito tab", () -> {
							var intent = new Intent(context, IncognitoActivity.class);
							intent.setData(Uri.parse(element.srcUri));
							context.startActivity(intent);
						});
						//menu.addAction("Download image", () -> {});
						menu.addAction("Share image link", () -> AndroidUtil.share("Share image link", element.srcUri));
						menu.addAction("Copy image link to clipboard", () -> AndroidUtil.copyToClipboard(element.srcUri));
					}

					case ContextElement.TYPE_AUDIO -> {
						menu.addAction("Open audio in new tab", () -> TabStore.createTab(element.srcUri, true));
						menu.addAction("Open audio in background", () -> TabStore.createTab(element.srcUri, false));
						menu.addAction("Open audio in new incognito tab", () -> {
							var intent = new Intent(context, IncognitoActivity.class);
							intent.setData(Uri.parse(element.srcUri));
							context.startActivity(intent);
						});
						//menu.addAction("Download image", () -> {});
						menu.addAction("Share audio link", () -> AndroidUtil.share("Share image link", element.srcUri));
						menu.addAction("Copy audio link to clipboard", () -> AndroidUtil.copyToClipboard(element.srcUri));
					}

					case ContextElement.TYPE_VIDEO -> {
						menu.addAction("Open video in new tab", () -> TabStore.createTab(element.srcUri, true));
						menu.addAction("Open video in background", () -> TabStore.createTab(element.srcUri, false));
						menu.addAction("Open video in new incognito tab", () -> {
							var intent = new Intent(context, IncognitoActivity.class);
							intent.setData(Uri.parse(element.srcUri));
							context.startActivity(intent);
						});
						//menu.addAction("Download image", () -> {});
						menu.addAction("Share video link", () -> AndroidUtil.share("Share image link", element.srcUri));
						menu.addAction("Copy video link to clipboard", () -> AndroidUtil.copyToClipboard(element.srcUri));
					}

					case ContextElement.TYPE_NONE -> {}
				}

				menu.setTitle(element.title != null ? element.title : element.altText);
				menu.setLink(element.linkUri);
				menu.setImage(element.srcUri);
				menu.build();
			}

			@Override
			public void onTitleChange(@NonNull GeckoSession session, @Nullable String title) {
				tab.title = title;

				for(var listener : TabManager.getTabListeners()) {
					listener.onTabGotTitle(tab, title);
				}
			}
		});

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
				tab.setUrl(url);

				for(var listener : TabManager.getTabListeners()) {
					listener.onTabLoadingStarted(tab);
				}
			}
		});

		tab.getSession().setNavigationDelegate(new GeckoSession.NavigationDelegate() {

			@Override
			public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession session, @NonNull String uri) {
				var tab = new Tab(uri, true);
				TabStore.addTab(tab);
				TabManager.setCurrentTab(tab, false);
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