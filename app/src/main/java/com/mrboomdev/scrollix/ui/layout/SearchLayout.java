package com.mrboomdev.scrollix.ui.layout;

import android.content.Context;
import android.graphics.Color;
import android.widget.LinearLayout;

import com.mrboomdev.scrollix.data.AppSettings;

public class SearchLayout extends LinearLayout {
	private boolean isOpened;

	public SearchLayout(Context context, AppSettings settings) {
		super(context);

		setOrientation(VERTICAL);
		setBackgroundColor(Color.parseColor("#222222"));
	}

	public void show() {
		setVisibility(VISIBLE, false);
		isOpened = true;


	}

	public void hide() {
		setVisibility(GONE, false);
		isOpened = false;
	}

	public boolean isOpened() {
		return isOpened;
	}

	@Override
	public void setVisibility(int visibility) {
		setVisibility(visibility, true);
	}

	public void setVisibility(int visibility, boolean promptMe) {
		super.setVisibility(visibility);

		if(promptMe) {
			switch(visibility) {
				case VISIBLE -> show();
				case GONE, INVISIBLE -> hide();
			}
		}
	}
}