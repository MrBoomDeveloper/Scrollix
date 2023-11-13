package com.mrboomdev.scrollix.data.search;

import android.graphics.drawable.Drawable;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;

public class YandexSearch implements SearchEngine {

	@Override
	public String getHome() {
		return "yandex.com";
	}

	@Override
	public String getSearchPrefix() {
		return "/search/touch/?text=";
	}

	@Override
	public Drawable getIcon() {
		return DrawableUtil.getDrawable(R.drawable.ic_google_colorful);
	}
}