package com.moe365.mopi.net.packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 
 * @author mailmindlin
 */
public class PropertyEnumerationPacket extends AbstractMutableDataPacket {
	PropertySignature[] signatures;
	
	public PropertyEnumerationPacket() {
		this.setTypeCode(PacketTypeCode.PROPERTY_ENUMERATION);
	}
	public PropertyEnumerationPacket(PropertySignature...signatures) {
		this();
		this.signatures = signatures;
	}
	public PropertyEnumerationPacket(ByteBuffer buf) {
		super(buf);
		//TODO parse
		final int length = buf.getShort() & 0xFF_FF;
		
		this.setTypeCode(PacketTypeCode.PROPERTY_ENUMERATION);
	}

	public int getPropertiesCount() {
		return signatures.length;
	}

	public PropertySignature[] getProperties() {
		return signatures;
	}


	@Override
	public int getLength() {
		return DataPacket.HEADER_LENGTH + 2 + signatures.length * 4;//TODO support names
	}
	
	@Override
	public ByteBuffer writeTo(ByteBuffer buf) {
		super.writeTo(buf);
		buf.putShort((short) signatures.length);
		for (int i = 0; i < signatures.length; i++) {
			PropertySignature signature = signatures[i];
			buf.putShort((short) signature.id)
				.put((byte)signature.getType().ordinal())
				.put((byte)signature.getName().length());
			//TODO write strings
		}
		return buf;
	}

	public static class PropertySignature {
		int id = -1;
		PropertyType type;
		String name;

		PropertySignature(int id, PropertyType type, String name) {
			this.id = id;
			this.type = type;
			if (name.getBytes(StandardCharsets.UTF_8).length > 255)
				System.out.println("Invalid name length. All property names must be <255 bytes when UTF-8 encoded");
			this.name = name;
		}

		public int getId() {
			return id;
		}

		public PropertyType getType() {
			return type;
		}

		public String getName() {
			return name;
		}
	}
}
