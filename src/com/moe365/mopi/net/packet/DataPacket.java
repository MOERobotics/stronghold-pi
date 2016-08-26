package com.moe365.mopi.net.packet;

import java.io.Externalizable;
import java.nio.ByteBuffer;

/**
 * Packet structure is as follows:
 * <pre>
 * 0  - 3:	Length of packet
 * 4  - 7:	Packet ID
 * 8  - 11:	Ack Packet ID (-1 if not set)
 * 12 - 13:	Resource ID
 * 14 - 15:	Type Code
 * </pre>
 * @author mailmindlin
 */
public interface DataPacket extends Externalizable {
	public static final int LENGTH_OFFSET = 0;
	public static final int PACKET_ID_OFFSET = 4;
	public static final int ACK_PACKET_ID_OFFSET = 8;
	public static final int CHANNEL_ID_OFFSET = 12;
	public static final int TYPE_CODE_OFFSET = 14;
	public static final int DATA_OFFSET = 16;
	public static final int HEADER_LENGTH = 16;
	int getLength();
	int getId();
	int getAckId();
	int getChannelId();
	int getTypeCode();
	ByteBuffer writeTo(ByteBuffer buf);
}
