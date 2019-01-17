package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.InputStream;

/**
 * Represents a file entry, represented by the relative path to the file and the input stream of that file.
 * @author P.J.S. Kools
 */
public class FileEntry {
	private final String relPath;
	private final InputStream inStream;
	
	/**
	 * Creates a new {@link FileEntry} for the given relative path and input stream.
	 * @param relPath - The relative path to the file without leading file separator.
	 * If relPath ends with a file separator, it is handled as a directory.
	 * @param inStream - The file input stream. This argument is ignored if the path denotes a directory.
	 */
	public FileEntry(String relPath, InputStream inStream) {
		this.relPath = relPath;
		this.inStream = (this.relPath.endsWith(File.separator) ? null : inStream);
	}
	
	/**
	 * Gets the relative path to the file or directory.
	 * @return The relative path to the file or directory without leading file separator.
	 * If this {@link FileEntry} represents a directory, the path is suffixed with a file separator ('/' or '\').
	 */
	public String getRelativePath() {
		return this.relPath;
	}
	
	/**
	 * Gets the file input stream.
	 * @return The file input stream or null if the file is a directory.
	 */
	public InputStream getFileStream() {
		return this.inStream;
	}
	
	/**
	 * Gets whether this entry represents a directory.
	 * @return {@code true} if this entry represents a directory, {@code false} otherwise.
	 */
	public boolean isDirectory() {
		return this.inStream == null;
	}
}
