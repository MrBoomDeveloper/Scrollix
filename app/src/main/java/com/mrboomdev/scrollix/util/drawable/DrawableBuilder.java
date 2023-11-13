package com.mrboomdev.scrollix.util.drawable;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;

public class DrawableBuilder {
	private int color, cornerRadius, strokeWidth, strokeColor;

	public DrawableBuilder() {}

	public DrawableBuilder(@ColorInt int color) {
		this.color = color;
	}

	public DrawableBuilder(String color) {
		this.color = Color.parseColor(color);
	}

	public DrawableBuilder setColor(@ColorInt int color) {
		this.color = color;
		return this;
	}

	public DrawableBuilder setCornerRadius(int cornerRadius) {
		this.cornerRadius = cornerRadius;
		return this;
	}

	public DrawableBuilder setStroke(@ColorInt int color, int width) {
		this.strokeColor = color;
		this.strokeWidth = width;
		return this;
	}

	public DrawableBuilder setStroke(String color, int width) {
		this.strokeColor = Color.parseColor(color);
		this.strokeWidth = width;
		return this;
	}

	public DrawableBuilder setColor(String color) {
		this.color = Color.parseColor(color);
		return this;
	}

	public Drawable build() {
		var drawable = new GradientDrawable();
		drawable.setColor(color);
		drawable.setStroke(strokeWidth, strokeColor);
		drawable.setCornerRadius(cornerRadius);
		return drawable;
	}
}