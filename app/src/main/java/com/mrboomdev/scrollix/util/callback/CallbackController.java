package com.mrboomdev.scrollix.util.callback;

public abstract class CallbackController {
	private boolean isCanceled;

	public boolean isCanceled() {
		return isCanceled;
	}

	public void cancel() {
		isCanceled = true;
	}
}