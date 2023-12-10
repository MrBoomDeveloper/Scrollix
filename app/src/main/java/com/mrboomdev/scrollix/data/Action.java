package com.mrboomdev.scrollix.data;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.ui.AppUi;
import com.mrboomdev.scrollix.ui.popup.ActionsMenu;
import com.mrboomdev.scrollix.ui.popup.TabsMenu;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.callback.ValueReturner1;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;
import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;

import org.jetbrains.annotations.Contract;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public abstract class Action {
	public static final String SCROLLIX_UI_PREFIX = "scrollix-ui://";
	private final String title;

	private static final Preset UNKNOWN_PRESET = new Preset("Unknown", new IconAction(
			R.drawable.ic_close_black, view ->
			AppUtils.toast("Unknown action!", false)));

	public Action(String title) {
		this.title = title;
	}

	public abstract View.OnClickListener getClickCallback();

	public abstract View getView(Context context);

	public String getTitle() {
		return title;
	}

	@NonNull
	@Contract("_ -> new")
	public static Action fromValue(String value) {
		try {
			return fromValue(new JSONObject(value));
		} catch(JSONException ignored) {}

		for(var preset : presets.entrySet()) {
			if(value.equals(preset.getKey())) {
				return fromValue(preset.getValue());
			}
		}

		return fromValue(UNKNOWN_PRESET);
	}

	public static CustomAction fromValue(JSONObject json) throws JSONException {
		throw new JSONException("Stub!");
	}

	@NonNull
	@Contract("_ -> new")
	public static ActionFromPreset fromValue(@NonNull Preset preset) {
		if(preset.valueReturner == null) {
			return new ActionFromPreset(UNKNOWN_PRESET);
		}

		return new ActionFromPreset(preset);
	}

	public static class Adapter {

		@ToJson
		public String toJson(@NonNull Action action) {
			if(action instanceof ActionFromPreset presetAction) {
				for(var preset : presets.entrySet()) {
					if(preset.getValue() != presetAction.getPreset()) continue;
					return preset.getKey();
				}
			}

			return "unknown";
		}

		@FromJson
		public Action fromJson(String string) {
			return fromValue(string);
		}
	}

	private static class CustomAction extends Action {
		private String url, icon;

		public CustomAction(String title) {
			super(title);
		}

		@Nullable
		@Contract(pure = true)
		@Override
		public View.OnClickListener getClickCallback() {
			return null;
		}

		public CustomAction setUrl(String url) {
			this.url = url;
			return this;
		}

		public CustomAction setIcon(String icon) {
			this.icon = icon;
			return this;
		}

		public String getUrl() {
			return url;
		}

		public String getIcon() {
			return icon;
		}

		@Nullable
		@Contract(pure = true)
		@Override
		public View getView(Context context) {
			throw new UnexpectedBehaviourException("Stub!");
		}
	}

	private static class ActionFromPreset extends Action {
		private final Preset preset;

		public ActionFromPreset(@NonNull Preset preset) {
			super(preset.title());
			this.preset = preset;
		}

		public Preset getPreset() {
			return preset;
		}

		@Nullable
		@Override
		public View.OnClickListener getClickCallback() {
			if(preset.valueReturner instanceof IconAction iconAction) {
				return iconAction.getClickCallback();
			}

			return null;
		}

		@Override
		public View getView(Context context) {
			return preset.getView(context);
		}
	}

	public static void styleAction(@NonNull View view, int padding) {
		var buttonRipple = DrawableUtil.getDrawable(
				AppUtils.isLandscape() ? R.drawable.ripple_circle : R.drawable.ripple_square);

		view.setBackground(buttonRipple);
		view.setClickable(true);
		view.setFocusable(true);
		FormatUtil.setPadding(view, padding);

		if(view instanceof ViewGroup viewGroup) {
			for(int i = 0; i < viewGroup.getChildCount(); i++) {
				var child = viewGroup.getChildAt(i);
				child.setOnClickListener(null);
				child.setClickable(false);
				child.setFocusable(false);
			}
		}
	}

	private static final Map<String, Preset> presets = new HashMap<>() {{
		put("home", new Preset("Home", new UrlIconAction(R.drawable.ic_home_black,
				SCROLLIX_UI_PREFIX + "pages/home.html")));

		put("history", new Preset("History", new UrlIconAction(R.drawable.ic_history_black,
				SCROLLIX_UI_PREFIX + "pages/list.html")));

		put("bookmarks", new Preset("Bookmarks", new UrlIconAction(R.drawable.ic_star_black,
				SCROLLIX_UI_PREFIX + "pages/list.html")));

		put("downloads", new Preset("Downloads", new UrlIconAction(R.drawable.ic_download_black,
				SCROLLIX_UI_PREFIX + "pages/list.html")));

		put("extensions", new Preset("Extensions", new UrlIconAction(R.drawable.ic_extension_black,
				SCROLLIX_UI_PREFIX + "pages/list.html", view -> {
					view.setScaleX(.9f);
					view.setScaleY(.9f);
					return view;
				})));

		put("settings", new Preset("Settings", new UrlIconAction(R.drawable.ic_settings_black,
				SCROLLIX_UI_PREFIX + "pages/settings.html")));

		put("back", new Preset("Back", new IconAction(R.drawable.ic_back_black,
				view -> TabManager.getCurrentTab().goBack(),
				view -> {
					AppUi.backButton = view;

					int padding = Math.round(view.getPaddingTop() * 1.2f);
					view.setPadding(padding, padding, padding, padding);
					return view;
				})));

		put("next", new Preset("Next", new IconAction(R.drawable.ic_back_black,
				view -> TabManager.getCurrentTab().goForward(),
				view -> {
					AppUi.forwardButton = view;

					int padding = Math.round(view.getPaddingTop() * 1.2f);
					view.setPadding(padding, padding, padding, padding);
					view.setScaleX(-1);
					return view;
				})));

		put("menu", new Preset("Menu", new IconAction(R.drawable.ic_menu_black,
				view -> new ActionsMenu(AppManager.getActivityContext()).showAt(view))));

		put("tabs", new Preset("Tabs", new IconAction(R.drawable.ic_tabs_black, null, view -> {
			var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();
			var parent = new FrameLayout(view.getContext());
			parent.setOnClickListener(_view -> new TabsMenu(AppManager.getActivityContext()).showAt(_view));

			view.setScaleX(1.1f);
			view.setScaleY(1.1f);
			parent.addView(view);

			var tabsCounter = new TextView(view.getContext());
			tabsCounter.setText(String.valueOf(TabStore.getTabCount()));
			tabsCounter.setTextColor(Color.parseColor(theme.barsOverlay));
			tabsCounter.setTextSize(Formats.SMALL_TEXT);
			tabsCounter.setGravity(Gravity.CENTER);
			AppUi.tabsCounter = tabsCounter;

			parent.addView(tabsCounter, Formats.MATCH_PARENT, Formats.MATCH_PARENT);
			return parent;
		})));
	}};

	private static class UrlIconAction extends IconAction {

		public UrlIconAction(int drawable, @NonNull final String url, ValueReturner1<View, View> styleCallback) {
			super(drawable, null, styleCallback);

			setClickCallback(url.startsWith(SCROLLIX_UI_PREFIX) ? view -> {
				var uri = url.substring(SCROLLIX_UI_PREFIX.length());

				ExtensionManager.getExtensionPageUrl(ExtensionManager.UI_EXTENSION_ID, uri, newUri -> {
					var tab = TabManager.getCurrentTab();
					tab.loadUrl(newUri);
				});
			} : view -> {
				var tab = TabManager.getCurrentTab();
				tab.loadUrl(url);
			});
		}

		public UrlIconAction(int drawable, @NonNull final String url) {
			this(drawable, url, null);
		}
	}

	private static class IconAction implements ValueReturner1<View, Context> {
		private final int drawable;
		private final ValueReturner1<View, View> styleCallback;
		private View.OnClickListener clickCallback;

		public IconAction(@DrawableRes int drawable, View.OnClickListener clickCallback, ValueReturner1<View, View> styleCallback) {
			this.drawable = drawable;
			this.clickCallback = clickCallback;
			this.styleCallback = styleCallback;
		}

		public IconAction(@DrawableRes int drawable, View.OnClickListener clickCallback) {
			this(drawable, clickCallback, null);
		}

		public void setClickCallback(View.OnClickListener clickCallback) {
			this.clickCallback = clickCallback;
		}

		public View.OnClickListener getClickCallback() {
			return clickCallback;
		}

		@Nullable
		@Contract(pure = true)
		@Override
		public View get(Context context) {
			var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

			int primaryColor = Color.parseColor(theme.barsOverlay);
			var button = new ImageView(context);

			button.setOnClickListener(view -> {
				if(clickCallback == null) return;
				clickCallback.onClick(view);
			});

			button.setScaleType(ImageView.ScaleType.FIT_CENTER);

			var buttonIcon = DrawableUtil.getDrawable(drawable, primaryColor);
			button.setImageDrawable(buttonIcon);

			if(styleCallback != null) {
				return styleCallback.get(button);
			}

			return button;
		}
	}

	private record Preset(String title, ValueReturner1<View, Context> valueReturner) {

		public View getView(Context context) {
			return valueReturner.get(context);
		}
	}
}