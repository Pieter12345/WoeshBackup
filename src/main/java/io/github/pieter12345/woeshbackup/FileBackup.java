package io.github.pieter12345.woeshbackup;

import java.io.File;

/**
 * Represents a backup with a file system backend.
 * @author P.J.S. Kools
 */
public interface FileBackup extends Backup {
	
	/**
	 * Gets the directory in which the backup files will be stored.
	 * @return The directory in which the backup files will be stored.
	 */
	public File getStorageDir();
	
	/**
	 * Sets the directory in which the backup files will be stored.
	 * When setting this directory, backup files in the old directory will remain untouched,
	 * but this backup will no longer use them.
	 * @param storageDir - The directory in which the backup files will be stored.
	 */
	public void setStorageDir(File storageDir);
}
