package com.mrboomdev.scrollix.app.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.ui.MainActivity;

public class SearchWidgetProvider extends AppWidgetProvider {

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, @NonNull int[] appWidgetIds) {
		for(int id : appWidgetIds) {
			var pendingIntent = PendingIntent.getActivity(context,
					0, new Intent(context, MainActivity.class).putExtra("type", "open_search"),
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

			var views = new RemoteViews(context.getPackageName(), R.layout.search_widget);
			views.setOnClickPendingIntent(R.id.search_widget, pendingIntent);

			appWidgetManager.updateAppWidget(id, views);
		}
	}
}