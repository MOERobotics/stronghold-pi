package com.moe365.mopi;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mindlin.mdns.DnsClass;
import com.mindlin.mdns.DnsMessage;
import com.mindlin.mdns.DnsQuery;
import com.mindlin.mdns.DnsRecord;
import com.mindlin.mdns.DnsType;
import com.mindlin.mdns.FQDN;
import com.mindlin.mdns.MDNSListener;
import com.mindlin.mdns.rdata.AddressRDATA;
import com.moe365.mopi.geom.PreciseRectangle;

/**
 * UDP server to broadcast data at the RIO.
 * <p>
 * All packets sent from this class start with a 32 bit unsigned integer
 * sequence number, which will always increase between consecutive packets.
 * Format of the UDP packets:
 * 
 * <pre>
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Sequence Number                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Status code         |               ACK             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 * <dl>
 * <dt>Sequence Number: 32 bits</dt>
 * <dd>The packet number, always increasing. If packet A is received with a
 * sequence number of 5, then all future packets with sequence numbers under 5
 * may be discarded. This may be a timestamp</dd>
 * <dt>Status code: 16 bits</dt>
 * <dd>One of the following:
 * <ol start=0> <li>NOP</li> <li>NONE_FOUND</li> <li>ONE_FOUMD</li>
 * <li>TWO_FOUND</li> <li>GOODBYE</li> </ol> All other status codes are reserved
 * for future use. </dd> <dt>Flag: 8 bits</dt> <dd>Like a secondary status code,
 * it is used for stuff like QOS. If unused, set to 0. <table> <thead> <tr>
 * <th>#</th> <th>Name</th> <th>Description</th> </tr> </thead> <tbody> <tr>
 * <td>0</td> <td>Ping</td> <td>Sends a ping request. For latency
 * measurement</td> </tr> <tr> <td>1</td> <li>PING</li> <li>PONG</li>
 * <li>ARE_YOU_STILL_THERE</li> <li>YES_I_AM</li> </tbpdy> </table> </dd> </dl>
 * </p>
 * 
 * @author mailmindlin
 */
public class RoboRioClient implements Closeable {
	public static final int SERVER_PORT = 5801;
	public static final int RIO_PORT = 5801;
	public static final boolean PREFER_IP6 = true;
	/**
	 * Size of the buffer.
	 */
	public static final int BUFFER_SIZE = 72;
	public static final int RESOLVE_RETRY_TIME = 5_000;
	/**
	 * mDNS address of the RoboRio.
	 * 
	 * TODO make more portable for other teams
	 */
	public static final String RIO_ADDRESS = "roboRIO-365-FRC.local";
	
	/**
	 * Denotes a packet that should be ignored. No idea why we would need
	 * to use this, though.
	 */
	public static final short STATUS_NOP = 0;
	/**
	 * Denotes a packet telling the Rio that no target(s) were found.
	 */
	public static final short STATUS_NONE_FOUND = 1;
	/**
	 * Denotes a packet telling the Rio that one target has been
	 * detected. The position data MUST be included in the packet.
	 */
	public static final short STATUS_ONE_FOUND = 2;
	/**
	 * Denotes a packet telling the Rio that two or more targets
	 * have been found. The position data of the two largest targets
	 * found (by area) MUST be included in the packet. 
	 */
	public static final short STATUS_TWO_FOUND = 3;
	public static final short STATUS_ERROR = 4;
	public static final short STATUS_HELLO_WORLD = 5;
	public static final short STATUS_GOODBYE = 6;
	public static final short STATUS_PING = 7;
	public static final short STATUS_PONG = 8;
	public static final short STATUS_ARE_YOU_THERE = 9;
	public static final short STATUS_YES_I_AM = 10;
	public static final short STATUS_REQUEST_CONFIG = 11;
	public static final short STATUS_CONFIG = 12;
	
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
	protected final AtomicReference<DatagramSocket> socket = new AtomicReference<>(null);
	
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
	 * Packet number. This number is to allow the client to ignore packets that
	 * are received out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
	
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
	
	public RoboRioClient(SocketAddress addr) throws SocketException, IOException {
		this(SERVER_PORT, addr);
	}
	
	/**
	 * Create a client that WILL NOT resolve the passed address via mDNS
	 * @param serverPort 
	 */
	public RoboRioClient(int serverPort, SocketAddress addr) throws SocketException, IOException {
		this.executor = null;
		this.serverPort = serverPort;
		this.address = addr;
		this.netIf = null;
		this.mdnsListener = null;
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
		this.resetSocket();
		System.out.println("Connecting to RIO: " + serverPort + " => " + addr);
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
	 * @throws SocketException if the socket could not be opened, or the socket could not bind to the specified local port.
	 * @throws IOException 
	 */
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, NetworkInterface netIf, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
		this.executor = executor;
		this.resolveRetryTime = resolveRetryTime;
		this.serverPort = serverPort;
		this.netIf = netIf;
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
		this.resetSocket();
		this.mdnsListener.setHandler(this::handleMdnsResponse);
		executor.submit(this.mdnsListener);
		executor.submit(this::sendQueries);
		System.out.println("Resolving to RIO " + serverPort + " => @ " + hostname + ':' + rioPort);
	}
	
	protected void resetSocket() throws IOException {
		DatagramSocket newSocket = new DatagramSocket();
		try {
			newSocket.setReuseAddress(true);
			newSocket.bind(new InetSocketAddress(this.serverPort));
		} catch (Exception e) {
			//There was a problem binding the new socket,
			//so close it before propagating the exception
			newSocket.close();
			throw e;
		}
		DatagramSocket oldSocket = this.socket.getAndSet(newSocket);
		if (oldSocket != null)
			oldSocket.close();
	}
	
	protected void build(short status, short ack) {
		buffer.position(0);
		buffer.limit(buffer.capacity());
		buffer.putInt(packetNum.getAndIncrement());
		buffer.putShort(status);
		buffer.putShort(ack);
	}
	
	protected void send(ByteBuffer buffer) throws IOException {
		SocketAddress address = this.address;
		DatagramSocket socket = this.socket.get();
		if (address == null || socket == null) {
			System.err.println("Dropped packet to Rio");
			return;
		}
		DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.position(), buffer.limit(), address);
		try {
			socket.send(packet);
		} catch (IOException e) {
			this.resetSocket();
			socket = this.socket.get();
			SocketAddress addr2 = this.address;
			if (socket == null || addr2 == null)
				return;
			if (addr2 != address)
				//Can we just change the address w/o creating a new object?
				packet = new DatagramPacket(buffer.array(), buffer.position(), buffer.limit(), addr2);
			try {
				socket.send(packet);
			} catch (Throwable t) {
				t.printStackTrace();
				throw t;
			}
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		}
	}
	
	public void write(short status) throws IOException {
		build(status, (short) 0);
		this.buffer.flip();
		send(this.buffer);
	}
	
	public void write(short status, short ack) throws IOException {
		build(status, ack);
		this.buffer.flip();
		send(this.buffer);
	}
	
	public void writeNoneFound() throws IOException {
		write(STATUS_NONE_FOUND);
	}
	
	public void writeOneFound(PreciseRectangle rect) throws IOException {
		writeOneFound(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	
	public void writeOneFound(double left, double top, double width, double height) throws IOException {
		build(STATUS_ONE_FOUND, (short) 0);
		this.buffer.putDouble(left);
		this.buffer.putDouble(top);
		this.buffer.putDouble(width);
		this.buffer.putDouble(height);
		this.buffer.flip();
		send(this.buffer);
	}
	
	public void writeTwoFound(PreciseRectangle rect1, PreciseRectangle rect2) throws IOException {
		writeTwoFound(rect1.getX(), rect1.getY(), rect1.getWidth(), rect1.getHeight(), rect2.getX(), rect2.getY(), rect2.getWidth(), rect2.getHeight());
	}
	
	public void writeTwoFound(double left1, double top1, double width1, double height1, double left2, double top2, double width2, double height2) throws IOException {
		build(STATUS_TWO_FOUND, (short) 0);
		buffer.putDouble(left1);
		buffer.putDouble(top1);
		buffer.putDouble(width1);
		buffer.putDouble(height1);
		buffer.putDouble(left2);
		buffer.putDouble(top2);
		buffer.putDouble(width2);
		buffer.putDouble(height2);
		this.buffer.flip();
		send(this.buffer);
	}
	
	public void writeError(long errorCode) throws IOException {
		build(STATUS_ERROR, (short)0);
		buffer.putLong(errorCode);
		//Pad with 0s
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.putLong(0);
		this.buffer.flip();
		send(this.buffer);
	}
	
	@Override
	public void close() {
		if (this.mdnsListener != null)
			this.mdnsListener.close();
		DatagramSocket socket = this.socket.getAndSet(null);
		if (socket != null)
			socket.close();
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
			this.address = new InetSocketAddress(addr, this.rioPort);
	}
}
