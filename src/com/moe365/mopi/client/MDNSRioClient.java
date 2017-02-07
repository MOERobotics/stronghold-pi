package com.moe365.mopi.client;

public class MDNSRioClient extends RioClient {
	/**
	 * RoboRIO's address. May change as it is continually resolved. NOT thread safe.s
	 */
	protected volatile SocketAddress address;
	
	/**
	 * Network interface to send on. May be null.
	 */
	protected final NetworkInterface netIf;
	
	/**
	 * UDP socket to datagrams from.
	 */
	protected final DatagramSocket socket;
	
	/**
	 * Buffer backing packets.
	 * TODO fix multithreading issues
	 */
	protected final ByteBuffer buffer = ByteBuffer.allocate(RoboRioClient.BUFFER_SIZE);
	
	/**
	 * Executor to run background tasks on. May be null if not attempting to resolve address.
	 */
	protected final ExecutorService executor;
	
	/**
	 * The port to send from (bound to on the local machine)
	 */
	protected final int serverPort;
	
	/**
	 * The port that we are sending packets to (on the Rio)
	 */
	protected final int rioPort;
	
	/**
	 * The Rio's hostname, to be resolved via mDNS. May be null
	 */
	protected FQDN rioHostname;
	
	/**
	 * Amount of time to wait between sending mDNS QUERY messages (in ms).
	 * Defaults to {@value #RESOLVE_RETRY_TIME}. No effect if not continuously resolving.
	 */
	protected final long resolveRetryTime;
	
	/**
	 * Resolver for resolving mDNS addresses.
	 */
	protected final MDNSListener mdnsListener;
	
	/**
	 * Create a client with default settings, which will continually resolve the Rio's address via mDNS.
	 * @throws SocketException 
	 * @throws IOException
	 */
	public RoboRioClient() throws SocketException, IOException {
		this(new InetSocketAddress(RIO_ADDRESS, RIO_PORT));
	}
	
	public RoboRioClient(ExecutorService executor) throws SocketException, IOException {
		this(executor, SERVER_PORT, RIO_ADDRESS, RIO_PORT);
	}
	
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param addr
	 * @throws SocketException
	 * @throws IOException
	 */
	public RoboRioClient(ExecutorService executor, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
		this(executor, RESOLVE_RETRY_TIME, null, serverPort, hostname, rioPort);
	}
	
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param buffSize
	 * @param addr
	 * @throws SocketException
	 *             if the socket could not be opened, or the socket could not
	 *             bind to the specified local port.
	 * @throws SecurityException
	 *             If this program is not allowed to connect to the given socket
	 */
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, NetworkInterface netIf, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
		this.executor = executor;
		this.resolveRetryTime = resolveRetryTime;
		this.serverPort = serverPort;
		this.netIf = netIf;
		System.out.println("Using netIF " + netIf);
		this.rioPort = rioPort;
		try {
			this.rioHostname = new FQDN(hostname);
			this.mdnsListener = new MDNSListener(netIf);
		} catch (NoClassDefFoundError e) {
			System.err.println("Check that mDNS4j is on your classpath");
			throw e;
		}
		
		//Handle the address resolution
		this.address = null;
		this.mdnsListener.setHandler(this::handleMdnsResponse);
		executor.submit(this.mdnsListener);
		executor.submit(this::sendQueries);
		this.socket = new DatagramSocket(null);
		this.socket.setReuseAddress(true);
		this.resetSocket();
		System.out.println("Resolving to RIO " + serverPort + " => @ " + hostname + ':' + rioPort);
	}
	
	protected void resetSocket() throws IOException {
		SocketAddress addr = new InetSocketAddress(this.serverPort);
		this.socket.close();
		this.socket.bind(addr);
	}
	
	public void sendQueries() {
		DnsMessage query = DnsMessage.builder()
			.setRecursionDesired(true)
			.ask(DnsQuery.builder()
			     .setName(this.rioHostname)
			     .setClass(DnsClass.IN)
			     .setType(DnsType.ANY)
			     .build())
			.build();
		while (!Thread.interrupted()) {
			System.out.println("Querying mDNS for " + rioHostname);
			try {
				this.mdnsListener.sendMessage(query);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(this.resolveRetryTime);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
	
	protected void handleMdnsResponse(DnsMessage message) {
		if (!message.isResponse())
			return;
		boolean isAddrIp6 = false;
		InetAddress addr = null;
		for (DnsRecord answer : message.getAnswers()) {
			if (!(this.rioHostname.equals(answer.getName()) && (answer.getType() == DnsType.A || answer.getType() == DnsType.AAAA)))
				continue;
			if (addr == null || (!isAddrIp6 && answer.getType() == DnsType.AAAA))
				addr = ((AddressRDATA)answer.getData()).getAddress();
		}
		if (addr == null)
			return;
		if (!addr.equals(this.address))
			System.out.println("Resolved rio address to " + (this.address = new InetSocketAddress(addr, this.rioPort)));
	}
}