package com.moe365.mopi.net.packet;

import com.moe365.mopi.net.exception.ErrorCode;
@PacketTypeCode(PacketTypeCode.ERROR)
public interface ErrorPacket extends DataPacket {
	ErrorCode getCode();
	String getMessage();
	public static class MutableErrorPacket extends AbstractMutableDataPacket implements ErrorPacket {

		public MutableErrorPacket() {
			
		}
		public MutableErrorPacket(ErrorCode code, String message) {
			
		}
		@Override
		public int getLength() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public ErrorCode getCode() {
			// TODO Auto-generated method stub
			return null;
		}
		
		public MutableErrorPacket setCode(ErrorCode code) {
			//TODO finish
			return this;
		}

		@Override
		public String getMessage() {
			// TODO Auto-generated method stub
			return null;
		}
		
		public MutableErrorPacket setMessage(String message) {
			//TODO finish
			return this;
		}
		
	}
}
