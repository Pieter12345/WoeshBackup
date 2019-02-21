package io.github.pieter12345.woeshbackup.bukkit;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import io.github.pieter12345.woeshbackup.Backup;
import io.github.pieter12345.woeshbackup.utils.Utils;

/**
 * The {@link TabCompleter} for the WoeshBackup Bukkit plugin.
 * @author P.J.S. Kools
 */
public class WoeshBackupTabCompleter implements TabCompleter {
	
	private final WoeshBackupPlugin plugin;
	private final Logger logger;
	
	private static final DateFormat RESTORE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	
	/**
	 * Creates a new {@link CommandExecutor} for WoeshBackup commands.
	 * @param plugin - The {@link WoeshBackupPlugin}.
	 * @param logger - The logger used for error logging.
	 */
	public WoeshBackupTabCompleter(WoeshBackupPlugin plugin, Logger logger) {
		this.plugin = plugin;
		this.logger = logger;
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
				for(Backup backup : this.plugin.getBackups()) {
					if(backup.getToBackupDir().getName().toLowerCase().startsWith(args[1].toLowerCase())) {
						ret.add(backup.getToBackupDir().getName());
					}
				}
				return ret;
			}
			if(args.length == 3) {
				List<String> ret = new ArrayList<String>();
				for(Backup backup : this.plugin.getBackups()) {
					if(backup.getToBackupDir().getName().equalsIgnoreCase(args[1])) {
						try {
							for(Long restoreDateThresh : backup.getRestoreDateThresholds()) {
								String restoreDate = RESTORE_DATE_FORMAT.format(new Date(restoreDateThresh));
								if(restoreDate.startsWith(args[2])) {
									ret.add(restoreDate);
								}
							}
						} catch (IOException e) {
							this.logger.warning("An exception occurred while tabcompleting"
									+ " backup part dates. Here's the stacktrace: " + Utils.getStacktrace(e));
						}
					}
				}
				return ret;
			}
		}
		
		// Don't use the default TABcompleter, completing names is useless here.
		return Collections.emptyList();
	}
}
