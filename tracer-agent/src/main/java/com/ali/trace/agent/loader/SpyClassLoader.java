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

public class SpyClassLoader extends ClassLoader {

    private Map<String, byte[]> itemBytes = new HashMap<String, byte[]>();
    private Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();

    public SpyClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void loadSource(InputStream in) {
        JarInputStream jarInput = null;
        try {
            jarInput = new JarInputStream(in);
            JarEntry entry = null;
            while ((entry = jarInput.getNextJarEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int chunk = 0;
                byte[] data = new byte[256];
                while (-1 != (chunk = jarInput.read(data))) {
                    bytes.write(data, 0, chunk);
                }
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    name = name.replace(".class", "").replace("/", ".");
                }
                itemBytes.put(name, bytes.toByteArray());
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

    public void loadClasses(Map<String, Boolean> newClasses) {
        for (String className : newClasses.keySet()) {
            if (newClasses.get(className)) {
                try {
                    Class<?> clazz = this.loadClass(className);
                    classMap.put(className, clazz);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void load() {
        for (String className : itemBytes.keySet()) {
            try {
                Class<?> clazz = this.loadClass(className);
                classMap.put(className, clazz);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = null;
        try {
            clazz = findClass(name);
        } catch (ClassNotFoundException e) {
            clazz = super.loadClass(name, resolve);
        }
        if (clazz == null) {
            clazz = super.loadClass(name, resolve);
        }
        return clazz;
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = this.findLoadedClass(name);
        if (clazz == null) {
            byte[] bytes = itemBytes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException("class not found : " + name);
            }
            clazz = this.defineClass(name, bytes, 0, bytes.length);
        }
        return clazz;
    }

    @Override
    protected URL findResource(String paramString) {
        byte[] extractedBytes = itemBytes.get(paramString);
        if (extractedBytes != null) {
            try {
                return new URL(null, "bytes:///" + paramString, new Handler(extractedBytes, paramString));
            } catch (MalformedURLException e) {
                // Do nothing
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

    class Handler extends URLStreamHandler {
        private byte[] byteContent = null;
        private String resourceName = null;

        /**
         * @param byteContent
         * @param resourceName
         */
        public Handler(byte[] byteContent, String resourceName) {
            this.byteContent = byteContent;
            this.resourceName = resourceName;
        }

        public void setByteContent(byte[] byteContent, String resourceName) {
            this.byteContent = byteContent;
            this.resourceName = resourceName;
        }

        @Override
        protected URLConnection openConnection(URL paramURL) throws IOException {
            if (byteContent == null || resourceName == null)
                throw new UnsupportedOperationException(
                    "This handler only support to be created with byte array in constructor");

            // Resource not match
            if (!paramURL.getFile().endsWith(resourceName))
                throw new UnsupportedOperationException("URL file (" + paramURL.getFile()
                    + ") name does not match with assigned resource name: " + resourceName);

            ByteURLConnection byteURLConnection = new ByteURLConnection(paramURL, byteContent);

            return byteURLConnection;
        }

    }

    // Define Custom ByteURLConnection
    class ByteURLConnection extends URLConnection {
        private byte[] byteContent = null;
        private ByteArrayInputStream byteInStream = null;

        protected ByteURLConnection(URL paramURL) {
            super(paramURL);
        }

        /**
         * @param paramURL
         * @param byteContent
         */
        public ByteURLConnection(URL paramURL, byte[] byteContent) {
            super(paramURL);
            this.byteContent = byteContent;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (byteInStream == null)
                connect();

            return byteInStream;
        }

        @Override
        public void connect() throws IOException {
            if (byteContent == null)
                throw new IOException("This handler only support to be created with byte array in constructor");

            byteInStream = new ByteArrayInputStream(byteContent);
        }
    }
}
