package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;

@PacketTypeCode(PacketTypeCode.PROPERTY_VALUES)
public class PropertyValuesPacket extends AbstractMutableDataPacket {
	public PropertyValuesPacket(ByteBuffer buf) {
		super(buf);
		this.setTypeCode(PacketTypeCode.PROPERTY_VALUES);
	}
	@Override
	public int getLength() {
		// TODO Auto-generated method stub
		return 0;
	}
	//TODO finish
}
