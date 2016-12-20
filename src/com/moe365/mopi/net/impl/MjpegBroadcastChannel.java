package com.moe365.mopi.net.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.moe365.mopi.net.channel.DataChannelClient;
import com.moe365.mopi.net.channel.DataChannelDirection;
import com.moe365.mopi.net.channel.DataChannelMediaType;
import com.moe365.mopi.net.packet.DataPacket;
import com.moe365.mopi.net.packet.StreamFramePacket;

import au.edu.jcu.v4l4j.VideoFrame;

public class MjpegBroadcastChannel extends AbstractWsDataChannel implements Runnable {

	protected final int STATUS_EMPTY = 0;
	protected final int STATUS_FILLED = 1;
	protected final int STATUS_READING = 2;
	protected final int STATUS_WRITING = 3;
	
	protected final AtomicInteger imageStatus = new AtomicInteger(0);
	
	protected final ByteBuffer imageBuffer = ByteBuffer.allocate(100 * 1024);
	
	public MjpegBroadcastChannel(WsDataSource source, int id, String name) {
		this.id = id;
		this.source = source;
		this.name = name;
		this.subscribers = ConcurrentHashMap.newKeySet();
		metadata.put("name", this.name);
		metadata.put("video.format", "MJPEG");
		metadata.put("video.width", "100");
		metadata.put("video.height", "100");
	}
	
	@Override
	public DataChannelMediaType getType() {
		return DataChannelMediaType.VIDEO;
	}

	@Override
	public DataChannelDirection getDirection() {
		return DataChannelDirection.SERVER_TO_CLIENT;
	}

	@Override
	public DataPacket parseNext(ByteBuffer buf, int typeCode) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void run() {
		DataPacket imagePacket = new StreamFramePacket(imageBuffer);
		while (!Thread.interrupted()) {
			try {
				imageBuffer.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
				continue;
			}
			if (imageStatus.compareAndSet(STATUS_FILLED, STATUS_READING)) {
				this.broadcastPacket(imagePacket);
				imageStatus.set(STATUS_EMPTY);
			}
		}
	}
	
	public void offerFrame(VideoFrame frame) {
		if (imageStatus.compareAndSet(STATUS_EMPTY, STATUS_WRITING)) {
			imageBuffer.clear();
			imageBuffer.put(frame.getBuffer());
			imageBuffer.flip();
			imageStatus.set(STATUS_FILLED);
		}
	}

	@Override
	protected void onRecievePacket(DataPacket packet, DataChannelClient client) {
		// TODO Auto-generated method stub
		
	}
	
}
