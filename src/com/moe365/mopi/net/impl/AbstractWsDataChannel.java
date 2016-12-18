package com.moe365.mopi.net.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import com.moe365.mopi.net.channel.DataChannel;
import com.moe365.mopi.net.channel.DataChannelClient;
import com.moe365.mopi.net.channel.DataChannelDirection;
import com.moe365.mopi.net.channel.DataChannelMediaType;
import com.moe365.mopi.net.channel.UnsubscriptionReason;
import com.moe365.mopi.net.impl.ResponseHandlerManager.ResponseHandler;
import com.moe365.mopi.net.impl.WsDataSource.WsClient;
import com.moe365.mopi.net.packet.DataPacket;
import com.moe365.mopi.net.packet.MutableDataPacket;
import com.moe365.mopi.net.packet.MutableWrappingDataPacket;

/**
 * An implementation of {@link com.moe365.moepi.net.channel.DataChannel DataChannel}
 * for the WebSocket protocol.
 * @author mailmindlin
 */
public abstract class AbstractWsDataChannel implements DataChannel {
	protected WsDataSource source;
	protected int id;
	protected String name;
	protected Set<WsClient> subscribers;

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public String getName() {
		return this.name;
	}
	
	protected final void setId(int id) {
		this.id = id;
	}

	@Override
	public Set<DataChannelClient> getSubscribers() {
		return Collections.unmodifiableSet(subscribers);
	}

	/**
	 * Prepare a packet to be send by ensuring that the <var>id</var> and
	 * <var>channelId</var> fields are correct.
	 * 
	 * @param packet
	 * @return
	 */
	protected MutableDataPacket preparePacket(DataPacket packet) {
		MutableDataPacket mutablePacket;
		if (packet instanceof MutableDataPacket)
			mutablePacket = (MutableDataPacket) packet;
		else
			mutablePacket = new MutableWrappingDataPacket(packet);
		mutablePacket.setId(source.lastPacketId++);
		mutablePacket.setChannelId(this.getId());
		return mutablePacket;
	}

	@Override
	public CompletableFuture<Void> broadcastPacket(DataPacket packet) {
		ArrayList<CompletableFuture<Void>> result = new ArrayList<>(subscribers.size());
		DataPacket prepared = preparePacket(packet);
		// TODO fix for clients added/removed while iterating
		for (WsClient client : subscribers)
			result.add(client.write(packet));
		return CompletableFuture.allOf(result.toArray(new CompletableFuture[result.size()]));
	}

	@Override
	public CompletableFuture<Void> sendPacket(DataPacket packet, DataChannelClient target) {
		return ((WsClient)target).write(preparePacket(packet));
	}

	@Override
	public CompletableFuture<Void> broadcastPacketExpectResponse(DataPacket packet,
			BiConsumer<DataPacket, DataChannelClient> responseHandler, Duration timeout) {
		ArrayList<CompletableFuture<DataPacket>> result = new ArrayList<>();
		DataPacket prepared = preparePacket(packet);
		
		for (WsClient subscriber : subscribers) {
			// TODO finish
		}
		
		//TODO *when* exactly should the result of this method be completed?
		//After writing to all the endpoints? After recieving the first response?
		//After all the responses?
		//Should it fail if timed out before any of the endpoints responded?
		return CompletableFuture.allOf(result.toArray(new CompletableFuture[result.size()]));
	}

	@Override
	@SuppressWarnings("unchecked")
	public CompletableFuture<? extends DataPacket> sendPacketExpectResponse(DataPacket packet, DataChannelClient target,
			Duration timeout) {
		DataPacket prepared = preparePacket(packet);
		final CompletableFuture<DataPacket> result = new CompletableFuture<>();
		this.source.responseHandlerManager
				.addHandler(ResponseHandler.with(packet.getId(), Instant.now().plus(timeout), true,
						result::complete, () -> result.completeExceptionally(new TimeoutException())));
		((WsClient)target).write(prepared);
		return result;
	}
	
	//TODO rename
	public void unsubscribe(DataChannelClient subscriber) {
		// TODO Auto-generated method stub

	}

	
	protected boolean onSubscription(DataChannelClient client) {
		this.subscribers.add((WsClient)client);
		return true;
	}
	protected abstract void onRecievePacket(DataPacket packet, DataChannelClient client);
	protected void onUnsubscription(DataChannelClient client, UnsubscriptionReason reason) {
		this.subscribers.remove(client);
	}
	
	protected boolean isSubscriber(DataChannelClient client) {
		return this.subscribers.contains(client);
	}

	@Override
	public boolean isOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

}
