package com.moe365.mopi.client;

public abstract class AbstractRioClient implements RioClient {
	/**
	 * Packet number. This number is to allow the client to ignore packets that
	 * are received out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
}