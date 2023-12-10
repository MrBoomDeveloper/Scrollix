package com.mrboomdev.scrollix.engine.extenison;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.BuildConfig;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExtensionDelegator {
	private static final String TAG = "ExtensionDelegator";
	private static final Map<WebExtension, Set<Tab>> extensions = new HashMap<>();
	private static final List<WebExtension.Port> ports = new ArrayList<>();

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

	public static void updateSettingsForExtensions() {
		for(var port : ports) {
			try {
				var cacheDir = AppManager.getActivityContext().getCacheDir();

				var settingsInfo = new JSONObject(FileUtil.readAssetsString("settings.json")
						.replace("$[APP_BUILD_YEAR]", BuildConfig.BUILD_DATE_YEAR)
						.replace("$[APP_BUILD_VERSION]", BuildConfig.VERSION_NAME)
						.replace("$[APP_SIZE_CACHE]", String.valueOf(FileUtil.getFileSize(cacheDir))));

				var entry = new JSONArray()
						.put(settingsInfo)
						.put(new JSONObject(AppManager.settings.toString()));

				var result = new JSONObject()
						.put("action", "retrieve-settings")
						.put("values", entry);

				port.postMessage(result);
			} catch(JSONException e) {
				throw new UnexpectedBehaviourException(e);
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

		var portDelegate = new WebExtension.PortDelegate() {
			@Override
			public void onDisconnect(@NonNull WebExtension.Port port) {
				ports.remove(port);
				Log.i(TAG, "Disconnected port! " + port.name);
			}

			@Override
			public void onPortMessage(@NonNull Object message, @NonNull WebExtension.Port port) {
				if(!port.name.equals("scrollix")) return;

				try {
					if(message instanceof JSONObject json) {
						switch(json.getString("action")) {
							case "get-settings" -> updateSettingsForExtensions();

							case "get-downloads" -> {
								var result = new JSONObject()
										.put("action", "retrieve-downloads")
										.put("value", new JSONArray());

								port.postMessage(result);
							}

							default -> Log.e(TAG, "Unknown action! " + json.getString("action"));
						}
					}
				} catch(JSONException e) {
					e.printStackTrace();
				}
			}
		};

		var messageDelegate = new WebExtension.MessageDelegate() {
			@Override
			public void onConnect(@NonNull WebExtension.Port port) {
				ports.add(port);
				Log.i(TAG, "Connected to a port! " + port.name);

				port.setDelegate(portDelegate);
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

							case "update-settings" -> AppManager.settings.merge(json.get("value").toString());

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