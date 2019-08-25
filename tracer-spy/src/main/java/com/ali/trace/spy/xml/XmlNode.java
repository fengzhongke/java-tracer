package com.ali.trace.spy.xml;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

/**
 * @author nkhanlang@163.com
 */
public abstract class XmlNode<T extends XmlNode<T>> {

    protected abstract Collection<T> getChildren();

    protected abstract String getStart();

    protected abstract String getEnd();

    public void write(Writer writer) {
        try {
            writer.write(getStart());
            for (XmlNode<T> child : getChildren()) {
                child.write(writer);
            }
            writer.write(getEnd());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
