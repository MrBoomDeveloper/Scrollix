package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.format.Formats;

public class ActionsMenu {
	private final LinearLayout actionsMenuView;
	private final DrawableBuilder background;

	public ActionsMenu(Context context) {
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();

		background = new DrawableBuilder().setColor(theme.popupBackground);

		actionsMenuView = new LinearLayout(context);
		actionsMenuView.setBackground(background.build());
	}

	public void showAt(View view) {
		boolean isLandscape = AppManager.isLandscape();

		background.setCornerRadius(isLandscape ? Formats.SMALL_POPUP_RADIUS : 0);
		actionsMenuView.setBackground(background.build());

		if(isLandscape) {
			var popup = new PopupWindow(actionsMenuView, 100, 100);
			popup.setFocusable(true);
			popup.showAsDropDown(view);
		}
	}
}