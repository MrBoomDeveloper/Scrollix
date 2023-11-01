package com.mrboomdev.scrollix.util;

import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;

public class FormatUtil {

	public static float getDip(int value) {
		return getResponsiveValue(value, Dimension.DIP);
	}

	public static float getSp(int value) {
		return getResponsiveValue(value, Dimension.SP);
	}

	public static float getResponsiveValue(int value, @NonNull Dimension dimension) {
		var context = AppManager.getAppContext();
		var metrics = context.getResources().getDisplayMetrics();

		return TypedValue.applyDimension(dimension.getType(), value, metrics);
	}

	public enum Dimension {
		DIP(TypedValue.COMPLEX_UNIT_DIP),
		SP(TypedValue.COMPLEX_UNIT_SP);

		private final int type;

		Dimension(int type) {
			this.type = type;
		}

		public int getType() {
			if(type != TypedValue.COMPLEX_UNIT_DIP && type != TypedValue.COMPLEX_UNIT_SP) {
				throw new RuntimeException("Unsupported Android dimension!");
			}

			return type;
		}
	}
}