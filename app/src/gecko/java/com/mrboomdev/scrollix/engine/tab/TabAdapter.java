package com.mrboomdev.scrollix.engine.tab;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.data.DataProfile;
import com.squareup.moshi.FromJson;
import com.squareup.moshi.ToJson;

import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.List;

public class TabAdapter {

	@ToJson
	public DataProfile.SavedTab toJson(@NonNull Tab tab) {
		var state = tab.getState();

		var item = new DataProfile.SavedHistoryItem(tab.getUrl(), tab.getTitle());
		return new DataProfile.SavedTab(List.of(item), 0, state.toString());
	}

	@FromJson
	public Tab fromJson(@NonNull DataProfile.SavedTab tab) {
		var state = GeckoSession.SessionState.fromString(tab.extra());

		var _tab = new Tab(true);

		if(tab.history() != null) {
			var item = tab.history().get(0);

			if(item != null) {
				_tab.setUrl(item.url());
				_tab.setTitle(item.title());
			}
		}

		_tab.restoreState(state);

		return _tab;
	}

	public static class ExportAdapter extends TabAdapter {

		@Override
		public Tab fromJson(@NonNull DataProfile.SavedTab tab) {
			var _tab = new Tab(true);
			var history = tab.history();

			if(history != null) {
				for(int i = 0; i < history.size(); i++) {
					if(i > tab.currentHistoryItem()) break;

					var item = history.get(i);
					_tab.loadUrl(item.url());
				}

				var current = history.get(tab.currentHistoryItem());

				if(current != null) {
					_tab.setUrl(current.url());
					_tab.setTitle(current.title());
				}
			}

			return _tab;
		}

		@Override
		public DataProfile.SavedTab toJson(@NonNull Tab tab) {
			var state = tab.getState();
			var history = new ArrayList<DataProfile.SavedHistoryItem>();
			int currentIndex = 0;

			for(var item : state) {
				history.add(new DataProfile.SavedHistoryItem(item.getUri(), item.getTitle()));
			}

			try {
				currentIndex = state.getCurrentIndex();
			} catch(IllegalStateException e) {
				e.printStackTrace();
			}

			return new DataProfile.SavedTab(history, currentIndex, null);
		}
	}
}