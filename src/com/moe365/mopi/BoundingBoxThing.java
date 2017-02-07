package com.moe365.mopi;

import java.util.List;

import com.moe365.mopi.geom.PreciseRectangle;

public class BoundingBoxThing {
	/**
	 * Smallest allowed width of a bounding box, in pixels.
	 * 
	 * Decreasing the value of this constant will find smaller blobs,
	 * but will be more computationally expensive.
	 */
	private static final int MIN_WIDTH = 20;
	/**
	 * Smallest allowed height of a bounding box.
	 * @see #MIN_WIDTH
	 */
	private static final int MIN_HEIGHT = 7;
	
	/**
	 * Find the power of two greater or equal to the value 
	 * @param value
	 * @return
	 */
	private static final int nextPowerOf2(final int value) {
		return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
	}
	
	/**
	 * Searches an image for blobs. You can think of it as a kind of binary search
	 * of a 2d array.
	 * @param img A boolean image, ordered row, column
	 * @param result List to populate with bounding boxes
	 * @param xMin Left bound of image to search (minimum index of the array)
	 * @param xMax Right bound of the image to search (maximum index of the array)
	 * @param yMin Top bound
	 * @param yMax Bottom bound
	 * @return
	 */
	public static boolean boundingBox(boolean[][] img, List<PreciseRectangle> result, final int xMin, final int xMax, final int yMin, final int yMax) {
		int width = xMax - xMin;
		int height= yMax - yMin;
		if (width < MIN_WIDTH || height < MIN_HEIGHT)
			// The image is too small to find any boxes
			return false;
		int xSplit = -2;
		int ySplit = -2;
		//It should be faster to calculate a split perpendicular to the widest axis
		if (width >= height) {
			if ((ySplit = splitH(img, xMin, xMax, yMin, yMax)) < 0)
				xSplit = splitV(img, xMin, xMax, yMin, yMax);
		} else {
			if ((xSplit = splitV(img, xMin, xMax, yMin, yMax)) < 0)
				ySplit = splitH(img, xMin, xMax, yMin, yMax);
		}
		if (xSplit >= 0)
			return boundingBox(img, result, xMin, xSplit - 1, yMin, yMax) | boundingBox(img, result, xSplit + 1, xMax, yMin, yMax);
		if (ySplit >= 0)
			return boundingBox(img, result, xMin, xMax, yMin, ySplit - 1) | boundingBox(img, result, xMin, xMax, ySplit + 1, yMax);
		return result.add(new PreciseRectangle(xMin, yMin, xMax - xMin, yMax - yMin));
	}
	
	/**
	 * Try to split the image horizontally (perpendicular to the Y axis)
	 * @param img Image
	 * @param xMin Left bound of search area
	 * @param xMax Right bound of search area
	 * @param yMin Bottom of search area
	 * @param yMax Top of search area
	 * @return The index that can be split at, or -1 if no split is found
	 */
	private static final int splitH(boolean[][] img, final int xMin, final int xMax, final int yMin, final int yMax) {
		int step = nextPowerOf2(yMax - yMin);
		while (step > 2) {
			outer: for (int split = yMin + step / 2; split < yMax; split += step) {
				boolean[] row = img[split];
				for (int x = xMin; x < xMax; x++)
					if (row[x])
						continue outer;
				return split;
			}
			step /= 2;
		}
		return -1;
	}
	
	private static final int splitV(final boolean[][] img, final int xMin, final int xMax, final int yMin, final int yMax) {
		int step = nextPowerOf2(xMax - xMin);
		while (step > 2) {
			outer: for (int split = xMin + step / 2; split < xMax; split += step) {
				for (int y = yMin; y < yMax; y++)
					if (img[y][split])
						continue outer;
				return split;
			}
			step /= 2;
		}
		return -1;
	}
}
