package com.mrboomdev.scrollix.data.search;

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
}