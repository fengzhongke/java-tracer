package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.util.CompressNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;

/**
 * @author nkhanlang@163.com
 * @date 2019-08-21 00:52
 */

public class DataRet<T> {
    private final boolean status;
    private final int code;
    private final String msg;
    private T data;

    public DataRet(boolean status, int code, String msg) {
        this.status = status;
        this.code = code;
        this.msg = msg;
    }

    public void setData(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }

    public boolean isStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return gson.toJson(this);
    }

    private static Gson gson = new GsonBuilder().registerTypeAdapter(LinkedHashMap.class, new JsonSerializer<LinkedHashMap>() {
        public JsonElement serialize(LinkedHashMap src, Type typeOfSrc, JsonSerializationContext context) {
            return !src.isEmpty() ? gson.toJsonTree(src.values()): JsonNull.INSTANCE;
        }
    }).create();


    public static void main(String[] args) {
        long id = CompressNode.getId("com.test.Service", "main");
        CompressNode node = new CompressNode(id);
        node.addSon(CompressNode.getId("com.test.Service", "main1"));
        node.addSon(CompressNode.getId("com.test.Service1", "main"));
        node.addSon(CompressNode.getId("com.test.Service1", "main"))
                .addSon(CompressNode.getId("com.test.Service1", "main"))
                .addSon(CompressNode.getId("com.test.Service", "main"));
        DataRet<CompressNode> ret = new DataRet<CompressNode>(true, 0, "");
        ret.setData(node);


        System.out.println(gson.toJson(ret));

        System.out.println(ret);

    }
}
