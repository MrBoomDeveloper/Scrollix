package com.mrboomdev.scrollix.data.search;

import android.graphics.drawable.Drawable;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;

public class GoogleSearch implements SearchEngine {

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