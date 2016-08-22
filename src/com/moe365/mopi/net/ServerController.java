package com.moe365.mopi.net;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import com.moe365.mopi.net.impl.WsDataSource;

public class ServerController {
	int port;
	Server server;
	public ServerController(int port) throws Exception {
		server = new Server(port);
		WebSocketHandler wsHandler = new WebSocketHandler() {
			@Override
			public void configure(WebSocketServletFactory factory) {
				factory.register(WsDataSource.WsDataSourceClient.class);
			}
		};
		server.setHandler(wsHandler);
		server.start();
		server.join();
	}
	public static void main(String...fred) throws Exception {
		new ServerController(8080);
	}
}
