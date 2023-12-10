package com.mrboomdev.scrollix.util.callback;

public interface ValueReturner2<T, V, W> {

	T get(V value, W secondValue);
}