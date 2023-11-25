package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
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
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mrboomdev.scrollix.R;
import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.data.settings.ThemeSettings;
import com.mrboomdev.scrollix.engine.tab.Tab;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();
		boolean isLandscape = AppManager.isLandscape();

		var linear = new LinearLayout(context);
		linear.setOrientation(LinearLayout.VERTICAL);
		linear.setBackground(DrawableUtil.createDrawable(theme.popupBackground, isLandscape ? 16 : 0));
		FormatUtil.setPadding(linear, Formats.SMALL_PADDING);

		var recycler = new RecyclerView(context);
		recycler.setLayoutManager(new LinearLayoutManager(context));
		recycler.setItemAnimator(new Animator());

		adapter = new Adapter();
		recycler.setAdapter(adapter);

		var touchHelper = new ItemTouchHelper(new SwipeCallback());
		touchHelper.attachToRecyclerView(recycler);

		linear.addView(recycler, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		((LinearLayout.LayoutParams)recycler.getLayoutParams()).weight = 1;

		var createIcon = DrawableUtil.getDrawable(R.drawable.ic_add_black, theme.popupTitle);
		var createButtonSizes = FormatUtil.getDip(12, 50);

		var createButton = new ImageView(context);
		FormatUtil.setPadding(createButton, createButtonSizes[0]);
		createButton.setImageDrawable(createIcon);
		createButton.setBackgroundResource(R.drawable.ripple_circle);
		createButton.setClickable(true);
		createButton.setFocusable(true);
		linear.addView(createButton, createButtonSizes[1], createButtonSizes[1]);

		createButton.setOnClickListener(_view -> {
			TabStore.createTab(true);

			close();
		});

		if(isLandscape) {
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
			var tab = TabStore.getTab(index);
			int wasCount = TabStore.getTabCount();

			TabStore.removeTab(index);
			notifyItemRemoved(index);

			int newIndex = TabStore.getTabIndex(TabManager.getCurrentTab());
			notifyItemChanged(newIndex);

			//A new tab is being automatically created if there is 0 tabs
			if(TabStore.getTabCount() == 1 && wasCount == 1) close();

			if(tab == null) return;

			var parent = AppManager.getActivityContext().findViewById(R.id.main_screen_parent);
			var snackbar = Snackbar.make(parent, "Tab was closed. Do you want to restore it?", Snackbar.LENGTH_SHORT);
			var didRestored = new AtomicBoolean(false);

			snackbar.setAction("Restore tab", _view -> {
				restore(tab, index);
				TabManager.setCurrentTab(tab);
				didRestored.set(true);
				snackbar.dismiss();
			});

			snackbar.addCallback(new BaseTransientBottomBar.BaseCallback<>() {
				@Override
				public void onDismissed(Snackbar transientBottomBar, int event) {
					if(didRestored.get()) return;
					tab.dispose();
				}
			});

			snackbar.show();
		}

		public void move(int fromIndex, int toIndex) {
			//TabsManager.move(fromIndex, toIndex);
			//notifyItemMoved(fromIndex, toIndex);
		}

		public void restore(Tab tab, int index) {
			TabStore.addTab(tab, index);
			notifyItemInserted(index);
		}

		@Override
		public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
			holder.setTab(Objects.requireNonNull(TabStore.getTab(position)));
		}

		@Override
		public int getItemCount() {
			return TabStore.getTabCount();
		}

		private class MyViewHolder extends RecyclerView.ViewHolder {
			private final LinearLayout linear;
			private final TextView title;

			public MyViewHolder(LinearLayout parent) {
				super(parent);

				linear = new LinearLayout(context);
				linear.setOrientation(LinearLayout.HORIZONTAL);
				linear.setClickable(true);
				linear.setFocusable(true);
				linear.setBackgroundResource(R.drawable.ripple_square);
				FormatUtil.setPadding(linear, Formats.BIG_PADDING);

				title = new TextView(context);
				linear.addView(title);

				parent.addView(linear, FormatUtil.getDip(LANDSCAPE_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT);
			}

			public void setTab(@NonNull Tab tab) {
				title.setText(tab.getTitle());

				boolean isCurrent = tab == TabManager.getCurrentTab();
				title.setTextColor(Color.parseColor(isCurrent ? "#ffffff" : "#bbaacc"));

				linear.setOnClickListener(view -> {
					TabManager.setCurrentTab(tab);
					close();
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
			return makeMovementFlags(
					ItemTouchHelper.UP | ItemTouchHelper.DOWN,
					ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
		}

		@Override
		public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
			int position = viewHolder.getAdapterPosition();

			if(direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
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