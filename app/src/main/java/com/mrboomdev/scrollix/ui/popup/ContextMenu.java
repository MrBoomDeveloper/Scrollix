package com.mrboomdev.scrollix.ui.popup;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.divider.MaterialDivider;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.callback.ViewUtil;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ContextMenu extends LinearLayout {

	public ContextMenu(Context context, @NonNull Builder builder) {
		super(context);
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		var background = new DrawableBuilder.ColorDrawableBuilder()
				.setColor(theme.popupBackground)
				.setCornerRadius(16)
				.setStroke("#000000", 4)
				.build();

		setBackground(background);
		setOrientation(VERTICAL);
		setPadding(0, Formats.BIG_PADDING, 0, Formats.BIG_PADDING);

		var header = new LinearLayout(context);
		header.setOrientation(HORIZONTAL);
		ViewUtil.setPadding(header, Formats.LARGE_PADDING, Formats.BIG_PADDING);
		addView(header);

		if(builder.image != null) {
			var previewSize = FormatUtil.getDip(60);

			var preview = new ImageView(context);
			header.addView(preview, previewSize, previewSize);
			((LayoutParams)preview.getLayoutParams()).rightMargin = Formats.LARGE_PADDING;

			if(builder.isBase64Image) {
				var decodedBytes = Base64.decode(builder.image, Base64.DEFAULT);
				var bitmap = FileUtil.createBitmap(decodedBytes);
				preview.setImageBitmap(bitmap);
			} else {
				var imageLoader = ImageLoader.getInstance();
				imageLoader.displayImage(builder.image, preview);
			}
		}

		var headerText = new LinearLayout(context);
		headerText.setOrientation(VERTICAL);
		header.addView(headerText);

		if(builder.title != null) {
			var title = new TextView(context);
			title.setTextColor(Color.WHITE);
			title.setTextSize(Formats.NORMAL_TEXT);
			title.setTypeface(null, Typeface.BOLD);
			title.setText(builder.title);
			title.setMaxLines(2);
			headerText.addView(title);
			ViewUtil.setBottomMargin(title, Formats.NORMAL_PADDING);
		}

		if(!builder.isBase64Image && (builder.url != null || builder.image != null)) {
			var url = new TextView(context);
			url.setTextSize(Formats.SMALL_TEXT);
			url.setMaxLines(2);
			url.setText(builder.url != null ? builder.url : builder.image);
			headerText.addView(url);
		}

		var divider = new MaterialDivider(context);
		addView(divider);
		ViewUtil.setBottomMargin(divider, Formats.NORMAL_PADDING);

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
			title.setPadding(Formats.NORMAL_PADDING, 0, Formats.NORMAL_PADDING, 0);

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
		protected boolean isBase64Image;
		private final List<Action> actions = new ArrayList<>();
		private String url, title, image;
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

		public Builder setImage(String image) {
			if(image != null && image.startsWith(Formats.BASE64_IMAGE_PREFIX)) {
				isBase64Image = true;
				image = FormatUtil.removeBase64ImagePrefix(image);
			}

			this.image = image;
			return this;
		}

		public Builder setLink(String link) {
			this.url = link;
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