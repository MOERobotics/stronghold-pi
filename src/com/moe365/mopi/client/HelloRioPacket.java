package com.moe365.mopi.client;
import java.nio.ByteBuffer;

public class HelloRioPacket implements RioPacket {
	@Override
	public int getStatus() {
		return RioClient.STATUS_HELLO_WORLD;
	}
	
	@Override
	public int getLength() {
		return 0;
	}
	
	@Override
	public void writeTo(ByteBuffer buffer) {
		//Nop
	}
}