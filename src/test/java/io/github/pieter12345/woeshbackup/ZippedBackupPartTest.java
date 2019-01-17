package io.github.pieter12345.woeshbackup;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.io.Files;

import io.github.pieter12345.woeshbackup.BackupPart.ChangeType;
import io.github.pieter12345.woeshbackup.exceptions.CorruptedBackupException;
import io.github.pieter12345.woeshbackup.utils.TestUtils;

/**
 * Tests the {@link ZippedBackupPart} class.
 * @author P.J.S. Kools
 */
class ZippedBackupPartTest {
	
	static final File BASE_DIR = new File(ZippedBackupPartTest.class.getSimpleName() + "-temp");
	static final String FILE1 = "file1";
	static boolean baseDirWasCreated = false;
	static List<String> toBackupDirRelPaths;
	
	@BeforeAll
	static void initAll() throws IOException {
		if(BASE_DIR.exists()) {
			fail("Temporary test directory already exists: " + BASE_DIR.getAbsolutePath());
		}
		if(!BASE_DIR.mkdir()) {
			fail("Temporary test directory could not be created: " + BASE_DIR.getAbsolutePath());
		}
		baseDirWasCreated = true;
	}
	
	@AfterAll
	static void tearDownAll() {
		if(baseDirWasCreated) {
			TestUtils.deleteFile(BASE_DIR);
		}
	}
	
	@Test
	void testGetCreationTime() {
		long time = System.currentTimeMillis();
		String name = "testBackupPart";
		BackupPart part = new ZippedBackupPart(new File(BASE_DIR, name), name, time);
		assertThat(part.getCreationTime()).isEqualTo(time);
	}
	
	@Test
	void testGetName() {
		String name = "testBackupPart";
		BackupPart part = new ZippedBackupPart(new File(BASE_DIR, name), name, 1000);
		assertThat(part.getName()).isEqualTo(name);
	}
	
	/**
	 * Tests writing changes to a backup part, reading them using a new backup part instance and deleting it.
	 */
	@Test
	void testWriteReadDelete() throws Exception {
		
		// Create the backup part to write.
		long time = System.currentTimeMillis();
		String name = "testBackupPart";
		BackupPart writePart = new ZippedBackupPart(BASE_DIR, name, time);
		
		// Create the files to add.
		File file1 = createFile(BASE_DIR, "file1", null);
		File file2 = createFile(BASE_DIR, "file2", new byte[] {6, 7, 8, 9, 0});
		File file3 = createFile(BASE_DIR, "file3", new byte[] {1, 2, 3, 4, 5});
		File dir1 = createDir(BASE_DIR, "dir1");
		File dir2 = createDir(BASE_DIR, "dir2");
		File dir3 = createDir(BASE_DIR, "dir3");
		
		// Add the files to the backup part.
		writePart.addAddition(fileToRelPath(BASE_DIR, file1), file1);
		writePart.addModification(fileToRelPath(BASE_DIR, file2), file2);
		writePart.addRemoval(fileToRelPath(BASE_DIR, file3));
		writePart.addAddition(fileToRelPath(BASE_DIR, dir1), dir1);
		writePart.addModification(fileToRelPath(BASE_DIR, dir2), dir2);
		writePart.addRemoval(fileToRelPath(BASE_DIR, dir3));
		
		// Verify that the changes match the added files.
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		changes.put(fileToRelPath(BASE_DIR, file1), ChangeType.ADDITION);
		changes.put(fileToRelPath(BASE_DIR, file2), ChangeType.ADDITION);
		changes.put(fileToRelPath(BASE_DIR, file3), ChangeType.REMOVAL);
		changes.put(fileToRelPath(BASE_DIR, dir1), ChangeType.ADDITION);
		changes.put(fileToRelPath(BASE_DIR, dir2), ChangeType.ADDITION);
		changes.put(fileToRelPath(BASE_DIR, dir3), ChangeType.REMOVAL);
		assertThat(writePart.getChanges()).containsAllEntriesOf(changes);
		
		// Close the backup part.
		writePart.close();
		
		// Create the backup part to read.
		BackupPart readPart = new ZippedBackupPart(BASE_DIR, name, time);
		readPart.readChanges();

		// Verify that the changes match the added files.
		assertThat(readPart.getChanges()).containsAllEntriesOf(changes);
		
		// Verify that the files match the added files.
		// Also verify that BackupPart.contains() agrees that the backup part has these files.
		List<String> relPaths = new ArrayList<String>();
		List<String> expectedRelPaths = new ArrayList<String>();
		for(Entry<String, ChangeType> entry : changes.entrySet()) {
			if(entry.getValue() != ChangeType.REMOVAL) {
				expectedRelPaths.add(entry.getKey());
				assertThat(readPart.contains(entry.getKey(), new File(BASE_DIR, entry.getKey()), true)).isTrue();
			}
		}
		readPart.readAll((FileEntry fileEntry) -> {
			relPaths.add(fileEntry.getRelativePath());
		});
		assertThat(relPaths).containsExactlyInAnyOrderElementsOf(expectedRelPaths);
		
		// Delete the backup.
		readPart.delete();
		
		// Verify that the backup was deleted.
		BackupPart part = new ZippedBackupPart(BASE_DIR, name, time);
		assertThrows(CorruptedBackupException.class, () -> {
			part.readChanges();
		});
	}
	
	static File createFile(File baseDir, String fileName, byte[] fileBytes) throws IOException {
		File file = new File(baseDir, fileName);
		assert !file.exists() : "File already exists.";
		file.createNewFile();
		if(fileBytes != null) {
			Files.write(fileBytes, file);
		}
		return file;
	}
	
	static File createDir(File baseDir, String dirName) {
		File file = new File(baseDir, dirName);
		assert !file.exists() : "File already exists.";
		file.mkdir();
		return file;
	}
	
	static String fileToRelPath(File parent, File file) {
		String prefix = parent.getAbsolutePath() + File.separator;
		String absPath = file.getAbsolutePath();
		assert absPath.startsWith(prefix) : "Given parent is not a parent of the given file.";
		return absPath.substring(prefix.length()) + (file.isDirectory() ? File.separator : "");
	}
}
