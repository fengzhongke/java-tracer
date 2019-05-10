# java-tracer

agent 工具 可以用来记录方法调用
步骤 1 下载打包
下载源码，打包代码如下
1.进入项目根目录：cd xx/java-tracer
2.打包：mvn clean package
3.检查agent包: 使用tracer-agent/target/java-tracer.jar作为agent来启动进程

步骤 2 进行进程
运行进程如下
1.如果使用命令行启动java进程
java -javaagent:xxx/tracer-agent/target/java-tracer.jar xxx
2.使用IDE启动进程
增加-javaagent:xxx/tracer-agent/target/java-tracer.jar 到VM启动参数

步骤 3 访问链接
访问 http://127.0.0.1:18902/tracer/info 看看返回结果如下
<handlers cnt="6">
	<handler path="/tracer/info" params="" desc="get all handlers"/>
	<handler path="/tracer/class" params="" desc="get class loaders and its classes"/>
	<handler path="/tracer/thread" params="" desc="get system threads"/>
	<handler path="/tracer/trace/set" params="type(can be compressThread),class,method" desc="setintercepter "/>
	<handler path="/tracer/trace/get" params="" desc="get xml trade of the method"/>
	<handler path="/tracer/trace/del" params="" desc="delete the intercepter"/>
</handlers>


agent tool used for trace method invoke
STEP 1 download and package
download source and package it as follow
1.enter project root DIR : cd xx/java-tracer
2.package : mvn clean package
3.check agent : use tracer-agent/target/java-tracer.jar as agent to start java processs

STEP 2 start process
running java process as follow
1.with commond:
java -javaagent:xxx/tracer-agent/target/java-tracer.jar xxx
2.with IDE
add -javaagent:xxx/tracer-agent/target/java-tracer.jar to VM arguments

STEP 3 visit links
visit http://127.0.0.1:18902/tracer/info see results as follow
<handlers cnt="6">
	<handler path="/tracer/info" params="" desc="get all handlers"/>
	<handler path="/tracer/class" params="" desc="get class loaders and its classes"/>
	<handler path="/tracer/thread" params="" desc="get system threads"/>
	<handler path="/tracer/trace/set" params="type(can be compressThread),class,method" desc="setintercepter "/>
	<handler path="/tracer/trace/get" params="" desc="get xml trade of the method"/>
	<handler path="/tracer/trace/del" params="" desc="delete the intercepter"/>
</handlers>

each handler correspondent with a page and function
1./tracer/info
show handlers info

2./tracer/class
show class loaders and classes loeaded

3./tracer/thread
show threads and its' stacks

4./tracer/trace/set
set method to be traced

5./tracer/trace/get
get the trace stack traced

6./tracer/trace/del
delete method trace