package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import io.github.pieter12345.woeshbackup.exceptions.CorruptedBackupException;

/**
 * Represents an original or update backup.
 * @author P.J.S. Kools
 */
public interface BackupPart {
	
	/**
	 * Add a file addition to the backup.
	 * @param relPath - The relative path to the file that was added.
	 * The full path would be toBackupDir + File.separator + relPath.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * @param file - The file that was added. Ignored when the relPath points to a directory.
	 * @throws IOException When an I/O error occurs while reading the file or writing to the backup part.
	 */
	public void addAddition(String relPath, File file) throws IOException;
	
	/**
	 * Add a file modification to the backup.
	 * @param relPath - The relative path to the file that was modified.
	 * The full path would be toBackupDir + File.separator + relPath.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * @param file - The file that was modified. Ignored when the relPath points to a directory.
	 * @throws IOException When an I/O error occurs while reading the file or writing to the backup part.
	 */
	public void addModification(String relPath, File file) throws IOException;
	
	/**
	 * Add a file removal to the backup.
	 * @param relPath - The relative path to the file that was removed.
	 * The full path would be toBackupDir + File.separator + relPath.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * @throws IOException When an I/O error occurs while writing to the backup part.
	 */
	public void addRemoval(String relPath) throws IOException;
	
	/**
	 * Loops over the given backup part and adds its changes to this backup part without overwriting changes in this
	 * backup, effectively merging the backup treatening this backup as the most recent (dominant) backup.
	 * @param backup - The backup part who's changes to add to this backup part.
	 * @throws IOException When an I/O error occurs while reading from or writing to the backup parts.
	 * @throws CorruptedBackupException If the given backup part is corrupted.
	 */
	public void merge(BackupPart backup) throws IOException, CorruptedBackupException;
	
	/**
	 * Closes the backup. This should always be called when finishing a new backup.
	 * If the {@link BackupPart} implementation has to store the made changes or close a zip file or database
	 * connection, then that should be implemented in this method.
	 * @throws IOException When an I/O error occurs.
	 */
	public void close() throws IOException;
	
	/**
	 * Checks whether the backup contains the given file by path, or by path and content if compareContent is true.
	 * This only returns true if the file is an addition or a modification in this backup.
	 * For a removal, this always returns false.
	 * @param relPath - The relative path to the file to check for.
	 * The full path would be toBackupDir + File.separator + relPath.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * @param file - The file to compare the contents of. This is ignored if compareContent is false
	 * or if relPath ends with a file separator.
	 * @param compareContent - If false, only the file path is compared. Otherwise, the file bytes are compared as well.
	 * @return True if the file path is equal and the contents are equal (if compareContent is given).
	 * @throws IOException When an I/O error occurs while reading the file (for comparing content only).
	 */
	public boolean contains(String relPath, File file, boolean compareContent) throws IOException;
	
	/**
	 * Reads the changes from this backup from the storage. Changes can be obtained using {@link #getChanges()}.
	 * @throws IOException When an I/O error occurs while reading the changes.
	 * @throws CorruptedBackupException When the changes are corrupted.
	 */
	public void readChanges() throws IOException, CorruptedBackupException;
	
	/**
	 * Gets the changes from this backup in the form of file paths relative to the backup directory and the change type.
	 * Directory paths are suffixed with a file separator ('/' or '\').
	 * Changes can be initialized from storage using {@link #readChanges()} or by adding changes directly.
	 * Example return: {"my/directory/": ADDITION, "someFile.db": REMOVAL, "some/other/file.txt": ADDITION}.
	 * @return The changes.
	 * @throws IOException When an I/O error occurs while reading the paths.
	 * @throws IllegalStateException When the changes are not yet initialized.
	 */
	public Map<String, ChangeType> getChanges();
	
	/**
	 * Gets the name of this backup.
	 * This can be a file name, a date string or something else that identifies this backup.
	 * @return The name of this backup.
	 */
	public String getName();
	
	/**
	 * Gets the creation time of this backup.
	 * @return The creation time of this backup.
	 */
	public long getCreationTime();
	
	/**
	 * Deletes this backup part.
	 * @throws IOException When an I/O error occurs during deletion.
	 */
	public void delete() throws IOException;
	
	/**
	 * Reads all file entries in this backup part and passes them to the given handler.
	 * These should be the added and/or modified files returned by {@link #getChanges()},
	 * but this might not be the case for corrupted backups.
	 * Note that the given handler is not allowed to call this method.
	 * @param handler - The handler for the zip file entry.
	 * @throws InvocationTargetException If invoking the handler results in a throwable being thrown.
	 * @throws IOException If an I/O error has occurred.
	 */
	public void readAll(FileEntryHandler handler) throws InvocationTargetException, IOException;
	
	/**
	 * The change type of a backup change entry.
	 * @author P.J.S. Kools
	 */
	public enum ChangeType {
		ADDITION,
		REMOVAL;
	}
}
