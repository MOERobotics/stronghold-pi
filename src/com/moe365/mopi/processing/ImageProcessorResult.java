package com.moe365.mopi.processing;

import au.edu.jcu.v4l4j.VideoFrame;

public class ImageProcessorResult<R> {
	protected final VideoFrameMetadata onMeta;
	protected final VideoFrameMetadata offMeta;
	protected final R result;
	
	public ImageProcessorResult(VideoFrameMetadata onMeta, VideoFrameMetadata offMeta, R result) {
		this.onMeta = onMeta;
		this.offMeta = offMeta;
		this.result = result;
	}
	
	public VideoFrameMetadata frameOnMeta() {
		return this.onMeta;
	}
	
	public VideoFrameMetadata frameOffMeta() {
		return this.offMeta;
	}
	
	pubilc R get() {
		return result;
	}
}
