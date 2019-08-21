package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.jetty.ModuleHttpServlet.TracerPath;
import com.ali.trace.spy.xml.XmlNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

public class ClassHandler implements ITraceHttpHandler {

    @TracerPath(value = "/class", order = 10)
    public void thread(PrintWriter writer) throws IOException {
        writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
        ConfigPool pool = ConfigPool.getPool();
        RootNode root = new RootNode();
        for (ClassLoader loader : pool.getLoaders()) {
            String name = "";
            if (loader != null) {
                name = loader.toString().replaceAll("'|<|>|\\$", "_");
            }
            LoaderNode node = root.addLoader(name);
            Map<String, Integer> classNames = pool.getClassNames(loader);
            for (Entry<String, Integer> entry : classNames.entrySet()) {
                String cname = entry.getKey();
                cname = cname.replaceAll("'|<|>|\\$", "_");
                node.addClass(cname, entry.getValue());
            }
        }
        root.write(writer);
    }

    class RootNode extends LoaderNode {
        List<LoaderNode> children = new ArrayList<LoaderNode>();

        RootNode() {
            super("boot");
        }

        int cnt;

        LoaderNode addLoader(String name) {
            LoaderNode node = new LoaderNode(name);
            children.add(node);
            cnt++;
            return node;
        }

        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<loaders ");
            sb.append(" cnt='").append(cnt);
            return sb.append("'>").toString();
        }

        protected String getEnd() {
            return "\r\n</loaders>";
        }

        @Override
        protected Collection<LoaderNode> getChildren() {
            Collections.sort(children, new Comparator<LoaderNode>() {
                public int compare(LoaderNode o1, LoaderNode o2) {
                    return o2.cnt - o1.cnt;
                }
            });
            return children;
        }
    }

    class ClassNode extends LoaderNode {
        Integer type;

        ClassNode(String name, Integer type) {
            super(name);
            this.type = type;
        }

        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<class");
            sb.append(" name='").append(name);
            sb.append("' type='").append(type);
            return sb.append("'>").toString();
        }

        protected String getEnd() {
            return "\r\n</class>";
        }
    }

    class LoaderNode extends XmlNode<LoaderNode> {
        int cnt;
        String name;
        Set<LoaderNode> children = new TreeSet<LoaderNode>(new Comparator<LoaderNode>() {
            public int compare(LoaderNode o1, LoaderNode o2) {
                return o1.name.compareTo(o2.name);
            }

        });

        LoaderNode(String name) {
            this.name = name;
        }

        void addClass(String className, Integer type) {
            cnt++;
            children.add(new ClassNode(className, type));
        }

        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<loader");
            sb.append(" name='").append(name);
            sb.append("' cnt='").append(cnt);
            return sb.append("'>").toString();
        }

        protected String getEnd() {
            return "\r\n</loader>";
        }

        @Override
        protected Collection<LoaderNode> getChildren() {
            return children;
        }

    }
}
