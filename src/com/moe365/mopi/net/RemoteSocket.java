package com.moe365.mopi.net;

import com.moe365.mopi.net.packet.DataPacket;

public interface RemoteSocket {
	void sendPacket(DataPacket packet);
}
