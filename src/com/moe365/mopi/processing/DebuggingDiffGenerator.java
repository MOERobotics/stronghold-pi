package com.moe365.mopi.processing;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

public class DebuggingDiffGenerator extends DiffGenerator {
	
	protected final AtomicInteger i = new AtomicInteger(0);
	public BufferedImage imgFlt;

	public DebuggingDiffGenerator(int frameMinX, int frameMinY, int frameMaxX, int frameMaxY, int tolerance) {
		super(frameMinX, frameMinY, frameMaxX, frameMaxY, tolerance);
	}
	
	@Override
	public BinaryImage apply(BufferedImage onImg, BufferedImage offImg) {
		// boolean array of the results. A cell @ result[y][x] is only
		int height = this.frameMaxY - this.frameMinY;
		int width = this.frameMaxX - this.frameMinX;
		boolean[][] result = new boolean[height][width];
		System.out.println("Calculating...");
		BufferedImage imgR = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		BufferedImage imgG = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		BufferedImage imgB = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		imgFlt = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		System.out.println("Calculating...");
		System.out.println("CM: " + onImg.getColorModel());
		System.out.println("CMCL: " + onImg.getColorModel().getClass());
		int[] pxOn = new int[3], pxOff = new int[3];
		for (int y = frameMinY; y < frameMaxY; y++) {
			//Y index into result array
			final int idxY = y - frameMinY;
			for (int x = frameMinX; x < frameMaxX; x++) {
				//X index into result array
				final int idxX = x - frameMinX;
				
				AbstractImageProcessor.splitRGB(onImg.getRGB(x, y), pxOn);
				AbstractImageProcessor.splitRGB(offImg.getRGB(x, y), pxOff);
				int dR = pxOn[0] - pxOff[0];
				int dG =  pxOn[1] - pxOff[1];
				int dB =  pxOn[2] - pxOff[2];
				if (dG > tolerance && (dR < dG - 10 || dR < tolerance)) {//TODO fix
					result[idxY][idxX] = true;
					imgFlt.setRGB(x, y, 0xFFFFFF);
				}
				imgR.setRGB(x, y, AbstractImageProcessor.saturateByte(dR) << 16);
				imgG.setRGB(x, y, AbstractImageProcessor.saturateByte(dG) << 8);
				imgB.setRGB(x, y, AbstractImageProcessor.saturateByte(dB));
			}
		}
		try {
			File imgDir = new File("img");
			if (!(imgDir.exists() && imgDir.isDirectory()))
				imgDir.mkdirs();
			int num = i.getAndIncrement();
			File file = new File(imgDir, "delta" + num + ".png");
			System.out.println("Saving image to " + file);
			ImageIO.write(imgR, "PNG", new File(imgDir, "dr" + num + ".png"));
			ImageIO.write(imgG, "PNG", new File(imgDir, "dg" + num + ".png"));
			ImageIO.write(imgB, "PNG", new File(imgDir, "db" + num + ".png"));
			ImageIO.write(onImg, "PNG", new File(imgDir, "on" + num + ".png"));
			ImageIO.write(offImg, "PNG", new File(imgDir, "off" + num + ".png"));
			ImageIO.write(imgFlt, "PNG", file);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new BinaryImage() {
			@Override
			public boolean test(int x, int y) {
				return result[y][x];
			}
		};
	}
}
