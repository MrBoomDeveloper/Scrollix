package com.mrboomdev.scrollix.ui.popup.dialog;

import android.content.Context;
import android.view.View;

public abstract class DialogElement {
	private View view;

	public abstract void createView(Context context);

	public View getView(Context context) {
		if(view == null) {
			createView(context);
		}

		return view;
	}

	protected void setView(View view) {
		this.view = view;
	}
}