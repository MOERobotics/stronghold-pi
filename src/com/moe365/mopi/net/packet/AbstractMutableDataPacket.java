package com.moe365.mopi.net.packet;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

public abstract class AbstractMutableDataPacket implements MutableDataPacket {
	protected int id;
	protected int channelId;
	protected int typeCode;
	protected int ackId;

	public AbstractMutableDataPacket() {

	}

	public AbstractMutableDataPacket(ByteBuffer buf) {
		this.typeCode = buf.getShort() & 0xFF_FF;
		this.channelId = buf.getShort() & 0xFF_FF;
		this.id = buf.getInt();
		this.ackId = buf.getInt();
	}

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
	public AbstractMutableDataPacket setId(int id) {
		this.id = id;
		return this;
	}

	@Override
	public AbstractMutableDataPacket setAckId(int ack) {
		this.ackId = ack;
		return this;
	}

	@Override
	public AbstractMutableDataPacket setChannelId(int channel) {
		this.channelId = channel;
		return this;
	}

	@Override
	public AbstractMutableDataPacket setTypeCode(int type) {
		this.typeCode = type;
		return this;
	}

	@Override
	public ByteBuffer writeTo(ByteBuffer buf) {
		buf.putShort((short) getTypeCode());
		buf.putShort((short) getChannelId());
		buf.putInt(getId());
		buf.putInt(getAckId());
		return buf;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeShort((short) getTypeCode());
		out.writeShort((short) getChannelId());
		out.writeInt(getId());
		out.writeInt(getAckId());
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.typeCode = in.readUnsignedShort();
		this.channelId = in.readUnsignedShort();
		this.id = in.readInt();
		this.ackId = in.readInt();
	}
}
