package com.mrboomdev.scrollix.util.drawable;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.util.AppUtils;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class DrawableUtil {

	@NonNull
	public static RippleDrawable createRippleDrawable(
			@ColorInt int backgroundColor,
			@ColorInt int foregroundColor,
			int radius
	) {
		var mask = createDrawable(foregroundColor, radius);
		return new RippleDrawable(ColorStateList.valueOf(backgroundColor), null, mask);
	}

	@NonNull
	public static RippleDrawable createRippleDrawable(
			@Size(min = 3) String backgroundColor,
			@Size(min = 3) String foregroundColor,
			int radius
	) {
		return createRippleDrawable(Color.parseColor(backgroundColor), Color.parseColor(foregroundColor), radius);
	}

	@NonNull
	public static RippleDrawable createRippleDrawable(@Size(min = 3) String color, int radius) {
		int intColor = Color.parseColor(color);
		return createRippleDrawable(intColor, intColor, radius);
	}

	@NonNull
	public static GradientDrawable createDrawable(@ColorInt int color, int cornerRadius) {
		var drawable = new GradientDrawable();
		drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		drawable.setColor(color);
		drawable.setCornerRadius(cornerRadius);
		drawable.setGradientCenter(0, 0);
		return drawable;
	}

	@NonNull
	public static GradientDrawable createDrawable(@Size(min = 3) String color, int cornerRadius) {
		return createDrawable(Color.parseColor(color), cornerRadius);
	}

	@NonNull
	public static GradientDrawable createDrawable(@ColorInt int color) {
		return createDrawable(color, 0);
	}

	@NonNull
	@Contract("_ -> new")
	public static Drawable readDrawable(File file) {
		try(var stream = new FileInputStream(file)) {
			return new BitmapDrawable(AppUtils.getResources(), stream);
		} catch(IOException e) {
			throw new RuntimeException("Failed to read a bitmap!", e);
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
	public static Drawable getDrawable(@DrawableRes int id, @ColorInt int color) {
		var drawable = copyDrawable(getDrawable(id));
		setDrawableColor(drawable, color);
		return drawable;
	}

	@NonNull
	public static Drawable getDrawable(@DrawableRes int id, @Size(min = 3) String color) {
		return getDrawable(id, Color.parseColor(color));
	}

	public static void setDrawableColor(Drawable drawable, int color) {
		DrawableCompat.setTint(drawable, color);
	}
}