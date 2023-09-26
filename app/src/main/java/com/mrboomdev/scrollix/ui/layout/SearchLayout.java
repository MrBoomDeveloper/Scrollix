package com.mrboomdev.scrollix.ui.layout;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.data.AppSettings;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;

public class SearchLayout extends LinearLayout {
	private ImageView engineIcon;
	private EditText editText;
	private LaunchLinkListener listener;
	private String url;
	private Animation animation;
	private boolean isOpened;

	public SearchLayout(Context context, @NonNull AppSettings settings) {
		super(context);

		setOrientation(VERTICAL);
		setBackgroundColor(Color.parseColor("#222222"));

		var inputBar = new LinearLayout(context);
		inputBar.setOrientation(HORIZONTAL);
		inputBar.setGravity(Gravity.CENTER_VERTICAL);
		inputBar.setPadding(12, 0, 12, 0);
		addView(inputBar, ViewGroup.LayoutParams.MATCH_PARENT, (int)FormatUtil.getResponsiveValue(50, FormatUtil.Dimension.DIP, context));

		int iconSize = (int)FormatUtil.getResponsiveValue(34, FormatUtil.Dimension.DIP, context);

		engineIcon = new ImageView(context);
		engineIcon.setImageDrawable(settings.searchEngine.getEngine().getIcon(context));
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

		editText.setOnEditorActionListener((view, action, event) -> {
			if(action != EditorInfo.IME_ACTION_SEARCH) return false;

			if(listener != null) {
				var engine = settings.searchEngine.getEngine();
				var query = editText.getText().toString();
				listener.launch(FormatUtil.isUrlValid(query) ? query : engine.getSearchUrl(query));
			}

			hide();
			return true;
		});

		var removeIcon = FileUtil.getDrawable(R.drawable.ic_close_black, context);
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
		editText.setText(url);

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

		if(!isOpened) editText.setText(url);
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

	public interface LaunchLinkListener {
		void launch(String url);
	}
}