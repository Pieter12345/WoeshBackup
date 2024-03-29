package io.github.pieter12345.woeshbackup.bukkit;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import io.github.pieter12345.woeshbackup.Backup;
import io.github.pieter12345.woeshbackup.BackupRestoreZipFileWriter;
import io.github.pieter12345.woeshbackup.api.WoeshBackupAPI;
import io.github.pieter12345.woeshbackup.exceptions.BackupException;
import io.github.pieter12345.woeshbackup.utils.Utils;

/**
 * The {@link CommandExecutor} for the WoeshBackup Bukkit plugin.
 * @author P.J.S. Kools
 */
public class WoeshBackupCommandExecutor implements CommandExecutor {
	
	private final WoeshBackupAPI api;
	private final Plugin plugin;
	private final Logger logger;
	
	private static final String PREFIX_RAW =
			ChatColor.GOLD + "[" + ChatColor.DARK_AQUA + "WoeshBackup" + ChatColor.GOLD + "]";
	private static final String PREFIX_INFO = PREFIX_RAW + ChatColor.GREEN + " ";
	private static final String PREFIX_ERROR = PREFIX_RAW + ChatColor.RED + " ";
	private static final String NO_PERMS_MSG = PREFIX_ERROR + "You do not have permission to use this command.";
	private static final String TOO_MANY_ARGS_MSG = PREFIX_ERROR + "Too many arguments.";
	
	/**
	 * Creates a new {@link CommandExecutor} for WoeshBackup commands.
	 * @param api - The {@link WoeshBackupAPI}.
	 * @param plugin - The {@link Plugin} using this {@link CommandExecutor}.
	 * @param logger - The logger used for error logging.
	 */
	public WoeshBackupCommandExecutor(WoeshBackupAPI api, Plugin plugin, Logger logger) {
		this.api = api;
		this.plugin = plugin;
		this.logger = logger;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		
		// Check for base permission.
		if(!sender.hasPermission("woeshbackup.use")) {
			sender.sendMessage(NO_PERMS_MSG);
			return true;
		}
		
		// "/woeshbackup".
		if(args.length == 0) {
			args = new String[] {"help"};
		}
		
		switch(args[0].toLowerCase()) {
			case "help": {
				
				// "/woeshbackup help [command]".
				if(args.length == 1) {
					List<String> authors = this.plugin.getDescription().getAuthors();
					String authorsStr = "Author" + (authors.size() == 1 ? "" : "s") + ": &8"
							+ (authors.size() == 0 ? "Unknown"
							: Utils.glueIterable(authors, (String str) -> str, "&a, &8")) + "&a.";
					sender.sendMessage((PREFIX_INFO + colorize(
							"&aVersion: &8" + this.plugin.getDescription().getVersion() + "&a. " + authorsStr
							+ "\n&6  - /woeshbackup help [subcommand]"
							+ "\n&3    Displays this page or information about the subcommand."
							+ "\n&6  - /woeshbackup status"
							+ "\n&3    Displays the backup status."
							+ "\n&6  - /woeshbackup now [--ignorelimit]"
							+ "\n&3    Creates a new backup."
							+ "\n&6  - /woeshbackup on"
							+ "\n&3    Enables the backup interval task."
							+ "\n&6  - /woeshbackup off"
							+ "\n&3    Disables the backup interval task."
							+ "\n&6  - /woeshbackup diskinfo"
							+ "\n&3    Displays the total, free and usable disk space."
							+ "\n&6  - /woeshbackup generatesnapshot <backupName> <date> [--ignorelimit]"
							+ "\n&3    Generates a snapshot for the given backup on the given date."
							+ "\n&6  - /woeshbackup removesnapshots"
							+ "\n&3    Removes all generated snapshots."
							+ "\n&6  - /woeshbackup toggledebug"
							+ "\n&3    Toggles debug mode."
							+ "\n&6  - /woeshbackup reload"
							+ "\n&3    Reloads the config.")).split("\n"));
				} else if(args.length == 2) {
					switch(args[1].toLowerCase()) {
						case "help":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup help &8-&3 Displays command help."));
							return true;
						case "status":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup status &8-&3 Displays the backup status."));
							return true;
						case "now":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup now [--ignorelimit] &8-&3 Creates a new backup."
									+ " The \"--ignorelimit\" argument can be used to bypass the minimum disk space"
									+ " requirement set in the configuration."));
							return true;
						case "on":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup on &8-&3 Enables the backup interval task."));
							return true;
						case "off":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup off &8-&3 Disables the backup interval task."));
							return true;
						case "diskinfo":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup diskinfo &8-&3 Displays the total, free and usable disk space."));
							return true;
						case "generatesnapshot":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup generatesnapshot <backupName> <date> [--ignorelimit] &8-&3"
									+ " Generates a snapshot for the given backup on the given date."
									+ " date is in format: yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss."
									+ " The \"--ignorelimit\" argument can be used to bypass the minimum disk space"
									+ " requirement set in the configuration."));
							return true;
						case "removesnapshots":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup removesnapshots &8-&3 Removes all generated snapshots."));
							return true;
						case "toggledebug":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup toggledebug &8-&3 Toggles debug mode."));
							return true;
						case "reload":
							sender.sendMessage(PREFIX_INFO + colorize(
									"&6/woeshbackup reload &8-&3 Reloads the config."));
							return true;
						default:
							sender.sendMessage(PREFIX_ERROR + "Unknown subcommand: /woeshbackup " + args[1]);
							return true;
					}
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "now": {
				
				// "/woeshbackup now [--ignorelimit]".
				if(args.length == 1 || args.length == 2) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.backupnow")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					// Get optional "--ignorelimit" argument.
					boolean bypassDiskSpaceLimit;
					if(args.length == 2) {
						if(args[1].toLowerCase().equals("--ignorelimit")) {
							bypassDiskSpaceLimit = true;
						} else {
							sender.sendMessage(PREFIX_ERROR + "Unknown argument: " + args[1]);
							return true;
						}
					} else {
						bypassDiskSpaceLimit = false;
					}
					
					// Return if a backup is running already.
					if(this.api.backupInProgress()) {
						sender.sendMessage(PREFIX_ERROR + "You cannot start a new backup while a backup is running.");
						return true;
					}
					
					// Cancel the current task if it exists.
					boolean taskExists = this.api.backupIntervalTaskActive();
					if(taskExists) {
						this.api.stopBackupIntervalTask();
					}
					
					// Perform the backup.
					this.api.performBackup(bypassDiskSpaceLimit);
					
					// Re-enable the task if it existed.
					if(taskExists) {
						this.api.startBackupIntervalTask();
					}
					
					// Print feedback.
					sender.sendMessage(PREFIX_INFO + "Backup started. Auto backup is currently "
							+ (taskExists ? "enabled" : "disabled") + ".");
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "status": {
				
				// "/woeshbackup status".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.status")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					// Send the status feedback.
					long lastBackupTime = this.api.getLastBackupTime();
					sender.sendMessage(new String[] {
							PREFIX_INFO + "Backup in progress: " + ChatColor.LIGHT_PURPLE
									+ (this.api.backupInProgress() ? "Yes" : "No") + ChatColor.GREEN + ".",
							PREFIX_INFO + "Last backup started: " + ChatColor.LIGHT_PURPLE + (lastBackupTime <= 0
									? "Never" : ((System.currentTimeMillis() - lastBackupTime) / 60000) + "m ago")
									+ ChatColor.GREEN + ".",
							PREFIX_INFO + "Auto backup: " + ChatColor.LIGHT_PURPLE
									+ (this.api.backupIntervalTaskActive()
											? (this.api.getBackupInterval() / 60) + "m interval" : "Disabled")
									+ ChatColor.GREEN + ".",
							PREFIX_INFO + "Backups loaded: " + Utils.glueIterable(this.api.getBackups(),
									(Backup b) -> ChatColor.LIGHT_PURPLE + b.getToBackupDir().getName()
											+ ChatColor.GREEN, ", ") + ".",
							PREFIX_INFO + "Debug enabled: " + ChatColor.LIGHT_PURPLE
									+ (this.api.debugEnabled() ? "Yes" : "No") + ChatColor.GREEN + "."
					});
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "on": {
				
				// "/woeshbackup on".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.toggleautobackup")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					// Check if there is a task running already.
					if(this.api.backupIntervalTaskActive()) {
						sender.sendMessage(PREFIX_ERROR + "Auto backup is already enabled.");
					} else {
						this.api.startBackupIntervalTask();
						sender.sendMessage(PREFIX_INFO + String.format(
								"Auto backup enabled. Backups will be made every %d minutes.",
								this.api.getBackupInterval() / 60));
					}
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "off": {
				
				// "/woeshbackup off".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.toggleautobackup")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					// Check if there is a task running already.
					if(!this.api.backupIntervalTaskActive()) {
						sender.sendMessage(PREFIX_ERROR + "Auto backup is already disabled.");
					} else {
						this.api.stopBackupIntervalTask();
						sender.sendMessage(PREFIX_INFO + "Auto backup disabled.");
					}
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "diskinfo": {
				
				// "/woeshbackup diskinfo".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.diskinfo")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					// Get the root directory and list its properties.
					File file = this.api.getBackupDir();
					while(file.getParentFile() != null) {
						file = file.getParentFile();
					}
					if(file.exists()) {
						sender.sendMessage(new String[] {
								PREFIX_INFO + "Listing disk info for: " + file.getAbsolutePath(),
								PREFIX_INFO + "  Free disk space: " + (file.getFreeSpace() / 1000000) + "MB",
								PREFIX_INFO + "  Total disk space: " + (file.getTotalSpace() / 1000000) + "MB",
								PREFIX_INFO + "  Free usable disk space: " + (file.getUsableSpace() / 1000000) + "MB"});
					} else {
						sender.sendMessage(
								PREFIX_ERROR + "Unable to get disk info. Root directory could not be resolved.");
					}
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "generatesnapshot": {
				
				// Get and strip optional "--ignorelimit" argument.
				boolean bypassDiskSpaceLimit = false;
				for(int i = 0; i < args.length; i++) {
					if(args[i].toLowerCase().equals("--ignorelimit")) {
						bypassDiskSpaceLimit = true;
						String[] newArgs = new String[args.length - 1];
						int ind = 0;
						for(int j = 0; j < args.length; j++) {
							if(j != i) {
								newArgs[ind] = args[j];
							}
							ind++;
						}
						args = newArgs;
						break;
					}
				}
				
				// Check argument size.
				if(args.length < 3) {
					sender.sendMessage(new String[] {
							PREFIX_ERROR + "Not enough arguments.",
							PREFIX_RAW + ChatColor.GOLD
									+ " Syntax: /woeshbackup generatesnapshot <backupName> <date>."
									+ " date is in format: yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss"});
					return true;
				} else if(args.length > 3) {
					sender.sendMessage(new String[] {
							TOO_MANY_ARGS_MSG,
							PREFIX_RAW + ChatColor.GOLD
									+ " Syntax: /woeshbackup generatesnapshot <backupName> <date>."
									+ " date is in format: yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss"});
					return true;
				}
				
				// Check for permission.
				if(!sender.hasPermission("woeshbackup.generatesnapshot")) {
					sender.sendMessage(NO_PERMS_MSG);
					return true;
				}
				
				// Parse date argument.
				String dateStr = args[2];
				long date;
				try {
					date = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").parse(dateStr).getTime();
				} catch (ParseException e) {
					try {
						date = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr).getTime();
					} catch (ParseException e1) {
						sender.sendMessage(PREFIX_ERROR + "Syntax error: date has to be in format"
								+ " yyyy-MM-dd or yyyy-MM-dd-HH-mm-ss. Found: " + dateStr);
						return true;
					}
				}
				
				// Check if the given backup exists.
				String backupName = args[1];
				Backup backup = null;
				for(Backup b : this.api.getBackups()) {
					if(b.getToBackupDir().getName().equalsIgnoreCase(backupName)) {
						backup = b;
						break;
					}
				}
				if(backup == null) {
					sender.sendMessage(PREFIX_ERROR + "Backup could not be found: " + backupName);
					return true;
				}
				
				// Check if there is at least a user-defined amount of free disk space. Don't restore otherwise.
				if(!bypassDiskSpaceLimit) {
					long availableDiskSpace = (this.api.getSnapshotsDir().isDirectory()
							? this.api.getSnapshotsDir() : this.api.getBackupDir()).getUsableSpace();
					if(availableDiskSpace < this.api.getMinRequiredDiskSpace() * 1000000L) {
						sender.sendMessage(PREFIX_ERROR + "Cannot generate snapshots because less than "
								+ this.api.getMinRequiredDiskSpace() + "MB of free disk space was found("
								+ (availableDiskSpace / 1000000) + "MB).");
						return true;
					}
				}
				
				// Print feedback about starting.
				sender.sendMessage(PREFIX_INFO + "Generating snapshot for backup: "
						+ backup.getToBackupDir().getName() + ", date: " + dateStr);
				
				// Create a snapshot for the given date (merge backups and place the result in the snapshots directory).
				final Backup finalBackup = backup;
				final long finalDate = date;
				new Thread(() -> {
					
					// Create the snapshots directory if it does not yet exist.
					if(!WoeshBackupCommandExecutor.this.api.getSnapshotsDir().exists()) {
						WoeshBackupCommandExecutor.this.api.getSnapshotsDir().mkdirs();
					}
					
					// Generate a snapshot from the backup.
					BackupException ex = null;
					try {
						File restoreToDir = new File(WoeshBackupCommandExecutor.this.api.getSnapshotsDir(),
								finalBackup.getToBackupDir().getName());
						finalBackup.restore(finalDate + 1, (restoreFileDate) ->
								new BackupRestoreZipFileWriter(restoreToDir, restoreFileDate));
					} catch (BackupException e) {
						ex = e;
					} catch (InterruptedException e) {
						sender.sendMessage(PREFIX_ERROR + "Backup restore was interrupted during execution: "
								+ finalBackup.getToBackupDir().getName());
						return;
					}
					
					// Give feedback to the player.
					final BackupException finalEx = ex;
					if(WoeshBackupCommandExecutor.this.plugin.isEnabled()) {
						Bukkit.getScheduler().runTask(WoeshBackupCommandExecutor.this.plugin, () -> {
							if(finalEx == null) {
								sender.sendMessage(PREFIX_INFO + "Succesfully generated snapshot for backup: "
										+ finalBackup.getToBackupDir().getName());
							} else {
								if(this.api.debugEnabled()) {
									WoeshBackupCommandExecutor.this.logger.severe("An Exception occurred "
											+ "while generating a snapshot for backup: "
											+ finalBackup.getToBackupDir().getName() + ". Here's the stacktrace:\n"
											+ Utils.getStacktrace(finalEx));
								}
								if(finalEx.getCause() == null) {
									sender.sendMessage(PREFIX_ERROR + "Failed to generate snapshot: "
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
									sender.sendMessage(PREFIX_ERROR + message);
								}
							}
						});
					}
				}).start();
				return true;
			}
			case "removesnapshots": {
				
				// "/woeshbackup removesnapshots".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.removesnapshot")
							&& !sender.hasPermission("woeshbackup.removesnapshots")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					int amount = this.api.removeGeneratedSnapshots();
					sender.sendMessage(amount >= 0 ? PREFIX_INFO + "Successfully removed " + amount + " snapshots."
							: PREFIX_ERROR + "An error occured while removing one or more snapshots.");
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "toggledebug": {
				
				// "/woeshbackup toggledebug".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.toggledebug")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					this.api.setDebugEnabled(!this.api.debugEnabled());
					sender.sendMessage(PREFIX_INFO + "Debug "
							+ (this.api.debugEnabled() ? "enabled" : "disabled") + ".");
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			case "reload": {
				
				// "/woeshbackup reload".
				if(args.length == 1) {
					
					// Check for permission.
					if(!sender.hasPermission("woeshbackup.reload")) {
						sender.sendMessage(NO_PERMS_MSG);
						return true;
					}
					
					this.api.loadConfig();
					sender.sendMessage(PREFIX_INFO + "Config reloaded.");
				} else {
					sender.sendMessage(TOO_MANY_ARGS_MSG);
				}
				return true;
			}
			default: {
				sender.sendMessage(PREFIX_ERROR + "Unknown argument: " + args[0]);
				return true;
			}
		}
	}
	
	/**
	 * Colorizes the given string by replacing color char '&' by {@link ChatColor#COLOR_CHAR} for
	 * color idenfitiers 0-9a-fA-F.
	 * @param str - The string to colorize.
	 * @return The colorized string.
	 */
	private static String colorize(String str) {
		return str.replaceAll("(?<!\\&)\\&(?=[0-9a-fA-F])", ChatColor.COLOR_CHAR + "");
	}
}
