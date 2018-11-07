package io.github.pieter12345.woeshbackup;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * WoeshZipBackup class.
 * Used to create, update, merge and restore zipped backups.
 * @author P.J.S. Kools
 */
public class WoeshZipBackup {
	
	// Variables & Constants.
	private File backupBaseDir; // Backups go to this directory.
	private final File toBackupDir; // The directory to backup (This should be a MineCraft world folder for example).
	private final File ignoreFile; // A file containing directories and files to ignore.
	
//	public static void main(String[] args) {
//		long time = System.currentTimeMillis();
//		
////		WoeshZipBackup backup = new WoeshZipBackup("~/backups", "C:/Users/Pietje/Documents/Java Eclipse Workspace/WoeshZipBackup/testToBackup");
////		WoeshZipBackup backup = new WoeshZipBackup("~/backups", "C:/Users/Pietje/Desktop/Styms_craftbukkit_1.8.8_testserver/redstone");
//		WoeshZipBackup backup = new WoeshZipBackup(new File("backups"), new File("testToBackup"));
//		
//		try {
////			backup.createInitialBackup();
////			backup.updateLatestBackup();
//			
//			File restoreToDir = new File("restores");
//			backup.restoreFromBackup(System.currentTimeMillis(), restoreToDir, true);
//		} catch (BackupException e) {
//			e.printStackTrace();
//		}
//		
////		backup.mergeUpdateBackups(System.currentTimeMillis()); // Merge everything.
//		
////		backup.mergeUpdateBackups(System.currentTimeMillis() - 1000*3600*24*7);
//		
//
//		
//		System.out.println("Time elapsed: " + (System.currentTimeMillis() - time) + "ms.");
//	}
	
	/**
	 * Constructor.
	 * Creates a new WoeshZipBackup object.
	 * @param backupBaseDir - The base directory to backup to.
	 * @param toBackupDir - The directory to backup from.
	 * @param ignoreFile - The file containing paths to files and directories to ignore.
	 */
	public WoeshZipBackup(File backupBaseDir, File toBackupDir, File ignoreFile) {
		this.backupBaseDir = backupBaseDir;
		this.toBackupDir = toBackupDir;
		this.ignoreFile = ignoreFile;
	}
	
	/**
	 * Constructor.
	 * Creates a new WoeshZipBackup object.
	 * @param backupBaseDir - The base directory to backup to.
	 * @param toBackupDir - The directory to backup from.
	 */
	public WoeshZipBackup(File backupBaseDir, File toBackupDir) {
		this.backupBaseDir = backupBaseDir;
		this.toBackupDir = toBackupDir;
		this.ignoreFile = null;
	}
	
	/**
	 * createInitialBackup method.
	 * Creates an initial backup at the backup base directory /name/original/yyyy-MM-dd HH-mm-ss.zip
	 * where "name" is the name of the directory to backup.
	 * @throws BackupException When the backup was not created successfully.
	 *  A part of the backup might exist, but is not valid.
	 * @throws InterruptedException When the current Thread is being interrupted.
	 */
	public synchronized void createInitialBackup() throws BackupException, InterruptedException {
		debug("Creating \"original\" backup for: " + this.toBackupDir.getName() + ".");
		checkInterrupt();
		
		// Throw an Exception if the directory to backup doesn't exist.
		if(!this.toBackupDir.exists()) {
			throw new BackupException("The directory to backup does not exist: " + this.toBackupDir.getAbsolutePath());
		}
		if(!this.toBackupDir.isDirectory()) {
			throw new BackupException("The directory to backup is not a valid directory: "
					+ this.toBackupDir.getAbsolutePath());
		}
		
		// Get the file paths that should be ignored.
		ArrayList<String> ignoreFilePaths;
		try {
			ignoreFilePaths = this.getIgnorePathsFromFile();
		} catch (IOException e) {
			throw new BackupException("Failed to read existing ignore file.", e);
		}
		if(ignoreFilePaths == null) {
			debug("\tIgnore file not found: " + this.ignoreFile.getAbsolutePath());
		}
		
		// Create the directory for the original backup.
		if(!this.backupBaseDir.isDirectory() && !this.backupBaseDir.mkdirs()) {
			throw new BackupException("Unable to create the backup base directory at: "
					+ this.backupBaseDir.getAbsolutePath());
		}
		String date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
		ZipFile backupZipFile = new ZipFile(new File(this.backupBaseDir.getAbsolutePath()
				+ "/" + this.toBackupDir.getName() + "/original/" + date + ".zip"));
		if(!backupZipFile.getFile().getParentFile().isDirectory()
				&& !backupZipFile.getFile().getParentFile().mkdirs()) {
			throw new BackupException("Unable to create the \"original\" backup directory at: "
					+ backupZipFile.getFile().getParentFile().getAbsolutePath());
		}
		
		// Create the zip file for the update backup.
		try {
			if(!backupZipFile.createFile()) {
				throw new BackupException("The zip file to copy the new backup to already exists: "
						+ backupZipFile.getFile().getAbsolutePath());
			}
			backupZipFile.open(); // Throws FileNotFoundException, but file is created above.
		} catch (IOException e) {
			throw new BackupException("Unable to create backup zip file at: "
					+ backupZipFile.getFile().getAbsolutePath(), e);
		}
		
		try {
			
			// Add the contents of the directory to backup to the zip file.
			ArrayList<String> changedFilesList = new ArrayList<String>();
			Stack<File> entryDirs = new Stack<File>();
			Stack<String> relEntryDirPaths = new Stack<String>();
			entryDirs.push(this.toBackupDir);
			relEntryDirPaths.push(this.toBackupDir.getName() + "/");
			
			while(!entryDirs.empty()) {
				checkInterrupt();
				
				File[] files = entryDirs.pop().listFiles();
				String relDirPath = relEntryDirPaths.pop();
				if(files == null) {
					throw new BackupException("An IOException occurred while walking directory: " + relDirPath);
				}
				for(File file : files) {
					checkInterrupt();
					
					boolean isDirectory = file.isDirectory();
					if(ignoreFilePaths != null
							&& ignoreFilePaths.contains(relDirPath + file.getName() + (isDirectory ? "/" : ""))) {
						continue;
					}
					try {
						backupZipFile.add(file, relDirPath);
					} catch (IOException e) {
						throw new BackupException("Could not create original backup: Failed to copy file to zip file: "
								+ file.getAbsolutePath(), e);
					}
					changedFilesList.add(relDirPath + file.getName() + (isDirectory ? "/" : ""));
					if(isDirectory) {
						entryDirs.push(file);
						relEntryDirPaths.push(relDirPath + file.getName() + "/");
					}
				}
			}
			
			// Create and add the changes.txt file.
			String changedFilesStr = "";
			final int beginIndex = this.toBackupDir.getName().length() + 1;
			for(String changedFile : changedFilesList) {
				changedFilesStr += "+" + changedFile.substring(beginIndex) + "\r\n";
			}
			try {
				backupZipFile.add("changes.txt", changedFilesStr.getBytes(StandardCharsets.UTF_8));
				backupZipFile.close();
			} catch (IOException e) {
				throw new BackupException("Could not create original backup (Failed to add changes file to the zip).", e);
			}
			
		} catch (BackupException | InterruptedException e) {
			
			// Make sure the backup zip file is closed. Ignore any exceptions.
			try {
				backupZipFile.close();
			} catch (IOException e1) {
				// Ignore.
			}
			
			// Attempt to remove the backup zip file since an Exception has occurred.
			if(!backupZipFile.getFile().delete()) {
				debug("\tCould not remove failed original backup at: " + backupZipFile.getFile().getAbsolutePath());
			}
			
			// Rethrow the Exception.
			throw e;
		}
		debug("\tOriginal backup created at: " + backupZipFile.getFile().getAbsolutePath());
	}
	
	/**
	 * updateLatestBackup method.
	 * Creates an update for the latest original backup, combined with newer update backups.
	 * @throws BackupException If an error occurred while creating the update backup. When an Exception is thrown,
	 * the newly generated zip file containing the update backup will be attempted to be removed.
	 * @throws InterruptedException When the current Thread is being interrupted.
	 */
	public synchronized void updateLatestBackup() throws BackupException, InterruptedException {
		debug("Creating \"update\" backup for: " + this.toBackupDir.getName() + ".");
		checkInterrupt();
		
		// Throw an Exception if the directory to backup doesn't exist.
		if(!this.toBackupDir.exists()) {
			throw new BackupException("The directory to backup does not exist: " + this.toBackupDir.getAbsolutePath());
		}
		if(!this.toBackupDir.isDirectory()) {
			throw new BackupException("The directory to backup is not a valid directory: "
					+ this.toBackupDir.getAbsolutePath());
		}
		
		// Get the latest "original" backup and all "update" backups after that.
		ZipFile[] sortedBackupUpdates = this.getZipBackups();
		if(sortedBackupUpdates == null) {
			throw new BackupException("Could not create update backup: No \"original\" backup available.");
		}
		
		// Get the file paths that should be ignored.
		final ArrayList<String> ignoreFilePaths;
		try {
			ignoreFilePaths = this.getIgnorePathsFromFile();
		} catch (IOException e) {
			throw new BackupException("An IOException occured while reading an ignore file.", e);
		}
		if(ignoreFilePaths == null) {
			debug("\tIgnore file not found: " + this.ignoreFile.getAbsolutePath());
		}
		
		// Read all "changes.txt" files and generate a list of files/directories of the backups combined.
		Map<String, ZipFile> stateMap;
		try {
			stateMap = readBackupStateFromChanges(sortedBackupUpdates);
		} catch (IOException e) {
			throw new BackupException("An IOException occured while reading a \"changes.txt\" file.", e);
		}
		
		// Filter the files to ignore from the stateMap.
		if(ignoreFilePaths != null && ignoreFilePaths.size() != 0) {
			Iterator<Entry<String, ZipFile>> iterator = stateMap.entrySet().iterator();
			while(iterator.hasNext()) {
				Entry<String, ZipFile> entry = iterator.next();
				if(ignoreFilePaths.contains(entry.getKey().substring(1))) {
					iterator.remove();
				}
			}
		}
		
		// Create the directory for the update backup.
		if(!this.backupBaseDir.isDirectory() && !this.backupBaseDir.mkdirs()) {
			throw new BackupException("Unable to create the backup base directory at: "
					+ this.backupBaseDir.getAbsolutePath());
		}
		String date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
		ZipFile backupZipFile = new ZipFile(new File(this.backupBaseDir.getAbsolutePath()
				+ "/" + this.toBackupDir.getName() + "/update/" + date + ".zip"));
		if(!backupZipFile.getFile().getParentFile().isDirectory()
				&& !backupZipFile.getFile().getParentFile().mkdirs()) {
			throw new BackupException("Unable to create the \"update\" backup directory at: "
					+ backupZipFile.getFile().getParentFile().getAbsolutePath());
		}
		
		// Create the zip file for the update backup.
		try {
			if(!backupZipFile.createFile()) {
				throw new BackupException("The zip file to copy the new backup to already exists: "
						+ backupZipFile.getFile().getAbsolutePath());
			}
			backupZipFile.open(); // Throws FileNotFoundException, but file is created above.
		} catch (IOException e) {
			throw new BackupException("Unable to create backup zip file at: "
					+ backupZipFile.getFile().getAbsolutePath(), e);
		}
		
		// Create a list to store all changes in for the "changes.txt" file of this update backup.
		ArrayList<String> changedFilesList = new ArrayList<String>(); // Relative paths starting with the toBackupDir dir name. Prefixed "+" or "-".
		
		// Walk all files in the directory to backup to check for added files and directories.
		Stack<File> entryDirs = new Stack<File>();
		Stack<String> relEntryDirPaths = new Stack<String>();
		entryDirs.push(this.toBackupDir);
		relEntryDirPaths.push("");
		FileFilter ignoreFileFilter = new FileFilter() {
			@Override
			public boolean accept(File file) {
				String path = file.getAbsolutePath();
				return path.startsWith(WoeshZipBackup.this.toBackupDir.getAbsolutePath() + File.separator)
						&& (ignoreFilePaths == null || !ignoreFilePaths.contains(
								
								path.replace(File.separatorChar, '/').substring(
								WoeshZipBackup.this.toBackupDir.getParentFile().getAbsolutePath().length()
								+ File.separator.length()) + (file.isDirectory() ? "/" : "")));
			}
		};
		try {
			while(!entryDirs.empty()) {
				checkInterrupt();
				
				File[] files = entryDirs.pop().listFiles(ignoreFileFilter);
				String relDirPath = relEntryDirPaths.pop();
				if(files == null) {
					throw new BackupException("An IOException occurred while walking directory: " + relDirPath);
				}
				for(File file : files) {
					checkInterrupt();
					
					boolean isDirectory = file.isDirectory();
					if(!stateMap.containsKey("+" + file.getAbsolutePath().replace(File.separatorChar, '/').substring(
							this.toBackupDir.getAbsolutePath().length() + 1) + (file.isDirectory() ? "/" : ""))) {
						debug("\tDetected added file: +" + relDirPath + file.getName() + (isDirectory ? "/" : ""));
						changedFilesList.add("+" + relDirPath + file.getName() + (isDirectory ? "/" : ""));
						try {
							backupZipFile.add(file, this.toBackupDir.getName() + "/" + relDirPath);
						} catch (IOException e) {
							throw new BackupException("An IOException occurred while adding a file to a zip file.", e);
						}
					}
					
					if(isDirectory) {
						entryDirs.push(file);
						relEntryDirPaths.push(relDirPath + file.getName() + "/");
					}
				}
			}
			
			// Compare all files from the stateMap with the current state and store changes.
			// First, the stateMap will be formatted so that it can be walked per zip file.
			Map<ZipFile, ArrayList<String>> stateMapByFile = new HashMap<ZipFile, ArrayList<String>>();
			for(Entry<String, ZipFile> entry : stateMap.entrySet()) {
				ArrayList<String> list = stateMapByFile.get(entry.getValue());
				if(list == null) {
					list = new ArrayList<String>();
					stateMapByFile.put(entry.getValue(), list);
				}
				list.add(entry.getKey());
			}
			
			for(Entry<ZipFile, ArrayList<String>> entry : stateMapByFile.entrySet()) {
				checkInterrupt();
				
				ZipFile localZip = entry.getKey();
				ArrayList<String> localPathChangesList = entry.getValue();
				try {
					ZipInputStream inStream = new ZipInputStream(new FileInputStream(localZip.getFile()));
					ZipEntry zipEntry;
					final int toBackupDirNameLength = (this.toBackupDir.getName() + "/").length();
					while((zipEntry = inStream.getNextEntry()) != null) {
						checkInterrupt();
						
						if(!zipEntry.getName().startsWith(this.toBackupDir.getName() + "/")) {
							continue; // Skip files outside of the backup dir entry (Such as "changes.txt").
						}
						String zipEntryName = zipEntry.getName().substring(
								toBackupDirNameLength, zipEntry.getName().length());
						
						// Skip entries that are not relevant (there's already a list of relevant changes).
						if(!localPathChangesList.contains("+" + zipEntryName)) {
							continue;
						}
						
						// Handle deletions.
						File fileInToBackupDir = new File(this.toBackupDir.getAbsolutePath() + "/" + zipEntryName);
						if(!fileInToBackupDir.exists() || (fileInToBackupDir.isDirectory() != zipEntry.isDirectory())) {
							debug("\tDetected removed file/directory: -" + zipEntryName);
							changedFilesList.add("-" + zipEntryName);
							continue;
						}
						
						// Add files if they have changed.
						if(!zipEntry.isDirectory()) {
							
							// getSize() can return -1, check byte changes if it does. Otherwise compare file lengths first.
							boolean fileHasChanged = zipEntry.getSize() != -1 && fileInToBackupDir.length() != zipEntry.getSize();
							if(!fileHasChanged) {
								
								// Compare the file and zip entry bytes.
								try {
									FileInputStream inStream2 = new FileInputStream(fileInToBackupDir);
									fileHasChanged = !inStreamEquals(inStream, inStream2);
									inStream2.close();
								} catch (IOException e) {
									// TODO - inStreamEquals() throws an Exception on in-use files. Find another way to compare.
									System.out.println("[WARN] [" + WoeshZipBackup.class.getSimpleName() + "]"
											+ " An IOException has occurred while comparing zipped file: "
											+ zipEntryName + " to file to backup: "
											+ fileInToBackupDir.getAbsolutePath() + ". Ignoring file.");
//									throw new BackupException("An IOException has occurred while comparing zipped file:"
//											+ " " + zipEntryName
//											+ " to file to backup: " + fileInToBackupDir.getAbsolutePath(), e);
								}
							}
							
							// Add the file to the new "update" backup if it has changed.
							if(fileHasChanged) {
								debug("\tDetected changed file: +" + zipEntryName);
								changedFilesList.add("+" + zipEntryName);
								try {
									backupZipFile.add(fileInToBackupDir, zipEntry.getName().substring(0,
											zipEntry.getName().length() - fileInToBackupDir.getName().length()));
								} catch (IOException e) {
									throw new BackupException("An IOException occurred while adding a file to a zip file.", e);
								}
							}
						}
						
						// Close the zip entry.
						inStream.closeEntry();
					}
					inStream.close();
				} catch (IOException e) {
					throw new BackupException("An IOException has occurred while reading zip file: "
							+ localZip.getFile().getAbsolutePath(), e);
				}
				
			}
			
			// Remove the created update backup zip file if no updates were made.
			if(changedFilesList.size() == 0) {
				try {
					backupZipFile.close();
				} catch (IOException e) {
					// Ignore. Nothing was written to this zip file anyways.
				}
				if(!backupZipFile.getFile().delete()) {
					throw new BackupException("Could not remove empty zip file.");
				}
				debug("\tUpdate backup was not created because no changes were found for backup: "
						+ this.toBackupDir.getAbsolutePath());
				return;
			}
			
			// Create and add the changes.txt file.
			String changedFilesStr = "";
			for(String changedFile : changedFilesList) {
				changedFilesStr += changedFile + "\r\n";
			}
			try {
				backupZipFile.add("changes.txt", changedFilesStr.getBytes(StandardCharsets.UTF_8));
				backupZipFile.close();
			} catch (IOException e) {
				throw new BackupException("Could not create update backup (Failed to add changes file to the zip).", e);
			}
		} catch (BackupException | InterruptedException e) {
			
			// Make sure the backup zip file is closed. Ignore any exceptions.
			try {
				backupZipFile.close();
			} catch (IOException e1) {
				// Ignore.
			}
			
			// Attempt to remove the backup zip file since an Exception has occurred.
			if(!backupZipFile.getFile().delete()) {
				debug("\tCould not remove failed update backup at: " + backupZipFile.getFile().getAbsolutePath());
			}
			
			// Rethrow the Exception.
			throw e;
		}
		debug("\tUpdate backup created at: " + backupZipFile.getFile().getAbsolutePath());
	}
	
	/**
	 * mergeUpdateBackups method.
	 * Creates a new original backup from the latest original backup + the update backups dated after that, but before the given beforeDate.
	 * After the new original backup is created, the merged backups will be removed.
	 * @param beforeDate - The timestamp to give to the new backup (Backups older than this will be merged and removed).
	 * When a beforeDate is given that is older than the latest original backup, nothing happends.
	 * @throws BackupException If an error occurred while creating the update backup. When an Exception is thrown,
	 * the newly generated zip file containing the update backup will be attempted to be removed.
	 * @throws InterruptedException When the current Thread is being interrupted.
	 */
	public synchronized void mergeUpdateBackups(long beforeDate) throws BackupException, InterruptedException {
		final String beforeDateStr = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(beforeDate));
		debug("Merging backups older than " + beforeDateStr + " for backup: " + this.toBackupDir.getName() + ".");
		checkInterrupt();
		
		// Get the most recent backup folder.
		final ZipFile latestOriginalBackup = getLatestOriginalBackup();
		if(latestOriginalBackup == null) {
			throw new BackupException("Unable to merge backup: No \"original\" backup available.");
		}
		debug("\tMost recent \"original\" backup: " + latestOriginalBackup.getFile().getName());
		
		// Check if the beforeDate is after the original backup.
		if(beforeDateStr.compareTo(latestOriginalBackup.getFile().getName()) < 0) {
			throw new BackupException("Unable to restore from backup: The given beforeDate is before the latest original backup.");
		}
		if(beforeDate > System.currentTimeMillis()) {
			throw new BackupException("Unable to merge backup: The given beforeDate is in the future.");
		}
		
		// Get all "update" backups, dated after the last original backup and before the given date.
		File backupUpdateMainDir = new File(latestOriginalBackup.getFile().getParentFile().getParentFile().getAbsolutePath() + "/update");
		FileFilter dateFileFilter = new FileFilter() {
			private final Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+\\.zip$"); // yyyy-MM-dd HH-mm-ss.zip.
			@Override
			public boolean accept(File file) {
				String fileName = file.getName();
				return file.isFile() && this.backupNamePattern.matcher(fileName).matches()
						&& fileName.compareTo(latestOriginalBackup.getFile().getName()) > 0
						&& fileName.compareTo(beforeDateStr) < 0;
			}
		};
		ArrayList<ZipFile> previousUpdateBackups = new ArrayList<>();
		if(backupUpdateMainDir.isDirectory()) {
			File[] files = backupUpdateMainDir.listFiles(dateFileFilter);
			if(files == null) {
				throw new BackupException("An IOException occurred while walking directory: "
						+ backupUpdateMainDir.getAbsolutePath());
			}
			for(File previousUpdateBackup : files) {
				previousUpdateBackups.add(new ZipFile(previousUpdateBackup));
			}
		}
		
		// Sort all "update" backups so that the first one will be the newest.
		Collections.sort(previousUpdateBackups, new Comparator<ZipFile>() {
			@Override
			public int compare(ZipFile f1, ZipFile f2) {
				return f2.getFile().getName().compareTo(f1.getFile().getName());
			}
		});
		previousUpdateBackups.add(latestOriginalBackup);
		ZipFile[] sortedBackupUpdates = previousUpdateBackups.toArray(new ZipFile[0]);
		
		// Get the file paths that should be ignored.
		final ArrayList<String> ignoreFilePaths;
		try {
			ignoreFilePaths = this.getIgnorePathsFromFile();
		} catch (IOException e) {
			throw new BackupException("An IOException occured while reading an ignore file.", e);
		}
		if(ignoreFilePaths == null) {
			debug("\tIgnore file not found: " + this.ignoreFile.getAbsolutePath());
		}
		
		// Read all "changes.txt" files and generate a list of files/directories of the backups combined.
		Map<String, ZipFile> stateMap;
		try {
			stateMap = readBackupStateFromChanges(sortedBackupUpdates);
		} catch (IOException e) {
			throw new BackupException("An IOException occured while reading a \"changes.txt\" file.", e);
		}
		
		// Filter the files to ignore from the stateMap.
		if(ignoreFilePaths != null && ignoreFilePaths.size() != 0) {
			Iterator<Entry<String, ZipFile>> iterator = stateMap.entrySet().iterator();
			while(iterator.hasNext()) {
				Entry<String, ZipFile> entry = iterator.next();
				if(ignoreFilePaths.contains(entry.getKey().substring(1))) {
					iterator.remove();
				}
			}
		}
		
		// Create the zip file object to output to.
		String zipFileName = previousUpdateBackups.get(0).getFile().getName(); // Use the timestamp of the newest backup.
		ZipFile newBackupZipFile = new ZipFile(new File(
				this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/" + zipFileName));
		if(newBackupZipFile.getFile().exists()) {
			throw new BackupException("Could not merge backups: An original backup with this name already exists: "
					+ newBackupZipFile.getFile().getAbsolutePath());
		}
		
		// Create the zip file for the merge backup.
		try {
			if(!newBackupZipFile.createFile()) {
				throw new BackupException("The zip file to merge the backup to already exists: "
						+ newBackupZipFile.getFile().getAbsolutePath());
			}
			newBackupZipFile.open(); // Throws FileNotFoundException, but file is created above.
		} catch (IOException e) {
			throw new BackupException("Unable to create backup merge zip file at: "
					+ newBackupZipFile.getFile().getAbsolutePath(), e);
		}
		
		// Format the stateMap so that it can be walked per zip file.
		Map<ZipFile, ArrayList<String>> stateMapByFile = new HashMap<ZipFile, ArrayList<String>>();
		for(Entry<String, ZipFile> entry : stateMap.entrySet()) {
			ArrayList<String> list = stateMapByFile.get(entry.getValue());
			if(list == null) {
				list = new ArrayList<String>();
				stateMapByFile.put(entry.getValue(), list);
			}
			list.add(entry.getKey());
		}
		
		// For each original/update backup, copy the files that do not occur in newer backups.
		try {
			for(Entry<ZipFile, ArrayList<String>> entry : stateMapByFile.entrySet()) {
				checkInterrupt();
				
				ZipFile localZip = entry.getKey();
				ArrayList<String> contentToCopy = entry.getValue();
				try {
					ZipInputStream inStream = new ZipInputStream(new FileInputStream(localZip.getFile()));
					ZipEntry zipEntry;
					final int toBackupDirNameLength = (this.toBackupDir.getName() + "/").length();
					while((zipEntry = inStream.getNextEntry()) != null) {
						checkInterrupt();
						
						if(!zipEntry.getName().startsWith(this.toBackupDir.getName() + "/")) {
							continue; // Skip files outside of the backup dir entry (Such as "changes.txt").
						}
						String zipRelEntryName = zipEntry.getName().substring(
								toBackupDirNameLength, zipEntry.getName().length());
						if(contentToCopy.contains("+" + zipRelEntryName)) {
							
							// Copy the zip entry to the restore zip.
							ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
							byte[] buffer = new byte[1024];
							int amount;
							// inStream.available() is not reliable and returns wrong values. This is reliable.
							while((amount = inStream.read(buffer, 0, buffer.length)) != -1) {
								byteArrayOutStream.write(buffer, 0, amount);
							}
							inStream.closeEntry();
							newBackupZipFile.add(zipEntry.getName(), byteArrayOutStream.toByteArray());
						}
					}
					
					inStream.close();
				} catch (IOException e) {
					throw new BackupException("An IOException has occurred while reading zip file: "
							+ localZip.getFile().getAbsolutePath(), e);
				}
				
			}
			
			// Create and add the changes.txt file.
			String changedFilesStr = "";
			for(String changedFile : stateMap.keySet()) {
				changedFilesStr += changedFile + "\r\n";
			}
			try {
				newBackupZipFile.add("changes.txt", changedFilesStr.getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new BackupException("Could not create update backup (Failed to add changes file to the zip).", e);
			}
			
			// Close the merge zip file.
			try {
				newBackupZipFile.close();
			} catch (IOException e) {
				throw new BackupException("An IOException has occurred while closing the merge zip file.", e);
			}
			
		} catch (BackupException | InterruptedException e) {
			debug("\tA " + e.getClass().getSimpleName() + " occurred while merging the backup: " + e.getMessage());
			
			// Make sure the merge zip file is closed. Ignore any exceptions.
			try {
				newBackupZipFile.close();
			} catch (IOException e1) {
				// Ignore.
			}
			
			// Attempt to remove the backup zip file since an Exception has occurred.
			if(!newBackupZipFile.getFile().delete()) {
				debug("\tCould not remove failed merge backup at: " + newBackupZipFile.getFile().getAbsolutePath());
			}
			
			// Rethrow the Exception.
			throw e;
		}
		
		// Delete the handled backups.
		for(ZipFile backup : sortedBackupUpdates) {
			if(!backup.getFile().delete()) {
				debug("\tFailed to remove merged backup: " + backup.getFile().getAbsolutePath());
			}
		}
		
		// Give feedback.
		debug("\tSuccessfully merged backups older than " + beforeDateStr + " at: "
				+ newBackupZipFile.getFile().getAbsolutePath());
		
	}
	
	/**
	 * restoreFromBackup method.
	 * Creates a new "original backup" from the latest original backup combined with update backups until the given timestamp.
	 * @param beforeDate - The date for which to get the latest backup.
	 * The returned backup will always be older or equal to this date.
	 * @param dirToRestoreTo - The directory to put the merged backup in.
	 * @param doZip - If true, the merged backup will be zipped.
	 * @throws BackupException If an error occurred while creating the restore image zip. When an Exception is thrown,
	 * the newly generated zip file will be attempted to be removed.
	 * @throws InterruptedException When the current Thread is being interrupted.
	 */
	public synchronized void restoreFromBackup(long beforeDate, File dirToRestoreTo, boolean doZip) throws BackupException, InterruptedException {
		final String beforeDateStr = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(beforeDate));
		debug("Restoring backup: " + this.toBackupDir.getName() + " to before " + beforeDateStr + " in directory: "
				+ dirToRestoreTo.getAbsolutePath() + ".");
		checkInterrupt();
		
		// Get the most recent backup folder.
		final ZipFile latestOriginalBackup = getLatestOriginalBackup();
		if(latestOriginalBackup == null) {
			throw new BackupException("Unable to restore from backup: No \"original\" backup available.");
		}
		debug("\tFound most recent \"original\" backup: " + latestOriginalBackup.getFile().getName());
		
		// Check if the beforeDate is after the original backup.
		if(beforeDateStr.compareTo(latestOriginalBackup.getFile().getName()) < 0) {
			throw new BackupException("Unable to restore from backup: The given beforeDate is before the latest original backup.");
		}
		if(beforeDate > System.currentTimeMillis()) {
			throw new BackupException("Unable to restore from backup: The given beforeDate is in the future.");
		}
		
		// Get all "update" backups, dated after the last original backup and before the given date.
		File backupUpdateMainDir = new File(latestOriginalBackup.getFile().getParentFile().getParentFile().getAbsolutePath() + "/update");
		FileFilter dateFileFilter = new FileFilter() {
			private final Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+\\.zip$"); // yyyy-MM-dd HH-mm-ss.zip.
			@Override
			public boolean accept(File file) {
				String fileName = file.getName();
				return file.isFile() && this.backupNamePattern.matcher(fileName).matches()
						&& fileName.compareTo(latestOriginalBackup.getFile().getName()) > 0
						&& fileName.compareTo(beforeDateStr) < 0;
			}
		};
		ArrayList<ZipFile> previousUpdateBackups = new ArrayList<>();
		if(backupUpdateMainDir.isDirectory()) {
			File[] files = backupUpdateMainDir.listFiles(dateFileFilter);
			if(files == null) {
				throw new BackupException("An IOException occurred while walking directory: "
						+ backupUpdateMainDir.getAbsolutePath());
			}
			for(File previousUpdateBackup : files) {
				previousUpdateBackups.add(new ZipFile(previousUpdateBackup));
			}
		}
		
		// Sort all "update" backups so that the first one will be the newest.
		Collections.sort(previousUpdateBackups, new Comparator<ZipFile>() {
			@Override
			public int compare(ZipFile f1, ZipFile f2) {
				return f2.getFile().getName().compareTo(f1.getFile().getName());
			}
		});
		previousUpdateBackups.add(latestOriginalBackup);
		ZipFile[] sortedBackupUpdates = previousUpdateBackups.toArray(new ZipFile[0]);
		
		// Get the file paths that should be ignored.
		final ArrayList<String> ignoreFilePaths;
		try {
			ignoreFilePaths = this.getIgnorePathsFromFile();
		} catch (IOException e) {
			throw new BackupException("An IOException occured while reading an ignore file.", e);
		}
		if(ignoreFilePaths == null) {
			debug("\tIgnore file not found: " + this.ignoreFile.getAbsolutePath());
		}
		
		// Read all "changes.txt" files and generate a list of files/directories of the backups combined.
		Map<String, ZipFile> stateMap;
		try {
			stateMap = readBackupStateFromChanges(sortedBackupUpdates);
		} catch (IOException e) {
			throw new BackupException("An IOException occured while reading a \"changes.txt\" file.", e);
		}
		
		// Filter the files to ignore from the stateMap.
		if(ignoreFilePaths != null && ignoreFilePaths.size() != 0) {
			Iterator<Entry<String, ZipFile>> iterator = stateMap.entrySet().iterator();
			while(iterator.hasNext()) {
				Entry<String, ZipFile> entry = iterator.next();
				if(ignoreFilePaths.contains(entry.getKey().substring(1))) {
					iterator.remove();
				}
			}
		}
		
		// Check if the parent of the directory to put the restore backup in exists (Fail if it doesn't).
		dirToRestoreTo = dirToRestoreTo.getAbsoluteFile();
		if(!dirToRestoreTo.getParentFile().exists()) {
			throw new BackupException("Unable to restore from backup: The parent directory of the directory to put"
					+ " the merged backup in does not exist: " + dirToRestoreTo.getParentFile().getAbsolutePath());
		}
		
		// Check if the directory to put the restore backup in exists (Attempt to create when it doesn't).
		if(!dirToRestoreTo.isDirectory() && !dirToRestoreTo.mkdir()) {
			throw new BackupException("Unable to restore from backup: The directory to put the merged"
					+ " backup in does not exist and could not be created: " + dirToRestoreTo.getAbsolutePath());
		}
		
		File restoreToDir = null;
		ZipFile restoreZipFile = null;
		if(doZip) {
			
			// Create the zip file object to output to.
			String zipFileName = previousUpdateBackups.get(0).getFile().getName(); // Use the timestamp of the newest backup.
			restoreZipFile = new ZipFile(new File(dirToRestoreTo.getAbsolutePath() + "/" + zipFileName));
			if(restoreZipFile.getFile().exists()) {
				throw new BackupException("A merged backup with this name already exists"
						+ " (remove this first if you want to generate a new one): "
						+ restoreZipFile.getFile().getAbsolutePath());
			}
			
			// Create the zip file for the update backup.
			try {
				if(!restoreZipFile.createFile()) {
					throw new BackupException("The zip file to restore the backup to already exists: "
							+ restoreZipFile.getFile().getAbsolutePath());
				}
				restoreZipFile.open(); // Throws FileNotFoundException, but file is created above.
			} catch (IOException e) {
				throw new BackupException("Unable to create backup restore zip file at: "
						+ restoreZipFile.getFile().getAbsolutePath(), e);
			}
			
		} else {
			
			// Create the directory to restore to.
			String restoreToDirName = previousUpdateBackups.get(0).getFile().getName();
			restoreToDirName = restoreToDirName.substring(0, restoreToDirName.length() - 4); // Cut ".zip" off.
			restoreToDir = new File(dirToRestoreTo.getAbsolutePath() + "/" + restoreToDirName);
			if(restoreToDir.exists()) {
				throw new BackupException("A merged backup with this name already exists (remove this first if you want to generate a new one): "
						+ restoreToDir.getAbsolutePath());
			}
			if(!restoreToDir.mkdir()) {
				throw new BackupException("Unable to create backup restore directory at: "
						+ restoreToDir.getAbsolutePath());
			}
			
		}
		
		// Format the stateMap so that it can be walked per zip file.
		Map<ZipFile, ArrayList<String>> stateMapByFile = new HashMap<ZipFile, ArrayList<String>>();
		for(Entry<String, ZipFile> entry : stateMap.entrySet()) {
			ArrayList<String> list = stateMapByFile.get(entry.getValue());
			if(list == null) {
				list = new ArrayList<String>();
				stateMapByFile.put(entry.getValue(), list);
			}
			list.add(entry.getKey());
		}
		
		// For each original/update backup, copy the files that do not occur in newer backups.
		try {
			for(Entry<ZipFile, ArrayList<String>> entry : stateMapByFile.entrySet()) {
				checkInterrupt();
				
				ZipFile localZip = entry.getKey();
				ArrayList<String> contentToCopy = entry.getValue();
				try {
					ZipInputStream inStream = new ZipInputStream(new FileInputStream(localZip.getFile()));
					ZipEntry zipEntry;
					final int toBackupDirNameLength = (this.toBackupDir.getName() + "/").length();
					while((zipEntry = inStream.getNextEntry()) != null) {
						checkInterrupt();
						
						if(!zipEntry.getName().startsWith(this.toBackupDir.getName() + "/")) {
							continue; // Skip files outside of the backup dir entry (Such as "changes.txt").
						}
						String zipRelEntryName = zipEntry.getName().substring(
								toBackupDirNameLength, zipEntry.getName().length());
						if(contentToCopy.contains("+" + zipRelEntryName)) {
							
							// Copy the zip entry to the restore zip.
							ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
							byte[] buffer = new byte[1024];
							int amount;
							// inStream.available() is not reliable and returns wrong values. This is reliable.
							while((amount = inStream.read(buffer, 0, buffer.length)) != -1) {
								byteArrayOutStream.write(buffer, 0, amount);
							}
							inStream.closeEntry();
							if(doZip) {
								restoreZipFile.add(zipEntry.getName(), byteArrayOutStream.toByteArray());
							} else {
								File restoreToFile = new File(
										restoreToDir.getAbsolutePath() + "/" + zipEntry.getName());
								try {
									restoreToFile.createNewFile();
									FileOutputStream fOutStream = new FileOutputStream(restoreToFile);
									fOutStream.write(byteArrayOutStream.toByteArray());
									fOutStream.flush();
									fOutStream.close();
								} catch (IOException e) {
									throw new BackupException("An IOException has occurred while writing to file: "
											+ restoreToFile.getAbsolutePath(), e);
								}
							}
						}
					}
					
					inStream.close();
				} catch (IOException e) {
					throw new BackupException("An IOException has occurred while reading zip file: "
							+ localZip.getFile().getAbsolutePath(), e);
				}
				
			}
			
			if(doZip) {
				// Close the restore zip file.
				try {
					restoreZipFile.close();
				} catch (IOException e) {
					throw new BackupException("An IOException has occurred while closing the restore zip file.", e);
				}
			}
			
		} catch (BackupException | InterruptedException e) {
			
			if(doZip) {
				
				// Make sure the backup zip file is closed. Ignore any exceptions.
				try {
					restoreZipFile.close();
				} catch (IOException e1) {
					// Ignore.
				}
				
				// Attempt to remove the backup zip file since an Exception has occurred.
				if(!restoreZipFile.getFile().delete()) {
					debug("\tCould not remove failed restored backup at: "
							+ restoreZipFile.getFile().getAbsolutePath());
				}
			} else {
				
				// Attempt to remove the files in the backup directory since an Exception has occurred.
				if(!deleteFile(restoreToDir)) {
					debug("\tCould not remove failed restored backup at: " + restoreToDir.getAbsolutePath());
				}
			}
			
			// Rethrow the Exception.
			throw e;
		}
		debug("Restore image created at: " + (doZip ? restoreZipFile.getFile() : restoreToDir).getAbsolutePath());
	}
	
	/**
	 * hasOriginalBackup method.
	 * Checks if this WoeshZipBackup does or does not have an original backup.
	 * @return True if an original backup File (backupBaseDir/toBackupDirName/original/yyyy-MM-dd HH-mm-ss) was found, false otherwise.
	 */
	public synchronized boolean hasOriginalBackup() {
		File mainBackupFolder = new File(this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/");
		File[] files = mainBackupFolder.listFiles();
		if(files == null) {
			return false; // The "original" directory was empty.
		}
		Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+\\.zip$"); // yyyy-MM-dd HH-mm-ss.
		for(File backupZipFile : files) {
			if(backupZipFile.isDirectory() || !backupNamePattern.matcher(backupZipFile.getName()).matches()) {
				continue;
			}
			return true; // An "original/yyyy-MM-dd HH-mm-ss.zip" file was found.
		}
		return false; // No found files matched the "yyyy-MM-dd HH-mm-ss.zip" format.
	}
	
	/**
	 * getBackupBaseDir method.
	 * @return The backup base directory used to store the backup in.
	 */
	public File getBackupBaseDir() {
		return this.backupBaseDir;
	}
	
	/**
	 * setBackupBaseDir method.
	 * @param backupBaseDir - The backup base directory used to store the backup in.
	 */
	public synchronized void setBackupBaseDir(File backupBaseDir) {
		this.backupBaseDir = backupBaseDir;
	}
	
	/**
	 * getToBackupDir method.
	 * @return The directory to backup.
	 */
	public File getToBackupDir() {
		return this.toBackupDir;
	}
	
	/**
	 * getFreeUsableDiskSpace method.
	 * @return The believed amount of free bytes on the drive this backup will be stored on that can be used to write to.
	 * Returns -1 if the root directory could not be resolved.
	 */
	public long getFreeUsableDiskSpace() {
		return WoeshZipBackup.getFreeUsableDiskSpace(this.backupBaseDir);
	}
	
	/**
	 * validateBackups method.
	 * Validates the latest original and following update backups, removing them if they do not have a "changes.txt" file in them.
	 * Backups without "changes.txt" file have been manually altered or were interrupted and are not complete.
	 * This method does NOT verify that the files listed in changes.txt exist.
	 * @throws InterruptedException When the current Thread is being interrupted.
	 */
	public synchronized void validateBackups() throws InterruptedException {
		debug("Validating backup: " + this.toBackupDir.getName());
		checkInterrupt();
		
		// Get the latest original backup and all update backups after that.
		ZipFile[] backupZipFiles = this.getZipBackups();
		if(backupZipFiles == null) {
			return; // No original backup found.
		}
		
		// Loop over the backups and remove any that do not have a "changes.txt" file in them.
		for(ZipFile backupZipFile : backupZipFiles) {
			checkInterrupt();
			
			try {
				byte[] changesFileBytes = backupZipFile.read("changes.txt");
				if(changesFileBytes == null) {
					debug("\tMissing changes.txt file during backup validation. Removing backup entry: "
							+ backupZipFile.getFile().getAbsolutePath());
					if(!backupZipFile.getFile().delete()) {
						debug("\tFailed to remove file: " + backupZipFile.getFile().getAbsolutePath());
					}
				}
			} catch (IOException e) {
				debug("\tIOException while reading changes.txt in zip file during backup validation."
						+ " Removing backup entry: " + backupZipFile.getFile().getAbsolutePath());
				if(!backupZipFile.getFile().delete()) {
					debug("\tFailed to remove file: " + backupZipFile.getFile().getAbsolutePath());
				}
			}
		}
		
		debug("\tBackup validated: " + this.toBackupDir.getName());
	}
	
	/**
	 * getFreeUsableDiskSpace method.
	 * @param file - The file on the disk to get the free space of.
	 * @return The believed amount of free bytes on the drive the given file is in that can be used to write to.
	 * Returns -1 if the root directory could not be resolved.
	 */
	public static long getFreeUsableDiskSpace(File file) {
		while(file.getParentFile() != null) {
			file = file.getParentFile();
		}
		if(file.exists()) {
			return file.getUsableSpace();
		} else {
			debug("Unable to get free disk space. Root directory could not be resolved from: " + file.getAbsolutePath());
			return -1;
		}
	}
	
	/**
	 * getLatestOriginalBackup method.
	 * @return The latest original backup File (.../yyyy-MM-dd HH-mm-ss.zip) or null when no backup was found.
	 */
	private ZipFile getLatestOriginalBackup() {
		
		// Get the most recent backup folder.
		File mainBackupFolder = new File(this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/");
		File[] files = mainBackupFolder.listFiles();
		if(files == null) {
			return null;
		}
		File latestOriginalBackup = null;
		
		Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+\\.zip$"); // yyyy-MM-dd HH-mm-ss.zip.
		for(File backupFile : files) {
			if(backupFile.isDirectory() || !backupNamePattern.matcher(backupFile.getName()).matches()) {
				continue;
			}
			if(latestOriginalBackup == null || backupFile.getName().compareTo(latestOriginalBackup.getName()) > 0) {
				latestOriginalBackup = backupFile;
			}
		}
		return (latestOriginalBackup == null ? null : new ZipFile(latestOriginalBackup));
	}
	
	/**
	 * getZipBackups method.
	 * @return An ordered File array with the latest original backup at the end and the most recent update backup at the front.
	 * Returns null if no original backup was found.
	 */
	private ZipFile[] getZipBackups() {
		
		// Get the most recent backup folder.
		ZipFile latestOriginalBackup = getLatestOriginalBackup();
		if(latestOriginalBackup == null) {
			return null; // Could not find an "original" backup.
		}
//		debug("Found latest backup: " + latestOriginalBackup.getFile().getName());
		
		// Get all "update" backups, dated after the last original backup.
		File backupUpdateMainDir = new File(this.backupBaseDir + "/" + this.toBackupDir.getName() + "/update");
		ArrayList<ZipFile> previousUpdateBackups = new ArrayList<>();
		File[] updateBackupFiles = backupUpdateMainDir.listFiles();
		if(updateBackupFiles != null) { // False when backupUpdateMainDir is not a directory.
			Pattern backupNamePattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}\\-\\d{2}\\-\\d{2}\\.zip$"); // yyyy-MM-dd HH-mm-ss.zip.
			for(File updateBackupFile : backupUpdateMainDir.listFiles()) {
				if(backupNamePattern.matcher(updateBackupFile.getName()).matches()
						&& updateBackupFile.getName().compareTo(latestOriginalBackup.getFile().getName()) > 0) {
					previousUpdateBackups.add(new ZipFile(updateBackupFile));
				}
			}
		}
		
		// Sort all "update" backups so that the first one will be the newest.
		Collections.sort(previousUpdateBackups, new Comparator<ZipFile>() {
			@Override
			public int compare(ZipFile f1, ZipFile f2) {
				return f2.getFile().getName().compareTo(f1.getFile().getName());
			}
		});
		
		// Push the original backup to the end of the list.
		previousUpdateBackups.add(latestOriginalBackup);
		
		// Return the list as an array.
		return previousUpdateBackups.toArray(new ZipFile[0]);
	}
	
	/**
	 * deleteFile method.
	 * Deletes the given file or directory from the system. When the file is a non-empty directory, the contents will also be removed.
	 * @param fileToDelete - The file or directory to delete.
	 * @return True if the file or directory existed and was successfully removed, false otherwise.
	 */
	private static boolean deleteFile(File fileToDelete) {
		if(fileToDelete.isFile()) {
			return fileToDelete.delete();
		} else if(fileToDelete.isDirectory()) {
			File[] files = fileToDelete.listFiles();
			if(files != null) {
				for(File file : files) {
					WoeshZipBackup.deleteFile(file); // Ignore return value since it would return false on removing the parent dir below.
				}
			}
			return fileToDelete.delete();
		} else {
			return false; // The file does not exist.
		}
	}
	
	/**
	 * readBackupStateFromChanges method.
	 * Reads the "changes.txt" zip entry of all given sorted(newest as first element!) sortedBackupUpdates
	 * and returns the believed state of the backup. 
	 * @param sortedBackupUpdates
	 * @return A Map containing all files and directories in the backup prefixed with "+" or "-" and the ZipFile
	 * in which their most recent version can be found. Directories are suffixed with a "/".
	 * @throws IOException When an I/O error occurs when reading the zip files.
	 */
	private static Map<String, ZipFile> readBackupStateFromChanges(ZipFile[] sortedBackupUpdates) throws IOException {
		HashMap<String, ZipFile> changesMap = new HashMap<String, ZipFile>();
		for(int i = 0; i < sortedBackupUpdates.length; i++) {
			ZipFile sortedBackupUpdate = sortedBackupUpdates[i];
			
			// Read the "changes.txt" file.
			byte[] changesFileBytes;
			try {
				changesFileBytes = sortedBackupUpdate.read("changes.txt");
				if(changesFileBytes == null) {
					debug("Missing changes.txt file in backup zip (skipping): "
							+ sortedBackupUpdate.getFile().getAbsolutePath());
					continue;
				}
			} catch (IOException e) {
				throw new IOException("Failed to read \"changes.txt\" from zip file at: "
						+ sortedBackupUpdate.getFile().getAbsolutePath(), e);
			}
			String changesFileStr = new String(changesFileBytes, StandardCharsets.UTF_8);
			
			// Parse the "changes.txt" file and process the data.
			for(String line : changesFileStr.split("\n")) {
				line = line.replaceAll("[\r\n\t]", "").trim();
				if(line.isEmpty()) {
					continue; // Skip empty lines.
				}
				
				// Store the most recent occurence of +/-. This determines wether the file should be there.
				if(!changesMap.containsKey("-" + line.substring(1))
						&& !changesMap.containsKey("+" + line.substring(1))) {
					changesMap.put(line, sortedBackupUpdate);
				}
			}
		}
		return changesMap;
	}
	
	/**
	 * getIgnorePathsFromFile method.
	 * Reads the ignore file of this WoeshZipBackup and returns all relative file paths in a list.
	 * @return An ArrayList containing all relative file paths that should not be backed up.
	 * Returns null if the ignore file doesn't exist.
	 * @throws IOException When the file exists, but could not be read.
	 */
	private ArrayList<String> getIgnorePathsFromFile() throws IOException {
		
		// Initialize return list.
		ArrayList<String> ignoreFilePaths = new ArrayList<String>();
		
		// Return an empty list if no ignoreFile was given.
		if(this.ignoreFile == null) {
			return ignoreFilePaths;
		}
		
		// Return an empty list when the ignoreFile did not exist or was not a file.
		if(!this.ignoreFile.isFile()) {
			return null;
		}
		
		// Read the file contents.
		try {
			BufferedReader reader = new BufferedReader(new FileReader(this.ignoreFile));
			while(reader.ready()) {
				String line = reader.readLine();
				
				// Add relative paths, assuming they are abstract. Ignore "//" prefixes to allow comments.
				if(!line.isEmpty() && !line.trim().startsWith("//")) {
					ignoreFilePaths.add(line.split("//", 1)[0].trim());
				}
				
			}
			reader.close();
		} catch (FileNotFoundException e) { // Should never occur, handle it anyways.
			return null;
		} catch (IOException e) {
			throw e;
		}
		return ignoreFilePaths;
	}
	
	/**
	 * inStreamEquals method.
	 * Reads and compares 2 InputStreams.
	 * @param inStream1
	 * @param inStream2
	 * @return True if inStream1 and inStream2 contained the same amount of the same bytes.
	 * @throws IOException If an I/O error occurs.
	 */
	private static boolean inStreamEquals(InputStream inStream1, InputStream inStream2) throws IOException {
		byte[] buffer1 = new byte[1024];
		byte[] buffer2 = new byte[1024];
		
		while(true) {
			
			// Fill the buffers.
			int count1 = 0;
			int amount1;
			while(count1 < buffer1.length && (amount1 = inStream1.read(buffer1, count1, buffer1.length - count1)) != -1) {
				count1 += amount1;
			}
			int count2 = 0;
			int amount2;
			while(count2 < buffer2.length && (amount2 = inStream2.read(buffer2, count2, buffer2.length - count2)) != -1) {
				count2 += amount2;
			}
			
			// Check if the buffer sizes match.
			if(count1 != count2) {
				return false;
			}
			
			// Check for end of stream.
			if(count1 == 0) {
				return true;
			}
			
			// Check if the buffer bytes match.
			for(int i = 0; i < count1; i++) {
				if(buffer1[i] != buffer2[i]) {
					return false;
				}
			}
			
		}
	}
	
	private static void checkInterrupt() throws InterruptedException {
		if(Thread.currentThread().isInterrupted()) {
			throw new InterruptedException();
		}
	}
	
	private static void debug(String message) {
		if(WoeshBackupPlugin.debugEnabled) {
			System.out.println("[DEBUG] [" + WoeshZipBackup.class.getSimpleName() + "] " + message);
		}
	}

}
