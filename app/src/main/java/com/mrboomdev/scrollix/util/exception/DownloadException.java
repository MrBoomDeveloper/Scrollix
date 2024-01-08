package com.mrboomdev.scrollix.util.exception;

public class DownloadException extends Exception {
	private int responseCode = -1;
	private String responseMessage;
	private final boolean isCancelled;

	public DownloadException(String text, Exception e, boolean isCancelled) {
		super(text, e);
		this.isCancelled = isCancelled;
	}

	public DownloadException(Exception e, boolean isCancelled) {
		this(null, e, isCancelled);
	}

	public DownloadException(Exception e) {
		this(e, false);
	}

	public DownloadException(String text, Exception e) {
		this(text, e, false);
	}

	public DownloadException(String text, boolean isCancelled) {
		this(text, null, isCancelled);
	}

	public DownloadException(String text) {
		this(text, null, false);
	}

	public boolean isCancelled() {
		return isCancelled;
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