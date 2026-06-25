package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.StackTraceVO;
import com.ali.trace.spy.jetty.vo.ThreadNodeVO;
import com.ali.trace.spy.xml.XmlNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author nkhanlang@163.com
 */
public class ThreadHandler implements ITraceHttpHandler {

    private static final ConcurrentHashMap<Long, ThreadStackSampler> samplerMap = new ConcurrentHashMap<Long, ThreadStackSampler>();
    private static final AtomicLong samplerIdSeq = new AtomicLong(0);

    @TracerPath(value = "/thread", order = 1)
    @TraceView
    public String index(ModelMap map) throws IOException {
        map.put("activePage", "thread");
        return "thread-tree";
    }

    @TracerPath(value = "/thread/tree.json", order = 2)
    public DataRet<List<ThreadNodeVO>> threadJson(PrintWriter writer) throws IOException {
        DataRet<List<ThreadNodeVO>> ret = new DataRet<List<ThreadNodeVO>>(true, 0, "get ok");
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();

        // Build merged tree structure (like get.xml)
        ThreadNodeVO root = new ThreadNodeVO();
        root.setName("root");

        for (Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stacks = entry.getValue();

            // Build stack trace tree from bottom to top, merging same paths
            ThreadNodeVO current = root;

            for (int i = stacks.length - 1; i >= 0; i--) {
                StackTraceElement stack = stacks[i];
                String key = stack.getClassName() + "#" + stack.getMethodName() + "#" + stack.getLineNumber();

                ThreadNodeVO frameNode = current.getChild(key);
                if (frameNode == null) {
                    frameNode = new ThreadNodeVO();
                    frameNode.setName(stack.getMethodName());
                    frameNode.setFullName(stack.getClassName() + "." + stack.getMethodName());
                    frameNode.setClassName(stack.getClassName());
                    frameNode.setLineNumber(stack.getLineNumber());
                    frameNode.setIsFrame(true);
                    current.addChildByKey(key, frameNode);
                }
                frameNode.incCount();
                current = frameNode;
            }

            // Add thread info at the leaf
            if (current != null && current != root) {
                ThreadNodeVO threadLeaf = new ThreadNodeVO();
                threadLeaf.setName(thread.getName());
                threadLeaf.setId(thread.getId());
                threadLeaf.setState(thread.getState().name());
                threadLeaf.setIsThread(true);
                current.addChild(threadLeaf);
            }
        }

        // Get root children and sort by count
        List<ThreadNodeVO> result = root.getChildren();
        sortAndClean(result);
        ret.setData(result);
        return ret;
    }

    @TracerPath(value = "/thread/detail.json", order = 3)
    public DataRet<List<StackTraceVO>> threadDetail(@TraceParam("id") String idStr) throws IOException {
        DataRet<List<StackTraceVO>> ret;
        long tid;
        try {
            tid = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return new DataRet<List<StackTraceVO>>(false, 400, "invalid thread id: " + idStr);
        }

        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        for (Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            Thread thread = entry.getKey();
            if (thread.getId() == tid) {
                StackTraceElement[] stacks = thread.getStackTrace();
                List<StackTraceVO> result = new ArrayList<StackTraceVO>();
                // Top-to-bottom order (most recent call first)
                for (int i = 0; i < stacks.length; i++) {
                    result.add(new StackTraceVO(stacks[i]));
                }
                ret = new DataRet<List<StackTraceVO>>(true, 0, "ok");
                // Add thread info to the data
                ret.setData(result);
                return ret;
            }
        }
        return new DataRet<List<StackTraceVO>>(false, 404, "thread not found: " + tid);
    }

    @TracerPath(value = "/thread/sampler/start.json", order = 3)
    public DataRet<String> samplerStart(@TraceParam("ids") String idsStr) throws IOException {
        Set<Long> threadIds = new HashSet<Long>();
        if (idsStr != null && idsStr.length() > 0) {
            String[] parts = idsStr.split(",");
            for (String part : parts) {
                try {
                    threadIds.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException e) {
                    // skip invalid IDs
                }
            }
        }

        if (threadIds.isEmpty()) {
            return new DataRet<String>(false, 400, "no valid thread ids provided");
        }

        long samplerId = samplerIdSeq.incrementAndGet();
        final ThreadStackSampler newSampler = new ThreadStackSampler(threadIds);
        // Self-cleaning: when sampler thread finishes, remove from map so the object can be GC'd
        newSampler.setOnComplete(new Runnable() {
            public void run() {
                samplerMap.remove(samplerId, newSampler);
            }
        });
        samplerMap.put(samplerId, newSampler);
        newSampler.start();
        return new DataRet<String>(true, 0, "" + samplerId);
    }

    @TracerPath(value = "/thread/sampler/result.json", order = 4)
    public DataRet<String> samplerResult(@TraceParam("sid") String sidStr, @TraceParam("ttl") String ttlStr, PrintWriter writer) throws IOException {
        long sid;
        try {
            sid = Long.parseLong(sidStr);
        } catch (NumberFormatException e) {
            return new DataRet<String>(false, 400, "invalid sampler id");
        }

        ThreadStackSampler sampler = samplerMap.get(sid);
        if (sampler == null) {
            return new DataRet<String>(false, 404, "sampler not found or expired");
        }

        // Renew expiry with custom TTL (default 10 seconds)
        int ttl = 10;
        if (ttlStr != null && ttlStr.length() > 0) {
            try {
                ttl = Integer.parseInt(ttlStr.trim());
                if (ttl < 1) ttl = 10;
            } catch (NumberFormatException e) {
                ttl = 10;
            }
        }
        sampler.renewExpiry(ttl);

        // Get result and build JSON response
        ThreadStackSampler.SamplerResult result = sampler.getResult();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":true,\"code\":0,\"msg\":\"ok\",\"data\":");
        sb.append(result.toJson());
        sb.append("}");
        writer.write(sb.toString());
        return null;
    }

    @TracerPath(value = "/thread/sampler/stop.json", order = 5)
    public DataRet<String> samplerStop(@TraceParam("sid") String sidStr) throws IOException {
        long sid;
        try {
            sid = Long.parseLong(sidStr);
        } catch (NumberFormatException e) {
            return new DataRet<String>(false, 400, "invalid sampler id");
        }

        ThreadStackSampler sampler = samplerMap.remove(sid);
        if (sampler != null) {
            sampler.stop();
        }
        return new DataRet<String>(true, 0, "sampler stopped");
    }

    private void sortAndClean(List<ThreadNodeVO> nodes) {
        Collections.sort(nodes, new Comparator<ThreadNodeVO>() {
            public int compare(ThreadNodeVO o1, ThreadNodeVO o2) {
                return o2.getCount() - o1.getCount();
            }
        });
        for (ThreadNodeVO node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortAndClean(node.getChildren());
            }
        }
    }

    @TracerPath(value = "/thread/get.xml", order = 10)
    public void thread(PrintWriter writer) throws IOException {
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
        Node root = new RootNode(stackTraces.size());
        for (Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stacks = entry.getValue();
            int size = stacks.length;
            Node node = root;
            for (size = stacks.length - 1; size >= 0; size--) {
                StackTraceElement stackTrace = stacks[size];
                String mname = stackTrace.getMethodName().replaceAll("'|<|>|\\$", "_");
                String cname = stackTrace.getClassName().replaceAll("'|<|>|\\$", "_");
                int line = stackTrace.getLineNumber();
                node = node.addSon(cname, mname, line);
            }
            long id = thread.getId();
            String name = thread.getName();
            if (name != null) {
                name = name.replaceAll("'|<|>|\\$", "_");
            }
            node.addSon(String.valueOf(id), new NameNode(name, id));
        }
        root.write(writer);
    }

    class RootNode extends Node {
        RootNode(int cnt) {
            this.cnt = cnt;
        }

        protected String getStart() {
            StringBuilder sb = new StringBuilder("<threads cnt='").append(cnt);
            return sb.append("'>").toString();
        }

        protected String getEnd() {
            return "</threads>";
        }
    }

    class NameNode extends Node {
        String name;
        long id;

        NameNode(String name, long id) {
            this.name = name;
            this.id = id;
        }

        protected String getStart() {
            StringBuilder sb = new StringBuilder("<thread name='");
            sb.append(name).append("' tid='").append(id);
            return sb.append("'/>").toString();
        }

        protected String getEnd() {
            return "";
        }
    }

    class Node extends XmlNode<Node> {
        String cname;
        String mname;
        int line;
        int cnt;
        Map<String, Node> children = new LinkedHashMap<String, Node>();

        private Node() {
        }

        private Node(String cname, String mname, int line) {
            this.cname = cname;
            this.mname = mname;
            this.line = line;
        }

        Node addSon(String cname, String mname, int line) {
            String key = cname + "#" + mname + "#" + line;
            Node son = children.get(key);
            if (son == null) {
                children.put(key, son = new Node(cname, mname, line));
            }
            son.cnt++;
            return son;
        }

        Node addSon(String key, Node son) {
            children.put(key, son);
            return son;
        }

        protected String getStart() {
            StringBuilder sb = new StringBuilder("<").append(mname);
            sb.append(" class='").append(cname);
            sb.append("' line='").append(line);
            sb.append("' cnt='").append(cnt);
            return sb.append("'>").toString();
        }

        protected String getEnd() {
            StringBuilder sb = new StringBuilder("</").append(mname);
            return sb.append(">").toString();
        }

        protected Collection<Node> getChildren() {
            List<Node> sons = new ArrayList<Node>(children.values());
            Collections.sort(sons, new Comparator<Node>() {
                public int compare(Node o1, Node o2) {
                    return o2.cnt - o1.cnt;
                }
            });
            return sons;
        }

    }
}
