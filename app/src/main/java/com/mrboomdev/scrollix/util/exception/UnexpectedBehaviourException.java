package com.mrboomdev.scrollix.util.exception;

public class UnexpectedBehaviourException extends RuntimeException {

	public UnexpectedBehaviourException(String text) {
		super(text);
	}

	public UnexpectedBehaviourException(Throwable e) {
		super(e);
	}

	public UnexpectedBehaviourException(String text, Throwable e) {
		super(text, e);
	}
}