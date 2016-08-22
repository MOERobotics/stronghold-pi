package com.moe365.mopi.net.packet;

/**
 * 
 * @author mailmindlin
 */
public interface PropertyEnumerationPacket extends DataPacket {
	int getNumProperties();
	PropertySignature[] getProperties();
	
	public static interface PropertySignature {
		int getId();
		PropertyType getType();
		String getName();
	}
	
	public class PropertyEnumerationWrappingPacket extends AbstractWrappedDataPacket implements PropertyEnumerationPacket {
		transient PropertySignature[] signatureCache = null;
		@Override
		public int getNumProperties() {
			return getBuffer().getShort(DataPacket.DATA_OFFSET + 0);
		}

		@Override
		public PropertySignature[] getProperties() {
			if (signatureCache != null)
				return signatureCache;
			PropertySignature[] tmp = new PropertySignature[getNumProperties()];
			for (int i = 0; i < getNumProperties(); i++)
				tmp[i] = new DeferringPropertySignature(i);
			return signatureCache = tmp;
		}
		
		/**
		 * A PropertySignature that gets and stores properties as they are called
		 * @author mailmindlin
		 */
		public class DeferringPropertySignature implements PropertySignature {
			final int offset;
			transient int id = -1;
			transient PropertyType type;
			transient String name;
			DeferringPropertySignature(int offset) {
				this.offset = offset;
			}
			@Override
			public int getId() {
				if (id >= 0)
					return id;
				// TODO Auto-generated method stub
				return 0;
			}
			@Override
			public PropertyType getType() {
				if (type != null)
					return type;
				// TODO Auto-generated method stub
				return null;
			}
			@Override
			public String getName() {
				if (name != null)
					return name;
				// TODO Auto-generated method stub
				return null;
			}
		}
	}
}
