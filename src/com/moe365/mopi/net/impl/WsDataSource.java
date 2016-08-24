package com.moe365.mopi.net.impl;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.WriteCallback;

import com.moe365.mopi.net.channel.DataChannel;
import com.moe365.mopi.net.channel.DataChannelClient;
import com.moe365.mopi.net.channel.DataSource;
import com.moe365.mopi.util.StringUtils;

public class WsDataSource implements DataSource {
	/**
	 * Last id for a packet sent from the server.
	 */
	protected volatile int lastPacketId = 0;
	protected ResponseHandlerManager responseHandlerManager = new ResponseHandlerManager();
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

	@Override
	public List<DataChannel> getAvailableChannels() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerChannel(DataChannel channel) {
		// TODO Auto-generated method stub

	}

	@Override
	public void unregisterChannel(DataChannel channel) {
		// TODO Auto-generated method stub

	}

	public static class WsDataSourceClient implements WebSocketListener, DataChannelClient {
		Session session;

		public CompletableFuture<Void> write(ByteBuffer data) {
			CompletableFuture<Void> result = new CompletableFuture<>();
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
		public void onWebSocketBinary(byte[] buf, int offset, int length) {
			System.out.println(StringUtils.toHexString(buf, offset, length, 16));
		}

		@Override
		public void onWebSocketText(String data) {
			// We aren't really interested in text messages
		}

	}
}
