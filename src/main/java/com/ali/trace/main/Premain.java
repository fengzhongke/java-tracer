package com.ali.trace.main;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * 执行的时候，在vm参数中加
 * -javaagent:/u01/project/java-tracer/target/java-tracer.jar=intercepter:thread&class:com.hema.sre.pool.service.
 * SqlService&method:exec&online:true 初始化xml tac a.xml |awk
 * '{s=substr($1,2,1);if(s=="/"){d++;end[d]="";for(i=2;i<NF;i++)end[d]=end[d]" "
 * $i;$0=$1$NF;}else{$1=$1end[d];d--;};print;}'|tac > b.xml 查看深度小于n的栈 cat b.xml |awk '{if(substr($1, 2,
 * 1)!="/"){num++;n=num;}else {n=num;num--;};if(n<NUM) print $0}' NUM=7 > b_7.xml 查看第n行的栈 cat b.xml |awk '{s=substr($1,
 * 2, 1);if(s!="/"){num++;attr[num]=$0}else {num--;}}END{for(i=1;i<num;i++)print i,attr[i]}' LINE=5000000 cat *.xml |awk
 * '{s=substr($1, 2, 1);if(s!="/"){num++;print num,$0;}else {print num,$0;num--;}}' NUM=6 awk '{s=substr($1, 2,
 * 1);if(s!="/"){num++;attr[num]=$0}else {num--;}}END{for(i in attr)print i, attr[i]}' NUM=6|sort
 * 
 * 
 * 
 * 
 * tac a.xml|awk -F " |>" '{s=substr($1,2,1);if(s=="/"){d++;end[d]="";for(i=2;i<NF;i++)end[d]=end[d]" "
 * $i;$0=$1">";}else{$1=$1end[d];$(NF-1)=$(NF-1)">";d--;};print;}'|tac > /tmp/b.xml
 * 
 * @author hanlang.hl
 * @version $Id: Premain.java, v 0.1 2017年12月6日 下午8:35:09 hanlang.hl Exp $
 */
public class Premain {

    public static void premain(String args, Instrumentation inst) {
        process(args, inst);
    }

    private static Set<String> CANT_TRANSFORM = new HashSet<String>();

    public static void agentmain(String args, Instrumentation inst) {
        process(args, inst);
        Class<?>[] classes = inst.getAllLoadedClasses();
        for (Class<?> clasz : classes) {
            String name = clasz.getName();
            try {
                if (clasz.getClassLoader() != null && clasz.getClassLoader().getParent() != null
                    && !name.equals("com.ali.") && !CANT_TRANSFORM.contains(name)) {
                    inst.retransformClasses(new Class<?>[] {clasz});
                }
            } catch (Throwable t) {
                CANT_TRANSFORM.add(name);
                // t.printStackTrace();
            }
        }
    }

    private static void process(String args, Instrumentation inst) {
        try {
            URL res = Premain.class.getResource("Premain.class");
            String file = res.getFile();
            int from = file.indexOf(":");
            int to = file.indexOf("!");
            if (from > -1 && to > -1) {
                file = file.substring(from + 1, to);
            }
            inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(file)));
            System.out.println("jar is :" + file);

            Class<?> clasz = Class.forName("com.ali.trace.main.CoreEngine");
            Method method = clasz.getDeclaredMethod("process", new Class<?>[] {String.class, Instrumentation.class});
            method.invoke(null, args, inst);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
