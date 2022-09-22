package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.github.pieter12345.woeshbackup.exceptions.CorruptedBackupException;

/**
 * Represents a zipped backup part.
 * @author P.J.S. Kools
 */
public class ZippedBackupPart implements BackupPart {
	
	private final long creationTime;
	private final String name;
	private final File parentDir;
	private final ZipFileReader zipFileReader;
	private final ZipFileWriter zipFileWriter;
	private final File metaFile;
	
	private Map<String, ChangeProperties> changesMap = null;
	
	/**
	 * Creates a new ZippedBackupPart.
	 * The given name will be used to create a name.zip and a name.meta file in the given parent directory.
	 * @param parentDir - The directory to put the files for this backup part in.
	 * @param name - The name of this backup.
	 * @param creationTime - The creation time of this backup.
	 */
	public ZippedBackupPart(File parentDir, String name, long creationTime) {
		this.creationTime = creationTime;
		this.name = name;
		this.parentDir = parentDir;
		this.zipFileReader = new ZipFileReader(new File(parentDir, name + ".zip"));
		this.zipFileWriter = new ZipFileWriter(new File(parentDir, name + ".zip"));
		this.metaFile = new File(parentDir, name + ".meta");
	}
	
	@Override
	public void addAddition(String relPath, File file) throws IOException {
		if(relPath.endsWith(File.separator)) {
			// Add directory.
			this.addAddition(relPath, (InputStream) null);
		} else {
			// Add file.
			FileInputStream inStream = new FileInputStream(file);
			this.addAddition(relPath, inStream);
			inStream.close();
		}
	}
	
	private void addAddition(String relPath, InputStream inStream) throws IOException {
		if(this.changesMap == null) {
			this.changesMap = new HashMap<String, ChangeProperties>();
			if(!this.parentDir.exists()) {
				this.parentDir.mkdirs();
			}
			if(this.zipFileWriter.getFile().exists()) {
				throw new IOException("Target file already exists: " + this.zipFileWriter.getFile().getAbsolutePath());
			}
			this.zipFileWriter.open();
		}
		
		// Throw an exception if the relPath was already added.
		if(this.changesMap.containsKey(relPath)) {
			throw new IllegalArgumentException("Relative path was already added: " + relPath);
		}
		
		// Add the directory or file to the zip, getting the MD5 hash in the process.
		String hash;
		if(relPath.endsWith(File.separator)) {
			this.zipFileWriter.add(relPath);
			hash = null;
		} else {
			MessageDigest messageDigest;
			try {
				messageDigest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw new Error(e); // Never happens, and if it does, then there is no fallback anyways.
			}
			this.zipFileWriter.add(relPath, new DigestInputStream(inStream, messageDigest));
			hash = Base64.getEncoder().encodeToString(messageDigest.digest());
		}
		
		// Store the change.
		this.changesMap.put(relPath, new ChangeProperties(relPath, ChangeType.ADDITION, hash));
	}
	
	@Override
	public void addModification(String relPath, File file) throws IOException {
		/* TODO - Store modifications differently? If this is done, the merge method should account for this.
		 * Merging could result in a 'first' backup with modifications instead of additions.
		 */
		this.addAddition(relPath, file);
	}
	
	@Override
	public void addRemoval(String relPath) throws IOException {
		if(this.changesMap == null) {
			this.changesMap = new HashMap<String, ChangeProperties>();
			if(!this.parentDir.exists()) {
				this.parentDir.mkdirs();
			}
			if(this.zipFileWriter.getFile().exists()) {
				throw new IOException("Target file already exists: " + this.zipFileWriter.getFile().getAbsolutePath());
			}
			this.zipFileWriter.open();
		}
		
		// Throw an exception if the relPath was already added.
		if(this.changesMap.containsKey(relPath)) {
			throw new IllegalArgumentException("Relative path was already added: " + relPath);
		}
		
		// Store the change.
		this.changesMap.put(relPath, new ChangeProperties(relPath, ChangeType.REMOVAL, null));
	}
	
	@Override
	public void merge(BackupPart backup) throws IOException, CorruptedBackupException {
		
		// Get changes map.
		final Map<String, ChangeType> changesMap = backup.getChanges();
		
		// Handle additions based on backup part files.
		try {
			backup.readAll((fileEntry) -> {
				String relPath = fileEntry.getRelativePath();
				
				// Remove change from changes map to mark it as handled and validate change type.
				ChangeType changeType = changesMap.remove(relPath);
				if(changeType == null) {
					throw new CorruptedBackupException(this,
							"Backup part contains file that does not occur in its changes: " + relPath);
				} else if(changeType == ChangeType.REMOVAL) {
					throw new CorruptedBackupException(this,
							"Backup part contains file that occurs as a removal in its changes: " + relPath);
				} else if(changeType != ChangeType.ADDITION) {
					throw new Error("Unsupported change type: " + changeType);
				}
				
				// Skip entries that are already in this backup.
				if(this.changesMap != null && this.changesMap.containsKey(relPath)) {
					return;
				}
				
				// Add addition entry to this backup part.
				ZippedBackupPart.this.addAddition(relPath, fileEntry.getFileStream());
			});
		} catch (InvocationTargetException e) {
			if(e.getTargetException() instanceof CorruptedBackupException) {
				throw (CorruptedBackupException) e.getTargetException();
			} else if(e.getTargetException() instanceof IOException) {
				throw (IOException) e.getTargetException();
			} else if(e.getTargetException() instanceof RuntimeException) {
				throw (RuntimeException) e.getTargetException();
			} else {
				// Should be impossible.
				throw new RuntimeException(e);
			}
		}
		
		// Handle removals based on backup part changes.
		for(Entry<String, ChangeType> changeEntry : changesMap.entrySet()) {
			ChangeType changeType = changeEntry.getValue();
			String relPath = changeEntry.getKey();
			switch(changeType) {
				case ADDITION: {
					throw new CorruptedBackupException(this,
							"Backup part is missing a file that occurs in its changes: " + relPath);
				}
				case REMOVAL: {
					
					// Skip entries that are already in this backup.
					if(this.changesMap != null && this.changesMap.containsKey(relPath)) {
						continue;
					}
					
					// Add removal entry to this backup part.
					this.addRemoval(relPath);
					break;
				}
				default: {
					throw new Error("Unsupported change type: " + changeType);
				}
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		
		// Close the zip file.
		this.zipFileWriter.close();
		
		// Write the changes file.
		if(this.changesMap != null) {
			StringBuilder changesStr = new StringBuilder();
			for(ChangeProperties change : this.changesMap.values()) {
				String relPath = change.relPath.replace(File.separatorChar, '/');
				switch(change.changeType) {
					case ADDITION:
						changesStr.append('+').append(relPath).append('\n');
						boolean isDirectory = relPath.endsWith("/");
						if(!isDirectory) {
							changesStr.append('\t').append(change.hash).append('\n');
						}
						break;
					case REMOVAL:
						changesStr.append('-').append(relPath).append('\n');
						break;
					default:
						throw new Error("Unimplemented change type found: " + change.changeType);
				}
			}
			changesStr.append('#').append(this.name).append('\n');
			FileOutputStream fos = new FileOutputStream(this.metaFile);
			fos.write(changesStr.toString().getBytes(StandardCharsets.UTF_8));
			fos.close();
		}
	}
	
	@Override
	public boolean contains(String relPath, File file, boolean compareContent) throws IOException {
		
		// Get the change for the given relPath.
		ChangeProperties change = this.changesMap.get(relPath);
		if(change == null || change.changeType == ChangeType.REMOVAL) {
			return false; // No change or a removal for relPath found.
		}
		if(!compareContent || relPath.endsWith(File.separator)) {
			return true; // A non-removal change was found and content does not matter or it's a directory.
		}
		if(file.isDirectory()) {
			throw new IllegalArgumentException(
					"Relative path does not denote a directory, but a directory is given. Relative path: "
					+ relPath + ", directory path: " + file.getAbsolutePath());
		}
		
		// Get the file hash.
		MessageDigest messageDigest;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new Error(e); // Never happens, and if it does, then there is no fallback anyways.
		}
		FileInputStream inStream = new FileInputStream(file);
		byte[] buffer = new byte[2048];
		int count;
		while((count = inStream.read(buffer)) > 0) {
			messageDigest.update(buffer, 0, count);
		}
		inStream.close();
		String fileHash = Base64.getEncoder().encodeToString(messageDigest.digest());
		
		// Compare the given file to the file in this backup part using their MD5 hashes.
		return fileHash.equals(change.hash);
	}
	
	@Override
	public void readChanges() throws IOException, CorruptedBackupException {
		
		/* Meta file format:
		 * +some/path/to/file
		 * \t<fileHash (only for file additions)>
		 * +some/path/to/dir/
		 * -some/other/file/path
		 * -some/other/dir/path/
		 * #<name>
		 * 
		 */
		
		// Return if the changes were already read.
		if(this.changesMap != null) {
			return;
		}
		
		// Check if the meta file exists.
		if(!this.metaFile.exists()) {
			throw new CorruptedBackupException(this, "Meta file does not exist.");
		}
		
		// Read the changes.
		String changes = new String(Files.readAllBytes(this.metaFile.toPath()), StandardCharsets.UTF_8);
		String[] lines = changes.replaceAll("\r\n", "\n").split("\n");
		
		// Create the new changes map.
		this.changesMap = new HashMap<String, ChangeProperties>();
		
		try {
			// Detect write corruption.
			if(!lines[lines.length - 1].equals('#' + this.name)) {
				throw new CorruptedBackupException(this, "Meta file does not end with expected suffix.");
			}
			
			// Parse and store the changes.
			for(int i = 0; i < lines.length - 1; i++) {
				String line = lines[i];
				if(line.isEmpty()) {
					throw new CorruptedBackupException(this, "Meta file contains an empty line.");
				}
				char typeChar = line.charAt(0);
				String relPath = line.substring(1).replace('/', File.separatorChar);
				switch(typeChar) {
					case '+':
						boolean isDirectory = relPath.endsWith(File.separator);
						String hash = null;
						if(!isDirectory) {
							String nextLine = lines[++i];
							if(!nextLine.startsWith("\t")) {
								throw new CorruptedBackupException(this,
										"Meta file does not contain hash for file addition: " + relPath);
							}
							hash = nextLine.substring(1);
						}
						this.changesMap.put(relPath, new ChangeProperties(relPath, ChangeType.ADDITION, hash));
						break;
					case '-':
						this.changesMap.put(relPath, new ChangeProperties(relPath, ChangeType.REMOVAL));
						break;
					default:
						throw new CorruptedBackupException(this, "Meta file contains"
								+ " unexpected first character on line " + (i + 1) + ": '" + typeChar + "'");
				}
			}
		} catch (CorruptedBackupException e) {
			
			// Reset the changes map so that it can be initialized again and rethrow the exception.
			this.changesMap = null;
			throw e;
		}
	}
	
	@Override
	public Map<String, ChangeType> getChanges() {
		if(this.changesMap == null) {
			return null;
		}
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		for(ChangeProperties change : this.changesMap.values()) {
			changes.put(change.relPath, change.changeType);
		}
		return changes;
	}
	
	@Override
	public String getName() {
		return this.name;
	}
	
	@Override
	public long getCreationTime() {
		return this.creationTime;
	}
	
	@Override
	public void delete() throws IOException {
		if(this.metaFile.exists()) {
			Files.delete(this.metaFile.toPath());
		}
		if(this.zipFileReader.getFile().exists()) {
			Files.delete(this.zipFileReader.getFile().toPath());
		}
	}
	
	@Override
	public void readAll(FileEntryHandler handler) throws InvocationTargetException, IOException {
		this.zipFileReader.readAll(handler);
	}
	
	/**
	 * Change properties for backup part changes.
	 * @author P.J.S. Kools
	 */
	private static class ChangeProperties {
		public final String relPath;
		public final ChangeType changeType;
		public final String hash;
		
		public ChangeProperties(String relPath, ChangeType changeType, String hash) {
			this.relPath = relPath;
			this.changeType = changeType;
			this.hash = hash;
		}
		
		public ChangeProperties(String relPath, ChangeType changeType) {
			this(relPath, changeType, null);
		}
	}
}
