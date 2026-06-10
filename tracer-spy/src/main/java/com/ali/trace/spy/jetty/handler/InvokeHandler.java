package com.ali.trace.spy.jetty.handler;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.ConfigPool.LoaderSet;
import com.ali.trace.spy.invoke.InvokeEngine;
import com.ali.trace.spy.jetty.vo.ClassSearchVO;
import com.ali.trace.spy.jetty.vo.DataRet;
import com.ali.trace.spy.jetty.vo.MemberMetaVO;
import com.ali.trace.spy.jetty.vo.MethodCallNode;
import com.ali.trace.spy.jetty.vo.MethodMetaVO;
import com.ali.trace.spy.util.SimpleJsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public String index() {
        return "invoke";
    }

    /**
     * Execute a method invocation expression.
     * Accepts POST with JSON body containing the recursive MethodCallNode tree.
     * Since ModuleHttpServlet only supports URL-encoded params via req.getParameter(),
     * this endpoint reads the JSON body directly from HttpServletRequest.getInputStream().
     */
    @TracerPath(value = "/invoke/exec", order = 2)
    public DataRet<MethodCallNode> exec(HttpServletRequest req) {
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
                return new DataRet<MethodCallNode>(false, -1, "empty request body");
            }

            // Parse JSON into MethodCallNode tree
            MethodCallNode rootNode = SimpleJsonParser.parseMethodCallNode(jsonBody);
            if (rootNode == null) {
                return new DataRet<MethodCallNode>(false, -1, "invalid JSON format");
            }

            // Invoke the engine
            rootNode = InvokeEngine.getInstance().invoke(rootNode);

            // Return result
            DataRet<MethodCallNode> ret = new DataRet<MethodCallNode>(true, 0, "ok");
            ret.setData(rootNode);
            return ret;

        } catch (Exception e) {
            return new DataRet<MethodCallNode>(false, -1, "invoke failed: " + e.getMessage());
        }
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


            while (clazz != null) {
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
                    for (int i = 0; i < paramTypes.length; i++) {
                        paramTypeNames[i] = paramTypes[i].getSimpleName();
                    }
                    vo.setParamTypes(paramTypeNames);

                    methodsList.add(vo);
                }
                clazz = clazz.getSuperclass();
            }

            DataRet<List<MethodMetaVO>> ret = new DataRet<List<MethodMetaVO>>(true, 0, "ok");
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

            while (clazz != null) {
                // Collect declared methods (includes private/protected, no inherited)
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

                    membersList.add(vo);
                }
                clazz = clazz.getSuperclass();
            }

            // Also include public methods from interfaces that the class implements.
            // getMethods() returns all accessible public methods including inherited
            // and interface methods — this covers interface default methods and methods
            // from interfaces that the class doesn't override.
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
}