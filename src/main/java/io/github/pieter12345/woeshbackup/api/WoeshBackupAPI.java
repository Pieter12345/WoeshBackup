package io.github.pieter12345.woeshbackup.api;

import java.io.File;
import java.util.Set;

import io.github.pieter12345.woeshbackup.Backup;

/**
 * API for external control over WoeshBackup.
 * @author P.J.S. Kools
 */
public interface WoeshBackupAPI {
	
	/**
	 * Schedules the recurring backup task that will first execute when the backup interval time has passed
	 * since the last backup has started. Does nothing if the backup task is already running.
	 */
	public void startBackupIntervalTask();
	
	/**
	 * Stops the recurring backup task. Does nothing if no backup task is running.
	 */
	public void stopBackupIntervalTask();
	
	/**
	 * Checks whether the recurring backup task is active or not.
	 * @return {@code true} if the recurring backup task is active, {@code false} otherwise.
	 */
	public boolean backupIntervalTaskActive();
	
	/**
	 * Updates all {@link Backup}s. Does nothing if a backup is already in progress.
	 */
	public void performBackup();
	
	/**
	 * Checks if a backup is currently in progress.
	 * @return {@code true} if a backup is in progress, {@code false} otherwise.
	 */
	public boolean backupInProgress();
	
	/**
	 * Gets the time on which the last backup started.
	 * @return The time on which the last backup started.
	 * Returns 0 when unknown (no backups were started since enabling and no persistent last backup time was available).
	 */
	public long getLastBackupTime();
	
	/**
	 * Gets the backup task interval.
	 * @return The backup task interval in seconds.
	 */
	public int getBackupInterval();
	
	/**
	 * Gets the minimal required disk space to perform a backup or generate a snapshot. This is defined in the
	 * configuration file.
	 * @return The minimal required disk space to perform a backup or generate a snapshot.
	 */
	public int getMinRequiredDiskSpace();
	
	/**
	 * Removes all generated snapshots from the snapshots directory.
	 * @return The number of removed snapshots if the removal was succesful or -1 if one or multiple snapshots
	 * could not be removed.
	 */
	public int removeGeneratedSnapshots();
	
	/**
	 * (Re)loads the "config.yml" file and applies made changes.
	 */
	public void loadConfig();
	
	/**
	 * Gets all loaded backups. There exists one backup per world and a backup for the plugins directory.
	 * Due to limitations of the Minecraft server software, worlds might not be loaded yet when the plugin is enabled.
	 * Backups for these worlds will be added after 10 seconds.
	 * @return All loaded backups.
	 */
	public Set<Backup> getBackups();
	
	/**
	 * Gets the directory used for storing backups.
	 * @return The directory used for storing backups.
	 */
	public File getBackupDir();
	
	/**
	 * Gets the directory used for storing generated snapshots.
	 * @return The directory used for storing generated snapshots.
	 */
	public File getSnapshotsDir();
	
	/**
	 * Checks if debug mode is enabled.
	 * @return {@code true} if debug mode is enabled, {@code false} otherwise.
	 */
	public boolean debugEnabled();
	
	/**
	 * Enables or disables debug mode.
	 * @param enabled - {@code true} to enable and {@code false} to disable debug mode.
	 */
	public void setDebugEnabled(boolean enabled);
}
