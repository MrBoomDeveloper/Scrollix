package com.mrboomdev.scrollix.ui.layout;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;
import com.mrboomdev.scrollix.util.LinkUtil;

import java.util.Objects;

public class SearchLayout extends LinearLayout implements TextView.OnEditorActionListener {
	private final EditText editText;
	private LaunchLinkListener listener;
	private String url;
	private Animation animation;
	private boolean isOpened;

	public SearchLayout(Context context) {
		super(context);
		setOrientation(VERTICAL);

		var inputBar = new LinearLayout(context);
		inputBar.setOrientation(HORIZONTAL);
		inputBar.setGravity(Gravity.CENTER_VERTICAL);
		inputBar.setPadding(12, 0, 12, 0);
		addView(inputBar, ViewGroup.LayoutParams.MATCH_PARENT, FormatUtil.getDip(50));

		int iconSize = FormatUtil.getDip(34);

		ImageView engineIcon = new ImageView(context);
		engineIcon.setImageDrawable(AppManager.settings.searchEngine.getEngine().getIcon());
		engineIcon.setClickable(true);
		engineIcon.setPadding(10, 10, 10, 10);
		engineIcon.setBackgroundResource(R.drawable.ripple_circle);
		engineIcon.setFocusable(true);
		inputBar.addView(engineIcon, iconSize, iconSize);

		editText = new EditText(context);
		editText.setBackgroundColor(getResources().getColor(android.R.color.transparent, context.getTheme()));
		editText.setSelectAllOnFocus(true);
		editText.setSingleLine();
		editText.setTextSize(14);
		editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_FULLSCREEN);
		editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		inputBar.addView(editText, 0, ViewGroup.LayoutParams.MATCH_PARENT);

		var editTextParams = (LayoutParams)editText.getLayoutParams();
		editTextParams.weight = 1;
		editTextParams.leftMargin = 16;
		editText.setLayoutParams(editTextParams);
		editText.setOnEditorActionListener(this);

		var removeIcon = FileUtil.getDrawable(R.drawable.ic_close_black);
		FileUtil.setDrawableColor(removeIcon, Color.parseColor("#ccccdd"));

		var removeButton = new ImageView(context);
		removeButton.setPadding(6, 6, 6, 6);
		removeButton.setImageDrawable(removeIcon);
		removeButton.setBackgroundResource(R.drawable.ripple_circle);
		removeButton.setOnClickListener(view -> editText.setText(""));
		removeButton.setClickable(true);
		removeButton.setFocusable(true);
		inputBar.addView(removeButton, iconSize, iconSize);

		setOnClickListener(view -> hide());
	}

	public void setTheme(@NonNull ThemeSettings theme) {
		setBackgroundColor(Color.parseColor(theme.bars));
		editText.setTextColor(Color.parseColor(theme.barsOverlay));
	}

	private void startAnimation() {
		if(animation != null) animation.cancel();

		animation = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				setAlpha(isOpened ? interpolatedTime : (1 - interpolatedTime));

				if(interpolatedTime == 1 && !isOpened) setVisibility(GONE, false);
			}
		};

		animation.setInterpolator(new AccelerateDecelerateInterpolator());
		animation.setDuration(100);
		startAnimation(animation);
	}

	public void setLaunchLinkListener(LaunchLinkListener listener) {
		this.listener = listener;
	}

	public void show() {
		setVisibility(VISIBLE, false);

		var url = AppManager.settings.urlFormatRules.parseSearchQuery ?
				SearchEngine.parseQueryAll(this.url)
				: this.url;

		editText.setText(this.url.equals(url) ? LinkUtil.formatInputUrl(url) : url);

		isOpened = true;
		startAnimation();

		boolean hadFocus = editText.hasFocus();
		editText.requestFocus();

		if(!hadFocus) {
			var inputManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
		}
	}

	public void hide() {
		isOpened = false;
		startAnimation();

		if(editText.hasFocus()) {
			var inputManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
		}
	}

	public boolean isOpened() {
		return isOpened;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Override
	public void setVisibility(int visibility) {
		setVisibility(visibility, true);
	}

	public void setVisibility(int visibility, boolean promptMe) {
		super.setVisibility(visibility);

		if(promptMe) {
			switch(visibility) {
				case VISIBLE -> show();
				case GONE, INVISIBLE -> hide();
			}
		}
	}

	@Override
	public boolean onEditorAction(TextView v, int action, KeyEvent event) {
		if(action != EditorInfo.IME_ACTION_SEARCH) return false;

		if(listener != null) {
			var engine = AppManager.settings.searchEngine.getEngine();
			var query = editText.getText().toString();

			if(LinkUtil.isUrlValid(query)) {
				listener.launch(LinkUtil.resolveInputUrl(query));
			} else {
				var fixed = LinkUtil.tryToFixUrl(query);
				listener.launch(Objects.requireNonNullElse(fixed, engine.getSearchUrl(query)));
			}
		}

		hide();
		return true;
	}

	public interface LaunchLinkListener {
		void launch(String url);
	}
}