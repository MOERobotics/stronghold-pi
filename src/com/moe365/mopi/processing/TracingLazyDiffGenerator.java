package com.moe365.mopi.processing;

import java.awt.image.BufferedImage;
import java.util.function.BiFunction;

@Deprecated
public class TracingLazyDiffGenerator implements BiFunction<BufferedImage, BufferedImage, BinaryImage> {
	//Left-shift by column
	private static final long COL_MASK = dup(0b1000_0000);
	
	private static long dup(int b) {
		long r = b | (b << 16);
		r |= r << 8;
		r |= r << 32;
		return r;
	}
	
	protected final int frameMinX, frameMaxX, frameMinY, frameMaxY;
	protected final int tolerance;//70
	
	public TracingLazyDiffGenerator(int frameMinX, int frameMinY, int frameMaxX, int frameMaxY, int tolerance) {
		this.frameMinX = frameMinX;
		this.frameMinY = frameMinY;
		this.frameMaxX = frameMaxX;
		this.frameMaxY = frameMaxY;
		this.tolerance = tolerance;
	}
	
	private long evalTile(int[] onPx, int[] offPx) {
		long result = 0;
		//RGB array already matches our format
		for (int i = 0; i < 64; i++) {
			//Find RGB components of each pixel
			int px1 = onPx[i];
			int r1 = (px1 & 0x00FF0000) >> 16;
			int g1 = (px1 & 0x0000FF00) >> 8;
			int b1 = (px1 & 0x000000FF) >> 0;
			
			int px2 = offPx[i];
			int r2 = (px2 & 0x00FF0000) >> 16;
			int g2 = (px2 & 0x0000FF00) >> 8;
			int b2 = (px2 & 0x000000FF) >> 0;
			
			//Find difference between pixels
			int dr = r1 - r2;
			int dg = g1 - g2;
			int db = b1 - b2;
			
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
		
		//Arrays to store the RGB pixels used to calculate each chunk
		int[] onPixels = new int[64];
		int[] offPixels = new int[64];
		
		for (int v = 0; v < height / 8; v++) {
			long[] tilesRow = tiles[v];
			for (int u = 0; u < width / 8; u++) {
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
		
		return new BinaryImage() {
			
			@Override
			public boolean test(int x, int y) {
				long tile = tiles[y / 8][x / 8];
				long mask = (1L << ((y % 8) * 8 + x % 8));
				return (tile & mask) != 0;
			}
			
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
			
			@Override
			public boolean testCol(int x, int yMin, int yMax) {
				//Mask that only selects bits in our column
				final long mask = COL_MASK >>> (x % 8);
				//More masks for the first and last tiles, because we might not be using all of them
				//Basically, we're cutting off the top or the bottom rows that we won't be using.
				//Note that -1L is the identity mask (all bits are on)
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
		};
	}
}
