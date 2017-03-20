package com.moe365.mopi.client;
import java.nio.ByteBuffer;

public interface RioPacket {
	
	/**
	 * Denotes a packet that should be ignored. No idea why we would need to use
	 * this, though.
	 */
	public static final short STATUS_NOP = 0;
	/**
	 * Denotes a packet telling the Rio that no target(s) were found.
	 */
	public static final short STATUS_NONE_FOUND = 1;
	/**
	 * Denotes a packet telling the Rio that one target has been detected. The
	 * position data MUST be included in the packet.
	 */
	public static final short STATUS_ONE_FOUND = 2;
	/**
	 * Denotes a packet telling the Rio that two or more targets have been
	 * found. The position data of the two largest targets found (by area) MUST
	 * be included in the packet.
	 */
	public static final short STATUS_TWO_FOUND = 3;
	//Statuses >= 0x8000 are special metadata things, and shouldn't be discarded, ever
	/**
	 * A packet that contains an error message
	 */
	public static final short STATUS_ERROR = (short) 0x8000;
	/**
	 * A packet that notifies the reciever that the sender has just connected.
	 * If this packet is recieved, the reciever should reset its last-recieved packet id
	 * to the id of this packet.
	 */
	public static final short STATUS_HELLO_WORLD = (short) 0x8001;
	/**
	 * Signals that the sender is terminating in an expected manner.
	 */
	public static final short STATUS_GOODBYE = (short) 0x8002;
	
	/**
	 * Get the status code for this packet
	 */
	int getStatus();
	/**
	 * Get the length (in bytes) of the payload
	 */
	int getLength();
	/**
	 * Write payload to buffer
	 */
	void writeTo(ByteBuffer buffer);
}