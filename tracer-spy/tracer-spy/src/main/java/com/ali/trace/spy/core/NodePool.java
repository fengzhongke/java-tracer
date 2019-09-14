package com.ali.trace.spy.core;

import com.ali.trace.spy.util.BaseNode;
import com.ali.trace.spy.util.RootNode;
import javafx.util.Pair;

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
    private final LinkedBlockingQueue<Pair<Long, RootNode>> QUEUE = new LinkedBlockingQueue<Pair<Long, RootNode>>();

    private volatile long size = 5;
    public static NodePool getPool() {
        return INSTANCE;
    }

    public RootNode getNode(Long seed){
        return POOL.get(seed);
    }

    public void setSize(long size){
        this.size = size;
        while(MAX.get() - MIN.get() > size) {
            Pair<Long, RootNode> expire = QUEUE.poll();
            if( expire != null){
                POOL.remove(expire.getKey());
                MIN.incrementAndGet();
            }else{
                break;
            }
        }
    }
    public long getSize(){return size;}

    public Map<Long, RootNode> getNodes(){
        Map<Long, RootNode> map = new HashMap<Long, RootNode>();
        Iterator<Pair<Long, RootNode>> itr = QUEUE.iterator();
        while(itr.hasNext()){
            Pair<Long, RootNode> pair = itr.next();
            map.put(pair.getKey(), pair.getValue());
        }
        return map;
    }

    public void addNode(BaseNode node, String type){
        long seed = MAX.incrementAndGet();
        while(seed - MIN.get() > size) {
            Pair<Long, RootNode> expire = QUEUE.poll();
            if( expire != null){
                POOL.remove(expire.getKey());
                MIN.incrementAndGet();
            }else{
                break;
            }
        }
        RootNode root = new RootNode(seed, node, type);
        POOL.put(seed, root);
        QUEUE.offer(new Pair<Long, RootNode>(seed, root));
    }



}
