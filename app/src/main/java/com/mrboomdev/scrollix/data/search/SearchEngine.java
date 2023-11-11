package com.mrboomdev.scrollix.data.search;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

public interface SearchEngine {
	String getHome();

	String getSearchPrefix();

	default String getSearchUrl(String query) {
		return "https://" + getHome() + getSearchPrefix() + query;
	}

	Drawable getIcon();

	default String parseQuery(@NonNull String url) {
		var shortenUrl = url + "";

		for(var beginChar : List.of("http", "ftp", "s", "://", "www.")) {
			if(!shortenUrl.startsWith(beginChar)) continue;
			shortenUrl = shortenUrl.substring(shortenUrl.indexOf(beginChar) + beginChar.length());
		}

		if(!shortenUrl.startsWith(getHome())) return url;

		var prefix = getSearchPrefix();
		int prefixIndex = shortenUrl.indexOf(prefix);
		if(prefixIndex == -1) return url;

		var query = shortenUrl.substring(prefixIndex + prefix.length());

		for(var endChar : List.of("&", "#")) {
			if(!query.contains(endChar)) continue;
			query = query.substring(0, query.indexOf(endChar));
		}

		try {
			final String encoding = "UTF-8";
			return URLDecoder.decode(query, encoding);
		} catch(UnsupportedEncodingException e) {
			e.printStackTrace();
			return query;
		}
	}

	static String parseQueryAll(String url) {
		for(var preset : SearchEnginePreset.values()) {
			var engine = preset.getEngine();
			if(engine == null) continue;

			var result = engine.parseQuery(url);
			if(!result.equals(url)) return result;
		}

		return url;
	}

	enum SearchEnginePreset {
		GOOGLE(new GoogleSearch()),
		DUCKDUCKGO(null),
		YANDEX(new YandexSearch()),
		BING(null),
		STARTPAGE(null);

		private final SearchEngine engine;

		SearchEnginePreset(SearchEngine engine) {
			this.engine = engine;
		}

		public SearchEngine getEngine() {
			return this.engine;
		}
	}
}