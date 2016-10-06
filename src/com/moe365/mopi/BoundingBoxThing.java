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
	
	public static boolean boundingBoxRecursive0(boolean[][] img, List<PreciseRectangle> bbr, final int xMin, final int xMax,
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
/**
 * Recursive function to create a List of Rectangles bbr 
 * that represent all bounding boxes of minimum size MINDIM
 * in boolean array img (which represents a thresholded image) 
 * It returns true or false to indicate if it found any boxes
 * This method attempts to split the image into 2 (first with a vertical line, then with a horizontal one) 
 * then call itself on the resulting 2 areas of the image (area set by the lim parameters)
 * The bound parameters store known edges of boxes, once the fully encased box it is added to list. 
 * Add to the bbr list are side effects and are not thread safe.  
 * It is executed in a single thread, depth first.
 * 
 * @param  img  A boolean array which represents a thresholded image
 * @param  bbr List of Rectangles bbr that represent all bounding boxes of minimum size MINDIM
 * @param  limXmin  lower limit of the x position in the area to search
 * @param  limXmax  upper limit of the x position in the area to search
 * @param  limYmin  lower limit of the y position in the area to search
 * @param  limYmax  upper limit of the y position in the area to search 
 * @param  boundXmin  location of valid left edge of box (-1 if none)
 * @param  boundXmax  location of valid right edge of box (-1 if none)
 * @param  boundYmin  location of valid top edge of box (-1 if none)
 * @param  boundYmax  location of valid bottom edge of box (-1 if none)
 * @return      if there are any bounding boxes
 * @see   Rectangle      
 */
	public static boolean boundingBoxRecursive(boolean[][] img, List<PreciseRectangle> bbr, final int limXmin, final int limXmax,
			final int limYmin, final int limYmax, int boundXmin, int boundXmax, int boundYmin, int boundYmax) {
		if (((limXmax - limXmin) < MIN_WIDTH) || ((limYmax - limYmin) < MIN_WIDTH))
			// The image is too small to possibly find any boxes that are larger than MINDIM
			// in width and height
			return false;
		// try to split the box in half vertically or horizontally and call
		// recursively on the 2 halves
		int x; //defined here since they will be reused and tested after for loops
		final int splitX = limXmin + (limXmax - limXmin) / 2; //half the width first vertical split line to try
		xLoop:
		for (x = splitX; x > limXmin; x--) {
			// Left side of half split, test all vertical lines till one doesn't go thru a contour
			
			//if a split line is free from connected pixels so ignore it and move the limits by 1
			boolean leftOff = false, rightOff = false;
			
			//top edge case
			if (test(img, x, limYmin)) {
				//indicates if pixel is connected to the right or left
				//if valid line, it is also a right edge
				leftOff = test(img, x - 1, limYmin) && test(img, x - 1, limYmin + 1);
				//if valid line, it is also a left edge
				rightOff = test(img, x + 1, limYmin) && test(img, x + 1, limYmin + 1);
				if (leftOff && rightOff)
					continue; //fully connected, try next split line
			}
			//bottom edge case
			if (test(img, x, limYmax)) {
				leftOff |= test(img, x - 1, limYmax) && test(img, x - 1, limYmax - 1);
				rightOff |= test(img, x + 1, limYmax) && test(img, x + 1, limYmax - 1);
				if (leftOff && rightOff)
					continue; //fully connected, try next split line
			}
			
			//test the middle of the line
			for (int y = limYmin + 1; y < limYmax; y++) {
				if (test(img, x, y)) {
					//if valid line, it is also a right edge
					leftOff  |= adjV(img, x - 1, y);
					//if valid line, it is also a left edge
					rightOff |= adjV(img, x + 1, y);
					if (leftOff && rightOff)
						continue xLoop; //fully connected, try next split line
				}
			}
			// valid split line, so split the rectangle and return results
			// if leftOff, we found a right edge, so include it as known edge, else
			//line is not a right edge, so don't check again by moving limit left
			return boundingBoxRecursive(img, bbr, limXmin, leftOff ? x : (x - 1), limYmin, limYmax, boundXmin, leftOff ? x : -1, -1, -1)
				// if rightOff, we found a left edge
				| boundingBoxRecursive(img, bbr, rightOff ? x : (x + 1), limXmax, limYmin, limYmax, rightOff ? x : -1, boundXmax, -1, -1);
		}
		
		// check for pixels on left edge of box since it is not a known edge
		if (boundXmin != x && updateXbound(img, limYmin, limYmax, x, true))
			boundXmin = x;

		xLoop:
		for (x = splitX + 1; x < limXmax; x++) {
			// Right side of half split, test all vertical lines till one doesn't go through a contour
			boolean leftOff = false, rightOff = false;
			if (test(img, x, limYmin)) {
				leftOff |= test(img, x - 1, limYmin) && test(img, x - 1, limYmin + 1);
				rightOff |= test(img, x + 1, limYmin) && test(img, x + 1, limYmin + 1);
				if (leftOff && rightOff)
					continue; //fully connected, try next split line
			}
			if (test(img, x, limYmax)) {
				leftOff  |= test(img, x - 1, limYmax) && test(img, x - 1, limYmax - 1);
				rightOff |= test(img, x + 1, limYmax) && test(img, x + 1, limYmax - 1);
				if (leftOff && rightOff)
					continue; //fully connected, try next split line
			}
			for (int y = limYmin + 1; y < limYmax; y++) {
				if (test(img, x, y)) {
					leftOff  |= adjV(img, x - 1, y);
					rightOff |= adjV(img, x + 1, y);
					if (leftOff && rightOff)
						continue xLoop;
				}
			}
			// valid split line, so split the rectangle and return results
			// if leftOff, we found a right edge
			return boundingBoxRecursive(img, bbr, limXmin, leftOff ? x : (x - 1), limYmin, limYmax, boundXmin, leftOff ? x : -1, -1, -1)
				// if rightOff, we found a left edge
				| boundingBoxRecursive(img, bbr, rightOff ? x : (x + 1), limXmax, limYmin, limYmax, rightOff ? x : -1, boundXmax, -1, -1);
		}
		// check for pixels on right edge of box
		if (boundXmax != x && updateXbound(img, limYmin, limYmax, x, false))
			boundXmax = x;
		
		final int splitY = limYmin + (limYmax - limYmin) / 2;
		int y;
		yLoop:
		for (y = splitY; y > limYmin; y--) {
			// Top side of half split, test all horizontal lines till one doesn't go thru a contour
			boolean topOff = false, botOff = false;
			if (test(img, limXmin, y)) {
				boolean topBool = test(img, limXmin, y - 1) && test(img, limXmin + 1, y - 1);
				boolean botBool = test(img, limXmin, y + 1) && test(img, limXmin + 1, y + 1);
				if (topBool && botBool)
					continue;
				if (topBool)
					topOff = true;
				if (botBool)
					botOff = true;
			}
			if (test(img, limXmax, y)) {
				boolean topBool = test(img, limXmax, y - 1) && test(img, limXmax - 1, y - 1);
				boolean botBool = test(img, limXmax, y + 1) && test(img, limXmax - 1, y + 1);
				if (topBool && botBool)
					continue;
				if (topBool)
					topOff = true;
				if (botBool)
					botOff = true;
			}
			for (x = limXmin + 1; x < limXmax; x++) {
				if (test(img, x, y)) {
					boolean topBool = adjH(img, x, y - 1);
					boolean botBool = adjH(img, x, y + 1);
					if (topBool && botBool)
						continue yLoop;
					if (topBool)
						topOff = true;
					if (botBool)
						botOff = true;
				}
			}
			// valid split line, so split the rectangle and return results
			// if topOff==true, we found a bottom edge
			return boundingBoxRecursive(img, bbr, limXmin, limXmax, limYmin, topOff ? y : (y - 1), -1, -1, boundYmin, topOff ? y : -1)
				// if rightOff == true, we found a top edge
				| boundingBoxRecursive(img, bbr, limXmin, limXmax, botOff ? y : (y + 1), limYmax, -1, -1, botOff ? y : -1, boundYmax);
		}
		
		// check for pixels on top edge of box
		if (boundYmin!= y && updateYbound(img, limXmin, limXmax, y, true))
			boundYmin = y;
		
		// Bottom side of half split, test all horizontal lines till one doesn't go thru a contour
		yLoop:
		for (y = splitY + 1; y < limYmax; y++) {
			boolean topOff = false, botOff = false;
			if (test(img, limXmin, y)) {
				boolean topBool = test(img, limXmin, y - 1) && test(img, limXmin + 1, y - 1);
				boolean botBool = test(img, limXmin, y + 1) && test(img, limXmin + 1, y + 1);
				if (topBool && botBool)
					continue;
				if (topBool)
					topOff = true;
				if (botBool)
					botOff = true;
			}
			if (test(img, limXmax, y)) {
				boolean topBool = test(img, limXmax, y - 1) && test(img, limXmax - 1, y - 1);
				boolean botBool = test(img, limXmax, y + 1) && test(img, limXmax - 1, y + 1);
				if (topBool && botBool)
					continue;
				if (topBool)
					topOff = true;
				if (botBool)
					botOff = true;
			}
			for (x = limXmin + 1; x < limXmax; x++) {
				if (test(img, x, y)) {
					boolean topBool = adjH(img, x, y - 1);
					boolean botBool = adjH(img, x, y + 1);
					if (topBool && botBool)
						continue yLoop;
					if (topBool)
						topOff = true;
					if (botBool)
						botOff = true;
				}
			}
			// valid split line, so split the rectangle and return results
			// if topOff, we found a bottom edge
			return boundingBoxRecursive(img, bbr, limXmin, limXmax, limYmin, topOff ? y : (y - 1), -1, -1, boundYmin, topOff ? y : -1)
				// if rightOff, we found a top edge
				| boundingBoxRecursive(img, bbr, limXmin, limXmax, botOff ? y : (y + 1), limYmax, -1, -1, botOff ? y : -1, boundYmax);
		}
		
		// check for pixels on bottom edge of box
		if (boundYmax != y && updateYbound(img, limXmin, limXmax, y, false))
			boundYmax = y;

		if ((boundXmin < boundXmax) && (boundXmin > -1) && (boundYmin < boundYmax) && (boundYmin > -1))
			//BASE CASE we have a valid bounding box described by the bound variables that cannot be futher split
			return bbr.add(new PreciseRectangle(boundXmin, boundYmin, boundXmax - boundXmin, boundYmax - boundYmin));
		return false;
	}
	
	/**
	 * 
	 * 
	 * @param img
	 * @param limXmin
	 * @param limXmax
	 * @param y
	 * @param boundYmax
	 * @param top Whether you are checking for pixels on the top of the box, or the bottom
	 * @return
	 */
	private static boolean updateYbound(boolean[][] img, int limXmin, int limXmax, int y, boolean top) {
		// check for pixels on top/bottom edge of box
		if (test(img, limXmin, y)) {
			if (test(img, limXmin, y + (top ? 1 : -1)) && test(img, limXmin + 1, y + (top ? 1 : -1)))
				return true;
		} else if (test(img, limXmax, y)) {
			if (test(img, limXmax, y + (top ? 1 : -1)) && test(img, limXmax - 1, y + (top ? 1 : -1)))
				return true;
		} else {
			for (int x = limXmin + 1; x < limXmax - 1; x++)
				if (test(img, x, y) && adjH(img, x, y + (top ? 1 : -1)))
					return true;
		}
		return false;
	}
	
	private static boolean updateXbound(boolean[][] img, final int limYmin, final int limYmax, int x, boolean left) {
		//X coordinate to the left/right of the given x
		final int xLR = x + (left ? 1 : -1);
		// check for pixels on left/right edge of box
		if (test(img, x, limYmin)) {
			if (test(img, xLR, limYmin) && test(img, xLR, limYmin + 1))
				return true;
		} else if (test(img, x, limYmax)) {
			if (test(img, xLR, limYmax) && test(img, xLR, limYmax - 1))
				return true;
		} else {
			for (int y = limYmin + 1; y < limYmax; y++)
				if (test(img, x, y) && adjV(img, xLR, y))
					return true;
		}
		return false;
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
