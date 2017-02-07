package com.moe365.mopi.client;
import java.nio.ByteBuffer;

public interface RioPacket {
	/**
	 * Get the status code for this packet
	 */
	int getStatus();
	/**
	 * Get the length (in bytes) of the payload
	 */
	int getLength();
	/**
	 * Write payload to buffer
	 */
	void writeTo(ByteBuffer buffer);
}