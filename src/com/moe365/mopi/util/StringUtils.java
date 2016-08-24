package com.moe365.mopi.util;

public class StringUtils {
	public static final char[] hexChars = "0123456789ABCDEF".toCharArray();
	public static String toHexString(byte[] buf, int offset, int length, int width) {
		StringBuilder sb = new StringBuilder(length * 2 + length/width + 1);
		for (int i = 0; i < length; i++) {
			byte b = buf[offset + i];
			sb.append(hexChars[b>>>4]).append(hexChars[b&0x0F]).append(' ');
			if (i % width == width - 1)
				sb.append('\n');
		}
		return sb.toString();
	}
}
