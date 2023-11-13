package com.mrboomdev.scrollix.data.settings;

import java.util.List;

public abstract class SettingsItem<T> {
	private T value;

	@SuppressWarnings("unchecked")
	public <W extends SettingsItem<?>> W as(W w) {
		return (W)this;
	}

	public String getTitle() {
		return null;
	}

	public List<SettingsItem<?>> getItems() {
		return null;
	}

	public String getDescription() {
		return null;
	}

	public abstract Type getType();

	public void setValue(T value) {
		this.value = value;
	}

	public T getValue() {
		return value;
	}

	public enum Type {
		STRING,
		BOOLEAN,
		INTEGER,
		LIST,
		MENU,
		BUTTON,
		SCREEN
	}

	public static class StringSetting extends SettingsItem<String> {

		public StringSetting(String title, String description, String defaultValue) {
			setValue(defaultValue);
		}

		@Override
		public Type getType() {
			return Type.STRING;
		}
	}

	public static class BooleanSetting extends SettingsItem<Boolean> {

		public BooleanSetting(String title, String description, Boolean defaultValue) {
			setValue(defaultValue);
		}

		@Override
		public Type getType() {
			return Type.BOOLEAN;
		}
	}

	public static class IntegerSetting extends SettingsItem<Integer> {

		public IntegerSetting(String title, String description, Integer defaultValue) {
			setValue(defaultValue);
		}

		@Override
		public Type getType() {
			return Type.INTEGER;
		}
	}
}