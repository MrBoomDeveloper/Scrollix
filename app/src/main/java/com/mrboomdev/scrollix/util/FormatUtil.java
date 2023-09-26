package com.mrboomdev.scrollix.util;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.NonNull;

public class FormatUtil {

	public static float getResponsiveValue(int value, @NonNull Dimension dimension, @NonNull Context context) {
		return TypedValue.applyDimension(dimension.getType(), value, context.getResources().getDisplayMetrics());
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