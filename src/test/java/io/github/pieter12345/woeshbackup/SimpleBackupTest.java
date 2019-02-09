package io.github.pieter12345.woeshbackup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.pieter12345.woeshbackup.BackupPart.ChangeType;
import io.github.pieter12345.woeshbackup.utils.TestUtils;

/**
 * Tests the {@link SimpleBackup} class.
 * @author P.J.S. Kools
 */
class SimpleBackupTest {
	
	static final File TO_BACKUP_DIR = new File(SimpleBackupTest.class.getSimpleName() + "-temp");
	static final String FILE1 = "file1";
	static boolean toBackupDirWasCreated = false;
	static List<String> toBackupDirRelPaths;
	
	@BeforeAll
	static void initAll() throws IOException {
		// Create a directory structure to backup.
		if(TO_BACKUP_DIR.exists()) {
			fail("Temporary test directory already exists: " + TO_BACKUP_DIR.getAbsolutePath());
		}
		if(!TO_BACKUP_DIR.mkdir()) {
			fail("Temporary test directory could not be created: " + TO_BACKUP_DIR.getAbsolutePath());
		}
		toBackupDirWasCreated = true;
		
		toBackupDirRelPaths = Arrays.asList(
				FILE1,
				"file2",
				"dir1/",
					"dir1/dir1_file1",
					"dir1/dir1_file2",
					"dir1/dir1_dir1/",
						"dir1/dir1_dir1/dir1_dir1_file1",
					"dir1/dir1_dir2/"
		);
		for(int i = 0; i < toBackupDirRelPaths.size(); i++) {
			toBackupDirRelPaths.set(i, toBackupDirRelPaths.get(i).replace('/', File.separatorChar));
		}
		TestUtils.createFiles(TO_BACKUP_DIR, toBackupDirRelPaths);
	}
	
	@AfterAll
	static void tearDownAll() {
		if(toBackupDirWasCreated) {
			TestUtils.deleteFile(TO_BACKUP_DIR);
		}
	}
	
	@Test
	void testGetToBackupDir() {
		File toBackupDir = mock(File.class);
		Backup backup = new SimpleBackup(toBackupDir, mock(BackupPartFactory.class), null);
		assertThat(backup.getToBackupDir()).isEqualTo(toBackupDir);
	}
	
	@Test
	void testInitialBackup() throws Exception {
		
		// Create mocked backend.
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart, null);
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform the backup.
		backup.backup();
		
		// Verify that only one backup part was requested from the factory.
		verify(backupPartFactory, times(1)).createNew(anyLong());
		
		// Verify that all files were added to the backup part as additions.
		for(File file : TestUtils.listFiles(TO_BACKUP_DIR)) {
			String relPath = file.getAbsolutePath().substring(TO_BACKUP_DIR.getAbsolutePath().length() + 1)
					+ (file.isDirectory() ? File.separator : "");
			verify(newBackupPart, times(1)).addAddition(relPath, file);
		}
		
		// Verify that the backup part was closed.
		verify(newBackupPart, times(1)).close();
		
		// Verify that no more interactions were made with the backup part.
		verifyNoMoreInteractions(newBackupPart);
	}
	
	@Test
	void testUpdateBackupNoChanges() throws Exception {
		
		// Create mocked backend.
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		for(String relPath : toBackupDirRelPaths) {
			changes.put(relPath, ChangeType.ADDITION);
		}
		BackupPart existingBackupPart = mockBackupPart(0L, changes, null);
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart, Arrays.asList(existingBackupPart));
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform the backup.
		backup.backup();
		
		// Verify that only one backup part was requested from the factory.
		verify(backupPartFactory, times(1)).createNew(anyLong());
		
		// Verify that no changes were added to the new backup part.
		verify(newBackupPart, never()).addAddition(anyString(), any(File.class));
		verify(newBackupPart, never()).addModification(anyString(), any(File.class));
		verify(newBackupPart, never()).addRemoval(anyString());
		verify(newBackupPart, never()).merge(any(BackupPart.class));
		
		// Verify that the new backup part was closed.
		verify(newBackupPart, times(1)).close();
	}
	
	@Test
	void testUpdateBackupSingleAddChange() throws Exception {
		
		// Create mocked backend.
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		for(String relPath : toBackupDirRelPaths) {
			changes.put(relPath, ChangeType.ADDITION);
		}
		changes.remove(FILE1);
		BackupPart existingBackupPart = mockBackupPart(0L, changes, null);
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart, Arrays.asList(existingBackupPart));
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform the backup.
		backup.backup();
		
		// Verify that only one backup part was requested from the factory.
		verify(backupPartFactory, times(1)).createNew(anyLong());
		
		// Verify that only the single change was added to the new backup part.
		verify(newBackupPart, times(1)).addAddition(anyString(), any(File.class));
		verify(newBackupPart, times(1)).addAddition(eq(FILE1), any(File.class));
		verify(newBackupPart, never()).addModification(anyString(), any(File.class));
		verify(newBackupPart, never()).addRemoval(anyString());
		verify(newBackupPart, never()).merge(any(BackupPart.class));
		
		// Verify that the new backup part was closed.
		verify(newBackupPart, times(1)).close();
	}
	
	@Test
	void testUpdateBackupSingleModificationChange() throws Exception {
		
		// Create mocked backend.
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		for(String relPath : toBackupDirRelPaths) {
			changes.put(relPath, ChangeType.ADDITION);
		}
		BackupPart existingBackupPart = mockBackupPart(0L, changes, Arrays.asList(FILE1));
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart, Arrays.asList(existingBackupPart));
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform the backup.
		backup.backup();
		
		// Verify that only one backup part was requested from the factory.
		verify(backupPartFactory, times(1)).createNew(anyLong());
		
		// Verify that only the single change was added to the new backup part.
		verify(newBackupPart, never()).addAddition(anyString(), any(File.class));
		verify(newBackupPart, times(1)).addModification(anyString(), any(File.class));
		verify(newBackupPart, times(1)).addModification(eq(FILE1), any(File.class));
		verify(newBackupPart, never()).addRemoval(anyString());
		verify(newBackupPart, never()).merge(any(BackupPart.class));
		
		// Verify that the new backup part was closed.
		verify(newBackupPart, times(1)).close();
	}
	
	@Test
	void testUpdateBackupSingleDeletionChange() throws Exception {
		
		// Create mocked backend.
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		for(String relPath : toBackupDirRelPaths) {
			changes.put(relPath, ChangeType.ADDITION);
		}
		String unexistingRelPath = "unexistingFile";
		changes.put(unexistingRelPath, ChangeType.ADDITION);
		BackupPart existingBackupPart = mockBackupPart(0L, changes, null);
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart, Arrays.asList(existingBackupPart));
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform the backup.
		backup.backup();
		
		// Verify that only one backup part was requested from the factory.
		verify(backupPartFactory, times(1)).createNew(anyLong());
		
		// Verify that only the single change was added to the new backup part.
		verify(newBackupPart, never()).addAddition(anyString(), any(File.class));
		verify(newBackupPart, never()).addModification(anyString(), any(File.class));
		verify(newBackupPart, times(1)).addRemoval(anyString());
		verify(newBackupPart, times(1)).addRemoval(eq(unexistingRelPath));
		verify(newBackupPart, never()).merge(any(BackupPart.class));
		
		// Verify that the new backup part was closed.
		verify(newBackupPart, times(1)).close();
	}
	
	/**
	 * Tests that {@link SimpleBackup#merge(long)} properly calls {@link BackupPart#merge(BackupPart)} on the
	 * backup parts that should be merged. Also tests that the merged backup parts will be deleted afterwards.
	 * @throws Exception
	 */
	@Test
	void testMerge() throws Exception {
		/*
		 * Test plan.
		 * Generate 5 backups and merge the first three. Check if the merge() methods were used in the proper order.
		 */
		
		// Create mocked backend.
		BackupPart backupPart1 = mockBackupPart(10000L, null, null);
		BackupPart backupPart2 = mockBackupPart(20000L, null, null);
		BackupPart backupPart3 = mockBackupPart(30000L, null, null);
		BackupPart backupPart4 = mockBackupPart(40000L, null, null);
		BackupPart backupPart5 = mockBackupPart(50000L, null, null);
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart,
				Arrays.asList(backupPart1, backupPart2, backupPart3, backupPart4, backupPart5));
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform the merge between backups {1, 2, 3} and {4, 5}.
		backup.merge(35000);
		
		// Verify that only one backup part was requested from the factory.
		verify(backupPartFactory, times(1)).createNew(anyLong());
		
		// Verify that no changes added to the new backup part without using merge().
		verify(newBackupPart, never()).addAddition(anyString(), any(File.class));
		verify(newBackupPart, never()).addModification(anyString(), any(File.class));
		verify(newBackupPart, never()).addRemoval(anyString());
		verify(newBackupPart, times(3)).merge(any(BackupPart.class));
		
		// Verify that the backups were merged in the expected order.
		InOrder inOrder = inOrder(newBackupPart);
		inOrder.verify(newBackupPart).merge(backupPart3);
		inOrder.verify(newBackupPart).merge(backupPart2);
		inOrder.verify(newBackupPart).merge(backupPart1);
		
		// Verify that the merged backups were deleted and the others were not.
		verify(backupPart1, times(1)).delete();
		verify(backupPart2, times(1)).delete();
		verify(backupPart3, times(1)).delete();
		verify(backupPart4, never()).delete();
		verify(backupPart5, never()).delete();
		
		// Verify that the new backup part was closed.
		verify(newBackupPart, times(1)).close();
	}
	
	/**
	 * Tests that {@link SimpleBackup#restore(long, BackupRestoreWriterFactory)} on a single backup part does include
	 * file and directory additions while not including file and directory removals.
	 * @throws Exception
	 */
	@Test
	void testRestoreSingleBackupPart() throws Exception {
		
		// Create mocked backend.
		String file1 = "file1";
		String dir1 = "dir1" + File.separator;
		String file2 = "file2";
		String dir2 = "dir2" + File.separator;
		long latestBackupPartDate = 10000L;
		Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
		Map<String, InputStream> inStreams = new HashMap<String, InputStream>();
		changes.put(file1, ChangeType.ADDITION);
		inStreams.put(file1, mock(InputStream.class));
		changes.put(dir1, ChangeType.ADDITION);
		inStreams.put(dir1, null);
		changes.put(file2, ChangeType.REMOVAL);
		inStreams.put(file2, mock(InputStream.class));
		changes.put(dir2, ChangeType.REMOVAL);
		inStreams.put(dir2, null);
		BackupPart backupPart = mockBackupPart(latestBackupPartDate, changes, null, inStreams);
		BackupPart newBackupPart = mock(BackupPart.class);
		BackupPartFactory backupPartFactory = mockBackupPartFactory(newBackupPart, Arrays.asList(backupPart));
		
		// Create mocked restore backup writer.
		BackupRestoreWriter restoreWriter = mock(BackupRestoreWriter.class);
		BackupRestoreWriterFactory restoreWriterFactory = mock(BackupRestoreWriterFactory.class);
		doReturn(restoreWriter).when(restoreWriterFactory).create(anyLong());
		
		// Create backup.
		Backup backup = new SimpleBackup(TO_BACKUP_DIR, backupPartFactory, mock(Logger.class));
		
		// Perform restore.
		backup.restore(latestBackupPartDate + 10000L, restoreWriterFactory);
		
		// Verify that exactly one restore writer was requested from the factory with the proper timestamp.
		verify(restoreWriterFactory, times(1)).create(latestBackupPartDate);
		verifyNoMoreInteractions(restoreWriterFactory);
		
		// Verify that the files were passed to the restore writer after opening and before closing the writer.
		verify(restoreWriter, times(2)).add(anyString(), any());
		verify(restoreWriter, times(1)).open();
		verify(restoreWriter, times(1)).close();
		verifyNoMoreInteractions(restoreWriter);
		for(String relPath : new String[] {file1, dir1}) {
			InOrder inOrder = inOrder(restoreWriter);
			inOrder.verify(restoreWriter).open();
			inOrder.verify(restoreWriter).add(
					TO_BACKUP_DIR.getName() + File.separator + relPath, inStreams.get(relPath));
			inOrder.verify(restoreWriter).close();
		}
	}
	
	/**
	 * Creates a {@link BackupPartFactory} mock.
	 * @param newBackupPart - The BackupPart to return on {@link BackupPartFactory#createNew(long)}.
	 * The passed long will be set as return value for {@link BackupPart#getCreationTime()}.
	 * Passing null or an empty list yields the same result.
	 * @param existingBackups - A list of existing backups to be returned by
	 * {@link BackupPartFactory#readAllBefore(long)}. They are selected based on the passed beforeDate.
	 * @return The mocked {@link BackupPartFactory}.
	 * @throws IOException
	 */
	static BackupPartFactory mockBackupPartFactory(BackupPart newBackupPart, List<BackupPart> existingBackups)
			throws IOException {

		// Create mocked backup part factory.
		BackupPartFactory backupPartFactory = mock(BackupPartFactory.class);
		
		// Make BackupPartFactory.createNew(long) return the given mocked backup part with the proper creation time.
		doAnswer((invocation) -> {
			doReturn(invocation.getArgument(0)).when(newBackupPart).getCreationTime();
			return newBackupPart;
		}).when(backupPartFactory).createNew(anyLong());
		
		// Make BackupPartFactory.readAllBefore(beforeDate) return all backups selected by the beforeDate.
		doAnswer((invocation) -> {
			long beforeDate = invocation.getArgument(0);
			List<BackupPart> ret = new ArrayList<BackupPart>();
			if(existingBackups != null) {
				for(BackupPart backup : existingBackups) {
					if(beforeDate < 0 || backup.getCreationTime() < beforeDate) {
						ret.add(backup);
					}
				}
			}
			ret.sort((b1, b2) -> Long.compare(b1.getCreationTime(), b2.getCreationTime()));
			return ret;
		}).when(backupPartFactory).readAllBefore(anyLong());
		
		// Can't determine usable disk space.
		doReturn(-1L).when(backupPartFactory).getFreeUsableSpace();
		
		// Return the factory.
		return backupPartFactory;
	}
	
	/**
	 * Creates a {@link BackupPart} mock.
	 * @param creationTime - The creation time of the backup or null to not set any.
	 * @param changes - The changes returned by {@link BackupPart#getChanges()}.
	 * @param changedFileRelPaths - A list of paths for which {@link BackupPart#contains(String, File, boolean)}
	 * will be forced to return false if it comes to comparing files. This won't affect the return value for
	 * directories, null file arguments, when the relPath is not in the changes and when compareContents is false.
	 * Passing null or an empty list yields the same result.
	 * @return The mocked {@link BackupPart}.
	 * @throws InvocationTargetException
	 * @throws IOException
	 */
	static BackupPart mockBackupPart(Long creationTime, Map<String, ChangeType> changes,
			List<String> changedFileRelPaths) throws InvocationTargetException, IOException {
		return mockBackupPart(creationTime, changes, changedFileRelPaths, null);
	}
	
	/**
	 * Creates a {@link BackupPart} mock.
	 * @param creationTime - The creation time of the backup or null to not set any.
	 * @param changes - The changes returned by {@link BackupPart#getChanges()}.
	 * @param changedFileRelPaths - A list of paths for which {@link BackupPart#contains(String, File, boolean)}
	 * will be forced to return false if it comes to comparing files. This won't affect the return value for
	 * directories, null file arguments, when the relPath is not in the changes and when compareContents is false.
	 * Passing null or an empty list yields the same result.
	 * @param fileContentsMap - A map containing {@link InputStream}s with their relative paths as key.
	 * These streams are returned through {@link BackupPart#readAll(FileEntryHandler)}.
	 * When a relative path does not appear in this map, a mocked {@link InputStream} will be used.
	 * Keys that do not appear in the passed changes map will be ignored.
	 * @return The mocked {@link BackupPart}.
	 * @throws InvocationTargetException
	 * @throws IOException
	 */
	static BackupPart mockBackupPart(
			Long creationTime, Map<String, ChangeType> changes, List<String> changedFileRelPaths,
			Map<String, InputStream> fileContentsMap) throws InvocationTargetException, IOException {
		
		// Create the mocked backup part.
		BackupPart backupPart = mock(BackupPart.class);
		
		// Define BackupPart.getCreationTime() if given.
		if(creationTime != null) {
			doReturn(creationTime).when(backupPart).getCreationTime();
		}
		
		// Define BackupPart.getChanges().
		if(changes != null) {
			doReturn(new HashMap<String, ChangeType>(changes)).when(backupPart).getChanges();
		}
		
		// Define BackupPart.readAll(FileEntryHandler) to pass a new FileEntry
		// for all changes with a mocked InputStream to the handler.
		doAnswer((invocation) -> {
			FileEntryHandler handler = invocation.getArgument(0);
			for(String change : changes.keySet()) {
				try {
					InputStream inStream = (fileContentsMap != null && fileContentsMap.containsKey(change)
							? fileContentsMap.get(change) : mock(InputStream.class));
					handler.handle(new FileEntry(change, inStream));
				} catch (Throwable t) {
					throw new InvocationTargetException(t);
				}
			}
			return null;
		}).when(backupPart).readAll(any(FileEntryHandler.class));
		
		// Define BackupPart.contains(relPath, file, compareContent) to never compare the contents,
		// but return false for the given relative paths.
		doAnswer((invocation) -> {
			String relPath = invocation.getArgument(0);
			File file = invocation.getArgument(1);
			boolean compareContents = invocation.getArgument(2);
			return changes.containsKey(relPath)
					&& (!compareContents || relPath.endsWith(File.separator)
					|| (file != null && (changedFileRelPaths == null || !changedFileRelPaths.contains(relPath))));
		}).when(backupPart).contains(anyString(), any(File.class), anyBoolean());

		// Return the backup part.
		return backupPart;
	}
}
