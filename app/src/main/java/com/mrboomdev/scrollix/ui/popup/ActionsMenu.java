package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.Action;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.ui.AppUi;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

public class ActionsMenu {
	private final LinearLayout actionsMenuView;
	private final DrawableBuilder background;
	private final Context context;
	private PopupWindow popup;
	private BottomSheetDialog bottomSheet;

	public ActionsMenu(Context context) {
		this.context = context;
		AppUi.actionsMenu = this;
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		background = new DrawableBuilder.ColorDrawableBuilder()
				.setColor(theme.popupBackground);

		actionsMenuView = new LinearLayout(context);
		actionsMenuView.setOrientation(LinearLayout.VERTICAL);
		actionsMenuView.setBackground(background.build());

		var grid = new RecyclerView(context);
		grid.setLayoutManager(new GridLayoutManager(context, 5));
		grid.setAdapter(new Adapter());
		FormatUtil.setPadding(grid, Formats.NORMAL_PADDING);
		actionsMenuView.addView(grid, Formats.MATCH_PARENT, Formats.MATCH_PARENT);

		if(!AppUtils.isLandscape()) {
			var additional = new LinearLayout(context);
			additional.setOrientation(LinearLayout.HORIZONTAL);

			for(var item : AppManager.settings.sideActions) {
				var view = item.getView(context);
				additional.addView(view, Formats.BIG_ELEMENT, Formats.BIG_ELEMENT);
			}

			actionsMenuView.addView(additional);
		}
	}

	public void close() {
		if(popup != null) popup.dismiss();
		if(bottomSheet != null) bottomSheet.dismiss();

		popup = null;
		bottomSheet = null;
	}

	public void showAt(View view) {
		boolean isLandscape = AppUtils.isLandscape();

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

		@NonNull
		@Override
		public ActionView onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new ActionView(new LinearLayout(context));
		}

		@Override
		public void onBindViewHolder(@NonNull ActionView holder, int position) {
			holder.setAction(AppManager.settings.menuActions.get(position));
		}

		@Override
		public int getItemCount() {
			return AppManager.settings.menuActions.size();
		}
	}

	private class ActionView extends RecyclerView.ViewHolder {
		private final LinearLayout parent;
		private final TextView title;
		private View view;

		public ActionView(@NonNull LinearLayout parent) {
			super(parent);
			this.parent = parent;

			parent.setGravity(Gravity.CENTER_HORIZONTAL);
			parent.setOrientation(LinearLayout.VERTICAL);
			parent.setClickable(true);
			parent.setFocusable(true);

			title = new TextView(context);
			title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
			title.setTextSize(Formats.TINY_TEXT);
			parent.addView(title);
		}

		public void setAction(@NonNull Action action) {
			title.setText(action.getTitle());

			if(view != null) {
				parent.removeView(view);
			}

			view = action.getView(context);
			FormatUtil.setPadding(view, Formats.NORMAL_PADDING);
			parent.addView(view, 0);

			View.OnClickListener clickListener = _view -> {
				var callback = action.getClickCallback();

				if(callback != null) {
					callback.onClick(_view);
				}

				close();
			};

			parent.setOnClickListener(clickListener);
			Action.styleAction(parent, Formats.NORMAL_PADDING);
		}
	}
}