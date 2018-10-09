package com.ali.trace.main;

import java.lang.instrument.Instrumentation;

import com.ali.trace.inject.TraceEnhance;
import com.ali.trace.inject.TraceTransformer;
import com.ali.trace.intercepter.CommonIntercepter;
import com.ali.trace.intercepter.CompressIntercepter;
import com.ali.trace.intercepter.ThreadIntercepter;

/**
 * 执行的时候，在vm参数中加 -noverify
 * -Xbootclasspath/a:/u01/project/bstack-tool/target/bstack.jar
 * -javaagent:/u01/project/bstack-tool/target/bstack.jar 初始化xml tac a.xml |awk
 * '{s=substr($1,2,1);if(s=="/"){d++;end[d]="";for(i=2;i<NF;i++)end[d]=end[d]" "
 * $i;$0=$1$NF;}else{$1=$1end[d];d--;};print;}'|tac > b.xml 查看深度小于n的栈 cat b.xml
 * |awk '{if(substr($1, 2, 1)!="/"){num++;n=num;}else {n=num;num--;};if(n<NUM)
 * print $0}' NUM=7 > b_7.xml 查看第n行的栈 cat b.xml |awk '{s=substr($1, 2,
 * 1);if(s!="/"){num++;attr[num]=$0}else {num--;}}END{for(i=1;i<num;i++)print
 * i,attr[i]}' LINE=5000000 cat *.xml |awk '{s=substr($1, 2,
 * 1);if(s!="/"){num++;print num,$0;}else {print num,$0;num--;}}' NUM=6 awk
 * '{s=substr($1, 2, 1);if(s!="/"){num++;attr[num]=$0}else {num--;}}END{for(i in
 * attr)print i, attr[i]}' NUM=6|sort
 * 
 * 
 * 
 * 
 * tac a.xml|awk -F " |>"
 * '{s=substr($1,2,1);if(s=="/"){d++;end[d]="";for(i=2;i<NF;i++)end[d]=end[d]" "
 * $i;$0=$1">";}else{$1=$1end[d];$(NF-1)=$(NF-1)">";d--;};print;}'|tac >
 * /tmp/b.xml
 * 
 * @author hanlang.hl
 * @version $Id: Premain.java, v 0.1 2017年12月6日 下午8:35:09 hanlang.hl Exp $
 */
public class Premain {

	public static void premain(String args, Instrumentation inst) {
		String path = "/tmp";
		String intercepter = null;
		String params = null;
		if (args != null) {
			String[] arg = args.split(":");
			intercepter = arg[0];
			if (arg.length > 1) {
				params = arg[1];
			}
		}
		if ("compress".equalsIgnoreCase(intercepter)) {
			TraceEnhance.setIntecepter(new CompressIntercepter(path));
		} else if ("thread".equals(intercepter)) {
			int idx = params.lastIndexOf(".");
			String c = params.substring(0, idx);
			String m = params.substring(idx + 1);
			TraceEnhance.setIntecepter(new ThreadIntercepter(path, c, m));
		} else {
			TraceEnhance.setIntecepter(new CommonIntercepter(path));
		}
		inst.addTransformer(new TraceTransformer(), true);
	}

}
