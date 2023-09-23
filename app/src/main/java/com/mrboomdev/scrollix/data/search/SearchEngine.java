package com.mrboomdev.scrollix.data.search;

public interface SearchEngine {
	String getHome();

	String getSearchUrl(String query);
}