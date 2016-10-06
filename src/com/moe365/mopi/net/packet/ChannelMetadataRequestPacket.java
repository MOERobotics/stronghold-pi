package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

public class ChannelMetadataRequestPacket extends AbstractMutableDataPacket {
	int targetChannelId;
	public ChannelMetadataRequestPacket() {
		super();
	}
	public ChannelMetadataRequestPacket(int targetChannelId) {
		this();
		this.targetChannelId = targetChannelId;
	}
	public ChannelMetadataRequestPacket(ByteBuffer buf) {
		super(buf);
		this.targetChannelId = buf.getShort();
	}
	
	public int getTargetChannelId() {
		return targetChannelId;
	}
	public ChannelMetadataRequestPacket setTargetChannelId(int targetChannelId) {
		this.targetChannelId = targetChannelId;
		return this;
	}
	@Override
	public int getLength() {
		return DataPacket.HEADER_LENGTH + 2;
	}
	
	@Override
	public ByteBuffer writeTo(ByteBuffer buf) {
		return super.writeTo(buf)
			.putInt(this.targetChannelId);
	}

}
