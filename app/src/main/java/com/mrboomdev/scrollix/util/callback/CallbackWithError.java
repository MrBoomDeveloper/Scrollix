package com.mrboomdev.scrollix.util.callback;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public interface CallbackWithError<R, E> {
	void onSuccess(R value);

	void onError(E e);

	@NonNull
	@Contract("_ -> new")
	static <R, E> CallbackWithError<R, E> fromValue(Callback1<R> callback) {
		return new CallbackWithError<>() {
			@Override
			public void onSuccess(R value) {
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

	@NonNull
	@Contract(" -> new")
	static <R, E> CallbackWithError<R, E> fromValue() {
		return new CallbackWithError<>() {
			@Override
			public void onSuccess(R value) {}

			@Override
			public void onError(E e) {
				if(e instanceof Throwable exception) {
					exception.printStackTrace();
				}
			}
		};
	}
}