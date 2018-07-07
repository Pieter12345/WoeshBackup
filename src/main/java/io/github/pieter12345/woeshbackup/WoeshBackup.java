//package io.github.pieter12345.woeshbackup;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.FileReader;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.nio.file.FileSystemException;
//import java.nio.file.Files;
//import java.nio.file.LinkOption;
//import java.nio.file.StandardCopyOption;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.Date;
//import java.util.Stack;
//import java.util.regex.Pattern;
//
///**
// * WoeshBackup class.
// * Used to create, update, merge and restore backups.
// * @author P.J.S. Kools
// * @deprecated Use WoeshZipBackup instead.
// */
//public class WoeshBackup {
//	
//	// Variables & Constants.
//	private File backupBaseDir; // Backups go to this directory.
//	private File toBackupDir; // The directory to backup (This should be a MineCraft world folder for example).
//	private File ignoreFile; // A file containing directories and files to ignore.
//	
////	public static void main(String[] args) {
////		long time = System.currentTimeMillis();
////
//////		WoeshBackup backup = new WoeshBackup("~/backups", "C:/Users/Pietje/Documents/Java Eclipse Workspace/WoeshBackup/testToBackup");
//////		WoeshBackup backup = new WoeshBackup("~/backups", "C:/Users/Pietje/Desktop/Styms_craftbukkit_1.8.8_testserver/redstone");
////		WoeshBackup backup = new WoeshBackup("~/backups", "C:/Users/Pietje/Java Eclipse Workspace/WoeshBackup/testToBackup");
////		
//////		backup.createInitialBackup();
////		
//////		backup.updateLatestBackup();
////		
//////		backup.mergeUpdateBackups(System.currentTimeMillis()); // Merge everything.
////		
//////		backup.mergeUpdateBackups(System.currentTimeMillis() - 1000*3600*24*7);
////		
////		File restoreToDir = new File("C:/Users/Pietje/Java Eclipse Workspace/WoeshBackup/restores/restore.zip");
////		backup.restoreZipFromBackup(System.currentTimeMillis(), restoreToDir);
////		
////		System.out.println("Time elapsed: " + (System.currentTimeMillis() - time) + "ms.");
////	}
//	
//	/**
//	 * Constructor.
//	 * Creates a new WoeshBackup object.
//	 * @param backupBaseDirPath - The base directory to backup to.
//	 * If this starts with a "~/", the path will be relative to the jars parent directory.
//	 * @param toBackupDirPath - The directory to backup from.
//	 * If this starts with a "~/", the path will be relative to the jars parent directory.
//	 * @param ignoreFilePath - The optional path to a file containing paths to files and directories to ignore.
//	 * If this starts with a "~/", the path will be relative to the jars parent directory.
//	 */
//	public WoeshBackup(String backupBaseDirPath, String toBackupDirPath, String ignoreFilePath) {
//		if(backupBaseDirPath.startsWith("~/")) {
//			this.backupBaseDir = new File(new File("").getAbsolutePath() + backupBaseDirPath.substring(1));
//		} else {
//			this.backupBaseDir = new File(backupBaseDirPath);
//		}
//		if(toBackupDirPath.startsWith("~/")) {
//			this.toBackupDir = new File(new File("").getAbsolutePath() + toBackupDirPath.substring(1));
//		} else {
//			this.toBackupDir = new File(toBackupDirPath);
//		}
//		if(ignoreFilePath != null) {
//			if(ignoreFilePath.startsWith("~/")) {
//				this.ignoreFile = new File(new File("").getAbsolutePath() + ignoreFilePath.substring(1));
//			} else {
//				this.ignoreFile = new File(ignoreFilePath);
//			}
//		} else {
//			this.ignoreFile = null;
//		}
//	}
//	
//	/**
//	 * Constructor.
//	 * Creates a new WoeshBackup object.
//	 * @param backupBaseDirPath - The base directory to backup to.
//	 * If this starts with a "~/", the path will be relative to the jars parent directory.
//	 * @param toBackupDirPath - The directory to backup from.
//	 * If this starts with a "~/", the path will be relative to the jars parent directory.
//	 */
//	public WoeshBackup(String backupBaseDirPath, String toBackupDirPath) {
//		this(backupBaseDirPath, toBackupDirPath, null);
//	}
//	
//	/**
//	 * createInitialBackup method.
//	 * Creates an initial backup at the backup base directory /name/original/yyyy-MM-dd HH-mm-ss/name/...
//	 * where "name" is the name of the directory to backup.
//	 * @return True if the backup was successfully created, false otherwise.
//	 */
//	public synchronized boolean createInitialBackup() {
//		
//		// Return if the directory to backup doesn't exist.
//		if(!this.toBackupDir.exists()) {
//			debug("The directory to backup does not exist: " + this.toBackupDir.getAbsolutePath());
//			return false;
//		}
//		if(!this.toBackupDir.isDirectory()) {
//			debug("The directory to backup is not a valid directory: " + this.toBackupDir.getAbsolutePath());
//			return false;
//		}
//		
//		// Get the file paths that should be ignored.
//		ArrayList<String> ignoreFilePaths;
//		try {
//			ignoreFilePaths = this.getIgnorePathsFromFile();
//		} catch (IOException e) {
//			debug("Could not create original backup (Failed to read existing ignore file).");
//			return false;
//		}
//		
//		// Create the directory for the original backup.
//		if(!this.backupBaseDir.exists() && !this.backupBaseDir.mkdirs()) {
//			debug("Unable to create the backup base directory at: " + this.backupBaseDir.getAbsolutePath());
//			return false;
//		}
//		String date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
//		File backupFolder = new File(this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/" + date);
//		if(backupFolder.exists()) {
//			debug("The directory to copy the new backup to already exists: " + backupFolder.getAbsolutePath());
//			return false;
//		}
//		if(!backupFolder.mkdirs()) {
//			debug("Unable to create the backup directory at: " + backupFolder.getAbsolutePath());
//			return false;
//		}
//		
//		// List all files and store them in changes.txt.
//		ArrayList<String> changedFilesList = WoeshBackup.listRelativePaths(this.toBackupDir, ignoreFilePaths);
//		File changesFile = new File(backupFolder.getAbsolutePath() + "/changes.txt");
//		if(!WoeshBackup.writeChangesToFile(changedFilesList, changesFile)) {
//			debug("Could not create original backup (Failed to create or write to changes file).");
//			return false;
//		}
//		
//		// Copy the content of the directory to backup to the new backup directory.
//		try {
//			if(copyFile(this.toBackupDir, backupFolder, false, false, ignoreFilePaths)) {
//				debug("Original backup created.");
//				return true;
//			} else {
//				debug("Could not create original backup (Failed to copy).");
//				return false;
//			}
//		} catch (FileSystemException e) {
//			debug("Could not create original backup (Believed to be out of disk space). Here's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//	}
//	
//	/**
//	 * updateLatestBackup method.
//	 * Creates an update for the latest original backup, combined with newer update backups.
//	 * @return True if the backup was able to start, false if no original backup was found.
//	 * A return of false does not mean that no files have been copied, it means that at least one didn't though.
//	 */
//	public synchronized boolean updateLatestBackup() {
//		
//		// Get the relevant backups as an array with the original backup as last element and the most recent update backup as first.
//		File[] sortedBackupUpdates = this.getBackups();
//		if(sortedBackupUpdates == null) {
//			debug("Failed to create update backup: Could not find last backup.");
//			return false;
//		}
//		
//		// Get the directory to put this update backup in.
//		File backupUpdateMainDir = new File(this.backupBaseDir + "/" + this.toBackupDir.getName() + "/update");
//		String date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
//		File backupUpdateDir = new File(backupUpdateMainDir.getAbsolutePath() + "/" + date + "/");
//		
//		// Read all "changes.txt" files from update backups to detect deleted files.
//		ArrayList<String> changesFilePaths = new ArrayList<String>();
//		try {
//			changesFilePaths = WoeshBackup.readBackupStateFromChanges(sortedBackupUpdates);
//		} catch (IOException e) {
//			debug("An IOException occured while reading a \"Changes.txt\" file. Here's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//		
//		// Get the file paths that should be ignored.
//		ArrayList<String> ignoreFilePaths;
//		try {
//			ignoreFilePaths = this.getIgnorePathsFromFile();
//		} catch (IOException e) {
//			debug("Could not create update backup (Failed to read existing ignore file).");
//			return false;
//		}
//		
//		// Go through all files and put them in the new backup if they are different from the last backup they appear in.
//		// List all changed and added files/directories in the progress.
//		ArrayList<String> changedFiles = new ArrayList<String>(); // Relative paths starting with the toBackupDir dir name. Prefixed "+" or "-".
//		boolean ret = true;
//		String toBackupDirParentPath = this.toBackupDir.getParentFile().getAbsolutePath(); // ../worldName/.. (contains worldName).
//		String backupUpdateDirPath = backupUpdateDir.getAbsolutePath(); // ../update/yyyy-MM-dd HH-mm-ss (contains worldName).
//		Stack<String> toBackupDirRelPaths = new Stack<String>();
//		
//		// Check if the main directory is on the ignore list. Skip if it is (Nothing will be backupped).
//		if(ignoreFilePaths.contains("/" + this.toBackupDir.getName())) {
//			return true;
//		}
//		
//		toBackupDirRelPaths.push(this.toBackupDir.getName()); // Add the to backup directory name.
//		try {
//			while(!toBackupDirRelPaths.empty()) {
//				
//				// Create File objects for the from directory and the new "update" backup directory.
//				String toBackupDirRelPath = toBackupDirRelPaths.pop();
//				File toBackupDirLocal = new File(toBackupDirParentPath + "/" + toBackupDirRelPath);
//				File backupDirLocal = new File(backupUpdateDirPath + "/" + toBackupDirRelPath);
//				
//				File[] files = toBackupDirLocal.listFiles();
//				if(files.length == 0) {
//					continue;
//				}
//				for(File f : files) { // For all files in the directory to backup.
//					
//					// Check if the file or directory is on the ignore list. Skip if it is.
//					if(ignoreFilePaths.contains("/" + toBackupDirRelPath + "/" + f.getName())) {
//						continue;
//					}
//					
//					// Check if the file or directory exists in one of the update backups or the original backup (detect changes).
//					boolean found = false;
//					for(int i = 0; i < sortedBackupUpdates.length; i++) {
//						File backupDirPathInBackup = new File(
//								sortedBackupUpdates[i].getAbsolutePath() + "/" + toBackupDirRelPath + "/" + f.getName());
//						if(backupDirPathInBackup.exists()) {
//							if(backupDirPathInBackup.isFile()) {
//								
//								// Check if a directory was found (file deleted, directory with same name created).
//								if(f.isDirectory()) {
//									changedFiles.add("+dir /" + toBackupDirRelPath + "/" + f.getName());
//									if(!new File(backupDirLocal.getAbsolutePath() + "/" + f.getName()).mkdirs()) {
//										ret = false;
//										debug("Failed to create a directory in an update backup at: " + new File(backupDirLocal.getAbsolutePath() + "/" + f.getName()).getAbsolutePath());
//									}
//								} else if(changesFilePaths.contains("-file /" + toBackupDirRelPath + "/" + f.getName())) {
//									break; // Handle the file as not found since it was deleted in a later update backup.
//								} else if(!fileEquals(f, backupDirPathInBackup)) { // Check if this file is different from the version in the backup.
//									
//									// A difference was found. Add the file to the new update backup.
//									changedFiles.add("+file /" + toBackupDirRelPath + "/" + f.getName());
//									if(!backupDirLocal.exists() && !backupDirLocal.mkdirs()) {
//										ret = false;
//										debug("Failed to create a directory for an update backup at: " + backupDirLocal.getAbsolutePath());
//									}
//									if(!copyFile(f, backupDirLocal, false, false)) {
//										ret = false;
//										debug("Failed to copy a file while creating an update backup: "
//												+ f.getAbsolutePath() + " to " + backupDirLocal.getAbsolutePath() + "/" + f.getName());
//									}
//								}
//							} else { // If the file in the backup is a directory.
//								
//								// Check if a file was found (directory deleted, file with same name created).
//								if(f.isFile()) {
//									changedFiles.add("+file /" + toBackupDirRelPath + "/" + f.getName());
//									if(!backupDirLocal.exists() && !backupDirLocal.mkdirs()) {
//										ret = false;
//										debug("Failed to create a directory for an update backup at: " + backupDirLocal.getAbsolutePath());
//									}
//									if(!copyFile(f, backupDirLocal, false, false)) {
//										ret = false;
//										debug("Failed to copy a file while creating an update backup.");
//									}
//								} else if(changesFilePaths.contains("-dir /" + toBackupDirRelPath + "/" + f.getName())) {
//									break; // Handle the directory as not found since it was deleted in a later update backup.
//								}
//								
//							}
//							found = true;
//							break; // We have the latest backup, skip the rest.
//						}
//					}
//					
//					// Create the file or directory in the update backup if it didn't exist before. Also add the directory to the stack for checking.
//					if(f.isFile()) {
//						if(!found) {
//							// Update the file.
//							changedFiles.add("+file /" + toBackupDirRelPath + "/" + f.getName());
//							if(!backupDirLocal.getParentFile().exists() && !backupDirLocal.getParentFile().mkdirs()) {
//								ret = false;
//								debug("Failed to create a directory for an update backup at: " + backupDirLocal.getParentFile().getAbsolutePath());
//							}
//							if(!copyFile(f, backupDirLocal, false, false)) {
//								ret = false;
//								debug("Failed to copy a file while creating an update backup.");
//							}
//						}
//					} else { // If f is a directory.
//						if(!found) {
//							changedFiles.add("+dir /" + toBackupDirRelPath + "/" + f.getName());
//							if(!new File(backupDirLocal.getAbsolutePath() + "/" + f.getName()).mkdirs()) {
//								ret = false;
//								debug("Failed to create a directory in an update backup at: " + backupDirLocal.getAbsolutePath() + "/" + f.getName());
//							}
//						}
//						toBackupDirRelPaths.push(toBackupDirRelPath + "/" + f.getName());
//					}
//				}
//			}
//		} catch (FileSystemException e) {
//			debug("Could not create update backup (Believed to be out of disk space). Here's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//		
//		// Check if all files in the backup still exist (detect deletion).
//		for(int i = 0; i < changesFilePaths.size(); i++) {
//			String relPath = changesFilePaths.get(i);
//			boolean isAddedDir = relPath.startsWith("+dir /");
//			boolean isAddedFile = !isAddedDir && relPath.startsWith("+file /");
//			if(isAddedFile || isAddedDir) { // Only check files that should be there.
//				relPath = (isAddedFile ? relPath.substring(6) : relPath.substring(5)); // Cut "+(dir|file)" off.
//				
//				// Check if the file or directory is on the ignore list. Skip if it is (don't store deletion).
//				if(ignoreFilePaths.contains(relPath)) {
//					continue;
//				}
//				
//				File file = new File(this.toBackupDir.getParentFile() + relPath);
//				if(!file.exists() || (isAddedFile ? file.isDirectory() : file.isFile())) { // If deletion detected.
//					changedFiles.add("-" + (isAddedFile ? "file" : "dir") + " " + relPath);
//				}
//			}
//		}
//		
//		// Store the changed files if there were any.
//		if(!changedFiles.isEmpty()) {
//			
//			// Make sure the update backup directory exists (could not be the case for just deletion).
//			if(!backupUpdateDir.exists() && !backupUpdateDir.mkdirs()) {
//				ret = false;
//				debug("Failed to create the main directory of an update backup at: " + backupUpdateDir.getAbsolutePath());
//			}
//			
//			// Store the changed files to changes.txt.
//			File changesFile = new File(backupUpdateDirPath + "/changes.txt");
//			if(!WoeshBackup.writeChangesToFile(changedFiles, changesFile)) {
//				ret = false;
//			}
//		}
//		
//		if(ret && !backupUpdateDir.exists()) {
//			debug("The backup is up to date. No update backup was created.");
//		} else {
//			debug(ret ? "Successfully updated the latest backup." : "updateLatestBackup() finished with failures.");
//		}
//		return ret;
//	}
//	
//	/**
//	 * mergeUpdateBackups method.
//	 * Creates a new original backup from the latest original backup + the update backups dated after that, but before the given beforeDate.
//	 * After the new original backup is created, all old data will be removed.
//	 * @param beforeDate - The timestamp to give to the new backup (Backups older than this will be merged and removed).
//	 * When a beforeDate is given that is older than the latest original backup, nothing happends.
//	 * @return True if the update backup was successfully created, false otherwise.
//	 */
//	public synchronized boolean mergeUpdateBackups(long beforeDate) {
//		
//		// Get the most recent backup folder.
//		File latestOriginalBackup = getLatestOriginalBackup();
//		if(latestOriginalBackup == null) {
//			debug("Unable to merge backup: Could not find latest original backup.");
//			return false;
//		}
//		debug("Found latest original backup: " + latestOriginalBackup.getName());
//		
//		// Get the beforeDate String and check if it's after the original backup.
//		String beforeDateStr = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(beforeDate));
//		debug("Merging backups older than: " + beforeDateStr);
//		if(beforeDateStr.compareTo(latestOriginalBackup.getName()) < 0) {
//			debug("Unable to update backup: The given beforeDate is before the latest backup (returning true).");
//			return true;
//		}
//		if(beforeDate > System.currentTimeMillis()) {
//			debug("Unable to merge backup: The given beforeDate is in the future.");
//			return false;
//		}
//		
//		// Get all "update" backups, dated after the last original backup and before the given date.
//		File backupUpdateMainDir = new File(latestOriginalBackup.getParentFile().getParentFile().getAbsolutePath() + "/update");
//		Pattern backupNamePattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}\\-\\d{2}\\-\\d{2}$"); // yyyy-MM-dd HH-mm-ss.
//		ArrayList<File> previousUpdateBackups = new ArrayList<>();
//		if(backupUpdateMainDir.isDirectory()) {
//			for(File previousUpdateBackup : backupUpdateMainDir.listFiles()) {
//				if(backupNamePattern.matcher(previousUpdateBackup.getName()).matches()
//						&& previousUpdateBackup.getName().compareTo(latestOriginalBackup.getName()) > 0
//						&& previousUpdateBackup.getName().compareTo(beforeDateStr) < 0) {
//					previousUpdateBackups.add(previousUpdateBackup);
//				}
//			}
//		}
//		
//		// Sort all "update" backups so that the first one will be the newest.
//		Collections.sort(previousUpdateBackups, new Comparator<File>() {
//			@Override
//			public int compare(File f1, File f2) {
//				return f2.getName().compareTo(f1.getName());
//			}
//		});
//		previousUpdateBackups.add(latestOriginalBackup);
//		File[] sortedBackupUpdates = previousUpdateBackups.toArray(new File[0]);
//		
//		// Read all "changes.txt" files from update backups to detect deleted files.
//		ArrayList<String> changesFilePaths = new ArrayList<String>();
//		try {
//			changesFilePaths = WoeshBackup.readBackupStateFromChanges(sortedBackupUpdates);
//		} catch (IOException e) {
//			debug("An IOException occured while reading a \"Changes.txt\" file. Here's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//		
//		// Create a new original backup directory.
//		String date = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(beforeDate));
//		File backupFolder = new File(this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/" + date);
//		if(backupFolder.exists()) {
//			debug("The directory to copy the new backup to already exists: " + backupFolder.getAbsolutePath());
//			return false;
//		}
//		if(!backupFolder.mkdirs()) {
//			debug("Unable to create the backup directory at: " + backupFolder.getAbsolutePath());
//			return false;
//		}
//		
//		// For all backup updates (new to old), copy their content to the new backup if it doesn't contain that content.
//		boolean ret = true;
//		for(File backup : sortedBackupUpdates) {
//			Stack<String> relDirPathName = new Stack<>();
//			relDirPathName.push(this.toBackupDir.getName());
//			String updateBackupBaseDir = backup.getAbsolutePath();
//			while(!relDirPathName.empty()) {
//				String dirPath = relDirPathName.pop();
//				File dir = new File(updateBackupBaseDir + "/" + dirPath);
//				for(File file : dir.listFiles()) {
//					if(file.isFile()) {
//						File fileInNewBackup = new File(backupFolder.getAbsolutePath() + "/" + dirPath + "/" + file.getName());
//						if(!fileInNewBackup.exists() && !changesFilePaths.contains("-file /" + dirPath + "/" + file.getName())) {
//							try {
//								if(!copyFile(file, fileInNewBackup.getParentFile(), false, false)) {
//									debug("Failed to copy a file while merging backups.");
//									ret = false;
//								}
//							} catch (FileSystemException e) {
//								debug("Could not merge backups (Believed to be out of disk space). Here's the stacktrace:");
//								e.printStackTrace();
//								return false;
//							}
//						}
//					} else if(file.isDirectory() && !changesFilePaths.contains("-dir /" + dirPath + "/" + file.getName())) {
//						File dirInNewBackup = new File(backupFolder.getAbsolutePath() + "/" + dirPath + "/" + file.getName());
//						if(!dirInNewBackup.exists() && !dirInNewBackup.mkdirs()) {
//							debug("Failed to create a directory while merging backups.");
//							ret = false;
//						}
//						relDirPathName.push(dirPath + "/" + file.getName());
//					}
//				}
//			}
//		}
//		
//		// List all files and store them in changes.txt.
//		ArrayList<String> changedFilesList = WoeshBackup.listRelativePaths(
//				new File(backupFolder.getAbsolutePath() + "/" + this.toBackupDir.getName()), null);
//		File changesFile = new File(backupFolder.getAbsolutePath() + "/changes.txt");
//		if(!WoeshBackup.writeChangesToFile(changedFilesList, changesFile)) {
//			debug("Could not merge backups (Failed to create or write to changes file)."); // TODO - Remove the created backup since it failed?
//			return false;
//		}
//		
//		// Remove the handled backups on success.
//		if(ret) {
//			for(File backup : sortedBackupUpdates) {
//				if(!WoeshBackup.deleteFile(backup)) {
//					debug("Failed to remove a merged backup: " + backup.getAbsolutePath());
//				}
//			}
//		}
//		
//		debug("Successfully merged backups into a new original backup.");
//		return ret;
//		
//	}
//	
//	/**
//	 * restoreFromBackup method.
//	 * Creates a new "original backup" from the latest original backup combined with update backups until the given timestamp.
//	 * @param beforeDate - The date for which to get the latest backup.
//	 * The returned backup will always be older or equal to this date.
//	 * @param directoryToRestoreTo - The directory to put the merged backup in.
//	 * @return True on success, false if any problem has occured.
//	 */
//	public synchronized boolean restoreFromBackup(long beforeDate, File directoryToRestoreTo) {
//		
//		// Get the most recent backup folder.
//		File latestOriginalBackup = getLatestOriginalBackup();
//		if(latestOriginalBackup == null) {
//			debug("Unable to restore from backup: Could not find latest original backup.");
//			return false;
//		}
//		debug("Found last original backup: " + latestOriginalBackup.getName());
//		
//		// Get the beforeDate String and check if it's after the original backup.
//		String beforeDateStr = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(beforeDate));
//		debug("Applying update backups older than: " + beforeDateStr);
//		if(beforeDateStr.compareTo(latestOriginalBackup.getName()) < 0) {
//			debug("Unable to restore from backup: The given beforeDate is before the latest backup.");
//			return false;
//		}
//		if(beforeDate > System.currentTimeMillis()) {
//			debug("Unable to restore from backup: The given beforeDate is in the future.");
//			return false;
//		}
//		
//		// Get all "update" backups, dated after the last original backup and before the given date.
//		File backupUpdateMainDir = new File(latestOriginalBackup.getParentFile().getParentFile().getAbsolutePath() + "/update");
//		Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+$"); // yyyy-MM-dd HH-mm-ss.
//		ArrayList<File> previousUpdateBackups = new ArrayList<>();
//		if(backupUpdateMainDir.isDirectory()) {
//			for(File previousUpdateBackup : backupUpdateMainDir.listFiles()) {
//				if(backupNamePattern.matcher(previousUpdateBackup.getName()).matches()
//						&& previousUpdateBackup.getName().compareTo(latestOriginalBackup.getName()) > 0
//						&& previousUpdateBackup.getName().compareTo(beforeDateStr) < 0) {
//					previousUpdateBackups.add(previousUpdateBackup);
//				}
//			}
//		}
//		
//		// Sort all "update" backups so that the first one will be the newest.
//		Collections.sort(previousUpdateBackups, new Comparator<File>() {
//			@Override
//			public int compare(File f1, File f2) {
//				return f2.getName().compareTo(f1.getName());
//			}
//		});
//		previousUpdateBackups.add(latestOriginalBackup);
//		File[] sortedBackupUpdates = previousUpdateBackups.toArray(new File[0]);
//		
//		// Read all "changes.txt" files from update backups to detect deleted files.
//		ArrayList<String> changesFilePaths = new ArrayList<String>();
//		try {
//			changesFilePaths = WoeshBackup.readBackupStateFromChanges(sortedBackupUpdates);
//		} catch (IOException e) {
//			debug("An IOException occured while reading a \"Changes.txt\" file. Here's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//		
//		
//		
//		
//		
//		// Create a new original backup directory.
//		File directoryToRestoreToParent = directoryToRestoreTo.getParentFile();
//		if(!directoryToRestoreToParent.exists()) {
//			debug("The parent directory to copy the new backup to does not exist: " + directoryToRestoreToParent.getAbsolutePath());
//			return false;
//		}
//		
//		if(directoryToRestoreTo.exists()) {
//			debug("The directory to copy the new backup to does already exists (Remove this directory first): "
//					+ directoryToRestoreTo.getAbsolutePath());
//			return false;
//		}
//		
//		// For all backup updates (new to old), copy their content to the new backup if it doesn't contain that content.
//		boolean ret = true;
//		for(File backup : sortedBackupUpdates) {
//			Stack<String> relDirPathName = new Stack<>();
//			relDirPathName.push(this.toBackupDir.getName());
//			String updateBackupBaseDir = backup.getAbsolutePath();
//			while(!relDirPathName.empty()) {
//				String dirPath = relDirPathName.pop();
//				File dir = new File(updateBackupBaseDir + "/" + dirPath);
//				File[] files = dir.listFiles();
//				if(files != null) {
//					for(File file : files) {
//						if(file.isFile()) {
//							File fileInNewBackup = new File(directoryToRestoreTo.getAbsolutePath() + "/" + dirPath + "/" + file.getName());
//							if(!fileInNewBackup.exists() && !changesFilePaths.contains("-file /" + dirPath + "/" + file.getName())) {
//								try {
//									if(!copyFile(file, fileInNewBackup.getParentFile(), false, false)) {
//										debug("Failed to copy a file while restoring a backup.");
//										ret = false;
//									}
//								} catch (FileSystemException e) {
//									debug("Could not restore from backup (Believed to be out of disk space). Here's the stacktrace:");
//									e.printStackTrace();
//									return false;
//								}
//							}
//						} else if(file.isDirectory() && !changesFilePaths.contains("-dir /" + dirPath + "/" + file.getName())) {
//							File dirInNewBackup = new File(directoryToRestoreTo.getAbsolutePath() + "/" + dirPath + "/" + file.getName());
//							if(!dirInNewBackup.exists() && !dirInNewBackup.mkdirs()) {
//								debug("Failed to create a directory while restoring a backup.");
//								ret = false;
//							}
//							relDirPathName.push(dirPath + "/" + file.getName());
//						}
//					}
//				} else { // If an empty directory was found.
//					if(!dir.mkdir()) {
//						debug("Failed to create a directory while restoring a backup.");
//						ret = false;
//					}
//				}
//			}
//		}
//		
//		if(ret) {
//			debug("Successfully merged backups into a new image at: " + directoryToRestoreTo.getAbsolutePath());
//		} else {
//			debug("Merged backups into a new image with errors at: " + directoryToRestoreTo.getAbsolutePath());
//		}
//		return ret;
//	}
//	
//	/**
//	 * restoreZipFromBackup method.
//	 * Creates a new "original backup" from the latest original backup combined with update backups until the given timestamp.
//	 * @param beforeDate - The date for which to get the latest backup.
//	 * The returned backup will always be older or equal to this date.
//	 * @param dirToRestoreTo - The directory to put the merged backup in.
//	 * @return True on success, false if any problem has occured.
//	 */
//	public synchronized boolean restoreZipFromBackup(long beforeDate, File dirToRestoreTo) {
//		
//		// Get the most recent backup folder.
//		File latestOriginalBackup = getLatestOriginalBackup();
//		if(latestOriginalBackup == null) {
//			debug("Unable to restore from backup: Could not find latest original backup.");
//			return false;
//		}
//		debug("Found last original backup: " + latestOriginalBackup.getName());
//		
//		// Get the beforeDate String and check if it's after the original backup.
//		String beforeDateStr = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date(beforeDate));
//		debug("Applying update backups older than: " + beforeDateStr);
//		if(beforeDateStr.compareTo(latestOriginalBackup.getName()) < 0) {
//			debug("Unable to restore from backup: The given beforeDate is before the latest original backup.");
//			return false;
//		}
//		if(beforeDate > System.currentTimeMillis()) {
//			debug("Unable to restore from backup: The given beforeDate is in the future.");
//			return false;
//		}
//		
//		// Get all "update" backups, dated after the last original backup and before the given date.
//		File backupUpdateMainDir = new File(latestOriginalBackup.getParentFile().getParentFile().getAbsolutePath() + "/update");
//		Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+$"); // yyyy-MM-dd HH-mm-ss.
//		ArrayList<File> previousUpdateBackups = new ArrayList<>();
//		if(backupUpdateMainDir.isDirectory()) {
//			for(File previousUpdateBackup : backupUpdateMainDir.listFiles()) {
//				if(backupNamePattern.matcher(previousUpdateBackup.getName()).matches()
//						&& previousUpdateBackup.getName().compareTo(latestOriginalBackup.getName()) > 0
//						&& previousUpdateBackup.getName().compareTo(beforeDateStr) < 0) {
//					previousUpdateBackups.add(previousUpdateBackup);
//				}
//			}
//		}
//		
//		// Sort all "update" backups so that the first one will be the newest.
//		Collections.sort(previousUpdateBackups, new Comparator<File>() {
//			@Override
//			public int compare(File f1, File f2) {
//				return f2.getName().compareTo(f1.getName());
//			}
//		});
//		previousUpdateBackups.add(latestOriginalBackup);
//		File[] sortedBackupUpdates = previousUpdateBackups.toArray(new File[0]);
//		
//		// Read all "changes.txt" files from update backups to detect deleted files.
//		ArrayList<String> changesFilePaths = new ArrayList<String>();
//		try {
//			changesFilePaths = WoeshBackup.readBackupStateFromChanges(sortedBackupUpdates);
//		} catch (IOException e) {
//			debug("An IOException occured while reading a \"Changes.txt\" file. Here's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//		
//		// Check if the parent of the directory to put the restore zip in exists (Fail if it doesn't).
//		File dirToRestoreToParent = dirToRestoreTo.getParentFile();
//		if(!dirToRestoreToParent.exists()) {
//			debug("The parent directory of the directory to put the merged backup in does not exist: " + dirToRestoreToParent.getAbsolutePath());
//			return false;
//		}
//
//		// Check if the directory to put the restore zip in exists (Attempt to create when it doesn't).
//		if(!dirToRestoreTo.exists() && !dirToRestoreTo.mkdir()) {
//			debug("The directory to put the merged backup in could not be created: " + dirToRestoreTo.getAbsolutePath());
//			return false;
//		}
//		
//		// Create the zip file to output to.
//		String zipFileName = previousUpdateBackups.get(0).getName(); // Use the timestamp of the newest backup.
//		ZipFile zipFile = new ZipFile(new File(dirToRestoreTo.getAbsolutePath() + "/" + zipFileName + ".zip"));
//		if(zipFile.getFile().exists()) {
//			debug("A merged backup with this name already exists (remove this first if you want to generate a new one): "
//					+ zipFile.getFile().getAbsolutePath());
//		}
//		try {
//			zipFile.createFile(); // Creates a new file or does nothing if the file exists already.
//			zipFile.open();
//		} catch (IOException e) {
//			debug("The zip file to put the merged backup in could not be created: " + zipFile.getFile().getAbsolutePath());
//			return false;
//		}
//		
//		// For all backup updates (new to old), copy their content to the new backup if it doesn't contain that content.
//		ArrayList<String> zipContentPaths = new ArrayList<String>();
//		for(File backup : sortedBackupUpdates) {
//			Stack<String> relDirPathName = new Stack<>();
//			relDirPathName.push(this.toBackupDir.getName());
//			String updateBackupBaseDir = backup.getAbsolutePath();
//			while(!relDirPathName.empty()) {
//				String dirPath = relDirPathName.pop();
//				File dir = new File(updateBackupBaseDir + "/" + dirPath);
//				File[] files = dir.listFiles();
//				if(files != null) {
//					for(File file : files) {
//						String filePath = dirPath + "/" + file.getName(); // Relative path.
//						if(file.isFile()) {
//							if(changesFilePaths.contains("-file /" + filePath) || zipContentPaths.contains(filePath)) {
//								continue; // File already found in a newer backup.
//							}
//							
//							// Add file to the zip file.
//							zipContentPaths.add(filePath);
//							try {
//								zipFile.add(file, dirPath);
//							} catch (NullPointerException | IOException e) {
//								debug("Could not restore from backup, failed to write file to zip file: " + zipFile.getFile().getAbsolutePath());
//								return false;
//							}
//						} else if(file.isDirectory() && !changesFilePaths.contains("-dir /" + filePath)) {
//							
//							// Add the directory to the zip file if it wasn't added already.
//							if(!zipContentPaths.contains(filePath)) {
//								zipContentPaths.add(filePath);
//								try {
//									zipFile.add(file, dirPath);
//								} catch (NullPointerException | IOException e) {
//									debug("Could not restore from backup, failed to write directory to zip file: " + zipFile.getFile().getAbsolutePath());
//									return false;
//								}
//							}
//							relDirPathName.push(filePath);
//						}
//					}
//				} else { // If an empty directory was found.
//					
//					// Add the directory to the zip file (get the parent path String of dirPath to put dir in first).
//					if(!zipContentPaths.contains(dirPath)) {
//						int index;
//						for(index = dirPath.length() - 1; index >= 0; index--) {
//							if(dirPath.charAt(index) == '/') {
//								break;
//							}
//						}
//						zipContentPaths.add(dirPath);
//						try {
//							zipFile.add(dir, dirPath.substring(0, index));
//						} catch (NullPointerException | IOException e) {
//							debug("Could not restore from backup, failed to write directory to zip file [2]: " + zipFile.getFile().getAbsolutePath());
//							return false;
//						}
//					}
//					relDirPathName.push(dirPath);
//				}
//			}
//		}
//		
//		// Close the zip file.
//		try {
//			zipFile.close();
//		} catch (IOException e) {
//			// TODO - Handle failures (There is a chance the zip file is corrupted, remove the file since it failed?).
//			debug("Merged backups into a new image with possible errors at: " + dirToRestoreTo.getAbsolutePath() + ".\nHere's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//		
//		debug("Successfully merged backups into a new image at: " + dirToRestoreTo.getAbsolutePath());
//		return true;
//	}
//	
//	/**
//	 * hasOriginalBackup method.
//	 * Checks if this WoeshBackup does or does not have an original backup.
//	 * @return True if an original backup File (backupBaseDir/toBackupDirName/original/yyyy-MM-dd HH-mm-ss) was found, false otherwise.
//	 */
//	public synchronized boolean hasOriginalBackup() {
//		File mainBackupFolder = new File(this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/");
//		File[] files = mainBackupFolder.listFiles();
//		if(files == null) {
//			return false; // The "original" directory was empty.
//		}
//		Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+$"); // yyyy-MM-dd HH-mm-ss.
//		for(File backupFolder : files) {
//			if(!backupFolder.isDirectory() || !backupNamePattern.matcher(backupFolder.getName()).matches()) {
//				continue;
//			}
//			return true; // An "original/yyyy-MM-dd HH-mm-ss" directory was found.
//		}
//		return false; // No found directories matched the "yyyy-MM-dd HH-mm-ss" format.
//	}
//	
//	/**
//	 * getBackupBaseDir method.
//	 * @return The backup base directory used to store the backup in.
//	 */
//	public File getBackupBaseDir() {
//		return this.backupBaseDir;
//	}
//	
//	/**
//	 * getToBackupDir method.
//	 * @return The directory to backup.
//	 */
//	public File getToBackupDir() {
//		return this.toBackupDir;
//	}
//	
//	/**
//	 * getFreeUsableDiskSpace method.
//	 * @return The believed amount of free bytes on the drive this backup will be stored on that can be used to write to.
//	 * Returns -1 if the root directory could not be resolved.
//	 */
//	public long getFreeUsableDiskSpace() {
//		return WoeshBackup.getFreeUsableDiskSpace(this.backupBaseDir);
//	}
//	
//	/**
//	 * validateBackups method.
//	 * Validates the latest original and following update backups, removing them if they do not have a "changes.txt" file in them.
//	 * Backups without "changes.txt" file have been manually altered or were interrupted and are not complete.
//	 */
//	public synchronized void validateBackups() {
//		
//		// Get the latest original backup and all update backups after that.
//		File[] backups = this.getBackups();
//		if(backups == null) {
//			return; // No original backup found.
//		}
//		
//		// Loop over the backups and remove any that do not have a "changes.txt" file in them.
//		for(File backup : backups) {
//			File changesFile = new File(backup.getAbsolutePath() + "/changes.txt");
//			if(!changesFile.isFile()) {
//				debug("Missing changes.txt file during validation. Removing backup: " + backup.getAbsolutePath());
//				if(!WoeshBackup.deleteFile(backup)) {
//					debug("Failed to remove one or multiple files at: " + backup.getAbsolutePath());
//				}
//			}
//		}
//		
//	}
//	
//	/**
//	 * getFreeUsableDiskSpace method.
//	 * @param file - The file on the disk to get the free space of.
//	 * @return The believed amount of free bytes on the drive the given file is in that can be used to write to.
//	 * Returns -1 if the root directory could not be resolved.
//	 */
//	public static long getFreeUsableDiskSpace(File file) {
//		while(file.getParentFile() != null) {
//			file = file.getParentFile();
//		}
//		if(file.exists()) {
//			return file.getUsableSpace();
//		} else {
//			debug("Unable to get free disk space. Root directory could not be resolved from: " + file.getAbsolutePath());
//			return -1;
//		}
//	}
//	
//	/**
//	 * getLatestOriginalBackup method.
//	 * @return The latest original backup File (directory ../yyyy-MM-dd HH-mm-ss) or null when no backup was found.
//	 */
//	private File getLatestOriginalBackup() {
//		
//		// Get the most recent backup folder.
//		File mainBackupFolder = new File(this.backupBaseDir.getAbsolutePath() + "/" + this.toBackupDir.getName() + "/original/");
//		File[] files = mainBackupFolder.listFiles();
//		if(files == null) {
//			return null;
//		}
//		File latestOriginalBackup = null;
//		
//		Pattern backupNamePattern = Pattern.compile("^\\d{4}+\\-\\d{2}+\\-\\d{2}+ \\d{2}+\\-\\d{2}+\\-\\d{2}+$"); // yyyy-MM-dd HH-mm-ss.
//		for(File backupFolder : files) {
//			if(!backupFolder.isDirectory() || !backupNamePattern.matcher(backupFolder.getName()).matches()) {
//				continue;
//			}
//			if(latestOriginalBackup == null || backupFolder.getName().compareTo(latestOriginalBackup.getName()) > 0) {
//				latestOriginalBackup = backupFolder;
//			}
//		}
//		return latestOriginalBackup;
//	}
//	
//	/**
//	 * getBackups method.
//	 * @return An ordered File array with the latest original backup at the end and the most recent update backup at the front.
//	 * Returns null if no original backup was found.
//	 */
//	private File[] getBackups() {
//		
//		// Get the most recent backup folder.
//		File latestOriginalBackup = getLatestOriginalBackup();
//		if(latestOriginalBackup == null) {
//			debug("Could not find last backup.");
//			return null;
//		}
//		debug("Found latest backup: " + latestOriginalBackup.getName());
//		
//		// Get all "update" backups, dated after the last original backup.
//		File backupUpdateMainDir = new File(this.backupBaseDir + "/" + this.toBackupDir.getName() + "/update");
//		Pattern backupNamePattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}\\-\\d{2}\\-\\d{2}$"); // yyyy-MM-dd HH-mm-ss.
//		ArrayList<File> previousUpdateBackups = new ArrayList<>();
//		if(backupUpdateMainDir.isDirectory()) {
//			for(File previousUpdateBackup : backupUpdateMainDir.listFiles()) {
//				if(backupNamePattern.matcher(previousUpdateBackup.getName()).matches()
//						&& previousUpdateBackup.getName().compareTo(latestOriginalBackup.getName()) > 0) {
//					previousUpdateBackups.add(previousUpdateBackup);
//				}
//			}
//		}
//		
//		// Sort all "update" backups so that the first one will be the newest.
//		Collections.sort(previousUpdateBackups, new Comparator<File>() {
//			@Override
//			public int compare(File f1, File f2) {
//				return f2.getName().compareTo(f1.getName());
//			}
//		});
//		
//		// Push the original backup to the end of the list.
//		previousUpdateBackups.add(latestOriginalBackup);
//		
//		// Return the list as an array.
//		return previousUpdateBackups.toArray(new File[0]);
//	}
//	
//	/**
//	 * copyFile method.
//	 * Copies the given FROM file or directory to the given TO directory. If FROM is a directory with contents, those will also be copied.
//	 * @param from - The file or directory to copy.
//	 * @param to - The target directory or file to put from in.
//	 * @param overWrite - Whether or not to overwrite existing files.
//	 * @param mergeDirectories - Whether or not to merge directories. If overWrite is enabled, this doesn't do anything.
//	 * @return True if all files were succesfully copied, false if any file was not copied.
//	 * @throws FileSystemException - When the system ran out of disk space (and probably some other cases).
//	 */
//	private static boolean copyFile(File from, File to, boolean overWrite, boolean mergeDirectories) throws FileSystemException {
//		return WoeshBackup.copyFile(from, to, overWrite, mergeDirectories, null);
//	}
//	
//	/**
//	 * copyFile method.
//	 * Copies the given FROM file or directory to the given TO directory. If FROM is a directory with contents, those will also be copied.
//	 * @param from - The file or directory to copy.
//	 * @param to - The target directory or file to put from in.
//	 * @param overWrite - Whether or not to overwrite existing files.
//	 * @param mergeDirectories - Whether or not to merge directories. If overWrite is enabled, this doesn't do anything.
//	 * @param ignoreFilePaths - A list of file paths to skip. If these paths are relative, the parent of "from" will be used as base directory.
//	 * @return True if all files were succesfully copied, false if any file was not copied.
//	 * @throws FileSystemException - When the system ran out of disk space (and probably some other cases).
//	 */
//	private static boolean copyFile(File from, File to, boolean overWrite,
//			boolean mergeDirectories, ArrayList<String> ignoreFilePaths) throws FileSystemException {
////		debug("Copying file from: " + from.getAbsolutePath() + "\nto: " + to.getAbsolutePath());
//		
//		// Check if the file or directory should be ignored.
//		if(ignoreFilePaths != null) {
//			for(String path : ignoreFilePaths) {
//				if(path.equals("/" + from.getName()) || from.getAbsolutePath().equals(path)) {
//					return true; // The file or directory is on the ignore list.
//				}
//			}
//		}
//		
//		if(from.isFile()) {
//			
//			// Get the file to copy to.
//			File toFile = new File(to.getAbsolutePath() + "/" + from.getName());
//			
//			// Check for file overwriting.
//			if(toFile.exists()) {
//				if(toFile.isFile() && !overWrite) {
//					debug("Trying to copy to an existing file without overwrite enabled: '" + toFile.getAbsolutePath() + "'");
//					return false;
//				} else if(toFile.isDirectory()) {
//					debug("Trying to copy a file but a directory with the same name already exists: '" + toFile.getAbsolutePath() + "'");
//					return false;
//				}
//			}
//			
//			// Create the directory to copy to if it does not exist.
//			if(!toFile.getParentFile().exists() && !toFile.getParentFile().mkdirs()) {
//				debug("Unable to create directory to copy file to: '" + toFile.getParentFile().getAbsolutePath() + "'");
//				return false;
//			}
//			
//			// Copy the file.
//			try {
//				Files.copy(from.toPath(), toFile.toPath(),
//						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
//			} catch(FileSystemException e) {
//				throw e;
//			} catch (IOException e) {
//				debug("An exception occured while copying a file. Here's the stacktrace:");
//				e.printStackTrace();
//				return false;
//			}
//		} else if(from.isDirectory()) {
//			
//			// Get the directory to copy to.
//			File toFolder = new File(to.getAbsoluteFile() + "/" + from.getName());
//			
//			// Check for directory "overwriting" (merging).
//			if(toFolder.exists() && !overWrite && !mergeDirectories) {
//				debug("Trying to copy to an existing directory without directory merge or overwrite enabled: '" + toFolder.getAbsolutePath() + "'");
//				return false;
//			}
//			
//			// Create the directory if it does not exist.
//			if(!toFolder.exists() && !toFolder.mkdirs()) {
//				debug("Could not create folder at: '" + toFolder.getAbsolutePath() + "'");
//				return false;
//			}
//			File[] fromFiles = from.listFiles();
//			if(fromFiles == null) {
//				return true; // The directory is empty.
//			}
//			boolean ret = true;
//			
//			// Create a new ignorePaths list, removing "/fromName" from relative paths.
//			ArrayList<String> ignoreFilePathsNew;
//			if(ignoreFilePaths != null) {
//				ignoreFilePathsNew = new ArrayList<String>();
//				for(String path : ignoreFilePaths) {
//					if(path.startsWith("/" + from.getName() + "/")) {
//						ignoreFilePathsNew.add(path.substring(("/" + from.getName()).length()));
//					} else if(!path.startsWith("/")) {
//						ignoreFilePathsNew.add(path);
//					}
//				}
//			} else {
//				ignoreFilePathsNew = null;
//			}
//			
//			for(File subFrom : fromFiles) {
//				ret = ret & copyFile(subFrom, toFolder, overWrite, mergeDirectories, ignoreFilePathsNew);
//			}
//			return ret;
//		} else {
//			debug("The file to copy does not exist: " + from.getAbsolutePath());
//			return false;
//		}
//		return true;
//	}
//	
//	/**
//	 * deleteFile method.
//	 * Deletes the given file or directory from the system. When the file is a non-empty directory, the contents will also be removed.
//	 * @param fileToDelete - The file to delete.
//	 * @return True if the deletion was successful, false otherwise.
//	 */
//	private static boolean deleteFile(File fileToDelete) {
//		if(fileToDelete.isFile()) {
//			return fileToDelete.delete();
//		} else if(fileToDelete.isDirectory()) {
//			File[] files = fileToDelete.listFiles();
//			if(files != null) {
//				for(File file : files) {
//					WoeshBackup.deleteFile(file); // Ignore return value since it would return false on removing the parent dir below.
//				}
//			}
//			return fileToDelete.delete();
//		} else {
//			return true; // The file does not exist.
//		}
//	}
//	
//	/**
//	 * fileEquals method.
//	 * Checks if 2 files have the same content (not nessesarily name). Does not work for directories.
//	 * @param f1
//	 * @param f2
//	 * @return True if 2 files are equal, false otherwise. Als returns false if one of the files is a directory.
//	 */
//	private static boolean fileEquals(File f1, File f2) {
//		// Check if the lengths are equal.
//		if(f1.length() != f2.length() || f1.isDirectory() || f2.isDirectory()) {
//			return false;
//		}
//		
//		// Check if the contents are equal.
//		FileInputStream inStream1;
//		FileInputStream inStream2;
//		try {
//			inStream1 = new FileInputStream(f1);
//			inStream2 = new FileInputStream(f2);
//			byte[] buffer1 = new byte[1024];
//			byte[] buffer2 = new byte[1024];
//			
//			int available1;
//			int available2;
//			while((available1 = inStream1.available()) > 0) {
//				available2 = inStream2.available();
//				if(available1 != available2) {
//					inStream1.close();
//					inStream2.close();
//					return false;
//				}
//				
//				int amount1 = inStream1.read(buffer1);
//				int amount2 = inStream2.read(buffer2);
//				if(amount1 != amount2) {
//					inStream1.close();
//					inStream2.close();
//					return false;
//				}
//				
//				for(int i = 0; i < amount1; i++) {
//					if(buffer1[i] != buffer2[i]) {
//						inStream1.close();
//						inStream2.close();
//						return false;
//					}
//				}
//			}
//			inStream1.close();
//			inStream2.close();
//		} catch (IOException e) {
//			return false;
//		}
//		return true;
//	}
//	
//	/**
//	 * listRelativePaths method.
//	 * @param inDir - The directory for which to name all paths.
//	 * @param ignoreFilePaths - An ArrayList containing relative paths from the backup base directory that should not be listed.
//	 * @return An ArrayList containing all relative paths prefixed with "+dir /" or "+file /". Returns null if inDir is not a directory.
//	 */
//	private static ArrayList<String> listRelativePaths(File inDir, ArrayList<String> ignoreFilePaths) {
//		if(!inDir.isDirectory()) {
//			return null;
//		}
//		ArrayList<String> relPathList = new ArrayList<String>();
//		Stack<String> relDirs = new Stack<>();
//		String inDirPath = inDir.getParentFile().getAbsolutePath();
//		relDirs.push("/" + inDir.getName());
//		
//		while(!relDirs.empty()) {
//			String relDir = relDirs.pop();
//			File localDir = new File(inDirPath + relDir);
//			for(File file : localDir.listFiles()) {
//				String relFilePath = relDir + "/" + file.getName();
//				if(ignoreFilePaths != null && ignoreFilePaths.contains(relFilePath)) {
//					continue; // This file should be ignored according to the ignoreFilePaths.
//				}
//				if(file.isFile()) {
//					relPathList.add("+file " + relFilePath);
//				} else {
//					relDirs.push(relFilePath);
//					relPathList.add("+dir " + relFilePath);
//				}
//			}
//		}
//		return relPathList;
//	}
//	
//	/**
//	 * writeChangesToFile method.
//	 * Writes all String elements in the changesFilePaths to the given changesFile, seperated by "\r\n".
//	 * @param changesFilePaths
//	 * @param changesFile
//	 * @return True if successful, false if an IOException occured.
//	 */
//	private static boolean writeChangesToFile(ArrayList<String> changesFilePaths, File changesFile) {
//		try {
//			FileWriter writer = new FileWriter(changesFile);
//			for(int i = 0; i < changesFilePaths.size(); i++) {
//				String relPath = changesFilePaths.get(i);
//				writer.append(relPath + "\r\n");
//			}
//			writer.flush();
//			writer.close();
//			return true;
//		} catch (IOException e) {
//			debug("An IOException occured while writing to: " + changesFile.getAbsolutePath() + "\nHere's the stacktrace:");
//			e.printStackTrace();
//			return false;
//		}
//	}
//	
//	/**
//	 * readBackupStateFromChanges method.
//	 * Reads the "changes.txt" file of all given sorted(newest as first element!) sortedBackupUpdates and returns the believed state of the backup. 
//	 * @param sortedBackupUpdates
//	 * @return An ArrayList containing all files and directories in the backup prefixed with "(+|-)(file|dir)".
//	 * @throws IOException - When an IO problem occurs when reading the files.
//	 */
//	private static ArrayList<String> readBackupStateFromChanges(File[] sortedBackupUpdates) throws IOException {
//		ArrayList<String> changesFilePaths = new ArrayList<String>();
//		for(int i = 0; i < sortedBackupUpdates.length; i++) {
//			File changesFile = new File(sortedBackupUpdates[i].getAbsolutePath() + "/changes.txt");
//			if(!changesFile.exists()) {
//				debug("Missing changes.txt file in backup (skipping): " + changesFile.getAbsolutePath());
//				continue;
//			}
//			try {
//				BufferedReader reader = new BufferedReader(new FileReader(changesFile));
//				while(reader.ready()) {
//					String line = reader.readLine();
//					
//					// Store the most recent occurence of +(file|dir) or -(file|dir). This determines wether the file should be there.
//					if((line.startsWith("+") && !changesFilePaths.contains("-" + line.substring(1)))
//							|| (line.startsWith("-") && !changesFilePaths.contains("+" + line.substring(1)))) {
//						changesFilePaths.add(line);
//					}
//				}
//				reader.close();
//			} catch (FileNotFoundException e) { // Should never occur, handle it anyways.
//				debug("Missing changes.txt file in update backup (skipping): " + changesFile.getAbsolutePath());
//				continue;
//			} catch (IOException e) {
//				debug("An IOException occured while reading from: " + changesFile.getAbsolutePath());
//				throw e;
//			}
//		}
//		return changesFilePaths;
//	}
//	
//	/**
//	 * getIgnorePathsFromFile method.
//	 * Reads the ignore file of this WoeshBackup and returns all relative file paths in a list.
//	 * @return An ArrayList containing all relative file paths that should not be backed up.
//	 * @throws IOException - When the file exists, but could not be read.
//	 */
//	private ArrayList<String> getIgnorePathsFromFile() throws IOException {
//		
//		// Initialize return list.
//		ArrayList<String> ignoreFilePaths = new ArrayList<String>();
//		
//		// Return an empty list if no ignoreFile was given.
//		if(this.ignoreFile == null) {
//			return ignoreFilePaths;
//		}
//		
//		// Return an empty list when the ignoreFile did not exist or was not a file.
//		if(!this.ignoreFile.isFile()) {
//			debug("Ignore file for backup \"" + this.toBackupDir.getName() + "\" did not exist: " + this.ignoreFile.getAbsolutePath());
//			return ignoreFilePaths;
//		}
//		
//		// Read the file contents.
//		try {
//			BufferedReader reader = new BufferedReader(new FileReader(this.ignoreFile));
//			while(reader.ready()) {
//				String line = reader.readLine();
//				
//				// Add relative paths, assuming they are abstract. Ignore "//" prefixes to allow comments.
//				if(!line.isEmpty() && line.charAt(0) == '/' && (line.length() >= 2 ? line.charAt(1) != '/' : true)) {
//					ignoreFilePaths.add(line);
//				}
//				
//			}
//			reader.close();
//		} catch (FileNotFoundException e) { // Should never occur, handle it anyways.
//			debug("Ignore file for backup \"" + this.toBackupDir.getName() + "\" did not exist: " + this.ignoreFile.getAbsolutePath());
//			return ignoreFilePaths;
//		} catch (IOException e) {
//			debug("An IOException occured while reading from: " + this.ignoreFile.getAbsolutePath());
//			throw e;
//		}
//		return ignoreFilePaths;
//	}
//	
//	private static void debug(String message) {
//		if(WoeshBackupPlugin.debugEnabled) {
//			System.out.println("[DEBUG] [" + WoeshBackup.class.getSimpleName() + "] " + message);
//		}
//	}
//
//}
