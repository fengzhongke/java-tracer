package com.ali.trace.spy.intercepter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import com.ali.trace.spy.helper.IFileNameGenerator;
import com.ali.trace.spy.helper.ThreadFileNameGenerator;

/**
 * print XML format stacks
 * 
 * @author hanlang.hl
 *
 */
public abstract class BaseIntercepter implements IIntercepter {
    private final String path;
    private final ThreadLocal<Writer> t_outs = new ThreadLocal<Writer>();
    private IFileNameGenerator nameGenerator;

    public BaseIntercepter(String path) {
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

}
