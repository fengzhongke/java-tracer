package com.ali.trace.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

class CrunchifyReverseLineReader {
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
                bufPos = (int)len;
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
                    while (bufPos > 0 && (c == '\r' || c == '\n')) {
                        c = buf.get(--bufPos);
                    }
                    if (!(c == '\r' || c == '\n'))
                        bufPos++;
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