package com.moe365.mopi.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractRioClient implements RioClient {
	/**
	 * Packet number. This number is to allow the client to ignore packets that
	 * are received out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
	
	@Override
	public void broadcast(RioPacket packet) throws IOException {
		//TODO finish
		throw new UnsupportedOperationException();
	}
	
	protected abstract void send(ByteBuffer buffer) throws IOException;
}