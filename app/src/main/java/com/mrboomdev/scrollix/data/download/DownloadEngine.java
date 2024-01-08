package com.mrboomdev.scrollix.data.download;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.util.exception.DownloadException;
import com.mrboomdev.scrollix.util.exception.UnexpectedBehaviourException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DownloadEngine {
	private static final List<Download> downloads = new ArrayList<>();
	private static final String TAG = "DownloadEngine";

	protected static void start(@NonNull Download download) {
		if(downloads.contains(download)) return;
		downloads.add(download);

		prepareDownloadFile(download);

		try {
			if(download.isBase64()) {
				downloadBase64File(download);
				return;
			}

			DownloadHttp.start(download);
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

	protected static void prepareDownloadFile(@NonNull Download download) {
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

		DownloadHttp.stop(download);
		download.onFinish();
	}
}