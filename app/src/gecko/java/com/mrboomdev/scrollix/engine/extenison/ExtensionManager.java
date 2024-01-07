package com.mrboomdev.scrollix.engine.extenison;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.ui.popup.dialog.DialogMenu;
import com.mrboomdev.scrollix.util.callback.Callback1;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import org.jetbrains.annotations.Contract;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.ArrayList;
import java.util.List;

public class ExtensionManager {
	public static final String UI_EXTENSION_ID = "scrollix-ui@mrboomdev.ru";
	public static final String UI_EXTENSION_PATH = "resource://android/assets/extensions/ui/";
	private static final String TAG = "ExtensionManager";
	private static final List<PendingUrlCallback> pendingUrlCallbacks = new ArrayList<>();
	private static List<WebExtension> extensions;
	private static String uiExtensionBaseUrl;
	private static GeckoRuntime runtime;
	private static boolean isLoadingExtensions = true;

	public static void getUiExtensionPageUrl(String relativePath, Callback1<String> callback) {
		getExtensionPageUrl(UI_EXTENSION_ID, relativePath, callback);
	}

	public static void getExtensionPageUrl(String extensionId, String relativePath, Callback1<String> callback) {
		if(isLoadingExtensions) {
			pendingUrlCallbacks.add(new PendingUrlCallback(extensionId, relativePath, callback));
			return;
		}

		resolvePendingCallback(extensionId, relativePath, callback);
	}

	public static String resolveOutputUrl(String url) {
		return url;
	}

	private static void resolvePendingCallback(String extensionId, String relativePath, Callback1<String> callback) {
		for(var extension : extensions) {
			if(!extension.id.equals(extensionId)) continue;

			callback.run(extension.metaData.baseUrl + relativePath);
			return;
		}

		throw new UnexpectedBehaviourException("Failed to find a extension with the same id!");
	}

	public static void update() {
		runtime.getWebExtensionController().list().accept(items -> {
			extensions = items;

			var iterator = pendingUrlCallbacks.iterator();
			while(iterator.hasNext()) {
				var item = iterator.next();
				resolvePendingCallback(item.extensionId, item.relativePath, item.callback);
				iterator.remove();
			}
		});
	}

	public static String getUiExtensionBaseUrl() {
		return uiExtensionBaseUrl;
	}

	@Contract(pure = true)
	public static void startup(@NonNull GeckoRuntime runtime) {
		ExtensionManager.runtime = runtime;
		var extensionController = runtime.getWebExtensionController();

		extensionController.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
			@Override
			public void onReady(@NonNull WebExtension extension) {
				update();
			}

			@Override
			public void onInstallationFailed(@Nullable WebExtension extension, @NonNull WebExtension.InstallException e) {
				handleError(extension, e, false);
				update();
			}
		});

		extensionController.ensureBuiltIn(UI_EXTENSION_PATH, UI_EXTENSION_ID)
				.accept(extension -> {
					if(extension == null) return;
					uiExtensionBaseUrl = extension.metaData.baseUrl;
					isLoadingExtensions = false;

					Log.i(TAG, "Successfully loaded Scrollix ui extension");
					ExtensionDelegator.setExtensionsDelegators(extension);
					ExtensionDelegator.update();
				}, e -> handleError("Scrollix UI", e, true));
	}

	private static void handleError(String extensionName, Throwable t, boolean isFatal) {
		var context = AppManager.getActivityContext();

		var title = isFatal ? "Failed to init an internal extension" : "Failed to install \"" + extensionName + "\"";
		var description = t != null ? Log.getStackTraceString(t) : "Unknown error has happened";

		var dialog = new DialogMenu(context)
				.setTitle(title)
				.setDescription(description)
				.setCancelable(!isFatal);

		if(isFatal) {
			dialog.addAction("Close app", AppManager::closeApp);
		} else {
			dialog.addAction("Ok");
		}

		dialog.show();
	}

	private static void handleError(@Nullable WebExtension extension, @Nullable WebExtension.InstallException e, boolean isFatal) {
		var name = extension != null ? extension.metaData.name : e != null ? e.extensionName : "Unknown extension";
		handleError(name, e, isFatal);
	}

	private record PendingUrlCallback(String extensionId, String relativePath, Callback1<String> callback) {}
}