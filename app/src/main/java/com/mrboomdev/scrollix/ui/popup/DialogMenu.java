package com.mrboomdev.scrollix.ui.popup;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mrboomdev.scrollix.util.FormatUtil;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;

import java.util.ArrayList;
import java.util.List;

public class DialogMenu {
	private final List<Action> actions = new ArrayList<>();
	private Runnable closeCallback;
	private final Context context;
	private String title, description;
	private AlertDialog dialog;

	public DialogMenu(Context context) {
		this.context = context;
	}

	public DialogMenu setTitle(String title) {
		this.title = title;
		return this;
	}

	public DialogMenu setDescription(String description) {
		this.description = description;
		return this;
	}

	public DialogMenu setOnCloseCallback(Runnable callback) {
		this.closeCallback = callback;
		return this;
	}

	public DialogMenu addAction(String title) {
		actions.add(new Action(title, null));
		return this;
	}

	public DialogMenu addAction(String title, Runnable callback) {
		actions.add(new Action(title, callback));
		return this;
	}

	public void show() {
		var parentViewPadding = FormatUtil.getDip(6);
		var elementPadding = FormatUtil.getDip(12);
		var smallElementPadding = FormatUtil.getDip(4);
		var smallTextSize = FormatUtil.getSp(10);

		var background = new DrawableBuilder("#141315")
				.setCornerRadius(16)
				.setStroke("#000000", 4)
				.build();

		var dialogView = new LinearLayout(context);
		dialogView.setOrientation(LinearLayout.VERTICAL);
		dialogView.setPadding(parentViewPadding, parentViewPadding, parentViewPadding, parentViewPadding);
		dialogView.setBackground(background);

		var titleView = new TextView(context);
		titleView.setText(title);

		titleView.setPadding(
				(int)(elementPadding * 1.75f),
				(int)(elementPadding * 1.5f),
				(int)(elementPadding * 1.75f),
				elementPadding);

		titleView.setTextSize(FormatUtil.getSp(14));
		titleView.setTextColor(Color.WHITE);
		dialogView.addView(titleView);

		if(description != null) {
			var descriptionView = new TextView(context);
			descriptionView.setText(description);

			descriptionView.setPadding(
					(int)(elementPadding * 1.75f),
					elementPadding / 2,
					(int)(elementPadding * 1.75f),
					elementPadding);

			descriptionView.setTextSize(smallTextSize);
			dialogView.addView(descriptionView);
		}

		if(!actions.isEmpty()) {
			var actionsView = new LinearLayout(context);
			actionsView.setOrientation(LinearLayout.HORIZONTAL);
			actionsView.setPadding(elementPadding, elementPadding, elementPadding, elementPadding);
			dialogView.addView(actionsView);

			for(var action : actions) {
				var actionView = new LinearLayout(context);
				actionView.setOrientation(LinearLayout.HORIZONTAL);
				actionView.setFocusable(true);
				actionView.setClickable(true);
				actionView.setPadding(smallElementPadding, smallElementPadding, smallElementPadding, smallElementPadding);
				actionView.setForeground(DrawableUtil.createRippleDrawable("#cc555555", "#ccffffff", 10));
				actionView.setBackground(DrawableUtil.createDrawable("#303030", 10));
				actionView.setGravity(Gravity.CENTER);

				actionView.setOnClickListener(_view -> {
					var callback = action.callback();
					if(callback != null) callback.run();

					dialog.dismiss();
				});

				actionsView.addView(actionView, 0, ViewGroup.LayoutParams.WRAP_CONTENT);
				((LinearLayout.LayoutParams)actionView.getLayoutParams()).weight = 1;
				((LinearLayout.LayoutParams)actionView.getLayoutParams()).topMargin = elementPadding;

				var actionTextView = new TextView(context);
				actionTextView.setText(action.title());

				actionTextView.setPadding(
						smallElementPadding,
						(int)(smallElementPadding * 1.5f),
						smallElementPadding,
						(int)(smallElementPadding * 1.5f));

				actionTextView.setTextSize(smallTextSize);
				actionTextView.setTextColor(Color.WHITE);
				actionView.addView(actionTextView);
			}
		}

		this.dialog = new AlertDialog.Builder(context)
				.setView(dialogView)
				.setOnDismissListener(_dialog -> {
					dispose();

					if(closeCallback != null) {
						closeCallback.run();
					}
				})
				.show();

		var window = dialog.getWindow();

		if(window != null) {
			window.setBackgroundDrawable(null);
		}
	}

	private void dispose() {
		dialog = null;
	}

	public record Action(String title, Runnable callback) {}
}