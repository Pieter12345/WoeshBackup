package io.github.pieter12345.woeshbackup;

import java.io.IOException;
import java.io.InputStream;

/**
 * Used to write backup restores.
 * @author P.J.S. Kools
 */
public interface BackupRestoreWriter {
	
	/**
	 * Opens the target storage for writing.
	 * @throws IOException If the target storage cannot be opened for any reason.
	 */
	public void open() throws IOException;

	/**
	 * Closes the target storage for writing. Does nothing if the target storage was not opened for writing.
	 * If this method is not called or throws an exception,
	 * then the data written using this writer is likely to be corrupted.
	 * @throws IOException If the target storage cannot be closed for any reason.
	 */
	public void close() throws IOException;
	
	/**
	 * Adds a file entry at the given relPath containing the given input stream contents.
	 * The input stream will be fully read, but not closed.
	 * @param relPath - The relative path to store the file contents at. This includes the file name.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * Examples: "path/to/file.txt" or "path/to/dir/".
	 * @param inStream - The stream to add the contents of.
	 * If the relative path ends with a file separator, this argument is ignored.
	 * If this argument is null and used, an empty file entry will be added.
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalStateException If the {@link BackupRestoreWriter} was not open for writing.
	 */
	public void add(String relPath, InputStream inStream) throws IOException, IllegalStateException;
	
	/**
	 * Deletes the underlying storage containing the restored data.
	 * @throws IOException - If an I/O error has occurred.
	 */
	public void delete() throws IOException;
}
