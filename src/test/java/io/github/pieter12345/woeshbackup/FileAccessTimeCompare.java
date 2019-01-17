package io.github.pieter12345.woeshbackup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Used to test and compare disk access time for different approaches and file formats.
 * The goal of this is to decrease the disk access time (which can be over an hour for large backups) in WoeshBackup.
 * @author P.J.S. Kools
 */
public class FileAccessTimeCompare {

	public static final File ZIP_FILE = new File("backups/2018-08-09 12-01-00.zip");
	public static final File ZIP_FILE_CONVERTED = new File("backups/2018-08-09 12-01-00-converted.woeshbackup");
	
	public static void main(String[] args) {
		if(!ZIP_FILE.exists()) {
			System.out.println("The zip file to run the tests on does not exist: " + ZIP_FILE.getAbsolutePath());
			System.exit(0);
		}
		ZipFileReader zipFile = new ZipFileReader(ZIP_FILE);
		try {
//			convertBackup(ZIP_FILE);
//			byte[] changesBytes = zipFile.read("changes.txt");
//			assert(changesBytes != null) : "changes.txt file not found.";
//			Map<String, byte[]> zipMap = zipFile.read();
			
			// Time reading "changes.txt" from the zip file.
			int readAmount = 1;
			System.out.println("Reading changes from zip file.");
			long time = System.currentTimeMillis();
			for(int i = 0; i < readAmount; i++) {
				byte[] changesBytes = zipFile.read("changes.txt");
				assert(changesBytes != null) : "changes.txt file not found.";
			}
			long timeElapsed = System.currentTimeMillis() - time;
			System.out.println("Time elapsed: " + timeElapsed + "ms (average " + (timeElapsed / 10) + "ms).");
			
			// Time reading the changes from the 'new' format.
			System.out.println("Reading changes from converted file.");
			time = System.currentTimeMillis();
			for(int i = 0; i < readAmount; i++) {
				BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(ZIP_FILE_CONVERTED));
				int ch1 = inStream.read();
				int ch2 = inStream.read();
				int ch3 = inStream.read();
				int ch4 = inStream.read();
				if((ch1 | ch2 | ch3 | ch4) < 0) {
					inStream.close();
					throw new EOFException("End of file reached where the length of the changes file was expected.");
				}
				int changesLength = (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0);
				byte[] changesBytes = new byte[changesLength];
				if(inStream.read(changesBytes) != changesLength) {
					inStream.close();
					throw new EOFException("End of file reached where the changes file was expected.");
				}
				inStream.close();
			}
			timeElapsed = System.currentTimeMillis() - time;
			System.out.println("Time elapsed: " + timeElapsed + "ms (average " + (timeElapsed / 10) + "ms).");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Convert a 'normal' backup (zip with changes.txt) to a backup where the changes.txt bytes are in front of the
	 * zipped content.
	 * TODO - Also consider a file format where every individual file is zipped (but check the size impact).
	 * @param backupZip
	 */
	private static void convertBackup(File backupZip) throws IOException {
		
		// Read the zip file entries.
		ZipFileReader zipFile = new ZipFileReader(backupZip);
		Map<String, byte[]> entryMap = zipFile.readAll();
		
		// Create the new zip file.
		File convertedFile = new File(backupZip.getParentFile(),
				backupZip.getName().split(".zip", 2)[0] + "-converted.woeshbackup");
		
		// Write changes.txt to the new file, preceeded with its length in bytes (int).
		FileOutputStream fileOutStream = new FileOutputStream(convertedFile);
		BufferedOutputStream bufferedFileOutStream = new BufferedOutputStream(fileOutStream);
		byte[] changesFileBytes = entryMap.get("changes.txt");
		int length = changesFileBytes.length;
		bufferedFileOutStream.write((length >>> 24) & 0xFF);
		bufferedFileOutStream.write((length >>> 16) & 0xFF);
		bufferedFileOutStream.write((length >>>  8) & 0xFF);
		bufferedFileOutStream.write((length >>>  0) & 0xFF);
		bufferedFileOutStream.write(changesFileBytes);
		
		// Write all zip entries to the new file.
		ZipOutputStream zipOutStream = new ZipOutputStream(bufferedFileOutStream);
		zipOutStream.setMethod(ZipOutputStream.DEFLATED);
		
		for(Entry<String, byte[]> entry : entryMap.entrySet()) {
			String filePath = entry.getKey();
			byte[] bytes = entry.getValue();
			
			// Put the file as next entry in the zip file.
			zipOutStream.putNextEntry(new ZipEntry(filePath));
			
			// Copy the file contents if the file was not a directory.
			if(!filePath.endsWith("/")) {
				zipOutStream.write(bytes);
			}
			
			// Close the zip entry.
			zipOutStream.closeEntry();
		}
		
		zipOutStream.close();
	}
}
