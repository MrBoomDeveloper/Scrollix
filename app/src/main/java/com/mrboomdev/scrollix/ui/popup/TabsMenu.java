package com.mrboomdev.scrollix.ui.popup;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
import com.mrboomdev.scrollix.engine.tab.TabListener;
import com.mrboomdev.scrollix.engine.tab.TabManager;
import com.mrboomdev.scrollix.engine.tab.TabStore;
import com.mrboomdev.scrollix.util.AppUtils;
import com.mrboomdev.scrollix.util.drawable.DrawableBuilder;
import com.mrboomdev.scrollix.util.drawable.DrawableUtil;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.wasabeef.recyclerview.animators.SlideInLeftAnimator;

public class TabsMenu implements TabListener {
	private static final int LANDSCAPE_WIDTH = 300;
	private final Context context;
	private PopupWindow popup;
	private BottomSheetDialog bottomSheet;
	private Adapter adapter;

	public TabsMenu(Context context) {
		this.context = context;
	}

	public void close() {
		if(popup != null) popup.dismiss();
		if(bottomSheet != null) bottomSheet.dismiss();

		popup = null;
		bottomSheet = null;

		TabManager.removeListener(this);
	}

	public void showAt(View showAtView) {
		var theme = ThemeSettings.ThemeManager.getCurrentValidTheme();
		boolean isLandscape = AppUtils.isLandscape();

		var linear = new LinearLayout(context);
		linear.setLayoutParams(new WindowManager.LayoutParams(Formats.MATCH_PARENT, Formats.WRAP_CONTENT));
		linear.setOrientation(LinearLayout.VERTICAL);
		linear.setBackground(DrawableUtil.createDrawable(theme.popupBackground, isLandscape ? Formats.SMALL_POPUP_RADIUS : 0));
		FormatUtil.setPadding(linear, Formats.SMALL_PADDING);

		var recycler = new RecyclerView(context);
		recycler.setLayoutManager(new LinearLayoutManager(context));

		adapter = new Adapter();
		recycler.setAdapter(adapter);
		recycler.setItemAnimator(new SlideInLeftAnimator(new AccelerateDecelerateInterpolator()));
		TabManager.addListener(this);

		var touchHelper = new ItemTouchHelper(new SwipeCallback());
		touchHelper.attachToRecyclerView(recycler);

		linear.addView(recycler, Formats.MATCH_PARENT, Formats.WRAP_CONTENT);
		((LinearLayout.LayoutParams)recycler.getLayoutParams()).weight = 1;

		var createIcon = DrawableUtil.getDrawable(R.drawable.ic_add_black, theme.popupTitle);

		var createButton = new ImageView(context);
		FormatUtil.setPadding(createButton, Formats.BIG_PADDING);
		createButton.setImageDrawable(createIcon);
		createButton.setBackgroundResource(R.drawable.ripple_circle);
		createButton.setClickable(true);
		createButton.setFocusable(true);
		linear.addView(createButton, Formats.NORMAL_ELEMENT, Formats.NORMAL_ELEMENT);

		createButton.setOnClickListener(_view -> {
			TabStore.createTab(true);
			close();
		});

		close();

		if(isLandscape) {
			popup = new PopupWindow(linear, FormatUtil.getDip(LANDSCAPE_WIDTH), Formats.WRAP_CONTENT);
			popup.setFocusable(true);
			popup.showAsDropDown(showAtView);
		} else {
			bottomSheet = new BottomSheetDialog(context);
			bottomSheet.setContentView(linear);
			bottomSheet.setCanceledOnTouchOutside(true);
			bottomSheet.show();
		}
	}

	@Override
	public void onTabGotTitle(Tab tab, String title) {
		adapter.notifyItemChanged(TabStore.getTabIndex(tab));
	}

	@Override
	public void onTabLoadingFinished(Tab tab) {
		adapter.notifyItemChanged(TabStore.getTabIndex(tab));
	}

	@Override
	public void onTabLoadingStarted(Tab tab) {
		adapter.notifyItemChanged(TabStore.getTabIndex(tab));
	}

	private class Adapter extends RecyclerView.Adapter<Adapter.MyViewHolder> {

		@NonNull
		@Override
		public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			var linear = new LinearLayout(context);
			var width = AppUtils.isLandscape() ? FormatUtil.getDip(LANDSCAPE_WIDTH) : Formats.MATCH_PARENT;
			linear.setLayoutParams(new RecyclerView.LayoutParams(width, Formats.WRAP_CONTENT));
			return new MyViewHolder(linear);
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

			var context = AppManager.getActivityContext();
			if(context == null || tab == null) return;

			var parent = context.findViewById(R.id.main_screen_parent);
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
			private final ImageView remove;

			public MyViewHolder(LinearLayout parent) {
				super(parent);

				linear = new LinearLayout(context);
				linear.setOrientation(LinearLayout.HORIZONTAL);
				linear.setGravity(Gravity.CENTER_VERTICAL);
				linear.setClickable(true);
				linear.setFocusable(true);
				linear.setBackgroundResource(R.drawable.ripple_square);
				linear.setPadding(Formats.BIG_PADDING, Formats.SMALL_PADDING, Formats.BIG_PADDING, Formats.SMALL_PADDING);

				title = new TextView(context);
				title.setSingleLine();
				linear.addView(title);
				((LinearLayout.LayoutParams)title.getLayoutParams()).weight = 1;

				remove = new ImageView(context);
				remove.setClickable(true);
				remove.setFocusable(true);

				remove.setForeground(new DrawableBuilder.RippleDrawableBuilder()
						.setColor("#55ffffff")
						.setShape(DrawableBuilder.Shape.OVAL)
						.build());

				remove.setImageDrawable(DrawableUtil.getDrawable(R.drawable.ic_close_black, "#eeeeee"));
				linear.addView(remove, Formats.NORMAL_ELEMENT, Formats.NORMAL_ELEMENT);
				FormatUtil.setPadding(remove, Formats.PADDING);

				var width = AppUtils.isLandscape() ? FormatUtil.getDip(LANDSCAPE_WIDTH) : Formats.MATCH_PARENT;
				parent.addView(linear, width, ViewGroup.LayoutParams.WRAP_CONTENT);
			}

			public void setTab(@NonNull Tab tab) {
				title.setText(tab.getTitle() != null ? tab.getTitle() : tab.getUrl());

				boolean isCurrent = tab == TabManager.getCurrentTab();
				title.setTextColor(Color.parseColor(isCurrent ? "#ffffff" : "#bbaacc"));

				linear.setOnClickListener(view -> {
					TabManager.setCurrentTab(tab);
					close();
				});

				remove.setOnClickListener(_view -> adapter.remove(TabStore.getTabIndex(tab)));
			}
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