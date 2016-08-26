package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

public class PropertyEnumerationRequestPacket extends AbstractMutableDataPacket {
	public PropertyEnumerationRequestPacket (ByteBuffer buf) {
		super(buf);
		this.setTypeCode(PacketTypeCode.PROPERTY_ENUMERATION_REQUEST);
	}
	@Override
	public int getLength() {
		// TODO Auto-generated method stub
		return 0;
	}
	//No properties
}
