package io.github.pieter12345.woeshbackup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class contains methods for reading zip files.
 * @author P.J.S. Kools
 */
public class ZipFileReader {
	
	private final File zipFile;
	
	/**
	 * Creates a new {@link ZipFileReader} from the given file.
	 * @param zipFile - The file to use as zip file. For example: new File("dir/dir2/myFile.zip").
	 */
	public ZipFileReader(File zipFile) {
		Objects.requireNonNull(zipFile);
		this.zipFile = zipFile;
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
		pathInZip = pathInZip.replace(File.separatorChar, '/');
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
	 * Reads all entries in the zip file and returns a Map containing the paths in the zip file
	 * with their corresponding file bytes or null for directories.
	 * @return A Map containing the paths in the zip file with their corresponding file bytes or null for directories.
	 * @throws IOException If an I/O error has occurred.
	 */
	public Map<String, byte[]> readAll() throws IOException {
		HashMap<String, byte[]> entryMap = new HashMap<String, byte[]>();
		ZipInputStream inStream = new ZipInputStream(new FileInputStream(this.zipFile));
		ZipEntry entry;
		while((entry = inStream.getNextEntry()) != null) {
			String relPath = entry.getName().replace('/', File.separatorChar);
			byte[] bytes;
			if(entry.isDirectory()) {
				bytes = null;
			} else {
				long size = entry.getSize();
				if(size != -1) {
					if(inStream.available() != 0) {
						bytes = new byte[(int) size];
						int amount = inStream.read(bytes);
						if(amount != bytes.length) {
							inStream.close();
							throw new IOException("Could not read all bytes of a zip entry. Read: "
									+ amount + ", expected: " + bytes.length);
						}
					} else {
						bytes = new byte[0];
					}
				} else {
					ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int amount;
					// inStream.available() is not reliable and returns wrong values. This is reliable.
					while((amount = inStream.read(buffer, 0, buffer.length)) != -1) {
						byteArrayOutStream.write(buffer, 0, amount);
					}
					bytes = byteArrayOutStream.toByteArray();
				}
			}
			entryMap.put(relPath, bytes);
			inStream.closeEntry();
		}
		inStream.close();
		return entryMap;
	}
	
	/**
	 * Reads all zip file entries and passes them to the given handler.
	 * Note that the handler is not allowed to call any reading methods from this class.
	 * @param handler - The handler for the zip file entry.
	 * @throws InvocationTargetException If invoking the handler results in a throwable being thrown.
	 * @throws IOException If an I/O error has occurred.
	 */
	public void readAll(FileEntryHandler handler) throws InvocationTargetException, IOException {
		ZipInputStream inStream = new ZipInputStream(new FileInputStream(ZipFileReader.this.zipFile));
		ZipEntry entry;
		while((entry = inStream.getNextEntry()) != null) {
			try {
				handler.handle(new FileEntry(entry.getName().replace('/', File.separatorChar), inStream));
			} catch (Throwable t) {
				throw new InvocationTargetException(t);
			}
			inStream.closeEntry();
		}
		inStream.close();
	}
	
	/**
	 * getFile method.
	 * @return The zip file.
	 */
	public File getFile() {
		return this.zipFile;
	}
}
