package com.mrboomdev.scrollix.app;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.util.format.FormatUtil;
import com.mrboomdev.scrollix.util.format.Formats;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.Random;

public class DownloadManager {

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
}