package com.ali.trace.spy.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TreeNode {

    private long id;
    private long cnt;
    private long totalRt;
    private Map<Long, TreeNode> sons = new LinkedHashMap<Long, TreeNode>();
    private static Map<String, Long> idMap = new HashMap<String, Long>();
    private static Map<Long, String> nameMap = new HashMap<Long, String>();

    public static long getId(String service, String method) {
        Long id = null;
        String name = service + "#" + method;
        if ((id = idMap.get(name)) == null) {
            synchronized (idMap) {
                if ((id = idMap.get(name)) == null) {
                    idMap.put(name, id = idMap.size() + 1L);
                    nameMap.put(id, name);
                }
            }
        }
        return id;
    }

    public TreeNode(long id) {
        this.id = id;
    }

    public TreeNode addSon(long id, long cnt) {
        TreeNode son = sons.get(id);
        if (son == null) {
            sons.put(id, son = new TreeNode(id));
        }
        son.cnt += cnt;
        return son;
    }

    public void addRt(long rt) {
        this.totalRt += rt;
    }

    public long getCnt() {
        return cnt;
    }

    public long getTotalRt() {
        return totalRt;
    }

    public boolean equal(String service, String method) {
        return getId(service, method) == id;
    }

    public String getName() {
        return nameMap.get(id);
    }

    public void writeFile(Writer writer) throws IOException {
        String name = nameMap.get(id);
        if (name == null) {
            throw new RuntimeException("name not exists ![" + id + "]");
        }
        String[] items = name.split("#");
        writer.write("<");
        writer.write(items[1]);
        writer.write(" cnt='" + cnt + "'");
        writer.write(" rt='" + totalRt + "'");
        writer.write(" c='" + items[0] + "'");
        writer.write(">\r\n");
        for (TreeNode son : sons.values()) {
            son.writeFile(writer);
        }
        writer.write("</");
        writer.write(items[1]);
        writer.write(">\r\n");
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

    }

}
