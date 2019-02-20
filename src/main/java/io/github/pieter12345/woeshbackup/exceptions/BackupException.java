package io.github.pieter12345.woeshbackup.exceptions;

/**
 * An Exception that should be thrown as outcome of a failed operation on a backup.
 * The message should contain the feedback for the user that has given the command to perform this failed operation.
 * @author P.J.S. Kools
 */
@SuppressWarnings("serial")
public class BackupException extends Exception  {
	
	public BackupException() {
		super();
	}
	
	public BackupException(String message) {
		super(message);
	}
	
	public BackupException(String message, Throwable throwable) {
		super(message, throwable);
	}
	
	public BackupException(Throwable throwable) {
		super(throwable);
	}
}
