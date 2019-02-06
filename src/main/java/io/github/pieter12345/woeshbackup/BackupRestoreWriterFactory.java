package io.github.pieter12345.woeshbackup;

/**
 * Used to create {@link BackupRestoreWriter} instances.
 * @author P.J.S. Kools
 */
public interface BackupRestoreWriterFactory {
	
	/**
	 * Creates a new {@link BackupRestoreWriter} instance.
	 * @param restoreFileDate - The date of the most recent backup part that is included in the restore.
	 * @return The new {@link BackupRestoreWriter} instance.
	 */
	public BackupRestoreWriter create(long restoreFileDate);
}
