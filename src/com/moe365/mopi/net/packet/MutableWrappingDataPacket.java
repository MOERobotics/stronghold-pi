package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

public class MutableWrappingDataPacket extends AbstractWrappedDataPacket implements MutableDataPacket {
	public MutableWrappingDataPacket(DataPacket origin) {
		this(origin.getBuffer());
	}
	public MutableWrappingDataPacket(ByteBuffer buf) {
		super(buf);
	}
	@Override
	public void setId(int id) {
		super.buf.putInt(DataPacket.CHANNEL_ID_OFFSET, id);
	}
	@Override
	public void setAckId(int ack) {
		super.buf.putInt(DataPacket.ACK_PACKET_ID_OFFSET, ack);
	}
	@Override
	public void setChannelId(int channel) {
		super.buf.putShort(DataPacket.CHANNEL_ID_OFFSET, (short) channel);
	}
	@Override
	public void setTypeCode(int type) {
		super.buf.putShort(DataPacket.TYPE_CODE_OFFSET, (short) type);
	}
}
