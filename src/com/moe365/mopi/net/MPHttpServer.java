package com.moe365.mopi.net;

import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.moe365.mopi.geom.Polygon;
import com.moe365.mopi.geom.PreciseRectangle;
import com.moe365.mopi.net.channel.DataChannel;
import com.moe365.mopi.net.impl.MjpegBroadcastChannel;
import com.moe365.mopi.net.impl.RandomlyBroadcastingChannel;
import com.moe365.mopi.net.impl.WsDataSource;

import au.edu.jcu.v4l4j.VideoFrame;

public class MPHttpServer {
	protected final Server server;
	protected final ServletContextHandler context;
	protected final WsDataSource source;
	protected final MjpegBroadcastChannel videoChannel;
	public MPHttpServer(int port, String staticDir) {
		this.server = new Server(port);
		this.context = new ServletContextHandler(ServletContextHandler.SESSIONS | ServletContextHandler.NO_SECURITY);
		context.setContextPath("/");
		server.setHandler(context);
		
		//Register servlets
		DefaultServlet staticServlet = new DefaultServlet();
		ServletHolder staticHolder = new ServletHolder("default", staticServlet);
		staticHolder.setInitParameter("resourceBase", staticDir);
		context.addServlet(staticHolder, "/*");
		
		//Set up WsDataSource
		this.source = new WsDataSource();
		context.addServlet(new ServletHolder(this.source), "/vdc.ws");
		
		DataChannel random = new RandomlyBroadcastingChannel(this.source);
		this.source.registerChannel(random);
		this.videoChannel = new MjpegBroadcastChannel(this.source, 365, "Main MJPEG video stream");
		this.source.registerChannel(this.videoChannel);
	}
	
	
	public void start() throws Exception {
		server.start();
	}
	
	public void offerFrame(VideoFrame frame) {
		this.videoChannel.offerFrame(frame);
	}
	
	public void offerPolygons(List<Polygon> polygons) {
		
	}
	
	public void offerRectangles(List<PreciseRectangle> rectangles) {
		
	}
	
	public void shutdown() throws Exception {
		this.server.stop();
	}
}
