package com.mrboomdev.scrollix.engine.extenison;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExtensionDelegator {
	private static final String TAG = "ExtensionDelegator";
	private static final Map<WebExtension, Set<Tab>> extensions = new HashMap<>();
	private static final Map<WebExtension, WebExtension.Port> ports = new HashMap<>();

	public static void update() {
		for(var entry : extensions.entrySet()) {
			for(var tab : TabStore.getAllTabs()) {
				if(!tab.didInit()) continue;

				if(!entry.getValue().contains(tab)) {
					entry.getValue().add(tab);
					setSessionDelegators(tab);
				}
			}
		}
	}

	public static void setExtensionsDelegators(@NonNull WebExtension extension) {
		extensions.put(extension, new HashSet<>());
		createDelegatorsFor(extension);
	}

	public static void setSessionDelegators(@NonNull Tab tab) {
		createDelegatorsFor(tab.getSession());
	}

	private static void createDelegatorsFor(Object target) {
		final var extension = (target instanceof WebExtension) ? (WebExtension) target : null;
		final var session = (target instanceof GeckoSession) ? (GeckoSession) target : null;

		var messageDelegate = new WebExtension.MessageDelegate() {
			@Override
			public void onConnect(@NonNull WebExtension.Port port) {
				if(extension != null) ports.put(extension, port);
				Log.i(TAG, "Connected to a port! " + port.name);

				port.setDelegate(new WebExtension.PortDelegate() {
					@Override
					public void onDisconnect(@NonNull WebExtension.Port port) {
						if(extension != null) ports.remove(extension, port);
						Log.i(TAG, "Disconnected port! " + port.name);
					}

					@Override
					public void onPortMessage(@NonNull Object message, @NonNull WebExtension.Port port) {
						Log.i(TAG, "Port message: " + message);
					}
				});
			}

			@Nullable
			@Override
			public GeckoResult<Object> onMessage(@NonNull String nativeApp, @NonNull Object message, @NonNull WebExtension.MessageSender sender) {
				if(!nativeApp.equals("scrollix")) return null;

				if(message instanceof JSONObject json) {
					try {
						switch(json.getString("action")) {
							case "reload" -> TabManager.getCurrentTab().reload();
							case "open-search" -> {
								var search = AppManager.getMainActivityContext().searchLayout;
								search.show();
								search.editText.setText("");
							}
							default -> Log.e(TAG, "Unknown action: " + json.getString("action"));
						}
					} catch(JSONException e) {
						throw new UnexpectedBehaviourException(e);
					}
				}

				return null;
			}
		};

		if(extension != null) extension.setMessageDelegate(messageDelegate, "scrollix");

		if(session != null) {
			for(var _extension : extensions.keySet()) {
				var controller = session.getWebExtensionController();
				controller.setMessageDelegate(_extension, messageDelegate, "scrollix");
			}
		}
	}
}