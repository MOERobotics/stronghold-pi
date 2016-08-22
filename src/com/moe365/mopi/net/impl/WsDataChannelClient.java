package com.moe365.mopi.net.impl;

import org.eclipse.jetty.websocket.api.Session;

import com.moe365.mopi.net.channel.DataChannelClient;

public class WsDataChannelClient implements DataChannelClient {
	protected final Session session;
	public WsDataChannelClient(Session session) {
		this.session = session;
	}
	public Session getSession() {
		return this.session;
	}

}
