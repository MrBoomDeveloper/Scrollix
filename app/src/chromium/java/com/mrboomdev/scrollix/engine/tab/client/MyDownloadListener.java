package com.mrboomdev.scrollix.engine.tab.client;

import android.webkit.DownloadListener;

import com.mrboomdev.scrollix.data.download.UserMadeDownload;

public class MyDownloadListener implements DownloadListener {

	@Override
	public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
		new UserMadeDownload(url).start();
	}
}