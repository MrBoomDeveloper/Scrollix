package com.mrboomdev.scrollix.util;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.net.URL;

public class FormatUtil {

	public static boolean isUrlValid(String url) {
		try {
			new URL(url).toURI();
			return true;
		} catch(Exception e) {
			return false;
		}
	}

	@Contract(value = "_, _ -> param1", pure = true)
	public static String formatUrl(String url, @NonNull UrlFormatRules rules) {
		if(rules.removeProtocol) {
			url = url.substring(url.indexOf("://") + 3);
		}

		if(rules.removeWww && url.startsWith("www.")) {
			url = url.substring(url.indexOf("www.") + 3);
		}

		if(rules.removeHash && url.contains("#")) {
			url = url.substring(0, url.indexOf("#"));
		}

		if(rules.removeParameters && url.contains("?")) {
			url = url.substring(0, url.indexOf("?"));
		}

		while(url.startsWith("/") || url.startsWith(".")) {
			url = url.substring(1);
		}

		while(url.endsWith("/") || url.endsWith("?") || url.endsWith("#")) {
			url = url.substring(0, url.length() - 1);
		}

		return url;
	}

	public static class UrlFormatRules {
		public boolean removeProtocol, removeWww;
		public boolean removeHash, removeParameters, removeTracking;
		public boolean removeExtension;
	}
}