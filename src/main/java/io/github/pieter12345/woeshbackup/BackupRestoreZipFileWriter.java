package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Used to write backup restores to a zip file.
 * @author P.J.S. Kools
 */
public class BackupRestoreZipFileWriter implements BackupRestoreWriter {
	
	private final ZipFileWriter writer;
	
	private static final DateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
	
	/**
	 * Created a new {@link BackupRestoreZipFileWriter} that will create a writer that writes to
	 * restoreToDir/restoreFileDate.zip where restoreFileDate is formatted as yyyy-MM-dd HH-mm-ss.
	 * @param restoreToDir - The directory to put the restore zip file in.
	 * @param restoreFileDate - The timestamp used to generate the file name.
	 */
	public BackupRestoreZipFileWriter(File restoreToDir, long restoreFileDate) {
		String restoreDate = BACKUP_DATE_FORMAT.format(new Date(restoreFileDate));
		this.writer = new ZipFileWriter(new File(restoreToDir, restoreDate + ".zip"));
	}
	
	@Override
	public void open() throws IOException {
		if(!this.writer.getFile().getParentFile().exists()) {
			this.writer.getFile().getParentFile().mkdir();
		}
		this.writer.open();
	}
	
	@Override
	public void close() throws IOException {
		this.writer.close();
	}
	
	@Override
	public void add(String relPath, InputStream inStream) throws IOException, IllegalStateException {
		this.writer.add(relPath, inStream);
	}
	
	@Override
	public void delete() throws IOException {
		if(!this.writer.getFile().delete()) {
			throw new IOException("Failed to delete file at: " + this.writer.getFile().getAbsolutePath());
		}
	}
}
