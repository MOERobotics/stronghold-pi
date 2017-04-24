package com.moe365.mopi.client;

import java.nio.ByteBuffer;

public class NoneFoundRioPacket implements RioPacket {

	@Override
	public int getStatus() {
		return RioPacket.STATUS_NONE_FOUND;
	}

	@Override
	public int getLength() {
		return 0;
	}

	@Override
	public void writeTo(ByteBuffer buffer) {
		
	}

}
