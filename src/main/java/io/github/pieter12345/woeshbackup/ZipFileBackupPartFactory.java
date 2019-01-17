package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A {@link BackupPartFactory} implementation that uses zip and metadata files in a single directory for storage.
 * @author P.J.S. Kools
 */
public class ZipFileBackupPartFactory implements BackupPartFactory {
	
	private File storageDir;
	
	private static final DateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
	
	/**
	 * Creates a new zip file backup factory using the given storage directory.
	 * @param storageDir - The directory in which the backups will be stored.
	 */
	public ZipFileBackupPartFactory(File storageDir) {
		this.storageDir = storageDir;
	}
	
	@Override
	public BackupPart createNew(long time) {
		String backupName = BACKUP_DATE_FORMAT.format(new Date(time));
		return this.createNew(time, backupName);
	}
	
	private BackupPart createNew(long time, String backupName) {
		return new ZippedBackupPart(this.storageDir, backupName, time);
	}
	
	@Override
	public List<BackupPart> readAllBefore(long beforeDate) throws IOException {
		
		// Create list to return.
		List<BackupPart> ret = new ArrayList<BackupPart>();
		
		// Get all .zip files dated before the given timestamp.
		String beforeDateStr = (beforeDate < 0 ? "" : BACKUP_DATE_FORMAT.format(new Date(beforeDate)));
		File[] files = this.storageDir.listFiles((dir, name) ->
				name.endsWith(".zip") && (beforeDateStr.isEmpty() || name.compareTo(beforeDateStr) < 0));
		if(files == null) {
			return ret; // Storage directory is not a directory, so there are no backups.
		}
		
		// Get all backup parts.
		for(File file : files) {
			
			// Validate the file name and skip if the file is not a backup.
			String fileName = file.getName().substring(0, file.getName().length() - ".zip".length());
			long time;
			try {
				time = BACKUP_DATE_FORMAT.parse(fileName).getTime();
			} catch (ParseException e) {
				continue;
			}
			
			// Create the backup part and add it to the return list.
			ret.add(this.createNew(time, fileName));
		}
		
		// Sort the backups from oldest (index 0) to newest.
		ret.sort((b1, b2) -> Long.compare(b1.getCreationTime(), b2.getCreationTime()));
		
		// Return the result.
		return ret;
	}
	
	@Override
	public long getFreeUsableSpace() {
		return (this.storageDir.exists() ? this.storageDir.getUsableSpace() : -1);
	}
	
	/**
	 * Gets the storage directory.
	 * @return The directory in which the backups will be stored.
	 */
	public File getStorageDir() {
		return this.storageDir;
	}
	
	/**
	 * Sets the storage directory.
	 * @param storageDir - The directory in which the backups will be stored.
	 */
	public void setStorageDir(File storageDir) {
		this.storageDir = storageDir;
	}
}
