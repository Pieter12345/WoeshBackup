package io.github.pieter12345.woeshbackup;

import java.io.IOException;
import java.util.List;

/**
 * Used to generate backup parts.
 * @author P.J.S. Kools
 */
public interface BackupPartFactory {
	
	/**
	 * Creates a new BackupPart dated at the given time.
	 * @param time - The timestamp of the backup.
	 * @return The new BackupPart.
	 */
	public BackupPart createNew(long time);
	
	/**
	 * Reads all backup parts dated before the given time threshold from the storage. No validation is performed.
	 * @param beforeDate - The timestamp threshold before which to get backup parts or -1 to get all backup parts.
	 * @return A list of backup parts, sorted from oldest to most recent.
	 * @throws IOException When a backup parts could not be obtained from the storage.
	 */
	public List<BackupPart> readAllBefore(long beforeDate) throws IOException;
	
	/**
	 * Gets the free usable space within the storage. This value often is an estimation by the OS or database software.
	 * @return The free usable space.
	 * Returns -1 if the usable space could not be determined or the storage does not exist.
	 */
	public long getFreeUsableSpace();
	
}
