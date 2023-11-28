package com.mrboomdev.scrollix.util.exception;

public class DownloadException extends Exception {
	private int responseCode = -1;
	private String responseMessage;

	public DownloadException(Exception e) {
		super(e);
	}

	public DownloadException(String text, Exception e) {
		super(text, e);
	}

	public DownloadException(String text) {
		super(text);
	}

	public void setResponseCode(int code) {
		this.responseCode = code;
	}

	public int getResponseCode() {
		return responseCode;
	}

	public void setResponseMessage(String message) {
		this.responseMessage = message;
	}

	public String getResponseMessage() {
		return responseMessage;
	}
}