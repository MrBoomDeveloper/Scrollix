package com.mrboomdev.scrollix.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;

import java.util.Objects;

public class SearchBarWidget extends LinearLayout {
	private final Drawable securityIconImage;
	private final ImageView refreshButton, securityIcon;
	private OnClickListener clickListener;
	private boolean isLoading;
	private final int primaryColor;
	private final TextView titleView;

	public SearchBarWidget(Context context, WebView webview) {
		super(context);

		var params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
		params.weight = 1;
		setLayoutParams(params);

		primaryColor = Color.parseColor("#ccccdd");

		var circleRipple = ResourcesCompat.getDrawable(
				getResources(),
				R.drawable.ripple_circle,
				context.getTheme());

		var styledHolder = new LinearLayout(context);
		styledHolder.setOrientation(LinearLayout.HORIZONTAL);
		styledHolder.setGravity(Gravity.CENTER_VERTICAL);
		styledHolder.setBackgroundResource(R.drawable.search_input_background);
		styledHolder.setPadding(10, 0, 10, 0);
		addView(styledHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		((LinearLayout.LayoutParams)styledHolder.getLayoutParams()).setMargins(8, 8, 8, 8);

		styledHolder.setClickable(true);
		styledHolder.setFocusable(true);

		styledHolder.setForeground(ResourcesCompat.getDrawable(
				getResources(),
				R.drawable.ripple_search,
				context.getTheme()));

		styledHolder.setOnClickListener(view -> {
			if(clickListener != null) {
				clickListener.onClick(this);
			}
		});

		int size = (int)FormatUtil.getResponsiveValue(34, FormatUtil.Dimension.DIP, context);

		securityIconImage = FileUtil.getDrawable(R.drawable.ic_lock_black, context);
		FileUtil.setDrawableColor(securityIconImage, primaryColor);

		securityIcon = new ImageView(context);
		securityIcon.setImageDrawable(securityIconImage);
		securityIcon.setPadding(11, 11, 11, 11);
		securityIcon.setBackground(circleRipple);
		securityIcon.setClickable(true);
		securityIcon.setFocusable(true);
		styledHolder.addView(securityIcon, size, size);
		((LayoutParams)securityIcon.getLayoutParams()).rightMargin = 10;

		securityIcon.setOnClickListener(view -> {
			Toast.makeText(context, "Not available currently!", Toast.LENGTH_SHORT).show();
		});

		titleView = new TextView(context);
		titleView.setTextSize(14);
		titleView.setTextColor(primaryColor);
		titleView.setSingleLine(true);
		titleView.setText("Search anything...");
		styledHolder.addView(titleView);
		((LayoutParams)titleView.getLayoutParams()).weight = 1;

		refreshButton = new ImageView(context);
		refreshButton.setBackground(Objects.requireNonNull(Objects.requireNonNull(circleRipple)
						.getConstantState()).newDrawable().mutate());

		refreshButton.setClickable(true);
		refreshButton.setFocusable(true);
		refreshButton.setPadding(10, 10, 10, 10);
		styledHolder.addView(refreshButton, size, size);
		((LayoutParams)refreshButton.getLayoutParams()).setMargins(20, 0, 0, 0);

		refreshButton.setOnClickListener(view -> {
			if(isLoading)
				webview.stopLoading();
			else
				webview.reload();
		});
		setIsLoading(false);
	}

	public void setFavicon(Bitmap favicon) {
		if(favicon == null) {
			securityIcon.setImageDrawable(securityIconImage);
			return;
		}

		securityIcon.setImageBitmap(favicon);
	}

	public void setTitle(String title) {
		titleView.setText(title);
	}

	public void setIsLoading(boolean isLoading) {
		this.isLoading = isLoading;

		int icon = isLoading ? R.drawable.ic_close_black : R.drawable.ic_refresh_black;
		var currentRefreshIcon = ResourcesCompat.getDrawable(getResources(), icon, getContext().getTheme());
		DrawableCompat.setTint(Objects.requireNonNull(currentRefreshIcon), primaryColor);

		refreshButton.setImageDrawable(currentRefreshIcon);
	}

	@Override
	public void setOnClickListener(@Nullable OnClickListener listener) {
		this.clickListener = listener;
	}
}