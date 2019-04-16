package com.ali.trace.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class InputStreamClassLoader extends ClassLoader {
    private Map<String, byte[]> classBytes = new HashMap<String, byte[]>();

    public InputStreamClassLoader(InputStream in) {
        super(InputStreamClassLoader.class.getClassLoader().getParent());

        Map<String, Boolean> newClasses = new HashMap<String, Boolean>();
        JarInputStream jarInput = null;
        try {
            jarInput = new JarInputStream(in);
            JarEntry entry = jarInput.getNextJarEntry();
            while (entry != null) {
                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    String className = entryName.replace(".class", "").replace("/", ".");
                    if (!classBytes.containsKey(className)) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        int chunk = 0;
                        byte[] data = new byte[256];
                        while (-1 != (chunk = jarInput.read(data))) {
                            bytes.write(data, 0, chunk);
                        }
                        classBytes.put(className, bytes.toByteArray());
                        newClasses.put(className, true);
                    } else {
                        newClasses.put(className, false);
                    }
                }
                entry = jarInput.getNextJarEntry();
            }
        } catch (Exception e) {
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

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = this.findLoadedClass(name);
        if (clazz == null) {
            byte[] bytes = classBytes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException("class not found : " + name);
            }
            clazz = this.defineClass(name, bytes, 0, bytes.length);
        }
        return clazz;
    }
}
