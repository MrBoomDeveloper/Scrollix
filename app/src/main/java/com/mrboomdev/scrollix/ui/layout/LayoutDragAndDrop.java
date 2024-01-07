package com.mrboomdev.scrollix.ui.layout;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Color;
import android.view.DragEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.transition.TransitionManager;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.ui.AppUi;
import com.mrboomdev.scrollix.util.callback.ViewUtil;

public class LayoutDragAndDrop {
	private static boolean isStarted;

	public static void start() {
		if(isStarted) return;
		isStarted = true;
		AppUi.barsAnimator.setIsExpanded(true);

		for(var view : ViewUtil.getChildIterable(AppUi.sidebar)) {
			setNestedLongClickCallback(view, view);
			view.setOnDragListener(new DragCallback());
		}

		for(var view : ViewUtil.getChildIterable(AppUi.topbar)) {
			setNestedLongClickCallback(view, view);
			view.setOnDragListener(new DragCallback());
		}

		for(var view : ViewUtil.getChildIterable(AppUi.bottombar)) {
			setNestedLongClickCallback(view, view);
			view.setOnDragListener(new DragCallback());
		}

		TransitionManager.beginDelayedTransition(AppUi.parent);
		TabManager.setWebViewIsActive(false);

		var context = AppManager.getActivityContext();

		var content = new LinearLayout(context);
		content.setBackgroundColor(Color.CYAN);
		content.setOnDragListener(new DragCallback());
		ViewUtil.setPadding(content, 50);
	}

	private static void setNestedLongClickCallback(View view, View originalView) {
		for(var child : ViewUtil.getChildIterable(view)) {
			setNestedLongClickCallback(child, originalView);
		}

		view.setOnLongClickListener(_view -> {
			var item = new ClipData.Item("a");
			var data = new ClipData("a", new String[]{ ClipDescription.MIMETYPE_TEXT_PLAIN }, item);
			var shadow = new View.DragShadowBuilder(originalView);

			originalView.startDragAndDrop(data, shadow, null, 0);

			return true;
		});
	}

	private static class DragCallback implements View.OnDragListener {

		@Override
		public boolean onDrag(View v, @NonNull DragEvent event) {
			switch(event.getAction()) {
				case DragEvent.ACTION_DRAG_STARTED -> {
					if(event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
						v.setBackgroundColor(Color.BLUE);
						return true;
					}
				}

				case DragEvent.ACTION_DRAG_ENTERED -> {
					v.setBackgroundColor(Color.GREEN);
					return true;
				}

				case DragEvent.ACTION_DRAG_LOCATION -> {

					return true;
				}

				case DragEvent.ACTION_DRAG_EXITED -> {
					v.setBackgroundColor(Color.RED);
					return true;
				}

				case DragEvent.ACTION_DROP -> {
					v.setBackgroundColor(Color.YELLOW);
					return true;
				}

				case DragEvent.ACTION_DRAG_ENDED -> {
					v.setBackgroundColor(Color.WHITE);


					return true;
				}
			}

			return false;
		}
	}

	public static void end() {
		if(!isStarted) return;

		finish();
	}

	public static void cancel() {
		if(!isStarted) return;

		finish();
	}

	private static void finish() {
		if(!isStarted) return;

		for(var view : ViewUtil.getChildIterable(AppUi.sidebar)) {
			view.setOnLongClickListener(null);
		}

		TransitionManager.beginDelayedTransition(AppUi.parent);
		TabManager.setWebViewIsActive(true);

		isStarted = false;
	}
}