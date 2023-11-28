package com.mrboomdev.scrollix.data.download;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.util.exception.DownloadException;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DownloadEngine {
	private static final List<Download> downloads = new ArrayList<>();
	private static final Map<Download, DownloadProcess> processes = new HashMap<>();
	private static final int DOWNLOAD_BUFFER_SIZE = 2;
	private static final int DOWNLOAD_TIMEOUT_SECONDS = 0;

	protected static void start(@NonNull Download download) {
		if(downloads.contains(download)) return;
		downloads.add(download);

		prepareDownloadFile(download);
		processes.put(download, new DownloadProcess());

		try {
			if(download.isBase64()) {
				downloadBase64File(download);
				return;
			}

			downloadHttpFile(download);
		} catch(DownloadException e) {
			download.onFailed(e);
			stop(download);
		}
	}

	private static void downloadBase64File(@NonNull Download download) throws DownloadException {
		var decodedBytes = Base64.decode(download.getBase64Data(), Base64.DEFAULT);

		try(var os = new FileOutputStream(download.getFile())) {
			os.write(decodedBytes);
		} catch(IOException e) {
			throw new DownloadException("Failed to write bytes into memory!", e);
		}

		download.onSucceed();
		stop(download);
	}

	private static void downloadHttpFile(@NonNull Download download) throws DownloadException {
		var process = processes.get(download);
		HttpURLConnection connection;
		boolean continueDownload;

		if(process == null) {
			throw new UnexpectedBehaviourException("Failed to find a process associated with a download.");
		}

		try {
			connection = (HttpURLConnection) download.getUrl().openConnection();
			//connection.setReadTimeout(DOWNLOAD_TIMEOUT_SECONDS * 1000);
			//connection.setRequestProperty("Connection", "keep-alive");

			for(var header : download.getHeaders().entrySet()) {
				//connection.setRequestProperty(header.getKey(), header.getValue());
			}
		} catch(IOException e) {
			throw new DownloadException("Failed to open a connection to a url: " + download.getUrl());
		}

		//try {
			//process.responseCode = connection.getResponseCode();
			//process.responseMessage = connection.getResponseMessage();
			download.setContentSize(connection.getContentLengthLong());
		//} catch(IOException e) {
			//throw new DownloadException("Failed to retrieve a response status", e);
		//}

		try(var is = new BufferedInputStream(connection.getInputStream())) {
			try(var os = new FileOutputStream(download.getFile())) {
				process.inputStream = is;
				process.outputStream = os;

				byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE * 1024];
				int bytesRead;

				while((bytesRead = is.read(buffer)) != -1) {
					os.write(buffer, 0, bytesRead);
					download.onProgress(bytesRead);
				}
			} catch(IOException e) {
				if(process.cancelled) return;

				throw new DownloadException("Failed to write into a file!", e);
			}
		} catch(IOException e) {
			if(process.cancelled) return;

			throw new DownloadException("Failed to read from a url!", e);
		}

		if(process.cancelled) return;
		download.onSucceed();
		stop(download);
	}

	private static void prepareDownloadFile(@NonNull Download download) {
		var parent = download.getFile().getParentFile();

		if(!Objects.requireNonNull(parent).exists()) {
			boolean didCreated = parent.mkdirs();

			if(!didCreated) {
				throw new UnexpectedBehaviourException("Failed to create a directory! " + parent.getPath());
			}
		}

		if(!download.getFile().exists()) {
			try {
				download.getFile().createNewFile();
			} catch(IOException e) {
				throw new UnexpectedBehaviourException("Failed to create a file!", e);
			}
		}
	}

	protected static void resume(Download download) {
		if(!downloads.contains(download)) return;
	}

	protected static void pause(Download download) {
		if(!downloads.contains(download)) return;
	}

	protected static void stop(Download download) {
		if(!downloads.contains(download)) return;
		downloads.remove(download);

		var process = processes.get(download);
		if(process == null) return;

		process.dispose();
		download.onFinish();
	}

	private static class DownloadProcess {
		public int responseCode;
		public String responseMessage;
		public InputStream inputStream;
		public OutputStream outputStream;
		public boolean cancelled;

		public void dispose() {
			cancelled = true;

			new Thread(() -> {
				try { inputStream.close(); } catch(Exception ignored) {}
				try { outputStream.close(); } catch(Exception ignored) {}

				inputStream = null;
				outputStream = null;
			}).start();
		}
	}
}