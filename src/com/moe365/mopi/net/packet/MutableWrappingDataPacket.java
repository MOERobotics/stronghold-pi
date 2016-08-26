package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

public class MutableWrappingDataPacket extends AbstractWrappedDataPacket implements MutableDataPacket {
	public MutableWrappingDataPacket(DataPacket origin) {
		super(origin.writeTo(ByteBuffer.allocate(origin.getLength())));
	}
	public MutableWrappingDataPacket(ByteBuffer buf) {
		super(buf);
	}
	@Override
	public MutableWrappingDataPacket setId(int id) {
		super.buf.putInt(DataPacket.CHANNEL_ID_OFFSET, id);
		return this;
	}
	@Override
	public MutableWrappingDataPacket setAckId(int ack) {
		super.buf.putInt(DataPacket.ACK_PACKET_ID_OFFSET, ack);
		return this;
	}
	@Override
	public MutableWrappingDataPacket setChannelId(int channel) {
		super.buf.putShort(DataPacket.CHANNEL_ID_OFFSET, (short) channel);
		return this;
	}
	@Override
	public MutableWrappingDataPacket setTypeCode(int type) {
		super.buf.putShort(DataPacket.TYPE_CODE_OFFSET, (short) type);
		return this;
	}
}
