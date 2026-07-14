package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.ConfigPool.LoaderSet;
import com.ali.trace.spy.interceptor.BaseInterceptor;
import com.ali.trace.spy.invoke.InvokeEngine;
import com.ali.trace.spy.interceptor.IInterceptor;
import com.ali.trace.spy.jetty.vo.ClassSearchVO;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.ExecMembersResult;
import com.ali.trace.spy.jetty.vo.MemberMetaVO;
import com.ali.trace.spy.jetty.vo.MethodCallNode;
import com.ali.trace.spy.jetty.vo.MethodMetaVO;
import com.ali.trace.spy.jetty.vo.WeaveStatusVO;
import com.ali.trace.spy.util.SimpleJsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

/**
 * Handler for method invocation feature.
 * Provides web UI and API endpoints to invoke arbitrary methods in the target JVM
 * with recursive call expressions where parameters can be other method return values.
 */
public class InvokeHandler implements ITraceHttpHandler {

    /**
     * Invoke page — renders invoke.vm Velocity template
     */
    @TracerPath(value = "/invoke", order = 1)
    @TraceView
    public String index(ModelMap map) {
        map.put("activePage", "invoke");
        map.put("invokeCallAllowed", ConfigPool.getPool().isInvokeCallAllowed());
        return "invoke";
    }

    /**
     * Execute a method invocation expression.
     * Accepts POST with JSON body containing the recursive MethodCallNode tree.
     * Since ModuleHttpServlet only supports URL-encoded params via req.getParameter(),
     * this endpoint reads the JSON body directly from HttpServletRequest.getInputStream().
     */
    @TracerPath(value = "/invoke/exec", order = 2)
    public DataRet<List<MethodCallNode>> exec(HttpServletRequest req) {
        try {
            // Read JSON body from request
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String jsonBody = sb.toString();

            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                return new DataRet<List<MethodCallNode>>(false, -1, "empty request body");
            }

            // Parse JSON into MethodCallNode tree (supports both chain array and target-linked tree formats)
            MethodCallNode rootNode = SimpleJsonParser.parseMethodCallNode(jsonBody);
            if (rootNode == null) {
                return new DataRet<List<MethodCallNode>>(false, -1, "invalid JSON format");
            }

            // Invoke the engine
            rootNode = InvokeEngine.getInstance().invoke(rootNode);

            // Return result as flat chain array (matches frontend display order)
            DataRet<List<MethodCallNode>> ret = new DataRet<List<MethodCallNode>>(true, 0, "ok");
            ret.setData(rootNode.flattenChain());
            return ret;

        } catch (Exception e) {
            return new DataRet<List<MethodCallNode>>(false, -1, "invoke failed: " + e.getMessage());
        }
    }

    /**
     * Execute the invoke chain, then use the root result's actual runtime type (getClass())
     * to list all available members — including superclass chain and interface methods.
     * This replaces the className-based lookup with a runtime type-based lookup,
     * which correctly handles Spring proxies, runtime subtypes, and inner classes.
     *
     * Accepts POST with JSON body (same format as /invoke/exec).
     * Returns: { status, code, msg, data: ExecMembersResult }
     */
    @TracerPath(value = "/invoke/execMembers.json", order = 6)
    public DataRet<ExecMembersResult> execMembers(HttpServletRequest req) {
        try {
            // Step 1: Read and parse JSON body (same as /invoke/exec)
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String jsonBody = sb.toString();

            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                return new DataRet<ExecMembersResult>(false, -1, "empty request body");
            }

            // Step 2: Parse and invoke the chain
            MethodCallNode rootNode = SimpleJsonParser.parseMethodCallNode(jsonBody);
            if (rootNode == null) {
                return new DataRet<ExecMembersResult>(false, -1, "invalid JSON format");
            }

            rootNode = InvokeEngine.getInstance().invoke(rootNode);

            // Step 3: Get the runtime type from the actual result object
            Object resolvedValue = rootNode.resolvedValue;
            ExecMembersResult result = new ExecMembersResult();
            result.setInvokeResult(rootNode.flattenChain());

            if (resolvedValue == null) {
                // Invocation succeeded but returned null — cannot determine runtime type
                // Fall back to returnType string to search for the class
                String returnTypeName = rootNode.getReturnType() != null ? rootNode.getReturnType() : "";
                result.setRuntimeClassName(returnTypeName);
                result.setMembers(null);
            } else {
                // Use the actual runtime type: resolvedValue.getClass()
                Class<?> runtimeClass = resolvedValue.getClass();
                String runtimeClassName = runtimeClass.getName();

                // Step 4: Collect members from the runtime type (superclass chain + interfaces)
                List<MemberMetaVO> members = collectMembersFromClass(runtimeClass);
                result.setRuntimeClassName(runtimeClassName);
                result.setMembers(members);
            }

            DataRet<ExecMembersResult> ret = new DataRet<ExecMembersResult>(true, 0, "ok");
            ret.setData(result);
            return ret;

        } catch (Exception e) {
            return new DataRet<ExecMembersResult>(false, -1, "invoke+members failed: " + e.getMessage());
        }
    }

    /**
     * Collect all members (methods + fields) from a Class object,
     * walking the superclass chain and including interface methods.
     */
    private List<MemberMetaVO> collectMembersFromClass(Class<?> clazz) {
        List<MemberMetaVO> membersList = new ArrayList<MemberMetaVO>();
        Set<String> seen = new HashSet<String>();
        Class<?> originalClazz = clazz;
        boolean invokeCallAllowed = ConfigPool.getPool().isInvokeCallAllowed();

        // Walk superclass chain
        while (clazz != null) {
            // Collect declared methods (includes private/protected, no inherited)
            // — skip when invokeCall=false
            if (invokeCallAllowed) {
                for (Method m : clazz.getDeclaredMethods()) {
                    String sig = buildSignature(m);
                    if (seen.contains(sig)) continue;
                    seen.add(sig);

                    MemberMetaVO vo = new MemberMetaVO();
                    vo.setName(m.getName());
                    vo.setReturnType(m.getReturnType().getSimpleName());
                    vo.setStatic(Modifier.isStatic(m.getModifiers()));
                    vo.setField(false);
                    vo.setDeclaringClass(m.getDeclaringClass().getSimpleName());

                    Class<?>[] paramTypes = m.getParameterTypes();
                    String[] paramTypeNames = new String[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        paramTypeNames[i] = paramTypes[i].getSimpleName();
                    }
                    vo.setParamTypes(paramTypeNames);
                    membersList.add(vo);
                }
            }

            // Collect declared fields (includes private/protected)
            for (Field f : clazz.getDeclaredFields()) {
                String fieldKey = "field:" + f.getName();
                if (seen.contains(fieldKey)) continue;
                seen.add(fieldKey);

                MemberMetaVO vo = new MemberMetaVO();
                vo.setName(f.getName());
                vo.setReturnType(f.getType().getSimpleName());
                vo.setStatic(Modifier.isStatic(f.getModifiers()));
                vo.setField(true);
                vo.setDeclaringClass(f.getDeclaringClass().getSimpleName());
                vo.setParamTypes(new String[0]);
                membersList.add(vo);
            }
            clazz = clazz.getSuperclass();
        }

        // Interface methods (public methods including interface defaults)
        // — skip when invokeCall=false
        if (invokeCallAllowed) {
            for (Method m : originalClazz.getMethods()) {
                String sig = buildSignature(m);
                if (seen.contains(sig)) continue;
                seen.add(sig);

                MemberMetaVO vo = new MemberMetaVO();
                vo.setName(m.getName());
                vo.setReturnType(m.getReturnType().getSimpleName());
                vo.setStatic(Modifier.isStatic(m.getModifiers()));
                vo.setField(false);
                vo.setDeclaringClass(m.getDeclaringClass().getSimpleName());

                Class<?>[] paramTypes = m.getParameterTypes();
                String[] paramTypeNames = new String[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    paramTypeNames[i] = paramTypes[i].getSimpleName();
                }
                vo.setParamTypes(paramTypeNames);
                membersList.add(vo);
            }
        }

        return membersList;
    }

    /**
     * List all methods for a given class name.
     * Helps the UI user discover available methods before constructing the call tree.
     */
    @TracerPath(value = "/invoke/methods.json", order = 3)
    public DataRet<List<MethodMetaVO>> methods(@TraceParam("className") String className) {
        if (className == null || className.isEmpty()) {
            return new DataRet<List<MethodMetaVO>>(false, -1, "className is required");
        }

        try {
            // Find class across classloaders (reuse the same pattern as InvokeEngine)
            Class<?> clazz = findClass(className);
            if (clazz == null) {
                return new DataRet<List<MethodMetaVO>>(false, -1, "class not found: " + className);
            }

            List<MethodMetaVO> methodsList = new ArrayList<MethodMetaVO>();
            Set<String> seen = new HashSet<String>(); // deduplicate by signature


            // Walk superclass chain, but stop at Object (exclude Object's methods)
            while (clazz != null && !clazz.equals(Object.class)) {
                // Collect declared methods (includes private/protected, no inherited)
                for (Method m : clazz.getDeclaredMethods()) {
                    String sig = buildSignature(m);
                    if (seen.contains(sig)) continue;
                    seen.add(sig);

                    MethodMetaVO vo = new MethodMetaVO();
                    vo.setName(m.getName());
                    vo.setReturnType(m.getReturnType().getSimpleName());
                    vo.setStatic(Modifier.isStatic(m.getModifiers()));
                    vo.setDeclaringClass(m.getDeclaringClass().getSimpleName());

                    Class<?>[] paramTypes = m.getParameterTypes();
                    String[] paramTypeNames = new String[paramTypes.length];
                    String[] paramTypeFqnNames = new String[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        paramTypeNames[i] = paramTypes[i].getSimpleName();
                        paramTypeFqnNames[i] = paramTypes[i].getName();
                    }
                    vo.setParamTypes(paramTypeNames);
                    vo.setParamTypeFqns(paramTypeFqnNames);

                    methodsList.add(vo);
                }
                clazz = clazz.getSuperclass();
            }

            DataRet<List<MethodMetaVO>> ret = new DataRet<List<MethodMetaVO>>(true, 0,
                ConfigPool.getPool().isInvokeCallAllowed() ? "ok" : "ok:invokeCall=false");
            ret.setData(methodsList);
            return ret;

        } catch (Exception e) {
            return new DataRet<List<MethodMetaVO>>(false, -1, "error: " + e.getMessage());
        }
    }

    /**
     * Search for loaded classes matching a name prefix.
     * Used by the Browse Class modal to let users find and select classes.
     * Returns up to 100 matching classes with their ClassLoader info, sorted alphabetically.
     */
    /**
     * Debug endpoint — dumps Instrumentation state and class loader summary
     */
    @TracerPath(value = "/invoke/debug.json", order = 99)
    public DataRet<String> debug() {
        try {
            ConfigPool pool = ConfigPool.getPool();
            Instrumentation inst = pool.getInst();

            StringBuilder sb = new StringBuilder();
            sb.append("instNull=").append(inst == null);

            if (inst != null) {
                Class<?>[] allClasses = inst.getAllLoadedClasses();
                sb.append(", totalClasses=").append(allClasses.length);

                int bootstrapCount = 0;
                int appClassLoaderCount = 0;
                int spyClassLoaderCount = 0;
                int otherClassLoaderCount = 0;

                java.util.Map<String, Integer> loaderNameCounts = new java.util.LinkedHashMap<String, Integer>();

                for (Class<?> clazz : allClasses) {
                    ClassLoader loader = clazz.getClassLoader();
                    if (loader == null) {
                        bootstrapCount++;
                    } else {
                        String loaderName = loader.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(loader));
                        Integer cnt = loaderNameCounts.get(loaderName);
                        loaderNameCounts.put(loaderName, cnt == null ? 1 : cnt + 1);

                        if (loader.getClass().getName().contains("SpyClassLoader")) {
                            spyClassLoaderCount++;
                        } else if (loader.getClass().getName().contains("AppClassLoader") || loader.getClass().getName().contains("LaunchedURLClassLoader")) {
                            appClassLoaderCount++;
                        } else {
                            otherClassLoaderCount++;
                        }
                    }
                }

                sb.append(", bootstrap=").append(bootstrapCount);
                sb.append(", app=").append(appClassLoaderCount);
                sb.append(", spy=").append(spyClassLoaderCount);
                sb.append(", other=").append(otherClassLoaderCount);

                sb.append(", loaders=").append(loaderNameCounts);

                // Check com.english classes
                int englishCount = 0;
                for (Class<?> clazz : allClasses) {
                    if (clazz.getName().startsWith("com.english")) englishCount++;
                }
                sb.append(", englishClasses=").append(englishCount);
            }

            return new DataRet<String>(true, 0, sb.toString());
        } catch (Exception e) {
            return new DataRet<String>(false, -1, "error: " + e.getMessage());
        }
    }

    @TracerPath(value = "/invoke/classes.json", order = 4)
    public DataRet<List<ClassSearchVO>> classes(@TraceParam("prefix") String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new DataRet<List<ClassSearchVO>>(false, -1, "prefix is required");
        }

        try {
            ConfigPool pool = ConfigPool.getPool();
            Instrumentation inst = pool.getInst();
            if (inst == null) {
                return new DataRet<List<ClassSearchVO>>(false, -1, "Instrumentation not available");
            }

            // Step 1: Build a map of className → loaderId from known LoaderSets
            java.util.Map<String, Integer> classLoaderMap = new java.util.LinkedHashMap<String, Integer>();
            for (LoaderSet ls : pool.getAllLoadedClasses()) {
                for (String name : ls.getClassNames().keySet()) {
                    // Convert internal form (com/ali/xxx) to binary name (com.ali.xxx)
                    String binaryName = name.replace('/', '.');
                    // Only store the first (most relevant) loaderId for each class
                    if (!classLoaderMap.containsKey(binaryName)) {
                        classLoaderMap.put(binaryName, ls.getId());
                    }
                }
            }

            // Step 2: Search all loaded classes by prefix, enrich with loaderId
            String lowerPrefix = prefix.toLowerCase();
            List<ClassSearchVO> result = new ArrayList<ClassSearchVO>();
            java.util.Set<String> addedNames = new HashSet<String>();

            // First: classes from LoaderSets (have known loaderId)
            for (java.util.Map.Entry<String, Integer> entry : classLoaderMap.entrySet()) {
                String name = entry.getKey();
                if (name.toLowerCase().contains(lowerPrefix)) {
                    if (!addedNames.contains(name)) {
                        result.add(new ClassSearchVO(name, entry.getValue()));
                        addedNames.add(name);
                    }
                }
            }

            // Second: remaining classes from Instrumentation (loaderId=0 for unknown loaders)
            Class<?>[] allClasses = inst.getAllLoadedClasses();
            for (Class<?> clazz : allClasses) {
                String name = clazz.getName();
                if (name.toLowerCase().contains(lowerPrefix) && !addedNames.contains(name)) {
                    result.add(new ClassSearchVO(name, 0));
                    addedNames.add(name);
                }
            }

            // Sort alphabetically by name
            java.util.Collections.sort(result, new java.util.Comparator<ClassSearchVO>() {
                public int compare(ClassSearchVO a, ClassSearchVO b) {
                    return a.getName().compareTo(b.getName());
                }
            });

            // Limit to 100 results
            if (result.size() > 100) {
                result = result.subList(0, 100);
            }

            DataRet<List<ClassSearchVO>> ret = new DataRet<List<ClassSearchVO>>(true, 0, "ok");
            ret.setData(result);
            return ret;

        } catch (Exception e) {
            return new DataRet<List<ClassSearchVO>>(false, -1, "error: " + e.getMessage());
        }
    }

    /**
     * List all methods and fields for a given class name.
     * Unified member listing for the Browse Class chain UI.
     * Returns methods and fields together as MemberMetaVO objects.
     */
    @TracerPath(value = "/invoke/members.json", order = 5)
    public DataRet<List<MemberMetaVO>> members(@TraceParam("className") String className) {
        if (className == null || className.isEmpty()) {
            return new DataRet<List<MemberMetaVO>>(false, -1, "className is required");
        }

        try {
            Class<?> clazz = findClass(className);
            if (clazz == null) {
                return new DataRet<List<MemberMetaVO>>(false, -1, "class not found: " + className);
            }

            List<MemberMetaVO> membersList = new ArrayList<MemberMetaVO>();
            Set<String> seen = new HashSet<String>(); // deduplicate by name+signature
            Class<?> originalClazz = clazz;
            boolean invokeCallAllowed = ConfigPool.getPool().isInvokeCallAllowed();

            // Walk superclass chain, but stop at Object (exclude Object's methods)
            while (clazz != null && !clazz.equals(Object.class)) {
                // Collect declared methods (includes private/protected, no inherited)
                // — skip when invokeCall=false
                if (invokeCallAllowed) {
                    for (Method m : clazz.getDeclaredMethods()) {
                        String sig = buildSignature(m);
                        if (seen.contains(sig)) continue;
                        seen.add(sig);

                        MemberMetaVO vo = new MemberMetaVO();
                        vo.setName(m.getName());
                        vo.setReturnType(m.getReturnType().getSimpleName());
                        vo.setStatic(Modifier.isStatic(m.getModifiers()));
                        vo.setField(false);
                        vo.setDeclaringClass(m.getDeclaringClass().getSimpleName());

                        Class<?>[] paramTypes = m.getParameterTypes();
                        String[] paramTypeNames = new String[paramTypes.length];
                        String[] paramTypeFqnNames = new String[paramTypes.length];
                        for (int i = 0; i < paramTypes.length; i++) {
                            paramTypeNames[i] = paramTypes[i].getSimpleName();
                            paramTypeFqnNames[i] = paramTypes[i].getName();
                        }
                        vo.setParamTypes(paramTypeNames);
                        vo.setParamTypeFqns(paramTypeFqnNames);

                        membersList.add(vo);
                    }
                }

                // Collect fields (declared fields — includes private/protected)
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    String fieldKey = "field:" + f.getName();
                    if (seen.contains(fieldKey)) continue;
                    seen.add(fieldKey);

                    MemberMetaVO vo = new MemberMetaVO();
                    vo.setName(f.getName());
                    vo.setReturnType(f.getType().getSimpleName());
                    vo.setStatic(Modifier.isStatic(f.getModifiers()));
                    vo.setField(true);
                    vo.setDeclaringClass(f.getDeclaringClass().getSimpleName());
                    vo.setParamTypes(new String[0]); // fields have no params
                    vo.setParamTypeFqns(new String[0]);

                    membersList.add(vo);
                }
                clazz = clazz.getSuperclass();
            }

            // Also include public methods from interfaces that the class implements.
            // Exclude methods declared by Object.class (equals, hashCode, toString, etc.)
            // — skip when invokeCall=false
            if (invokeCallAllowed) {
                for (Method m : originalClazz.getMethods()) {
                    if (m.getDeclaringClass().equals(Object.class)) continue;
                    String sig = buildSignature(m);
                    if (seen.contains(sig)) continue;
                    seen.add(sig);

                    MemberMetaVO vo = new MemberMetaVO();
                    vo.setName(m.getName());
                    vo.setReturnType(m.getReturnType().getSimpleName());
                    vo.setStatic(Modifier.isStatic(m.getModifiers()));
                    vo.setField(false);
                    vo.setDeclaringClass(m.getDeclaringClass().getSimpleName());

                    Class<?>[] paramTypes = m.getParameterTypes();
                    String[] paramTypeNames = new String[paramTypes.length];
                    String[] paramTypeFqnNames = new String[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        paramTypeNames[i] = paramTypes[i].getSimpleName();
                        paramTypeFqnNames[i] = paramTypes[i].getName();
                    }
                    vo.setParamTypes(paramTypeNames);
                    vo.setParamTypeFqns(paramTypeFqnNames);

                    membersList.add(vo);
                }
            }

            DataRet<List<MemberMetaVO>> ret = new DataRet<List<MemberMetaVO>>(true, 0, "ok");
            ret.setData(membersList);
            return ret;

        } catch (Exception e) {
            return new DataRet<List<MemberMetaVO>>(false, -1, "error: " + e.getMessage());
        }
    }

    /**
     * Find a class by name across all loaded ClassLoaders.
     * Same logic as InvokeEngine.findClass() but accessible from handler.
     */
    private Class<?> findClass(String className) {
        ConfigPool pool = ConfigPool.getPool();

        // Strategy 1: Try all loaded ClassLoaders
        for (LoaderSet ls : pool.getAllLoadedClasses()) {
            try {
                return ls.getLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                // Not in this ClassLoader, try next
            }
        }

        // Strategy 2: Instrumentation.getAllLoadedClasses()
        Instrumentation inst = pool.getInst();
        if (inst != null) {
            Class<?>[] allClasses = inst.getAllLoadedClasses();
            for (Class<?> clazz : allClasses) {
                if (clazz.getName().equals(className)) {
                    return clazz;
                }
            }
        }

        // Strategy 3: Class.forName
        try {
            return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
        }

        return null;
    }

    private String buildSignature(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName()).append("(");
        Class<?>[] pts = m.getParameterTypes();
        for (int i = 0; i < pts.length; i++) {
            sb.append(pts[i].getName());
            if (i < pts.length - 1) sb.append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    // ========== Param Capture Weave ==========

    /**
     * Weave a class+method for parameter capture.
     * Resolves a precise Executable (Method or Constructor) using className + methodName + paramTypes,
     * stores it in ConfigPool, and retransforms the class.
     *
     * @param className JVM internal slash format (e.g. "com/example/Service")
     * @param methodName original method name (e.g. "foo" or "<init>") — NOT sanitized
     * @param paramTypesStr comma-separated fully qualified parameter type names (e.g. "java.lang.String,int")
     */
    @TracerPath(value = "/invoke/weave.json", order = 6)
    public DataRet<String> weave(@TraceParam("className") String className,
        @TraceParam("methodName") String methodName,
        @TraceParam("paramTypes") String paramTypesStr) throws IOException {
        DataRet<String> ret;
        try {
            ConfigPool pool = ConfigPool.getPool();

            // Convert className from internal slash format to dot format (preserving $ signs)
            String dotName = className.replace('/', '.');

            // Find the target class across classloaders
            Class<?> clazz = findClass(dotName);
            if (clazz == null) {
                ret = new DataRet<String>(false, -1, "class not found: " + dotName);
                return ret;
            }

            // Parse paramTypes from comma-separated FQN string
            Class<?>[] paramClasses = null;
            if (paramTypesStr != null && !paramTypesStr.trim().isEmpty()) {
                String[] typeNames = paramTypesStr.split(",");
                paramClasses = new Class<?>[typeNames.length];
                for (int i = 0; i < typeNames.length; i++) {
                    String typeName = typeNames[i].trim();
                    Class<?> resolved = InvokeEngine.resolveParamClass(typeName);
                    if (resolved == null) {
                        ret = new DataRet<String>(false, -1, "param type not found: " + typeName);
                        return ret;
                    }
                    paramClasses[i] = resolved;
                }
            }

            // Resolve Executable (Method or Constructor)
            java.lang.reflect.Executable target;
            try {
                if ("<init>".equals(methodName) || "_init_".equals(methodName)) {
                    // Constructor
                    if (paramClasses == null || paramClasses.length == 0) {
                        target = clazz.getDeclaredConstructor();
                    } else {
                        target = clazz.getDeclaredConstructor(paramClasses);
                    }
                } else {
                    // Regular method
                    if (paramClasses == null || paramClasses.length == 0) {
                        // No-arg method — may be ambiguous for overloaded no-arg methods
                        target = clazz.getDeclaredMethod(methodName);
                    } else {
                        target = clazz.getDeclaredMethod(methodName, paramClasses);
                    }
                }
                target.setAccessible(true);
            } catch (NoSuchMethodException e) {
                // Fallback: walk superclass chain for methods (not constructors)
                if (!"<init>".equals(methodName) && !"_init_".equals(methodName)) {
                    target = findMethodInSuperclassChain(clazz, methodName, paramClasses);
                    if (target != null) {
                        target.setAccessible(true);
                    }
                } else {
                    target = null;
                }
                if (target == null) {
                    // Fallback: backward compat — resolve by name only (imprecise for overloaded methods)
                    if (paramClasses == null || paramClasses.length == 0) {
                        pool.setParamCaptureTarget(className, methodName);
                        // Continue with retransform using name-only matching
                        Instrumentation inst = pool.getInst();
                        if (inst == null) {
                            ret = new DataRet<String>(false, -1, "instrumentation not available");
                            return ret;
                        }
                        synchronized (pool) {
                            pool.setRedefine(clazz.getClassLoader(), className);
                            inst.retransformClasses(clazz);
                            pool.clearRedefine();
                        }
                        ret = new DataRet<String>(true, 0, "weave success (name-only, imprecise): " + dotName + "#" + methodName);
                        return ret;
                    }
                    ret = new DataRet<String>(false, -1, "method not found: " + methodName + " in " + dotName + " with paramTypes " + paramTypesStr);
                    return ret;
                }
            }

            // Before setting new target, save old weave class info for cleanup
            String oldInternalClassName = pool.getParamCaptureInternalClassName();

            // Set param capture target (also auto-adds include prefix for new class,
            // removes include prefix for old class)
            pool.setParamCaptureTarget(target);

            Instrumentation inst = pool.getInst();
            if (inst == null) {
                ret = new DataRet<String>(false, -1, "instrumentation not available");
                return ret;
            }

            // Retransform old weave class to remove param capture instrumentation
            // (now that include prefix has been removed, retransform will not re-inject)
            // Failure to retransform old class is non-critical — it won't block the new weave.
            if (oldInternalClassName != null && !oldInternalClassName.equals(className)) {
                try {
                    String oldDotName = oldInternalClassName.replace('/', '.').replaceAll("\\$", ".");
                    Class<?> oldClazz = findClass(oldDotName);
                    if (oldClazz != null) {
                        synchronized (pool) {
                            pool.setRedefine(oldClazz.getClassLoader(), oldInternalClassName);
                            inst.retransformClasses(oldClazz);
                            pool.clearRedefine();
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[tracer] unweave old class failed (non-critical): " + e.getMessage());
                }
            }

            // Retransform new class to add param capture instrumentation
            synchronized (pool) {
                pool.setRedefine(clazz.getClassLoader(), className);
                inst.retransformClasses(clazz);
                pool.clearRedefine();
            }

            ret = new DataRet<String>(true, 0, "weave success: " + dotName + "#" + methodName);
        } catch (Exception e) {
            e.printStackTrace();
            ret = new DataRet<String>(false, -1, "weave failed: " + e.getMessage());
        }
        return ret;
    }

    /**
     * Walk superclass chain to find a declared method by name + param types.
     */
    private java.lang.reflect.Executable findMethodInSuperclassChain(Class<?> clazz, String methodName, Class<?>[] paramClasses) {
        Class<?> current = clazz.getSuperclass();
        while (current != null && current != Object.class) {
            try {
                if (paramClasses == null || paramClasses.length == 0) {
                    return current.getDeclaredMethod(methodName);
                } else {
                    return current.getDeclaredMethod(methodName, paramClasses);
                }
            } catch (NoSuchMethodException e) {
                // Not in this class, try next
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Retrieve captured method parameters from the interceptor.
     */
    @TracerPath(value = "/invoke/captured-params.json", order = 7)
    public DataRet<List<ParamInfo>> capturedParams() throws IOException {
        DataRet<List<ParamInfo>> ret;
        try {
            Object[] params = BaseInterceptor.getParams();

            List<ParamInfo> paramInfos = new ArrayList<ParamInfo>();
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object p = params[i];
                    String type = p != null ? p.getClass().getName() : "null";
                    String value = p != null ? String.valueOf(p) : "null";
                    // Truncate long values
                    if (value.length() > 200) {
                        value = value.substring(0, 200) + "...";
                    }
                    // First element (index 0) is the receiver (this) or null (static method)
                    paramInfos.add(new ParamInfo(type, value, i == 0));
                }
            }

            ret = new DataRet<List<ParamInfo>>(true, 0, "ok");
            ret.setData(paramInfos);
        } catch (Exception e) {
            ret = new DataRet<List<ParamInfo>>(false, -1, "error: " + e.getMessage());
        }
        return ret;
    }

    private static Object invokeCapture(Object[] params) throws Throwable {
        Object target = params[0];
        if (target == null) {
            throw new RuntimeException("no capture target set");
        }
        if (target instanceof java.lang.reflect.Constructor) {
            throw new RuntimeException("cannot invokeCapture on a constructor");
        }
        Method method = (Method) target;
        Object receiver = params[1];
        Object[] args = Arrays.copyOfRange(params, 2, params.length);
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Get current weave status (paramCapture target + captured params).
     * Used by the invoke page to restore weave state on page load/refresh.
     */
    @TracerPath(value = "/invoke/weave-status.json", order = 7)
    public DataRet<WeaveStatusVO> weaveStatus() throws IOException {
        DataRet<WeaveStatusVO> ret;
        try {
            ConfigPool pool = ConfigPool.getPool();
            String className = pool.getParamCaptureDotClassName();
            String methodName = pool.getParamCaptureMethodName();
            String internalClassName = pool.getParamCaptureInternalClassName();
            String[] paramTypeNames = pool.getParamCaptureParamTypeNames();

            WeaveStatusVO status = new WeaveStatusVO();
            if (className != null) {
                // className is already in dot format from ConfigPool
                status.setClassName(className);
                status.setMethodName(methodName != null ? methodName : "");
                status.setInternalClassName(internalClassName);  // slash format for API calls
                status.setParamTypes(paramTypeNames != null ? paramTypeNames : new String[0]);
            }

            // Also include captured params
            Object[] params = BaseInterceptor.getParams();
            List<ParamInfo> paramInfos = new ArrayList<ParamInfo>();
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object p = params[i];
                    String type = p != null ? p.getClass().getName() : "null";
                    String value = p != null ? String.valueOf(p) : "null";
                    if (value.length() > 200) {
                        value = value.substring(0, 200) + "...";
                    }
                    paramInfos.add(new ParamInfo(type, value, i == 0));
                }
            }
            status.setCapturedParams(paramInfos);

            ret = new DataRet<WeaveStatusVO>(true, 0, "ok");
            ret.setData(status);
        } catch (Exception e) {
            ret = new DataRet<WeaveStatusVO>(false, -1, "error: " + e.getMessage());
        }
        return ret;
    }


    /**
     * Remove param capture weave from a class.
     */
    @TracerPath(value = "/invoke/unweave.json", order = 8)
    public DataRet<String> unweave(@TraceParam("className") String className) throws IOException {
        DataRet<String> ret;
        try {
            ConfigPool pool = ConfigPool.getPool();
            pool.clearParamCaptureTarget();

            // Retransform the class again without param capture
            String dotName = className.replace('/', '.').replaceAll("\\$", ".");
            Class<?> clazz = findClass(dotName);
            if (clazz == null) {
                ret = new DataRet<String>(true, 0, "unweave ok (class not found, no retransform needed)");
                return ret;
            }

            Instrumentation inst = pool.getInst();
            if (inst != null) {
                synchronized (pool) {
                    pool.setRedefine(clazz.getClassLoader(), className);
                    inst.retransformClasses(clazz);
                    pool.clearRedefine();
                }
            }

            ret = new DataRet<String>(true, 0, "unweave success: " + dotName);
        } catch (Exception e) {
            e.printStackTrace();
            ret = new DataRet<String>(false, -1, "unweave failed: " + e.getMessage());
        }
        return ret;
    }

    /**
     * Clear captured params (set interceptParams to null).
     */
    @TracerPath(value = "/invoke/clear-params.json", order = 8)
    public DataRet<String> clearParams() throws IOException {
        BaseInterceptor.setParams(null);
        return new DataRet<String>(true, 0, "ok");
    }

    /**
     * Configure the max display length for return values and exceptions.
     * Both frontend (JS truncate) and backend (InvokeEngine.maxValueLen) use this value.
     */
    @TracerPath(value = "/invoke/config-max-len.json", order = 11)
    public DataRet<String> configMaxLen(@TraceParam("maxLen") String maxLen) {
        try {
            int len = Integer.parseInt(maxLen);
            if (len < 60) len = 60;
            if (len > 10000) len = 10000;
            InvokeEngine.setMaxValueLen(len);
            return new DataRet<String>(true, 0, "max display length set to " + len);
        } catch (NumberFormatException e) {
            return new DataRet<String>(false, -1, "invalid maxLen: " + maxLen);
        }
    }

    /**
     * Simple value object for a captured parameter
     */
    public static class ParamInfo {
        private String type;
        private String value;
        private boolean isReceiver;

        public ParamInfo(String type, String value) {
            this.type = type;
            this.value = value;
            this.isReceiver = false;
        }

        public ParamInfo(String type, String value, boolean isReceiver) {
            this.type = type;
            this.value = value;
            this.isReceiver = isReceiver;
        }

        public String getType() { return type; }
        public String getValue() { return value; }
        public boolean getIsReceiver() { return isReceiver; }

        @Override
        public String toString() {
            return "{\"type\":\"" + escapeJson(type) + "\",\"value\":\"" + escapeJson(value) + "\",\"isReceiver\":" + isReceiver + "}";
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}