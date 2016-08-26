package com.moe365.mopi.net.channel;

import java.util.concurrent.CompletableFuture;

import com.moe365.mopi.net.packet.DataPacket;

public interface DataChannelClient {
	public CompletableFuture<Void> write(DataPacket packet);
}
