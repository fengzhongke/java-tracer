package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.intercepter.ThreadCompressIntercepter;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.MetaVO;
import com.ali.trace.spy.jetty.vo.RecordVO;
import com.ali.trace.spy.jetty.vo.SetVO;
import com.ali.trace.spy.jetty.vo.TraceVO;
import com.ali.trace.spy.util.TreeNode;
import org.apache.commons.lang.math.NumberUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author nkhanlang@163.com
 */
public class TraceHandler implements ITraceHttpHandler {

    private volatile ThreadCompressIntercepter intercepter;
    private final NodePool nodePool = NodePool.getPool();

    @TracerPath(value = "/trace/set", order = 1)
    public DataRet<String> set(PrintWriter writer, @TraceParam("class") String cname,
                       @TraceParam("method") String mname,
                       @TraceParam("size") String sizeStr) throws IOException {
        DataRet<String> ret = null;
        try {
            if (NumberUtils.isDigits(sizeStr)) {
                nodePool.setSize(Integer.valueOf(sizeStr));
            }
            if (cname != null && mname != null) {
                intercepter = new ThreadCompressIntercepter(cname, mname);
                ConfigPool.getPool().setIntercepter(intercepter);
            }else{
                ConfigPool.getPool().delIntercepter();
                intercepter = null;
            }
            ret = new DataRet<String>(true, 0, "set class[" + cname + "]method[" + mname + "]size[" + sizeStr + "]");
        } catch (Exception e) {
            ret = new DataRet<String>(false, -1, "set class[" + cname + "]method[" + mname + "]failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/trace/getSet", order = 1)
    public DataRet<SetVO> getSet() throws IOException {
        DataRet<SetVO> ret = null;
        try {
            String cname = null;
            String mname = null;
            if (intercepter != null) {
                cname = intercepter.getC();
                mname = intercepter.getM();
            }
            long size = nodePool.getSize();
            MetaVO metaVO = new MetaVO(cname, mname);
            ret = new DataRet<SetVO>(true, 0, "get ok");
            ret.setData(new SetVO(metaVO, size));
        } catch (Exception e) {
            ret = new DataRet(false, -1, "getSet failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/trace/list", order = 1)
    public DataRet<List<RecordVO>> list() throws IOException {
        DataRet<List<RecordVO>> ret = null;
        try {
            List<RecordVO> list = new ArrayList<RecordVO>();
            Map<Long, Long> nodes = nodePool.getNodes();
            for (Map.Entry<Long, Long> entry : nodes.entrySet()) {
                long seed = entry.getKey();
                String[] names = TreeNode.getName(entry.getValue());
                list.add(new RecordVO(seed, new MetaVO(names[0], names[1])));
            }
            ret = new DataRet<List<RecordVO>>(true, 0, "get ok");
            ret.setData(list);
        } catch (Exception e) {
            ret = new DataRet(false, -1, "list failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/trace/get.json", order = 1)
    public DataRet<TraceVO> getjson(@TraceParam("id") String id) throws IOException, InterruptedException {
        DataRet<TraceVO> ret = null;
        try {
            Long seed = Long.valueOf(id);
            TreeNode node = nodePool.getNode(seed);
            Map<Long, String[]> metas = node.getMetas();
            ret = new DataRet<TraceVO>(true, 0, "getjson id:[" + id + "]success");
            ret.setData(new TraceVO(node, metas));
        } catch (Exception e) {
            ret = new DataRet(false, -1, "getjson id:[" + id + "]failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/trace/get.xml", order = 1)
    public void get(@TraceParam("id") String id, PrintWriter writer) throws IOException, InterruptedException {
        Long seed = Long.valueOf(id);
        if (intercepter != null) {
            TreeNode node = nodePool.getNode(seed);
            if (node != null) {
                writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
                node.writeFile(writer);
            } else {
                writer.write("no result no record");
            }
        } else {
            writer.write("no result intercepter is null");
        }
    }

    @TracerPath(value = "/trace", order = 1)
    @TraceView
    public String trace(ModelMap map, @TraceParam("id") String id) throws IOException {
        map.put("id", id);
        return "trace";
    }

}
