package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.util.FileUtil;

public class TabsMenu {
	private final Context context;

	public TabsMenu(Context context) {
		this.context = context;
	}

	public void showAt(View view) {
		var linear = new LinearLayout(context);
		linear.setOrientation(LinearLayout.VERTICAL);
		linear.setBackgroundResource(R.color.black);

		var createIcon = FileUtil.getDrawable(R.drawable.ic_add_black, context);
		FileUtil.setDrawableColor(createIcon, Color.parseColor("#ccccdd"));

		var createButton = new ImageView(context);
		createButton.setPadding(6, 6, 6, 6);
		createButton.setImageDrawable(createIcon);
		createButton.setBackgroundResource(R.drawable.ripple_circle);
		createButton.setOnClickListener(_view -> {});
		createButton.setClickable(true);
		createButton.setFocusable(true);
		linear.addView(createButton);

		var popup = new PopupWindow(linear, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		popup.setFocusable(true);
		popup.showAsDropDown(view);
	}
}