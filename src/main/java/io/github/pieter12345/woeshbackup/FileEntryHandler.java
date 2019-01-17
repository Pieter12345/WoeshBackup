package io.github.pieter12345.woeshbackup;

/**
 * A {@link FileEntry} handler.
 * @author P.J.S. Kools
 */
public interface FileEntryHandler {
	public void handle(FileEntry entry) throws Exception;
}
