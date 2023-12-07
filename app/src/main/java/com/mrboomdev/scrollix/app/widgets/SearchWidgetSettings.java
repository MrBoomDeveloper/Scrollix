package com.mrboomdev.scrollix.app.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mrboomdev.scrollix.R;

public class SearchWidgetSettings extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search_widget_settings);

		var id = getIntent().getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

		TextView saveButton = findViewById(R.id.save_button);
		saveButton.setOnClickListener(_view -> {
			var intent = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
			setResult(Activity.RESULT_OK, intent);
			finish();
		});

		TextView cancelButton = findViewById(R.id.cance_button);
		cancelButton.setOnClickListener(_view -> {
			var intent = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
			setResult(Activity.RESULT_CANCELED, intent);
			finish();
		});
	}
}