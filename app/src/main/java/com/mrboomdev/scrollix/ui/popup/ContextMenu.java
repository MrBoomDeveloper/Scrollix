package com.mrboomdev.scrollix.ui.popup;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ContextMenu extends LinearLayout {
	private final String url;

	public ContextMenu(Context context, @NonNull Builder builder) {
		super(context);

		setBackgroundResource(R.color.black);
		setOrientation(VERTICAL);

		url = builder.url;

		for(var action : builder.actions) {
			var padding = FormatUtil.getDip(10);

			var linear = new LinearLayout(context);
			linear.setPadding(padding, padding, padding, padding);
			linear.setFocusable(true);
			linear.setClickable(true);
			addView(linear);

			linear.setForeground(ResourcesCompat.getDrawable(
					getResources(),
					R.drawable.ripple_search,
					context.getTheme()));

			var title = new TextView(context);
			title.setText(action.title());
			title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

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
		}
	}
}