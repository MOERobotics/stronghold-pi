package com.moe365.mopi.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mindlin.mdns.DnsClass;
import com.mindlin.mdns.DnsMessage;
import com.mindlin.mdns.DnsQuery;
import com.mindlin.mdns.DnsRecord;
import com.mindlin.mdns.DnsType;
import com.mindlin.mdns.FQDN;
import com.mindlin.mdns.MDNSListener;
import com.mindlin.mdns.rdata.AddressRDATA;

/**
 * RioClient that tries to send its packets to a MDNS address. While it is running, this client will
 * continuously query the client's MDNS address, and, if it receives a new A/AAAA record, update the
 * IP address that it is broadcasting to. 
 * @author mailmindlin
 */
public class MDNSRioClient extends AbstractRioClient {
	/**
	 * RoboRIO's address. May change as it is continually resolved. NOT thread safe.s
	 */
	protected volatile SocketAddress address;
	
	/**
	 * Network interface to send on. May be null.
	 */
	protected final NetworkInterface netIf;
	
	/**
	 * UDP socket to packets from.
	 */
	protected DatagramChannel channel;
	
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
	public MDNSRioClient() throws SocketException, IOException {
		this(Executors.newSingleThreadExecutor(), SERVER_PORT, RIO_ADDRESS, RIO_PORT);
	}
	
	public MDNSRioClient(ExecutorService executor) throws SocketException, IOException {
		this(executor, SERVER_PORT, RIO_ADDRESS, RIO_PORT);
	}
	
	/**
	 * 
	 * @param executor
	 * @param serverPort
	 * @param hostname
	 * @param rioPort
	 * @throws SocketException
	 * @throws IOException
	 */
	public MDNSRioClient(ExecutorService executor, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
		this(executor, RESOLVE_RETRY_TIME, null, serverPort, hostname, rioPort);
	}
	
	/**
	 * 
	 * @param executor
	 * @param resolveRetryTime
	 * @param netIf
	 * @param serverPort
	 * @param hostname
	 * @param rioPort
	 * @throws SocketException
	 *             If the socket could not be opened, or the socket could not
	 *             bind to the specified local port.
	 * @throws SecurityException
	 *             If this program is not allowed to connect to the given socket
	 * @throws IOException
	 *             If any other I/O error occurs.
	 */
	public MDNSRioClient(ExecutorService executor, long resolveRetryTime, NetworkInterface netIf, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
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
		this.channel = DatagramChannel.open()
				.setOption(StandardSocketOptions.SO_REUSEADDR, true)
				.setOption(StandardSocketOptions.IP_MULTICAST_IF, netIf)
				.bind(new InetSocketAddress(this.serverPort));
		this.resetSocket();
		System.out.println("Resolving to RIO " + serverPort + " => @ " + hostname + ':' + rioPort);
		
		try {
			this.broadcast(new HelloRioPacket());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void send(ByteBuffer buffer) throws IOException {
		SocketAddress address = this.address;
		synchronized (this.channel) {
			//Can we even send a packet?
			if (address == null || !channel.isOpen()) {
				System.err.println("Dropped packet to Rio");
				return;
			}
			
			try {
				channel.send(buffer, address);
				System.out.println("Sent packet to Rio");
			} catch (IOException e) {
				//Try to recover
				this.resetSocket();
				address = this.address;
				if (address == null || !channel.isOpen())
					return;
				try {
					channel.send(buffer, address);
					System.out.println("Sent packet to Rio");
				} catch (Throwable t) {
					t.addSuppressed(e);
					t.printStackTrace();
					throw t;
				}
			} catch (Throwable t) {
				//Ensure that every exception is printed
				t.printStackTrace();
				throw t;
			}
		}
	}
	
	protected void resetSocket() throws IOException {
//		SocketAddress localAddress = new InetSocketAddress(this.serverPort);
		//TODO finish
		
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
		
		if (!addr.equals(this.address)) {
			SocketAddress address = new InetSocketAddress(addr, this.rioPort);
			this.address = address;
			System.out.println("Resolved rio address to " + address);
			
			//Broadcast a 'hello' packet
			try {
				this.broadcast(new HelloRioPacket());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	

	
	@Override
	public void close() throws IOException {
		IOException mdnsExcept = null;
		
		if (this.mdnsListener != null) {
			try {
				this.mdnsListener.close();
			} catch (Exception e) {
				mdnsExcept = (IOException) e;
			}
		}
		
		try {
			this.channel.close();
		} catch (Exception e) {
			if (mdnsExcept != null)
				e.addSuppressed(mdnsExcept);
			throw e;
		}
		
		if (mdnsExcept != null)
			throw mdnsExcept;
	}
}