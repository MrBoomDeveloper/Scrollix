package com.mrboomdev.scrollix.util.drawable;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.Size;

public abstract class DrawableBuilder {
	protected int cornerRadius, strokeWidth, strokeColor;
	protected Shape shape = Shape.RECTANGLE;

	public static Drawable ofColor(String color) {
		return new ColorDrawableBuilder(color).build();
	}

	public static Drawable ofColor(@ColorInt int color) {
		return new ColorDrawableBuilder(color).build();
	}

	public DrawableBuilder() {}

	public DrawableBuilder setCornerRadius(int cornerRadius) {
		this.cornerRadius = cornerRadius;
		return this;
	}

	public DrawableBuilder setShape(Shape shape) {
		this.shape = shape;
		return this;
	}

	public DrawableBuilder setStroke(@ColorInt int color, int width) {
		this.strokeColor = color;
		this.strokeWidth = width;
		return this;
	}

	public DrawableBuilder setStroke(@Size(min = 3) String color, int width) {
		this.strokeColor = Color.parseColor(color);
		this.strokeWidth = width;
		return this;
	}

	public abstract Drawable build();

	public static class ColorDrawableBuilder extends DrawableBuilder {
		protected int color;

		public ColorDrawableBuilder() {}

		public ColorDrawableBuilder(@ColorInt int color) {
			this.color = color;
		}

		public ColorDrawableBuilder(@Size(min = 3) String color) {
			this.color = Color.parseColor(color);
		}

		public ColorDrawableBuilder setColor(@Size(min = 3) String color) {
			this.color = Color.parseColor(color);
			return this;
		}

		public ColorDrawableBuilder setColor(@ColorInt int color) {
			this.color = color;
			return this;
		}

		@SuppressLint("WrongConstant")
		public Drawable build() {
			var drawable = new GradientDrawable();
			drawable.setColor(color);
			drawable.setStroke(strokeWidth, strokeColor);
			drawable.setCornerRadius(cornerRadius);
			drawable.setShape(shape.getAndroidCode());
			return drawable;
		}
	}

	public static class RippleDrawableBuilder extends DrawableBuilder {
		protected int backgroundColor, foregroundColor;
		protected Drawable mask;

		public RippleDrawableBuilder() {}

		public RippleDrawableBuilder setMask(Drawable drawable) {
			this.mask = drawable;
			return this;
		}

		public RippleDrawableBuilder setBackgroundColor(@Size(min = 3) String color) {
			backgroundColor = Color.parseColor(color);
			return this;
		}

		public RippleDrawableBuilder setColor(@Size(min = 3) String color) {
			backgroundColor = Color.parseColor(color);
			foregroundColor = Color.parseColor(color);
			return this;
		}

		public RippleDrawableBuilder setColor(@ColorInt int color) {
			backgroundColor = color;
			foregroundColor = color;
			return this;
		}

		public RippleDrawableBuilder setForegroundColor(@Size(min = 3) String color) {
			foregroundColor = Color.parseColor(color);
			return this;
		}

		@Override
		public RippleDrawableBuilder setShape(Shape shape) {
			super.setShape(shape);
			return this;
		}

		public RippleDrawableBuilder setBackgroundColor(@ColorInt int color) {
			backgroundColor = color;
			return this;
		}

		public RippleDrawableBuilder setForegroundColor(@ColorInt int color) {
			foregroundColor = color;
			return this;
		}

		@Override
		public RippleDrawable build() {
			if(mask == null) {
				mask = new ColorDrawableBuilder()
						.setColor(foregroundColor)
						.setStroke(strokeColor, strokeWidth)
						.setCornerRadius(cornerRadius)
						.setShape(shape)
						.build();
			}

			return new RippleDrawable(ColorStateList.valueOf(backgroundColor), null, mask);
		}
	}

	public enum Shape {
		OVAL(GradientDrawable.OVAL),
		RECTANGLE(GradientDrawable.RECTANGLE);

		private final int code;

		Shape(int code) {
			this.code = code;
		}

		public int getAndroidCode() {
			return code;
		}
	}
}