package com.mrboomdev.scrollix.ui.popup;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ContextMenu extends LinearLayout {
	private final String url;

	public ContextMenu(Context context, @NonNull Builder builder) {
		super(context);
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		var background = new DrawableBuilder(theme.popupBackground)
				.setCornerRadius(16)
				.setStroke("#000000", 4)
				.build();

		setBackground(background);
		setOrientation(VERTICAL);

		url = builder.url;

		for(var action : builder.actions) {
			var linear = new LinearLayout(context);
			linear.setFocusable(true);
			linear.setClickable(true);
			linear.setForeground(DrawableUtil.getDrawable(R.drawable.ripple_square));
			FormatUtil.setPadding(linear, Formats.BIG_PADDING);
			addView(linear);

			var title = new TextView(context);
			title.setText(action.title());
			title.setTextSize(Formats.NORMAL_TEXT);

			linear.setOnClickListener(view -> {
				action.callback().run();

				if(builder.dismissOnSelect) {
					builder.dialog.dismiss();
				}
			});

			linear.addView(title);
		}
	}

	public record Action(String title, Runnable callback) {}

	public static class Builder {
		private final List<Action> actions = new ArrayList<>();
		private String url, title;
		private final Context context;
		private boolean dismissOnSelect;
		private AlertDialog dialog;

		public Builder(Context context) {
			this.context = context;
		}

		public Builder setDismissOnSelect(boolean bool) {
			this.dismissOnSelect = bool;
			return this;
		}

		public Builder addAction(String title, Runnable callback) {
			actions.add(new Action(title, callback));
			return this;
		}

		public Builder setTitle(String title) {
			this.title = title;
			return this;
		}

		public void build() {
			var menu = new ContextMenu(context, this);

			dialog = new AlertDialog.Builder(context)
					.setView(menu)
					.show();

			var window = dialog.getWindow();

			if(window != null) {
				window.setBackgroundDrawable(null);
			}
		}
	}
}