package com.moe365.mopi.client;

public class StaticRioClient extends RioClient {
	/**
	 * RoboRIO's address.
	 */
	protected final SocketAddress address;
	
	public RoboRioClient(SocketAddress addr) throws SocketException, IOException {
		this(SERVER_PORT, addr);
	}
	/**
	 * Create a client that WILL NOT resolve the passed address via mDNS
	 * @param serverPort 
	 */
	public RoboRioClient(int serverPort, SocketAddress addr) throws SocketException, IOException {
		this.serverPort = serverPort;
		this.address = addr;
		this.resolveRetryTime = RESOLVE_RETRY_TIME;
		if (addr instanceof InetSocketAddress) {
			//TODO finish
			InetSocketAddress iAddr = (InetSocketAddress) addr;
			this.rioPort = iAddr.getPort();
			try {
				this.rioHostname = new FQDN(iAddr.getHostName());
			} catch (NoClassDefFoundError e) {
				//mDNS4J is not on the classpath, but we don't need it this time,
				//so swallow the exception
				e.printStackTrace();
				this.rioHostname = null;
			}
		} else {
			this.rioPort = -1;
			this.rioHostname = null;
		}
		this.socket = new DatagramSocket(this.serverPort);
		System.out.println("Connecting to RIO: " + serverPort + " => " + addr);
	}
}