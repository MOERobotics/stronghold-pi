package com.moe365.mopi.net.impl;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import com.moe365.mopi.net.channel.DataChannelClient;
import com.moe365.mopi.net.channel.DataChannelDirection;
import com.moe365.mopi.net.channel.DataChannelMediaType;
import com.moe365.mopi.net.impl.WsDataSource.WsClient;
import com.moe365.mopi.net.packet.AckPacket;
import com.moe365.mopi.net.packet.DataPacket;

public class RandomlyBroadcastingChannel extends AbstractWsDataChannel implements Runnable {
	public RandomlyBroadcastingChannel(WsDataSource source) {
		this.id = 500;
		this.source = source;
		this.subscribers = ConcurrentHashMap.newKeySet();
	}
	@Override
	public DataPacket parseNext(ByteBuffer buf, int typeCode) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean onSubscription(DataChannelClient client) {
		this.subscribers.add((WsClient) client);
		return true;
	}

	@Override
	protected void onRecievePacket(DataPacket packet, DataChannelClient client) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void run() {
		DataPacket packet = new AckPacket(1234);
		while(!Thread.interrupted()) {
			this.broadcastPacket(packet);
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	@Override
	public DataChannelMediaType getType() {
		return DataChannelMediaType.OBJECT_STREAM;
	}
	@Override
	public DataChannelDirection getDirection() {
		return DataChannelDirection.CLIENT_TO_SERVER;
	}

}
