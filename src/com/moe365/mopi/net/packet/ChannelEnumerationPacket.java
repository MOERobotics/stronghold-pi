package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

public class ChannelEnumerationPacket extends AbstractMutableDataPacket {

	public ChannelEnumerationPacket(ByteBuffer buf) {
		super(buf);
		this.setTypeCode(PacketTypeCode.CHANNEL_ENUMERATION);
	}
	@Override
	public int getLength() {
		return DataPacket.HEADER_LENGTH;
	}

}
