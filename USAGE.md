# java-tracer 使用手册

## Classes — 类织入配置

### 功能概述

Classes 页面用于查看 JVM 中所有已加载的类，并通过 include/exclude 前缀规则控制哪些类需要织入（weave）追踪代码。织入后的类会在方法进入和退出时调用 `TraceEnhance.s()/e()`，从而记录调用链。

### 核心概念

**Include / Exclude 前缀规则**

- 类名使用 JVM 内部格式（斜杠分隔），如 `com/english/api/controller/GlobalExceptionHandler`
- **Include**：匹配 include 前缀的类会被织入。特殊值 `*` 表示织入所有类
- **Exclude**：匹配 exclude 前缀的类不会被织入，**优先级高于 include**
- 未匹配任何 include 前缀的类默认不织入
- 前缀匹配后会自动追加 `/`，例如添加 `com/ali/dts` 作为 include 时，实际存储的是 `com/ali/dts/`，这样只会织入该包下的类（如 `com/ali/dts/TaskService`），不会误织入相邻包（如 `com/ali/dtsmanager`）

**Weave 状态**

- `type=1`（绿色）：该类已被织入，字节码中已注入追踪代码
- `type=0`（灰色）：该类未织入，字节码未被修改
- 修改 include/exclude 规则后，系统会自动对受影响的类执行 `retransformClasses()`，将需要追踪的类织入、不需要的类还原

### 页面布局

页面顶部显示当前的 include 和 exclude 配置字符串，下方是一个按 ClassLoader 分组的包树：

- **ClassLoader 节点**：顶层节点，每个节点显示类加载器名称和 `织入数/总数` 标签
- **包节点**：中间层级，显示包路径和 `织入数/总数`
- **类节点**：叶子节点，显示类名和织入状态标签

### 操作流程

#### 对包或类进行 weave/unweave

1. 找到目标包或类节点，点击右侧的 **齿轮图标**
2. 弹出配置对话框，显示：
   - 节点的完整路径（如 `com/english/api/controller`）
   - 当前过滤状态：
     - 🟢 "Currently included by prefix: xxx" — 已被 include 前缀匹配
     - 🔴 "Currently excluded by prefix: xxx" — 已被 exclude 前缀匹配
     - ⚪ "Not matched by any prefix (default excluded)" — 未被任何规则匹配
3. 点击操作按钮：
   - **Add to Include**（绿色）— 将路径添加到 include 前缀，开始织入该包/类下的所有方法
   - **Add to Exclude**（红色）— 将路径添加到 exclude 前缀，阻止织入
   - **Remove from Include**（黄色）— 从 include 中移除该前缀，取消织入（仅在当前被 include 匹配时可见）
   - **Remove from Exclude**（黄色）— 从 exclude 中移除该前缀（仅在当前被 exclude 匹配时可见）
4. 操作后出现 **进度条**，显示 "Retransforming: X / Y classes (Z failed)"
5. 进度完成后页面自动刷新

#### 搜索类

页面顶部搜索框支持按类名/包名模糊搜索（不区分大小写），匹配的节点和其父路径会高亮显示。

#### 其他

- **Expand All / Collapse All** — 展开/折叠所有包节点
- **XML View** — 在新标签页打开 `/class/get.xml`，查看 XML 格式的类列表
- **JSON View** — 在新标签页打开 `/class/tree.json`，查看原始 JSON 数据

### 常见场景

**场景 1：追踪某个包下所有方法的调用链**

```
1. 在包树中找到 com/english/api/service 包
2. 点击齿轮图标 → "Add to Include"
3. 等待 retransform 完成
4. 该包下所有类变为绿色（type=1）
5. 此时访问目标方法，/index 页面会记录调用链
```

**场景 2：排除不需要的类，避免追踪框架内部调用**

```
1. 先设置 include 为 com/english/api（织入整个业务包）
2. 发现 Spring/Hibernate 代理类也被织入了
3. 对 com/english/api/controller 包中的 $Proxy/$SpringCGLIB 类点击齿轮 → "Add to Exclude"
   （实际这些类在 shouldSkipClass 中已自动过滤，但如果遇到其他不需要的类可以手动排除）
```

---

## Threads — 线程栈树

### 功能概述

Threads 页面展示 JVM 中所有线程的调用栈，并将多个线程的公共调用路径合并为一棵树，帮助快速理解线程行为和热点调用路径。还支持采样功能，可对指定线程持续采样并展示 CPU 使用率。

### 核心概念

**合并栈树**

- 所有线程的栈帧从**底部（最早/最深）**向**顶部（最近/最浅）**合并
- 相同的调用路径（`className#methodName#lineNumber`）会被合并到同一节点，`count` 表示经过该节点的线程数
- 根节点是入口方法（如 `Thread.run()`、`main()`），越深的节点越接近当前执行位置

**节点类型**

- **帧节点**（`isFrame=true`）：表示一个方法调用，显示方法名、类名、行号和线程数
- **线程叶子**（`isThread=true`）：表示一个真实的线程实例，显示线程名、ID 和状态

**线程状态颜色**

- 🟢 RUNNABLE / NEW — 正常运行
- 🔴 BLOCKED — 阻塞等待锁
- 🟠 WAITING / TIMED_WAITING — 等待通知或超时等待
- ⚪ TERMINATED — 已结束

### 页面布局

- **顶部按钮栏**：Refresh、Expand All、Collapse All、Hide/Show Threads、搜索框
- **树区域**：合并栈树，帧节点可展开/折叠，线程叶子带复选框
- **底部操作栏**：Select All、View Selected Stack（启动采样器）

### 操作流程

#### 浏览线程栈

1. 页面加载时自动调用 `/thread/tree.json` 获取当前所有线程栈
2. 点击帧节点展开/折叠子节点
3. 线程叶子节点显示在帧节点下方，每个叶子有复选框

#### 搜索线程或方法

1. 在搜索框输入关键词（线程名、方法名、类名）
2. 搜索结果按调用路径分组显示：
   - 匹配的帧用 ⭐ 标记高亮
   - 每个分组显示调用链路径和经过的线程列表
3. 清空搜索框恢复完整树视图

#### 对选中线程采样（Stack Sampler）

1. 通过复选框选中感兴趣的线程
2. 点击 **"View Selected Stack"**
3. 弹出采样器窗口，显示实时采样数据：
   - 帧节点的标签显示采样次数和占比（如 "45 (75.2%)"）
   - 线程叶子显示 CPU% 标签（≥50% 红色、≥10% 橙色、其他浅蓝）
4. 采样器每 5 秒自动刷新数据
5. 可调整参数：
   - **Depth**：控制树展开深度（0=全展开）
   - **Min%**：过滤低于此占比的帧（默认 5%）
   - **TTL(s)**：采样器存活时间，每次刷新时续期（默认 10 秒）
6. 点击 **Close** 停止采样器并关闭窗口

### 常见场景

**场景 1：找出哪些线程在阻塞**

```
1. 加载线程树，展开根节点
2. 搜索 "BLOCKED" 或搜索特定类名
3. 找到阻塞线程，查看其调用栈
4. 确定阻塞发生在哪个方法
```

**场景 2：分析 CPU 热点**

```
1. 选中 CPU 占用高的线程
2. 点击 "View Selected Stack" 启动采样
3. 调整 Min% 到 10% 以上，过滤低频调用
4. 观察哪些帧的采样占比最高 → 这些就是热点方法
5. 结合 CPU% 标签确定线程的 CPU 使用率
```

---

## Invoke — 方法调用链

### 功能概述

Invoke 页面允许通过反射在目标 JVM 中执行任意方法调用链，支持递归嵌套参数（方法返回值作为下一个方法的参数）。还可以通过 Weave 功能捕获真实运行时的方法参数，并回放调用。

### 核心概念

**调用链（Chain）**

调用链是一个从内到外的步骤序列。步骤 0 是最内层（无 receiver），步骤 N 使用步骤 N-1 的返回值作为 receiver（调用目标对象）。例如：

```
步骤0: Service.getInstance()       → 返回 service 实例
步骤1: service.getConfig()         → 使用步骤0的返回值作为 receiver
步骤2: config.getMaxSize()         → 使用步骤1的返回值作为 receiver
```

**步骤类型（Node Types）**

| 类型 | 说明 | invokeCall=false 时可用 | 示例 |
|------|------|:-:|------|
| **method** | 调用方法（`Method.invoke()`） | ❌ | `service.getConfig()` |
| **field** | 读取字段（`Field.get()`） | ✅ | `config.maxSize` |
| **classRef** | 获取 Class 对象（`Class.forName()`） | ✅ | `SomeClass.class` |
| **literal** | 字面量值 | ✅ | `"hello"`, `42`, `null` |
| **arrayGet** | 数组/列表取值 | ✅ | `list[0]`, `array[2]` |

**invokeCall 参数**

attach 时可通过 `-invokeCall=false` 禁用方法调用功能，此时：
- 方法调用节点会被阻止，返回错误 "method invocation is disabled"
- 字段读取、Class引用、字面量、数组取值仍然可用
- `/invoke/members.json` 只返回字段，不返回方法
- 页面顶部显示黄色警告横幅

### 页面布局

- **Weave 区域**（顶部）：参数捕获状态栏，Set/Unweave/Params/Clear/UseInvoke 按钮
- **链构建区域**（中部）：可视化步骤序列，每步显示类型标签 + 类名 + 方法名
- **全局操作**（中部下方）：Invoke、Clear、JSON 按钮 + 最大显示长度设置
- **结果区域**（底部）：调用结果展示

### 操作流程

#### 构建并执行调用链

**添加第一个步骤（无 receiver，只能是静态方法/字段）：**

1. 点击 **"Add First Step"**
2. 弹出成员选择器：
   - 搜索类名前缀 → 选择一个类
   - 显示该类的成员列表（方法、字段、Class.forName）
   - 点击一个静态方法或静态字段 → 创建步骤 0

**添加后续步骤（使用前一步的返回值作为 receiver）：**

1. 上一步右侧显示 **"Next Step"** 按钮（仅在返回值可链式调用时出现）
2. 点击后弹出成员选择器：
   - 如果前一步已有 `realReturnType`（来自之前的执行结果），直接加载该运行时类型的成员
   - 如果类型未知，先自动执行 `execMembers.json` 获取运行时类型再显示成员

**添加数组取值步骤：**

1. 当上一步的返回类型是数组或 List 时，显示 **[i]** 按钮
2. 点击后输入索引数字 → 创建 arrayGet 步骤

**编辑方法参数：**

- 方法步骤的每个参数有编辑模式选择：
  - **null** — 传 null 值（对象参数默认）
  - **value** — 输入字面量（基本类型和 String）
  - **String** — 字面量字符串
  - **chain** — 子调用链（嵌套调用，点击展开子链编辑器）
  - **empty** — 空数组（如 `new String[0]`）

**执行调用链：**

- **Invoke 按钮** — 执行整条链
- **步骤类型标签**（如蓝色的 M/F/C/[i]）— 点击可仅执行到该步骤（部分执行）
- 结果显示：
  - 🟢 成功：返回类型 + 返回值 + 耗时
  - 🔴 失误：异常信息
  - 🟡 级联失败：上游步骤失败导致的下游失败

#### JSON 编辑器

1. 点击 **JSON** 按钮 → 打开 JSON 编辑器窗口
2. **Export**：将当前链导出为 JSON 文本
3. **Import**：从 JSON 文本导入链（支持 flat chain 和 target-linked tree 两种格式）
4. 适合保存和复用复杂调用链

#### 参数捕获（Weave）

**Weave 用于捕获目标 JVM 中真实方法调用时的参数值，可在 Invoke 中使用这些参数回放调用。**

**设置 Weave：**

1. 点击 **Set** 按钮 → 打开 Weave 模态框
2. 搜索类 → 选择类 → 选择方法（只显示有参数的方法）
3. 点击方法选中（绿色高亮）→ 点击 **Weave**
4. 后端通过 `Instrumentation.retransformClasses()` 给目标方法注入参数捕获代码
5. 状态栏显示 "Weaved: ClassName#methodName"

**获取捕获的参数：**

1. 在目标 JVM 中调用被 weave 的方法（自然触发）
2. 点击 **Params** 按钮 → 显示捕获的参数列表
   - 参数 0 标记为 `isReceiver`（即 `this` 对象）
   - 每个参数显示类型和值（值截断到 200 字符）

**使用捕获的参数回放调用：**

1. 获取到参数后，点击 **UseInvoke** 按钮
2. 自动构建一条特殊调用链：`InvokeHandler.invokeCapture(BaseInterceptor.getParams())`
3. 该链会使用捕获到的真实参数（包括 receiver）回放原始方法调用

**取消 Weave：**

1. 点击 **Unweave** 按钮
2. 后端清除 weave 目标并 retransform 该类还原字节码

**清除参数：**

- 点击 **Clear** 按钮 → 清空已捕获的参数

### 常见场景

**场景 1：查看某个 Spring Bean 的配置值**

```
1. Add First Step → 搜索 "Config" → 选择配置类 → 点击静态字段 "instance"
2. Next Step → 选择 "getMaxSize" 方法
3. Invoke → 看到返回值如 1024
```

**场景 2：查看 List 中的第一个元素**

```
1. Add First Step → 搜索 "Service" → 选择 "getUsers" 静态方法
2. 上一步返回 List → 点击 [i] → 输入 0
3. Invoke → 看到第一个 User 对象的 toString
```

**场景 3：捕获真实参数并回放**

```
1. Weave: 对 OrderService.createOrder(String, int) 设置 weave
2. 在业务系统中触发一次创建订单操作
3. Params → 看到 capturedParams: [this=OrderService, "SKU001", 5]
4. UseInvoke → 用捕获的参数回放 createOrder 调用
5. 观察方法的实际返回值或异常
```

**场景 4：invokeCall=false 模式下只读取字段**

```
1. 用 -invokeCall=false attach 到目标 JVM
2. 页面显示警告横幅 "Method invocation is disabled"
3. Add First Step → 选择类 → 只能看到字段（方法被过滤）
4. 点击静态字段 → Invoke → 成功读取字段值
5. Next Step → 继续读取实例字段 → Invoke → 成功
6. 试图选择方法 → 不出现在成员列表中
```

---

## 快速参考

### 启动参数

```bash
# -javaagent 方式（默认端口 18902，默认允许 invoke 调用方法）
java -javaagent:java-tracer.jar -jar your-app.jar

# 指定端口和模式
java -javaagent:java-tracer.jar=-port=19902:-mode=1 -jar your-app.jar

# 禁用 invoke 方法调用（只允许读字段）
java -javaagent:java-tracer.jar=-port=18902:-invokeCall=false -jar your-app.jar

# 动态 attach 到运行中的 JVM
java -jar java-tracer.jar -pid=12345:-port=18902:-invokeCall=false

# 带配置文件（include/exclude 前缀）
java -Dtracer.config.file=/path/to/tracer.conf -javaagent:java-tracer.jar -jar your-app.jar
```

### 配置文件格式

```text
include:
com.example.service
com.example.controller
exclude:
com.example.dto
java.
javax.
```

### 页面导航

| 页面 | URL | 功能 |
|------|-----|------|
| 首页（追踪配置 + 类树） | `/index` | 设置追踪目标、查看追踪记录、类 weave 配置 |
| 类树（独立页面） | `/class` | 类 weave 配置（与首页的类树相同功能） |
| 线程栈树 | `/thread` | 线程栈查看、采样分析 |
| 方法调用链 | `/invoke` | 执行方法/字段调用、参数捕获与回放 |
| 卸载 Agent | `/unload` | 从 JVM 中卸载 java-tracer |
