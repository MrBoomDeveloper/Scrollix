package com.mrboomdev.scrollix.util;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class FormatUtil {

	public static boolean isUrlValid(String url) {
		try {
			new URL(url).toURI();
			return true;
		} catch(MalformedURLException | URISyntaxException e) {
			return false;
		}
	}

	public static boolean isSameLink(String link1, String link2) {
		if(!(isUrlValid(link1) && isUrlValid(link2))) return false;

		try {
			var url1 = new URL(link1);
			var url2 = new URL(link2);

			return url1.sameFile(url2);
		} catch(MalformedURLException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static float getResponsiveValue(int value, @NonNull Dimension dimension, @NonNull Context context) {
		return TypedValue.applyDimension(dimension.getType(), value, context.getResources().getDisplayMetrics());
	}

	public enum Dimension {
		DIP(TypedValue.COMPLEX_UNIT_DIP),
		SP(TypedValue.COMPLEX_UNIT_SP);

		private final int type;

		Dimension(int type) {
			this.type = type;
		}

		public int getType() {
			if(type != TypedValue.COMPLEX_UNIT_DIP && type != TypedValue.COMPLEX_UNIT_SP) {
				throw new RuntimeException("Unsupported Android dimension!");
			}

			return type;
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