package com.moe365.mopi.util;

public interface ExceptionalBiFunction<T, U, E extends Exception, R> {
	R apply(T t, U u) throws E;
}
