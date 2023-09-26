package com.mrboomdev.scrollix.data.search;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.res.ResourcesCompat;

import com.mrboomdev.scrollix.R;
public class GoogleSearch implements SearchEngine {
	private final String domain = "https://www.google.com";

	@Override
	public String getHome() {
		return domain;
	}

	@Override
	public String getSearchUrl(String query) {
		return domain + "/search?q=" + query;
	}

	@Override
	public Drawable getIcon(Context context) {
		return ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_google_colorful, context.getTheme());
	}
}