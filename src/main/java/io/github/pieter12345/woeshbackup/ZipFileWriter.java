package io.github.pieter12345.woeshbackup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This class contains methods for writing zip files.
 * @author P.J.S. Kools
 */
public class ZipFileWriter {
	
	private final File zipFile;
	private ZipOutputStream zipOutStream = null;
	private static final int BUFFER_SIZE = 2048; // The buffer size for copying files/streams.
	
	/**
	 * Creates a new {@link ZipFileWriter} from the given file.
	 * @param zipFile - The file to use as zip file. For example: new File("dir/dir2/myFile.zip").
	 */
	public ZipFileWriter(File zipFile) {
		Objects.requireNonNull(zipFile);
		this.zipFile = zipFile;
	}
	
	/**
	 * Opens the zip file for writing.
	 * @throws FileNotFoundException If the zip file exists but is a directory rather than a regular file,
	 * does not exist but cannot be created or cannot be opened for any other reason.
	 */
	public void open() throws FileNotFoundException {
		if(this.zipOutStream == null) {
			this.zipOutStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(this.zipFile)));
			this.zipOutStream.setMethod(ZipOutputStream.DEFLATED);
		}
	}
	
	/**
	 * Closes the zip file for writing. Does nothing if the file was not opened.
	 * @throws IOException If an I/O error has occurred.
	 */
	public void close() throws IOException {
		if(this.zipOutStream != null) {
			this.zipOutStream.close();
			this.zipOutStream = null;
		}
	}
	
	/**
	 * Adds an empty zip entry at the given relPath (directory or empty file).
	 * @param relPath - The relative path to the file or directory.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * Examples: "path/to/file.txt" or "path/to/dir/".
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalStateException If the ZipFile was not open for writing.
	 */
	public void add(String relPath) throws IOException, IllegalStateException {
		this.add(relPath, (InputStream) null);
	}
	
	/**
	 * Adds a zip entry at the given relPath containing the given file contents.
	 * @param relPath - The relative path to store the file contents at. This includes the file name.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * Examples: "path/to/file.txt" or "path/to/dir/".
	 * @param file - The file to add. If the relative path ends with a file separator, this argument is ignored.
	 * If this argument is null and used, an empty file entry will be added.
	 * Note that the file name is ignored as the relPath should contain it.
	 * @throws FileNotFoundException If the relPath represents a directory and the file does not exist,
	 * is a directory rather than a regular file or for some other reason cannot be opened for reading.
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalStateException If the ZipFile was not open for writing.
	 */
	public void add(String relPath, File file) throws FileNotFoundException, IOException, IllegalStateException {
		if(file == null) {
			this.add(relPath, (InputStream) null);
		}
		FileInputStream inStream = new FileInputStream(file);
		this.add(relPath, inStream);
		inStream.close();
	}
	
	/**
	 * Adds a zip entry at the given relPath containing the given file bytes.
	 * @param relPath - The relative path to store the file contents at. This includes the file name.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * Examples: "path/to/file.txt" or "path/to/dir/".
	 * @param fileBytes - The bytes to add. If the relative path ends with a file separator, this argument is ignored.
	 * If this argument is null and used, an empty file entry will be added.
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalStateException If the ZipFile was not open for writing.
	 */
	public void add(String relPath, byte[] fileBytes) throws IOException, IllegalStateException {
		Objects.requireNonNull(relPath);
		
		// Check if this ZipFile is open for writing.
		if(this.zipOutStream == null) {
			throw new IllegalStateException("ZipFile was not opened or already closed.");
		}
		
		// Put the file or directory as next entry in the zip file.
		this.zipOutStream.putNextEntry(new ZipEntry(relPath));
		
		// Copy the file contents if the file was not a directory and not an empty file.
		if(fileBytes != null && !relPath.endsWith(File.separator)) {
			this.zipOutStream.write(fileBytes);
		}
		
		// Close the zip entry.
		this.zipOutStream.closeEntry();
	}
	
	/**
	 * Adds a zip entry at the given relPath containing the given input stream contents.
	 * The input stream will be fully read, but not closed.
	 * @param relPath - The relative path to store the file contents at. This includes the file name.
	 * Paths to directories should end with a file separator and paths to normal files should not.
	 * Examples: "path/to/file.txt" or "path/to/dir/".
	 * @param inStream - The stream to add the contents of.
	 * If the relative path ends with a file separator, this argument is ignored.
	 * If this argument is null and used, an empty file entry will be added.
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalStateException If the ZipFile was not open for writing.
	 */
	public void add(String relPath, InputStream inStream) throws IOException, IllegalStateException {
		Objects.requireNonNull(relPath);
		
		// Check if this ZipFile is open for writing.
		if(this.zipOutStream == null) {
			throw new IllegalStateException("ZipFile was not opened or already closed.");
		}
		
		// Handle null files and directories.
		if(inStream == null || relPath.endsWith(File.separator)) {
			this.zipOutStream.putNextEntry(new ZipEntry(relPath));
			this.zipOutStream.closeEntry();
			return;
		}
		
		// Create the entry in the zip file.
		this.zipOutStream.putNextEntry(new ZipEntry(relPath));
		
		// Write the stream contents to the zip file.
		byte[] buffer = new byte[BUFFER_SIZE];
		int count;
		while(true) {
			count = inStream.read(buffer, 0, BUFFER_SIZE);
			if(count == -1) {
				break;
			}
			this.zipOutStream.write(buffer, 0, count);
		}
		
		// Close the zip entry.
		this.zipOutStream.closeEntry();
	}
	
	/**
	 * getFile method.
	 * @return The zip file.
	 */
	public File getFile() {
		return this.zipFile;
	}
}
