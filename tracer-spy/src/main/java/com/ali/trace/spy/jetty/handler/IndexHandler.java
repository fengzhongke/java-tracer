package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.NodePool;
import com.ali.trace.spy.interceptor.CommonTreeInterceptor;
import com.ali.trace.spy.interceptor.CompressTreeInterceptor;
import com.ali.trace.spy.interceptor.MethodTreeInterceptor;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.MetaVO;
import com.ali.trace.spy.jetty.vo.RecordVO;
import com.ali.trace.spy.jetty.vo.SetVO;
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
public class IndexHandler implements ITraceHttpHandler {

    private volatile MethodTreeInterceptor interceptor;

    private final NodePool nodePool = NodePool.getPool();

    @TracerPath(value = "/index", order = 1)
    @TraceView
    public String index(PrintWriter writer) throws IOException {
        return "index";
    }

    @TracerPath(value = "/index/set", order = 1)
    public DataRet<String> set(PrintWriter writer, @TraceParam("class") String cname,
        @TraceParam("method") String mname,
        @TraceParam("size") String sizeStr,
        @TraceParam("type") String type) throws IOException {
        DataRet<String> ret = null;
        try {
            if (NumberUtils.isDigits(sizeStr)) {
                nodePool.setSize(Integer.valueOf(sizeStr));
            }
            boolean set = false;
            if (cname != null && mname != null) {
                if ("CompressTreeInterceptor".equalsIgnoreCase(type)) {
                    interceptor = new CompressTreeInterceptor(cname, mname);
                } else {
                    interceptor = new CommonTreeInterceptor(cname, mname);
                }
                set = ConfigPool.getPool().setinterceptor(interceptor);
            } else {
                set = ConfigPool.getPool().delInterceptor();
                interceptor = null;
            }
            ret = new DataRet<String>(set, 0, "set class[" + cname + "]method[" + mname + "]size[" + sizeStr + "]");
        } catch (Exception e) {
            e.printStackTrace();
            ret = new DataRet<String>(false, -1,
                "set class[" + cname + "]method[" + mname + "]failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/index/get", order = 1)
    public DataRet<SetVO> get() throws IOException {
        DataRet<SetVO> ret = null;
        try {
            String cname = null;
            String mname = null;
            String type = null;
            if (interceptor != null) {
                cname = interceptor.getC();
                mname = interceptor.getM();
                type = interceptor.getClass().getSimpleName();
            }
            long size = nodePool.getSize();
            int mode = nodePool.getMode();
            MetaVO metaVO = new MetaVO(cname, mname);
            ret = new DataRet<SetVO>(true, 0, "get ok");
            ret.setData(new SetVO(metaVO, type, size, mode));
        } catch (Exception e) {
            ret = new DataRet(false, -1, "get failed" + e.getMessage());
        }
        return ret;
    }


    @TracerPath(value = "/index/list", order = 1)
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
}
