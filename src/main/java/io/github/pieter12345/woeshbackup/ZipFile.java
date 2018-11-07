package io.github.pieter12345.woeshbackup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFile {
	
	// Variables & Constants.
	private final File zipFile;
	private ZipOutputStream zipOutStream = null;
	private static final int BUFFER_SIZE = 2048; // The buffer size for copying files.
	
	public static void main(String[] args) {
		ZipFile zipFile = new ZipFile(new File("zipFileTest.zip"));
		try {
			zipFile.createFile();
			zipFile.open();
//			File toZipFile = new File(new File("").getAbsolutePath() + "/testToBackup");

			File toZipFile = new File("testToBackup");
			debug("Adding file to zip: " + toZipFile.getAbsolutePath());
			zipFile.add(toZipFile, "");
			for(File file : toZipFile.listFiles()) { // Just one layer deep for testing.
				zipFile.add(file, toZipFile.getName());
			}
			zipFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Constructor.
	 * @param zipFile - The file to use as zip file. For example: new File("dir/dir2/myFile.zip").
	 */
	public ZipFile(File zipFile) {
		this.zipFile = zipFile;
	}
	
	/**
	 * createFile method.
	 * Creates the zip file of this object.
	 * @return True if the file was succesfully created, false if the file already exists.
	 * @throws IOException If an I/O error has occurred.
	 */
	public boolean createFile() throws IOException {
		return this.zipFile.createNewFile();
	}
	
	/**
	 * open method.
	 * Opens the zip file for writing.
	 * @throws FileNotFoundException If the zip file for this ZipFile does not exist.
	 */
	public void open() throws FileNotFoundException {
		if(!this.zipFile.exists() || !this.zipFile.isFile()) {
			throw new FileNotFoundException("File does not exist: " + this.zipFile.getAbsolutePath());
		}
		FileOutputStream fileOutStream = new FileOutputStream(this.zipFile);
		this.zipOutStream = new ZipOutputStream(new BufferedOutputStream(fileOutStream));
		this.zipOutStream.setMethod(ZipOutputStream.DEFLATED);
	}
	
	/**
	 * close method.
	 * Closes the zip file for writing. Does nothing if the file was not opened.
	 * @throws IOException If an I/O error has occurred.
	 */
	public void close() throws IOException {
		if(this.zipOutStream == null) {
			return;
		}
		this.zipOutStream.close();
		this.zipOutStream = null;
	}
	
	/**
	 * add method.
	 * Adds the given file to the zip file. The open() method has to be called before files can be added.
	 * @param file - The File to add to the zip file.
	 * @param parentPath - A string denoting the path of the file within the zip file. This path does not need a root and does not need to exist.
	 * Since it's a directory, it has to end with a "/". Example: "dir/dir2/" to put file at dir/dir2/file.
	 * @throws FileNotFoundException If the file does not exist.
	 * @throws IllegalStateException If file is null or if the ZipFile was not open for writing.
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalArgumentException If the parentPath does not end with a "/".
	 */
	public void add(File file, String parentPath)
			throws FileNotFoundException, IOException, IllegalStateException, IllegalArgumentException {
		
		// Check if the file to add exists.
		if(!file.exists()) {
			throw new FileNotFoundException("File does not exist: " + file.getAbsolutePath());
		}
		
		if(!parentPath.endsWith("/")) {
			throw new IllegalArgumentException("The parent path has to end with a \"/\". Received: " + parentPath);
		}
		
		// Check if this ZipFile was opened for writing.
		if(this.zipOutStream == null) {
			throw new IllegalStateException("Unable to add file to ZipFile: ZipFile was not opened (or already closed).");
		}
		
		// Put the file as next entry in the zip file.
		this.zipOutStream.putNextEntry(new ZipEntry(
				(parentPath.isEmpty() ? "" : parentPath) + file.getName() + (file.isDirectory() ? "/" : "")));
		
		// Copy the file contents if the file was not a directory.
		if(file.isFile()) {
			FileInputStream fInStream = new FileInputStream(file);
			BufferedInputStream inStream = new BufferedInputStream(fInStream, BUFFER_SIZE);
			byte[] buffer = new byte[BUFFER_SIZE];
			
			int count;
			
			// Attempt to read bytes. This might fail if the file is locked. Use a temp file if this is the case.
			File tempFile = null;
			try {
				count = inStream.read(buffer, 0, BUFFER_SIZE);
			} catch (IOException e) {
				debug("Failed to add file to zip file. Attempting to add it through a temp file: "
						+ file.getAbsolutePath());
				inStream.close();
				tempFile = new File(this.zipFile.getParentFile().getAbsolutePath() + "/~woeshbackup temp - " + file.getName());
				Files.copy(file.toPath(), tempFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
				inStream = new BufferedInputStream(new FileInputStream(tempFile), BUFFER_SIZE);
				try {
					count = inStream.read(buffer, 0, BUFFER_SIZE);
				} catch (Exception e2) {
					if(!tempFile.delete()) {
						// Just print debug feedback and throw the original exception.
						debug("Failed to remove temporary file: " + tempFile.getAbsolutePath());
					}
					throw e2;
				}
			}
			
			while(count != -1) {
				this.zipOutStream.write(buffer, 0, count);
				count = inStream.read(buffer, 0, BUFFER_SIZE);
			}
			inStream.close();
			
			// Remove the temp file if it was used.
			if(tempFile != null) {
				if(!tempFile.delete()) {
					throw new IOException("Failed to remove temporary file: " + tempFile.getAbsolutePath());
				}
			}
		}
		
		// Close the zip entry.
		this.zipOutStream.closeEntry();
	}
	
	/**
	 * add method.
	 * Adds the given file bytes to the zip file. The open() method has to be called before files can be added.
	 * @param fileName - The name of the file to add to the zip file. Names ending with a "/" are considered directories,
	 * in which case the fileBytes will be ignored.
	 * @param fileBytes - The file data in a byte array.
	 * @param parentPath - A string denoting the path of the file within the zip file. This path does not need a root and does not need to exist.
	 * Since it's a directory, it has to end with a "/". Example: "dir/dir2/" to put file at dir/dir2/file.
	 * @throws IllegalStateException If the ZipFile was not open for writing.
	 * @throws IOException If an I/O error occurs.
	 * @throws IllegalArgumentException If the parentPath does not end with a "/".
	 */
	public void add(String fileName, byte[] fileBytes, String parentPath)
			throws IOException, IllegalStateException, IllegalArgumentException {
		
		if(!parentPath.endsWith("/")) {
			throw new IllegalArgumentException("The parent path has to end with a \"/\". Received: " + parentPath);
		}
		
		// Check if this ZipFile was opened for writing.
		if(this.zipOutStream == null) {
			throw new IllegalStateException("Unable to add file to ZipFile: ZipFile was not opened (or already closed).");
		}
		
		// Put the file as next entry in the zip file.
		this.zipOutStream.putNextEntry(new ZipEntry(
				(parentPath == null || parentPath.isEmpty() ? "" : parentPath + "/") + fileName));
		
		// Copy the file contents if the file was not a directory.
		if(!fileName.endsWith("/")) {
			this.zipOutStream.write(fileBytes);
		}
		
		// Close the zip entry.
		this.zipOutStream.closeEntry();
	}
	
	/**
	 * add method.
	 * Adds the given file bytes to the zip file. The open() method has to be called before files can be added.
	 * @param filePath - The path of the file to add to the zip file. Names ending with a "/" are considered directories,
	 * in which case the fileBytes will be ignored.
	 * @param fileBytes - The file data in a byte array.
	 * @throws IllegalStateException - If the ZipFile was not open for writing.
	 * @throws IOException - If an I/O error occurs.
	 */
	public void add(String filePath, byte[] fileBytes) throws IOException, IllegalStateException {
		
		// Check if this ZipFile was opened for writing.
		if(this.zipOutStream == null) {
			throw new IllegalStateException("Unable to add file to ZipFile: ZipFile was not opened (or already closed).");
		}
		
		// Put the file as next entry in the zip file.
		this.zipOutStream.putNextEntry(new ZipEntry(filePath));
		
		// Copy the file contents if the file was not a directory.
		if(!filePath.endsWith("/")) {
			this.zipOutStream.write(fileBytes);
		}
		
		// Close the zip entry.
		this.zipOutStream.closeEntry();
	}
	
	/**
	 * read method.
	 * Reads all entries in the zip file and returns a Map containing the paths in the zip file
	 *  with their corresponding file bytes or null for directories.
	 * @return A Map containing the paths in the zip file with their corresponding file bytes or null for directories.
	 * @throws IOException If an I/O error occurs.
	 */
	public Map<String, byte[]> read() throws IOException {
		HashMap<String, byte[]> entryMap = new HashMap<>();
		ZipInputStream inStream = new ZipInputStream(new FileInputStream(this.zipFile));
		ZipEntry entry;
		while((entry = inStream.getNextEntry()) != null) {
			if(entry.isDirectory()) {
				entryMap.put(entry.getName(), null);
			} else {
				long size = entry.getSize();
				if(size != -1) {
					if(inStream.available() != 0) {
						byte[] entryBytes = new byte[(int) size];
						int amount = inStream.read(entryBytes);
						if(amount != entryBytes.length) {
							inStream.close();
							throw new IOException("Could not read all bytes of a zip entry. Read: "
									+ amount + ", expected: " + entryBytes.length);
						}
						entryMap.put(entry.getName(), entryBytes);
					}
				} else {
					ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int amount;
					// inStream.available() is not reliable and returns wrong values. This is reliable.
					while((amount = inStream.read(buffer, 0, buffer.length)) != -1) {
						byteArrayOutStream.write(buffer, 0, amount);
					}
					entryMap.put(entry.getName(), byteArrayOutStream.toByteArray());
				}
			}
			inStream.closeEntry();
		}
		inStream.close();
		return entryMap;
	}
	
	/**
	 * read method.
	 * Reads the entry at the given path.
	 * @param pathInZip - The file path in the zip file.
	 * @return The bytes of the file. Returns null if the file was not found or if the file was a directory.
	 * @throws IOException If an I/O error occurs.
	 */
	public byte[] read(String pathInZip) throws IOException {
		ZipInputStream inStream = new ZipInputStream(new FileInputStream(this.zipFile));
		ZipEntry entry;
		while((entry = inStream.getNextEntry()) != null) {
			if(entry.getName().equals(pathInZip)) {
				long size = entry.getSize();
				if(size != -1) {
					if(inStream.available() != 0) {
						byte[] entryBytes = new byte[(int) size];
						int amount = inStream.read(entryBytes);
						if(amount != entryBytes.length) {
							inStream.close();
							throw new IOException("Could not read all bytes of a zip entry. Read: "
									+ amount + ", expected: " + entryBytes.length);
						}
						inStream.closeEntry();
						inStream.close();
						return entryBytes;
					}
				} else {
					ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int amount;
					// inStream.available() is not reliable and returns wrong values. This is reliable.
					while((amount = inStream.read(buffer, 0, buffer.length)) != -1) {
						byteArrayOutStream.write(buffer, 0, amount);
					}
					inStream.closeEntry();
					inStream.close();
					return byteArrayOutStream.toByteArray();
				}
			}
			inStream.closeEntry();
		}
		inStream.close();
		return null;
	}
	
	/**
	 * getFile method.
	 * @return The zip file.
	 */
	public File getFile() {
		return this.zipFile;
	}
	
	
	private static void debug(String message) {
		System.out.println("[DEBUG] [" + ZipFile.class.getSimpleName() + "] " + message);
	}
}
