package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

@PacketTypeCode(PacketTypeCode.CHANNEL_ENUMERATION_REQUEST)
public interface ChannelEnumerationRequestPacket extends DataPacket {
	public static class ChannelEnumerationRequestWrappedPacket extends AbstractWrappedDataPacket implements ChannelEnumerationRequestPacket {
		ChannelEnumerationRequestWrappedPacket() {
			super();
		}
		public ChannelEnumerationRequestWrappedPacket(ByteBuffer buf) {
			super(buf);
		}
	}
	public static class ChannelEnumerationRequestMutablePacket extends AbstractMutableDataPacket implements ChannelEnumerationRequestPacket {

		@Override
		public int getLength() {
			return DataPacket.HEADER_LENGTH;
		}
		
	}
}
