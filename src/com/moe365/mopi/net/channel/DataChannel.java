package com.moe365.mopi.net.channel;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.moe365.mopi.net.packet.DataPacket;

public interface DataChannel {
	/**
	 * Get the id of this channel
	 * 
	 * @return
	 */
	int getId();

	/**
	 * Get the name of this channel. Channel names can be 0-255 bytes when UTF-8
	 * encoded.
	 * 
	 * @return
	 */
	String getName();

	/**
	 * Get the media type of this channel.
	 * 
	 * @return
	 */
	DataChannelMediaType getType();

	/**
	 * Get the data flow direction of this channel. The direction is more of a
	 * guideline, of where unique data is generated. For example, a property
	 * access channel that sets server properties would be marked as
	 * {@link DataChannelDirection#SERVER_TO_CLIENT SERVER_TO_CLIENT}, if it is
	 * intended to be mainly used to access properties, while it would be marked
	 * as {@link DataChannelDirection#CLIENT_TO_SERVER CLIENT_TO_SERVER} if it
	 * is intended to be mainly used to set properties.
	 * 
	 * @return direction of DataChannel
	 */
	DataChannelDirection getDirection();

	/**
	 * Get the current list of subscribers to this channel
	 * 
	 * @return
	 */
	Set<DataChannelClient> getSubscribers();

	/**
	 *Broadcast a packet to all connected clients, without expecting a return packet.
	 * @param packet
	 * @return
	 */
	CompletableFuture<Void> broadcastPacket(DataPacket packet);

	CompletableFuture<Void> sendPacket(DataPacket packet, DataChannelClient target);

	CompletableFuture<Void> broadcastPacketExpectResponse(DataPacket packet, BiConsumer<DataPacket, DataChannelClient> responseHandler, Duration timeout);
	
	<T extends DataPacket> CompletableFuture<T> sendPacketExpectResponse(DataPacket packet, DataChannelClient target, Duration timeout);

	void addSubscriptionHandler(Consumer<DataChannelClient> handler);

	boolean removeSubscriptionHandler(Consumer<DataChannelClient> handler);

	void addUnsubscriptionHandler(BiConsumer<DataChannelClient, UnsubscriptionReason> handler);

	boolean removeUnsubscriptionHandler(BiConsumer<DataChannelClient, UnsubscriptionReason> handler);

	void addRecievePacketHandler(BiConsumer<DataPacket, DataChannelClient> handler);

	boolean removeRecievePacketHandler(BiConsumer<DataPacket, DataChannelClient> handler);
}
