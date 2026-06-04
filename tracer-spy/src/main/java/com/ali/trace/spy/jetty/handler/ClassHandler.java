package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.ConfigPool.LoaderSet;
import com.ali.trace.spy.interceptor.CommonTreeInterceptor;
import com.ali.trace.spy.interceptor.CompressTreeInterceptor;
import com.ali.trace.spy.jetty.vo.ClassTreeVO;
import com.ali.trace.spy.jetty.vo.ConfigVO;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.LoaderTreeVO;
import com.ali.trace.spy.jetty.vo.MetaVO;
import com.ali.trace.spy.jetty.vo.PackageNodeVO;
import com.ali.trace.spy.jetty.vo.SetVO;
import com.ali.trace.spy.xml.XmlNode;

import org.apache.commons.lang.math.NumberUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author nkhanlang@163.com
 */
public class ClassHandler implements ITraceHttpHandler {

    @TracerPath(value = "/class", order = 1)
    @TraceView
    public String index(PrintWriter writer) throws IOException {
        return "class";
    }


    @TracerPath(value = "/class/set", order = 1)
    public DataRet<String> set(PrintWriter writer, @TraceParam("class") String cname,
        @TraceParam("include") String include,
        @TraceParam("exclude") String exclude) throws IOException {
        DataRet<String> ret = null;
        try {
            ConfigPool.getPool().resetConfig(include, exclude);
            ret = new DataRet(true, 0, "redefine succeed!");
        } catch (Exception e) {
            ret = new DataRet(false, -1, "redefine failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/class/get", order = 1)
    public DataRet<ConfigVO> get() throws IOException {
        DataRet<ConfigVO> ret = null;
        try {
            List<String> config = ConfigPool.getPool().getConfig();
            ConfigVO configVO = new ConfigVO(config.get(0), config.get(1));
            ret = new DataRet<ConfigVO>(true, 0, "get ok");
            ret.setData(configVO);
        } catch (Exception e) {
            ret = new DataRet(false, -1, "get failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/class/redefine", order = 10)
    public DataRet<String> redefineType(@TraceParam("type")String type) throws IOException {
        DataRet<String> ret = null;
        try {
            ConfigPool.getPool().redefine(Integer.valueOf(type));
            ret = new DataRet(true, 0, "redefine type failed");
        } catch (Exception e) {
            ret = new DataRet(false, -1, "redefine type failed" + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/class/get.xml", order = 10)
    public void thread(PrintWriter writer) throws IOException {
        writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
        ConfigPool pool = ConfigPool.getPool();
        RootNode root = new RootNode();
        for (LoaderSet loader : pool.getAllLoadedClasses()) {
            String name = "";
            if (loader != null) {
                name = loader.toString().replaceAll("'|<|>|\\$", "_");
            }
            LoaderNode node = root.addLoader(name);
            Map<String, Integer> classNames = loader.getClassNames();
            for (Entry<String, Integer> entry : classNames.entrySet()) {
                String cname = entry.getKey();
                cname = cname.replaceAll("'|<|>|\\$", "_");
                node.addClass(cname, entry.getValue(), loader.getId());
            }
        }
        root.write(writer);
    }

    @TracerPath(value = "/class/tree", order = 11)
    @TraceView
    public String tree(PrintWriter writer) throws IOException {
        return "class-tree";
    }

    @TracerPath(value = "/class/tree.json", order = 12)
    public DataRet<List<PackageNodeVO>> treeJson(PrintWriter writer) throws IOException {
        DataRet<List<PackageNodeVO>> ret = new DataRet<List<PackageNodeVO>>(true, 0, "get ok");
        ConfigPool pool = ConfigPool.getPool();

        // Build package tree for each loader - use getAllLoadedClasses for full class list
        List<PackageNodeVO> loaderTrees = new ArrayList<PackageNodeVO>();
        for (LoaderSet loader : pool.getAllLoadedClasses()) {
            PackageNodeVO loaderRoot = new PackageNodeVO();
            loaderRoot.setName(loader.toString());
            loaderRoot.setFullPath(loader.toString());

            // Build package hierarchy for all classes in this loader
            PackageNodeVO packageRoot = new PackageNodeVO("root", "");
            for (Entry<String, Integer> entry : loader.getClassNames().entrySet()) {
                String className = entry.getKey();
                int type = entry.getValue();
                addClassToPackageTree(packageRoot, className, type, loader.getId());
            }

            // Calculate totals and sort children
            packageRoot.calculateTotals();
            sortPackageChildren(packageRoot);

            // Add package children to loader root
            loaderRoot.setChildren(packageRoot.getChildren());
            loaderRoot.setTotalCount(packageRoot.getTotalCount());
            loaderRoot.setWovenCount(packageRoot.getWovenCount());
            loaderTrees.add(loaderRoot);
        }

        // Sort loaders by total count
        Collections.sort(loaderTrees, new Comparator<PackageNodeVO>() {
            public int compare(PackageNodeVO o1, PackageNodeVO o2) {
                return o2.getTotalCount() - o1.getTotalCount();
            }
        });

        ret.setData(loaderTrees);
        return ret;
    }

    /**
     * Add a class to the package tree hierarchy
     */
    private void addClassToPackageTree(PackageNodeVO root, String className, int type, int loaderId) {
        // Split class name into package parts: com/english/api/controller/GlobalExceptionHandler
        String[] parts = className.split("/");
        if (parts.length == 0) return;

        PackageNodeVO current = root;
        StringBuilder fullPath = new StringBuilder();

        // Walk through package levels (except last part which is the class name)
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (fullPath.length() > 0) fullPath.append("/");
            fullPath.append(part);

            PackageNodeVO child = current.getChild(part);
            if (child == null) {
                child = new PackageNodeVO(part, fullPath.toString());
                current.addChild(child);
            }
            current = child;
        }

        // Add the class as a leaf node with wovenCount and totalCount
        String classSimpleName = parts[parts.length - 1];
        PackageNodeVO classNode = new PackageNodeVO(classSimpleName, className);
        classNode.setClass(true);
        classNode.setType(type);
        classNode.setLoaderId(loaderId);
        // Each class: totalCount=1, wovenCount=1 if type=1 else 0
        classNode.setTotalCount(1);
        classNode.setWovenCount(type == 1 ? 1 : 0);
        current.addChild(classNode);
    }

    /**
     * Sort package children alphabetically
     */
    private void sortPackageChildren(PackageNodeVO node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) return;

        Collections.sort(node.getChildren(), new Comparator<PackageNodeVO>() {
            public int compare(PackageNodeVO o1, PackageNodeVO o2) {
                // Packages first, then classes
                if (o1.isClass() != o2.isClass()) {
                    return o1.isClass() ? 1 : -1;
                }
                return o1.getName().compareTo(o2.getName());
            }
        });

        // Recursively sort children
        for (PackageNodeVO child : node.getChildren()) {
            sortPackageChildren(child);
        }
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

        @Override
        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<loaders ");
            sb.append(" cnt='").append(cnt);
            return sb.append("'>").toString();
        }

        @Override
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
        int type;
        int loaderId;

        ClassNode(String name, int type, int loaderId) {
            super(name);
            this.type = type;
            this.loaderId = loaderId;
        }

        @Override
        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<class");
            sb.append(" name='").append(name);
            sb.append("' type='").append(type);
            sb.append("' lid='").append(loaderId);
            return sb.append("'>").toString();
        }

        @Override
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

        void addClass(String className, int type, int loaderId) {
            cnt++;
            children.add(new ClassNode(className, type, loaderId));
        }

        @Override
        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<loader");
            sb.append(" name='").append(name);
            sb.append("' cnt='").append(cnt);
            return sb.append("'>").toString();
        }

        @Override
        protected String getEnd() {
            return "\r\n</loader>";
        }

        @Override
        protected Collection<LoaderNode> getChildren() {
            return children;
        }

    }
}
