package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.ConfigPool.LoaderSet;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.PackageNodeVO;
import com.ali.trace.spy.jetty.vo.PackageRootVO;
import com.ali.trace.spy.xml.XmlNode;

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

/**
 * Handler for package-level woven class statistics
 */
public class PackageHandler implements ITraceHttpHandler {

    @TracerPath(value = "/package", order = 1)
    @TraceView
    public String index(ModelMap map) throws IOException {
        map.put("activePage", "package");
        return "package";
    }

    @TracerPath(value = "/package/get.json", order = 10)
    public DataRet<PackageRootVO> getJson() throws IOException {
        DataRet<PackageRootVO> ret;
        try {
            PackageRootVO rootVO = buildPackageTree();
            ret = new DataRet<PackageRootVO>(true, 0, "get ok");
            ret.setData(rootVO);
        } catch (Exception e) {
            ret = new DataRet(false, -1, "get failed: " + e.getMessage());
        }
        return ret;
    }

    @TracerPath(value = "/package/get.xml", order = 10)
    public void getXml(PrintWriter writer) throws IOException {
        writer.write("<?xml version='1.0' encoding='UTF-8' ?>");
        PackageRootVO rootVO = buildPackageTree();
        PackageXmlRoot xmlRoot = new PackageXmlRoot(rootVO);
        xmlRoot.write(writer);
    }

    private PackageRootVO buildPackageTree() {
        PackageRootVO rootVO = new PackageRootVO();
        ConfigPool pool = ConfigPool.getPool();
        List<LoaderSet> loaderSets = pool.getAllLoadedClasses();

        rootVO.setLoaderCount(loaderSets.size());

        Map<String, PackageNodeVO> rootPackages = new HashMap<String, PackageNodeVO>();
        int totalClasses = 0;
        int totalWoven = 0;

        for (LoaderSet loaderSet : loaderSets) {
            Map<String, Integer> classNames = loaderSet.getClassNames();
            for (Entry<String, Integer> entry : classNames.entrySet()) {
                String className = entry.getKey();
                Integer type = entry.getValue();
                totalClasses++;

                if (type == 1) {
                    totalWoven++;
                }

                String packageName = extractPackage(className);
                if (packageName == null || packageName.isEmpty()) {
                    continue;
                }

                String[] segments = packageName.split("/");
                PackageNodeVO current = null;
                StringBuilder pathBuilder = new StringBuilder();

                for (int i = 0; i < segments.length; i++) {
                    String segment = segments[i];
                    if (pathBuilder.length() > 0) {
                        pathBuilder.append("/");
                    }
                    pathBuilder.append(segment);
                    String fullPath = pathBuilder.toString();

                    if (i == 0) {
                        current = rootPackages.get(segment);
                        if (current == null) {
                            current = new PackageNodeVO(segment, fullPath);
                            rootPackages.put(segment, current);
                        }
                    } else {
                        PackageNodeVO child = current.getChild(segment);
                        if (child == null) {
                            child = new PackageNodeVO(segment, fullPath);
                            current.addChild(child);
                        }
                        current = child;
                    }
                }

                // Add class as leaf node under package
                if (current != null) {
                    String classSimpleName = className.substring(className.lastIndexOf('/') + 1);
                    PackageNodeVO classNode = new PackageNodeVO(classSimpleName, className);
                    classNode.setClass(true);
                    classNode.setType(type);
                    classNode.setTotalCount(1);
                    classNode.setWovenCount(type == 1 ? 1 : 0);
                    current.addChild(classNode);
                }
            }
        }

        rootVO.setTotalClasses(totalClasses);
        rootVO.setTotalWoven(totalWoven);
        rootVO.setTotalNotWoven(totalClasses - totalWoven);

        List<PackageNodeVO> sortedPackages = new ArrayList<PackageNodeVO>(rootPackages.values());
        Collections.sort(sortedPackages, new Comparator<PackageNodeVO>() {
            public int compare(PackageNodeVO o1, PackageNodeVO o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        for (PackageNodeVO pkg : sortedPackages) {
            pkg.calculateTotals();
        }

        rootVO.setPackages(sortedPackages);
        return rootVO;
    }

    private String extractPackage(String className) {
        if (className == null) {
            return null;
        }
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash <= 0) {
            return null;
        }
        return className.substring(0, lastSlash);
    }

    class PackageXmlRoot extends XmlNode<PackageXmlNode> {
        private PackageRootVO rootVO;
        private List<PackageXmlNode> children = new ArrayList<PackageXmlNode>();

        PackageXmlRoot(PackageRootVO rootVO) {
            this.rootVO = rootVO;
            for (PackageNodeVO pkg : rootVO.getPackages()) {
                children.add(new PackageXmlNode(pkg));
            }
        }

        @Override
        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<packages");
            sb.append(" loaderCount='").append(rootVO.getLoaderCount());
            sb.append("' totalClasses='").append(rootVO.getTotalClasses());
            sb.append("' totalWoven='").append(rootVO.getTotalWoven());
            sb.append("' totalNotWoven='").append(rootVO.getTotalNotWoven());
            return sb.append(">").toString();
        }

        @Override
        protected String getEnd() {
            return "\r\n</packages>";
        }

        @Override
        protected Collection<PackageXmlNode> getChildren() {
            return children;
        }
    }

    class PackageXmlNode extends XmlNode<PackageXmlNode> {
        private PackageNodeVO nodeVO;
        private List<PackageXmlNode> children = new ArrayList<PackageXmlNode>();

        PackageXmlNode(PackageNodeVO nodeVO) {
            this.nodeVO = nodeVO;
            for (PackageNodeVO child : nodeVO.getChildren()) {
                children.add(new PackageXmlNode(child));
            }
        }

        @Override
        protected String getStart() {
            StringBuilder sb = new StringBuilder("\r\n<package");
            sb.append(" name='").append(escapeXml(nodeVO.getName()));
            sb.append("' fullPath='").append(escapeXml(nodeVO.getFullPath()));
            sb.append("' woven='").append(nodeVO.getWovenCount());
            sb.append("' total='").append(nodeVO.getTotalCount());
            return sb.append(">").toString();
        }

        @Override
        protected String getEnd() {
            return "\r\n</package>";
        }

        @Override
        protected Collection<PackageXmlNode> getChildren() {
            return children;
        }
    }

    private String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("'|<|>|\\$", "_");
    }
}