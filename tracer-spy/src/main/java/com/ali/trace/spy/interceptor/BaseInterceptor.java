package com.ali.trace.spy.interceptor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicReference;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.helper.IFileNameGenerator;
import com.ali.trace.spy.helper.ThreadFileNameGenerator;

/**
 * print XML format stacks
 *
 * @author nkhanlang@163.com
 *
 */
public abstract class BaseInterceptor implements IInterceptor {

    private static final AtomicReference<Object[]> interceptParams = new AtomicReference<>();
    private final String path;
    private final ThreadLocal<Writer> t_outs = new ThreadLocal<Writer>();
    private IFileNameGenerator nameGenerator;

    public BaseInterceptor(String path) {
        if (path != null) {
            this.path = path;
        } else {
            this.path = "/tmp/";
        }
    }

    public void setNameGenerator(IFileNameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    protected void write(String line) throws Exception {
        if (line != null) {
            Writer out = getWriter();
            out.write(line);
            out.flush();
        }
    }

    protected Writer getWriter() throws IOException {
        Writer out = t_outs.get();
        if (out == null) {
            if (nameGenerator == null) {
                synchronized (this) {
                    if (nameGenerator == null) {
                        nameGenerator = new ThreadFileNameGenerator(path);
                    }
                }
            }
            String fName = nameGenerator.getName();
            t_outs.set(out = new BufferedWriter(new FileWriter(fName)));
        }
        return out;
    }

    public static void setParams(Object[] params) {
        if (params != null && params.length > 0) {
            Object[] array = new Object[params.length + 1];
            array[0] = ConfigPool.getPool().getParamCaptureTarget();
            System.arraycopy(params, 0, array, 1, params.length);
            interceptParams.set(array);
        } else {
            interceptParams.set(null);
        }
    }

    public static Object[] getParams() {
        return interceptParams.get();
    }
}
