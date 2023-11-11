package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.data.tabs.Tab;
import com.mrboomdev.scrollix.data.tabs.TabsManager;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.FormatUtil;

import java.util.Objects;

public class TabsMenu {
	private static final int LANDSCAPE_WIDTH = 300;
	private final Context context;
	private final ThemeSettings theme;
	private PopupWindow popup;
	private BottomSheetDialog bottomSheet;
	private Adapter adapter;

	public TabsMenu(Context context, ThemeSettings theme) {
		this.context = context;
		this.theme = theme;
	}

	public void close() {
		if(popup != null) popup.dismiss();
		if(bottomSheet != null) bottomSheet.cancel();

		popup = null;
		bottomSheet = null;
	}

	public void showAt(View showAtView) {
		var linear = new LinearLayout(context);
		linear.setOrientation(LinearLayout.VERTICAL);
		linear.setBackgroundResource(R.color.black);

		var recycler = new RecyclerView(context);
		recycler.setLayoutManager(new LinearLayoutManager(context));
		recycler.setItemAnimator(new Animator());

		adapter = new Adapter();
		recycler.setAdapter(adapter);

		var touchHelper = new ItemTouchHelper(new SwipeCallback());
		touchHelper.attachToRecyclerView(recycler);

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

			close();
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

		public void remove(int index) {
			TabsManager.remove(index);
			notifyItemRemoved(index);

			int newIndex = TabsManager.getIndex(TabsManager.getCurrent());
			notifyItemChanged(newIndex);

			if(TabsManager.getCount() == 0) close();
		}

		public void move(int fromIndex, int toIndex) {
			TabsManager.move(fromIndex, toIndex);
			notifyItemMoved(fromIndex, toIndex);
		}

		public void restore(Tab tab, int index) {
			TabsManager.add(tab, index);
			notifyItemInserted(index);
		}

		@Override
		public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
			holder.setTab(Objects.requireNonNull(TabsManager.get(position)));
		}

		@Override
		public int getItemCount() {
			return TabsManager.getCount();
		}

		private class MyViewHolder extends RecyclerView.ViewHolder {
			private final LinearLayout linear;
			private final TextView title;

			public MyViewHolder(LinearLayout parent) {
				super(parent);
				int padding = FormatUtil.getDip(12);

				linear = new LinearLayout(context);
				linear.setOrientation(LinearLayout.HORIZONTAL);
				linear.setClickable(true);
				linear.setFocusable(true);
				linear.setPadding(padding, padding, padding, padding);
				linear.setBackgroundResource(R.drawable.ripple_square);

				title = new TextView(context);
				linear.addView(title);

				parent.addView(linear, FormatUtil.getDip(LANDSCAPE_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT);
			}

			public void setTab(@NonNull Tab tab) {
				title.setText(tab.title);

				boolean isCurrent = tab == TabsManager.getCurrent();
				title.setTextColor(Color.parseColor(isCurrent ? "#ffffff" : "#bbaacc"));

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

	private class SwipeCallback extends ItemTouchHelper.Callback {

		@Override
		public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
			return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT);
		}

		@Override
		public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			int position = viewHolder.getAdapterPosition();

			if(direction == ItemTouchHelper.LEFT) {
				adapter.remove(position);
			}
		}

		@Override
		public void onMoved(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, int fromPos, @NonNull RecyclerView.ViewHolder target, int toPos, int x, int y) {
			adapter.move(fromPos, toPos);
		}

		@Override
		public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
			return false;
		}
	}
}