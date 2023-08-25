package com.mrboomdev.scrollix.ui.widgets;

import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

public class SearchBarWidget extends LinearLayout {
	private OnEnterListener enterListener;
	private final EditText editText;
	private boolean isEditorOpened;
	private String url, title;

	public SearchBarWidget(Context context) {
		super(context);
		setClickable(true);

		var params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
		params.weight = 1;
		setLayoutParams(params);

		editText = new EditText(context);
		editText.setSingleLine();
		editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		editText.setSelectAllOnFocus(true);
		editText.setBackgroundResource(android.R.color.transparent);

		var textParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
		textParams.weight = 1;
		editText.setLayoutParams(textParams);

		editText.setOnEditorActionListener((view, action, event) -> {
			if(action != EditorInfo.IME_ACTION_SEARCH) return false;

			if(enterListener != null) enterListener.entered(editText.getText().toString());

			toggleEditorVisibility(false);
			return true;
		});

		editText.setOnFocusChangeListener((view, isFocused) -> {
			toggleEditorVisibility(isFocused);

			var inputManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
		});

		addView(editText);

		toggleEditorVisibility(false);
	}

	public void setOnEnterListener(OnEnterListener listener) {
		this.enterListener = listener;
	}

	public void toggleEditorVisibility(boolean isVisible) {
		isEditorOpened = isVisible;

		if(isVisible) {
			editText.setText(url);
			editText.selectAll();
			editText.requestFocus();
		} else {
			editText.setText(title);
		}
	}

	public void setTitle(String title) {
		this.title = title;

		if(!isEditorOpened) editText.setText(title);
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public interface OnEnterListener {
		void entered(String request);
	}
}