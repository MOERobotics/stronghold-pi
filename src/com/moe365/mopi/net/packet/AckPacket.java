package com.moe365.mopi.net.packet;
/**
 * <p>
 * A packet that acknowledges that another has recieved,
 * explicitly declaring that no error has occurred as a result
 * of any action invoked by the previous packet.
 * </p>
 * No <var>ACK</var> packet should be used to reply to an ACK packet.
 * @author mailmindlin
 */
@PacketTypeCode(PacketTypeCode.ACK)
public interface AckPacket extends DataPacket {
	public static class AckWrappedPacket extends AbstractWrappedDataPacket implements AckPacket {
		
	}
	public static class AckMutablePacket extends AbstractMutableDataPacket implements AckPacket {
		@Override
		public int getLength() {
			return DataPacket.HEADER_LENGTH;
		}
	}
}
