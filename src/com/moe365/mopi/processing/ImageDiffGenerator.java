package com.moe365.mopi.processing;

public class ImageDiffGenerator implements BiFunction<BuffferedImage, BufferedImage, boolean[][]> {
	protected final int frameMinX, frameMaxX, frameMinY, frameMaxY;
	protected final int tolerance = 70;
	public boolean[][] apply(BufferedImage onImg, BufferedImage offImg) {
		// boolean array of the results. A cell @ result[y][x] is only
		// valid if processed[y][x] is true.
		boolean[][] result = new boolean[getFrameHeight()][getFrameWidth()];
		System.out.println("Calculating...");
		BufferedImage offImg = frameOff.getBufferedImage();
		BufferedImage onImg = frameOn.getBufferedImage();
		int[] pxOn = new int[3], pxOff = new int[3];
		for (int y = frameMinY; y < frameMaxY; y++) {
			//Y index into result array
			final int idxY = y - frameMinY;
			for (int x = frameMinX; x < frameMaxX; x++) {
				//X index into result array
				final int idxX = x - frameMinX;
				
				//Calculate deltas
				splitRGB(onImg.getRGB(x, y), pxOn);
				splitRGB(offImg.getRGB(x, y), pxOff);
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