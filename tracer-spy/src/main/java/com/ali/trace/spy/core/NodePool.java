package com.ali.trace.spy.core;

import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.RootNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author nkhanlang@163.com
 */
public class NodePool {

    private static final AtomicLong MAX = new AtomicLong(0L);
    private static final AtomicLong MIN = new AtomicLong(0L);
    private static final NodePool INSTANCE = new NodePool();

    // Memory cache with limited size (for hot traces)
    private final Map<Long, RootNode> MEMORY_CACHE = new ConcurrentHashMap<Long, RootNode>();
    private final LinkedBlockingQueue<RootNode> QUEUE = new LinkedBlockingQueue<RootNode>();

    // File storage directory
    private Path traceDir;
    private boolean fileStorageEnabled = false;

    // Maximum memory cache size (reduce memory usage)
    private int maxMemoryCacheSize = 100;

    private int mode;
    private volatile long size = 5;

    public static NodePool getPool() {
        return INSTANCE;
    }

    private NodePool() {
        initTraceDir();
    }

    /**
     * Initialize trace directory for file storage
     */
    private void initTraceDir() {
        try {
            // Try multiple ways to find the jar location
            Path jarDir = null;

            // Method 1: From resource URL
            try {
                String jarPath = NodePool.class.getResource("/META-INF/lib").getPath();
                if (jarPath != null) {
                    // jarPath is like: file:/path/to/java-tracer.jar!/META-INF/lib
                    jarPath = jarPath.replace("file:", "");
                    int idx = jarPath.indexOf("!");
                    if (idx > 0) {
                        jarPath = jarPath.substring(0, idx);
                    }
                    jarDir = Paths.get(jarPath).getParent();
                }
            } catch (Exception e1) {
                // Method 2: From class protection domain
                try {
                    String path = NodePool.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                    if (path != null && path.endsWith(".jar")) {
                        jarDir = Paths.get(path).getParent();
                    }
                } catch (Exception e2) {
                    // Method 3: From system property
                    String classPath = System.getProperty("java.class.path");
                    if (classPath != null) {
                        for (String cp : classPath.split(File.pathSeparator)) {
                            if (cp.contains("java-tracer") && cp.endsWith(".jar")) {
                                jarDir = Paths.get(cp).getParent();
                                break;
                            }
                        }
                    }
                }
            }

            // Fallback: use current working directory
            if (jarDir == null) {
                jarDir = Paths.get("").toAbsolutePath();
            }

            traceDir = jarDir.resolve("trace");

            // Create directory if not exists
            if (!Files.exists(traceDir)) {
                Files.createDirectories(traceDir);
                System.out.println("Trace directory created: " + traceDir);
            }

            fileStorageEnabled = true;
            System.out.println("Trace storage initialized at: " + traceDir);
        } catch (Exception e) {
            System.err.println("Failed to initialize trace directory: " + e.getMessage());
            fileStorageEnabled = false;
        }
    }

    /**
     * Clear all data (for hot reload)
     */
    public void clear() {
        MEMORY_CACHE.clear();
        QUEUE.clear();
        MAX.set(0L);
        MIN.set(0L);
        mode = 0;
        size = 5;

        // Reinitialize trace directory
        initTraceDir();

        // Optionally clear trace files
        if (fileStorageEnabled && traceDir != null) {
            try {
                Files.list(traceDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try { Files.delete(p); } catch (Exception e) { }
                    });
            } catch (Exception e) {
                System.err.println("Failed to clear trace files: " + e.getMessage());
            }
        }
    }

    public RootNode getNode(Long seed) {
        // Check memory cache first
        RootNode node = MEMORY_CACHE.get(seed);
        if (node != null) {
            return node;
        }

        // Load from file if not in memory
        return loadFromFile(seed);
    }

    public void setMode(int mode) {
        this.mode = mode;
        if (mode > 0) {
            setSize(1024L);
        }
    }

    public int getMode() {
        return mode;
    }

    public void setSize(long size) {
        this.size = size;
        // Evict from memory cache if exceeds limit
        while (QUEUE.size() > maxMemoryCacheSize) {
            RootNode root = QUEUE.poll();
            if (root != null) {
                MEMORY_CACHE.remove(root.getId());
                MIN.incrementAndGet();
            } else {
                break;
            }
        }
    }

    public long getSize() {
        return size;
    }

    public long getInQueue() {
        return QUEUE.size();
    }

    /**
     * Get all nodes - scan both memory cache and trace files
     */
    public Map<Long, RootNode> getNodes() {
        Map<Long, RootNode> map = new HashMap<Long, RootNode>();

        // Add from memory cache
        Iterator<RootNode> itr = QUEUE.iterator();
        while (itr.hasNext()) {
            RootNode root = itr.next();
            map.put(root.getId(), root);
        }

        // Add from trace files
        if (fileStorageEnabled && traceDir != null) {
            try {
                Files.list(traceDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            String filename = p.getFileName().toString();
                            long id = Long.parseLong(filename.replace(".json", ""));
                            if (!map.containsKey(id)) {
                                RootNode node = loadFromFile(id);
                                if (node != null) {
                                    // Create lightweight metadata entry
                                    map.put(id, node);
                                }
                            }
                        } catch (Exception e) {
                            // Skip invalid files
                        }
                    });
            } catch (Exception e) {
                System.err.println("Failed to scan trace directory: " + e.getMessage());
            }
        }

        return map;
    }

    public boolean isFull() {
        return getInQueue() >= maxMemoryCacheSize;
    }

    /**
     * Add a trace node - save to both memory cache and file
     */
    public void addNode(BaseNode node, String type) {
        long seed = MAX.incrementAndGet();

        // Create RootNode
        RootNode root = new RootNode(seed, node, type);

        // Save to file first (ensure persistence)
        saveToFile(root);

        // Evict from memory cache if exceeds limit (but keep in file)
        while (QUEUE.size() >= maxMemoryCacheSize) {
            RootNode oldRoot = QUEUE.poll();
            if (oldRoot != null) {
                MEMORY_CACHE.remove(oldRoot.getId());
                MIN.incrementAndGet();
            } else {
                break;
            }
        }

        // Add to memory cache
        MEMORY_CACHE.put(seed, root);
        QUEUE.offer(root);
    }

    /**
     * Save trace node to file
     */
    private void saveToFile(RootNode node) {
        if (!fileStorageEnabled || traceDir == null) {
            return;
        }

        Path filePath = traceDir.resolve(node.getId() + ".json");
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(node.toJson());
        } catch (Exception e) {
            System.err.println("Failed to save trace " + node.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Load trace node from file
     */
    private RootNode loadFromFile(Long id) {
        if (!fileStorageEnabled || traceDir == null) {
            return null;
        }

        Path filePath = traceDir.resolve(id + ".json");
        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            String content = new String(Files.readAllBytes(filePath), "UTF-8");
            return parseJson(content);
        } catch (Exception e) {
            System.err.println("Failed to load trace " + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse JSON content to RootNode
     */
    private RootNode parseJson(String json) {
        try {
            long id = extractLong(json, "id");
            String type = extractString(json, "type");
            long start = extractLong(json, "start");
            String nodeJson = extractObject(json, "node");

            // Parse the BaseNode tree
            BaseNode baseNode = parseBaseNode(nodeJson);
            if (baseNode == null) {
                baseNode = new PlaceholderNode(nodeJson);
            }

            return new RootNode(id, baseNode, type, start);
        } catch (Exception e) {
            System.err.println("Failed to parse trace JSON: " + e.getMessage());
            return null;
        }
    }

    private long extractLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return 0;
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return unescapeJson(json.substring(start, end));
    }

    private String extractObject(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return "{}";
        start += pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) start++;

        // Find matching braces
        int braceCount = 0;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '{') braceCount++;
            else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    end++;
                    break;
                }
            }
            end++;
        }
        return json.substring(start, end);
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
    }

    /**
     * Parse BaseNode from JSON
     */
    private BaseNode parseBaseNode(String json) {
        try {
            long id = extractLong(json, "i");
            long t = extractLong(json, "t");
            String sonsJson = extractArray(json, "s");

            BaseNode node = new LoadedNode(id, t, json);
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractArray(String json, String key) {
        String pattern = "\"" + key + "\":[";
        int start = json.indexOf(pattern);
        if (start < 0) return "[]";
        start += pattern.length() - 1; // include '['

        // Find matching brackets
        int bracketCount = 0;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '[') bracketCount++;
            else if (c == ']') {
                bracketCount--;
                if (bracketCount == 0) {
                    end++;
                    break;
                }
            }
            end++;
        }
        return json.substring(start, end);
    }

    /**
     * Placeholder node for loaded traces
     */
    private static class PlaceholderNode extends BaseNode<PlaceholderNode> {
        private final String originalJson;

        public PlaceholderNode(String json) {
            super(0);
            this.originalJson = json;
        }

        @Override
        public void writeFile(Writer writer, int depth) throws IOException {
            writer.write(originalJson);
        }

        @Override
        public PlaceholderNode addSon(long id) {
            return null;
        }

        @Override
        public Collection<PlaceholderNode> getSons() {
            return Collections.emptyList();
        }
    }

    /**
     * Loaded node with preserved JSON
     */
    private static class LoadedNode extends BaseNode<LoadedNode> {
        private final String originalJson;

        public LoadedNode(long id, long t, String json) {
            super(id);
            this.t = t;
            this.originalJson = json;
        }

        @Override
        public void writeFile(Writer writer, int depth) throws IOException {
            writer.write(originalJson);
        }

        @Override
        public LoadedNode addSon(long id) {
            return null;
        }

        @Override
        public Collection<LoadedNode> getSons() {
            return Collections.emptyList();
        }
    }
}