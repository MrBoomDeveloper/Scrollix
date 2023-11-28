package com.mrboomdev.scrollix.ui.popup;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.ArrayList;
import java.util.List;

public class DialogMenu {
	private final List<Action> actions = new ArrayList<>();
	private Runnable closeCallback;
	private final Context context;
	private String title, description;
	private AlertDialog dialog;
	private boolean isCancelable;

	public DialogMenu(Context context) {
		this.context = context;
		setCancelable(true);
	}

	public DialogMenu setTitle(String title) {
		this.title = title;
		return this;
	}

	public DialogMenu setCancelable(boolean enable) {
		this.isCancelable = enable;
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

	@UiThread
	public void show() {
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		var background = new DrawableBuilder(theme.popupBackground)
				.setCornerRadius(16)
				.setStroke("#000000", 4)
				.build();

		var dialogView = new LinearLayout(context);
		dialogView.setOrientation(LinearLayout.VERTICAL);
		dialogView.setBackground(background);

		dialogView.setPadding(
				Formats.NORMAL_PADDING,
				Formats.NORMAL_PADDING,
				Formats.NORMAL_PADDING,
				Formats.NORMAL_PADDING);

		var titleView = new TextView(context);
		titleView.setText(title);

		titleView.setPadding(
				Formats.LARGE_PADDING,
				Formats.LARGE_PADDING,
				Formats.LARGE_PADDING,
				Formats.BIG_PADDING);

		titleView.setTextSize(Formats.LARGE_TEXT);
		titleView.setTextColor(Color.parseColor(theme.popupTitle));
		dialogView.addView(titleView);

		addContent(dialogView, theme);
		addActions(dialogView, theme);

		this.dialog = new AlertDialog.Builder(context)
				.setView(dialogView)
				.setCancelable(isCancelable)
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
			window.setDimAmount(.75f);
		}
	}

	private void addContent(@NonNull LinearLayout dialogView, ThemeSettings theme) {
		var scrollView = new ScrollView(context);
		var contentView = new LinearLayout(context);

		if(description != null) {
			var descriptionView = new TextView(context);
			descriptionView.setText(description);
			descriptionView.setTextColor(Color.parseColor(theme.popupDescription));
			descriptionView.setTextIsSelectable(true);

			descriptionView.setPadding(
					Formats.LARGE_PADDING,
					Formats.NORMAL_PADDING,
					Formats.LARGE_PADDING,
					Formats.BIG_PADDING);

			descriptionView.setTextSize(Formats.NORMAL_TEXT);
			contentView.addView(descriptionView);
		}

		contentView.setOrientation(LinearLayout.VERTICAL);
		scrollView.addView(contentView);
		dialogView.addView(scrollView);

		((LinearLayout.LayoutParams)scrollView.getLayoutParams()).weight = 1;
	}

	private void addActions(@NonNull LinearLayout dialogView, ThemeSettings theme) {
		if(actions.isEmpty()) return;

		var actionsView = new LinearLayout(context);
		actionsView.setOrientation(LinearLayout.HORIZONTAL);
		dialogView.addView(actionsView);

		actionsView.setPadding(
				Formats.BIG_PADDING,
				Formats.BIG_PADDING,
				Formats.BIG_PADDING,
				Formats.BIG_PADDING);

		for(var action : actions) {
			var actionView = new LinearLayout(context);
			actionView.setOrientation(LinearLayout.HORIZONTAL);
			actionView.setFocusable(true);
			actionView.setClickable(true);

			actionView.setPadding(
					Formats.SMALL_PADDING,
					Formats.SMALL_PADDING,
					Formats.SMALL_PADDING,
					Formats.SMALL_PADDING);

			actionView.setForeground(DrawableUtil.createRippleDrawable(theme.primaryRipple, 10));
			actionView.setBackground(DrawableUtil.createDrawable(theme.primary, 10));
			actionView.setGravity(Gravity.CENTER);

			actionView.setOnClickListener(_view -> {
				var callback = action.callback();
				if(callback != null) callback.run();

				dialog.dismiss();
			});

			actionsView.addView(actionView, 0, ViewGroup.LayoutParams.WRAP_CONTENT);
			((LinearLayout.LayoutParams)actionView.getLayoutParams()).weight = 1;
			((LinearLayout.LayoutParams)actionView.getLayoutParams()).topMargin = Formats.BIG_PADDING;

			var actionTextView = new TextView(context);
			actionTextView.setText(action.title());

			actionTextView.setPadding(
					Formats.SMALL_PADDING,
					Formats.NORMAL_PADDING,
					Formats.SMALL_PADDING,
					Formats.NORMAL_PADDING);

			actionTextView.setTextSize(Formats.NORMAL_TEXT);
			actionTextView.setTextColor(Color.WHITE);
			actionView.addView(actionTextView);
		}
	}

	private void dispose() {
		dialog = null;
	}

	public record Action(String title, Runnable callback) {}
}