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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.callback.ViewUtil;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class SearchBarWidget extends LinearLayout {
	private final Drawable securityIconImage;
	private final ImageView refreshButton, securityIcon;
	private OnClickListener clickListener;
	private boolean isLoading;
	private final int primaryColor;
	private final TextView titleView;
	private String wasUrl;

	public SearchBarWidget(Context context, @NonNull ThemeSettings theme) {
		super(context);

		var params = new LinearLayout.LayoutParams(0, Formats.WRAP_CONTENT);
		params.weight = 1;
		setLayoutParams(params);

		primaryColor = Color.parseColor(theme.barsOverlay);
		var circleRipple = DrawableUtil.getDrawable(R.drawable.ripple_circle);

		var styledHolder = new LinearLayout(context);
		styledHolder.setOrientation(LinearLayout.HORIZONTAL);
		styledHolder.setGravity(Gravity.CENTER_VERTICAL);

		styledHolder.setBackground(DrawableUtil.getDrawable(
				R.drawable.search_input_background, theme.barsInner));

		boolean isLandscape = AppUtils.isLandscape();
		var holderPadding = FormatUtil.getDip(isLandscape ? 10 : 8, isLandscape ? 2 : 4);
		var holderMargin = FormatUtil.getDip(isLandscape ? 8 : 10, isLandscape ? 6 : 8);

		addView(styledHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		ViewUtil.setMargin(styledHolder, holderMargin[0], holderMargin[1]);
		ViewUtil.setPadding(styledHolder, holderPadding[0], holderPadding[1]);

		styledHolder.setClickable(true);
		styledHolder.setFocusable(true);
		styledHolder.setForeground(DrawableUtil.getDrawable(R.drawable.ripple_search));

		styledHolder.setOnClickListener(view -> {
			if(clickListener != null) {
				clickListener.onClick(this);
			}
		});

		int size = FormatUtil.getDip(34);

		securityIconImage = DrawableUtil.getDrawable(R.drawable.ic_lock_black, primaryColor);
		securityIcon = new ImageView(context);
		securityIcon.setImageDrawable(securityIconImage);
		securityIcon.setBackground(circleRipple);
		securityIcon.setClickable(true);
		securityIcon.setFocusable(true);
		styledHolder.addView(securityIcon, size, size);
		ViewUtil.setRightMargin(securityIcon, 10);
		ViewUtil.setPadding(securityIcon, 11);

		securityIcon.setOnClickListener(view ->
				AppUtils.toast("Not available currently!", false));

		titleView = new TextView(context);
		titleView.setTextSize(Formats.SMALL_TEXT);
		titleView.setTextColor(primaryColor);
		titleView.setSingleLine(true);
		titleView.setText(R.string.comment_search_something);
		styledHolder.addView(titleView);
		ViewUtil.setWeight(titleView, 1);

		refreshButton = new ImageView(context);
		refreshButton.setBackground(DrawableUtil.copyDrawable(circleRipple));

		refreshButton.setClickable(true);
		refreshButton.setFocusable(true);
		styledHolder.addView(refreshButton, size, size);
		ViewUtil.setRightMargin(refreshButton, 20);
		ViewUtil.setPadding(refreshButton, 10);

		refreshButton.setOnClickListener(view -> {
			var tab = TabManager.getCurrentTab();
			if(isLoading) tab.stopLoading(); else tab.reload();
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
		if(Objects.equals(url, wasUrl)) return;
		wasUrl = url;

		var uiExtensionUrl = ExtensionManager.getUiExtensionBaseUrl();

		if(url == null || (uiExtensionUrl != null && url.startsWith(uiExtensionUrl))) {
			titleView.setText(R.string.comment_search_something);
			return;
		}

		titleView.setText(SearchEngine.parseQueryAll(url));
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