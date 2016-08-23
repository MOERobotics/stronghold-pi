package com.moe365.mopi.net.channel;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.moe365.mopi.net.packet.DataPacket;

/**
 * A specific resource that is available from a DataSource.
 * 
 * @author mailmindlin
 */
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
	 * Broadcast a packet to all connected clients, without expecting a return
	 * packet.
	 * 
	 * @param packet
	 *            Packet to broadcast
	 * @return future of when the packet has been written to all clients
	 */
	CompletableFuture<Void> broadcastPacket(DataPacket packet);
	// TODO add selective broadcast methods (e.g., broadcastPacketIf; needs
	// better name)

	/**
	 * Send a packet to a specific client, without expecting a return packet.
	 * 
	 * @param packet
	 *            Packet to send
	 * @param target
	 *            Client to send packet to
	 * @return future of when the packet has been written
	 */
	CompletableFuture<Void> sendPacket(DataPacket packet, DataChannelClient target);

	/**
	 * Broadcast a packet to all connected clients, waiting for <code>ACK</code>
	 * packets in response.
	 * 
	 * @param packet
	 *            Packet to broadcast
	 * @param responseHandler
	 *            Handler for responses
	 * @param timeout
	 *            Timeout for response handler to remain active TODO decide when
	 *            the future should actually complete (maybe standardize for
	 *            compat. with
	 *            {@link #sendPacketExpectResponse(DataPacket, DataChannelClient, Duration)})
	 * @return future of when the packet has been written
	 */
	CompletableFuture<Void> broadcastPacketExpectResponse(DataPacket packet, BiConsumer<DataPacket, DataChannelClient> responseHandler, Duration timeout);

	/**
	 * Send a packet, expecting a response. Returns success after the first
	 * acknowledgement packet, ignoring all subsequent ones with the same
	 * <code>ACK_ID</code> field.
	 * 
	 * @param packet
	 *            Packet to send
	 * @param target
	 *            Client to send packet to
	 * @param timeout
	 *            Timeout for the first
	 * @return
	 */
	<T extends DataPacket> CompletableFuture<T> sendPacketExpectResponse(DataPacket packet, DataChannelClient target, Duration timeout);

	/**
	 * Register a handler function for when a new client subscribes to this
	 * channel.
	 * 
	 * @param handler
	 *            Subscription handler
	 */
	void addSubscriptionHandler(Consumer<DataChannelClient> handler);

	/**
	 * Deregister a subscription handler function.
	 * 
	 * @param handler
	 *            Previously registered handler method
	 * @return Whether the handler was removed. If false, then the handler was
	 *         never registered with this channel previously; if true, the
	 *         handler was removed successfully. All other possible states
	 *         should be expressed by throwing an exception
	 */
	boolean removeSubscriptionHandler(Consumer<DataChannelClient> handler);

	/**
	 * Register a handler for when a client unsubscribes.
	 * 
	 * @param handler
	 *            Handler to register
	 */
	void addUnsubscriptionHandler(BiConsumer<DataChannelClient, UnsubscriptionReason> handler);

	/**
	 * Deregister a handler for when a client unsubscribes
	 * 
	 * @param handler
	 *            Handler to deregister
	 * @return
	 */
	boolean removeUnsubscriptionHandler(BiConsumer<DataChannelClient, UnsubscriptionReason> handler);

	/**
	 * Register a handler to be triggered when a packet sent with this channel's
	 * id is received.
	 * 
	 * @param handler
	 *            Handler to register
	 */
	void addRecievePacketHandler(BiConsumer<DataPacket, DataChannelClient> handler);

	boolean removeRecievePacketHandler(BiConsumer<DataPacket, DataChannelClient> handler);

	/**
	 * Forcibly remove a client from this channel's subscriber list. Doing so
	 * immediately cancels all packets being sent to the client, possibly
	 * excluding broadcast operations in progress at the time of the method
	 * call. The client should be notified of this.
	 * 
	 * TODO whether an invocation of this method invokes unsubscription handlers
	 * is undefined
	 * 
	 * @param subscriber
	 */
	void unsubscribe(DataChannelClient subscriber);

	/**
	 * Whether this channel is currently open.
	 * 
	 * @return if it's open
	 */
	boolean isOpen();

	/**
	 * Close this channel. If this channel is already closed, no action should
	 * occur, and no exception should be thrown. All clients should be notified
	 * of their disconnection via <code>CHANNEL_CLOSE</code> packets, and it
	 * should be detrgistered from all DataSources.
	 */
	void close();
}
