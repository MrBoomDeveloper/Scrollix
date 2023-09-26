package com.mrboomdev.scrollix.data.tabs;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TabsManager {
	private static List<Tab> tabs = new ArrayList<>();

	@NonNull
	public static Tab create(Context context) {
		var tab = new Tab(context);

		tab.webView.loadUrl("file:///android_asset/pages/home.html");
		tab.reloadSettings();

		tabs.add(tab);

		return tab;
	}

	public static void remove(Tab tab) {
		tabs.remove(tab);
	}

	public static void restoreFromFile(File file) {
		tabs = new ArrayList<>();
	}

	public static int getCount() {
		return tabs.size();
	}
}