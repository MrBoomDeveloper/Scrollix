package com.mrboomdev.scrollix.util.callback;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.Iterator;

public class ViewUtil {

	@NonNull
	@Contract(pure = true)
	public static Iterable<View> getChildIterable(ViewGroup view) {
		return () -> getChildIterator(view);
	}

	public static void setWeight(@NonNull View view, int weight) {
		var params = view.getLayoutParams();

		if(params instanceof LinearLayout.LayoutParams layoutParams) {
			layoutParams.weight = weight;
		}
	}

	public static void setTopMargin(@NonNull View view, int margin) {
		var params = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
		params.topMargin = margin;
		view.requestLayout();
	}

	public static void setBottomMargin(@NonNull View view, int margin) {
		var params = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
		params.bottomMargin = margin;
		view.requestLayout();
	}

	public static void setRightMargin(@NonNull View view, int margin) {
		var params = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
		params.rightMargin = margin;
		view.requestLayout();
	}

	public static void setLeftMargin(@NonNull View view, int margin) {
		var params = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
		params.leftMargin = margin;
		view.requestLayout();
	}

	public static void setMargin(@NonNull View view, int left, int top, int right, int bottom) {
		var params = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
		params.setMargins(left, top, right, bottom);
	}

	public static void setMargin(@NonNull View view, int x, int y) {
		var params = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
		params.setMargins(x, y, x, y);
	}

	@NonNull
	@Contract(pure = true)
	public static Iterable<View> getChildIterable(View view) {
		if(view instanceof ViewGroup viewGroup) {
			return getChildIterable(viewGroup);
		}

		return EmptyIterator::new;
	}

	@NonNull
	@Contract(value = "_ -> new", pure = true)
	public static Iterator<View> getChildIterator(ViewGroup view) {
		return new ViewIterator(view);
	}

	public static void setPadding(@NonNull View view, int padding) {
		view.setPadding(padding, padding, padding, padding);
	}

	public static void setPadding(@NonNull View view, int x, int y) {
		view.setPadding(x, y, x, y);
	}

	private static class EmptyIterator implements Iterator<View> {

		@Override
		public boolean hasNext() {
			return false;
		}

		@Nullable
		@Contract(pure = true)
		@Override
		public View next() {
			return null;
		}
	}

	private static class ViewIterator implements Iterator<View> {
		private final ViewGroup view;
		private int current = 0;

		public ViewIterator(ViewGroup view) {
			this.view = view;
		}

		@Override
		public boolean hasNext() {
			return current < view.getChildCount();
		}

		@Override
		public View next() {
			var item = view.getChildAt(current);
			current++;
			return item;
		}
	}
}