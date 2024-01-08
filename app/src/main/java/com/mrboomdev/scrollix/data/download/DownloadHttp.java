package com.mrboomdev.scrollix.data.download;

import android.util.Log;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;
import com.mrboomdev.scrollix.util.FileUtil;
import com.mrboomdev.scrollix.util.exception.DownloadException;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DownloadHttp {
	private static final Map<Download, DownloadProcess> processes = new HashMap<>();
	private static final int DOWNLOAD_TIMEOUT_SECONDS = 30;
	private static final int FROM_START = -2;
	private static final int TO_END = -3;
	private static final int NOT_RANGED = -1;
	private static final String TAG = "DownloadHttp";

	/**
	 * Tests were made to check the most optimal result.
	 * DOWNLOAD_BUFFER_SIZE was set to 10x.
	 * <p>1x = 1:39</p>
	 * <p>5x = 42</p>
	 **/
	private static final int RANGED_CHUCKS_COUNT = 5;

	/**
	 * Tests were made to check the most optimal result.
	 * RANGED_CHUCKS_COUNT was set to 5x.
	 * <p>20-50x = 40</p>
	 * <p>100x = 41</p>
	 **/
	private static final int DOWNLOAD_BUFFER_SIZE = 15;

	protected static void start(Download download) throws DownloadException {
		var process = new DownloadProcess();
		processes.put(download, process);
		HttpURLConnection connection;

		try {
			connection = (HttpURLConnection) download.getUrl().openConnection();
			connection.setReadTimeout(DOWNLOAD_TIMEOUT_SECONDS * 1000);
			download.setContentSize(connection.getContentLengthLong());
			var areRangesAllowed = connection.getHeaderField("Accept-Ranges").equals("bytes");
			connection.disconnect();

			if(download.getContentSize() != -1 && areRangesAllowed) {
				startRanged(download, process);
			} else {
				Log.d(TAG, "Downloading in a SingleRange mode.");

				downloadAtRange(new DownloadRange(download), () -> {
					download.onSucceed();
					processes.remove(download);
				});
			}

			//connection.setRequestProperty("Connection", "keep-alive");

			//for(var header : download.getHeaders().entrySet()) {
			//connection.setRequestProperty(header.getKey(), header.getValue());
			//}
		} catch(IOException e) {
			throw new DownloadException("Failed to open a connection to a url: " + download.getUrl());
		}

		//try {
		//process.responseCode = connection.getResponseCode();
		//process.responseMessage = connection.getResponseMessage();
		//} catch(IOException e) {
		//throw new DownloadException("Failed to retrieve a response status", e);
		//}

		//if(process.cancelled) return;
		//download.onSucceed();
		//stop(download);
	}

	private static void startRanged(@NonNull Download download, DownloadProcess process) {
		long chunkLength = download.getContentSize() / RANGED_CHUCKS_COUNT;

		Log.d(TAG, "Downloading in a MultiRanged mode!");
		download.setIsMultiRange(true);

		long used = 0;
		int count = 0;

		while(used < download.getContentSize()) {
			long to = used + chunkLength;

			if(to > download.getContentSize()) {
				chunkLength = download.getContentSize() - used;
				to = download.getContentSize();
			}

			process.ranges.add(new DownloadRange(count, used, to, download));

			used += chunkLength;
			count++;
		}

		process.ranges.sort(Comparator.comparingInt(next -> next.id));

		for(var range : process.ranges) {
			new Thread(() -> {
				try {
					downloadAtRange(range, () -> checkIfAllFinished(download));
				} catch(DownloadException e) {
					if(e.isCancelled()) return;
					process.dispose();
					throw new RuntimeException(e);
				}
			}).start();
		}
	}

	private static synchronized void checkIfAllFinished(Download download) {
		var process = Objects.requireNonNull(processes.get(download));
		if(!process.ranges.get(0).didFinished) return;

		while(true) {
			var nextRange = process.ranges.get(process.writtenRangesCount);

			if(!nextRange.didFinished) {
				return;
			}

			var child = "ranged" + nextRange.id + ".bin";
			var file = new File(AppManager.getActivityContext().getExternalFilesDir(null), child);
			FileUtil.writeFile(file, download.getFile(), true);
			process.writtenRangesCount++;

			if(process.writtenRangesCount == process.ranges.size()) {
				finishRanged(download);
				return;
			}
		}
	}

	private static void finishRanged(@NonNull Download download) {
		var process = Objects.requireNonNull(processes.get(download));
		download.onSucceed();
		processes.remove(download);

		for(int id = 1; id < process.ranges.size(); id++) {
			var child = "ranged" + id + ".bin";
			var file = new File(AppManager.getActivityContext().getExternalFilesDir(null), child);
			FileUtil.deleteFile(file);
		}
	}

	private static void downloadAtRange(@NonNull DownloadRange range, Runnable callback) throws DownloadException {
		final boolean isRanged = range.id != NOT_RANGED;
		var file = range.download.getFile();
		var process = processes.get(range.download);
		HttpURLConnection connection;

		if(process == null) {
			throw new UnexpectedBehaviourException("Failed to find a process associated with a download.");
		}

		try {
			connection = (HttpURLConnection) range.download.getUrl().openConnection();

			if(isRanged) {
				var rangedBuilder = new StringBuilder("bytes=");
				rangedBuilder.append(range.from == FROM_START ? 0 : range.from);
				rangedBuilder.append("-");

				if(range.to != TO_END) {
					rangedBuilder.append(range.to);
				}

				connection.setRequestProperty("Range", rangedBuilder.toString());
			}
		} catch(IOException e) {
			throw new DownloadException("Failed to open a connection to a url: " + range.download.getUrl());
		}

		if(isRanged && range.id != 0) {
			var child = "ranged" + range.id + ".bin";
			file = new File(AppManager.getActivityContext().getExternalFilesDir(null), child);
			Objects.requireNonNull(file.getParentFile()).mkdirs();
		}

		try(var is = new BufferedInputStream(connection.getInputStream())) {
			process.closeables.add(is);

			try(var os = new FileOutputStream(file)) {
				process.closeables.add(os);

				byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE * 1024];
				int bytesRead;

				while((bytesRead = is.read(buffer)) != -1) {
					os.write(buffer, 0, bytesRead);
					range.download.onProgress(bytesRead);

					if(process.cancelled) {
						throw new DownloadException("Download was cancelled.", true);
					}
				}

				process.closeables.remove(os);
			} catch(IOException e) {
				if(process.cancelled) return;
				throw new DownloadException("Failed to write into a file!", e);
			}

			process.closeables.remove(is);
		} catch(IOException e) {
			if(process.cancelled) return;
			throw new DownloadException("Failed to read from a url!", e);
		}

		range.didFinished = true;
		callback.run();
	}

	protected static void stop(Download download) {
		var process = processes.get(download);
		if(process == null) return;

		process.dispose();
	}

	private static class DownloadRange {
		public boolean didFinished;
		public final int id;
		public final long from, to;
		public final Download download;

		public DownloadRange(int id, long from, long to, Download download) {
			this.id = id;
			this.download = download;
			this.from = from;
			this.to = to;
		}

		public DownloadRange(Download download) {
			this(NOT_RANGED, FROM_START, TO_END, download);
		}
	}

	private static class DownloadProcess {
		public final List<DownloadRange> ranges = new ArrayList<>();
		public final List<Closeable> closeables = new ArrayList<>();
		public int writtenRangesCount = 1;
		public int responseCode;
		public String responseMessage;
		public boolean cancelled;

		public void dispose() {
			if(cancelled) return;
			cancelled = true;

			for(var closeable : closeables) {
				try {
					closeable.close();
				} catch(IOException ignored) {}
			}

			closeables.clear();
		}
	}
}