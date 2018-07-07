package io.github.pieter12345.woeshbackup;

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
