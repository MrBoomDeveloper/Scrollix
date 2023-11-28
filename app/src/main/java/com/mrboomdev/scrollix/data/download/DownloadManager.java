package com.mrboomdev.scrollix.data.download;

import android.os.Environment;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class DownloadManager {
	protected static final Map<Integer, Download> downloads = new HashMap<>();
	private static final Random random = new Random();

	public static Download getDownloadById(int id) {
		return downloads.get(id);
	}

	public static void obtainIdForDownload(Download download) {
		if(downloads.containsValue(download)) return;

		boolean contains = true;
		int id = 0;

		while(contains) {
			id = random.nextInt(Integer.MAX_VALUE);
			contains = downloads.containsKey(id);
		}

		download.setId(id);
		downloads.put(id, download);
	}

	public static void download(Download download) {
		obtainIdForDownload(download);

		if(download.getFile() == null) {
			var parentDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			var file = new File(parentDirectory, "download.bin");
			download.setFile(file);
		}

		var thread = new Thread(() -> DownloadEngine.start(download));
		thread.setName("DownloadFile");
		thread.start();
	}

	@Contract(pure = true)
	public static void download(@NonNull String url, File parentDirectory, String fileName) {
		if(url.startsWith(Formats.BASE64_IMAGE_PREFIX)) {
			saveBase64Image(FormatUtil.removeBase64ImagePrefix(url), parentDirectory);
			return;
		}
	}

	@Contract(pure = true)
	public static void download(@NonNull String url, File parentDirectory) {
		download(url, parentDirectory, getUniqueFile(parentDirectory, "png").getName());
	}

	public static void saveBase64Image(String base64, String extension, File parentDirectory) {
		var decodedBytes = Base64.decode(base64, Base64.DEFAULT);
		var file = getUniqueFile(parentDirectory, extension);
	}

	public static void saveBase64Image(String base64, File parentDirectory) {
		saveBase64Image(base64, "png", parentDirectory);
	}

	private static File getUniqueFile(File parentDirectory, String extension) {
		var random = new Random();
		File file = null;
		boolean done = false;

		while(!done) {
			file = new File(parentDirectory, random.nextInt() + "." + extension);
			if(file.exists()) continue;
			done = true;
		}

		return file;
	}

	public static void cancel(int id) {
		DownloadEngine.stop(getDownloadById(id));
	}
}