package com.mrboomdev.scrollix.engine;

public class EngineInternal {
	public static final String INTERNAL_LINK_PREFIX = "resource://android/assets/pages/";

	public enum Link {
		HOME(INTERNAL_LINK_PREFIX + "home.html", "scrollix://home"),
		SETTINGS(INTERNAL_LINK_PREFIX + "settings.html", "scrollix://settings"),
		ERROR(INTERNAL_LINK_PREFIX + "error.html", "scrollix://error"),
		DOWNLOADS("list.html?page=downloads", "scrollix://downloads"),
		HISTORY("list.html?page=history", "scrollix://history"),
		BOOKMARKS("list.html?page=bookmarks", "scrollix://bookmarks");

		final String realUrl, scrollixUrl;

		Link(String realUrl, String scrollixUrl) {
			this.realUrl = realUrl;
			this.scrollixUrl = scrollixUrl;
		}

		public String getRealUrl() {
			return realUrl;
		}

		public String getScrollixUrl() {
			return scrollixUrl;
		}
	}
}