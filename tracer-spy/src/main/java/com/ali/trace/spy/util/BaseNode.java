package com.ali.trace.spy.util;

import com.google.gson.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author nkhanlang@163.com
 */
public abstract class BaseNode<T extends BaseNode<T>> {

    protected long i;
    protected long t;
    private static AtomicLong seq = new AtomicLong(0);
    private static Map<String, Map<String, Long>> idMap = new HashMap<String, Map<String, Long>>();
    private static Map<Long, String[]> nameMap = new HashMap<Long, String[]>();

    public static long getId(String service, String method) {
        Map<String, Long> cMap = null;
        if ((cMap = idMap.get(service)) == null) {
            synchronized (idMap) {
                if ((cMap = idMap.get(service)) == null) {
                    idMap.put(service, cMap = new HashMap<String, Long>());
                }
            }
        }

        Long id = null;
        if ((id = cMap.get(method)) == null) {
            synchronized (cMap) {
                if ((id = cMap.get(method)) == null) {
                    cMap.put(method, id = seq.incrementAndGet());
                    nameMap.put(id, new String[]{service, method});
                }
            }
        }
        return id;
    }

    public static String[] getName(long id) {
        return nameMap.get(id);
    }


    protected BaseNode(long id) {
        this.i = id;
    }

    public boolean equal(String service, String method) {
        return getId(service, method) == i;
    }

    public String[] getName() {
        return nameMap.get(i);
    }

    public long getId() {
        return i;
    }

    public long getT() {
        return t;
    }

    public void addRt(long rt) {
        this.t += rt;
    }

    public Map<Long, String[]> getMetas() {
        Map<Long, String[]> metas = new HashMap<Long, String[]>();
        Stack<BaseNode<T>> stack = new Stack<BaseNode<T>>();
        stack.push(this);
        while (!stack.isEmpty()) {
            BaseNode<T> node = stack.pop();
            if (!metas.containsKey(node.i)) {
                metas.put(node.i, getName(node.i));
            }
            Collection<T> ss = node.getSons();
            if (!ss.isEmpty()) {
                stack.addAll(ss);
            }
        }
        return metas;
    }

    public void writeFile(Writer writer) throws IOException {
        writeFile(writer, Integer.MAX_VALUE);
    }

    public abstract void writeFile(Writer writer, int depth) throws IOException;

    public abstract BaseNode<T> addSon(long id) ;

    public abstract Collection<T> getSons();

    protected StringBuilder buildInner(StringBuilder builder) {
        return builder;
    }

    public final StringBuilder build(StringBuilder builder) {
        //{"s":[{"i":763,"t":0}],"i":763,"t":0}
        builder.append("{'i':");
        builder.append(i);
        builder.append(",'t':");
        builder.append(t);
        buildInner(builder);
        Collection<T> s = getSons();
        if(!s.isEmpty()){
            builder.append(",'s':[");
            String split = "";
            for(T node : s){
                builder.append(split);
                node.build(builder);
                split = ",";
            }
            builder.append("]");
        }
        builder.append("}");
        return builder;
    }

}
