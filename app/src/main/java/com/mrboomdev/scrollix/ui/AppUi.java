package com.mrboomdev.scrollix.ui;

import android.annotation.SuppressLint;

import com.mrboomdev.scrollix.ui.popup.ActionsMenu;
import com.mrboomdev.scrollix.ui.popup.TabsMenu;

@SuppressLint("StaticFieldLeak")
public class AppUi {
	public static TabsMenu tabsMenu;
	public static ActionsMenu actionsMenu;
	public static BarsAnimator barsAnimator;

	public static void dispose() {
		if(tabsMenu != null) tabsMenu.close();
		if(actionsMenu != null) actionsMenu.close();

		tabsMenu = null;
		actionsMenu = null;
		barsAnimator = null;
	}
}