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
		addWsHandler();
		server.start();
		server.join();
	}

	protected void addWsHandler() {
		WsDataSource source = new WsDataSource();
		final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS|ServletContextHandler.NO_SECURITY);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(source), "/");
	}

	public static void main(String... fred) throws Exception {
		new ServerController(8080);
	}
}
