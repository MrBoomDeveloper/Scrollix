package com.mrboomdev.scrollix.ui.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class SearchBarWidget extends LinearLayout {
	private final Drawable securityIconImage;
	private final ImageView refreshButton, securityIcon;
	private OnClickListener clickListener;
	private boolean isLoading;
	private final int primaryColor;
	private final TextView titleView;

	public SearchBarWidget(Context context, @NonNull ThemeSettings theme) {
		super(context);

		var params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.weight = 1;
		setLayoutParams(params);

		primaryColor = Color.parseColor(theme.barsOverlay);

		var circleRipple = ResourcesCompat.getDrawable(
				getResources(),
				R.drawable.ripple_circle,
				context.getTheme());

		var styledHolder = new LinearLayout(context);
		styledHolder.setOrientation(LinearLayout.HORIZONTAL);
		styledHolder.setGravity(Gravity.CENTER_VERTICAL);

		var background = FileUtil.getDrawable(R.drawable.search_input_background, theme.barsInner);
		styledHolder.setBackground(background);

		boolean isLandscape = AppManager.isLandscape();
		var styledHolderPadding = FormatUtil.getDip(isLandscape ? 10 : 8, isLandscape ? 2 : 4);
		var styledHolderMargin = FormatUtil.getDip(isLandscape ? 8 : 10, isLandscape ? 6 : 8);

		styledHolder.setPadding(styledHolderPadding[0], styledHolderPadding[1], styledHolderPadding[0], styledHolderPadding[1]);
		addView(styledHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		((LinearLayout.LayoutParams)styledHolder.getLayoutParams()).setMargins(styledHolderMargin[0], styledHolderMargin[1], styledHolderMargin[0], styledHolderMargin[1]);

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

		int size = FormatUtil.getDip(34);

		securityIconImage = FileUtil.getDrawable(R.drawable.ic_lock_black);
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
		refreshButton.setBackground(FileUtil.copyDrawable(circleRipple));

		refreshButton.setClickable(true);
		refreshButton.setFocusable(true);
		refreshButton.setPadding(10, 10, 10, 10);
		styledHolder.addView(refreshButton, size, size);
		((LayoutParams)refreshButton.getLayoutParams()).setMargins(20, 0, 0, 0);

		refreshButton.setOnClickListener(view -> {
			var webView = TabsManager.getCurrent().webView;
			if(isLoading) webView.stopLoading(); else webView.reload();
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

	public void setUrl(String url) {
		titleView.setText(AppManager.settings.urlFormatRules.parseSearchQuery ?
				SearchEngine.parseQueryAll(url)
				: url);
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