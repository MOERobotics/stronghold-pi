package com.moe365.mopi.net.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.WriteCallback;

import com.moe365.mopi.net.channel.DataChannel;
import com.moe365.mopi.net.channel.DataChannelClient;
import com.moe365.mopi.net.channel.DataChannelDirection;
import com.moe365.mopi.net.channel.DataChannelMediaType;
import com.moe365.mopi.net.channel.UnsubscriptionReason;
import com.moe365.mopi.net.impl.ResponseHandlerManager.ResponseHandler;
import com.moe365.mopi.net.packet.DataPacket;
import com.moe365.mopi.net.packet.MutableDataPacket;
import com.moe365.mopi.net.packet.MutableWrappingDataPacket;

/**
 * An implementation of {@link com.moe365.moepi.net.channel.DataChannel DataChannel}
 * for the WebSocket protocol.
 * @author mailmindlin
 */
public class WsDataChannel implements DataChannel {
	protected WsDataSource source;
	protected int id;
	protected String name;
	protected DataChannelMediaType mediaType;
	protected DataChannelDirection direction;
	protected Set<Consumer<DataChannelClient>> subscriptionHandlers;
	protected Set<BiConsumer<DataChannelClient, UnsubscriptionReason>> unsubscriptionHandlers;
	protected Set<BiConsumer<DataPacket, DataChannelClient>> packetRecievedHandlers;
	protected Set<WsDataChannelClient> subscribers;

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public DataChannelMediaType getType() {
		return this.mediaType;
	}

	@Override
	public DataChannelDirection getDirection() {
		return this.direction;
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

	protected CompletableFuture<Void> doSendPacket(DataPacket packet, RemoteEndpoint e) {
		CompletableFuture<Void> result = new CompletableFuture<>();
		e.sendBytes(packet.getBuffer(), new WriteCallback() {
			@Override
			public void writeFailed(Throwable t) {
				result.completeExceptionally(t);
			}

			@Override
			public void writeSuccess() {
				result.complete(null);
			}
		});
		return result;
	}

	@Override
	public CompletableFuture<Void> broadcastPacket(DataPacket packet) {
		ArrayList<CompletableFuture<Void>> result = new ArrayList<>(subscribers.size());
		DataPacket prepared = preparePacket(packet);
		// TODO fix for clients added/removed while iterating
		for (WsDataChannelClient client : subscribers)
			result.add(doSendPacket(prepared, client.getSession().getRemote()));
		return CompletableFuture.allOf(result.toArray(new CompletableFuture[result.size()]));
	}

	@Override
	public CompletableFuture<Void> sendPacket(DataPacket packet, DataChannelClient target) {
		return doSendPacket(preparePacket(packet), ((WsDataChannelClient)target).getSession().getRemote());
	}

	@Override
	public CompletableFuture<Void> broadcastPacketExpectResponse(DataPacket packet,
			BiConsumer<DataPacket, DataChannelClient> responseHandler, Duration timeout) {
		ArrayList<CompletableFuture<DataPacket>> result = new ArrayList<>();
		DataPacket prepared = preparePacket(packet);
		
		for (WsDataChannelClient subscriber : subscribers) {
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
		doSendPacket(prepared, ((WsDataChannelClient)target).getSession().getRemote());
		return result;
	}

	@Override
	public void addSubscriptionHandler(Consumer<DataChannelClient> handler) {
		this.subscriptionHandlers.add(handler);
	}

	@Override
	public boolean removeSubscriptionHandler(Consumer<DataChannelClient> handler) {
		return this.subscriptionHandlers.remove(handler);
	}

	@Override
	public void addUnsubscriptionHandler(BiConsumer<DataChannelClient, UnsubscriptionReason> handler) {
		this.unsubscriptionHandlers.add(handler);
	}

	@Override
	public boolean removeUnsubscriptionHandler(BiConsumer<DataChannelClient, UnsubscriptionReason> handler) {
		return this.unsubscriptionHandlers.remove(handler);
	}

	@Override
	public void addRecievePacketHandler(BiConsumer<DataPacket, DataChannelClient> handler) {
		this.packetRecievedHandlers.add(handler);
	}

	@Override
	public boolean removeRecievePacketHandler(BiConsumer<DataPacket, DataChannelClient> handler) {
		return this.packetRecievedHandlers.remove(handler);
	}

	@Override
	public void unsubscribe(DataChannelClient subscriber) {
		// TODO Auto-generated method stub
		
	}
	
	protected void handlePacketRecieve(DataPacket packet, DataChannelClient client) {
		//TODO finish
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
