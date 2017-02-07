package com.moe365.mopi.client;

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
 * UDP server to broadcast data at the RIO. <strong>Not</strong> thread safe.
 * <p>
 * 
 * <section id="header">
 * <h2>Packet header format</h2>
 * <pre>
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Sequence Number                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Status code         |              Flag             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 * <dl>
 * <dt>Sequence Number</dt>
 * <dd>32-bit packet number, always increasing. If packet A is received with a
 * sequence number of 5, then all future packets with sequence numbers under 5
 * may be discarded. Because this field is not assured to be consecutive, unix
 * (or other) timestamps may be used, assuming no consecutive packets are sent
 * within the same lowest resolution time unit. 
 * </dd>
 * <dt>Status code</dt>
 * <dd>16-bit code indicating the intent of the packet. See <a href="#statusCodes">Status Codes</a>.</dd>
 * <dt>Flag</dt>
 * <dd>8-bit secondary status code, it is used for stuff like QOS. If unused,
 * set to 0. See <a href="#flags">Flags</a>.
 * </dd>
 * </dl>
 * </section>
 * <section id="statusCodes">
 * <h3>Status Codes</h3>
 * A status code may be one of the following:
 * <ol start="0">
 * <li>{@linkplain #STATUS_NOP NOP}</li>
 * <li>{@linkplain #STATUS_NONE_FOUND NONE_FOUND}</li>
 * <li>{@linkplain #STATUS_ONE_FOUND ONE_FOUND}</li>
 * <li>{@linkplain #STATUS_TWO_FOUND TWO_FOUND}</li>
 * <li>{@linkplain #STATUS_ERROR ERROR}</li>
 * </ol>
 * All other status codes are reserved for future use.
 * </section>
 * <section id="flags">
 * <h3>Flags</h3>
 * The flags field is a bitfield.
 * <table style="border:1px solid black;border-collapse:collapse;">
 * <thead>
 * <tr>
 * <th style="text-align:left">Bit</th>
 * <th style="text-align:left">Name</th>
 * <th style="text-align:left">Description</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>0</td>
 * <td>PING</td>
 * <td>Sends a ping request. For latency measurement</td>
 * </tr>
 * <tr>
 * <td>1</td>
 * <td>PONG</td>
 * <td>A response to a ping request</td>
 * </tr>
 * <tr>
 * <td>2</td>
 * <td>ARE_YOU_STILL_THERE</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>3</td>
 * <td>YES_I_AM</td>
 * <td></td>
 * </tr>
 * </tbody>
 * </table>
 * </section>
 * @author mailmindlin
 */
public abstract class RioClient implements Closeable {
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
	 * Denotes a packet that should be ignored. No idea why we would need to use
	 * this, though.
	 */
	public static final short STATUS_NOP = 0;
	/**
	 * Denotes a packet telling the Rio that no target(s) were found.
	 */
	public static final short STATUS_NONE_FOUND = 1;
	/**
	 * Denotes a packet telling the Rio that one target has been detected. The
	 * position data MUST be included in the packet.
	 */
	public static final short STATUS_ONE_FOUND = 2;
	/**
	 * Denotes a packet telling the Rio that two or more targets have been
	 * found. The position data of the two largest targets found (by area) MUST
	 * be included in the packet.
	 */
	public static final short STATUS_TWO_FOUND = 3;
	/**
	 * A packet that contains the 
	 */
	public static final short STATUS_ERROR = 4;
	/**
	 * A packet that notifies the reciever that the sender has just connected.
	 * If this packet is recieved, the reciever should reset its last-recieved packet id
	 * to the id of this packet.
	 */
	public static final short STATUS_HELLO_WORLD = 5;
	/**
	 * Signals that the sender is terminating in an expected manner.
	 */
	public static final short STATUS_GOODBYE = 6;
	
	protected void build(short status, short ack) {
		buffer.position(0);
		buffer.limit(buffer.capacity());
		buffer.putInt(packetNum.getAndIncrement());
		buffer.putShort(status);
		buffer.putShort(ack);
	}
	
	void broadcast(RioPacket packet) throws IOException;
	
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
		// Pad with 0s
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
		this.socket.close();
	}
}
