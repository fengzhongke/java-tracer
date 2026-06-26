package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.jetty.vo.ThreadNodeVO;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.Map.Entry;

/**
 * Background thread that continuously samples selected threads' stack traces,
 * accumulating frame counts into a merged ThreadNodeVO tree.
 * Auto-expires after TTL unless renewed by frontend poll requests.
 * Also tracks CPU usage via ThreadMXBean.getThreadCpuTime().
 */
public class ThreadStackSampler {

    private final Set<Long> selectedThreadIds;
    private final ThreadNodeVO mergedRoot;
    private volatile long expiryTime;
    private volatile boolean running;
    private int sampleCount;
    private Thread samplerThread;
    private Runnable onComplete;

    // CPU time tracking via ThreadMXBean
    private final ThreadMXBean threadMXBean;
    private final boolean cpuTimeSupported;
    private long initialWallTime;                     // nanoTime at sampler start
    private final Map<Long, Long> initialCpuTimes;    // threadId -> cpuTime at start

    public ThreadStackSampler(Set<Long> selectedThreadIds) {
        this.selectedThreadIds = new HashSet<Long>(selectedThreadIds);
        this.mergedRoot = new ThreadNodeVO();
        mergedRoot.setName("root");
        this.expiryTime = System.currentTimeMillis() + 10000;
        this.running = true;
        this.sampleCount = 0;
        this.initialCpuTimes = new HashMap<Long, Long>();

        // Check if ThreadMXBean supports CPU time measurement
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.cpuTimeSupported = threadMXBean.isThreadCpuTimeSupported();
        if (cpuTimeSupported && !threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }
    }

    /**
     * Set a callback to invoke when the sampler thread finishes (expiry or stop).
     */
    public void setOnComplete(Runnable callback) {
        this.onComplete = callback;
    }

    public void start() {
        samplerThread = new Thread(new Runnable() {
            public void run() {
                runSampler();
            }
        }, "thread-stack-sampler");
        samplerThread.setDaemon(true);
        samplerThread.start();
    }

    private void runSampler() {
        List<Thread> threads = new ArrayList<Thread>();
        Map<Thread, StackTraceElement[]> allTraces = Thread.getAllStackTraces();
        for (Entry<Thread, StackTraceElement[]> entry : allTraces.entrySet()) {
            Thread thread = entry.getKey();
            if (selectedThreadIds.contains(thread.getId())) {
                threads.add(thread);
            }
        }

        // Record initial wall time and per-thread CPU times for CPU% calculation
        initialWallTime = System.nanoTime();
        if (cpuTimeSupported) {
            for (Thread thread : threads) {
                try {
                    long cpuTime = threadMXBean.getThreadCpuTime(thread.getId());
                    if (cpuTime >= 0) {
                        initialCpuTimes.put(thread.getId(), cpuTime);
                    }
                } catch (Exception e) {
                    // JDK 9+ module access restriction: sun.management not exported
                    // Fall back — CPU percentage will not be reported
                    System.err.println("getThreadCpuTime FAILED: " + e);
                }
            }
        }

        while (running && System.currentTimeMillis() < expiryTime) {
            synchronized (this) {
                if (!running) break;
                for (Thread thread : threads) {
                    StackTraceElement[] stacks = thread.getStackTrace();
                    if (stacks == null || stacks.length == 0) continue;

                    ThreadNodeVO current = mergedRoot;

                    // Build from bottom to top, merging same paths
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

                    // Add/update thread leaf at deepest frame
                    if (current != null && current != mergedRoot) {
                        String threadKey = "thread#" + thread.getId();
                        ThreadNodeVO existingLeaf = current.getChild(threadKey);
                        if (existingLeaf == null) {
                            ThreadNodeVO threadLeaf = new ThreadNodeVO();
                            threadLeaf.setName(thread.getName());
                            threadLeaf.setId(thread.getId());
                            threadLeaf.setState(thread.getState().name());
                            threadLeaf.setIsThread(true);
                            current.addChildByKey(threadKey, threadLeaf);
                            existingLeaf = threadLeaf;
                        }
                        existingLeaf.incCount();
                        existingLeaf.setState(thread.getState().name());
                    }
                }
                sampleCount++;
            }

            // Sleep briefly between sampling rounds (~10ms to avoid excessive CPU usage)
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
        running = false;

        // Self-cleaning
        if (onComplete != null) {
            onComplete.run();
            onComplete = null;
        }
        samplerThread = null;
    }

    public synchronized void renewExpiry(int seconds) {
        expiryTime = System.currentTimeMillis() + seconds * 1000L;
    }

    public synchronized SamplerResult getResult() {
        SamplerResult result = new SamplerResult();
        result.sampleCount = sampleCount;
        result.running = running;

        // Deep copy children list for thread safety
        result.tree = new ArrayList<ThreadNodeVO>(mergedRoot.getChildren());
        sortAndClean(result.tree);

        // Calculate CPU percentage for thread leaf nodes using ThreadMXBean
        if (cpuTimeSupported) {
            long elapsedWall = System.nanoTime() - initialWallTime;
            if (elapsedWall > 0) {
                setCpuPct(result.tree, elapsedWall);
            }
        }

        return result;
    }

    private void setCpuPct(List<ThreadNodeVO> nodes, long elapsedWallNanos) {
        for (ThreadNodeVO node : nodes) {
            if (node.isThread()) {
                Long initialCpu = initialCpuTimes.get(node.getId());
                if (initialCpu != null) {
                    try {
                        long currentCpu = threadMXBean.getThreadCpuTime(node.getId());
                        if (currentCpu >= 0) {
                            double cpuPct = (currentCpu - initialCpu) * 100.0 / elapsedWallNanos;
                            // Cap at 100% (single core) — can exceed if multiple cores
                            node.setCpuPct(cpuPct);
                        }
                    } catch (Exception e) {
                        // JDK 9+ module access may block reflective getThreadCpuTime
                        System.err.println("getThreadCpuTime FAILED: " + e);
                    }
                }
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                setCpuPct(node.getChildren(), elapsedWallNanos);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        expiryTime = 0;
    }

    public boolean isRunning() {
        return running;
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

    /**
     * Result wrapper for JSON serialization
     */
    public static class SamplerResult {
        public int sampleCount;
        public boolean running;
        public List<ThreadNodeVO> tree;

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"sampleCount\":").append(sampleCount);
            sb.append(",\"running\":").append(running);
            sb.append(",\"tree\":");
            if (tree != null && !tree.isEmpty()) {
                sb.append("[");
                for (int i = 0; i < tree.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(tree.get(i).toString());
                }
                sb.append("]");
            } else {
                sb.append("[]");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
