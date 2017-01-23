package com.moe365.mopi.net.exception;

import com.moe365.mopi.net.packet.ErrorPacket;

public class DataPacketException extends RuntimeException {
	private static final long serialVersionUID = -5919608724175821450L;
	protected final ErrorCode code;
	public DataPacketException(ErrorCode code) {
		super("" + code);
		this.code = code;
	}
	public ErrorCode getCode() {
		return this.code;
	}
}
