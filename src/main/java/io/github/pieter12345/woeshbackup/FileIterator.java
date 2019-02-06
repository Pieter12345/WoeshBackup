package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

/**
 * An iterator used to iterate over (nested) files and directories.
 * @author P.J.S. Kools
 */
public class FileIterator implements Iterator<File> {
	
	private final File baseDir;
	private final Set<String> relativeIgnorePaths;
	private final Stack<File> dirStack = new Stack<File>();
	private final FileFilter fileFilter;
	private File[] files;
	private int filesIndex;
	private File next;
	
	/**
	 * Creates a new FileIterator starting at the given base directory, ignoring the given relative paths. The base
	 * directory itself will not be returned by the iterator, but its (nested) contents including directories will.
	 * @param baseDir - The directory containing the files that will be iterated over.
	 * @param relativeIgnorePaths - Ignore paths relative to the base directory. These will be interpreted as
	 * "baseDir.getAbsolutePath() + separator + relativeIgnorePath.get(x)".
	 * Ignore paths that end with a path separator char will be interpreted as directories.
	 */
	public FileIterator(File baseDir, Collection<String> relativeIgnorePaths) {
		this.baseDir = baseDir;
		this.relativeIgnorePaths = new HashSet<String>(relativeIgnorePaths);
		int baseDirPathLength = this.baseDir.getAbsolutePath().length() + 1; // Includes ending separator.
		this.fileFilter = (file) -> !this.relativeIgnorePaths.contains(
				file.getAbsolutePath().substring(baseDirPathLength) + (file.isDirectory() ? File.separator : ""));
		
		// Initialize the files, file index and next element.
		this.files = this.baseDir.listFiles(this.fileFilter);
		this.filesIndex = 0;
		this.next = (this.files == null || this.files.length == 0 ? null : this.files[this.filesIndex++]);
	}
	
	@Override
	public boolean hasNext() {
		return this.next != null;
	}
	
	@Override
	public File next() {
		
		// Throw an exception when there are no more elements.
		if(!this.hasNext()) {
			throw new NoSuchElementException();
		}
		
		// Get the next element.
		final File next = this.next;
		
		// Schedule the file for walking if it is a directory.
		if(this.next.isDirectory()) {
			this.dirStack.push(this.next);
		}
		
		// Get a new files array if the current one has been iterated over.
		if(this.filesIndex == this.files.length) {
			this.files = null;
			this.filesIndex = 0;
			while((this.files == null || this.files.length == 0) && !this.dirStack.empty()) {
				this.files = this.dirStack.pop().listFiles(this.fileFilter);
			}
			this.next = (this.files == null || this.files.length == 0 ? null : this.files[this.filesIndex++]);
		} else {
			this.next = this.files[this.filesIndex++];
		}
		
		// Return the next element.
		return next;
	}
}
