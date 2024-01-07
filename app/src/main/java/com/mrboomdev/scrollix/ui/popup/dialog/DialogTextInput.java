package com.mrboomdev.scrollix.ui.popup.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.util.callback.Callback1;

public class DialogTextInput extends DialogElement implements TextView.OnEditorActionListener {
	private final String defaultValue;
	private String placeholder;
	private Callback1<String> doneCallback;
	private TextInputLayout inputLayout;
	private TextInputEditText editText;
	private DialogTextInput next;
	private boolean focusOnCreate;

	public DialogTextInput(String defaultValue, boolean focusOnCreate) {
		this.defaultValue = defaultValue;
		this.focusOnCreate = focusOnCreate;
	}

	public DialogTextInput(String defaultValue) {
		this(defaultValue, false);
	}

	public DialogTextInput() {
		this("");
	}

	public void setFocusOnShow(boolean focus) {
		this.focusOnCreate = focus;
	}

	public void setNext(@NonNull DialogTextInput input) {
		if(editText != null) {
			if(next != null) {
				editText.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_FULLSCREEN);
			} else {
				editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN);
			}
		}

		this.next = input;
	}

	public void setDoneListener(Callback1<String> callback) {
		this.doneCallback = callback;
	}

	public void focus() {
		if(Integer.parseInt("1") == 1) return;
		if(inputLayout == null) return;

		var context = AppManager.getActivityContext();
		var inputManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		inputManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
	}

	public void pressDone() {
		if(doneCallback == null) return;
		doneCallback.run("");
	}

	public void setPlaceholder(String placeholder) {
		if(inputLayout != null) {
			inputLayout.setHint(placeholder);

			if(!placeholder.isBlank()) {
				inputLayout.setHintEnabled(true);
			}
		}

		this.placeholder = placeholder;
	}

	public String getText() {
		if(editText == null) {
			return defaultValue;
		}

		var text = editText.getText();

		if(text != null) {
			return text.toString();
		}

		return defaultValue;
	}

	@Override
	@SuppressLint("InflateParams")
	public void createView(Context context) {
		var layoutInflater = AppManager.getActivityContext().getLayoutInflater();
		inputLayout = (TextInputLayout) layoutInflater.inflate(R.layout.edit_text, null);
		inputLayout.setBoxStrokeColor(Color.WHITE);
		inputLayout.setHintTextColor(ColorStateList.valueOf(Color.WHITE));

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			inputLayout.setCursorColor(ColorStateList.valueOf(Color.WHITE));
		}

		editText = inputLayout.findViewById(R.id.input);
		editText.setText(defaultValue);
		editText.setOnEditorActionListener(this);

		setNext(next);
		setPlaceholder(placeholder);
		setDoneListener(doneCallback);
	}

	@Override
	public View getView(Context context) {
		if(inputLayout == null) {
			createView(context);
		}

		if(focusOnCreate) {
			focus();
		}

		return inputLayout;
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		switch(actionId) {
			case EditorInfo.IME_ACTION_DONE -> {
				pressDone();
				return true;
			}

			case EditorInfo.IME_ACTION_NEXT -> {
				if(next != null) {
					next.focus();
					return true;
				}
			}
		}


		return false;
	}
}