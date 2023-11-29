package com.mrboomdev.scrollix.engine;

public class EngineInternal {
	public static final String INTERNAL_LINK_PREFIX = "file:///android_asset/pages/";

	public enum Link {
		HOME(INTERNAL_LINK_PREFIX + "home.html", "scrollix://home"),
		SETTINGS(INTERNAL_LINK_PREFIX + "settings.html", "scrollix://settings"),
		DOWNLOADS(INTERNAL_LINK_PREFIX + "downloads.html", "scrollix://downloads"),
		HISTORY(INTERNAL_LINK_PREFIX + "history.html", "scrollix://history"),
		BOOKMARKS(INTERNAL_LINK_PREFIX + "bookmarks.html", "scrollix://bookmarks");

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