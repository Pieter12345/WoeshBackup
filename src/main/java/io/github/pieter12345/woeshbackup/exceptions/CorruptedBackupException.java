package io.github.pieter12345.woeshbackup.exceptions;

import io.github.pieter12345.woeshbackup.BackupPart;

/**
 * An Exception that should be thrown when a backup part is corrupted.
 * @author P.J.S. Kools
 */
@SuppressWarnings("serial")
public class CorruptedBackupException extends Exception  {
	
	private final BackupPart backup;
	
	public CorruptedBackupException(BackupPart backup) {
		super();
		this.backup = backup;
	}
	
	public CorruptedBackupException(BackupPart backup, String message) {
		super(message);
		this.backup = backup;
	}
	
	public CorruptedBackupException(BackupPart backup, String message, Throwable throwable) {
		super(message, throwable);
		this.backup = backup;
	}
	
	public CorruptedBackupException(BackupPart backup, Throwable throwable) {
		super(throwable);
		this.backup = backup;
	}
	
	public BackupPart getBackup() {
		return this.backup;
	}
}
