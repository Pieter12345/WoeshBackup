package io.github.pieter12345.woeshbackup.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Utils class.
 * This class contains useful methods that do not belong elsewhere.
 * @author P.J.S. Kools
 * @since 05-01-2017
 */
public abstract class Utils {
	
//	/**
//	 * removeFile method.
//	 * Removes the given file or directory (including subdirectories).
//	 * @param file - The File to remove.
//	 * @return True if the removal was successful. False if one or more files could not be removed.
//	 *  If the file does not exist, true is returned.
//	 */
//	public static boolean removeFile(File file) {
//		if(file.isFile()) {
//			return file.delete();
//		} else if(file.isDirectory()) {
//			boolean ret = true;
//			Stack<File> dirStack = new Stack<File>();
//			dirStack.push(file);
//			while(!dirStack.isEmpty()) {
//				File localDir = dirStack.pop();
//				File[] localFiles = localDir.listFiles();
//				if(localFiles != null) {
//					for(File localFile : localFiles) {
//						if(localFile.isDirectory()) {
//							dirStack.push(localFile);
//						} else  {
//							ret &= localFile.delete();
//						}
//					}
//				}
//				ret &= localDir.delete();
//			}
//			return ret;
//		} else {
//			return !file.exists(); // It's not a file and not a directory, return true.
//		}
//	}
	
	/**
	 * getStacktrace method.
	 * @param throwable - The Throwable for which to create the stacktrace String.
	 * @return The stacktrace printed when "throwable.printStackTrace()" is called.
	 */
	public static String getStacktrace(Throwable throwable) {
		if(throwable == null) {
			throw new NullPointerException("Exception argument is null.");
		}
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		try {
			throwable.printStackTrace(new PrintStream(outStream, true, StandardCharsets.UTF_8.name()));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace(); // Never happens.
		}
		return new String(outStream.toByteArray(), StandardCharsets.UTF_8);
	}
}
