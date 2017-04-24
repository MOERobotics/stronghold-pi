package com.moe365.mopi.client;

import java.io.Closeable;
import java.io.IOException;

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
public interface RioClient extends Closeable {
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
	
	default void start() throws IOException {
		this.broadcast(new HelloRioPacket());
	}
	
	void broadcast(RioPacket packet) throws IOException;
	
	default void writeNoneFound() throws IOException {
		this.broadcast(new NoneFoundRioPacket());
	}
	
	default void writeOneFound(PreciseRectangle rect) throws IOException {
		this.broadcast(new OneFoundRioPacket(rect));
	}
	
	default void writeOneFound(double left, double top, double width, double height) throws IOException {
		this.broadcast(new OneFoundRioPacket(left, top, width, height));
	}
	
	default void writeTwoFound(PreciseRectangle rect1, PreciseRectangle rect2) throws IOException {
		this.broadcast(new TwoFoundRioPacket(rect1, rect2));
	}
	
	default void writeTwoFound(double left1, double top1, double width1, double height1, double left2, double top2, double width2, double height2) throws IOException {
		this.broadcast(new TwoFoundRioPacket(left1, top1, width1, height1, left2, top2, width2, height2));
	}
	
	default void writeError(String message) throws IOException {
		broadcast(new ErrorRioPacket(message));
	}
}
