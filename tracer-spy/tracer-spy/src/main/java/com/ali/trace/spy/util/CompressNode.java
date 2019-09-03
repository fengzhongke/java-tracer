package com.ali.trace.spy.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author nkhanlang@163.com
 */
public class CompressNode extends BaseNode<CompressNode> {

    private long c;
    private LinkedHashMap<Long, CompressNode> s = new LinkedHashMap<Long, CompressNode>();

    public CompressNode(long id) {
        super(id);
    }

    public CompressNode addSon(long id) {
        CompressNode son = s.get(id);
        if (son == null) {
            s.put(id, son = new CompressNode(id));
        }
        son.c ++;
        return son;
    }

    public Collection<CompressNode> getSons() {
        return new ArrayList<CompressNode>(s.values());
    }

    public void writeFile(Writer writer, int depth) throws IOException {
        if (depth > 0) {
            String[] items = getName(i);
            if (items == null) {
                throw new RuntimeException("name not exists ![" + i + "]");
            }
            writer.write("<");
            writer.write(items[1]);
            writer.write(" cnt='" + c + "'");
            writer.write(" rt='" + t + "'");
            writer.write(" c='" + items[0] + "'");
            writer.write(">\r\n");
            for (CompressNode son : s.values()) {
                son.writeFile(writer, depth--);
            }
            writer.write("</");
            writer.write(items[1]);
            writer.write(">\r\n");
        }
    }

    public static void main(String[] args) throws IOException {
        long id = CompressNode.getId("com.test.Service", "main");
        CompressNode node = new CompressNode(id);
        node.addSon(CompressNode.getId("com.test.Service", "main1"));
        node.addSon(CompressNode.getId("com.test.Service1", "main"));
        node.addSon(CompressNode.getId("com.test.Service1", "main"))
                .addSon(CompressNode.getId("com.test.Service1", "main"))
                .addSon(CompressNode.getId("com.test.Service", "main"));
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
        System.out.println("map : " + new Gson().toJson(map));

        System.out.println(new GsonBuilder().registerTypeAdapter(LinkedHashMap.class, new JsonSerializer<LinkedHashMap>() {
            public JsonElement serialize(LinkedHashMap src, Type typeOfSrc, JsonSerializationContext context) {
                if(!src.isEmpty()){
                    return new GsonBuilder().registerTypeAdapter(LinkedHashMap.class, this).create().toJsonTree(src.values());
                }else{
                    return null;
                }
            }
        }).create().toJson(node));

    }

}
