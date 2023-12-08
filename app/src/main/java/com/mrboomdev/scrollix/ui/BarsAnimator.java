package com.mrboomdev.scrollix.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.transition.TransitionManager;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;

public class BarsAnimator {
	private final View.OnTouchListener touchListener;
	private ConstraintLayout parent;
	private View topbar, bottombar, contentHolder;
	private View bottomHelper;
	private float startY, currentY, offset;
	private boolean barsExpandable;
	private Runnable changeSettingListener;

	public BarsAnimator() {
		this.touchListener = new TouchListener();
	}

	public void setBarsFromActivity(@NonNull Activity activity) {
		parent = activity.findViewById(R.id.main_screen_parent);
		contentHolder = activity.findViewById(R.id.webViewHolder);

		topbar = activity.findViewById(R.id.top_bar);
		bottombar = activity.findViewById(R.id.bottom_bar);
		bottomHelper = activity.findViewById(R.id.bottomHelper);

		AppManager.settings.removeChangeListener("collapseBars", changeSettingListener);

		changeSettingListener = () -> setBarsAreExpandable(AppManager.settings.collapseBars);
		changeSettingListener.run();

		AppManager.settings.addChangeListener("collapseBars", changeSettingListener);
	}

	public View.OnTouchListener getOnTouchListener() {
		return touchListener;
	}

	public void setBarsAreExpanded(boolean areExpanded) {
		if(parent != null) {
			TransitionManager.beginDelayedTransition(parent);
		}

		offset = areExpanded ? 0 : topbar.getHeight();
		doALittleUpdate();
	}

	public void setBarsAreExpandable(boolean areExpandable) {
		barsExpandable = areExpandable;
		if(!areExpandable) offset = 0;

		if(parent != null) {
			TransitionManager.beginDelayedTransition(parent);
		}

		doALittleUpdate();
	}

	public void doALittleUpdate() {
		var difference = Math.round(offset);

		if(topbar != null) {
			var topbarParams = (ConstraintLayout.LayoutParams)topbar.getLayoutParams();
			topbarParams.topMargin = -difference;
			topbar.requestLayout();
		}

		if(bottombar != null) {
			var bottombarParams = (ConstraintLayout.LayoutParams)bottombar.getLayoutParams();
			bottombarParams.bottomMargin = -difference;
			bottombar.requestLayout();
		}

		if(bottomHelper != null) {
			var bottomHelperParams = (ConstraintLayout.LayoutParams)bottomHelper.getLayoutParams();
			bottomHelperParams.topMargin = -difference;
			bottomHelper.requestLayout();
		}
	}

	private class TouchListener implements View.OnTouchListener {

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View v, @NonNull MotionEvent event) {
			if(!barsExpandable) return false;

			switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN -> startY = event.getRawY();

				case MotionEvent.ACTION_UP -> {
					currentY = event.getRawY();

					//Don't do anything with bars because user just touched the screen, not scrolled.
					if(Math.abs(startY - currentY) < 5) return false;

					setBarsAreExpanded(currentY > startY);
				}
			}

			return false;
		}
	}
}