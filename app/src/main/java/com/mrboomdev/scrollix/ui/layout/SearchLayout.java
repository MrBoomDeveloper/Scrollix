package com.mrboomdev.scrollix.ui.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.search.SearchEngine;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.LinkUtil;
import com.mrboomdev.scrollix.util.callback.CallbackController;
import com.mrboomdev.scrollix.util.callback.ViewUtil;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.List;
import java.util.Objects;

public class SearchLayout extends LinearLayout implements TextView.OnEditorActionListener, TextWatcher {
	public final EditText editText;
	private static final List<SearchEngine.SearchSuggestion> EMPTY_SUGGESTIONS_LIST = List.of();
	private final SuggestionsAdapter adapter;
	private CallbackController searchQueriesCallbackController;
	private LaunchLinkListener listener;
	private String url = "";
	private Animation animation;
	private boolean isOpened;

	public SearchLayout(Context context) {
		super(context);
		setOrientation(VERTICAL);

		var inputBar = new LinearLayout(context);
		inputBar.setOrientation(HORIZONTAL);
		inputBar.setGravity(Gravity.CENTER_VERTICAL);
		//inputBar.setPadding(Formats.NORMAL_PADDING, Formats.NORMAL_PADDING, Formats.NORMAL_PADDING, Formats.NORMAL_PADDING);
		FormatUtil.setPadding(inputBar, Formats.NORMAL_PADDING);
		addView(inputBar, Formats.MATCH_PARENT, Formats.WRAP_CONTENT);

		ImageView engineIcon = new ImageView(context);
		engineIcon.setImageDrawable(AppManager.settings.searchEngine.getEngine().getIcon());
		engineIcon.setClickable(true);
		FormatUtil.setPadding(engineIcon, Formats.PADDING);
		engineIcon.setBackgroundResource(R.drawable.ripple_circle);
		engineIcon.setFocusable(true);
		inputBar.addView(engineIcon, Formats.NORMAL_ELEMENT, Formats.NORMAL_ELEMENT);

		editText = new EditText(context);
		editText.setBackgroundResource(android.R.color.transparent);
		editText.setSelectAllOnFocus(true);
		editText.setSingleLine();
		editText.setTextColor(Color.WHITE);
		editText.setTextSize(Formats.NORMAL_TEXT);
		editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_FULLSCREEN);
		editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		inputBar.addView(editText, 0, ViewGroup.LayoutParams.MATCH_PARENT);

		ViewUtil.setWeight(editText, 1);
		ViewUtil.setLeftMargin(editText, Formats.BIG_PADDING);
		editText.setOnEditorActionListener(this);
		editText.addTextChangedListener(this);

		var removeIcon = DrawableUtil.getDrawable(R.drawable.ic_close_black, "#ccccdd");

		var removeButton = new ImageView(context);
		FormatUtil.setPadding(removeButton, Formats.PADDING);
		removeButton.setImageDrawable(removeIcon);
		removeButton.setBackgroundResource(R.drawable.ripple_circle);
		removeButton.setOnClickListener(view -> editText.setText(""));
		removeButton.setClickable(true);
		removeButton.setFocusable(true);
		inputBar.addView(removeButton, Formats.NORMAL_ELEMENT, Formats.NORMAL_ELEMENT);

		setOnClickListener(view -> hide());

		var recycler = new RecyclerView(context);
		recycler.setLayoutManager(new LinearLayoutManager(context));
		recycler.setAdapter(adapter = new SuggestionsAdapter());
		addView(recycler);
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

		var url = SearchEngine.parseQueryAll(this.url);
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
		var uiExtensionUrl = ExtensionManager.getUiExtensionBaseUrl();

		if(url == null || (uiExtensionUrl != null && url.startsWith(uiExtensionUrl))) {
			this.url = "";
			return;
		}

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

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {}

	@Override
	public void afterTextChanged(@NonNull Editable s) {
		var query = s.toString();

		if(this.searchQueriesCallbackController != null) {
			this.searchQueriesCallbackController.cancel();
		}

		if(query.isBlank()) {
			adapter.setData(EMPTY_SUGGESTIONS_LIST);
			return;
		}

		var searchEngine = SearchEngine.Preset.GOOGLE.getEngine();
		searchQueriesCallbackController = searchEngine.getSearchResults(query, results ->
				AppUtils.runOnUiThread(() -> adapter.setData(results)));
	}

	private class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionView> {
		private List<SearchEngine.SearchSuggestion> data;

		@NonNull
		@Override
		public SuggestionView onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			var linear = new LinearLayout(getContext());
			linear.setLayoutParams(new RecyclerView.LayoutParams(Formats.MATCH_PARENT, Formats.WRAP_CONTENT));

			return new SuggestionView(linear);
		}

		@Override
		public void onBindViewHolder(@NonNull SuggestionView holder, int position) {
			holder.setData(data.get(position));
		}

		@SuppressLint("NotifyDataSetChanged")
		public void setData(List<SearchEngine.SearchSuggestion> data) {
			this.data = data;
			notifyDataSetChanged();
		}

		@Override
		public int getItemCount() {
			return data != null ? data.size() : 0;
		}
	}

	private class SuggestionView extends RecyclerView.ViewHolder {
		private final TextView title;
		private SearchEngine.SearchSuggestion suggestion;

		public SuggestionView(@NonNull LinearLayout itemView) {
			super(itemView);
			itemView.setGravity(Gravity.CENTER_VERTICAL);
			itemView.setAlpha(.75f);
			//var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

			itemView.setOrientation(HORIZONTAL);
			itemView.setClickable(true);
			itemView.setFocusable(true);
			itemView.setBackgroundResource(R.drawable.ripple_square);

			itemView.setPadding(
					Formats.NORMAL_PADDING,
					Formats.LARGE_PADDING - Formats.SMALL_PADDING,
					Formats.BIG_PADDING,
					Formats.LARGE_PADDING - Formats.SMALL_PADDING);

			itemView.setOnClickListener(view -> {
				if(listener == null) return;

				var engine = SearchEngine.Preset.GOOGLE.getEngine();
				var url = engine.getSearchUrl(suggestion.title());

				listener.launch(url);
				hide();
			});

			var icon = new ImageView(getContext());
			icon.setImageDrawable(DrawableUtil.getDrawable(R.drawable.ic_search_black, "#ffffff"));
			icon.setScaleX(1.1f);
			icon.setScaleY(1.1f);
			icon.setPadding(Formats.SMALL_PADDING / 2, Formats.SMALL_PADDING / 2, Formats.SMALL_PADDING, 0);
			itemView.addView(icon, Formats.NORMAL_ELEMENT, Formats.SMALL_ELEMENT);
			((LayoutParams)icon.getLayoutParams()).rightMargin = Formats.BIG_PADDING;

			title = new TextView(getContext());
			title.setTextSize(Formats.NORMAL_TEXT);
			title.setTextColor(Color.WHITE);
			itemView.addView(title);
		}

		public void setData(@NonNull SearchEngine.SearchSuggestion suggestion) {
			title.setText(suggestion.title());
			this.suggestion = suggestion;
		}
	}

	public interface LaunchLinkListener {
		void launch(String url);
	}
}