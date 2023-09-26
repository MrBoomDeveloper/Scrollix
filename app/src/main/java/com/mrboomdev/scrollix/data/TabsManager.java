package com.mrboomdev.scrollix.data;

import android.content.Context;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TabsManager {
	private static List<Tab> tabs = new ArrayList<>();

	@NonNull
	public static Tab create(Context context) {
		var tab = new Tab();
		tab.webView = new WebView(context);
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

	public static class Tab {
		public String url, title;
		public WebView webView;
	}
}