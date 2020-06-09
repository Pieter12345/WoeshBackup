package io.github.pieter12345.woeshbackup.bukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import io.github.pieter12345.woeshbackup.Backup;
import io.github.pieter12345.woeshbackup.BoundedInterval;
import io.github.pieter12345.woeshbackup.SimpleBackup;
import io.github.pieter12345.woeshbackup.ZipFileBackupPartFactory;
import io.github.pieter12345.woeshbackup.api.WoeshBackupAPI;
import io.github.pieter12345.woeshbackup.exceptions.BackupException;
import io.github.pieter12345.woeshbackup.utils.AnsiColor;
import io.github.pieter12345.woeshbackup.utils.Utils;

/**
 * WoeshBackupPlugin class.
 * This is the main class that will be loaded by Bukkit.
 * @author P.J.S. Kools
 * @since 10-04-2016
 */
public class WoeshBackupPlugin extends JavaPlugin implements WoeshBackupAPI {
	
	// Variables & Constants.
	private File backupDir = null;
	private File snapshotsDir = null;
	private Map<Backup, File> backups;
	private Thread backupThread = null;
	private BukkitTask backupIntervalTask = null;
	private long lastBackupStartTime = -1; // [ms].
	private int backupIntervalSeconds = -1; // [sec].
	private List<BoundedInterval> mergeIntervals; // {{interval [sec], duration [sec]}, ...}.
	private int minDiskSpaceToAllowBackup; // [MB].
	public boolean debugEnabled;
	
	private final WoeshBackupCommandExecutor commandExecutor;
	private final WoeshBackupTabCompleter tabCompleter;
	private final Logger logger;
	
	public WoeshBackupPlugin() {
		// This runs when Bukkit creates WoeshBackup. Use onEnable() for initialization on enable instead.
		
		// Create a logger that adds the plugin name as a colorized name tag and converts Minecraft colorcodes to ANSI.
		this.logger = new Logger(WoeshBackupPlugin.class.getCanonicalName(), null) {
			private final String prefix = WoeshBackupPlugin.this.getDescription().getPrefix();
			private final String pluginName = ChatColor.GOLD + "[" + ChatColor.DARK_AQUA
					+ (this.prefix != null ? this.prefix : WoeshBackupPlugin.this.getDescription().getName())
					+ ChatColor.GOLD + "] ";
			
			@Override
			public void log(LogRecord logRecord) {
				logRecord.setMessage(AnsiColor.colorize(this.pluginName
						+ (logRecord.getLevel().equals(Level.SEVERE) ? ChatColor.RED : ChatColor.GREEN)
						+ logRecord.getMessage() + ChatColor.RESET, ChatColor.COLOR_CHAR));
				super.log(logRecord);
			}
		};
		this.logger.setParent(this.getServer().getLogger());
		this.logger.setLevel(Level.ALL);
		
		// Create the command executor and tab completer.
		this.commandExecutor = new WoeshBackupCommandExecutor(this, this, this.logger);
		this.tabCompleter = new WoeshBackupTabCompleter(this, this.logger);
	}
	
	@Override
	public void onEnable() {
		
		// Initialize backups map.
		this.backups = new HashMap<Backup, File>();
		
		// Load the config file, creating the default config if it did not exist.
		this.loadConfig();
		
		// Create a WoeshBackup for all worlds. The delay is there to allow the worlds to load (10 sec delay @ 20tps).
		this.addBackupsForWorlds();
		Bukkit.getScheduler().runTaskLater(this, () -> WoeshBackupPlugin.this.addBackupsForWorlds(), 20 * 10);
		
		// Create a WoeshBackup for the plugins directory.
//		File serverBaseDir = Bukkit.getServer().getWorldContainer();
		File ignoreFile = new File("woeshBackupIgnore - plugins.txt");
		List<String> ignorePaths;
		try {
			ignorePaths = readIgnorePaths(ignoreFile);
		} catch (IOException e) {
			ignorePaths = new ArrayList<String>();
			this.logger.severe(
					"IOException while reading plugins ignore file. No files will be ignored during the next backup.");
		}
		File toBackupDir = new File("plugins");
		ZipFileBackupPartFactory backupPartFactory =
				new ZipFileBackupPartFactory(new File(this.backupDir, toBackupDir.getName()));
		this.backups.put(new SimpleBackup(toBackupDir, backupPartFactory, this.logger, ignorePaths), ignoreFile);
		
		// Schedule a task to update the backups every backupInterval minutes, at least one minute from now.
		boolean autoBackup = this.getConfig().getBoolean("autoBackup.enabled", true);
		if(autoBackup) {
			int timeSinceLastBackupSec = (int) ((System.currentTimeMillis() - this.getLastBackupTime()) / 1000);
			int timeUntilNextBackupSec = this.backupIntervalSeconds - timeSinceLastBackupSec;
			this.startBackupIntervalTask((timeUntilNextBackupSec < 60 ? 60 : timeUntilNextBackupSec));
		}
		
		// Remove existing generated snapshots if the option is set in the config.
		boolean removeSnapshotsOnEnable = this.getConfig().getBoolean("removeSnapshotsOnEnable", true);
		if(removeSnapshotsOnEnable) {
			int count = WoeshBackupPlugin.this.removeGeneratedSnapshots();
			if(count > 0) {
				this.logger.info("Removed " + count + " snapshots.");
			}
		}
		
		// Print disk space feedback.
		this.logger.info("Believed free disk space: " + (this.backupDir.getUsableSpace() / 1000000) + "MB.");
	}
	
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
	 * Schedules the recurring backup task to run after the given initial delay.
	 * Does nothing if the backup task is already running.
	 * @param initialDelaySeconds - The delay before the task will run in seconds.
	 */
	private void startBackupIntervalTask(int initialDelaySeconds) {
		
		// Return if the backup task is active already.
		if(this.backupIntervalTask != null && !this.backupIntervalTask.isCancelled()) {
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
	
	@Override
	public void startBackupIntervalTask() {
		int timeSinceLastBackupSec = (int) ((System.currentTimeMillis() - this.getLastBackupTime()) / 1000);
		int timeUntilNextBackupSec = this.backupIntervalSeconds - timeSinceLastBackupSec;
		this.startBackupIntervalTask((timeUntilNextBackupSec < 0 ? 0 : timeUntilNextBackupSec));
	}
	
	@Override
	public void stopBackupIntervalTask() {
		
		// Return if the backup task is not active.
		if(this.backupIntervalTask == null) {
			return;
		}
		
		// Stop the backup task.
		this.backupIntervalTask.cancel();
		this.backupIntervalTask = null;
	}
	
	@Override
	public boolean backupIntervalTaskActive() {
		return this.backupIntervalTask != null;
	}
	
	@Override
	public void performBackup() {
		
		// Return if backups are still/already in progress.
		if(this.backupInProgress()) {
			this.logger.warning("Skipping backup because a backup is already in progress.");
			return;
		}
		
		// Create the backup directory if it does not exist.
		if(!this.backupDir.isDirectory()) {
			if(!this.backupDir.mkdir()) {
				this.logger.severe("Failed to create the main backup directory at: "
						+ this.backupDir.getAbsolutePath() + ". Skipping backup.");
				return;
			}
		}
		
		// Abort backup if there is less than some minimum amount free disk space.
		long availableDiskSpace = this.backupDir.getUsableSpace();
		if(availableDiskSpace < this.minDiskSpaceToAllowBackup * 1000000L) {
			this.logger.severe("Skipping backups since less than "
					+ this.minDiskSpaceToAllowBackup + "MB of free disk space was found("
					+ (availableDiskSpace / 1000000) + "MB).");
			return;
		}
		
		// Give feedback about starting the backup.
		String prefix = ChatColor.GOLD + "[" + ChatColor.DARK_AQUA
				+ "WoeshBackup" + ChatColor.GOLD + "]" + ChatColor.GREEN + " ";
		Bukkit.broadcastMessage(prefix + "Starting backups.");
		
		// Set the last backup start time.
		final long currentTime = System.currentTimeMillis();
		this.setLastBackupTime(currentTime);
		
		// Add a WoeshBackup for all worlds that are loaded but do not have one yet.
		this.addBackupsForWorlds();
		
		// Update all backups on a separate thread.
		final List<BoundedInterval> mergeIntervals = new ArrayList<>(this.mergeIntervals); // Clone for thread safety.
		this.backupThread = new Thread() {
			@Override
			public void run() {
				final long fullBackupStartTime = currentTime;
				
				// Update all backups.
				for(final Backup backup : WoeshBackupPlugin.this.backups.keySet()) {
					
					// Return if the thread has been interrupted (cancelled / server shutting down).
					if(this.isInterrupted()) {
						return;
					}
					
					// Give feedback about starting the backup and store the start time.
					WoeshBackupPlugin.this.logger.info(
							"Starting backup: " + backup.getToBackupDir().getName() + ".");
					final long singleBackupStartTime = System.currentTimeMillis();
					
					Exception ex = null;
					try {
						
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
							throw e;
						} catch (ExecutionException e) {
							// Never happens.
							throw new Error(e);
						}
						final boolean wasAutoSaveEnabled = (boolean) retInfo[0];
						final World world = (World) retInfo[1];
						
						// Merge (and remove) old backups.
						try {
							backup.merge(mergeIntervals, currentTime);
						} catch (BackupException e) {
							WoeshBackupPlugin.this.logger.severe("Merging backups failed for backup: "
									+ backup.getToBackupDir().getName() + ". Here's the stacktrace:\n"
									+ Utils.getStacktrace(e));
						}
					
						// Perform the backup.
						try {
							backup.backup(currentTime);
						} catch (InterruptedException e) {
							throw e;
						} catch (Exception e) {
							ex = e;
						}
						
						// Re-enable auto-save for the world if it was disabled.
						if(wasAutoSaveEnabled && world != null) {
							Bukkit.getScheduler().runTask(WoeshBackupPlugin.this, () -> world.setAutoSave(true));
						}
					} catch (InterruptedException e) {
						WoeshBackupPlugin.this.logger.warning("Backup was interrupted during execution: "
								+ backup.getToBackupDir().getName());
						return;
					}
					
					// Send feedback to console.
					if(WoeshBackupPlugin.this.isEnabled()) {
						float timeElapsed = (float) ((System.currentTimeMillis() - singleBackupStartTime) / 1000);
						String timeElapsedStr = String.format("%.0f sec", timeElapsed);
						if(ex == null) {
							WoeshBackupPlugin.this.logger.info("Finished backup: "
									+ backup.getToBackupDir().getName() + " (" + timeElapsedStr + ").");
						} else {
							WoeshBackupPlugin.this.logger.severe("Finished backup with errors: "
									+ backup.getToBackupDir().getName() + " (" + timeElapsedStr + ").\n"
									+ (WoeshBackupPlugin.this.debugEnabled
											? "Here's the stacktrace:\n" + Utils.getStacktrace(ex)
											: "Exception type: " + ex.getClass().getSimpleName()
											+ ", Exception message: " + ex.getMessage()
									));
						}
					}
					
				}
				
				// Write the last backup start time to file.
				try {
					WoeshBackupPlugin.this.storeLastBackupTime();
				} catch (IOException e) {
					WoeshBackupPlugin.this.logger.severe("An IOException occured while storing the last backup date.");
				}
				
				// Send feedback since the backups are done.
				final float timeElapsed = (float) ((System.currentTimeMillis() - fullBackupStartTime) / 1000);
				WoeshBackupPlugin.this.logger.info(String.format("Backups finished in %.0f sec.", timeElapsed));
				
				// Allow a new backup to start.
				Bukkit.getScheduler().runTask(WoeshBackupPlugin.this, () -> WoeshBackupPlugin.this.backupThread = null);
				
			}
		};
		this.backupThread.setName("WoeshBackup Backup Thread");
		this.backupThread.start();
	}
	
	@Override
	public boolean backupInProgress() {
		return this.backupThread != null && this.backupThread.isAlive();
	}
	
	/**
	 * Sets the time on which the last backup started.
	 * @param time - The time on which the last backup started.
	 */
	private void setLastBackupTime(long time) {
		this.lastBackupStartTime = time;
	}
	
	@Override
	public long getLastBackupTime() {
		if(this.lastBackupStartTime == -1) {
			this.lastBackupStartTime = this.getPersistentLastBackupTime();
		}
		return this.lastBackupStartTime;
	}
	
	/**
	 * Stores the time on which the last backup started as the lastModified timestamp in the persistent
	 * ".lastBackup" file.
	 * @throws IOException - When the time could not be set in the ".lastBackup" file as lastModified time.
	 */
	private void storeLastBackupTime() throws IOException {
		File timeFile = new File(this.backupDir, ".lastBackup");
		if(!timeFile.exists()) {
			timeFile.createNewFile();
		}
		if(!timeFile.setLastModified(this.lastBackupStartTime)) {
			throw new IOException("Unable to set last modified time for file: " + timeFile.getAbsolutePath());
		}
	}
	
	/**
	 * Gets the time on which a backup was last performed from the persistent ".lastBackup" file.
	 * @return The time on which the last backup that has finished started, according to the ".lastBackup" file
	 * lastModified timestamp. Returns 0 when no persistent last backup time was available.
	 */
	private long getPersistentLastBackupTime() {
		File timeFile = new File(this.backupDir, ".lastBackup");
		return timeFile.lastModified();
	}
	
	@Override
	public int getBackupInterval() {
		return this.backupIntervalSeconds;
	}
	
	@Override
	public int getMinRequiredDiskSpace() {
		return this.minDiskSpaceToAllowBackup;
	}
	
	@Override
	public int removeGeneratedSnapshots() {
		File[] snapDirs = this.snapshotsDir.listFiles();
		if(snapDirs == null) {
			return 0; // The snapshots directory does not exist or an I/O error has occurred.
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
				boolean snapDirEmpty = true;
				for(File file : snapDirFiles) {
					if(file.isFile() && snapshotPattern.matcher(file.getName()).matches()) {
						this.logger.info("Removing snapshot: " + snapDir.getName() + "/" + file.getName());
						success = success && file.delete();
						count++;
					} else {
						snapDirEmpty = false;
					}
				}
				if(snapDirEmpty) {
					snapDir.delete();
				}
			}
		}
		return success ? count : -1;
	}
	
	@Override
	public void loadConfig() {
		
		// Create the default config file if no "config.yml" is present.
		this.saveDefaultConfig();
		
		// Reload the config.
		this.reloadConfig();
		
		// Read and validate values from the config.
		String backupDirPath = this.getConfig().getString("backupDirPath", "woeshBackups");
		String snapshotsDirPath = this.getConfig().getString("snapshotsDirPath", "snapshots");
		int backupIntervalSeconds = (int) this.getConfigTimeSeconds("autoBackup.interval", 3600);
		if(backupIntervalSeconds < 60) {
			this.logger.warning("Invalid config entry found: autoBackup.interval has to be >= 60 [sec]. Found: "
					+ backupIntervalSeconds + ". Using default value: 3600 [sec].");
			backupIntervalSeconds = 3600;
		}
		
		// Read and valiate merge intervals.
		List<?> mergeIntervalsRaw = this.getConfig().getList("mergeIntervals");
		List<BoundedInterval> mergeIntervals = new ArrayList<>();
		if(mergeIntervalsRaw == null) {
			this.logger.warning("No mergeIntervals supplied in the configuration. This means that backups will never"
					+ " be merged and that more and more disk space will be used. You can put merge interval with"
					+ " interval -1 and duration -1 to never backup forever and suppress this warning.");
		} else {
			for(Object mergeIntervalRaw : mergeIntervalsRaw) {
				if(!(mergeIntervalRaw instanceof List<?>)) {
					this.logger.warning("Invalid config entry found: mergeIntervals has to be a list of lists,"
							+ " where each list is in format: [interval (sec), duration (sec)].");
					mergeIntervals.add(new BoundedInterval(-1, -1)); // Safe fallback. Don't merge after this.
					break;
				}
				Object intervalRaw = ((List<?>) mergeIntervalRaw).get(0);
				Object durationRaw = ((List<?>) mergeIntervalRaw).get(1);
				Long interval = this.getTimeSeconds(intervalRaw);
				Long duration = this.getTimeSeconds(durationRaw);
				if(interval == null || interval < -1) {
					this.logger.warning("Invalid config entry found in mergeIntervals: interval has to be a"
							+ " valid time representation greater or equal to -1. Found: " + intervalRaw);
					mergeIntervals.add(new BoundedInterval(-1, -1)); // Safe fallback. Don't merge after this.
					break;
				}
				if(duration == null || duration < -1) {
					this.logger.warning("Invalid config entry found in mergeIntervals: duration has to be a"
							+ " valid time representation greater or equal to -1. Found: " + durationRaw);
					mergeIntervals.add(new BoundedInterval(-1, -1)); // Safe fallback. Don't merge after this.
					break;
				}
				mergeIntervals.add(new BoundedInterval(interval, duration));
			}
		}
		this.mergeIntervals = mergeIntervals;
		
		this.minDiskSpaceToAllowBackup = this.getConfig().getInt("dontBackupIfLessThanThisSpaceIsAvailableInMB", 5000);
		if(this.minDiskSpaceToAllowBackup < 1000) {
			this.logger.warning(
					"Invalid config entry found: dontBackupIfLessThanThisSpaceIsAvailableInMB has to be >= 1000 [MB]."
					+ " Found: " + this.minDiskSpaceToAllowBackup + ". Using default value: 5000 [MB].");
			this.minDiskSpaceToAllowBackup = 5000;
		}
		
		this.debugEnabled = this.getConfig().getBoolean("debugEnabled", false);
		
		// Set the directories in which backups/snapshots will be stored if they have changed.
		File backupDir = new File(new File("").getAbsoluteFile(), backupDirPath);
		File snapshotsDir = new File(new File("").getAbsoluteFile(), snapshotsDirPath);
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
					this.logger.severe("IOException while reading ignore file for backup: "
							+ backupEntry.getKey().getToBackupDir().getName() + ". Ignore file is not reloaded.");
				}
			}
		}
		
		// Restart the backup task if the interval has changed.
		if(backupIntervalSeconds != this.backupIntervalSeconds) {
			this.backupIntervalSeconds = backupIntervalSeconds;
			
			// Cancel and restart the current task if it exists.
			if(this.backupIntervalTaskActive()) {
				this.stopBackupIntervalTask();
				this.startBackupIntervalTask();
			}
		}
		
		// Give feedback about the backup interval.
		this.logger.info(String.format(
				"WoeshBackup will now backup every %d minutes.", (this.backupIntervalSeconds / 60)));
	}
	
	/**
	 * Gets a time in seconds from the configuration. Accepted config values:
	 * Integer, Long, Float/Double (will be rounded to long),
	 * String in format (\\d+[dhms])+ (e.g. "1d12h5m30s" or "5d").
	 * @param configPath - Path in the configuration.
	 * @param def - Default value.
	 * @return The time in seconds or the default value if the time was not present or not in a valid format.
	 */
	private long getConfigTimeSeconds(String configPath, long def) {
		
		// Get object from the configuration.
		Object obj = this.getConfig().get(configPath);
		
		// Convert the object.
		Long time = this.getTimeSeconds(obj);
		
		// Return the result.
		return (time == null ? def : time);
	}
	
	/**
	 * Gets the time in seconds from the given {@link Object}. Accepted values:
	 * {@link Integer}, {@link Long}, {@link Float}/{@link Double} (will be rounded to long),
	 * {@link String} in format (\\d+[dhms])+ (e.g. "1d12h5m30s" or "5d").
	 * @param obj - The object to convert.
	 * @return The time in seconds or {@code null} if the object was not in a valid format.
	 */
	private Long getTimeSeconds(Object obj) {
		
		// Return null if null is given.
		if(obj == null) {
			return null;
		}
		
		// Return a long if a number was found. This may be rounded and/or truncated.
		if(obj instanceof Number) {
			return ((Number) obj).longValue();
		}
		
		// Parse string value to time.
		if(obj instanceof String) {
			String str = (String) obj;
			if(str.isEmpty()) {
				return null;
			}
			long time = 0;
			int startInd = 0;
			for(int i = 0; i < str.length(); i++) {
				char ch = str.charAt(i);
				if(ch >= '0' && ch <= '9') {
					continue; // Skip numbers.
				}
				if(startInd == i) {
					return null; // Invalid time identifier.
				}
				long num = Long.parseLong(str.substring(startInd, i));
				switch(ch) {
					case 'y': time += num * 60 * 60 * 24 * 365; break;
					case 'w': time += num * 60 * 60 * 24 * 7; break;
					case 'd': time += num * 60 * 60 * 24; break;
					case 'h': time += num * 60 * 60; break;
					case 'm': time += num * 60; break;
					case 's': time += num; break;
					default: return null; // Invalid time identifier.
				}
				startInd = i + 1; // Start of the next number.
			}
			if(startInd != str.length()) {
				return null; // Trailing number(s).
			}
			return time;
		}
		
		// Return default for unknown values.
		return null;
	}
	
	@Override
	public Set<Backup> getBackups() {
		return Collections.unmodifiableSet(this.backups.keySet());
	}
	
	@Override
	public File getBackupDir() {
		return this.backupDir;
	}
	
	@Override
	public File getSnapshotsDir() {
		return this.snapshotsDir;
	}
	
	@Override
	public boolean debugEnabled() {
		return this.debugEnabled;
	}
	
	@Override
	public void setDebugEnabled(boolean enabled) {
		this.debugEnabled = enabled;
	}
	
	/**
	 * Adds a backup for all loaded worlds. If a world already has a corresponding backup, it is ignored.
	 */
	private void addBackupsForWorlds() {
		iterateLoop:
		for(World world : Bukkit.getWorlds()) {
			File toBackupWorldDir = world.getWorldFolder().getAbsoluteFile();
			for(Backup backup : this.backups.keySet()) {
				if(backup.getToBackupDir().equals(toBackupWorldDir)) {
					continue iterateLoop;
				}
			}
			ZipFileBackupPartFactory backupPartFactory =
					new ZipFileBackupPartFactory(new File(this.backupDir, toBackupWorldDir.getName()));
			this.backups.put(new SimpleBackup(toBackupWorldDir, backupPartFactory, this.logger), null);
		}
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
	public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
		
		// Check if the plugin is enabled and validate the command prefix.
		if(!this.isEnabled() || !command.getName().equalsIgnoreCase("woeshbackup")) {
			return false;
		}
		
		// Pass the command to the command executor.
		return this.commandExecutor.onCommand(sender, command, label, args);
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return this.tabCompleter.onTabComplete(sender, command, alias, args);
	}
}
