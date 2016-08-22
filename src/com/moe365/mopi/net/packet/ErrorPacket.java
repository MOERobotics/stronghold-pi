package com.moe365.mopi.net.packet;

import com.moe365.mopi.net.exception.ErrorCode;
@PacketTypeCode(PacketTypeCode.ERROR)
public interface ErrorPacket extends DataPacket {
	ErrorCode getErrorCode();
	String getErrorMessage();
}
