package io.github.pieter12345.woeshbackup;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A cancellable implementation of {@link BukkitTask}.
 * @author P.J.S. Kools
 */
public class CancellableBukkitTask implements BukkitTask {
	
	private final BukkitTask task;
	private boolean isCancelled = false;
	
	public CancellableBukkitTask(BukkitTask task) {
		this.task = task;
	}
	
	@Override
	public void cancel() {
		this.task.cancel();
		this.isCancelled = true;
	}
	
	@Override
	public Plugin getOwner() {
		return this.task.getOwner();
	}
	
	@Override
	public int getTaskId() {
		return this.task.getTaskId();
	}
	
	@Override
	public boolean isSync() {
		return this.task.isSync();
	}
	
	public boolean isCancelled() {
		return this.isCancelled;
	}
}
