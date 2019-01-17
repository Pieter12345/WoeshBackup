package io.github.pieter12345.woeshbackup;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.pieter12345.woeshbackup.utils.TestUtils;

/**
 * Tests the {@link FileIterator} class.
 * @author P.J.S. Kools
 */
class FileIteratorTest {
	
	static final File BASE_DIR = new File(FileIteratorTest.class.getSimpleName() + "-temp");
	static boolean baseDirWasCreated = false;
	static List<String> baseDirRelPaths;
	
	@BeforeAll
	static void initAll() throws Exception {
		// Create a directory structure to iterate over.
		if(BASE_DIR.exists()) {
			fail("Temporary test directory already exists: " + BASE_DIR.getAbsolutePath());
		}
		if(!BASE_DIR.mkdir()) {
			fail("Temporary test directory could not be created: " + BASE_DIR.getAbsolutePath());
		}
		baseDirWasCreated = true;
		
		baseDirRelPaths = Arrays.asList(
				"file1",
				"file2",
				"dir1/",
					"dir1/dir1_file1",
					"dir1/dir1_file2",
					"dir1/dir1_dir1/",
						"dir1/dir1_dir1/dir1_dir1_file1",
					"dir1/dir1_dir2/"
		);
		for(int i = 0; i < baseDirRelPaths.size(); i++) {
			baseDirRelPaths.set(i, baseDirRelPaths.get(i).replace('/', File.separatorChar));
		}
		TestUtils.createFiles(BASE_DIR, baseDirRelPaths);
	}
	
	@AfterAll
	static void tearDownAll() throws Exception {
		if(baseDirWasCreated) {
			TestUtils.deleteFile(BASE_DIR);
		}
	}
	
	/**
	 * Tests iteration over the generated base directory without ignore paths.
	 */
	@Test
	void testIterateWithoutIgnorePaths() {
		FileIterator it = new FileIterator(BASE_DIR, new ArrayList<String>());
		String baseDirPath = BASE_DIR.getAbsolutePath();
		List<String> relPaths = new ArrayList<String>();
		while(it.hasNext()) {
			File next = it.next();
			assertThat(next.getAbsolutePath().startsWith(baseDirPath + File.separator)).isTrue();
			relPaths.add(next.getAbsolutePath().substring(baseDirPath.length() + 1)
					+ (next.isDirectory() ? File.separator : ""));
		}
		assertThat(relPaths).containsExactlyInAnyOrderElementsOf(baseDirRelPaths);
	}
	
	/**
	 * Tests iteration over the generated base directory with a single file in ignore paths.
	 */
	@ParameterizedTest(name = "Ignore path: \"{0}\"")
	@CsvSource({
		"file1", "dir1/", "dir1/dir1_dir1/", "dir1/dir1_dir1/dir1_dir1_file1"
	})
	void testIterateWithSingleIgnorePath(String fileToIgnore) {
		fileToIgnore = fileToIgnore.replace('/', File.separatorChar);
		
		// Determine the expected result, given a list of paths to ignore.
		List<String> expectedResult = new ArrayList<String>(baseDirRelPaths);
		assertThat(expectedResult.remove(fileToIgnore)).isTrue();
		if(fileToIgnore.endsWith(File.separator)) {
			for(Iterator<String> it = expectedResult.iterator(); it.hasNext();) {
				if(it.next().startsWith(fileToIgnore)) {
					it.remove();
				}
			}
		}
		
		// Perform the iteration, storing the relative paths.
		FileIterator it = new FileIterator(BASE_DIR, Arrays.asList(fileToIgnore));
		String baseDirPath = BASE_DIR.getAbsolutePath();
		List<String> relPaths = new ArrayList<String>();
		while(it.hasNext()) {
			File next = it.next();
			assertThat(next.getAbsolutePath().startsWith(baseDirPath + File.separator)).isTrue();
			relPaths.add(next.getAbsolutePath().substring(baseDirPath.length() + 1)
					+ (next.isDirectory() ? File.separator : ""));
		}
		
		// Assert the result.
		assertThat(relPaths).containsExactlyInAnyOrderElementsOf(expectedResult);
	}
}
