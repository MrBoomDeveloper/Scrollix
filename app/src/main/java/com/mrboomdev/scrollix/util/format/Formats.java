package com.mrboomdev.scrollix.util.format;

import android.view.ViewGroup;

public class Formats {
	public static int SMALL_POPUP_RADIUS = 16;
	public static int LARGE_TEXT = 14, NORMAL_TEXT = 10, SMALL_TEXT = 9;
	public static int SMALL_PADDING = 4, NORMAL_PADDING = 6, PADDING = 9, BIG_PADDING = 12, LARGE_PADDING = 20;
	public static int SMALL_ELEMENT = 25, NORMAL_ELEMENT = 40, BIG_ELEMENT = 60;
	public static final String BASE64_IMAGE_PREFIX = "data:image/";
	public static final String BASE64_JPEG_PREFIX = "data:image/jpeg;base64,";
	public static final String BASE64_JPG_PREFIX = "data:image/jpg;base64,";
	public static final String BASE64_PNG_PREFIX = "data:image/png;base64,";
	public static int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
	public static int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;

	public static void init() {
		LARGE_TEXT = FormatUtil.getSp(14);
		NORMAL_TEXT = FormatUtil.getSp(10);
		SMALL_TEXT = FormatUtil.getSp(9);

		SMALL_PADDING = FormatUtil.getDip(4);
		NORMAL_PADDING = FormatUtil.getDip(6);
		PADDING = FormatUtil.getDip(9);
		BIG_PADDING = FormatUtil.getDip(12);
		LARGE_PADDING = FormatUtil.getDip(20);

		SMALL_ELEMENT =  FormatUtil.getDip(25);
		NORMAL_ELEMENT =  FormatUtil.getDip(40);
		BIG_ELEMENT =  FormatUtil.getDip(60);;
	}
}