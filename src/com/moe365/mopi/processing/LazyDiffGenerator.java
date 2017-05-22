package com.moe365.mopi.processing;

import java.awt.image.BufferedImage;
import java.util.function.BiFunction;

/**
 * Another implementation of the algorithm used in {@link DiffGenerator}, except that it's implemented a bit more efficiently here.
 * <p>
 * The idea is that using a boolean[][] to back our returned BinaryImage is bad because:
 * <ol>
 * <li>Each boolean requires 1 byte of space which isn't terribly efficient</li>
 * <li>We have to do a lot of lookups when testing a row/column</li>
 * <li>We can't optimize a lot of the operations at all</li>
 * </ol>
 * The solution, presented by this class, is that we can use a long[][] to back our BinaryImage, such that each long (called a tile) is a bitmap
 * for an 8x8 square of the image. This is nice, because we a) solved our space problem, as longs can be aligned on almost all architectures nicely,
 * b) we reduce the number of array lookups by 8x to 64x (compared to a boolean[][]-backed BinaryImage; depending on the operation being performed),
 * and c) we can do a lot of our (hopefully pretty common) operations in parallel with bitmasks.
 * </p>
 * <p>
 * If you aren't familiar with bitmasks, bitmaps, or bitwise operations (or just like Wikipedia), see:
 * <ol>
 * <li><a href="https://en.wikipedia.org/wiki/Bitwise_operation#AND">Bitwise AND</a></li>
 * <li><a href="https://en.wikipedia.org/wiki/Mask_(computing)">Bitmask</a></li>
 * <li><a href="https://en.wikipedia.org/wiki/Bitmap">Bitmap (not as important)</a></li>
 * </ol>
 * </p>
 * @author mailmindlin
 * @see DiffGenerator
 */
public class LazyDiffGenerator implements BiFunction<BufferedImage, BufferedImage, BinaryImage> {
	//Left-shift by column
	private static final long COL_MASK = dup(0b1000_0000);
	
	/**
	 * Utility method to duplicate a byte to all bytes of a long.
	 * This method is used for making masks
	 * For example, <code>dup(0xF0) == 0xF0F0F0F0F0F0F0F0L</code>.
	 * @param b Byte to duplicate. Range should be {@code 0 <= b <= 255}.
	 * @return
	 */
	private static long dup(int b) {
		long r = b | (b << 16);//Fill in bytes 0 & 2
		r |= r << 8;//Fill in bytes 1 & 3 also
		r |= r << 32;//Duplicate to 2nd half of long
		return r;
	}
	
	//Frame bounds
	protected final int frameMinX, frameMaxX, frameMinY, frameMaxY;
	protected final int tolerance;//70
	
	public LazyDiffGenerator(int frameMinX, int frameMinY, int frameMaxX, int frameMaxY, int tolerance) {
		this.frameMinX = frameMinX;
		this.frameMinY = frameMinY;
		this.frameMaxX = frameMaxX;
		this.frameMaxY = frameMaxY;
		this.tolerance = tolerance;
	}
	
	/**
	 * Calculates the delta between two 8x8 squares of the images.
	 * 
	 * TODO maybe explain tile format or something
	 * 
	 * @param onPx
	 *            Pixels from the 'on' image
	 * @param offPx
	 *            Pixels from thie 'off' image
	 * @return Delta calculated
	 */
	private long evalTile(int[] onPx, int[] offPx) {
		long result = 0;
		//RGB array already matches our format
		for (int i = 0; i < 64; i++) {
			//Find RGB components of each pixel
			int px1 = onPx[i];
			int r1 = (px1 & 0x00FF0000) >> 16;
			int g1 = (px1 & 0x0000FF00) >> 8;
			//int b1 = (px1 & 0x000000FF) >> 0;
			
			int px2 = offPx[i];
			int r2 = (px2 & 0x00FF0000) >> 16;
			int g2 = (px2 & 0x0000FF00) >> 8;
			//int b2 = (px2 & 0x000000FF) >> 0;
			
			//Find difference between pixels
			int dr = r1 - r2;
			int dg = g1 - g2;
			//int db = b1 - b2;
			
			if (dg > tolerance && (dr < dg - 10 || dr < tolerance))
				result |= 1L << i;
		}
		return result;
	}
	
	@Override
	public BinaryImage apply(BufferedImage onImg, BufferedImage offImg) {
		/*
		 * Each long contains 64 pixels.
		 * Stored in format:
		 * 00000000
		 * 11111111
		 * 22222222
		 * (etc.)
		 */
		final int width = (this.frameMaxX - this.frameMinX);
		final int height = (this.frameMaxY - this.frameMinY);
		//Round up (if not multiple of 8)
		final int xTiles = (width + 7) / 8;
		final int yTiles= (height + 7) / 8;
		final long[][] tiles = new long[yTiles][xTiles];
		
		//Arrays to store the RGB pixels used to calculate each chunk. Their values change every iteration,
		//but we don't have to allocate them every time this way.
		int[] onPixels = new int[64];
		int[] offPixels = new int[64];
		
		for (int v = 0; v < height / 8; v++) {
			long[] tilesRow = tiles[v];
			for (int u = 0; u < width / 8; u++) {
				//Grab 2 8x8 squares
				onImg.getRGB(u * 8, v * 8, 8, 8, onPixels, 0, 8);
				offImg.getRGB(u * 8, v * 8, 8, 8, offPixels, 0, 8);
				tilesRow[u] = evalTile(onPixels, offPixels);
			}
			
			if (width % 8 != 0) {
				int u = width / 8;
				//Rightmost tile column isn't complete
				onImg.getRGB(u * 8, v * 8, width - u, 8, onPixels, 0, 8);
				offImg.getRGB(u * 8, v * 8, width - u, 8, offPixels, 0, 8);
				tilesRow[u] = evalTile(onPixels, offPixels);
			}
		}
		if (height % 8 != 0) {
			//Complete bottom tile row
			int v = height / 8;
			long[] tilesRow = tiles[v];
			for (int u = 0; u < width / 8; u++) {
				//Get chunk
				onImg.getRGB(u * 8, v * 8, 8, 8, onPixels, 0, 8);
				offImg.getRGB(u * 8, v * 8, 8, 8, offPixels, 0, 8);
				tilesRow[u] = evalTile(onPixels, offPixels);
			}
			
			if (width % 8 != 0) {
				int u = width / 8;
				//Rightmost tile column isn't complete
				onImg.getRGB(u * 8, v * 8, width - u * 8, 8, onPixels, 0, 8);
				offImg.getRGB(u * 8, v * 8, width - u * 8, 8, offPixels, 0, 8);
				tilesRow[u] = evalTile(onPixels, offPixels);
			}
		}
		
		return new TiledBinaryImage(tiles);
	}
	
	/**
	 * A binary array backed by a long[][] (where each long is an 8x8 bitmask).
	 * Gives us a bit (no pun intended) better performance.
	 * @author mailmindlin
	 */
	public static class TiledBinaryImage implements BinaryImage {
		protected final long[][] tiles;
		
		public TiledBinaryImage(long[][] tiles) {
			this.tiles = tiles;
		}
		
		/**
		 * Test a single pixel of this image.
		 * <p>
		 * We have to assume that this operation isn't as common as others such
		 * as {@link #testCol(int, int, int)} or {@link #testRow(int, int, int)}
		 * , because this method will actually perform a (little) bit worse than
		 * a <code>boolean[][]</code> lookup, so hopefully we regain most of the
		 * cost through the other operations.
		 * </p>
		 * <p>
		 * This method first looks up the tile that the pixel should reside on,
		 * then generates a mask for only that pixel. For example, looking up
		 * the value of the pixel at (3,2):
		 * 
		 * <pre>
		 *     ┌                 ┐
		 *     | - - - - - - - - |
		 *     | - - 1 1 1 - - - |
		 *     | - - - - 1 - - - |
		 * T = | - - 1 1 1 - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     └                 ┘
		 *     ┌                 ┐
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - 1 - - - - |
		 * M = | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     └                 ┘
		 * </pre>
		 * 
		 * Then <code>(T & M) == 0</code> should get the value of that bit.
		 * </p>
		 * <p>
		 * This method has undefined behavior if testing outside the range
		 * covered by this image. It may throw an ArrayIndexOutOfBoundsException
		 * or emit nasal demons.
		 * </p>
		 * 
		 * @param x
		 * @param y
		 * @return
		 */
		@Override
		public boolean test(int x, int y) {
			long tile = tiles[y / 8][x / 8];
			long mask = (1L << ((y % 8) * 8 + x % 8));
			return (tile & mask) != 0;
		}
		
		/**
		 * This method tests if <i>any</i> pixel on the row {@code y} between
		 * {@code xMin} and {@code xMax} is set.
		 * <p>
		 * We can accelerate this operation with bitmasks (yay!), to test larger
		 * swathes at a time (approaching 8x speed). Assuming we have a tile
		 * (T):
		 * 
		 * <pre>
		 *     ┌                 ┐
		 *     | - - - - - - - - |
		 *     | - - 1 1 1 - - - |
		 *     | - - - - 1 - - - |
		 * T = | - - 1 1 1 - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     └                 ┘
		 * </pre>
		 * 
		 * and we're testing all the pixels in y=5, we can build a mask (M):
		 * 
		 * <pre>
		 *     ┌                 ┐
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 * M = | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     | 1 1 1 1 1 1 1 1 |
		 *     | - - - - - - - - |
		 *     | - - - - - - - - |
		 *     └                 ┘
		 * </pre>
		 * 
		 * Then {@code (T & M) == 0} should be enough to test all the pixels on
		 * the row y=5 on tile T.
		 * </p>
		 * <p>
		 * Note that for the first and last tile, we may have to use a second
		 * mask, to ignore all the columns that are not in the range [xMin,
		 * xMax].
		 * </p>
		 * <p>
		 * As far as speed is concerned, we're only doing 1/64 (if unoptimized)
		 * to 1/8 (best case) of the lookups as a simple boolean[][]-based
		 * BinaryImage would have to do (and therefore fewer bounds checking),
		 * and considering that most of the 'extra' operations (e.g., to build
		 * the masks) are cheap for the CPU to do, this method does a lot
		 * better.
		 * <p>
		 * This method has undefined behavior if testing outside the range
		 * covered by this image. It may throw an ArrayIndexOutOfBoundsException
		 * or emit nasal demons.
		 * </p>
		 * 
		 * @param y
		 *            Y coordinate of row to test
		 * @param xMin
		 *            Minimum x coordinate on row to test (inclusive)
		 * @param xMax
		 *            Maximum x coordinate on row to test (inclusive)
		 * @return True iff any pixel in the given range is on
		 */
		@Override
		public boolean testRow(int y, int xMin, int xMax) {
			//We can test rows and cols faster
			final long mask = 0xFFL << (8 * (y % 8));
			//Mask the columns that are out of range on the first and last tiles
			final long maskI = dup(0xFF >> (xMin & 8));
			//TODO could do with Integer.reverseBytes, but not sure if actually faster
			final long maskF = dup(0xFF & (0xFF << xMax));
			
			final int v = y / 8;
			final int uMin = xMin / 8;
			final int uMax = xMax / 8;
			final long[] tileRow = tiles[v];
			
			for (int u = uMin; u <= uMax; u++) {
				long tile = tileRow[u] & mask;
				if (tile != 0) {//This shouldn't be true often
					if (u == uMin)
						tile &= maskI;
					if (u == uMax)
						tile &= maskF;
					if (tile != 0)
						return true;
				}
			}
			return false;
		}
		
		/**
		 * Basically the same as {@link #testRow(int, int, int)}, just rotated
		 * for columns. We get a little bit less performance out of this method
		 * compared to {@link #testRow(int, int, int)}, just because our backing
		 * array is y-indexed (and we loose some CPU caching, I think), but it's
		 * still much better than a boolean[][]-based implementation.
		 * 
		 * @param x
		 *            Column to test
		 * @param yMin
		 *            Minimum y coordinate to test (inclusive)
		 * @param yMax
		 *            Maximum y coordinate to test (inclusive)
		 * @return True iff any pixel in the range specified is on
		 */
		@Override
		public boolean testCol(int x, int yMin, int yMax) {
			//Mask that only selects bits in our column
			final long mask = COL_MASK >>> (x % 8);
			//More masks for the first and last tiles, because we might not be using all of them
			//Basically, we're cutting off the top or the bottom rows that we won't be using.
			//Note that -1L is the identity mask (all bits are on)
			//TODO move into loop, because we might not use these masks every time this method is called,
			//so let's not calculate them if we don't have to (the cost of calculating these isn't much, but it's nonzero).
			final long maskI = (-1L) << ((yMin % 8) * 8);
			final long maskF = (-1L) >>> ((yMax % 8) * 8);
			
			final int u = x / 8;
			final int vMin = yMin / 8;
			final int vMax = yMax / 8;
			
			for (int v = vMin; v <= vMax; v++) {
				long tile = tiles[v][u] & mask;
				if (tile != 0) {
					if (v == vMin)
						tile &= maskI;
					if (v == vMax)
						tile &= maskF;
					if (tile != 0)
						return true;
				}
			}
			return false;
		}
	}
}