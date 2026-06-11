package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.MetaVO;
import com.ali.trace.spy.jetty.vo.RecordVO;
import com.ali.trace.spy.jetty.vo.TraceVO;
import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.RootNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * @author nkhanlang@163.com
 */
public class TraceHandler implements ITraceHttpHandler {

    private final NodePool nodePool = NodePool.getPool();


    @TracerPath(value = "/trace", order = 1)
    @TraceView
    public String trace(ModelMap map, @TraceParam("id") String id) throws IOException {
        map.put("id", id);
        map.put("activePage", "index");  // trace detail doesn't have its own nav entry
        return "trace";
    }

    @TracerPath(value = "/trace/get.json", order = 1)
    public DataRet<TraceVO> getjson(@TraceParam("id") String id) throws IOException, InterruptedException {
        DataRet<TraceVO> ret = null;
        try {
            Long seed = Long.valueOf(id);
            RootNode node = nodePool.getNode(seed);
            Map<Long, String[]> metas = node.getNode().getMetas();
            ret = new DataRet<TraceVO>(true, 0, "getjson id:[" + id + "]success");
            ret.setData(new TraceVO(node.getType(), node.getNode(), metas));
        } catch (Exception e) {
            ret = new DataRet(false, -1, "getjson id:[" + id + "]failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/trace/get.xml", order = 1)
    public void get(@TraceParam("id") String id, PrintWriter writer) throws IOException, InterruptedException {
        Long seed = Long.valueOf(id);
        if (ConfigPool.getPool().getInterceptor() != null) {
            RootNode node = nodePool.getNode(seed);
            if (node != null) {
                writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
                node.getNode().writeFile(writer);
            } else {
                writer.write("no result no record");
            }
        } else {
            writer.write("no result interceptor is null");
        }
    }
}
