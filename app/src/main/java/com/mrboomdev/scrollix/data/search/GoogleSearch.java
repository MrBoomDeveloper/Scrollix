package com.mrboomdev.scrollix.data.search;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.util.callback.CallbackController;
import com.mrboomdev.scrollix.util.callback.CallbackWithError;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GoogleSearch implements SearchEngine {
	private static final String SEARCH_QUERY_PREFIX = "https://google.com/complete/search?q=";
	private static final String SEARCH_QUERY_SUFFIX = "&client=opera";

	@Override
	public CallbackController getSearchResults(String query, CallbackWithError<List<SearchSuggestion>, Exception> callback) {
		var okhttp = new OkHttpClient.Builder().build();
		var request = new Request.Builder().url(SEARCH_QUERY_PREFIX + query + SEARCH_QUERY_SUFFIX).build();
		var call = okhttp.newCall(request);

		var moshi = new Moshi.Builder().build();
		JsonAdapter<List<String>> adapter = moshi.adapter(Types.newParameterizedType(List.class, String.class));

		new Thread(() -> call.enqueue(new Callback() {
			@Override
			public void onFailure(@NonNull Call call, @NonNull IOException e) {
				if(call.isCanceled()) return;

				callback.onError(new UnexpectedBehaviourException("Failed to fetch search query results", e));
			}

			@Override
			public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
				var string = Objects.requireNonNull(response.body()).string();
				List<String> results;

				try {
					var startIndex = string.indexOf("\",[") + 2;
					var endIndex = string.indexOf("]") + 1;
					var substring = string.substring(startIndex, endIndex);
					results = Objects.requireNonNull(adapter.fromJson(substring));
				} catch(StringIndexOutOfBoundsException e) {
					return;
				}

				var suggestions = new ArrayList<SearchSuggestion>(results.size());

				for(var suggestion : results) {
					suggestions.add(new SearchSuggestion(suggestion));
				}

				callback.onSuccess(suggestions);
			}
		})).start();

		return new CallbackController() {
			@Override
			public void cancel() {
				call.cancel();
			}
		};
	}

	@Override
	public String getHome() {
		return "google.com";
	}

	@Override
	public String getSearchPrefix() {
		return "/search?q=";
	}

	@Override
	public Drawable getIcon() {
		return DrawableUtil.getDrawable(R.drawable.ic_google_colorful);
	}
}