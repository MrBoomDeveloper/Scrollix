package com.mrboomdev.scrollix.webview;

import android.os.Message;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.app.permission.Permission;
import com.mrboomdev.scrollix.app.permission.PermissionManager;
import com.mrboomdev.scrollix.util.AppUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Deprecated
public class MyWebChromeClient extends WebChromeClient {

	@Override
	public void onPermissionRequest(@NonNull PermissionRequest request) {
		var requestedPermissions = new HashMap<Permission, String>();
		var unknownPermissions = new ArrayList<String>();
		var requestedPermissionsOriginal = List.of(request.getResources());

		for(var permission : requestedPermissionsOriginal) {
			switch(permission) {
				case PermissionRequest.RESOURCE_AUDIO_CAPTURE -> requestedPermissions.put(Permission.MICROPHONE, permission);
				case PermissionRequest.RESOURCE_VIDEO_CAPTURE -> requestedPermissions.put(Permission.CAMERA, permission);
				default -> unknownPermissions.add(permission);
			}
		}

		if(!unknownPermissions.isEmpty()) {
			new MaterialAlertDialogBuilder(AppManager.getActivityContext())
					.setTitle("Requested unknown permissions")
					.setMessage("Sorry, but we don't know how to handle these permissions: \n" + unknownPermissions)
					.setPositiveButton("Continue", (_dialog, _button) -> _dialog.cancel())
					.setNegativeButton("Copy to clipboard", (_dialog, _button) -> {
						AppUtils.copyToClipboard(unknownPermissions.toString());
						_dialog.cancel();
					})
					.show();
		}

		var results = new HashMap<String, Boolean>();
		Runnable finishCallback = () -> {
			if(results.size() != requestedPermissions.size()) return;
			var list = new ArrayList<String>();

			for(var entry : results.entrySet()) {
				if(entry.getValue()) list.add(entry.getKey());
			}

			request.grant(list.toArray(new String[0]));
		};

		for(var permission : requestedPermissions.entrySet()) {
			var description = "Site \"" + request.getOrigin() + "\" wants to access the \"" + permission.getKey() + "\"permission";
			var result = new AtomicBoolean(false);
			var didSetValue = new AtomicBoolean(false);

			new MaterialAlertDialogBuilder(AppManager.getActivityContext())
					.setTitle("Permission request")
					.setMessage(description)
					.setOnDismissListener(_dialog -> {
						if(didSetValue.get()) return;

						results.put(permission.getValue(), result.get());
						finishCallback.run();
					})
					.setPositiveButton("Accept", (_dialog, _button) -> {
						didSetValue.set(true);

						PermissionManager.checkAndRequestPermission(permission.getKey(), isOk -> {
							results.put(permission.getValue(), isOk);
							finishCallback.run();
						});

						_dialog.cancel();
					})
					.setNegativeButton("Deny", (_dialog, _button) -> {
						results.put(permission.getValue(), false);
						didSetValue.set(true);
						_dialog.cancel();
						finishCallback.run();
					})
					.show();
		}
	}

	@Override
	public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
		AppUtils.runOnUiThread(() -> new MaterialAlertDialogBuilder(view.getContext())
				.setTitle("Url \"" + url + "\" says:")
				.setMessage(message)
				.setPositiveButton("Confirm", (_dialog, _button) -> {
					result.confirm();
					_dialog.cancel();
				})
				.setNegativeButton("Cancel", (_dialog, _button) -> {
					result.cancel();
					_dialog.cancel();
				})
				.show());

		return true;
	}

	@Override
	public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
		return false;
	}

	@Override
	public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
		AppManager.getActivityContext().runOnUiThread(() -> {
			new MaterialAlertDialogBuilder(view.getContext())
					.setTitle("Url \"" + url + "\" asks:")
					.setMessage(message)
					.setPositiveButton("Confirm", (_dialog, _button) -> {
						result.confirm();
						_dialog.cancel();
					})
					.setNegativeButton("Cancel", (_dialog, _button) -> {
						result.cancel();
						_dialog.cancel();
					})
					.show();
		});

		return true;
	}

	@Override
	public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
		return false;
	}
}