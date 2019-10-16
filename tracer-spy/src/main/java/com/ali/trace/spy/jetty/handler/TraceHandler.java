package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.intercepter.MethodTreeIntercepter;
import com.ali.trace.spy.intercepter.CommonTreeIntercepter;
import com.ali.trace.spy.intercepter.CompressTreeIntercepter;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.MetaVO;
import com.ali.trace.spy.jetty.vo.RecordVO;
import com.ali.trace.spy.jetty.vo.SetVO;
import com.ali.trace.spy.jetty.vo.TraceVO;
import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.RootNode;
import org.apache.commons.lang.math.NumberUtils;

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

    private volatile MethodTreeIntercepter intercepter;
    private final NodePool nodePool = NodePool.getPool();

    @TracerPath(value = "/trace/set", order = 1)
    public DataRet<String> set(PrintWriter writer, @TraceParam("class") String cname,
                       @TraceParam("method") String mname,
                               @TraceParam("size") String sizeStr,
                               @TraceParam("type") String type) throws IOException {
        DataRet<String> ret = null;
        try {
            if (NumberUtils.isDigits(sizeStr)) {
                nodePool.setSize(Integer.valueOf(sizeStr));
            }
            if (cname != null && mname != null) {
                if("CompressTreeIntercepter".equalsIgnoreCase(type)){
                    intercepter = new CompressTreeIntercepter(cname, mname);
                }else {
                    intercepter = new CommonTreeIntercepter(cname, mname);
                }
                ConfigPool.getPool().setIntercepter(intercepter);
            }else{
                ConfigPool.getPool().delIntercepter();
                intercepter = null;
            }
            ret = new DataRet<String>(true, 0, "set class[" + cname + "]method[" + mname + "]size[" + sizeStr + "]");
        } catch (Exception e) {
            e.printStackTrace();
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
            String type = null;
            if (intercepter != null) {
                cname = intercepter.getC();
                mname = intercepter.getM();
                type = intercepter.getClass().getSimpleName();
            }
            long size = nodePool.getSize();
            int mode = nodePool.getMode();
            MetaVO metaVO = new MetaVO(cname, mname);
            ret = new DataRet<SetVO>(true, 0, "get ok");
            ret.setData(new SetVO(metaVO, type, size, mode));
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
            Map<Long, RootNode> nodes = nodePool.getNodes();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
            Calendar cal = Calendar.getInstance();
            for (Map.Entry<Long, RootNode> entry : nodes.entrySet()) {
                long seed = entry.getKey();
                RootNode root = entry.getValue();
                String[] names = BaseNode.getName(root.getNode().getId());
                cal.setTimeInMillis(root.getStart());
                String time = sdf.format(cal.getTime());
                long rt = root.getNode().getT();
                list.add(new RecordVO(seed, root.getType(), new MetaVO(names[0], names[1]), time, rt));
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
        if (intercepter != null) {
            RootNode node = nodePool.getNode(seed);
            if (node != null) {
                writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
                node.getNode().writeFile(writer);
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
