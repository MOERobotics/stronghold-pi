package com.moe365.mopi.net.packet;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

/**
 * A packet that is sent in response to the SERVER_HELLO packet.
 * @author mailmindlin
 */
@PacketTypeCode(PacketTypeCode.CLIENT_HELLO)
public interface ClientHelloPacket extends DataPacket {
	int getProtocolVersion();

	public static class ClientHelloWrappedPacket extends AbstractWrappedDataPacket implements ClientHelloPacket {
		ClientHelloWrappedPacket() {
			super();
		}

		public ClientHelloWrappedPacket(ByteBuffer buf) {
			super(buf);
		}

		@Override
		public int getProtocolVersion() {
			return getBuffer().getInt(DataPacket.DATA_OFFSET);
		}
	}

	public static class ClientHelloMutablePacket extends AbstractMutableDataPacket implements ClientHelloPacket {
		int protocolVersion;

		public ClientHelloMutablePacket() {
			super();
		}

		public ClientHelloMutablePacket(int protocolVersion) {
			this.protocolVersion = protocolVersion;
		}

		@Override
		public int getLength() {
			return DataPacket.HEADER_LENGTH + 4;
		}
		
		public ClientHelloMutablePacket setProtocolVersion(int version) {
			this.protocolVersion = version;
			return this;
		}

		@Override
		public int getProtocolVersion() {
			return protocolVersion;
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			super.readExternal(in);
			this.protocolVersion = in.readInt();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			super.writeExternal(out);
			out.writeInt(getProtocolVersion());
		}
		
		@Override
		public ByteBuffer getBuffer() {
			ByteBuffer result = super.getBuffer();
			result.putInt(DataPacket.DATA_OFFSET, this.protocolVersion);
			return result;
		}
	}
}
