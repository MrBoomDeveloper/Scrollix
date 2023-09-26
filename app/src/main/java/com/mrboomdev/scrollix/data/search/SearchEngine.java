package com.mrboomdev.scrollix.data.search;

import android.content.Context;
import android.graphics.drawable.Drawable;
public interface SearchEngine {
	String getHome();
	String getSearchUrl(String query);
	Drawable getIcon(Context context);
}