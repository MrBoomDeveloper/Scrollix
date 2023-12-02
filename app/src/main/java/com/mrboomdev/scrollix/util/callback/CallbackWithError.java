package com.mrboomdev.scrollix.util.callback;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public interface CallbackWithError<T, E> {
	void onSuccess(T value);

	void onError(E e);

	@NonNull
	@Contract("_ -> new")
	static <T, E> CallbackWithError<T, E> fromValue(Callback1<T> callback) {
		return new CallbackWithError<>() {
			@Override
			public void onSuccess(T value) {
				callback.run(value);
			}

			@Override
			public void onError(E e) {
				if(e instanceof Throwable exception) {
					exception.printStackTrace();
				}
			}
		};
	}
}