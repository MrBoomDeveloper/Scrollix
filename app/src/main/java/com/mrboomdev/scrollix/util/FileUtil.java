package com.mrboomdev.scrollix.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrboomdev.scrollix.app.AppManager;

import org.jetbrains.annotations.Contract;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class FileUtil {

	public static boolean deleteFile(@NonNull File file) {
		if(file.isDirectory()) {
			var children = file.listFiles();
			if(children == null) return false;

			for(var child : children) {
				deleteFile(child);
			}
		}

		return file.delete();
	}

	@NonNull
	public static String readFileString(File file) {
		return new String(readFileBytes(file));
	}

	@NonNull
	@Contract("_ -> new")
	public static String readAssetsString(String path) {
		return new String(readAssetsBytes(path));
	}

	@Nullable
	public static byte[] readAssetsBytes(String path) {
		try(var is = AppManager.getAppContext().getAssets().open(path)) {
			return readStreamBytes(is);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static long getFileSize(@NonNull File file) {
		long size = 0;

		if(file.isFile()) {
			size += file.length();
		} if(file.isDirectory()) {
			var kids = file.listFiles();
			if(kids == null) return 0;

			for(var childFile : kids) {
				size += getFileSize(childFile);
			}
		}

		return size;
	}

	@Nullable
	public static byte[] readFileBytes(@NonNull File file) {
		try(var stream = new FileInputStream(file)) {
			return readStreamBytes(stream);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Nullable
	public static byte[] readStreamBytes(@NonNull InputStream stream) {
		try(var io = new ByteArrayOutputStream()) {
			var buffer = new byte[1024];
			int read;

			while((read = stream.read(buffer, 0, buffer.length)) != -1) {
				io.write(buffer, 0, read);
			}

			return io.toByteArray();
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@NonNull
	@Contract("_ -> new")
	public static File getFile(String path) {
		return new File(AppManager.getAppContext().getExternalFilesDir(null), path);
	}

	public static void writeFile(@NonNull String text, @NonNull File file, boolean append) {
		Objects.requireNonNull(file.getParentFile()).mkdirs();

		try(var stream = new FileOutputStream(file, append)) {
			var bytes = text.getBytes();
			stream.write(bytes);
		} catch(IOException e) {
			throw new RuntimeException("Failed to write a file!", e);
		}
	}

	public static void writeFile(@NonNull String text, @NonNull File file) {
		writeFile(text, file, false);
	}

	public static void writeFile(File from, File to, boolean append) {
		try(var in = new FileInputStream(from)) {
			try(var out = new FileOutputStream(to, append)) {
				var buffer = new byte[1024];
				int read;

				while((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeFile(File from, File to) {
		writeFile(from, to, false);
	}

	public static void writeBitmap(@NonNull Bitmap bitmap, File file) {
		try(var stream = new FileOutputStream(file)) {
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
		} catch(IOException e) {
			throw new RuntimeException("Failed to write a bitmap file!", e);
		}
	}

	public static void writeDrawable(int res, File file) {
		var resources = AppManager.getAppContext().getResources();
		var bitmap = BitmapFactory.decodeResource(resources, res);
		writeBitmap(bitmap, file);
	}

	public static Bitmap createBitmap(byte[] bytes) {
		return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
	}
}