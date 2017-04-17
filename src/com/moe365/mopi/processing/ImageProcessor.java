package com.moe365.mopi.processing;

import au.edu.jcu.v4l4j.VideoFrame;

/**
 * Interface for image processors to implement.
 * @author mailmindlin
 */
public interface ImageProcessor<R> {
	/**
	 * Offer a frame for processing.
	 * @return If a reference was retained to the offered frame
	 */
	boolean offerFrame(VideoFrame frame, boolean flash);
	
	/**
	 * Start processor
	 */
	void start() throws IllegalStateException;
	
	/**
	 * Attempt to stop the processor.
	 * @param timeout
	 *     Time to wait for the processor to stop. If null, this method will block until the processor stops, or an error occurs
	 * @return if the processor was successfully shut down in the given timeout
	 */
	boolean shutdown(Duration timeout) throws InterruptedException;
	
	/**
	 * (Optional operation)
	 * Set the handler that processes all results
	 */
	default void setHandler(BiConsumer<ImageProcessorResult<R>> handler) throws UnsupportedOperationException, IllegalStateException {
		throw new UnsupportedOperationException("setHandler is not supported");
	}
	
	public static interface ImageProcessorResult<R> {
		VideoFrameMetadata frameOnMeta();
		VideoFrameMetadata frameOffMeta();
		R get();
	}
}
