package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.util.TreeNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * @auther hanlang@mallcai.com
 * @date 2019-08-21 00:52
 */

public class DataRet<T>{
    private final boolean status;
    private final int code;
    private final String msg;
    private T data;

    public DataRet(boolean status, int code, String msg){
        this.status = status;
        this.code = code;
        this.msg = msg;
    }

    public void setData(T data){
        this.data = data;
    }
    public T getData(){
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
    public String toString(){
        return new GsonBuilder().registerTypeAdapter(LinkedHashMap.class, new JsonSerializer<LinkedHashMap> (){
            public JsonElement serialize(LinkedHashMap src, Type typeOfSrc, JsonSerializationContext context) {
                return new Gson().toJsonTree(src.values()).getAsJsonArray();
            }
        }).create().toJson(this);
    }

    public static void main(String[] args){
        long id = TreeNode.getId("com.test.Service", "main");
        TreeNode node = new TreeNode(id);
        node.addSon(TreeNode.getId("com.test.Service", "main1"), 1L);
        node.addSon(TreeNode.getId("com.test.Service1", "main"), 1L);
        node.addSon(TreeNode.getId("com.test.Service1", "main"), 1L);
        node.addSon(TreeNode.getId("com.test.Service1", "main"), 1L);
        node.addSon(TreeNode.getId("com.test.Service", "main"), 1L);
        DataRet<TreeNode> ret = new DataRet<TreeNode>(true, 0, "");
        ret.setData(node);
        System.out.println(ret);

    }
}
