package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.xml.XmlNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author nkhanlang@163.com
 */
public class ThreadHandler implements ITraceHttpHandler {

    @TracerPath(value = "/thread", order = 10)
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
