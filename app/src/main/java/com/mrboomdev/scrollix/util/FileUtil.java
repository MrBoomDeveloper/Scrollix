package com.mrboomdev.scrollix.util;

import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mrboomdev.scrollix.app.AppManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
public class FileUtil {

	public static String readFile(File file) {
		try(var stream = new FileInputStream(file)) {
			return null;
		} catch(IOException e) {
			throw new RuntimeException("Failed to read a file!", e);
		}
	}

	@NonNull
	public static Drawable copyDrawable(Drawable drawable) {
		if(drawable == null) {
			throw new NullPointerException("Drawable can't be null!");
		}

		var state = drawable.getConstantState();

		if(state == null) {
			throw new NullPointerException("Drawable state can't be null!");
		}

		return state.newDrawable().mutate();
	}

	public static Drawable getDrawable(@DrawableRes int id) {
		var context = AppManager.getAppContext();
		return ResourcesCompat.getDrawable(context.getResources(), id, context.getTheme());
	}

	@NonNull
	public static Drawable getDrawable(@DrawableRes int id, String color) {
		var drawable = copyDrawable(getDrawable(id));
		setDrawableColor(drawable, Color.parseColor(color));
		return drawable;
	}

	public static void setDrawableColor(Drawable drawable, int color) {
		DrawableCompat.setTint(drawable, color);
	}
}