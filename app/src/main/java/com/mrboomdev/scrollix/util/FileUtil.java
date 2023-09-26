package com.mrboomdev.scrollix.util;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import java.io.File;
public class FileUtil {

	public static String readFile(File file) {
		return null;
	}

	public static File getFile(String path, Context context) {
		return null;
	}

	public static Drawable getDrawable(@DrawableRes int id, @NonNull Context context) {
		return ResourcesCompat.getDrawable(context.getResources(), id, context.getTheme());
	}

	public static void setDrawableColor(Drawable drawable, int color) {
		DrawableCompat.setTint(drawable, color);
	}
}