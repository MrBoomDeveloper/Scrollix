package com.mrboomdev.scrollix.engine;

public class EngineInternal {
	public static final String INTERNAL_LINK_PREFIX = "resource://android/assets/pages/";

	public enum Link {
		HOME("pages/home.html", "scrollix://home"),
		SETTINGS("pages/settings.html", "scrollix://settings"),
		ERROR("pages/error.html", "scrollix://error"),
		DOWNLOADS("pages/list.html?page=downloads", "scrollix://downloads"),
		HISTORY("pages/list.html?page=history", "scrollix://history"),
		BOOKMARKS("pages/list.html?page=bookmarks", "scrollix://bookmarks"),
		EXTENSIONS("pages/extensions.html", "scrollix://extensions");

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