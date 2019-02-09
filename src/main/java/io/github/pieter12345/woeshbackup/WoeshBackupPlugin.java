package io.github.pieter12345.woeshbackup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.pieter12345.woeshbackup.utils.Utils;

/**
 * WoeshBackupPlugin class.
 * This is the main class that will be loaded by Bukkit.
 * @author P.J.S. Kools
 * @version 0.0.1-SNAPSHOT
 * @since 10-04-2016
 */
public class WoeshBackupPlugin extends JavaPlugin {
	
	// Variables & Constants.
	private File backupDir;
	private File snapshotsDir;
	private Map<Backup, File> backups;
	private Thread backupThread = null;
	private BukkitTask backupIntervalTask;
	private int backupIntervalSeconds; // [sec].
	private long timeToKeepBackups; // [ms].
	private int minDiskSpaceToAllowBackup; // [MB].
	public static boolean debugEnabled;
	
	private static final String NO_PERMS_MSG = ChatColor.RED + "You do not have permission to use this command.";
	private static final DateFormat RESTORE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	
	public WoeshBackupPlugin() {
	}
	
	// onEnable will run when the plugin is enabled.
	@Override
	public void onEnable() {
		
		// Create the default config file if no "Config.yml" is present.
		this.saveDefaultConfig();
		
		// Read and validate values from the config.
		String backupDirPath = this.getConfig().getString("backupDirPath", "woeshBackups");
		String snapshotsDirPath = this.getConfig().getString("snapshotsDirPath", "snapshots");
		boolean autoBackup = this.getConfig().getBoolean("autoBackup.enabled", true);
		this.backupIntervalSeconds = this.getConfig().getInt("autobackup.interval", 3600);
		if(this.backupIntervalSeconds <= 60) {
			this.getLogger().warning("Invalid config entry found: autobackup.interval has to be >= 60 [sec]. Found: "
					+ this.backupIntervalSeconds + ". Using default value: 3600 [sec].");
			this.backupIntervalSeconds = 3600;
		}
		this.timeToKeepBackups = 1000 * this.getConfig().getInt("maxBackupAge", 1814400);
		if(this.timeToKeepBackups <= 1000 * 3600) {
			this.getLogger().warning("Invalid config entry found: maxBackupAge has to be >= 3600 [sec]. Found: "
					+ (this.timeToKeepBackups / 1000) + ". Using default value: 1814400 [sec] (21 days).");
			this.timeToKeepBackups = 1000 * 1814400;
		}
		this.minDiskSpaceToAllowBackup = this.getConfig().getInt("dontBackupIfLessThanThisSpaceIsAvailableInMB", 1000);
		debugEnabled = this.getConfig().getBoolean("debugEnabled", false);
		
		// Define the directory in which backups will be stored.
		this.backupDir = new File(new File("").getAbsolutePath(), backupDirPath);
		this.snapshotsDir = new File(new File("").getAbsolutePath(), snapshotsDirPath);
		
		// Initialize backups and backupThreads list.
		this.backups = new HashMap<Backup, File>();
		
		// Create a WoeshBackup for all worlds. The delay is there to allow the worlds to load.
		Bukkit.getScheduler().runTaskLater(this, () -> {
			
			// Add a WoeshBackup for all worlds that are loaded but do not have one yet.
			Iterator<World> worldIterator = Bukkit.getWorlds().iterator();
			iterateLoop:
			while(worldIterator.hasNext()) {
				File toBackupWorldDir = worldIterator.next().getWorldFolder().getAbsoluteFile();
				for(Backup backup : WoeshBackupPlugin.this.backups.keySet()) {
					if(backup.getToBackupDir().equals(toBackupWorldDir)) {
						continue iterateLoop;
					}
				}
				ZipFileBackupPartFactory backupPartFactory = new ZipFileBackupPartFactory(
						new File(WoeshBackupPlugin.this.backupDir, toBackupWorldDir.getName()));
				WoeshBackupPlugin.this.backups.put(
						new SimpleBackup(toBackupWorldDir, backupPartFactory, this.getLogger()), null);
			}
		},
		20 * 10); // 10 seconds delay (20 tps).
		
		// Create a WoeshBackup for the plugins directory.
//		File serverBaseDir = Bukkit.getServer().getWorldContainer();
		File ignoreFile = new File("woeshBackupIgnore - plugins.txt");
		List<String> ignorePaths;
		try {
			ignorePaths = readIgnorePaths(ignoreFile);
		} catch (IOException e) {
			ignorePaths = new ArrayList<String>();
			this.getLogger().severe(
					"IOException while reading plugins ignore file. No files will be ignored during the next backup.");
		}
		File toBackupDir = new File("plugins");
		ZipFileBackupPartFactory backupPartFactory =
				new ZipFileBackupPartFactory(new File(this.backupDir, toBackupDir.getName()));
		this.backups.put(new SimpleBackup(toBackupDir, backupPartFactory, this.getLogger(), ignorePaths), ignoreFile);
		
		// Schedule a task to generate initial backups if they do not exist or update existing
		// backups every backupInterval minutes.
		if(autoBackup) {
			long timeSinceLastBackupMillis = System.currentTimeMillis() - this.getPersistentLastBackupDate();
			int timeUntilNextBackupSec = this.backupIntervalSeconds - (int) (timeSinceLastBackupMillis / 1000);
			this.startBackupIntervalTask((timeUntilNextBackupSec < 60 ? 60 : timeUntilNextBackupSec));
		}
		
		// Remove existing generated snapshots.
		new Thread(() -> WoeshBackupPlugin.this.removeGeneratedSnapshots()).start();
		
		// Print disk space feedback.
		this.getLogger().info("Believed free disk space: " + (this.backupDir.getUsableSpace() / 1000000) + "MB.");
		
	}
	
	// onDisable will run when the plugin is disabled.
	@Override
	public void onDisable() {
		this.backupDir = null;
		this.backups = null;
		if(this.backupThread != null) {
			this.backupThread.interrupt();
		}
		Bukkit.getScheduler().cancelTasks(this);
	}
	
	/**
	 * startBackupIntervalTask method.
	 * Schedules the recurring backup task to run after the given initial delay.
	 * Does nothing if the backup task is already running.
	 * @param initialDelaySeconds - The amount of seconds before the task will run.
	 */
	private void startBackupIntervalTask(int initialDelaySeconds) {
		
		// Return if the backup task is active already.
		if(this.backupIntervalTask != null) {
			return;
		}
		
		// Start the backup task.
		final BukkitTask[] task = new BukkitTask[1];
		task[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
			if(!task[0].isCancelled()) {
				WoeshBackupPlugin.this.performBackup();
			}
		},
		20 * initialDelaySeconds,
		20 * this.backupIntervalSeconds);
		this.backupIntervalTask = task[0];
	}
	
	/**
	 * performBackup method.
	 * Creates an initial backup if it did't exist or creates an update backup for the existing backup.
	 * This is done for all WoeshBackup objects.
	 */
	private void performBackup() {
		
		// Create the backup directory if it does not exist.
		if(!this.backupDir.isDirectory()) {
			if(!this.backupDir.mkdir()) {
				WoeshBackupPlugin.this.getLogger().severe("Failed to create the main backup directory at: "
						+ this.backupDir.getAbsolutePath() + ". Skipping backup.");
				return;
			}
		}
		
		// Check if there is at least 1GB of free disk space. Don't backup otherwise.
		long availableDiskSpace = this.backupDir.getUsableSpace();
		if(availableDiskSpace < this.minDiskSpaceToAllowBackup * 1000000L) {
			WoeshBackupPlugin.this.getLogger().severe("Skipping backups since less than "
					+ this.minDiskSpaceToAllowBackup + "MB of free disk space was found("
					+ (availableDiskSpace / 1000000) + "MB).");
			return;
		}
		
		// Return if backups are still/already in progress.
		if(this.isBackupInProgress()) {
			WoeshBackupPlugin.this.getLogger().warning("Skipping backup because a backup is already in progress.");
			return;
		}
		
		// Give feedback about starting the backup.
		Bukkit.broadcastMessage("[WoeshBackup] Starting backups.");
		
		// Add a WoeshBackup for all worlds that are loaded but do not have one yet.
		Iterator<World> worldIterator = Bukkit.getWorlds().iterator();
		iterateLoop:
		while(worldIterator.hasNext()) {
			File toBackupWorldDir = worldIterator.next().getWorldFolder().getAbsoluteFile();
			for(Backup backup : this.backups.keySet()) {
				if(backup.getToBackupDir().equals(toBackupWorldDir)) {
					continue iterateLoop;
				}
			}
			ZipFileBackupPartFactory backupPartFactory =
					new ZipFileBackupPartFactory(new File(this.backupDir, toBackupWorldDir.getName()));
			this.backups.put(new SimpleBackup(toBackupWorldDir, backupPartFactory, this.getLogger()), null);
		}
		
		// Update all backups on a seperate thread.
		final long mergeBeforeDate = System.currentTimeMillis() - this.timeToKeepBackups;
		
		this.backupThread = new Thread() {
			@Override
			public void run() {
				final long fullBackupStartTime = System.currentTimeMillis();
				for(final Backup backup : WoeshBackupPlugin.this.backups.keySet()) {
					
					// Return if the thread has been interrupted (cancelled / server shutting down).
					if(this.isInterrupted()) {
						return;
					}
					
					// Give feedback about starting the backup and store the start time.
					WoeshBackupPlugin.this.getLogger().info(
							"Starting backup: " + backup.getToBackupDir().getName() + ".");
					final long singleBackupStartTime = System.currentTimeMillis();
					
					// Check if the backup directory has the same name as a world.
					// If it does, disable autosave for that world and save it.
					Object[] retInfo = new Object[] {false, null};
					try {
						retInfo = Bukkit.getScheduler().callSyncMethod(
								WoeshBackupPlugin.this, new Callable<Object[]>() {
							@Override
							public Object[] call() throws Exception {
								World world = Bukkit.getWorld(backup.getToBackupDir().getName());
								if(world != null) {
									boolean isAutoSave = world.isAutoSave();
									world.setAutoSave(false);
									world.save();
									return new Object[] {isAutoSave, world};
								}
								return new Object[] {false, null};
							}
						}).get();
					} catch (InterruptedException e) {
						this.interrupt(); // Make sure the interrupt flag is set.
					} catch (ExecutionException e) {
						// Never happens.
						e.printStackTrace();
					}
					final boolean wasAutoSaveEnabled = (boolean) retInfo[0];
					final World world = (World) retInfo[1];
					
					Exception ex = null;
					try {
						
						// Merge (and remove) old backups.
						try {
							backup.merge(mergeBeforeDate);
						} catch (BackupException e) {
							WoeshBackupPlugin.this.getLogger().severe("Merging backups failed for backup: "
									+ backup.getToBackupDir().getName() + ". Here's the stacktrace:\n"
									+ Utils.getStacktrace(e));
						}
					
						// Perform the backup.
						try {
							backup.backup();
						} catch (InterruptedException e) {
							throw e;
						} catch (Exception e) {
							ex = e;
						}
					} catch (InterruptedException e) {
						WoeshBackupPlugin.this.getLogger().warning("Backup was interrupted during execution: "
								+ backup.getToBackupDir().getName());
						return;
					}
					
					// Re-enable auto-save for the world if it was disabled.
					if(wasAutoSaveEnabled && world != null) {
						Bukkit.getScheduler().runTask(WoeshBackupPlugin.this, new Runnable() {
							@Override
							public void run() {
								world.setAutoSave(true);
							}
						});
					}
					
					// Send feedback to console.
					if(WoeshBackupPlugin.this.isEnabled()) {
						float timeElapsed = (float) ((System.currentTimeMillis() - singleBackupStartTime) / 1000);
						String timeElapsedStr = String.format("%.0f sec", timeElapsed);
						if(ex == null) {
							WoeshBackupPlugin.this.getLogger().info("Finished backup: "
									+ backup.getToBackupDir().getName() + " (" + timeElapsedStr + ").");
						} else {
							WoeshBackupPlugin.this.getLogger().severe("Finished backup with errors: "
									+ backup.getToBackupDir().getName() + " (" + timeElapsedStr + ").\n"
									+ (debugEnabled ? "Here's the stacktrace:\n" + Utils.getStacktrace(ex)
										: "Exception type: " + ex.getClass().getSimpleName()
										+ ", Exception message: " + ex.getMessage()
									));
						}
					}
					
				}
				
				// Write the last backup date to file.
				try {
					WoeshBackupPlugin.this.setPersistentLastBackupDate(System.currentTimeMillis());
				} catch (IOException e) {
					WoeshBackupPlugin.this.getLogger().severe(
							"An IOException occured while storing the last backup date.");
				}
				
				// Send feedback since the backups are done.
				final float timeElapsed = (float) ((System.currentTimeMillis() - fullBackupStartTime) / 1000);
				WoeshBackupPlugin.this.getLogger().info(String.format("Backups finished in %.0f sec.", timeElapsed));
				
				// Allow a new backup to start.
				Bukkit.getScheduler().runTask(WoeshBackupPlugin.this, new Runnable() {
					@Override
					public void run() {
						WoeshBackupPlugin.this.backupThread = null;
					}
				});
				
			}
		};
		this.backupThread.start();
	}
	
	/**
	 * loadConfig method.
	 * (Re)loads the "config.yml" file and applies made changes.
	 */
	private void loadConfig() {
		
		// Create the default config file if no "Config.yml" is present.
		this.saveDefaultConfig();
		
		// Reload the config.
		this.reloadConfig();
		
		// Read and validate values from the config.
		String backupDirPath = this.getConfig().getString("backupDirPath", "woeshBackups");
		String snapshotsDirPath = this.getConfig().getString("snapshotsDirPath", "snapshots");
//		boolean autoBackup = this.getConfig().getBoolean("autoBackup.enabled", true);
		int backupIntervalSeconds = this.getConfig().getInt("autobackup.interval", 3600);
		if(backupIntervalSeconds <= 60) {
			this.getLogger().warning("Invalid config entry found: autobackup.interval has to be >= 60 [sec]. Found: "
					+ backupIntervalSeconds + ". Using default value: 3600 [sec].");
			backupIntervalSeconds = 3600;
		}
		this.timeToKeepBackups = 1000 * this.getConfig().getInt("maxBackupAge", 1814400);
		if(this.timeToKeepBackups <= 1000 * 3600) {
			this.getLogger().warning("Invalid config entry found: autobackup.interval has to be >= 3600 [sec]. Found: "
					+ (this.timeToKeepBackups / 1000) + ". Using default value: 1814400 [sec] (21 days).");
			this.timeToKeepBackups = 1000 * 1814400;
		}
		this.minDiskSpaceToAllowBackup = this.getConfig().getInt("dontBackupIfLessThanThisSpaceIsAvailableInMB", 1000);
		if(this.minDiskSpaceToAllowBackup < 1000) {
			this.getLogger().warning(
					"Invalid config entry found: dontBackupIfLessThanThisSpaceIsAvailableInMB has to be >= 1000 [MB]."
					+ " Found: " + this.minDiskSpaceToAllowBackup + ". Using default value: 5000 [MB].");
			this.minDiskSpaceToAllowBackup = 5000;
		}
		
		debugEnabled = this.getConfig().getBoolean("debugEnabled", false);
		
		// Set the directories in which backups/snapshots will be stored if they have changed.
		File backupDir = new File(new File("").getAbsolutePath() + "/" + backupDirPath).getAbsoluteFile();
		File snapshotsDir = new File(new File("").getAbsolutePath() + "/" + snapshotsDirPath).getAbsoluteFile();
		if(!backupDir.equals(this.backupDir)) {
			this.backupDir = backupDir;
			for(Backup backup : this.backups.keySet()) {
				ZipFileBackupPartFactory factory =
						(ZipFileBackupPartFactory) ((SimpleBackup) backup).getBackupPartFactory();
				factory.setStorageDir(new File(this.backupDir, backup.getToBackupDir().getName()));
			}
		}
		if(!snapshotsDir.equals(this.snapshotsDir)) {
			this.snapshotsDir = snapshotsDir;
		}
		
		// Reload the ignore paths for the plugins backup.
		for(Entry<Backup, File> backupEntry : this.backups.entrySet()) {
			if(backupEntry.getValue() != null) {
				try {
					backupEntry.getKey().setIgnorePaths(readIgnorePaths(backupEntry.getValue()));
				} catch (IOException e) {
					this.getLogger().severe("IOException while reading ignore file for backup: "
							+ backupEntry.getKey().getToBackupDir().getName() + ". Ignore file is not reloaded.");
				}
			}
		}
		
		// Restart the backup task if the interval has changed.
		if(backupIntervalSeconds != this.backupIntervalSeconds) {
			this.backupIntervalSeconds = backupIntervalSeconds;
			
			// Cancel and restart the current task if it exists.
			if(this.backupIntervalTask != null) {
				this.backupIntervalTask.cancel();
				this.backupIntervalTask = null;
				int timeUntilNextBackupSeconds;
				if(this.isBackupInProgress()) {
					timeUntilNextBackupSeconds = this.backupIntervalSeconds;
				} else {
					long timeSinceLastBackupMillis = System.currentTimeMillis() - this.getPersistentLastBackupDate();
					timeUntilNextBackupSeconds = this.backupIntervalSeconds - (int) (timeSinceLastBackupMillis / 1000);
					if(timeUntilNextBackupSeconds < 0) {
						timeUntilNextBackupSeconds = 0;
					}
				}
				this.startBackupIntervalTask(timeUntilNextBackupSeconds);
			}
		}
		
		// Give feedback about the backup interval.
		this.getLogger().info(String.format(
				"WoeshBackup will now backup every %d minutes.", (int) (this.backupIntervalSeconds / 60)));
		
	}
	
	/**
	 * isBackupInProgress method.
	 * @return True if a backup is in progress, false otherwise.
	 */
	private boolean isBackupInProgress() {
		return this.backupThread != null && this.backupThread.isAlive();
	}
	
	/**
	 * Reads the given ignore paths file and returns the ignore paths as a list.
	 * @param ignoreFile - The ignore paths file.
	 * @return A list of ignore paths or null if the ignore paths file did not exist.
	 * Note that these paths have not been validated and may have any file separator.
	 * @throws IOException If an I/O error has occurred.
	 */
	private static List<String> readIgnorePaths(File ignoreFile) throws IOException {
		
		// Return null if no ignore file exists.
		if(ignoreFile == null || !ignoreFile.isFile()) {
			return null;
		}
		
		// Read the ignore paths.
		List<String> ignorePaths = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(ignoreFile));
		String line;
		while((line = reader.readLine()) != null) {
			// Add relative paths, assuming that they are abstract. Ignore "//" comments.
			line = line.split("//", 1)[0].trim();
			if(!line.isEmpty()) {
				ignorePaths.add(line);
			}
		}
		reader.close();
		return ignorePaths;
	}
	
	@Override
	public boolean onCommand(final CommandSender sender, Command cmd, String label, String[] args) {
		final String syntaxMessage = "Syntax: /woeshbackup <now, status, on, off, diskinfo,"
				+ " generatesnapshot, removesnapshots, toggledebug, reload>";
		final String tooManyArgsMessage = "Too many arguments.";
		
		// Validate command prefix.
		if(!cmd.getName().equalsIgnoreCase("woeshbackup")) {
			return false; // Command didn't match.
		}
		
		// Check for base permission (redundant but adds safety).
		if(!sender.hasPermission("woeshbackup.woeshbackup")) {
			sender.sendMessage(NO_PERMS_MSG);
			return true;
		}
		
		// Display syntax when no arguments are given.
		if(args.length == 0) {
			sender.sendMessage(syntaxMessage);
			return true;
		}
		
		// Select the right command.
		switch(args[0].toLowerCase()) {
		case "now": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.backupnow")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			// Check args.
			if(args.length != 1) {
				sender.sendMessage(tooManyArgsMessage);
				break;
			}
			
			// Return if a backup is running already.
			if(this.isBackupInProgress()) {
				sender.sendMessage("You cannot start a new backup while a backup is running.");
				break;
			}
			
			// Cancel the current task if it exists.
			boolean taskExists = this.backupIntervalTask != null;
			if(taskExists) {
				this.backupIntervalTask.cancel();
				this.backupIntervalTask = null;
			}
			
			// Perform the backup.
			this.performBackup();
			
			// Re-enable the task if it existed.
			if(taskExists) {
				this.startBackupIntervalTask(this.backupIntervalSeconds);
			}
			
			// Print feedback.
			sender.sendMessage("Backup started. Auto-backups are currently "
					+ (taskExists ? "enabled" : "disabled") + ".");
			
			break;
		}
		case "status": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.status")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			// Check args.
			if(args.length != 1) {
				sender.sendMessage(tooManyArgsMessage);
				break;
			}
			
			// Give feedback about if a backup is in progress.
			sender.sendMessage(this.isBackupInProgress()
					? "There is a backup in progress."
					: "There is no backup in progress.");
			break;
		}
		case "on": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.toggleautobackup")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			// Check args.
			if(args.length != 1) {
				sender.sendMessage(tooManyArgsMessage);
				break;
			}
			
			// Check if there is a task running already.
			boolean taskExists = this.backupIntervalTask != null;
			if(taskExists) {
				sender.sendMessage("WoeshBackup is already enabled.");
			} else {
				long timeSinceLastBackupMillis = System.currentTimeMillis() - this.getPersistentLastBackupDate();
				int timeUntilNextBackupSeconds = this.backupIntervalSeconds - (int) (timeSinceLastBackupMillis / 1000);
				sender.sendMessage(String.format(
						"WoeshBackup will now backup every %d minutes.", (int) (this.backupIntervalSeconds / 60)));
				this.startBackupIntervalTask(
						(timeUntilNextBackupSeconds < 0 ? 0 : timeUntilNextBackupSeconds));
			}
			break;
		}
		case "off": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.toggleautobackup")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			// Check args.
			if(args.length != 1) {
				sender.sendMessage(tooManyArgsMessage);
				break;
			}
			
			// Check if there is a task running already.
			boolean taskExists = this.backupIntervalTask != null;
			if(!taskExists) {
				sender.sendMessage("WoeshBackup is already disabled.");
			} else {
				this.backupIntervalTask.cancel();
				this.backupIntervalTask = null;
				sender.sendMessage("WoeshBackup stopped.");
			}
			
			break;
		}
		case "diskinfo": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.diskinfo")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			// Check args.
			if(args.length != 1) {
				sender.sendMessage(tooManyArgsMessage);
				break;
			}
			
			// Get the root directory and list its properties.
			File file = this.backupDir;
			while(file.getParentFile() != null) {
				file = file.getParentFile();
			}
			if(file.exists()) {
				file.getUsableSpace();
				sender.sendMessage("Listing disk info for: " + file.getAbsolutePath()
						+ "\n  Free disk space: " + (file.getFreeSpace() / 1000000) + "MB"
						+ "\n  Total disk space: " + (file.getTotalSpace() / 1000000) + "MB"
						+ "\n  Free usable disk space: " + (file.getUsableSpace() / 1000000) + "MB");
			} else {
				sender.sendMessage("Unable to get disk info. Root directory could not be resolved.");
			}
			
			break;
		}
		case "generatesnapshot": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.generatesnapshot")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			// Check and parse args.
			if(args.length != 3) {
				sender.sendMessage("Syntax: /woeshbackup generatesnapshot <backupName> <beforeData>."
						+ " beforeDate is in format: yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss");
				break;
			}
			String backupName = args[1];
			String beforeDateStr = args[2];
			long beforeDate;
			try {
				beforeDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").parse(beforeDateStr).getTime();
			} catch (ParseException e) {
				try {
					beforeDate = new SimpleDateFormat("yyyy-MM-dd").parse(beforeDateStr).getTime();
				} catch (ParseException e1) {
					sender.sendMessage("Syntax error: beforeDate has to be in format"
							+ " yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss. Found: " + beforeDateStr);
					break;
				}
			}
			
			// Check if the given backup exists.
			Backup backup = null;
			for(Backup b : this.backups.keySet()) {
				if(b.getToBackupDir().getName().equalsIgnoreCase(backupName)) {
					backup = b;
					break;
				}
			}
			if(backup == null) {
				sender.sendMessage("Backup could not be found: " + backupName);
				break;
			}
			
			// Check if there is at least 2GB of free disk space. Don't restore otherwise.
			long availableDiskSpace =
					(this.snapshotsDir.isDirectory() ? this.snapshotsDir : this.backupDir).getUsableSpace();
			if(availableDiskSpace < this.minDiskSpaceToAllowBackup * 1000000L) {
				sender.sendMessage("Cannot generate snapshots because less than " + this.minDiskSpaceToAllowBackup
						+ "MB of free disk space was found(" + (availableDiskSpace / 1000000) + "MB).");
				break;
			}
			
			// Print feedback about starting.
			sender.sendMessage("Generating snapshot for backup: "
					+ backup.getToBackupDir().getName() + ", before date: " + beforeDateStr);
			
			// Create a snapshot for the given date (merge backups and place the result in the snapshots directory).
			final Backup finalBackup = backup;
			final long finalBeforeDate = beforeDate;
			new Thread(() -> {
//				// Remove previously generated snapshots.
//				WoeshBackupPlugin.this.removeGeneratedSnapshots();
				
				// Generate a snapshot from the backup.
				BackupException ex = null;
				try {
					File restoreToDir =
							new File(WoeshBackupPlugin.this.snapshotsDir, finalBackup.getToBackupDir().getName());
					finalBackup.restore(finalBeforeDate, (restoreFileDate) ->
							new BackupRestoreZipFileWriter(restoreToDir, restoreFileDate));
				} catch (BackupException e) {
					ex = e;
				} catch (InterruptedException e) {
					sender.sendMessage("Backup restore was interrupted during execution: "
							+ finalBackup.getToBackupDir().getName());
					return;
				}
				
				// Give feedback to the player.
				final BackupException finalEx = ex;
				if(WoeshBackupPlugin.this.isEnabled()) {
					Bukkit.getScheduler().runTask(WoeshBackupPlugin.this, () -> {
						if(debugEnabled) {
							WoeshBackupPlugin.this.getLogger().severe("An Exception occurred "
									+ "while generating a snapshot for backup: "
									+ finalBackup.getToBackupDir().getName() + ". Here's the stacktrace:\n"
									+ Utils.getStacktrace(finalEx));
						}
						if(finalEx == null) {
							sender.sendMessage("Succesfully generated snapshot for backup: "
									+ finalBackup.getToBackupDir().getName());
						} else {
							if(finalEx.getCause() == null) {
								sender.sendMessage("Failed to generate snapshot: "
										+ finalBackup.getToBackupDir().getName()
										+ ". Info: " + finalEx.getMessage());
							} else {
								String message = "Failed to generate snapshot: "
										+ finalBackup.getToBackupDir().getName()
										+ ". Info: " + finalEx.getMessage();
								Throwable cause = finalEx.getCause();
								while(cause != null) {
									message += "\nCaused by: " + finalEx.getCause().getClass().getSimpleName()
											+ "\n\tMessage: " + finalEx.getCause().getMessage();
									cause = cause.getCause();
								}
								sender.sendMessage(message);
							}
						}
					});
				}
			}).start();
			break;
		}
		case "removesnapshots": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.removesnapshot")
					&& !sender.hasPermission("woeshbackup.removesnapshots")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			int amount = this.removeGeneratedSnapshots();
			sender.sendMessage(amount >= 0 ? "Successfully removed " + amount + " snapshots."
					: "An error occured while removing one or multiple snapshots.");
			break;
		}
		case "toggledebug": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.toggledebug")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			debugEnabled = !debugEnabled;
			sender.sendMessage("Debug " + (debugEnabled ? "enabled" : "disabled") + ".");
			break;
		}
		case "reload": {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.reload")) {
				sender.sendMessage(NO_PERMS_MSG);
				return true;
			}
			
			this.loadConfig();
			sender.sendMessage("Config reloaded.");
			break;
		}
		default: {
			sender.sendMessage(syntaxMessage);
		}
		}
		return true;
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		
		// Check for permission.
		if(!sender.hasPermission("woeshbackup.woeshbackup") || args.length == 0) { // 0 length should be impossible.
			return new ArrayList<String>();
		}
		
		// TAB-complete "/woeshbackup <arg>".
		if(args.length == 1) {
			List<String> ret = new ArrayList<String>();
			for(String comp : new String[] {"now", "status", "on", "off", "diskinfo",
					"generatesnapshot", "removesnapshots", "toggledebug", "reload"}) {
				if(comp.startsWith(args[0].toLowerCase())) {
					ret.add(comp);
				}
			}
			return ret;
		}
		
		// TAB-complete "/woeshbackup generatesnapshot <backupName> <beforeData>".
		// beforeDate is in format: yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss.
		if(args[0].equalsIgnoreCase("generatesnapshot")) {
			
			// Check for permission.
			if(!sender.hasPermission("woeshbackup.generatesnapshot")) {
				return new ArrayList<String>(); // Return an empty list so no info about backups can be obtained.
			}
			
			if(args.length == 2) {
				List<String> ret = new ArrayList<String>();
				for(Backup backup : this.backups.keySet()) {
					if(backup.getToBackupDir().getName().toLowerCase().startsWith(args[1].toLowerCase())) {
						ret.add(backup.getToBackupDir().getName());
					}
				}
				return ret;
			}
			if(args.length == 3) {
				List<String> ret = new ArrayList<String>();
				for(Backup backup : this.backups.keySet()) {
					if(backup.getToBackupDir().getName().equalsIgnoreCase(args[1])) {
						try {
							for(Long restoreDateThresh : backup.getRestoreDateThresholds()) {
								String restoreDate = RESTORE_DATE_FORMAT.format(new Date(restoreDateThresh));
								if(restoreDate.startsWith(args[2])) {
									ret.add(restoreDate);
								}
							}
						} catch (IOException e) {
							this.getLogger().warning("An exception occurred while tabcompleting backup part dates."
									+ " Here's the stacktrace: " + Utils.getStacktrace(e));
						}
					}
				}
				return ret;
			}
		}
		
		// Don't use the default TABcompleter, completing names is useless here.
		return new ArrayList<String>();
	}
	
	/**
	 * removeGeneratedSnapshots method.
	 * Removes all generated snapshots from the snapshots directory.
	 * @return The number of removed snapshots if the removal was succesful or -1 if one or multiple snapshots
	 * could not be removed.
	 */
	private int removeGeneratedSnapshots() {
		File[] snapDirs = this.snapshotsDir.listFiles();
		if(snapDirs == null) {
			return 0; // The snapshots directory was already empty.
		}
		boolean success = true;
		int count = 0;
		Pattern snapshotPattern = Pattern.compile(
				"^\\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}-\\d{2}\\.zip$"); // Format: "yyyy-MM-dd HH-mm-ss.zip".
		for(File snapDir : snapDirs) {
			if(snapDir.isDirectory()) {
				File[] snapDirFiles = snapDir.listFiles();
				if(snapDirFiles == null) {
					continue; // An I/O error occurred, skip the directory.
				}
				for(File file : snapDirFiles) {
					if(file.isFile() && snapshotPattern.matcher(file.getName()).matches()) {
						WoeshBackupPlugin.this.getLogger().info(
								"Removing snapshot: " + snapDir.getName() + "/" + file.getName());
						success = success && file.delete();
						count++;
					}
				}
			}
		}
		return success ? count : -1;
	}
	
	/**
	 * setPersistentLastBackupDate method.
	 * Sets the time a backup was last performed.
	 * @param time - The time when the last backup started.
	 * @throws IOException - When the time could not be set in the ".lastBackup" file as lastModified time.
	 */
	private void setPersistentLastBackupDate(long time) throws IOException {
		File timeFile = new File(this.backupDir, ".lastBackup");
		if(!timeFile.exists()) {
			timeFile.createNewFile();
		}
		if(!timeFile.setLastModified(System.currentTimeMillis())) {
			throw new IOException("Unable to set last modified time for file: " + timeFile.getAbsolutePath());
		}
	}
	
	/**
	 * getPersistentLastBackupDate method.
	 * @return The last known time a backup was created according to the lastBackup.txt file lastModified timestamp
	 * or 0 when unknown.
	 */
	private long getPersistentLastBackupDate() {
		File timeFile = new File(this.backupDir, ".lastBackup");
		return timeFile.lastModified();
	}
}
