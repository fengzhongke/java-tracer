package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.jetty.vo.DataRet;

import java.io.PrintWriter;

/**
 * Handler for agent unload feature.
 * Provides a confirmation page and an exec endpoint that
 * calls ConfigPool.unload() which delegates to Premain.unload()
 * via cross-classloader reflection.
 *
 * @author nkhanlang@163.com
 */
public class UnloadHandler implements ITraceHttpHandler {

    @TracerPath(value = "/unload", order = 1)
    @TraceView
    public String index(ModelMap map) throws Exception {
        map.put("activePage", "unload");
        return "unload";
    }

    @TracerPath(value = "/unload/exec", order = 1)
    public DataRet<String> exec(PrintWriter writer) throws Exception {
        DataRet<String> ret = null;
        try {
            boolean result = ConfigPool.getPool().unload();
            if (result) {
                ret = new DataRet<String>(true, 0, "Agent unloaded successfully");
            } else {
                ret = new DataRet<String>(false, -1, "Agent unload failed");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            ret = new DataRet<String>(false, -1, "Unload error: " + e.getMessage());
        }
        return ret;
    }
}