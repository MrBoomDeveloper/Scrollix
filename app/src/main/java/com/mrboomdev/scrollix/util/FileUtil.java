package com.mrboomdev.scrollix.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.mrboomdev.scrollix.app.AppManager;

import org.jetbrains.annotations.Contract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;

public class FileUtil {

	@NonNull
	public static String readFileString(File file) {
		try(var reader = new BufferedReader(new FileReader(file))) {
			var builder = new StringBuilder();

			String line;
			while((line = reader.readLine()) != null) {
				builder.append(line);
			}

			return builder.toString();
		} catch(IOException e) {
			throw new RuntimeException("Failed to read a file!", e);
		}
	}

	@NonNull
	public static byte[] readFileBytes(@NonNull File file) {
		try(var stream = new FileInputStream(file)) {
			var array = new byte[(int)file.length()];
			stream.read(array);
			return array;
		} catch(IOException e) {
			throw new RuntimeException("Failed to read bytes from a file!", e);
		}
	}

	@NonNull
	@Contract("_ -> new")
	public static File getFile(String path) {
		return new File(AppManager.getAppContext().getExternalFilesDir(null), path);
	}

	public static void writeFile(@NonNull String text, @NonNull File file) {
		Objects.requireNonNull(file.getParentFile()).mkdirs();

		try(var stream = new FileOutputStream(file)) {
			var bytes = text.getBytes();
			stream.write(bytes);
		} catch(IOException e) {
			throw new RuntimeException("Failed to write a file!", e);
		}
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