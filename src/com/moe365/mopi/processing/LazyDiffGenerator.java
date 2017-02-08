package com.moe365.mopi.processing;

import java.awt.image.BufferedImage;
import java.util.function.BiFunction;

public class LazyDiffGenerator implements BiFunction<BufferedImage, BufferedImage, BinaryImage> {
	protected final int frameMinX, frameMaxX, frameMinY, frameMaxY;
	protected final int tolerance;//70
	
	public LazyDiffGenerator(int frameMinX, int frameMinY, int frameMaxX, int frameMaxY, int tolerance) {
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
			int px1 = onPixels[i];
			int r1 = (px1 & 0x00FF0000) >> 16;
			int g1 = (px1 & 0x0000FF00) >> 8;
			int b1 = (px1 & 0x000000FF) >> 0;
			
			int px2 = offPixels[i];
			int r2 = (px2 & 0x00FF0000) >> 16;
			int g2 = (px2 & 0x0000FF00) >> 8;
			int b2 = (px2 & 0x000000FF) >> 0;
			
			//Find difference between pixels
			int dr = r1 - r2;
			int dg = g1 - g2;
			int db = b1 - b2;
			
			if (dg > tolerance && (dr < dg - 10 || dr < tolerance))
				result |= 1L << (64 - i);
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
		int[] onPixels = new int[64];
		int[] offPixels = new int[64];
		for (int v = 0; v < height / 8; v++) {
			long[] tilesRow = tiles[v];
			for (int u = 0; u < width / 8; u++) {
				//Get chunk
				onImg.getRGB(u * 8, v * 8, 8, 8, onPixels, 0, 8);
				offImg.getRGB(u * 8, v * 8, 8, 8, offPixels, 0, 8);
				tilesRow[u] = evalTile(onPixels, offPixels);
			}
			
			if (width & 8 != 0) {
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
			
			if (width & 8 != 0) {
				int u = width / 8;
				//Rightmost tile column isn't complete
				onImg.getRGB(u * 8, v * 8, width - u * 8, 8, onPixels, 0, 8);
				offImg.getRGB(u * 8, v * 8, width - u * 8, 8, offPixels, 0, 8);
				tilesRow[u] = evalTile(onPixels, offPixels);
			}
		}
		
		return new BinaryImage() {
			//Left-shift by column
			private static final long COL_MASK = 0x0101010101010101;
			
			@Override
			public boolean test(int x, int y) {
				long tile = tiles[x / 8][y / 8];
				return tile & (1L << ((y % 8) * 8 + x % 8));
			}
			
			@Override
			public boolean testRow(int y, int xMin, int xMax) {
				//We can test rows and cols faster
				final long mask = 0xFFL << (8 * (y % 8));
				final long startMask = -1L;
				final long endMask = -1L;
				int v = y / 8;
				int u = xMin / 8;
				//TODO finish
				return false;
			}
			
			@Override
			public boolean testCol(int x, int yMin, int yMax) {
				//Mask that only selects bits in our column
				final long mask = COL_MASK << (x % 8);
				final long mask0 = (-1L) >>> ((yMin % 8) * 8);
				final long maskF;
				
				final int u = x / 8;
				int v = yMin / 8;
				final int vMax = yMax / 8;
				
				long tile = tiles[v][u] & mask;
				if (tile & mask0 != 0) {
					if (v == vMax && (tile & maskF == 0))
						return false;
					return true;
				}
				for (; v <= vMax; v++)
					if ((tile = tiles[v][u] & mask) != 0)
						return !(v == vMax && (tile & maskF == 0));
				
				return false;
			}
		};
		// boolean array of the results. A cell @ result[y][x] is only
		boolean[][] result = new boolean[this.frameMaxY - this.frameMinY][this.frameMaxX - this.frameMinX];
		System.out.println("Calculating...");
		int[] pxOn = new int[3], pxOff = new int[3];
		for (int y = frameMinY; y < frameMaxY; y++) {
			//Y index into result array
			final int idxY = y - frameMinY;
			for (int x = frameMinX; x < frameMaxX; x++) {
				//X index into result array
				final int idxX = x - frameMinX;
				
				//Calculate deltas
				AbstractImageProcessor.splitRGB(onImg.getRGB(x, y), pxOn);
				AbstractImageProcessor.splitRGB(offImg.getRGB(x, y), pxOff);
				int dR = pxOn[0] - pxOff[0];
				int dG =  pxOn[1] - pxOff[1];
				int dB =  pxOn[2] - pxOff[2];
				
				//Decide whether the pixel is on. This predicate is kinda magic-y, but
				//basically, it requires green to increase by a lot, but red not much.
				if (dG > tolerance && (dR < dG - 10 || dR < tolerance))//TODO fix
					result[idxY][idxX] = true;
			}
		}
		return result;
	}
}