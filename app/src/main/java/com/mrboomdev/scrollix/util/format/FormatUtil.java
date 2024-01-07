package com.mrboomdev.scrollix.util.format;

import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;

import org.jetbrains.annotations.Contract;

import java.util.List;

public class FormatUtil {

	public static int getDip(float value) {
		return (int)getResponsiveValue(value, Dimension.DIP);
	}

	public static void setPadding(@NonNull View view, int padding) {
		view.setPadding(padding, padding, padding, padding);
	}

	@NonNull
	public static int[] getDip(@NonNull float ...values) {
		var newArray = new int[values.length];

		for(int i = 0; i < values.length; i++) {
			newArray[i] = (int)getResponsiveValue(values[i], Dimension.DIP);
		}

		return newArray;
	}

	public static int getBytesFromMegabytes(int mb) {
		return getBytesFromKilobytes(mb * 1024);
	}

	public static int getBytesFromKilobytes(int kb) {
		return kb * 1024;
	}

	@NonNull
	@Contract(pure = true)
	public static String formatFileSize(long bytes) {
		var kb = bytes / 1024;

		if(kb >= 1000) {
			var mb = kb / 1024;

			if(mb >= 1000) {
				var gb = mb / 1024;
				return gb + "gb";
			}

			return mb + "mb";
		}

		return kb + "kb";
	}

	public static String removeBase64ImagePrefix(String string) {
		for(var prefix : List.of(Formats.BASE64_JPEG_PREFIX, Formats.BASE64_JPG_PREFIX, Formats.BASE64_PNG_PREFIX)) {
			if(!string.startsWith(prefix)) continue;
			string = string.substring(prefix.length());
		}

		return string;
	}

	@NonNull
	public static int[] getSp(@NonNull float ...values) {
		var newArray = new int[values.length];

		for(int i = 0; i < values.length; i++) {
			newArray[i] = (int)getResponsiveValue(values[i], Dimension.SP);
		}

		return newArray;
	}

	public static int getSp(float value) {
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