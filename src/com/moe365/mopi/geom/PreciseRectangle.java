package com.moe365.mopi.geom;

import java.awt.Rectangle;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Comparator;
import java.util.function.Function;

import com.moe365.mopi.util.NumberUtils;
import com.moe365.mopi.util.ReflectionUtils;

/**
 * Like a Rectangle, but immutable, and double precision.
 * 
 * @since April 2016
 * @author mailmindlin
 */
public class PreciseRectangle implements Externalizable {
	protected final double x, y, width, height;
	protected transient int hash = 0;
	
	public static Function<PreciseRectangle, PreciseRectangle> scalar(double xf, double yf, double wf, double hf) {
		return rectangle -> (rectangle.scale(xf, yf, wf, hf));
	}
	
	/**
	 * For deserializing
	 */
	protected PreciseRectangle() {
		this(0, 0, 0, 0);
	}
	
	public PreciseRectangle(Rectangle rect) {
		this(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	
	public PreciseRectangle(double x, double y, double width, double height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	/**
	 * Get the left offset for this rectangle
	 * 
	 * @return the X coordinate of the top-left corner
	 */
	public double getX() {
		return this.x;
	}
	
	/**
	 * Get the top offset for this rectangle
	 * 
	 * @return the Y coordinate of the top-left corner
	 */
	public double getY() {
		return this.y;
	}
	
	/**
	 * Get the width of the rectangle
	 * 
	 * @return the width
	 */
	public double getWidth() {
		return this.width;
	}
	
	/**
	 * Get this rectangle's height
	 * 
	 * @return the height
	 */
	public double getHeight() {
		return this.height;
	}
	
	public double getArea() {
		return width * height;
	}
	
	/**
	 * Scale the width and height by a given factor. The top-left corner is not
	 * changed.
	 * 
	 * @param factor
	 *            the factor by which to scale
	 * @return the scaled rectangle
	 */
	public PreciseRectangle scale(double factor) {
		return new PreciseRectangle(x, y, width * factor, height * factor);
	}
	
	/**
	 * Scale all coordinates by the given value.
	 * 
	 * @param xf
	 *            factor for the X coordinate
	 * @param yf
	 *            factor for the Y coordinate
	 * @param wf
	 *            factor for the width
	 * @param hf
	 *            factor for the height
	 * @return scaled rectangle
	 */
	public PreciseRectangle scale(double xf, double yf, double wf, double hf) {
		return new PreciseRectangle(x * xf, y * yf, width * wf, height * hf);
	}
	
	public static class PreciseRectangleAreaComparator implements Comparator<PreciseRectangle> {
		@Override
		public int compare(PreciseRectangle a, PreciseRectangle b) {
			return Double.compare(b.getArea(), a.getArea());
		}
	}
	
	@Override
	public int hashCode() {
		if (hash == 0) {
			final long v = (((31 + Double.doubleToLongBits(getX())) * 31
					+ Double.doubleToLongBits(getY())) * 31
					+ Double.doubleToLongBits(getWidth())) * 31
					+ Double.doubleToLongBits(getHeight());
			hash = (int) ((v >>> 32) ^ (v & 0xFFFF_FFFF));
		}
		return hash;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof PreciseRectangle) {
			PreciseRectangle other = (PreciseRectangle) o;
			return NumberUtils.sortaEqual(getX(), other.getX())
					&& NumberUtils.sortaEqual(getY(), other.getY())
					&& NumberUtils.sortaEqual(getWidth(), other.getWidth())
					&& NumberUtils.sortaEqual(getHeight(), other.getHeight());
		}
		return false;
	}
	
	@Override
	public String toString() {
		return new StringBuilder('[')
				.append(getX())
				.append(',').append(getY())
				.append(',').append(getWidth())
				.append(',').append(getHeight())
				.append(']').toString();
	}
	
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		try {
			ReflectionUtils.setDouble(this, "x", in.readDouble());
			ReflectionUtils.setDouble(this, "y", in.readDouble());
			ReflectionUtils.setDouble(this, "width", in.readDouble());
			ReflectionUtils.setDouble(this, "height", in.readDouble());
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			throw new IOException("Unable to update fields", e);
		}
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeDouble(getX());
		out.writeDouble(getY());
		out.writeDouble(getWidth());
		out.writeDouble(getHeight());
	}
}
