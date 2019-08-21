package com.ali.trace.spy.util;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicLong;

public class TreeNode {

    private long i;
    private long c;
    private long t;
    private LinkedHashMap<Long, TreeNode> s = new LinkedHashMap<Long, TreeNode>();
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
                    nameMap.put(id, new String[] {service, method});
                }
            }
        }
        return id;
    }

    public static String[] getName(long id){
        return nameMap.get(id);
    }

    public TreeNode(long id) {
        this.i = id;
    }

    public TreeNode addSon(long id, long cnt) {
        TreeNode son = s.get(id);
        if (son == null) {
            s.put(id, son = new TreeNode(id));
        }
        son.c += cnt;
        return son;
    }

    public void addRt(long rt) {
        this.t += rt;
    }

    public boolean equal(String service, String method) {
        return getId(service, method) == i;
    }

    public String[] getName() {
        return nameMap.get(i);
    }

    public long getId(){
        return i;
    }

    public void writeFile(Writer writer) throws IOException {
        String[] items = nameMap.get(i);
        if (items == null) {
            throw new RuntimeException("name not exists ![" + i + "]");
        }
        writer.write("<");
        writer.write(items[1]);
        writer.write(" cnt='" + c + "'");
        writer.write(" rt='" + t + "'");
        writer.write(" c='" + items[0] + "'");
        writer.write(">\r\n");
        for (TreeNode son : s.values()) {
            son.writeFile(writer);
        }
        writer.write("</");
        writer.write(items[1]);
        writer.write(">\r\n");
    }

    public Map<Long, String[]> getMetas(){
        Map<Long, String[]> metas = new HashMap<Long, String[]>();
        Stack<TreeNode> stack = new Stack<TreeNode>();
        stack.push(this);
        while(!stack.isEmpty()){
            TreeNode node = stack.pop();
            if(!metas.containsKey(node.i)){
                metas.put(node.i, getName(node.i));
            }
            if(!node.s.isEmpty()){
                stack.addAll(node.s.values());
            }
        }
        return metas;
    }

    public void writeFile(Writer writer, int depth) throws IOException {
        if(depth > 0) {
            String[] items = nameMap.get(i);
            if (items == null) {
                throw new RuntimeException("name not exists ![" + i + "]");
            }
            writer.write("<");
            writer.write(items[1]);
            writer.write(" cnt='" + c + "'");
            writer.write(" rt='" + t + "'");
            writer.write(" c='" + items[0] + "'");
            writer.write(">\r\n");
            for (TreeNode son : s.values()) {
                son.writeFile(writer, depth--);
            }
            writer.write("</");
            writer.write(items[1]);
            writer.write(">\r\n");
        }
    }

    public static void main(String[] args) throws IOException {
        long id = TreeNode.getId("com.test.Service", "main");
        TreeNode node = new TreeNode(id);
        node.addSon(TreeNode.getId("com.test.Service", "main1"), 1L);
        node.addSon(TreeNode.getId("com.test.Service1", "main"), 1L);
        node.addSon(TreeNode.getId("com.test.Service1", "main"), 1L);
        node.addSon(TreeNode.getId("com.test.Service1", "main"), 1L);
        node.addSon(TreeNode.getId("com.test.Service", "main"), 1L);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("/tmp/test.xml"));
            node.writeFile(writer);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        System.out.println(new Gson().toJson(node));

        Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();
        map.put(1, 12);
        map.put(3, 12);
        map.put(4, 12);
        map.put(6, 12);
        map.put(2, 12);
        System.out.println(new Gson().toJson(map));

    }

}
