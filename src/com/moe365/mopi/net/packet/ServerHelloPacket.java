package com.moe365.mopi.net.packet;

@PacketTypeCode(PacketTypeCode.SERVER_HELLO)
public interface ServerHelloPacket extends DataPacket {
	int getProtocolVersion();
}
