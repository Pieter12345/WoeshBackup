package io.github.pieter12345.woeshbackup;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests the {@link FileIterator} class.
 * @author P.J.S. Kools
 */
class IgnorePathsTest {
	
	/**
	 * Tests various single ignore paths and if they hide expected paths and do not hide for example parent directories.
	 */
	@ParameterizedTest(name = "Ignore path: \"{0}\", Check for: \"{1}\", Expect ignored: {2}")
	@CsvSource({
		// File ignore path.
		"f1/f2/f3, f1/f2/f3, true",
		"f1/f2/f3, f1/f2/f4, false",
		"f1/f2/f3, f1/f2/f3/, false",
		"f1/f2/f3, f1/f2/f3/f4, false",
		"f1/f2/f3, f1/f2/f3/f4/, false",
		"f1/f2/f3, f1/f2/f3/f4/f5, false",
		"f1/f2/f3, f1/f2/, false",
		"f1/f2/f3, f1/f2, false",
		
		// Directory ignore path.
		"f1/f2/f3/, f1/f2/f3/, true",
		"f1/f2/f3/, f1/f2/f3, false",
		"f1/f2/f3/, f1/f2/f3/f4, true",
		"f1/f2/f3/, f1/f2/f3/f4/, true",
		"f1/f2/f3/, f1/f2/, false",
		"f1/f2/f3/, f1/f2, false",

		// File ignore path main dir.
		"f1, f1, true",
		"f1, f2, false",
		"f1, f1/, false",
		"f1, f1/f2, false",
		"f1, f1/f2/, false",
		
		// Directory ignore path main dir.
		"f1/, f1/, true",
		"f1/, f1, false",
		"f1/, f1/f2, true",
		"f1/, f1/f2/, true"
	})
	void testSingleIgnorePath(String ignorePath, String checkPath, boolean expectIgnored) {
		ignorePath = ignorePath.replace('/', File.separatorChar);
		checkPath = checkPath.replace('/', File.separatorChar);
		IgnorePaths ignorePaths = new IgnorePaths(new HashSet<String>(Arrays.asList(ignorePath)));
		assertThat(ignorePaths.isIgnored(checkPath)).isEqualTo(expectIgnored);
	}
}
