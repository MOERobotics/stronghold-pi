package com.moe365.mopi.processing;

import au.edu.jcu.v4l4j.VideoFrame;

public static interface ImageProcessorResult<R> {
	VideoFrameMetadata frameOnMeta();
	VideoFrameMetadata frameOffMeta();
	R get();
}
