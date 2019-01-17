package io.github.pieter12345.woeshbackup.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * TestUtils class.
 * This class contains useful methods for tests that do not belong elsewhere.
 * @author P.J.S. Kools
 */
public abstract class TestUtils {
	
	/**
	 * Deletes the given file or directory including files and subdirectories.
	 * @param file - The file or directory to remove.
	 * @return {@code true} if the removal was successful and {@code false} if one or more files could not be removed.
	 * If the file does not exist, {@code true} is returned.
	 */
	public static boolean deleteFile(File file) {
		if(!file.exists()) {
			return true;
		}
		if(file.isDirectory()) {
			boolean ret = true;
			for(File localFile : file.listFiles()) {
				ret &= deleteFile(localFile);
			}
			return file.delete() && ret;
		} else {
			return file.delete();
		}
	}
	
	/**
	 * Creates the given list of files and directories in the given base directory.
	 * This will not automatically create required directories, so these have to be passed as well.
	 * @param baseDir - The directory to create the given files and directories in.
	 * @param relPaths - Relative paths to files and directories, interpreted as new File(baseDir, relPath).
	 * If a path ends with a file separator, a directory is created.
	 * @throws IOException If an I/O error has occurred.
	 * @throws IllegalStateException If a file already exists at a given path.
	 */
	public static void createFiles(File baseDir, List<String> relPaths) throws IOException, IllegalStateException {
		List<String> sortedPaths = new ArrayList<String>(relPaths);
		sortedPaths.sort((p1, p2) -> p1.compareTo(p2)); // Sort so that directories come before files.
		String baseDirPath = baseDir.getAbsolutePath();
		for(String path : sortedPaths) {
			File file = new File(baseDir, path);
			if(!file.getAbsolutePath().startsWith(baseDirPath + File.separator)) {
				throw new SecurityException("Relative path points outside of the base directory: " + path);
			}
			if(path.endsWith(File.separator)) {
				if(!file.mkdir()) {
					throw new IOException("Could not create directory: " + path);
				}
			} else {
				if(!file.createNewFile()) {
					throw new IllegalStateException("File already exists: " + path);
				}
			}
		}
	}
	
	/**
	 * Lists all files and directories in the given directory, including subdirectories.
	 * @param dir - The directory to list the files of.
	 * @return All files and directories in the given directory, including subdirectories.
	 */
	public static List<File> listFiles(File dir) {
		List<File> ret = new ArrayList<File>();
		Stack<File> dirStack = new Stack<File>();
		dirStack.push(dir);
		while(!dirStack.empty()) {
			for(File f : dirStack.pop().listFiles()) {
				ret.add(f);
				if(f.isDirectory()) {
					dirStack.push(f);
				}
			}
		}
		return ret;
	}
}
