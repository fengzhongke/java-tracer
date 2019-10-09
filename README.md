# java-tracer : 方法调用跟踪记录工具
  
## agent 工具 可以用来记录方法调用
### 步骤 1 下载agent包
直接从 https://github.com/fengzhongke/java-tracer/blob/master/java-tracer.jar 下载到本地
或者下载源码包 mvn clean package 打包，在项目根目录下面就有一个java-tracer.jar

### 步骤 2 运行进程
运行进程如下
1. 如果使用命令行启动java进程
java -javaagent:xxx/tracer-agent/target/java-tracer.jar xxx
2. 使用IDE启动进程
增加-javaagent:xxx/tracer-agent/target/java-tracer.jar 到VM启动参数

### 步骤 3 访问链接
游览器访问 http://127.0.0.1:18902
页面功能包括
1. 系统信息，包括 a.classLoader&class信息，b.thread信息
2. 设置需要跟踪的类(cname)、方法(mname)、保存链路跟踪个数(size)
3. 查看链路

## agent tool used for trace method invoke
### STEP 1 download agent jar
you can download from https://github.com/fengzhongke/java-tracer/blob/master/java-tracer.jar to local storage
or download source and use maven to package and then in the root directory you can find java-tracer.jar

### STEP 2 start process
running java process as follow
1. with commond:
java -javaagent:xxx/tracer-agent/target/java-tracer.jar xxx
2. with IDE
add -javaagent:xxx/tracer-agent/target/java-tracer.jar to VM arguments

### STEP 3 visit links
visit http://127.0.0.1:18902/tracer/info see results as follow
use browser to visit http://127.0.0.1:18902
actions include
1. system infomation include a.classLoader&class detail，b.intime thread detail
2. to set class(cname) and method(mname) to trace and numbers of trace result to retain
3. view the trance

END

## DEMO:
1. Mybatis ：https://fengzhongke.github.io/pages/chart.html?page=mybatis
2. Spring ：https://fengzhongke.github.io/pages/chart.html?page=spring
3. Netty1 ：https://fengzhongke.github.io/pages/chart.html?page=NettyServer
4. Netty2 ：https://fengzhongke.github.io/pages/chart.html?page=NioEventLoop

