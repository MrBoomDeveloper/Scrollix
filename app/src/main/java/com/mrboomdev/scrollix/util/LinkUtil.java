package com.mrboomdev.scrollix.util;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.AppSettings;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
public class LinkUtil {
	public static final String ANDROID_ASSET = "file:///android_asset";
	public static final String SCROLLIX_PROTOCOL = "scrollix://";
	public static final String SCROLLIX_PAGES = ANDROID_ASSET + "/pages/";

	public static boolean isUrlValid(String url) {
		if(url == null) return false;

		try {
			new URL(url.replace("scrollix://", "https://")).toURI();
			return true;
		} catch(MalformedURLException | URISyntaxException e) {
			return false;
		}
	}

	@Nullable
	public static String tryToFixUrl(String url) {
		try {
			String newUrl = "http://" + url;
			new URL(newUrl).toURI();

			var split = newUrl.split("\\.");
			if(split.length <= 1) return null;

			return LinkUtil.resolveInputUrl(newUrl);
		} catch(Exception e) {
			e.printStackTrace();
			return null;
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

	@NonNull
	public static String generateFileName(String url, @NonNull String contentDisposition) {
		var fileNameHeader = "filename=";
		var nameIndex = contentDisposition.indexOf(fileNameHeader);

		if(nameIndex == -1) return generateFileName(url);

		var name = contentDisposition.substring(nameIndex + fileNameHeader.length());

		if(name.contains(" ")) {
			return name.substring(0, name.indexOf(" "));
		}

		return name;
	}

	public static String removeProtocol(String url) {
		for(var beginChar : List.of("http", "ftp", "s", "://", "www.")) {
			if(!url.startsWith(beginChar)) continue;
			url = url.substring(url.indexOf(beginChar) + beginChar.length());
		}

		return url;
	}

	@NonNull
	public static String generateFileName(@NonNull String url) {
		var name = url.substring(url.lastIndexOf("/") + 1);

		if(name.contains("#")) {
			name = name.substring(0, name.indexOf("#"));
		}

		if(name.contains("?")) {
			name = name.substring(0, name.indexOf("?"));
		}

		return name;
	}

	public static String formatInputUrl(String url) {
		if(AppManager.settings.urlFormatRules.replaceScrollix && url.startsWith(SCROLLIX_PAGES)) {
			var shortenUrl = url.substring(SCROLLIX_PAGES.length());

			for(var link : ScrollixUrls.values()) {
				if(shortenUrl.startsWith(link.getRealUrl())) {
					return SCROLLIX_PROTOCOL + link.getScrollixUrl();
				}
			}
		}

		return url;
	}

	public static String resolveInputUrl(String url) {
		if(AppManager.settings.urlFormatRules.replaceScrollix && url.startsWith(SCROLLIX_PROTOCOL)) {
			var shortenUrl = url.substring(SCROLLIX_PROTOCOL.length());

			for(var link : ScrollixUrls.values()) {
				var fullUrl = link.getFullUrl();

				if(shortenUrl.startsWith(fullUrl)) {
					return fullUrl;
				}
			}
		}

		return url;
	}

	public enum ScrollixUrls {
		HOME("home", "home.html"),
		SETTINGS("settings", "settings.html"),
		DOWNLOADS("downloads", "list.html?show=downloads"),
		HISTORY("history", "list.html?show=history"),
		BOOKMARKS("bookmarks", "list.html?show=bookmarks");

		private final String scrollixUrl, realUrl;

		ScrollixUrls(String scrollixStyle, String realStyle) {
			this.scrollixUrl = scrollixStyle;
			this.realUrl = realStyle;
		}

		public String getScrollixUrl() {
			return scrollixUrl;
		}

		public String getFullUrl() {
			return SCROLLIX_PAGES + getRealUrl();
		}

		public String getRealUrl() {
			return realUrl;
		}
	}

	@Contract(value = "_, _ -> param1", pure = true)
	public static String formatUrl(String url, @NonNull AppSettings.UrlFormatRules rules) {
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
}