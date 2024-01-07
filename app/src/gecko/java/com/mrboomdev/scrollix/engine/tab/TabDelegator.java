package com.mrboomdev.scrollix.engine.tab;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.app.IntentHandler;
import com.mrboomdev.scrollix.data.download.UserMadeDownload;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.ui.AppUi;
import com.mrboomdev.scrollix.ui.IncognitoActivity;
import com.mrboomdev.scrollix.ui.popup.ContextMenu;
import com.mrboomdev.scrollix.ui.popup.dialog.DialogMenu;
import com.mrboomdev.scrollix.ui.popup.dialog.DialogTextInput;
import com.mrboomdev.scrollix.util.AppUtils;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

public class TabDelegator {
	private final Tab tab;

	public TabDelegator(Tab tab) {
		this.tab = tab;
	}

	public void register() {
		tab.getSession().setPromptDelegate(new GeckoSession.PromptDelegate() {

			@Override
			public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession session, @NonNull ButtonPrompt prompt) {
				/*var result = new GeckoResult<PromptResponse>();

				new DialogMenu(AppManager.getActivityContext())
						.setTitle(prompt.title)
						.setDescription(prompt.message)
						.addAction("Cancel", () -> result.complete(prompt.dismiss()))
						.setCancelable(false)
						.show();

				return result;*/

				return null;
			}

			@NonNull
			@Override
			public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession session, @NonNull TextPrompt prompt) {
				var result = new GeckoResult<PromptResponse>();

				var input = new DialogTextInput();
				input.setPlaceholder(prompt.defaultValue);
				input.setFocusOnShow(true);

				var dialog = new DialogMenu(AppManager.getActivityContext())
						.setTitle(prompt.title)
						.setDescription(prompt.message)
						.addElement(input)
						.addAction("Cancel", () -> result.complete(prompt.dismiss()))
						.addAction("Confirm", () -> result.complete(prompt.confirm(input.getText())))
						.setCancelable(false)
						.show();

				input.setDoneListener(text -> {
					result.complete(prompt.confirm(text));
					dialog.dismiss();
				});

				return result;
			}

			@Override
			public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
				var result = new GeckoResult<PromptResponse>();

				new DialogMenu(AppManager.getActivityContext())
						.setTitle(prompt.title)
						.setDescription(prompt.message)
						.addAction("Continue")
						.setOnCloseCallback(() -> result.complete(prompt.dismiss()))
						.show();

				return result;
			}
		});

		tab.getSession().setContentDelegate(new GeckoSession.ContentDelegate() {

			@Override
			public void onCrash(@NonNull GeckoSession session) {
				tab.setIsCrash(true);
			}

			@Override
			public void onExternalResponse(@NonNull GeckoSession session, @NonNull WebResponse response) {
				new UserMadeDownload(response.uri)
						.setHeaders(response.headers)
						.start();
			}

			@Override
			public void onShowDynamicToolbar(@NonNull GeckoSession geckoSession) {
				AppUi.barsAnimator.setIsExpanded(true);
			}

			@Override
			public void onFullScreen(@NonNull GeckoSession session, boolean fullScreen) {
				AppUi.toggleFullscreen(tab, fullScreen);
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
					menu.addAction("Share link", () -> AppUtils.share("Share link", element.linkUri));
					menu.addAction("Copy link to clipboard", () -> AppUtils.copyToClipboard(element.linkUri));
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

						menu.addAction("Download image", () -> {
							var download = new UserMadeDownload(element.srcUri);
							download.start();
						});

						menu.addAction("Share image link", () -> AppUtils.share("Share image link", element.srcUri));
						menu.addAction("Copy image link to clipboard", () -> AppUtils.copyToClipboard(element.srcUri));
					}

					case ContextElement.TYPE_AUDIO -> {
						menu.addAction("Open audio in new tab", () -> TabStore.createTab(element.srcUri, true));
						menu.addAction("Open audio in background", () -> TabStore.createTab(element.srcUri, false));
						menu.addAction("Open audio in new incognito tab", () -> {
							var intent = new Intent(context, IncognitoActivity.class);
							intent.setData(Uri.parse(element.srcUri));
							context.startActivity(intent);
						});

						menu.addAction("Download audio", () -> {
							var download = new UserMadeDownload(element.srcUri);
							download.start();
						});

						menu.addAction("Share audio link", () -> AppUtils.share("Share image link", element.srcUri));
						menu.addAction("Copy audio link to clipboard", () -> AppUtils.copyToClipboard(element.srcUri));
					}

					case ContextElement.TYPE_VIDEO -> {
						menu.addAction("Open video in new tab", () -> TabStore.createTab(element.srcUri, true));
						menu.addAction("Open video in background", () -> TabStore.createTab(element.srcUri, false));
						menu.addAction("Open video in new incognito tab", () -> {
							var intent = new Intent(context, IncognitoActivity.class);
							intent.setData(Uri.parse(element.srcUri));
							context.startActivity(intent);
						});

						menu.addAction("Download video", () -> {
							var download = new UserMadeDownload(element.srcUri);
							download.start();
						});

						menu.addAction("Share video link", () -> AppUtils.share("Share image link", element.srcUri));
						menu.addAction("Copy video link to clipboard", () -> AppUtils.copyToClipboard(element.srcUri));
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

				if(TabManager.getCurrentTab() == tab) {
					AppUi.searchBar.setTitle(title);
				}
			}
		});

		tab.getSession().setProgressDelegate(new GeckoSession.ProgressDelegate() {
			@Override
			public void onPageStop(@NonNull GeckoSession session, boolean success) {
				tab.progress = 100;
				AppUi.finishTabLoading(tab);
			}

			@Override
			public void onSessionStateChange(@NonNull GeckoSession session, @NonNull GeckoSession.SessionState sessionState) {
				tab.sessionState = sessionState;
			}

			@Override
			public void onProgressChange(@NonNull GeckoSession session, int progress) {
				tab.progress = progress;
				AppUi.updateTabLoading(tab);
			}

			@Override
			public void onPageStart(@NonNull GeckoSession session, @NonNull String url) {
				tab.setIsError(false);
				tab.setUrl(url);

				tab.progress = 0;
				AppUi.startTabLoading(tab);
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
				tab.setIsError(true);

				var errorMessage = switch(error.code) {
					case WebRequestError.ERROR_CONNECTION_REFUSED -> "Connection refused";
					case WebRequestError.ERROR_BAD_HSTS_CERT -> "Can't verify certificates";
					case WebRequestError.ERROR_CONTENT_CRASHED -> "Page has crashed";
					case WebRequestError.ERROR_UNKNOWN_PROTOCOL -> "Unknown protocol";
					case WebRequestError.ERROR_REDIRECT_LOOP -> "Page was redirected too much times";
					case WebRequestError.ERROR_SECURITY_BAD_CERT -> "Untrusted certificates were found";
					case WebRequestError.ERROR_SECURITY_SSL -> "SSL error has happened";
					case WebRequestError.ERROR_MALFORMED_URI -> "Invalid url was permitted";
					case WebRequestError.ERROR_FILE_NOT_FOUND -> "File was not found";
					case WebRequestError.ERROR_FILE_ACCESS_DENIED -> "File access denied";
					case WebRequestError.ERROR_CORRUPTED_CONTENT -> "Page content was corrupted";
					case WebRequestError.ERROR_NET_TIMEOUT -> "Connection timeout has passed";
					case WebRequestError.ERROR_NET_RESET -> "Connection has been reset";
					case WebRequestError.ERROR_NET_INTERRUPT -> "Connection was interrupted";
					default -> "Unknown error";
				};

				var result = new GeckoResult<String>();

				var relativePath = "pages/error.html?reason=" + errorMessage + "&url=" + uri;
				ExtensionManager.getUiExtensionPageUrl(relativePath, result::complete);

				return result;
			}

			@Nullable
			@Override
			public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession session, @NonNull LoadRequest request) {
				if(request.uri.startsWith("intent://")) {
					IntentHandler.launchInExternal(request.uri);
					return GeckoResult.deny();
				}

				return null;
			}

			@Override
			public void onCanGoForward(@NonNull GeckoSession session, boolean canGoForward) {
				tab.canGoForward = canGoForward;
				AppUi.updateBackForwardState();
			}

			@Override
			public void onCanGoBack(@NonNull GeckoSession session, boolean canGoBack) {
				tab.canGoBack = canGoBack;
				AppUi.updateBackForwardState();
			}
		});
	}
}