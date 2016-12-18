package com.moe365.mopi.util;

public class NumberUtils {
	public static final boolean sortaEqual(double a, double b) {
		return a == b || (a != a && b != b) || Math.abs(a - b) < 0.000000001;
	}
}
