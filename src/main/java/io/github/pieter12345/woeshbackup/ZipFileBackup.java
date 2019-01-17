package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import io.github.pieter12345.woeshbackup.BackupPart.ChangeType;
import io.github.pieter12345.woeshbackup.exceptions.CorruptedBackupException;

// TODO - Change class description and probably the name as well. This class can be fully abstracted from the backend.
/**
 * Represents a backup with a zip file system backend.
 * @author P.J.S. Kools
 */
public class ZipFileBackup implements Backup {
	
	private final File toBackupDir;
	private final BackupPartFactory backupPartFactory;
	private List<String> ignorePaths;
	
	private static final DateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
	
	/**
	 * Creates a new {@link ZipFileBackup} that stores backups of toBackupDir in storageDir.
	 * @param toBackupDir - The directory that will be backed up.
	 * @param backupPartFactory - The factory used to create and read backup parts.
	 */
	public ZipFileBackup(File toBackupDir, BackupPartFactory backupPartFactory) {
		this(toBackupDir, backupPartFactory, null);
	}
	
	/**
	 * Creates a new {@link ZipFileBackup} that stores backups of toBackupDir in storageDir, while ignoring files and
	 * directories specified in ignorePaths.
	 * @param toBackupDir - The directory that will be backed up.
	 * @param backupPartFactory - The factory used to create and read backup parts.
	 * @param ignorePaths - The relative paths to the files and directories to ignore. These paths are relative
	 * to the toBackupDir directory and will be used as "toBackupDir + separator + path".
	 * Ignore paths specifying a directory should end with a file separator ('/' or '\').
	 */
	public ZipFileBackup(File toBackupDir, BackupPartFactory backupPartFactory, List<String> ignorePaths) {
		Objects.requireNonNull(toBackupDir);
		Objects.requireNonNull(backupPartFactory);
		this.toBackupDir = toBackupDir;
		this.backupPartFactory = backupPartFactory;
		this.setIgnorePaths(ignorePaths);
	}
	
	@Override
	public void backup() throws BackupException, InterruptedException {
		
		// Throw an Exception if the directory to backup doesn't exist.
		if(!this.toBackupDir.isDirectory()) {
			throw new BackupException("The directory to backup does not exist: " + this.toBackupDir.getAbsolutePath());
		}
		
		// Read the backup parts.
		List<BackupPart> sortedBackups = this.readBackupParts();
		
		// Get the current backup state (all files that exist according to the backup parts).
		Map<String, BackupPart> stateMap = this.getBackupState(sortedBackups);
		
		// Create the new backup part.
		long currentTime = System.currentTimeMillis();
		BackupPart backup = this.backupPartFactory.createNew(currentTime);
		
		// Loop over all existing files and add them to the backup if they are not in the current backup state.
		final int toBackupDirPathLength = this.toBackupDir.getAbsolutePath().length() + 1; // Includes ending separator.
		FileIterator it = new FileIterator(this.toBackupDir, this.ignorePaths);
		while(it.hasNext()) {
			File file = it.next();
			String relPath = file.getAbsolutePath().substring(toBackupDirPathLength)
					+ (file.isDirectory() ? File.separator : "");
			if(!stateMap.containsKey(relPath)) {
				try {
					backup.addAddition(relPath, file);
				} catch (IOException e) {
					throw new BackupException("Failed to add file to backup: " + file.getAbsolutePath(), e);
				}
			} else {
				// Compare the file and store a modification if it is different.
				boolean backupContainsEqualFile;
				try {
					backupContainsEqualFile = stateMap.get(relPath).contains(relPath, file, true);
				} catch (IOException e) {
					throw new BackupException("Failed to compare file with file in backup state.", e);
				}
				if(!backupContainsEqualFile) {
					try {
						backup.addModification(relPath, file);
					} catch (IOException e) {
						throw new BackupException(
								"Failed to add modified file to backup: " + file.getAbsolutePath(), e);
					}
				}
				
				// Remove the handled file or directory from the state map so that only deleted files will remain.
				stateMap.remove(relPath);
			}
		}
		
		// Add all remaining files in the state map as deletions. These did not appear in the current files.
		for(String relativePath : stateMap.keySet()) {
			try {
				backup.addRemoval(relativePath);
			} catch (IOException e) {
				throw new BackupException("Failed to add removal to backup.", e);
			}
		}
		
		// Close the new backup.
		try {
			backup.close();
		} catch (IOException e) {
			try {
				backup.delete();
			} catch (IOException e1) {
				// TODO - Log as warning on console.
				e1.printStackTrace();
				throw new BackupException("Failed to close the new backup. It could also not be removed.", e);
			}
			throw new BackupException("Failed to close the new backup.", e);
		}
	}
	
	@Override
	public void merge(long beforeDate) throws BackupException, InterruptedException {
		
		// Disallow a beforeDate in the future.
		if(beforeDate > System.currentTimeMillis()) {
			throw new BackupException("The given beforeDate is in the future.");
		}
		
		// Read the backup parts.
		List<BackupPart> sortedBackups = this.readBackupParts(beforeDate);
		if(sortedBackups.isEmpty()) {
			return; // Nothing to merge.
		}
		
		// Create the new backup part.
		long backupTime = sortedBackups.get(sortedBackups.size() - 1).getCreationTime();
		// TODO - Backup with this time/name already exists. Overwrite or is subtracting a second acceptable?
		BackupPart newBackup = this.backupPartFactory.createNew(backupTime - 1000);
		
		// Merge the backups.
		for(int i = sortedBackups.size() - 1; i >= 0; i--) {
			try {
				newBackup.merge(sortedBackups.get(i));
			} catch (IOException e) {
				throw new BackupException("Failed to merge backup parts.", e);
			} catch (CorruptedBackupException e) {
				throw new BackupException(
						"Failed to merge backup part with corrupted backup part: " + e.getBackup().getName(), e);
			}
		}
		try {
			newBackup.close();
		} catch (IOException e) {
			try {
				newBackup.delete();
			} catch (IOException e1) {
				// TODO - Log as warning on console.
				e1.printStackTrace();
				throw new BackupException("Failed to close the merged backup. It could also not be removed.", e);
			}
			throw new BackupException("Failed to close the merged backup.", e);
		}
		
		// Remove the merged backups.
		for(BackupPart backup : sortedBackups) {
			try {
				backup.delete();
			} catch (IOException e) {
				// TODO - Log as warning on console.
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void restore(long beforeDate, File restoreToDir) throws BackupException, InterruptedException {
		
		// Disallow a beforeDate in the future.
		if(beforeDate > System.currentTimeMillis()) {
			throw new BackupException("The given beforeDate is in the future.");
		}
		
		// Read the backup parts.
		List<BackupPart> sortedBackups = this.readBackupParts(beforeDate);
		if(sortedBackups.isEmpty()) {
			String dateStr = BACKUP_DATE_FORMAT.format(new Date(beforeDate));
			throw new BackupException("No backup found before the given date: " + dateStr);
		}
		
		// Create the zip file to output to.
		if(!restoreToDir.exists()) {
			restoreToDir.mkdir();
		}
		long backupTime = sortedBackups.get(sortedBackups.size() - 1).getCreationTime();
		String restoreDate = BACKUP_DATE_FORMAT.format(new Date(backupTime));
		ZipFileWriter zipFileWriter = new ZipFileWriter(new File(restoreToDir, restoreDate + ".zip"));
		try {
			try {
				zipFileWriter.open();
			} catch (IOException e) {
				throw new BackupException("Failed to create restore zip file.", e);
			}
			
			// Fill the zip file with content from the backup parts.
			Set<String> handledFiles = new HashSet<String>();
			for(int i = sortedBackups.size() - 1; i >= 0; i--) {
				BackupPart backup = sortedBackups.get(i);
				Map<String, ChangeType> changes = backup.getChanges();
				for(Iterator<Entry<String, ChangeType>> it = changes.entrySet().iterator(); it.hasNext();) {
					Entry<String, ChangeType> change = it.next();
					String changePath = change.getKey();
					if(handledFiles.contains(changePath)) {
						it.remove();
					} else if(change.getValue() == ChangeType.REMOVAL) {
						handledFiles.add(changePath);
						it.remove();
					}
				}
				if(!changes.isEmpty()) {
					try {
						backup.readAll((fileEntry) -> {
							if(changes.get(fileEntry.getRelativePath()) == ChangeType.ADDITION) {
								zipFileWriter.add(fileEntry.getRelativePath(), fileEntry.getFileStream());
							}
						});
					} catch (IOException e) {
						throw new BackupException("Failed to read backup part to restore from.", e);
					} catch (InvocationTargetException e) {
						throw new BackupException("Failed to write to restore zip file.", e.getTargetException());
					}
				}
			}
			
			// Close the zip file.
			try {
				zipFileWriter.close();
			} catch (IOException e) {
				throw new BackupException("Failed to close restore zip file.", e);
			}
		} catch (Throwable t) {
			
			// Attempt to remove the created restore zip file.
			if(!zipFileWriter.getFile().delete()) {
				// TODO - Log as warning on console.
			}
			
			// Rethrow the exception.
			throw t;
		}
	}
	
	@Override
	public File getToBackupDir() {
		return this.toBackupDir;
	}
	
	@Override
	public long getFreeUsableSpace() {
		return this.backupPartFactory.getFreeUsableSpace();
	}
	
	@Override
	public void setIgnorePaths(List<String> ignorePaths) {
		this.ignorePaths = (ignorePaths == null ? Collections.emptyList() : new ArrayList<String>(ignorePaths));
		
		// Set OS-specific file separator char.
		for(int i = 0; i < this.ignorePaths.size(); i++) {
			this.ignorePaths.set(i,
					this.ignorePaths.get(i).replace('/', File.separatorChar).replace('\\', File.separatorChar));
		}
	}
	
	@Override
	public List<String> getIgnorePaths() {
		return Collections.unmodifiableList(this.ignorePaths);
	}
	
	/**
	 * Gets the backup part factory of this {@link ZipFileBackup}.
	 * @return The backup part factory.
	 */
	public BackupPartFactory getBackupPartFactory() {
		return this.backupPartFactory;
	}
	
	/**
	 * Reads all backup parts.
	 * @return A list of backup parts from oldest to most recent.
	 * @throws BackupException When a backup part is corrupted or could not be read.
	 */
	private List<BackupPart> readBackupParts() throws BackupException {
		return this.readBackupParts(-1);
	}
	
	/**
	 * Reads all backup parts dated before the given beforeDate.
	 * @param beforeDate - The timestamp threshold before which to get backup parts or -1 to get all backup parts.
	 * @return A list of backup parts from oldest to most recent.
	 * @throws BackupException When a backup part is corrupted or could not be read.
	 */
	private List<BackupPart> readBackupParts(long beforeDate) throws BackupException {
		
		// Get the backup parts.
		List<BackupPart> backupParts;
		try {
			backupParts = this.backupPartFactory.readAllBefore(beforeDate);
		} catch (IOException e) {
			throw new BackupException("Failed to read backup parts from the storage.", e);
		}
		
		// Read the backup part changes.
		for(Iterator<BackupPart> it = backupParts.iterator(); it.hasNext();) {
			BackupPart backupPart = it.next();
			try {
				backupPart.readChanges();
			} catch (IOException e) {
				throw new BackupException("Failed to read changes from backup: " + backupPart.getName() + ".", e);
			} catch (CorruptedBackupException e) {
				try {
					// TODO - Log this to console. - warning("Found corrupted backup: " + backupPart.getName());
					e.getBackup().delete();
					it.remove();
				} catch (IOException e1) {
					throw new BackupException("Failed to remove corrupted backup: " + backupPart.getName(), e1);
				}
			}
		}
		
		// Return the backup parts.
		return backupParts;
	}
	
	// Assumes sortedBackups to be sorted from oldest to latest (initial backup at index 0).
	private Map<String, BackupPart> getBackupState(List<BackupPart> sortedBackups) {
		Map<String, BackupPart> stateMap = new HashMap<String, BackupPart>();
		Set<String> removedFiles = new HashSet<String>();
		for(BackupPart backup : sortedBackups) {
			Map<String, ChangeType> changes = backup.getChanges();
			for(Entry<String, ChangeType> change : changes.entrySet()) {
				String changePath = change.getKey();
				if(!removedFiles.contains(changePath)
						&& !stateMap.containsKey(changePath) && !this.ignorePaths.contains(changePath)) {
					switch(change.getValue()) {
						case ADDITION:
							stateMap.put(changePath, backup);
							break;
						case REMOVAL:
							removedFiles.add(changePath);
							break;
						default:
							throw new InternalError("Unsupported change type: '"
									+ change.getValue() + "' in backup: " + backup.getName());
					}
				}
			}
		}
		return stateMap;
	}
}
