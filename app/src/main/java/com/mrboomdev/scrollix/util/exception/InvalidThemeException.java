package com.mrboomdev.scrollix.util.exception;

public class InvalidThemeException extends RuntimeException {

	public InvalidThemeException(String text) {
		super(text);
	}

	public InvalidThemeException(Exception e) {
		super(e);
	}

	public InvalidThemeException(String text, Exception e) {
		super(text, e);
	}
}