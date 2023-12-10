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
	private View topbar, bottombar,  bottomHelper;
	private float startY, currentY, offset;
	private boolean isExpandable, isExpanded;
	private Runnable changeSettingListener;

	public BarsAnimator() {
		this.touchListener = new TouchListener();
	}

	public void setBarsFromActivity(@NonNull Activity activity) {
		parent = activity.findViewById(R.id.main_screen_parent);

		topbar = activity.findViewById(R.id.top_bar);
		bottombar = activity.findViewById(R.id.bottom_bar);
		bottomHelper = activity.findViewById(R.id.bottomHelper);

		AppManager.settings.removeChangeListener("collapseBars", changeSettingListener);

		changeSettingListener = () -> setIsExpandable(AppManager.settings.collapseBars);
		changeSettingListener.run();

		AppManager.settings.addChangeListener("collapseBars", changeSettingListener);
	}

	public View.OnTouchListener getOnTouchListener() {
		return touchListener;
	}

	public void setIsExpanded(boolean isExpanded) {
		if(parent != null) {
			TransitionManager.beginDelayedTransition(parent);
		}

		setIsExpandedImmediately(isExpanded);
	}

	public void setIsExpandable(boolean isExpandable) {
		this.isExpandable = isExpandable;
		if(!isExpandable) offset = 0;

		if(parent != null) {
			TransitionManager.beginDelayedTransition(parent);
		}

		doALittleUpdate();
	}

	public void setIsExpandedImmediately(boolean isExpanded) {
		this.offset = isExpanded ? 0 : topbar.getHeight();
		this.isExpanded = isExpanded;
		this.doALittleUpdate();
	}

	public boolean isExpanded() {
		return isExpanded;
	}

	public boolean isExpandable() {
		return isExpandable;
	}

	private void doALittleUpdate() {
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
			if(!isExpandable) return false;

			switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN -> startY = event.getRawY();

				case MotionEvent.ACTION_UP -> {
					currentY = event.getRawY();

					//Don't do anything with bars because user just touched the screen, not scrolled.
					if(Math.abs(startY - currentY) < 10) return false;

					setIsExpanded(currentY > startY);
				}
			}

			return false;
		}
	}
}