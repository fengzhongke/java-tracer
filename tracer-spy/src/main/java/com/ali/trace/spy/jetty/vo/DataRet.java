package com.ali.trace.spy.jetty.vo;

import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.CommonNode;
import com.ali.trace.spy.util.CompressNode;

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
        try {
            //return gson.toJson(this);
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"status\":").append(status);
            sb.append(",\"code\":").append(code);
            sb.append(",\"msg\":\"").append(msg).append("\"");
            sb.append(",\"data\":").append(data);

            sb.append("}");
            return sb.toString();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return "{'error':'e'}";
    }

    // private static Gson gson = new GsonBuilder()/*.registerTypeAdapter(BaseNode.class, new JsonSerializer<BaseNode>() {
    //     public JsonElement serialize(BaseNode src, Type typeOfSrc, JsonSerializationContext context) {
    //         return gson.toJsonTree(src.build(new StringBuilder()));
    //     }
    // })*/.registerTypeAdapter(LinkedHashMap.class, new JsonSerializer<LinkedHashMap>() {
    //     public JsonElement serialize(LinkedHashMap src, Type typeOfSrc, JsonSerializationContext context) {
    //         return !src.isEmpty() ? gson.toJsonTree(src.values()): JsonNull.INSTANCE;
    //     }
    // }).registerTypeAdapter(List.class, new JsonSerializer<List>() {
    //     public JsonElement serialize(List src, Type typeOfSrc, JsonSerializationContext context) {
    //         return !src.isEmpty() ? gson.toJsonTree(src): JsonNull.INSTANCE;
    //     }
    // }).create();

    public static void main(String[] args) {

        DataRet ret = new DataRet(true, 0, "");
        long id = BaseNode.getId("com.test.Service", "main");

        CompressNode node = new CompressNode(id);
        node.addSon(BaseNode.getId("com.test.Service", "main1"));
        node.addSon(BaseNode.getId("com.test.Service1", "main"));
        node.addSon(BaseNode.getId("com.test.Service1", "main"))
            .addSon(BaseNode.getId("com.test.Service1", "main"));

        ret.setData(node);
        // System.out.println(gson.toJson(ret));
        System.out.println(ret);

        CommonNode node1 = new CommonNode(id);
        node1.addSon(BaseNode.getId("com.test.Service", "main1"));
        node1.addSon(BaseNode.getId("com.test.Service1", "main"));
        node1.addSon(BaseNode.getId("com.test.Service1", "main"))
            .addSon(BaseNode.getId("com.test.Service1", "main"));

        ret.setData(node1);
        // System.out.println(gson.toJson(ret));
        System.out.println(ret);

    }
}
