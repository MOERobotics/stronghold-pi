package com.moe365.mopi.net.packet;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

public abstract class AbstractMutableDataPacket implements MutableDataPacket {
	protected int id;
	protected int ackId;
	protected int channelId;
	protected int typeCode;
	
	@Override
	public int getId() {
		return id;
	}
	
	@Override
	public int getAckId() {
		return ackId;
	}

	@Override
	public int getChannelId() {
		return channelId;
	}

	@Override
	public int getTypeCode() {
		return typeCode;
	}
	
	@Override
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public void setAckId(int ack) {
		this.ackId = ack;
	}

	@Override
	public void setChannelId(int channel) {
		this.channelId = channel;
	}

	@Override
	public void setTypeCode(int type) {
		this.typeCode = type;
	}

	@Override
	public ByteBuffer getBuffer() {
		ByteBuffer buf = ByteBuffer.allocate(getLength());
		buf.putInt(DataPacket.LENGTH_OFFSET, getLength());
		buf.putInt(DataPacket.PACKET_ID_OFFSET, getId());
		buf.putInt(DataPacket.ACK_PACKET_ID_OFFSET, getAckId());
		buf.putShort(DataPacket.CHANNEL_ID_OFFSET, (short)getChannelId());
		buf.putShort(DataPacket.TYPE_CODE_OFFSET, (short)getTypeCode());
		return buf;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(getId());
		out.writeInt(getAckId());
		out.writeShort((short)getChannelId());
		out.writeShort((short)getTypeCode());
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.id = in.readInt();
		this.ackId = in.readInt();
		this.channelId = in.readUnsignedShort();
		this.typeCode = in.readUnsignedShort();
	}
}
