package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
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
import java.util.logging.Logger;

import io.github.pieter12345.woeshbackup.BackupPart.ChangeType;
import io.github.pieter12345.woeshbackup.exceptions.BackupException;
import io.github.pieter12345.woeshbackup.exceptions.CorruptedBackupException;
import io.github.pieter12345.woeshbackup.utils.Utils;

/**
 * A {@link Backup} implementation that backups a directory and its contents from the file system.
 * @author P.J.S. Kools
 */
public class SimpleBackup implements Backup {
	
	private final File toBackupDir;
	private final BackupPartFactory backupPartFactory;
	private final Logger logger;
	private Set<String> ignorePaths;
	
	private static final DateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
	
	/**
	 * Creates a new {@link SimpleBackup} that stores backups of toBackupDir in backup parts generated by the
	 * given backup part factory.
	 * @param toBackupDir - The directory that will be backed up.
	 * @param backupPartFactory - The factory used to create and read backup parts.
	 * @param logger - The logger to use for info/warning/error logging.
	 */
	public SimpleBackup(File toBackupDir, BackupPartFactory backupPartFactory, Logger logger) {
		this(toBackupDir, backupPartFactory, logger, null);
	}
	
	/**
	 * Creates a new {@link SimpleBackup} that stores backups of toBackupDir in backup parts generated by the
	 * given backup part factory, while ignoring files and directories specified by the given ignorePaths.
	 * @param toBackupDir - The directory that will be backed up.
	 * @param backupPartFactory - The factory used to create and read backup parts.
	 * @param logger - The logger to use for info/warning/error logging.
	 * @param ignorePaths - The relative paths to the files and directories to ignore. These paths are relative
	 * to the toBackupDir directory and will be used as "toBackupDir + separator + path".
	 * Ignore paths specifying a directory should end with a file separator ('/' or '\').
	 */
	public SimpleBackup(File toBackupDir,
			BackupPartFactory backupPartFactory, Logger logger, Collection<String> ignorePaths) {
		Objects.requireNonNull(toBackupDir);
		Objects.requireNonNull(backupPartFactory);
		this.toBackupDir = toBackupDir;
		this.backupPartFactory = backupPartFactory;
		this.logger = logger;
		this.setIgnorePaths(ignorePaths);
	}
	
	@Override
	public void backup(long currentTime) throws BackupException, InterruptedException {
		
		// Throw an Exception if the directory to backup doesn't exist.
		if(!this.toBackupDir.isDirectory()) {
			throw new BackupException("The directory to backup does not exist: " + this.toBackupDir.getAbsolutePath());
		}
		
		// Read the backup parts.
		List<BackupPart> sortedBackups = this.readBackupParts();
		
		// Get the current backup state (all files that exist according to the backup parts).
		Map<String, BackupPart> stateMap = this.getBackupState(sortedBackups);
		
		// Create the new backup part.
		BackupPart backup = this.backupPartFactory.createNew(currentTime);
		try {
			
			// Loop over all existing files and add them to the backup if they are not in the current backup state.
			int toBackupDirPathLength = this.toBackupDir.getAbsolutePath().length() + 1; // Includes ending separator.
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
						throw new BackupException(
								"Failed to compare file with file in backup state: " + file.getAbsolutePath(), e);
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
			for(String relPath : stateMap.keySet()) {
				try {
					backup.addRemoval(relPath);
				} catch (IOException e) {
					throw new BackupException("Failed to add removal to backup: " + relPath, e);
				}
			}
			
			// Close the new backup.
			try {
				backup.close();
			} catch (IOException e) {
				throw new BackupException("Failed to close the new backup.", e);
			}
		} catch (BackupException e) {
			
			// Delete backup part.
			try {
				backup.delete();
			} catch (IOException e1) {
				this.logger.severe(
						"Failed to remove a failed backup. Here's the stacktrace:\n" + Utils.getStacktrace(e1));
			}
			
			// Rethrow exception.
			throw e;
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
				throw new BackupException("Failed to merge backup part with corrupted backup part: "
						+ this.toBackupDir.getName() + "/" + e.getBackup().getName(), e);
			}
		}
		try {
			newBackup.close();
		} catch (IOException e) {
			try {
				newBackup.delete();
			} catch (IOException e1) {
				this.logger.severe(
						"Failed to remove a failed merge backup. Here's the stacktrace:\n" + Utils.getStacktrace(e1));
				throw new BackupException("Failed to close the merged backup. It could also not be removed.", e);
			}
			throw new BackupException("Failed to close the merged backup.", e);
		}
		
		// Remove the merged backups.
		for(BackupPart backup : sortedBackups) {
			try {
				backup.delete();
			} catch (IOException e) {
				this.logger.severe(
						"Failed to remove a merged backup. Here's the stacktrace:\n" + Utils.getStacktrace(e));
			}
		}
	}
	
	@Override
	public void merge(List<BoundedInterval> intervals, long currentTime) throws BackupException, InterruptedException {
		
		// Return if no intervals were given.
		if(intervals.size() == 0) {
			return;
		}
		
		// Calculate the start and end time of the last interval (start >= end).
		long intervalStartTime = currentTime;
		long intervalEndTime = currentTime;
		int lastIntervalIndex = intervals.size() - 1;
		for(int i = 0; i < intervals.size(); i++) {
			BoundedInterval interval = intervals.get(i);
			lastIntervalIndex = i;
			intervalStartTime = intervalEndTime;
			if(interval.getDuration() <= -1) {
				intervalEndTime = 0; // Infinite interval is interpreted as timestamp 0.
				break;
			} else {
				intervalEndTime -= 1000L * interval.getDuration();
			}
		}
		
		// Merge backups older than the last interval.
		if(intervalEndTime > 0) {
			this.merge(intervalEndTime);
		}
		
		// Read the backup parts.
		List<BackupPart> sortedBackups = this.readBackupParts();
		if(sortedBackups.isEmpty()) {
			return; // Nothing to merge.
		}
		
		// Apply merging per interval, going from oldest to latest.
		int intervalIndex = lastIntervalIndex;
		BoundedInterval interval = intervals.get(intervalIndex);
		int lastAcceptedBackupIndex = -1;
		long lastAcceptedBackupTime = -1;
		for(int i = 0; i < sortedBackups.size(); i++) {
			BackupPart backup = sortedBackups.get(i);
			accepted: {
				
				// Detect entry of the next interval.
				if(backup.getCreationTime() > intervalStartTime) {
					
					// Update interval to the interval containing the current backup.
					do {
						intervalIndex--;
						interval = intervals.get(intervalIndex);
						intervalStartTime += 1000L * interval.getDuration();
					} while(backup.getCreationTime() > intervalStartTime);
					
					// The last backup of a new interval is always included.
					break accepted;
				}
				
				// Detect valid intervals, accepting the most recent and oldest backup as well.
				if(interval.getInterval() == -1
						|| (backup.getCreationTime() - lastAcceptedBackupTime) / 1000 >= interval.getInterval()
						|| i == sortedBackups.size() - 1 || i == 0) {
					break accepted;
				}
				
				// Current backup has a too small interval.
				continue; // Will be merged with the next accepted backup.
			}
			
			// Backup is accepted. Merge unaccepted backups if they are available.
			if(lastAcceptedBackupIndex != i - 1) {
				
				// Create the new backup part.
				long backupTime = backup.getCreationTime();
				// TODO - Backup with this time/name already exists. Overwrite or is subtracting a second acceptable?
				BackupPart newBackup = this.backupPartFactory.createNew(backupTime - 1000);
				
				// Merge the backups.
				for(int j = i; j > lastAcceptedBackupIndex; j--) {
					try {
						newBackup.merge(sortedBackups.get(j));
					} catch (IOException e) {
						throw new BackupException("Failed to merge backup parts.", e);
					} catch (CorruptedBackupException e) {
						throw new BackupException("Failed to merge backup part with corrupted backup part: "
								+ this.toBackupDir.getName() + "/" + e.getBackup().getName(), e);
					}
				}
				try {
					newBackup.close();
				} catch (IOException e) {
					try {
						newBackup.delete();
					} catch (IOException e1) {
						this.logger.severe("Failed to remove a failed merge backup. Here's the stacktrace:\n"
								+ Utils.getStacktrace(e1));
						throw new BackupException(
								"Failed to close the merged backup. It could also not be removed.", e);
					}
					throw new BackupException("Failed to close the merged backup.", e);
				}
				
				// Remove the merged backups.
				for(int j = i; j > lastAcceptedBackupIndex; j--) {
					try {
						sortedBackups.get(j).delete();
					} catch (IOException e) {
						this.logger.severe(
								"Failed to remove a merged backup. Here's the stacktrace:\n" + Utils.getStacktrace(e));
					}
				}
			}
			
			// Update last accepted backup data.
			lastAcceptedBackupIndex = i;
			lastAcceptedBackupTime = sortedBackups.get(lastAcceptedBackupIndex).getCreationTime();
		}
	}
	
	@Override
	public void restore(long beforeDate, BackupRestoreWriterFactory restoreWriterFactory)
			throws BackupException, InterruptedException {
		
		// Disallow a beforeDate in the future.
		if(beforeDate > System.currentTimeMillis()) {
			throw new BackupException("The given beforeDate is in the future.");
		}
		
		// Read the backup parts.
		List<BackupPart> sortedBackups = this.readBackupParts(beforeDate);
		if(sortedBackups.isEmpty()) {
			throw new BackupException("No backup found before the given date: "
					+ BACKUP_DATE_FORMAT.format(new Date(beforeDate)));
		}
		
		// Create the writer to output to.
		long restoreFileDate = sortedBackups.get(sortedBackups.size() - 1).getCreationTime();
		BackupRestoreWriter restoreWriter = restoreWriterFactory.create(restoreFileDate);
		try {
			try {
				restoreWriter.open();
			} catch (IOException e) {
				throw new BackupException("Failed to open backup restore writer.", e);
			}
			
			// Fill the backup restore writer with content from the backup parts.
			Set<String> handledFiles = new HashSet<String>();
			IgnorePaths ignorePaths = new IgnorePaths(this.ignorePaths);
			for(int i = sortedBackups.size() - 1; i >= 0; i--) {
				BackupPart backup = sortedBackups.get(i);
				Map<String, ChangeType> changes = backup.getChanges();
				for(Iterator<Entry<String, ChangeType>> it = changes.entrySet().iterator(); it.hasNext();) {
					Entry<String, ChangeType> change = it.next();
					String changePath = change.getKey();
					boolean changeAlreadyHandled = !handledFiles.add(changePath);
					if(changeAlreadyHandled
							|| change.getValue() == ChangeType.REMOVAL || ignorePaths.isIgnored(changePath)) {
						it.remove();
					}
				}
				if(!changes.isEmpty()) {
					try {
						backup.readAll((fileEntry) -> {
							if(changes.containsKey(fileEntry.getRelativePath())) {
								changes.remove(fileEntry.getRelativePath());
								restoreWriter.add(this.toBackupDir.getName() + File.separator
										+ fileEntry.getRelativePath(), fileEntry.getFileStream());
							}
						});
					} catch (IOException e) {
						throw new BackupException("Failed to read backup part to restore from.", e);
					} catch (InvocationTargetException e) {
						throw new BackupException("Failed to write to backup restore writer.", e.getTargetException());
					}
					if(!changes.isEmpty()) {
						throw new BackupException("Backup part " + this.toBackupDir.getName() + "/" + backup.getName()
								+ " does not contain files that should be there according to its meta file: "
								+ Utils.glueIterable(changes.keySet(), (change) -> change, ", ") + ".");
					}
				}
			}
			
			// Close the backup restore writer.
			try {
				restoreWriter.close();
			} catch (IOException e) {
				throw new BackupException("Failed to close backup restore writer.", e);
			}
		} catch (Throwable t) {
			
			// Attempt to remove the (partially) created restore data.
			try {
				restoreWriter.delete();
			} catch (IOException e) {
				this.logger.severe("Failed to remove failed restore file. "
						+ e.getClass().getSimpleName() + ": " + e.getMessage());
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
	public List<Long> getRestoreDateThresholds() throws IOException {
		List<Long> ret = new ArrayList<Long>();
		for(BackupPart part : this.backupPartFactory.readAllBefore(-1)) {
			ret.add(part.getCreationTime());
		}
		return ret;
	}
	
	@Override
	public long getFreeUsableSpace() {
		return this.backupPartFactory.getFreeUsableSpace();
	}
	
	@Override
	public void setIgnorePaths(Collection<String> ignorePaths) {
		if(ignorePaths == null) {
			this.ignorePaths = Collections.emptySet();
		} else {
			// Copy set and set OS-specific file separator char.
			this.ignorePaths = new HashSet<String>();
			for(String ignorePath : ignorePaths) {
				this.ignorePaths.add(ignorePath.replace('/', File.separatorChar).replace('\\', File.separatorChar));
			}
		}
	}
	
	@Override
	public Set<String> getIgnorePaths() {
		return Collections.unmodifiableSet(this.ignorePaths);
	}
	
	/**
	 * Gets the backup part factory of this {@link SimpleBackup}.
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
				throw new BackupException("Failed to read changes from backup: "
						+ this.toBackupDir.getName() + "/" + backupPart.getName() + ".", e);
			} catch (CorruptedBackupException e) {
				this.logger.warning("Found corrupted backup: "
						+ this.toBackupDir.getName() + "/" + backupPart.getName());
				try {
					e.getBackup().delete();
					it.remove();
				} catch (IOException e1) {
					this.logger.severe("Failed to remove corrupted backup: "
							+ this.toBackupDir.getName() + "/" + backupPart.getName()
							+ ". Here's the stacktrace:\n" + Utils.getStacktrace(e1));
					throw new BackupException("Failed to remove corrupted backup: "
							+ this.toBackupDir.getName() + "/" + backupPart.getName(), e1);
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
		IgnorePaths ignorePaths = new IgnorePaths(this.ignorePaths);
		for(int i = sortedBackups.size() - 1; i >= 0; i--) {
			BackupPart backup = sortedBackups.get(i);
			Map<String, ChangeType> changes = backup.getChanges();
			for(Entry<String, ChangeType> change : changes.entrySet()) {
				String changePath = change.getKey();
				if(!removedFiles.contains(changePath)
						&& !stateMap.containsKey(changePath) && !ignorePaths.isIgnored(changePath)) {
					switch(change.getValue()) {
						case ADDITION:
							stateMap.put(changePath, backup);
							break;
						case REMOVAL:
							removedFiles.add(changePath);
							break;
						default:
							throw new InternalError("Unsupported change type: '"
									+ change.getValue() + "' in backup: "
									+ this.toBackupDir.getName() + "/" + backup.getName());
					}
				}
			}
		}
		return stateMap;
	}
}
