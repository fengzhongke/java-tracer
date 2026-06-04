# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

java-tracer is a Java Agent-based method call tracing tool. It uses bytecode instrumentation via ASM to inject tracing code into methods, recording call chains and displaying them through an embedded Jetty web server with Velocity templates and Raphael.js sequence diagram visualization.

## Build Commands

```bash
# Build the entire project (produces java-tracer.jar in root directory)
mvn clean package

# Build a specific module
mvn clean package -pl tracer-agent
mvn clean package -pl tracer-spy
```

The final agent jar (`java-tracer.jar`) is copied to the project root by `tracer-agent`'s maven-antrun-plugin. It bundles tracer-spy and all dependencies under `META-INF/lib/`. The JAR manifest sets `Premain-Class`, `Agent-Class`, `Main-Class`, `Can-Redefine-Classes`, `Can-Retransform-Classes`, and `Boot-Class-Path: java-tracer.jar` (puts the entire agent on the bootstrap classpath so `TraceEnhance` hooks are visible to all woven classes).

No unit test framework — test classes under `src/test/java/com/ali/dbtech/test/` use `main()` methods for manual testing (ASM exploration tools, test target apps like Netty server).

## Running the Agent

```bash
# Basic usage (default port 18902)
java -javaagent:path/to/java-tracer.jar -jar your-app.jar

# With custom configuration (-key=value separated by ':')
java -javaagent:java-tracer.jar=-port=19902:-mode=1 -jar your-app.jar

# With config file for include/exclude prefixes
java -Dtracer.config.file=/path/to/tracer.conf -javaagent:java-tracer.jar -jar your-app.jar

# Dynamic attach to running JVM
java -jar java-tracer.jar -pid=12345:-port=18902

# Hot reload (creates entirely new SpyClassLoader, shuts down old Jetty)
java -jar java-tracer.jar -pid=12345:-port=18902:-reload=true
```

Agent arguments: `-port` (18902), `-sleep` (1ms transformer delay), `-mode` (0/1/2), `-reload` (force new ClassLoader), `-retransform` (retransform all loaded classes), `-pid` (target PID for attach).

## Architecture

### Two-Module Structure with Classloader Isolation

tracer-agent is the thin bootstrap layer; tracer-spy is the heavy payload (ASM, Jetty, Velocity, interceptors). They are **separated by a custom classloader boundary** — tracer-agent never imports tracer-spy types at compile time. All interaction is reflective.

**The bridge pattern:**
1. Premain creates `SpyClassLoader(null)` (parent=bootstrap, **child-first** delegation) and loads tracer-spy JARs from `META-INF/lib/` into its `bytesMap`.
2. TraceInjector is instantiated reflectively: `injectClass.getConstructor(Instrumentation.class, Class.class, ...)`.newInstance(inst, TraceEnhance.class, port, mode).
3. `TraceEnhance.class` (from tracer-agent, on bootstrap classpath via `Boot-Class-Path`) is passed as a `Class<?>` parameter — this object bridges both classloader namespaces.
4. ConfigPool uses **reflection on weaveClass** (`TraceEnhance.class`) to call `setInterceptor()` across the classloader boundary, because direct static method calls would fail across different classloader namespaces.
5. TraceEnhance holds the IInterceptor via an inner `interceptor` wrapper that stores `Method` references for `start()`/`end()`, invoked reflectively.

Child-first delegation in SpyClassLoader ensures tracer-spy's own ASM 7, Jetty 8, Velocity 1.5 are used rather than any versions in the target application's classpath.

### TraceEnhance — Re-entry Guard and Cross-Classloader Calls

`TraceEnhance.s(c, m)` / `TraceEnhance.e(c, m)` are the static methods injected into woven bytecode. They have a **ThreadLocal<Boolean> IN guard** that prevents infinite recursion: if the interceptor's own internal method calls (HashMap lookups, NodePool operations) hit woven classes, the guard skips the second `s()/e()` call. Without this, any interceptor call would trigger itself recursively.

### ASM Bytecode Injection Chain

```
TraceInjector.getBytes() → CodeReader(ClassReader) → CodeVisitor(ClassVisitor) → CommonAdapter/FinallyAdapter(AdviceAdapter)
```

- **CommonAdapter** (`common=true`, default): injects `TraceEnhance.s()/e()` in `onMethodEnter()`/`onMethodExit()`. Uses `COMPUTE_MAXS` only — safer, avoids frame computation issues.
- **FinallyAdapter** (`common=false`): wraps method body in try-finally to guarantee `e()` is called on exceptions. Uses `COMPUTE_FRAMES` with custom `getCommonSuperClass()` that throws `TypeNotPresentException` for the current class to prevent circular loading during stack frame computation.
- Method names sanitized: `<init>` → `_init_`, `<clinit>` → `_clinit_` (avoids XML/JSON issues).
- `filter()` logic: exclude prefixes checked first (match → skip); then include prefixes (match → weave; `"*"` means weave everything; empty include → skip all). Default with no config: **nothing is woven**.

### Interceptor Hierarchy

All interceptors implement `IInterceptor` (`start(c,m)` / `end(c,m)`). `BaseInterceptor` provides per-thread `Writer` and file output. The three trigger branches then diverge:

**Method-triggered** (target a specific class+method entry point):
`MethodTreeInterceptor<T extends BaseNode>` (abstract) → `CommonTreeInterceptor` (CommonNode), `CompressTreeInterceptor` (CompressNode). Activates the call stack only when the trigger method is entered; traces its subtree; stops on trigger `end()`.

**Thread-triggered** (whole-thread tracing):
`ThreadTreeInterceptor<T extends BaseNode>` (abstract) → `CommonThreadntercepter` (CommonNode, note typo), `CompressThreadInterceptor` (CompressNode). Activates on the **first** `start()` on any thread; traces the entire thread lifetime. Tree key uses thread name + thread ID.

**File-writing** (standalone, no NodePool):
`CommonInterceptor` writes XML directly to per-thread files. `CompressInterceptor` adds MD5-based deduplication that collapses repeated subtrees into references.

### Node Hierarchy

- `BaseNode<T>` (abstract) — core trace node. `i` (method ID from static `idMap`/`nameMap` bidirectional mapping), `t` (accumulated time). `addSon()`, `getSons()`, `writeFile()` are abstract.
- `CommonNode` extends `BaseNode` — `List<CommonNode>` children, full uncompressed call tree.
- `CompressNode` extends `BaseNode` — `LinkedHashMap<Long, CompressNode>` keyed by method ID. `addSon()` increments `c` (count) if child exists, otherwise creates new child. Adds `"c":COUNT` to JSON.
- `RootNode` (standalone, not extending BaseNode) — wrapper for a `BaseNode` tree with `id`, `type`, `start` timestamp. Entry in `NodePool`.

### ConfigPool — Central Coordination

Singleton that bridges injection infrastructure and web UI:
- **LoaderSet** wraps each ClassLoader with an ID and `ConcurrentHashMap<String, Integer>` mapping class names → weave status (0=not woven, 1=woven). Populated by `TraceInjector.getBytes()` finally block.
- **setConfig/resetConfig**: parses include/exclude config; `resetConfig` iterates all loaded classes and calls `redefine()` for any class where weave status changed. Uses `Instrumentation.retransformClasses()`.
- **redefine mechanism**: sets `redefineLoader/redefineName` before the call (synchronized). TraceInjector's CodeReader checks `isRedefine()` to decide whether `getCommonSuperClass()` can safely compute frames.
- Interceptor is set via **reflection on weaveClass** (`TraceEnhance.class`): `weaveClass.getDeclaredMethod("setInterceptor", Object.class).invoke(null, interceptor)`.

### NodePool — Two-Tier Trace Storage

- **Memory**: `ConcurrentHashMap<Long, RootNode>` + `LinkedBlockingQueue<RootNode>` for eviction order. Default limit 100 (1024 in thread-mode). Evicted nodes removed from memory but remain on disk.
- **Disk**: `trace/<id>.json` adjacent to the agent JAR. Write-through: `saveToFile()` before adding to memory cache.
- **JSON**: manual string parsing (no Gson dependency). `RootNode.toJson()` → `BaseNode.build()` produces `{"i":metaId, "t":totalTime, "s":[children]}`.
- **PlaceholderNode/LoadedNode**: file-loaded traces are lightweight wrappers that replay the original JSON string on `writeFile()` but do NOT reconstruct the full tree structure (`addSon` returns null, `getSons` returns empty). This is a deliberate memory trade-off — historical traces can be displayed but not traversed as live trees.

### Hot Reload — Full SpyClassLoader Swap

When `-reload=true` in agentmain:
1. Create entirely new `SpyClassLoader(null)`.
2. Reflectively call old classloader's `JettyServer.shutdown()`, `ConfigPool.clear()`, `NodePool.clear()`.
3. 2-second sleep for Jetty thread cleanup.
4. Update `AgentResVmLoader.updateClassLoader(newLoader)` and `VmViewResolver.reinit()` for Velocity template loading.
5. Replace static `LOADER` and `INJECT` references. Old classloader and all its loaded classes become unreachable (JVM cannot truly unload them, but they're dead).

### Web Handler Routing

`HandlerConfig` scans handler classes for `@TracerPath`-annotated methods (defined in `ITraceHttpHandler`), creating `Module` entries sorted by order → path length → path specificity. `ModuleHttpServlet.doMethod()` matches request path against Module regex patterns.

Annotation framework (all in `ITraceHttpHandler`):
- `@TracerPath(value="/path", order=N)` — method-level, URL path regex, priority
- `@TraceParam("name")` — parameter-level, injects request parameter value into String arg
- `@TraceView` — method-level, signals that String return value is a Velocity template name
- `ModelMap` (extends `HashMap`) — handler method parameter type, populated as model data for Velocity

Parameter injection: `HttpServletRequest`/`HttpServletResponse`/`PrintWriter` injected by type; `@TraceParam` String params from request parameters; `ModelMap` gets a fresh HashMap. View rendering: `@TraceView` annotations cause String return values to be resolved as Velocity template names via `VmViewResolver`.

### Web API Endpoints

| Handler | Path | Purpose |
|---------|------|---------|
| IndexHandler | `/`, `/index` | Trace list and settings (cname, mname, type, size) |
| IndexHandler | `/index/set` | Set interceptor config (triggers retransform) |
| IndexHandler | `/index/list` | List all trace records |
| TraceHandler | `/trace?id=<seed>` | Trace detail page (sequence diagram) |
| TraceHandler | `/trace/get.json` | Trace data as JSON |
| TraceHandler | `/trace/get.xml` | Trace data as raw XML |
| ClassHandler | `/class` | Class config page (include/exclude prefixes) |
| ClassHandler | `/class/set` | Set include/exclude prefixes (retransforms all loaded classes) |
| ClassHandler | `/class/tree` | Class loader tree viewer |
| ClassHandler | `/class/tree.json` | Class hierarchy JSON (woven/total per class) |
| ClassHandler | `/class/redefine` | Retransform classes by weave status type |
| ThreadHandler | `/thread/tree` | Thread stack tree viewer (collapsed view) |
| ThreadHandler | `/thread/tree.json` | Merged thread stack tree JSON |
| ThreadHandler | `/thread/get.xml` | Thread stack info as XML |
| PackageHandler | `/package` | Package statistics view |
| PackageHandler | `/package/get.json` | Package woven stats JSON |
| StaticHandler | `/static/(js|css)/.*` | Serve static JS/CSS resources |
| UnloadHandler | `/unload` | Agent unload confirmation page |
| UnloadHandler | `/unload/exec` | Detach the agent from the JVM |

## Config File Format

```text
include:
com.example.service
com.example.controller
exclude:
com.example.dto
java.
javax.
```

Prefix matching (`startsWith`). Exclude checked first, then include. Default with no config file: nothing is woven.

## Notes

- JDK 8 bytecode (major version 52), ASM 7.0, Jetty 8.1.2, Velocity 1.5
- Velocity templates in `tracer-spy/src/main/resources/static/vm/`; static JS/CSS in `static/js/`, `static/css/`
- Trace visualization uses Raphael.js + sequence-diagram-min.js for rendering call sequences as interactive sequence diagrams
- `SpyClassLoader` uses custom `bytes:///` URL scheme with inner `ByteURLConnection` to serve classloader resources (needed for Velocity template and static resource loading)
- `shouldSkipClass()` filters out `java.*`, `javax.*`, `sun.*`, `$Proxy`, `$Hibernate`, `$SpringCGLIB`, `$$Lambda`, `$aux` during retransform
- `CommonThreadntercepter` class name contains a typo (missing 'o' in Interceptor) — do not "fix" this, it would break the class name reference in ConfigPool's interceptor selection
- Dynamic attach uses bundled Attach API (`com.sun.tools.attach`) classes + native libs (`attach.dll`/`libattach.so` extracted at runtime) so no `tools.jar` dependency needed
- `XmlNode` (abstract, in `tracer-spy/spy/xml/`) provides XML rendering base for `ThreadHandler` and `ClassHandler` ad-hoc output (thread stacks, class loader trees)