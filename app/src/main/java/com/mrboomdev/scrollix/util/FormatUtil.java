package com.mrboomdev.scrollix.util;

import android.util.TypedValue;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;

public class FormatUtil {

	public static int getDip(int value) {
		return (int)getResponsiveValue(value, Dimension.DIP);
	}

	@NonNull
	public static int[] getDip(@NonNull int ...values) {
		var newArray = new int[values.length];

		System.arraycopy(values, 0, newArray, 0, values.length);

		for(int i = 0; i < values.length; i++) {
			newArray[i] = (int)getResponsiveValue(values[i], Dimension.DIP);
		}

		return newArray;
	}

	@NonNull
	public static int[] getSp(@NonNull int ...values) {
		var newArray = new int[values.length];

		System.arraycopy(values, 0, newArray, 0, values.length);

		for(int i = 0; i < values.length; i++) {
			newArray[i] = (int)getResponsiveValue(values[i], Dimension.SP);
		}

		return newArray;
	}

	public static int getSp(int value) {
		return (int)getResponsiveValue(value, Dimension.SP);
	}

	@NonNull
	public static float[] getResponsiveValues(@NonNull Dimension dimension, @NonNull float ...values) {
		var newArray = new float[values.length];

		System.arraycopy(values, 0, newArray, 0, values.length);

		for(int i = 0; i < values.length; i++) {
			newArray[i] = getResponsiveValue(values[i], dimension);
		}

		return newArray;
	}

	public static float getResponsiveValue(float value, @NonNull Dimension dimension) {
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