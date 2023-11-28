package com.mrboomdev.scrollix.data.download;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.util.exception.DownloadException;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;
import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class Download {
	private final URL url;
	private long contentSize = -1;
	private File file;
	private String base64data;
	private Map<String, String> headers;
	private boolean isBase64;
	private DownloadException exception;
	private int id = -1;

	public Download(@NonNull String url) {
		if(url.startsWith(Formats.BASE64_IMAGE_PREFIX)) {
			this.isBase64 = true;
			this.base64data = FormatUtil.removeBase64ImagePrefix(url);
			this.url = null;
			return;
		}

		try {
			this.url = new URL(url);
		} catch(MalformedURLException e) {
			throw new UnexpectedBehaviourException("Failed to parse a url: " + url, e);
		}
	}

	public DownloadException getException() {
		return exception;
	}

	public URL getUrl() {
		return url;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public Download setHeaders(Map<String, String> header) {
		this.headers = header;
		return this;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setContentSize(long size) {
		this.contentSize = size;
	}

	public long getContentSize() {
		return contentSize;
	}

	public void onSucceed() {}

	public void onProgress(int bytesRead) {}

	public void onFailed(DownloadException e) {}

	public void onFinish() {}

	public int getId() {
		if(id != -1) return id;

		for(var download : DownloadManager.downloads.entrySet()) {
			if(download.getValue() == this) {
				id = download.getKey();
				return download.getKey();
			}
		}

		return -1;
	}

	public String getBase64Data() throws IllegalStateException {
		if(!isBase64) {
			throw new IllegalStateException("This download doesn't contains a Base64 string!");
		}

		return base64data;
	}

	public boolean isBase64() {
		return isBase64;
	}

	public void start() {
		DownloadManager.download(this);
	}
}