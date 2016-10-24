package com.moe365.mopi;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

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
	public static final int RIO_PORT = 5801;
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
	 * 8 byte packet. Used for sending {@link #STATUS_NONE_FOUND} packets.
	 */
	protected final DatagramPacket packet_8;
	/**
	 * 40 byte packet. Used for sending {@link #STATUS_ONE_FOUND} and
	 * {@link #STATUS_ERROR} packets.
	 */
	protected final DatagramPacket packet_40;
	/**
	 * 72 byte packet. Used for sending {@link #STATUS_TWO_FOUND} packets.
	 */
	protected final DatagramPacket packet_72;
	
	protected final AtomicBoolean addressUpdated = new AtomicBoolean(false);
	/**
	 * RoboRIO's address. May change as it is continually resolved
	 */
	protected volatile SocketAddress address;
	/**
	 * UDP socket to send from
	 */
	protected final DatagramSocket socket;
	/**
	 * Executor to run background tasks on
	 */
	protected final ExecutorService executor;
	/**
	 * The port that we are sending packets to (on the Rio)
	 */
	protected final int port;
	/**
	 * The Rio's hostname, to be resolved via mDNS
	 */
	protected final String hostname;
	/**
	 * Buffer backing packets
	 */
	protected final ByteBuffer buffer;
	/**
	 * Packet number. This number is to allow the client to ignore packets that
	 * are recieved out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
	protected final MDNSListener resolver;
	protected volatile boolean isResolved = false;
	/**
	 * Create a client with default settings
	 * @param executor 
	 * @throws SocketException 
	 * @throws IOException 
	 */
	public RoboRioClient(ExecutorService executor) throws SocketException, IOException {
		this(executor, RIO_PORT, RIO_ADDRESS, RIO_PORT);
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
		this(executor, RESOLVE_RETRY_TIME, serverPort, hostname, rioPort);
	}
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param addr
	 * @throws SocketException
	 * @throws IOException
	 */
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
		this(executor, resolveRetryTime, BUFFER_SIZE, serverPort, hostname, rioPort);
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
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int buffSize, int serverPort, String hostname, int rioPort) throws SocketException, IOException {
		this.executor = executor;
		this.port = rioPort;
		this.buffer = ByteBuffer.allocate(buffSize);
		this.socket = new DatagramSocket(serverPort);
		this.hostname = hostname;
		try {
			socket.setTrafficClass(0x10);//Low delay
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		this.packet_8  = new DatagramPacket(buffer.array(), 0, 8);
		this.packet_40 = new DatagramPacket(buffer.array(), 0, 40);
		this.packet_72 = new DatagramPacket(buffer.array(), 0, 72);
		
		//Handle the address resolution
		this.isResolved = false;
		this.address = null;
		this.resolver = new MDNSListener();
		this.resolver.setHandler(this::handleMdnsResponse);
		executor.submit(this.resolver);
		System.out.println("Resolving to RIO " + serverPort + " => @ " + hostname + ':' + rioPort);
	}
	
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int serverPort, SocketAddress addr) throws SocketException, IOException {
		this.executor = executor;
		this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
		this.socket = new DatagramSocket(serverPort);
		try {
			socket.setTrafficClass(0x10);//Low delay
		} catch (SocketException e) {
			e.printStackTrace();
		}
		this.packet_8  = new DatagramPacket(buffer.array(), 0, 8);
		this.packet_40 = new DatagramPacket(buffer.array(), 0, 40);
		this.packet_72 = new DatagramPacket(buffer.array(), 0, 72);
		if (addr instanceof InetSocketAddress) {
			//TODO finish
			InetSocketAddress iAddr = (InetSocketAddress) addr;
			this.port = iAddr.getPort();
			this.hostname = iAddr.getHostName();
			this.isResolved = !iAddr.isUnresolved();
		} else {
			this.port = -1;
			this.hostname = null;
			this.isResolved = true;
		}
		
		if (this.isResolved) {
			System.out.println("Connecting to RIO: " + serverPort + " => " + addr);
			this.resolver = null;
			this.address = addr;
		} else {
			System.out.println("Resolving to RIO " + serverPort + " => @ " + hostname + ':' + rioPort);
			this.resolver = new MDNSListener();
			this.resolver.setHandler(this::handleMdnsResponse);
			executor.submit(this.resolver);
		}
	}
	
	protected void build(short status, short ack) {
		buffer.position(0);
		buffer.putInt(packetNum.getAndIncrement());
		buffer.putShort(status);
		buffer.putShort(ack);
	}
	protected void send(DatagramPacket packet) throws IOException {
		socket.send(packet);
	}
	public void write(short status) throws IOException {
		build(status, (short) 0);
		send(packet_8);
	}
	public void write(short status, short ack) throws IOException {
		build(status, ack);
		send(packet_8);
	}
	public void writeNoneFound() throws IOException {
		write(STATUS_NONE_FOUND);
	}
	public void writeOneFound(PreciseRectangle rect) throws IOException {
		writeOneFound(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	public void writeOneFound(double left, double top, double width, double height) throws IOException {
		if (!isResolved)
			return;
		build(STATUS_ONE_FOUND, (short) 0);
		buffer.putDouble(left);
		buffer.putDouble(top);
		buffer.putDouble(width);
		buffer.putDouble(height);
		send(packet_40);
	}
	public void writeTwoFound(PreciseRectangle rect1, PreciseRectangle rect2) throws IOException {
		writeTwoFound(rect1.getX(), rect1.getY(), rect1.getWidth(), rect1.getHeight(), rect2.getX(), rect2.getY(), rect2.getWidth(), rect2.getHeight());
	}
	public void writeTwoFound(double left1, double top1, double width1, double height1, double left2, double top2, double width2, double height2) throws IOException {
		if (!isResolved)
			return;
		build(STATUS_TWO_FOUND, (short) 0);
		buffer.putDouble(left1);
		buffer.putDouble(top1);
		buffer.putDouble(width1);
		buffer.putDouble(height1);
		buffer.putDouble(left2);
		buffer.putDouble(top2);
		buffer.putDouble(width2);
		buffer.putDouble(height2);
		send(packet_72);
	}
	public void writeError(long errorCode) throws IOException {
		if (!isResolved)
			return;
		build(STATUS_ERROR, (short)0);
		buffer.putLong(errorCode);
		//Pad with 0s
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.putLong(0);
		send(packet_40);
	}
	@Override
	public void close() throws IOException {
		socket.close();
	}
	
	public void sendQueries() {
		DnsMessage query = DnsMessage.builder()
			.setRecursionDesired(true)
			.ask(DnsQuery.builder()
			     .setName(hostname)
			     .setClass(DnsClass.IN)
			     .setType(DnsType.ANY)
			     .build())
			.build();
		while (!Thread.interrupted()) {
			try {
				listener.sendMessage(query);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
			try {
				Thread.sleep(RESOLVE_RETRY_TIME);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
	
	public void handleMdnsResponse(DnsMessage message) {
		final DnsRecord[] answers = message.getAnswers();
		for (int i = 0; i < answers.length; i++) {
			DnsRecord answer = answers[i];
			
		}
	}
}
