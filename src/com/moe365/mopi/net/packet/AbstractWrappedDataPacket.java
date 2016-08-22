package com.moe365.mopi.net.packet;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public abstract class AbstractWrappedDataPacket implements DataPacket {
	ByteBuffer buf;
	/**
	 * For deserialization
	 */
	protected AbstractWrappedDataPacket() {
		
	}
	protected AbstractWrappedDataPacket(ByteBuffer buf) {
		this.buf = buf;
	}

	@Override
	public int getLength() {
		return getBuffer().getInt(DataPacket.LENGTH_OFFSET);
	}

	@Override
	public int getId() {
		return getBuffer().getInt(DataPacket.PACKET_ID_OFFSET);
	}

	@Override
	public int getAckId() {
		return getBuffer().getInt(DataPacket.ACK_PACKET_ID_OFFSET);
	}

	@Override
	public int getChannelId() {
		return getBuffer().getShort(DataPacket.CHANNEL_ID_OFFSET);
	}

	@Override
	public int getTypeCode() {
		return getBuffer().getShort(DataPacket.TYPE_CODE_OFFSET);
	}

	@Override
	public ByteBuffer getBuffer() {
		return buf;
	}

	protected String stringAt(int pos) {
		int len = getBuffer().get(pos);
		byte[] tmp = new byte[len];
		getBuffer().get(tmp, pos + 1, len);
		return new String(tmp, StandardCharsets.UTF_8);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		ByteBuffer buf = getBuffer();
		byte[] data;
		if (buf.hasArray()) {
			data = buf.array();
		} else {
			data = new byte[buf.limit()];
			buf.get(data, 0, data.length);
		}
		out.writeInt(data.length);
		out.write(data);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int len = in.readInt();
		byte[] tmp = new byte[len];
		in.read(tmp);
		buf = ByteBuffer.wrap(tmp);
	}
}
