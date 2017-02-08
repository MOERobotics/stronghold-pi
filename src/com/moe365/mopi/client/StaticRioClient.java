package com.moe365.mopi.client;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

import com.mindlin.mdns.FQDN;

public class StaticRioClient implements RioClient {
	
	protected final int serverPort;
	
	protected final int rioPort;
	
	protected final DatagramSocket socket;
	/**
	 * RoboRIO's address.
	 */
	protected final SocketAddress address;
	
	public StaticRioClient(SocketAddress addr) throws SocketException, IOException {
		this(RioClient.SERVER_PORT, addr);
	}
	/**
	 * Create a client that WILL NOT resolve the passed address via mDNS
	 * @param serverPort 
	 */
	public StaticRioClient(int serverPort, SocketAddress addr) throws SocketException, IOException {
		this.serverPort = serverPort;
		this.address = addr;
		if (addr instanceof InetSocketAddress) {
			//TODO finish
			InetSocketAddress iAddr = (InetSocketAddress) addr;
			this.rioPort = iAddr.getPort();
		} else {
			this.rioPort = -1;
		}
		this.socket = new DatagramSocket(this.serverPort);
		System.out.println("Connecting to RIO: " + serverPort + " => " + addr);
	}
}