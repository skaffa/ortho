package dev.ortho.core;

/** Rotation from a shape's local frame to world axes: up-axis mapping, then tilt, then spin. */
public final class Orientation {
	/** Row-major 3x3, local to world. */
	final double[] m;

	private Orientation(double[] m) {
		this.m = m;
	}

	public static Orientation of(Stencil.Axis up, int spinDegrees, int tiltDegrees) {
		double[] axis = switch (up) {
			case Y -> new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
			// local Y -> world X (rotation of -90 degrees around Z)
			case X -> new double[] {0, 1, 0, -1, 0, 0, 0, 0, 1};
			// local Y -> world Z (rotation of 90 degrees around X)
			case Z -> new double[] {1, 0, 0, 0, 0, -1, 0, 1, 0};
		};
		double t = Math.toRadians(tiltDegrees);
		double ct = clean(Math.cos(t));
		double st = clean(Math.sin(t));
		double[] tilt = {1, 0, 0, 0, ct, -st, 0, st, ct};
		double s = Math.toRadians(spinDegrees);
		double cs = clean(Math.cos(s));
		double ss = clean(Math.sin(s));
		double[] spin = {cs, 0, ss, 0, 1, 0, -ss, 0, cs};
		return new Orientation(mul(mul(axis, tilt), spin));
	}

	/** True when every axis maps onto a world axis (all rotations are multiples of 90 degrees). */
	public boolean axisAligned() {
		for (double v : m) {
			if (v != 0 && v != 1 && v != -1) {
				return false;
			}
		}
		return true;
	}

	/** local -> world */
	public double[] apply(double x, double y, double z) {
		return new double[] {
				m[0] * x + m[1] * y + m[2] * z,
				m[3] * x + m[4] * y + m[5] * z,
				m[6] * x + m[7] * y + m[8] * z};
	}

	private static double clean(double v) {
		// cos(90 deg) is 6e-17, not 0; exact values keep axis-aligned shapes exact
		double r = Math.rint(v);
		return Math.abs(v - r) < 1e-12 ? r : v;
	}

	private static double[] mul(double[] a, double[] b) {
		double[] r = new double[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				double sum = 0;
				for (int k = 0; k < 3; k++) {
					sum += a[i * 3 + k] * b[k * 3 + j];
				}
				r[i * 3 + j] = sum;
			}
		}
		return r;
	}
}
