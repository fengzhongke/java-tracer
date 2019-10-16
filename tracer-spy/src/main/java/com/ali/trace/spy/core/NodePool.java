package com.ali.trace.spy.core;

import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.RootNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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

    private final Map<Long, RootNode> POOL = new ConcurrentHashMap<Long, RootNode>();
    private final LinkedBlockingQueue<RootNode> QUEUE = new LinkedBlockingQueue< RootNode>();


    private int mode;
    private volatile long size = 5;
    public static NodePool getPool() {
        return INSTANCE;
    }

    public RootNode getNode(Long seed){
        return POOL.get(seed);
    }


    public void setMode(int mode){
        this.mode = mode;
        if(mode > 0){
            setSize(1024L);
        }
    }
    public int getMode(){
        return mode;
    }

    public void setSize(long size){
        this.size = size;
        while(MAX.get() - MIN.get() > size) {
            RootNode root = QUEUE.poll();
            if( root != null){
                POOL.remove(root.getId());
                MIN.incrementAndGet();
            }else{
                break;
            }
        }
    }
    public long getSize(){return size;}

    public Map<Long, RootNode> getNodes(){
        Map<Long, RootNode> map = new HashMap<Long, RootNode>();
        Iterator<RootNode> itr = QUEUE.iterator();
        while(itr.hasNext()){
            RootNode root = itr.next();
            map.put(root.getId(), root);
        }
        return map;
    }

    public void addNode(BaseNode node, String type){
        long seed = MAX.incrementAndGet();
        while(seed - MIN.get() > size) {
            RootNode root = QUEUE.poll();
            if( root != null){
                POOL.remove(root.getId());
                MIN.incrementAndGet();
            }else{
                break;
            }
        }
        RootNode root = new RootNode(seed, node, type);
        POOL.put(seed, root);
        QUEUE.offer(root);
    }



}
