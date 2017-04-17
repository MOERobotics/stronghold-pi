package com.moe365.mopi.processing;

import au.edu.jcu.v4l4j.VideoFrame;

public class VideoFrameMetadata {
	protected final long captureTime;
	protected final long sequence;
	protected final boolean flash;
	
	public VideoFrameMetadata(VideoFrame frame, boolean flash) {
		this(frame.getCaptureTime(), frame.getSequenceNumber(), flash);
	}
	
	public VideoFrameMetadata(long captureTime, long sequence, boolean flash) {
		this.captureTime = captureTime;
		this.sequence = sequence;
		this.flash = flash;
	}
	
	public long getCaptureTime() {
		return this.captureTime;
	}
	
	public long getSequenceNumber() {
		return this.sequence;
	}
	
	public boolean getFlash() {
		return this.flash;
	}
}
