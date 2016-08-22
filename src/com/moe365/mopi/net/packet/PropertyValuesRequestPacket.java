package com.moe365.mopi.net.packet;

/**
 * Get the values of 1+ properties
 * @author mailmindlin
 */
@PacketTypeCode(PacketTypeCode.PROPERTY_VALUES_REQUEST)
public interface PropertyValuesRequestPacket extends DataPacket {
	public int[] getPropertyIds();
}
