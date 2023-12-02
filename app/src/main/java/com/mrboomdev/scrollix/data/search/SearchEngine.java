package com.mrboomdev.scrollix.data.search;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.util.LinkUtil;
import com.mrboomdev.scrollix.util.callback.Callback1;
import com.mrboomdev.scrollix.util.callback.CallbackController;
import com.mrboomdev.scrollix.util.callback.CallbackWithError;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

public interface SearchEngine {
	String getHome();

	String getSearchPrefix();

	default boolean isSearchUrl(String url) {
		if(url == null) return false;

		var shortenUrl = LinkUtil.removeProtocol(url);
		return shortenUrl.startsWith(getHome() + getSearchPrefix());
	}

	default String getSearchUrl(String query) {
		return "https://" + getHome() + getSearchPrefix() + query;
	}

	Drawable getIcon();

	default String parseQuery(@NonNull String url) {
		if(!isSearchUrl(url)) return url;

		var shortenUrl = LinkUtil.removeProtocol(url);
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

	default CallbackController getSearchResults(String query, CallbackWithError<List<SearchSuggestion>, Exception> callback) {
		if(this instanceof GoogleSearch) {
			throw new UnexpectedBehaviourException("Google search results doesn't override it's default behaviour!");
		}

		return Preset.GOOGLE.getEngine().getSearchResults(query, callback);
	}

	default CallbackController getSearchResults(String query, Callback1<List<SearchSuggestion>> callback) {
		return getSearchResults(query, CallbackWithError.fromValue(callback));
	}

	record SearchSuggestion(String title) {}

	static String parseQueryAll(String url) {
		if(url == null) return null;

		for(var preset : Preset.values()) {
			var engine = preset.getEngine();
			if(engine == null) continue;

			var result = engine.parseQuery(url);
			if(!result.equals(url)) return result;
		}

		return url;
	}

	enum Preset {
		GOOGLE(new GoogleSearch()),
		DUCKDUCKGO(null),
		YANDEX(new YandexSearch()),
		BING(null),
		STARTPAGE(null);

		private final SearchEngine engine;

		Preset(SearchEngine engine) {
			this.engine = engine;
		}

		public SearchEngine getEngine() {
			return this.engine;
		}
	}
}