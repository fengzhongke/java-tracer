package com.ali.trace.agent.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * self define classLoader to load jar bytes resources
 * 
 * @author hanlang.hl
 *
 */
public class SpyClassLoader extends ClassLoader {

    private Map<String, byte[]> bytesMap = new HashMap<String, byte[]>();

    /**
     * with parent
     */
    public SpyClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * load input stream
     */
    public void load(InputStream in) {
        JarInputStream jarInput = null;
        try {
            jarInput = new JarInputStream(in);
            JarEntry entry = null;
            while ((entry = jarInput.getNextJarEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int len = 0;
                byte[] data = new byte[256];
                while ((len = jarInput.read(data)) != -1) {
                    bytes.write(data, 0, len);
                }
                bytesMap.put(entry.getName(), bytes.toByteArray());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (jarInput != null) {
                try {
                    jarInput.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    /**
     * load by self first
     */
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = null;
        try {
            clazz = findClass(name);
        } catch (ClassNotFoundException e) {
        }
        if (clazz == null) {
            clazz = super.loadClass(name, resolve);
        }
        return clazz;
    }

    /**
     * define from bytes loaded
     */
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = this.findLoadedClass(name);
        if (clazz == null && name != null) {
            String fileName = name.replace(".", "/") + ".class";
            byte[] bytes = bytesMap.get(fileName);
            if (bytes == null) {
                throw new ClassNotFoundException("class not found : " + name);
            }
            clazz = this.defineClass(name, bytes, 0, bytes.length);
        }
        return clazz;
    }

    @Override
    protected URL findResource(String paramString) {
        byte[] extractedBytes = bytesMap.get(paramString);
        if (extractedBytes != null) {
            try {
                return new URL(null, "bytes:///" + paramString, new Handler(extractedBytes));
            } catch (MalformedURLException e) {
            }
        }
        return null;
    }

    public URL getResource(String name) {
        URL url = findResource(name);
        if (url == null) {
            url = super.getResource(name);
        }
        return url;
    }

    /**
     * self define handler to make byte array into URL
     */
    class Handler extends URLStreamHandler {
        private final byte[] bytes;

        public Handler(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        protected URLConnection openConnection(URL paramURL) throws IOException {
            return new ByteURLConnection(paramURL);
        }

        /**
         * self defined URL connection
         */
        class ByteURLConnection extends URLConnection {
            public ByteURLConnection(URL paramURL) {
                super(paramURL);
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void connect() throws IOException {}
        }
    }
}
