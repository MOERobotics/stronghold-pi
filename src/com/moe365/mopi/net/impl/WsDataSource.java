package com.moe365.mopi.net.impl;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import com.moe365.mopi.net.channel.DataChannel;
import com.moe365.mopi.net.channel.DataChannelClient;
import com.moe365.mopi.net.channel.DataSource;
import com.moe365.mopi.net.exception.ErrorCode;
import com.moe365.mopi.net.packet.DataPacket;
import com.moe365.mopi.net.packet.ErrorPacket.MutableErrorPacket;
import com.moe365.mopi.util.StringUtils;

import android.util.SparseArray;

public class WsDataSource extends WebSocketServlet implements DataSource {
	private static final long serialVersionUID = -902434272219432543L;
	/**
	 * Last id for a packet sent from the server.
	 */
	protected volatile int lastPacketId = 0;
	protected ResponseHandlerManager responseHandlerManager = new ResponseHandlerManager();
	protected SparseArray<AbstractWsDataChannel> channels = new SparseArray<>();
	protected SparseArray<Function<ByteBuffer, DataPacket>> packetBuilders = new SparseArray<>();
	protected ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
		volatile int threadId = 0;

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("DataSource-" + threadId++);
			return t;
		}
	});
	public WsDataSource() {
		this.channels.put(0, new MetaChannel());
		//Register constructors
		packetBuilders.put(PacketTypeCode.SERVER_HELLO, ServerHelloPacket::new);
		packetBuilders.put(PacketTypeCode.CLIENT_HELLO, ClientHelloPacket::new);
		packetBuilders.put(PacketTypeCode.ERROR, ErrorPacket.MutableErrorPacket::new);
		packetBuilders.put(PacketTypeCode.ACK, AckPacket::new);
		packetBuilders.put(PacketTypeCode.CHANNEL_ENUMERATION_REQUEST, ChannelEnumerationRequestPacket::new);
		packetBuilders.put(PacketTypeCode.CHANNEL_ENUMERATION, ChannelEnumerationPacket::new);
		packetBuilders.put(PacketTypeCode.CHANNEL_SUBSCRIBE, ChannelSubscribePacket::new);
		packetBuilders.put(PacketTypeCode.CHANNEL_UNSUBSCRIBE, ChannelUnsubscribePacket::new);
		packetBuilders.put(PacketTypeCode.CHANNEL_CLOSE, null);
		packetBuilders.put(PacketTypeCode.CHANNEL_METADATA_REQUEST, null);
		packetBuilders.put(PacketTypeCode.CHANNEL_METADATA, null);
		packetBuilders.put(PacketTypeCode.PROPERTY_ENUMERATION_REQUEST, PropertyEnumerationRequestPacket::new);
		packetBuilders.put(PacketTypeCode.PROPERTY_ENUMERATION, PropertyEnumerationPacket::new);
		packetBuilders.put(PacketTypeCode.PROPERTY_VALUES_REQUEST, PropertyValuesRequestPacket::new);
		packetBuilders.put(PacketTypeCode.PROPERTY_VALUES, PropertyValuesPacket::new);
	}

	@Override
	public List<DataChannel> getAvailableChannels() {
		List<DataChannel> result = new ArrayList<>(channels.size());
		for (int i = 0; i < channels.size(); i++)
			result.add(channels.valueAt(i));
		return result;
	}

	@Override
	public void registerChannel(DataChannel channel) {
		channels.append(channel.getId(), (AbstractWsDataChannel) channel);
	}

	@Override
	public void unregisterChannel(DataChannel channel) {
		//TODO check if valid op
		channels.remove(channel.getId());
	}
	
	class MetaChannel extends AbstractWsDataChannel {

		@Override
		protected boolean onSubscription(DataChannelClient client) {
			return false;//All clients are implicitly subscribed
		}

		@Override
		protected void onRecievePacket(DataPacket packet, DataChannelClient client) {
			System.out.println("Handling packet " + packet + ", type " + packet.getTypeCode());
			switch (packet.getTypeCode()) {
				case PacketTypeCode.CHANNEL_ENUMERATION_REQUEST: {
					System.out.println("Channel enumeration request from " + client);
					ChannelEnumerationPacket response = null;//TODO finish
					response.setAckId(packet.getId())
						.setId(lastPacketId++)
						.setChannelId(0);
					client.write(response);
					break;
				}
				case PacketTypeCode.CHANNEL_METADATA_REQUEST:
					System.out.println("Channel metadata request from " + client);
					//TODO handle
					break;
				case PacketTypeCode.CHANNEL_SUBSCRIBE: {
					System.out.println("Channel subscription from " + client);
					ChannelSubscribePacket subscribePacket = (ChannelSubscribePacket) packet;
					int[] ids = subscribePacket.getChannelIds();
					for (int id : subscribePacket.getChannelIds()) {
						System.out.println("\tSubscribing to channel #" + id);
						AbstractWsDataChannel channel = channels.get(id);
						//TODO figure out how to make atomic
						if (channel == null || !channel.onSubscription(client)) {
							System.err.println("Writing error");
							client.write(new MutableErrorPacket(ErrorCode.INVALID_CHANNEL, "Cannot subscribe to channel " + (channel == null ? "null" : channel.getId()))
								.setId(lastPacketId++)
								.setChannelId(0)
								.setAckId(packet.getId()));
							return;
						}
					}
					client.write(new AckPacket(packet.getId())
						.setId(lastPacketId++)
						.setChannelId(0));
					break;
				}
				case PacketTypeCode.CHANNEL_UNSUBSCRIBE: {
					ChannelUnsubscribePacket unsubscribePacket = (ChannelUnsubscribePacket) packet;
					int[] ids = unsubscribePacket.getChannelIds();
					UnsubscriptionReason[] reasons = unsubscribePacket.getReasons();
					for (int i = 0; i < ids.length; i++) {
						AbstractWsDataChannel channel = channels.get(ids[i]);
						//TODO handle INVALID_CHANNEL
						channel.onUnsubscription(client, reasons[i]);
					}
					break;
				}
				case PacketTypeCode.CHANNEL_ENUMERATION:
				case PacketTypeCode.CHANNEL_METADATA:
				default:
					client.write(new MutableErrorPacket(ErrorCode.ILLEGAL_PACKET_TYPE, "Illegal packet type " + packet.getTypeCode())
							.setAckId(packet.getId())
							.setChannelId(0)
							.setId(lastPacketId++));
					break;
			}
		}

		@Override
		protected void onUnsubscription(DataChannelClient client, UnsubscriptionReason reason) {
			// TODO Auto-generated method stub
			
		}
		
	}

	public class WsClient implements WebSocketListener, DataChannelClient {
		Session session;

		public CompletableFuture<Void> write(ByteBuffer data) {
			CompletableFuture<Void> result = new CompletableFuture<>();
			if (data.hasArray()) {
				System.out.println("Writing: " + StringUtils.toHexString(data.array(), data.position(), data.limit(), 16));
			}
			try {
				session.getRemote().sendBytes(data, new WriteCallback() {
	
					@Override
					public void writeFailed(Throwable t) {
						result.completeExceptionally(t);
					}
	
					@Override
					public void writeSuccess() {
						result.complete(null);
					}
	
				});
			} catch (Exception e) {
				e.printStackTrace();
				result.completeExceptionally(e);
			}
			return result;
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			System.out.println("Close: statusCode=" + statusCode + ", reason=" + reason);
		}

		@Override
		public void onWebSocketConnect(Session session) {
			System.out.println("Connected: " + session.getRemoteAddress());
			this.session = session;
		}

		@Override
		public void onWebSocketError(Throwable t) {
			System.out.println("Error: " + t.getMessage());
		}

		@Override
		public void onWebSocketBinary(byte[] arr, int offset, int length) {
			System.out.println(StringUtils.toHexString(arr, offset, length, 16));
			//Read packet
			ByteBuffer buf = ByteBuffer.wrap(arr, offset, length);
			//check packet size, to make sure that we can read the header fields
			if (length < DataPacket.HEADER_LENGTH) {
				System.err.println("Recieved packet w/ length=" + length);
				return;
			}
			
			//Get builder
			int packetType = buf.getShort(buf.position() + DataPacket.TYPE_CODE_OFFSET) & 0xFF_FF;//Convert ushort=>int
			Function<ByteBuffer, DataPacket> builder = WsDataSource.this.packetBuilders.get(packetType);
			if (builder == null) {
				System.err.println("Unknown packet ID " + packetType);
				write(new MutableErrorPacket(ErrorCode.UNKNOWN_PACKET_TYPE, "Unknown packet type #" + packetType)
						.setId(lastPacketId++)
						.setAckId(buf.getShort(buf.position() + DataPacket.PACKET_ID_OFFSET))
						.setChannelId(0));//TODO should this be sent on the same channel?
				return;		
			}
			System.out.println("Builder: " + builder);
			
			//Build packet
			DataPacket packet;
			try {
				packet = builder.apply(buf);
			} catch (Exception e) {
				//TODO handle
				e.printStackTrace();
				return;
			}
			System.out.println("Packet: " + packet);
			
			//Respond to ACK listener(s)
			if (packet.getAckId() != 0)
				WsDataSource.this.responseHandlerManager.handle(this, packet);
			
			//Get channel
			int channelId = buf.getShort(offset + DataPacket.CHANNEL_ID_OFFSET) & 0xFF_FF;//Convert ushort=>int
			AbstractWsDataChannel channel = WsDataSource.this.channels.get(channelId);
			if (channel == null) {
				write(new MutableErrorPacket(ErrorCode.INVALID_CHANNEL, "Unable to find channel with id " + channelId)
						.setAckId(buf.getInt(offset + DataPacket.CHANNEL_ID_OFFSET))
						.setId(lastPacketId++)
						.setChannelId(channelId))
					.exceptionally(e->{new RuntimeException("Error while writing error packet for invalid channel", e).printStackTrace();return null;});
				return;
			}
			
			try {
				channel.onRecievePacket(packet, this);
			} catch (Exception e0) {
				write(new MutableErrorPacket(ErrorCode.INTERNAL_ERROR, e0.getMessage())
						.setAckId(buf.getInt(offset + DataPacket.CHANNEL_ID_OFFSET))
						.setId(lastPacketId++)
						.setChannelId(channelId))
					//Yes, really
					.exceptionally(e->{new RuntimeException("Error while writing error packet for internal error", e).printStackTrace();return null;});
			}
		}

		@Override
		public void onWebSocketText(String data) {
			// We aren't really interested in text messages
		}
	}

	@Override
	public void configure(WebSocketServletFactory factory) {
		//Wouldn't have figured this out if not for github.com/czyzby/reinvent/blob/master/websocket/src/com/github/czyzby/reinvent/websocket/WebSocketServer.java
		factory.setCreator(new WebSocketCreator() {
			@Override
			public Object createWebSocket(ServletUpgradeRequest request, ServletUpgradeResponse response) {
				response.setAcceptedSubProtocol("v" + WsDataSource.SERVER_VERSION + ".moews");
				System.out.println("Generating client for " + request.getRequestPath() + ", protocol = " + request.getProtocolVersion() + ", " + request.getSubProtocols());
				return new WsClient();
			}
		});
	}
}
