package com.moe365.mopi;

import java.awt.Rectangle;
import java.util.List;

import com.moe365.mopi.geom.PreciseRectangle;

public class BoundingBoxThing {
	/**
	 * Smallest allowable dimension for any side of the box.
	 * Increasing this value will increase the speed of searching through an image,
	 * while decreasing it will find more boxes
	 */
	private static final int MIN_WIDTH = 40;
	private static final int MIN_HEIGHT = 40;
	
	private static final int nextPowerOf2(final int value) {
		final int tmp = value - 1;
		return (tmp >>> 16 | tmp >>> 8 | tmp >>> 4 | tmp >>> 2 | tmp >>> 1) + 1;
	}
	
	public static boolean boundingBoxRecursive(boolean[][] img, List<PreciseRectangle> bbr, final int xMin, final int xMax,
			final int yMin, final int yMax) {
		int width = xMax - xMin;
		int height= yMax - yMin;
		if (width < MIN_WIDTH || height < MIN_HEIGHT)
			// The image is too small to possibly find any boxes that are larger than MINDIM
			// in width and height
			return false;
		if (width >= height) {
			//Compute smallest power of two greater than width
			int step = nextPowerOf2(width);
			while (step > 2) {
				//TODO optimize w/ bit twiddling
				outer:
				for (int split = xMin + (step / 2); split < xMax; split += step) {
					for (int y = yMin; y < yMax; y++)
						if (test(img, xMin + split, y))
							continue outer;
					return boundingBoxRecursive0(img, bbr, split + 1, xMax, yMin, yMax) | boundingBoxRecursive0(img, bbr, xMin, split - 1, yMin, yMax);
				}
				step /= 2;
			}
			//Complete for top/bottom
			return boundingBoxRecursiveH(img, bbr, xMin, xMax, yMin, yMax);
		}
		int step = nextPowerOf2(height);
		while (step > MIN_HEIGHT) {
			outer:
			for (int split = yMin + (step / 2); split < yMax; split += step) {
				for (int x = 0; x < height; x++)
					if (test(img, xMin + x, yMin + split))
						continue outer;
				return boundingBoxRecursive0(img, bbr, xMin, xMax, split + 1, yMax) | boundingBoxRecursive0(img, bbr, xMin, xMax, yMin, split - 1);
			}
			step /= 2;
		}
		return boundingBoxRecursiveW(img, bbr, xMin, xMax, yMin, yMax);
	}
	/**
	 * Called after the upper/lower bounds are known
	 * @param img
	 * @param bbr
	 * @param xMin
	 * @param xMax
	 * @param yMin
	 * @param yMax
	 * @return
	 */
	private static boolean boundingBoxRecursiveW(boolean[][] img, List<PreciseRectangle> bbr, final int xMin, final int xMax,
			final int yMin, final int yMax) {
		final int width = xMax - xMin;
		final int height= yMax - yMin;
		if (width < MIN_WIDTH || height < MIN_WIDTH)
			// The image is too small to possibly find any boxes that are larger than MINDIM
			// in width and height
			return false;
		//Compute smallest power of two greater than width
		int step = nextPowerOf2(width);
		while (step > 2) {
			//TODO optimize w/ bit twiddling
			outer:
			for (int split = xMin + (step / 2); split < xMax; split += step) {
				for (int y = yMin; y < yMax; y++)
					if (test(img, xMin + split, y))
						continue outer;
				return boundingBoxRecursive0(img, bbr, split + 1, xMax, yMin, yMax) | boundingBoxRecursive0(img, bbr, xMin, split - 1, yMin, yMax);
			}
			step /= 2;
		}
		return bbr.add(new PreciseRectangle(xMin, xMax, yMin, yMax));
	}
	
	private static boolean boundingBoxRecursiveH(boolean[][] img, List<PreciseRectangle> bbr, final int xMin, final int xMax,
			final int yMin, final int yMax) {
		final int width = xMax - xMin;
		final int height= yMax - yMin;
		if (width < MIN_WIDTH || height < MIN_HEIGHT)
			// The image is too small to possibly find any boxes that are larger than MINDIM
			// in width and height
			return false;
		//Compute smallest power of two greater than width
		int step = nextPowerOf2(height);
		while (step > 2) {
			//TODO optimize w/ bit twiddling
			outer:
			for (int split = yMin + (step / 2); split < yMax; split += step) {
				for (int x = xMin; x < xMax; x++)
					if (test(img, x, yMin + split))
						continue outer;
				return boundingBoxRecursive0(img, bbr, split + 1, xMax, yMin, yMax) | boundingBoxRecursive0(img, bbr, xMin, split - 1, yMin, yMax);
			}
			step /= 2;
		}
		return bbr.add(new PreciseRectangle(xMin, xMax, yMin, yMax));
	}

	// used to test a 3 pixel vertical line to determine if it is adjacent.
	// Middle pixel must meet threshold, plus on of the two others
	private static final boolean adjV(boolean[][] img, int x, int y) {
		return ((test(img, x, y)) && ((test(img, x, y - 1)) || (test(img, x, y + 1))));
	}

	// used to test a 3 pixel horizontal line to determine if it is adjacent.
	// Middle pixel must meet threshold, plus on of the two others
	private static final boolean adjH(boolean[][] img, int x, int y) {
		return ((test(img, x, y)) && ((test(img, x - 1, y)) || (test(img, x + 1, y))));
	}

	private static final boolean test(boolean[][] img, int x, int y) {
		return img[y][x];
	}
}
