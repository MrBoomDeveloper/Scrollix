package com.mrboomdev.scrollix.ui.popup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;

public class TabsMenu {
	private static final int LANDSCAPE_WIDTH = 300;
	private final Context context;
	private PopupWindow popup;
	private BottomSheetDialog bottomSheet;

	public TabsMenu(Context context) {
		this.context = context;
	}

	public void showAt(View showAtView) {
		var linear = new LinearLayout(context);
		linear.setOrientation(LinearLayout.VERTICAL);
		linear.setBackgroundResource(R.color.black);

		var recycler = new RecyclerView(context);
		recycler.setLayoutManager(new LinearLayoutManager(context));
		recycler.setAdapter(new Adapter());
		linear.addView(recycler, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		((LinearLayout.LayoutParams)recycler.getLayoutParams()).weight = 1;

		var createIcon = FileUtil.getDrawable(R.drawable.ic_add_black, "#ccccdd");

		var createButtonSizes = FormatUtil.getDip(12, 50);

		var createButton = new ImageView(context);
		createButton.setPadding(createButtonSizes[0], createButtonSizes[0], createButtonSizes[0], createButtonSizes[0]);
		createButton.setImageDrawable(createIcon);
		createButton.setBackgroundResource(R.drawable.ripple_circle);
		createButton.setClickable(true);
		createButton.setFocusable(true);
		linear.addView(createButton, createButtonSizes[1], createButtonSizes[1]);

		createButton.setOnClickListener(_view -> {
			TabsManager.create(true);

			if(popup != null) popup.dismiss();
			if(bottomSheet != null) bottomSheet.cancel();

			popup = null;
			bottomSheet = null;
		});

		if(AppManager.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
			popup = new PopupWindow(linear, FormatUtil.getDip(LANDSCAPE_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT);
			popup.setFocusable(true);
			popup.showAsDropDown(showAtView);
		} else {
			bottomSheet = new BottomSheetDialog(context);
			bottomSheet.setContentView(linear);
			bottomSheet.setCanceledOnTouchOutside(true);
			bottomSheet.show();
		}
	}

	private class Adapter extends RecyclerView.Adapter<Adapter.MyViewHolder> {

		@NonNull
		@Override
		public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new MyViewHolder(new LinearLayout(context));
		}

		@Override
		public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
			holder.setTab(TabsManager.get(position));
		}

		@Override
		public int getItemCount() {
			return TabsManager.getCount();
		}

		private class MyViewHolder extends RecyclerView.ViewHolder {
			private final LinearLayout linear;
			private final TextView title;

			@SuppressLint("ClickableViewAccessibility")
			public MyViewHolder(LinearLayout parent) {
				super(parent);
				int padding = FormatUtil.getDip(12);

				linear = new LinearLayout(context);
				linear.setOrientation(LinearLayout.HORIZONTAL);
				linear.setClickable(true);
				linear.setFocusable(true);
				linear.setPadding(padding, padding, padding, padding);
				linear.setBackgroundResource(R.drawable.ripple_square);

				linear.setOnTouchListener(new View.OnTouchListener() {
					private float x;

					private void updateCoordinates(@NonNull MotionEvent event) {
						this.x = event.getX();
					}

					@Override
					public boolean onTouch(View view, MotionEvent event) {
						switch(event.getAction()) {
							case MotionEvent.ACTION_UP -> {
								view.setTranslationX(0);
								return false;
							}

							case MotionEvent.ACTION_MOVE -> {
								view.setTranslationX(x - event.getX());
								return true;
							}

							case MotionEvent.ACTION_DOWN -> {
								updateCoordinates(event);
								return true;
							}
						}

						return false;
					}
				});

				title = new TextView(context);
				linear.addView(title);

				parent.addView(linear, FormatUtil.getDip(LANDSCAPE_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT);
			}

			public void setTab(@NonNull Tab tab) {
				title.setText(tab.title);

				linear.setOnClickListener(view -> {
					TabsManager.setCurrent(tab);
					popup.dismiss();
					popup = null;
				});
			}
		}
	}

	private static class Animator extends RecyclerView.ItemAnimator {

		@Override
		public boolean animateDisappearance(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo) {
			return false;
		}

		@Override
		public boolean animateAppearance(@NonNull RecyclerView.ViewHolder viewHolder, @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
			return false;
		}

		@Override
		public boolean animatePersistence(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
			return false;
		}

		@Override
		public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder, @NonNull RecyclerView.ViewHolder newHolder, @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
			return false;
		}

		@Override
		public void runPendingAnimations() {

		}

		@Override
		public void endAnimation(@NonNull RecyclerView.ViewHolder item) {

		}

		@Override
		public void endAnimations() {

		}

		@Override
		public boolean isRunning() {
			return false;
		}
	}


}