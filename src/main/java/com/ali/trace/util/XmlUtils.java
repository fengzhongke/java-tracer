package com.ali.trace.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class XmlUtils {

	public static void main(String[] args) throws IOException {
		formatXml("/tmp/b.xml");
	}
	
	public static void formatXml(String fileName)  throws IOException {
		fillXml(fileName, fileName + ".xml");
		restoreXml(fileName + ".xml", fileName + ".tmp");
		reverseFile(fileName + ".tmp", fileName + ".xml");
	}

	public static void fillXml(String fileName, String fillFile) throws IOException {
		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(fileName));
			writer = new BufferedWriter(new FileWriter(fillFile));
			String line = null;
			Stack<String> params = new Stack<String>();
			while ((line = reader.readLine()) != null) {
				char c = line.charAt(1);
				if (c != '/') {
					int idx = line.indexOf(' ');
					if (idx > 0) {
						String param = line.substring(1, idx);
						params.push(param);
					}
				} else {
					params.pop();
				}
				writer.write(line);
				writer.write("\r\n");
			}
			if (!params.isEmpty()) {
				while (!params.isEmpty()) {
					String param = params.pop();
					writer.write("</");
					writer.write(param);
					writer.write(" _f='false'>\r\n");
				}
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static void restoreXml(String fileName, String reverseFile) throws IOException {
		CrunchifyReverseLineReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new CrunchifyReverseLineReader(fileName);
			writer = new BufferedWriter(new FileWriter(reverseFile));
			String line = null;
			Stack<String> params = new Stack<String>();
			while ((line = reader.readLine()) != null) {
				if (line.length() > 1) {
					char c = line.charAt(1);
					int idx = line.indexOf(' ');
					String prefix = line.substring(0, idx);
					String param = line.substring(idx, line.length() - 1);
					writer.write(prefix);
					if (c == '/') {
						params.push(param);
					} else {
						writer.write(params.pop());
						writer.write(param);
					}
					writer.write(">");
					writer.write("\r\n");
				}
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static void reverseFile(String fileName, String reverseFile) throws IOException {
		CrunchifyReverseLineReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new CrunchifyReverseLineReader(fileName);
			writer = new BufferedWriter(new FileWriter(reverseFile));
			String line = null;
			while ((line = reader.readLine()) != null) {
				writer.write(line);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static List<String> cat(String fileName) throws IOException {
		List<String> lines = new ArrayList<String>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(fileName));
			String line = null;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return lines;
	}

	public static void echo(String fileName, List<String> lines) throws IOException {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(fileName));
			for (String line : lines) {
				if (line != null) {
					writer.write(line);
				}
			}
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	private static class CrunchifyReverseLineReader {
		private static final int BUFFER_SIZE = 8192;
		private final FileChannel channel;
		private final String encoding;
		private long filePos;
		private ByteBuffer buf;
		private int bufPos;
		private ByteArrayOutputStream baos = new ByteArrayOutputStream();
		private RandomAccessFile raf;

		public CrunchifyReverseLineReader(String fileName) throws IOException {
			this(fileName, null);
		}

		public CrunchifyReverseLineReader(String fileName, String encoding) throws IOException {
			raf = new RandomAccessFile(fileName, "r");
			channel = raf.getChannel();
			filePos = raf.length();
			this.encoding = encoding;
		}

		public void close() throws IOException {
			raf.close();
		}

		public String readLine() throws IOException {
			byte c;
			while (true) {
				if (bufPos < 0) {
					if (filePos == 0) {
						if (baos == null) {
							return null;
						}
						String line = bufToString();
						baos = null;
						return line;
					}

					long start = Math.max(filePos - BUFFER_SIZE, 0);
					long end = filePos;
					long len = end - start;

					buf = channel.map(FileChannel.MapMode.READ_ONLY, start, len);
					bufPos = (int) len;
					filePos = start;

					c = buf.get(--bufPos);
					Byte preC = null;
					if (c == '\r' || c == '\n') {
						while (bufPos > 0 && (c == '\r' || c == '\n')) {
							bufPos--;
							preC = c;
							c = buf.get(bufPos);
						}
					}
					if (!(c == '\r' || c == '\n')) {
						bufPos++;
						if (preC != null && (preC == '\r' || preC == '\n')) {
							bufPos++;
						}
					}
				}
				while (bufPos-- > 0) {
					c = buf.get(bufPos);
					if (c == '\r' || c == '\n') {
						// skip \r\n
						while (bufPos > 0 && (c == '\r' || c == '\n')) {
							c = buf.get(--bufPos);
						}
						// restore cursor
						if (!(c == '\r' || c == '\n'))
							bufPos++;// IS THE NEW Line
						return bufToString();
					}
					baos.write(c);
				}
			}
		}

		private String bufToString() throws UnsupportedEncodingException {
			if (baos.size() == 0) {
				return "";
			}

			byte[] bytes = baos.toByteArray();
			for (int i = 0; i < bytes.length / 2; i++) {
				byte t = bytes[i];
				bytes[i] = bytes[bytes.length - i - 1];
				bytes[bytes.length - i - 1] = t;
			}

			baos.reset();
			if (encoding != null) {
				return new String(bytes, encoding);
			} else {
				return new String(bytes);
			}
		}
	}
}
