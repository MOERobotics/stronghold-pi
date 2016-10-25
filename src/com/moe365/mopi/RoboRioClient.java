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
	 * Buffer backing packets
	 */
	protected final ByteBuffer buffer = ByteBuffer.allocate(RoboRioClient.BUFFER_SIZE);
	protected final int rioPort;
	protected FQDN rioHostname;
	protected final int serverPort;
	protected final Object socketLock = new Object();
	/**
	 * RoboRIO's address
	 */
	protected SocketAddress address;
	/**
	 * UDP socket
	 */
	protected volatile DatagramSocket socket;
	protected final long resolveRetryTime;
	protected final ExecutorService executor;
	/**
	 * Packet number. This number is to allow the client to ignore packets that
	 * are recieved out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
	protected final MDNSListener mdnsListener;
	/**
	 * Create a client with default settings
	 * @param executor 
	 * @throws SocketException 
	 * @throws IOException 
	 */
	public RoboRioClient(ExecutorService executor) throws SocketException, IOException {
		this(executor, RESOLVE_RETRY_TIME, SERVER_PORT, RIO_ADDRESS, RIO_PORT);
	}
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param addr
	 * @throws SocketException
	 * @throws IOException
	 */
	public RoboRioClient(SocketAddress addr) throws SocketException, IOException {
		this(SERVER_PORT, addr);
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
	public RoboRioClient(int serverPort, SocketAddress addr) throws SocketException, IOException {
		this.executor = null;
		this.mdnsListener = null;
		this.serverPort = serverPort;
		this.address = addr;
		resolveRetryTime = RESOLVE_RETRY_TIME;
		if (addr instanceof InetSocketAddress) {
			//Try to fill in some of the fields
			InetSocketAddress iAddr = (InetSocketAddress) addr;
			this.rioPort = iAddr.getPort();
			try {
				this.rioHostname = new FQDN(iAddr.getHostName());
			} catch (NoClassDefFoundError e) {
				//mDNS4J is not on the classpath
				e.printStackTrace();
				this.rioHostname = null;
			}
		} else {
			this.rioPort = -1;
			this.rioHostname = null;
		}
		
		System.out.println("Connecting to RIO: " + serverPort + " => " + addr);
		this.socket = new DatagramSocket(serverPort);
		try {
			socket.setTrafficClass(0x10);//Low delay
			socket.setReuseAddress(true);
		} catch (SocketException e) {
			//It's not too important that these are set
			e.printStackTrace();
		}
		socket.connect(addr);
	}
	
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int serverPort, String rioHostname, int rioPort) throws SocketException, IOException {
		this.executor = executor;
		this.resolveRetryTime = resolveRetryTime;
		this.serverPort = serverPort;
		try {
			this.rioHostname = new FQDN(rioHostname);
			this.mdnsListener = new MDNSListener(NetworkInterface.getByName("eth0"));
		} catch (NoClassDefFoundError e) {
			System.err.println("Check that mDNS4J is on your classpath");
			throw e;
		}
		this.mdnsListener.setHandler(this::handleMdnsResponses);
		executor.submit(this.mdnsListener);
		executor.submit(this::sendQueries);
		this.rioPort = rioPort;
		System.out.println("Resolving " + serverPort + " => " + rioHostname + ":" + rioPort);
		this.socket = new DatagramSocket(serverPort);
		try {
			socket.setTrafficClass(0x10);//Low delay
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	protected void build(short status, short ack) {
		buffer.position(0);
		buffer.putInt(packetNum.getAndIncrement());
		buffer.putShort(status);
		buffer.putShort(ack);
	}
	
	protected void send(int len) throws IOException {
		synchronized (socket) {
			if (socket.getRemoteSocketAddress() == null) {
				System.err.println("Dropped packet: rio unresolved");
				return;
			}
			synchronized (this.socketLock) {
				System.out.println("Sending to Rio @ " + this.address);
				try {
					DatagramPacket packet = new DatagramPacket(buffer.array(), 0, len, this.address);
					socket.send(packet);
				} catch (Throwable t) {
					t.printStackTrace();
					throw t;
				}
			}
		}
	}
	
	public void write(short status) throws IOException {
		build(status, (short) 0);
		send(8);
	}
	
	public void write(short status, short ack) throws IOException {
		build(status, ack);
		send(8);
	}
	
	public void writeNoneFound() throws IOException {
		write(STATUS_NONE_FOUND);
	}
	
	public void writeOneFound(PreciseRectangle rect) throws IOException {
		writeOneFound(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	
	public void writeOneFound(double left, double top, double width, double height) throws IOException {
		build(STATUS_ONE_FOUND, (short) 0);
		buffer.putDouble(left);
		buffer.putDouble(top);
		buffer.putDouble(width);
		buffer.putDouble(height);
		send(40);
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
		send(72);
	}
	
	public void writeError(long errorCode) throws IOException {
		build(STATUS_ERROR, (short)0);
		buffer.putLong(errorCode);
		//Pad with 0s
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.putLong(0);
		send(40);
	}
	
	@Override
	public void close() throws IOException {
		socket.close();
	}
	
	protected void handleMdnsResponses(DnsMessage message) {
		if (!message.isResponse())
			return;
		System.out.println("Response: " + message);
		InetAddress addr = null;
		for (DnsRecord answer : message.getAnswers()) {
			if ((!answer.getName().equals(this.rioHostname)) || !(answer.getType() == DnsType.A || answer.getType() == DnsType.AAAA))
				continue;
			addr = ((AddressRDATA)answer.getData()).getAddress();
		}
		System.out.println("Resolved to " + addr);
		if (addr != null) {
			synchronized (this.socketLock) {
				this.address = new InetSocketAddress(addr, this.rioPort);
				DatagramSocket socket = this.socket;
				socket.disconnect();
				socket.close();
				try {
					socket = new DatagramSocket(this.serverPort);
					try {
						socket.setTrafficClass(0x10);
					} catch (SocketException e) {
						//Ignore
					}
					this.socket = socket;
					this.socket.connect(this.address);
				} catch (SocketException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	protected void sendQueries() {
		//Build query message
		DnsMessage query = DnsMessage.builder()
				.ask(DnsQuery.builder()
						.setName(this.rioHostname)
						.setClass(DnsClass.IN)
						.setType(DnsType.ANY)
						.build())
				.build();
		System.out.println("Query: " + query);
		
		while (!Thread.interrupted()) {
			try {
				this.mdnsListener.sendMessage(query);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				Thread.sleep(this.resolveRetryTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
				break;
			}
		}
	}
}
