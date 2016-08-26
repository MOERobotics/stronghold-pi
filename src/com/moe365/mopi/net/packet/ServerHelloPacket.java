package com.moe365.mopi.net.packet;

@PacketTypeCode(PacketTypeCode.SERVER_HELLO)
public interface ServerHelloPacket extends DataPacket {
	int getProtocolVersion();
	@Override
	public ByteBuffer writeTo(ByteBuffer buf) {
		super.writeTo(buf);
		buf.putInt(this.getProtocolVersion());
		return buf;
	}
}
