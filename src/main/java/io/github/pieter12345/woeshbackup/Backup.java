package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * Represents a backup.
 * @author P.J.S. Kools
 */
public interface Backup {
	
	/**
	 * Creates a new backup part containing the differences between the current to-backup directory state and
	 * the state of the backup.
	 * @throws BackupException When the backup part was not created successfully.
	 * @throws InterruptedException When the current Thread is interrupted.
	 */
	public void backup() throws BackupException, InterruptedException;
	
	/**
	 * Merges all backups dated before the given timestamp, forming a new backup with the timestamp of the latest
	 * merged backup. Merged backups will be removed in the process.
	 * @param beforeDate - The timestamp threshold for merging
	 * (Backups older than this will be merged into a single backup).
	 * When a beforeDate is given that is older (lower) than the oldest backup, nothing will be merged.
	 * @throws BackupException When the backup was not merged successfully.
	 * @throws InterruptedException When the current Thread is interrupted.
	 */
	public void merge(long beforeDate) throws BackupException, InterruptedException;
	
	/**
	 * Creates a zip file containing the state of the to-backup directory at the given timestamp,
	 * rounding down to the closest older backup.
	 * @param beforeDate - The timestamp threshold for restoring.
	 * The restored backup will always be older or equal to this date.
	 * @param restoreToDir - The directory to put the restored backup in.
	 * @throws BackupException When the backup was not restored successfully.
	 * @throws InterruptedException When the current Thread is interrupted.
	 */
	public void restore(long beforeDate, File restoreToDir) throws BackupException, InterruptedException;
	
	/**
	 * Gets the directory that is being backupped by this {@link Backup}.
	 * @return The directory that is being backupped by this {@link Backup}
	 */
	public File getToBackupDir();
	
	/**
	 * Gets the free usable space within the storage. This value often is an estimation by the OS or database software.
	 * @return The free usable space.
	 * Returns -1 if the usable space could not be determined or the storage does not exist.
	 */
	public long getFreeUsableSpace();
	
	/**
	 * Sets the paths to files and directories to ignore for both backing up and restoring.
	 * @param ignorePaths - The relative paths to the files and directories to ignore. These paths are relative
	 * to the to-backup directory and will be used as "{@link #getToBackupDir()} + separator + path".
	 * Ignore paths specifying a directory should end with a file separator ('/' or '\').
	 */
	public void setIgnorePaths(Collection<String> ignorePaths);
	
	/**
	 * Gets the paths to files and directories to ignore for both backing up and restoring.
	 * @return The ignore paths. These paths are relative to the to-backup directory and are used as
	 * "{@link #getToBackupDir()} + separator + path".
	 * Ignore paths specifying a directory end with a file separator ('/' or '\').
	 * When set to null or an empty list, no files will be ignored.
	 */
	public Set<String> getIgnorePaths();
}
