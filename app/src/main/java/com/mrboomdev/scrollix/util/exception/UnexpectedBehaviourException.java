package com.mrboomdev.scrollix.util.exception;

public class UnexpectedBehaviourException extends RuntimeException {

	public UnexpectedBehaviourException(String text) {
		super(text);
	}

	public UnexpectedBehaviourException(Exception e) {
		super(e);
	}

	public UnexpectedBehaviourException(String text, Exception e) {
		super(text, e);
	}
}