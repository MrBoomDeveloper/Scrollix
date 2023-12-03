package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.EngineInternal;
import com.mrboomdev.scrollix.engine.extenison.ExtensionManager;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import org.jetbrains.annotations.Contract;

import java.util.List;

public class ActionsMenu {
	private final LinearLayout actionsMenuView;
	private final DrawableBuilder background;
	private final Context context;
	private PopupWindow popup;
	private BottomSheetDialog bottomSheet;

	public ActionsMenu(Context context) {
		this.context = context;
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		background = new DrawableBuilder.ColorDrawableBuilder()
				.setColor(theme.popupBackground);

		actionsMenuView = new LinearLayout(context);
		actionsMenuView.setOrientation(LinearLayout.VERTICAL);
		actionsMenuView.setBackground(background.build());

		var actions = List.of(
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page"),
				new Action(R.drawable.ic_star_black, "Bookmark page")
		);

		var grid = new RecyclerView(context);
		grid.setLayoutManager(new GridLayoutManager(context, 5));
		grid.setAdapter(new Adapter(actions));
		FormatUtil.setPadding(grid, Formats.NORMAL_PADDING);
		actionsMenuView.addView(grid, Formats.MATCH_PARENT, Formats.MATCH_PARENT);

		if(!AppManager.isLandscape()) {
			var additional = new LinearLayout(context);
			additional.setOrientation(LinearLayout.HORIZONTAL);

			for(var item : AppManager.settings.menuActions) {
				var view = createActionButton(item, theme);
				additional.addView(view, Formats.BIG_ELEMENT, Formats.BIG_ELEMENT);
			}

			actionsMenuView.addView(additional);
		}
	}

	@Contract("_, _, _ -> param2")
	private int setUrlAction(@NonNull ImageView button, @DrawableRes int icon, EngineInternal.Link link) {
		button.setOnClickListener(view -> ExtensionManager.getExtensionPageUrl(ExtensionManager.UI_EXTENSION_ID, link.getRealUrl(), url -> {
			var tab = TabManager.getCurrentTab();
			tab.loadUrl(url);
			close();
		}));

		return icon;
	}

	@NonNull
	private View createActionButton(@NonNull String name, @NonNull ThemeSettings theme) {
		int icon = R.drawable.ic_close_black, primaryColor = Color.parseColor(theme.barsOverlay);
		var button = new ImageView(context);

		var buttonRipple = DrawableUtil.getDrawable(R.drawable.ripple_circle);

		button.setOnClickListener(view -> {
			String message = "Unknown action, please check your settings!";
			Toast.makeText(context, message, Toast.LENGTH_LONG).show();
		});

		FormatUtil.setPadding(button, Formats.BIG_PADDING);

		switch(name) {
			case "home" -> icon = setUrlAction(button, R.drawable.ic_home_black, EngineInternal.Link.HOME);
			case "settings" -> icon = setUrlAction(button, R.drawable.ic_settings_black, EngineInternal.Link.SETTINGS);
			case "downloads" -> icon = setUrlAction(button, R.drawable.ic_download_black, EngineInternal.Link.DOWNLOADS);
			case "history" -> icon = setUrlAction(button, R.drawable.ic_history_black, EngineInternal.Link.HISTORY);
			case "bookmarks" -> icon = setUrlAction(button, R.drawable.ic_star_black, EngineInternal.Link.BOOKMARKS);
		}

		if(name.equals("next") || name.equals("back")) {
			int padding = Math.round(button.getPaddingTop() * 1.2f);
			button.setPadding(padding, padding, padding, padding);
		}

		button.setScaleType(ImageView.ScaleType.FIT_CENTER);
		button.setBackground(buttonRipple);
		button.setClickable(true);
		button.setFocusable(true);

		var buttonIcon = DrawableUtil.getDrawable(icon, primaryColor);
		button.setImageDrawable(buttonIcon);

		return button;
	}

	public void close() {
		if(popup != null) popup.dismiss();
		if(bottomSheet != null) bottomSheet.dismiss();

		popup = null;
		bottomSheet = null;
	}

	public void showAt(View view) {
		boolean isLandscape = AppManager.isLandscape();

		background.setCornerRadius(isLandscape ? Formats.SMALL_POPUP_RADIUS : 0);
		actionsMenuView.setBackground(background.build());

		if(isLandscape) {
			var sizes = FormatUtil.getDip(350, 200);
			popup = new PopupWindow(actionsMenuView, sizes[0], sizes[1]);
			popup.setFocusable(true);
			popup.showAsDropDown(view);
		} else {
			bottomSheet = new BottomSheetDialog(context);
			bottomSheet.setContentView(actionsMenuView);
			bottomSheet.show();
		}
	}

	private class Adapter extends RecyclerView.Adapter<ActionView> {
		private final List<Action> actions;

		public Adapter(List<Action> actions) {
			this.actions = actions;
		}

		@NonNull
		@Override
		public ActionView onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			var params = new RecyclerView.LayoutParams(Formats.MATCH_PARENT, Formats.WRAP_CONTENT);
			return new ActionView(new LinearLayout(context));
		}

		@Override
		public void onBindViewHolder(@NonNull ActionView holder, int position) {
			holder.setAction(actions.get(position));
		}

		@Override
		public int getItemCount() {
			return actions.size();
		}
	}

	private class ActionView extends RecyclerView.ViewHolder {
		private final View view;
		private final TextView title;
		private final ImageView icon;

		public ActionView(@NonNull LinearLayout view) {
			super(view);
			this.view = view;

			var background = new DrawableBuilder.RippleDrawableBuilder()
					.setColor("#55ffffff")
					.setShape(DrawableBuilder.Shape.OVAL)
					.build();

			view.setGravity(Gravity.CENTER_HORIZONTAL);
			view.setOrientation(LinearLayout.VERTICAL);
			view.setClickable(true);
			view.setFocusable(true);
			view.setBackground(background);
			FormatUtil.setPadding(view, Formats.NORMAL_PADDING);

			icon = new ImageView(context);
			view.addView(icon, Formats.SMALL_ELEMENT, Formats.SMALL_ELEMENT);

			title = new TextView(context);
			title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
			title.setTextSize(Formats.SMALL_TEXT);
			view.addView(title);
		}

		public void setAction(@NonNull Action action) {
			icon.setImageDrawable(DrawableUtil.getDrawable(action.icon, "#ffffff"));
			title.setText(action.name);

			view.setOnClickListener(_view -> {
				close();
			});
		}
	}

	public record Action(@DrawableRes int icon, String name) {}
}