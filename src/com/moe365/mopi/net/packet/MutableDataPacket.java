package com.moe365.mopi.net.packet;

public interface MutableDataPacket extends DataPacket {
	void setId(int id);
	void setAckId(int ack);
	void setChannelId(int channel);
	void setTypeCode(int type);
}
