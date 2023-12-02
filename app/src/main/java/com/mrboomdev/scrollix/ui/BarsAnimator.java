package com.mrboomdev.scrollix.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.transition.TransitionManager;

import com.mrboomdev.scrollix.R;

public class BarsAnimator {
	public static final short TOPBAR = 1, BOTTOMBAR = 2;
	private final View.OnTouchListener touchListener;
	private ConstraintLayout parent;
	private View topbar, bottombar, contentHolder;
	private View bottomHelper;
	private float startY, currentY, offset;
	private boolean topbarExpandable = true, bottombarExpandable = true;

	public BarsAnimator() {
		this.touchListener = new TouchListener();
	}

	public void setBarsFromActivity(@NonNull Activity activity) {
		parent = activity.findViewById(R.id.main_screen_parent);
		contentHolder = activity.findViewById(R.id.webViewHolder);

		topbar = activity.findViewById(R.id.top_bar);
		bottombar = activity.findViewById(R.id.bottom_bar);

		bottomHelper = activity.findViewById(R.id.bottomHelper);
	}

	public View.OnTouchListener getOnTouchListener() {
		return touchListener;
	}

	public void setBarsExpandable(int flags) {
		//topbarExpandable = ((flags & TOPBAR) == TOPBAR);
		//bottombarExpandable = ((flags & BOTTOMBAR) == BOTTOMBAR);
	}

	public void setBarsAreExpanded(boolean areExpanded) {
		if(parent != null) {
			TransitionManager.beginDelayedTransition(parent);
		}

		offset = areExpanded ? 0 : topbar.getHeight();
		doALittleUpdate();
	}

	public void doALittleUpdate() {
		var difference = Math.round(offset);

		if(topbar != null && topbarExpandable) {
			var topbarParams = (ConstraintLayout.LayoutParams)topbar.getLayoutParams();
			topbarParams.topMargin = -difference;
			topbar.requestLayout();
		}

		if(bottombar != null && bottombarExpandable) {
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
			switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN -> {
					startY = event.getRawY();
				}

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