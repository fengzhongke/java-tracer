package com.ali.trace.spy.util;

import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NameUtils {
	
	/**
	 * delete chars like <, >, $
	 * 
	 * @param str
	 * @return
	 */
	public static final String replace(String str) {
		return str == null ? "" : str.replaceAll("<|>|\\$", "");
	}

	/**
	 * with file system path and class name get the byte-code file
	 * 
	 * @param path
	 * @param name
	 * @return
	 */
	public static final String getClassFile(String path, String name) {
		return String.valueOf(new StringBuffer(path).append('/').append(getClassPath(name)).append(".class"));
	}

	/**
	 * with class name get the path com.ali.bat convert to com/ali/bat
	 * 
	 * @param name
	 * @return
	 */
	public static final String getClassPath(String name) {
		return name.replace('.', '/');
	}

	/**
	 * com/ali/bat to com.ali.bat
	 */
	public static final String getClassName(String path) {
		return replace(path.replace('/', '.'));
	}


	private static final String MD5_ALGORITHM_NAME = "MD5";
	private static final char[] CHARS_HEX = "0123456789abcdef".toCharArray();

	public static String getHexMd5(String content) {
		try {
			byte[] bytes = MessageDigest.getInstance(MD5_ALGORITHM_NAME).digest(content.getBytes());
			char chars[] = new char[32];
			for (int i = 0; i < chars.length; i = i + 2) {
				byte b = bytes[i / 2];
				chars[i] = CHARS_HEX[(b >>> 0x4) & 0xf];
				chars[i + 1] = CHARS_HEX[b & 0xf];
			}
			return new String(chars);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Could not find MessageDigest with algorithm MD5 ", ex);
		}
	}

	
	
	public static final String getMethodName(String methodName) {
		return replace(methodName.replaceAll("<\\|>", ""));
	}

	public static String getJarPath() {
		URL url = NameUtils.class.getProtectionDomain().getCodeSource().getLocation();
		return url.getPath();
	}

	public static void main(String[] args) {
		System.out.println(getJarPath());
		System.out.println(getMethodName("<clinit>"));

	}

}
