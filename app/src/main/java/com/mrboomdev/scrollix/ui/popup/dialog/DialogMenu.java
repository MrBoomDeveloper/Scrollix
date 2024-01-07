package com.mrboomdev.scrollix.ui.popup.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.util.callback.ViewUtil;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.ArrayList;
import java.util.List;

public class DialogMenu {
	private final List<DialogElement> elements = new ArrayList<>();
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

	public DialogMenu addElement(DialogElement element) {
		elements.add(element);
		return this;
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
	public DialogMenu show() {
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		var background = new DrawableBuilder.ColorDrawableBuilder()
				.setColor(theme.popupBackground)
				.setCornerRadius(16)
				.setStroke("#000000", 4)
				.build();

		var dialogView = new LinearLayout(context);
		dialogView.setOrientation(LinearLayout.VERTICAL);
		dialogView.setBackground(background);
		ViewUtil.setPadding(dialogView, Formats.NORMAL_PADDING);

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

		return this;
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

		for(var element : elements) {
			var view = element.getView(context);
			contentView.addView(view, Formats.MATCH_PARENT, Formats.WRAP_CONTENT);
			ViewUtil.setMargin(view, Formats.BIG_PADDING, 0);
		}

		contentView.setOrientation(LinearLayout.VERTICAL);
		scrollView.addView(contentView);
		dialogView.addView(scrollView);

		ViewUtil.setWeight(scrollView, 1);
	}

	private void addActions(@NonNull LinearLayout dialogView, ThemeSettings theme) {
		if(actions.isEmpty()) return;

		var actionsView = new LinearLayout(context);
		actionsView.setOrientation(LinearLayout.HORIZONTAL);
		dialogView.addView(actionsView);
		ViewUtil.setPadding(actionsView, Formats.PADDING, Formats.BIG_PADDING);

		for(var action : actions) {
			var actionView = new LinearLayout(context);
			actionView.setOrientation(LinearLayout.HORIZONTAL);
			actionView.setFocusable(true);
			actionView.setClickable(true);
			ViewUtil.setPadding(actionView, Formats.SMALL_PADDING);

			actionView.setForeground(DrawableUtil.createRippleDrawable(theme.primaryRipple, 10));
			actionView.setBackground(DrawableUtil.createDrawable(theme.primary, 10));
			actionView.setGravity(Gravity.CENTER);

			actionView.setOnClickListener(_view -> {
				var callback = action.callback();
				if(callback != null) callback.run();

				dialog.dismiss();
			});

			actionsView.addView(actionView, 0, Formats.WRAP_CONTENT);
			ViewUtil.setMargin(actionView, Formats.SMALL_PADDING, 0);
			ViewUtil.setTopMargin(actionView, Formats.BIG_PADDING);
			ViewUtil.setWeight(actionView, 1);

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

	public void dismiss() {
		if(dialog == null) return;
		dialog.dismiss();
	}

	private void dispose() {
		dialog = null;
	}

	public record Action(String title, Runnable callback) {}
}