package io.github.pieter12345.woeshbackup;

/**
 * Represents a bounded interval, being an interval that only applies for a set duration.
 * @author P.J.S. Kools
 */
public class BoundedInterval {
	
	private final long interval;
	private final long duration;
	
	/**
	 * Creates a new bounded interval.
	 * @param interval - The interval in seconds.
	 * @param duration - The duration in seconds.
	 */
	public BoundedInterval(long interval, long duration) {
		this.interval = interval;
		this.duration = duration;
	}
	
	/**
	 * Gets the interval.
	 * @return The interval in seconds.
	 */
	public long getInterval() {
		return this.interval;
	}
	
	/**
	 * Gets the duration for which the interval applies.
	 * @return The duration in seconds.
	 */
	public long getDuration() {
		return this.duration;
	}
}
