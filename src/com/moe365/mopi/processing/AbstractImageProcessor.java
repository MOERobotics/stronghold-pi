package com.moe365.mopi.processing;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import au.edu.jcu.v4l4j.VideoFrame;

/**
 * An abstract image processor. This class is designed to allow easy
 * swapping of different image processing algorithms.
 * @author mailmindlin
 * @param <R> the result type generated by this processor
 * @since April 2016 (v0.2.0)
 */
public abstract class AbstractImageProcessor<R> implements Runnable, BiFunction<VideoFrame, VideoFrame, R> {
	/**
	 * Saturate num to [0, 255]. Saturation allows us to convert an integer
	 * to an unsigned byte. If num > 255, this method returns 255. If
	 * num < 0, this method returns 0. Otherwise, it returns num as a byte.
	 * <p>
	 * Note the difference between saturation and overflowing.
	 * </p>
	 * @param num number to saturate
	 * @return saturated byte
	 * @see <a href="https://en.wikipedia.org/wiki/Saturation_arithmetic">Saturation Arithmatic | Wikipedia</a>
	 */
	public static int saturateByte(int num) {
		return (num > 0xFF) ? (0xFF) : ((num < 0) ? 0 : num);
	}
	
	/**
	 * Split an RGB32 pixel into an array of its components
	 * @param px RGB32 color
	 * @param buf array of size>=3
	 */
	public static void splitRGB(int px, int[] buf) {
		buf[0] = (px >>> 16) & 0xFF;
		buf[1] = (px >> 8) & 0xFF;
		buf[2] = px & 0xFF;
	}
	
	/**
	 * Whether the processor is currently processing images. If this lock
	 * set to true, calls to {@link #offerFrame(VideoFrame, boolean)} should
	 * do nothing and return false.
	 */
	protected final AtomicBoolean imageLock = new AtomicBoolean(false);
	/**
	 * A frame where the flash is off.
	 */
	protected final AtomicReference<VideoFrame> frameOff = new AtomicReference<>();
	/**
	 * A frame where the flash is on.
	 */
	protected final AtomicReference<VideoFrame> frameOn = new AtomicReference<>();
	/**
	 * The minimum valid X coordinate
	 */
	protected final int frameMinX, frameMaxX, frameMinY, frameMaxY;
	/**
	 * The thread that this processor runs on
	 */
	protected Thread thread;
	/**
	 * A method to do something with the results.
	 */
	protected Consumer<R> resultConsumer;
	protected AbstractImageProcessor(int frameMinX, int frameMinY, int frameMaxX, int frameMaxY, Consumer<R> output) {
		this.frameMinX = frameMinX;
		this.frameMaxX = frameMaxX;
		if (getFrameWidth() < 0)
			throw new IllegalArgumentException("Invalid width (expect: width > 0; width = " + getFrameWidth() + ")");
		this.frameMinY = frameMinY;
		this.frameMaxY = frameMaxY;
		if (getFrameHeight() < 0)
			throw new IllegalArgumentException("Invalid height (expect: height > 0; height = " + getFrameHeight() + ")");
		
		this.resultConsumer = output;
		
		this.thread = new Thread(this);
		thread.setName("ProcessorThread-" + thread.getId());
	}
	/**
	 * Start the thread
	 * @return self
	 */
	public AbstractImageProcessor<R> start() {
		thread.start();
		return this;
	}
	
	/**
	 * Stop this processor from processing more images
	 */
	@SuppressWarnings("deprecation")
	public void stop() {
		thread.stop();
	}
	/**
	 * Offer a frame. Any VideoFrame passed into this method should be treated as if recycle() has been called on it.
	 * @param frame VideoFrame offered
	 * @param flash whether the flash was on when this frame was captured
	 * @return whether the frame was used
	 */
	public boolean offerFrame(VideoFrame frame, boolean flash) {
		if (imageLock.get()) {
			frame.recycle();
			return false;
		}
		VideoFrame oldFrame = (flash ? frameOff : frameOn).getAndSet(frame);
		if (oldFrame != null)
			oldFrame.recycle();
		return true;
	}
	/**
	 * Get the width of the valid region for this processor
	 */
	protected int getFrameWidth() {
		return frameMaxX - frameMinX;
	}
	/**
	 * Get the height of the valid region for this processor.
	 */
	protected int getFrameHeight() {
		return frameMaxY - frameMinY;
	}
	@Override
	public void run() {
		try {
			while (!Thread.interrupted()) {
				while (frameOff.get() == null || frameOn.get() == null) {
					Thread.yield();//TODO test if this is correct
					Thread.sleep(100);//TODO remove this if yield works?
				}
				
				//Attempt to lock image writes
				if (!imageLock.compareAndSet(false, true))
					throw new IllegalMonitorStateException("Did you start the thread multiple times?");
				try {
					//check again, just to be safe
					if (frameOff.get() != null && frameOn.get() != null) {
						R result;
						try {
							result = apply(frameOn.get(), frameOff.get());
						} catch(ArrayIndexOutOfBoundsException | NullPointerException e) {
							//These exceptions can probably be recovered from.
							e.printStackTrace();
							continue;
						}
						if (this.resultConsumer != null)
							this.resultConsumer.accept(result);
						
						//release the processed frames
						frameOff.getAndSet(null).recycle();
						frameOn.getAndSet(null).recycle();
					}
				} finally {
					//release the lock on images
					if (!imageLock.compareAndSet(true, false))
						throw new IllegalStateException();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} catch (Exception e) {
			//be sure to print any/all exceptions
			e.printStackTrace();
			throw e;
		}
	}
	/**
	 * Internal method to process the two frames.
	 * @param frameOn A frame that was taken with a flash
	 * @param frameOff A frame that was taken without a flash
	 * @return generated data
	 */
	@Override
	public abstract R apply(VideoFrame frameOn, VideoFrame frameOff);
}
