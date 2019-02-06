package io.github.pieter12345.woeshbackup;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Used to check whether given paths should be ignored depending on the set ignore paths.
 * @author P.J.S. Kools
 */
public class IgnorePaths {
	
	private Map<String, Object> ignorePathMap = new HashMap<String, Object>();
	
	/**
	 * Creates a new ignore paths object containing the given relative ignore paths.
	 * @param relIgnorePaths - The ignore paths. These should not have a leading file separator.
	 * Directories should be suffixed with a file separator.
	 */
	public IgnorePaths(Set<String> relIgnorePaths) {
		for(String relIgnorePath : relIgnorePaths) {
			this.addIgnorePath(relIgnorePath);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void addIgnorePath(String relIgnorePath) {
		String[] parts = relIgnorePath.split(File.separatorChar == '\\' ? "\\\\" : File.separator);
		Map<String, Object> pathMap = this.ignorePathMap;
		for(int i = 0; i < parts.length - 1; i++) {
			String part = parts[i] + File.separator;
			Object val = pathMap.get(part);
			if(val == null) {
				Map<String, Object> newMap = new HashMap<String, Object>();
				pathMap.put(part, newMap);
				pathMap = newMap;
			} else if(val instanceof Map<?, ?>) {
				pathMap = (Map<String, Object>) val;
			} else {
				return; // Parent directory already (in)directly ignored.
			}
		}
		boolean isDirectory = relIgnorePath.endsWith(File.separator);
		pathMap.put(parts[parts.length - 1] + (isDirectory ? File.separator : ""), new Object());
	}
	
	/**
	 * Checks whether the given path is in the ignore paths or not.
	 * @param relPath - The relative path to check.
	 * @return {@code true} if the given path or one of its parents is in the ignore paths, {@code false} otherwise.
	 */
	@SuppressWarnings("unchecked")
	public boolean isIgnored(String relPath) {
		String[] parts = relPath.split(File.separatorChar == '\\' ? "\\\\" : File.separator);
		Map<String, Object> pathMap = this.ignorePathMap;
		for(int i = 0; i < parts.length - 1; i++) {
			String part = parts[i] + File.separator;
			Object val = pathMap.get(part);
			if(val == null) {
				return false; // This parent directory and all its children are not ignored.
			} else if(val instanceof Map<?, ?>) {
				pathMap = (Map<String, Object>) val;
			} else {
				return true; // This parent directory is ignored.
			}
		}
		boolean isDirectory = relPath.endsWith(File.separator);
		Object val = pathMap.get(parts[parts.length - 1] + (isDirectory ? File.separator : ""));
		return val != null && !(val instanceof Map<?, ?>);
	}
}
