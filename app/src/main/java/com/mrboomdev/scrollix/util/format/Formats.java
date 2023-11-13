package com.mrboomdev.scrollix.util.format;

public class Formats {
	public static int LARGE_TEXT = 14, NORMAL_TEXT = 10;
	public static int SMALL_PADDING = 4, NORMAL_PADDING = 6, BIG_PADDING = 12, LARGE_PADDING = 20;

	public static void init() {
		LARGE_TEXT = FormatUtil.getSp(14);
		NORMAL_TEXT = FormatUtil.getSp(10);

		SMALL_PADDING = FormatUtil.getDip(4);
		NORMAL_PADDING = FormatUtil.getDip(6);
		BIG_PADDING = FormatUtil.getDip(12);
		LARGE_PADDING = FormatUtil.getDip(20);
	}
}