package com.ali.trace.spy.invoke;

import com.ali.trace.spy.core.ConfigPool;
import com.ali.trace.spy.core.ConfigPool.LoaderSet;
import com.ali.trace.spy.jetty.vo.MethodCallNode;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Core recursive method invocation engine.
 * Traverses a MethodCallNode call tree, evaluates inner calls first (depth-first),
 * then uses the resolved values as parameters for outer calls.
 * Invokes methods via reflection across classloader boundaries.
 */
public class InvokeEngine {

    private static final InvokeEngine INSTANCE = new InvokeEngine();
    private static final int MAX_VALUE_LEN = 200;
    private static final int MAX_DEPTH = 20;

    public static InvokeEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Recursively evaluate a MethodCallNode call tree.
     * Each node is either a literal (resolved to a Java value) or a method call
     * (invoked via reflection). The resolvedValue field stores the actual Java Object
     * for parent nodes to use as parameters or receiver.
     */
    public MethodCallNode invoke(MethodCallNode node) {
        return invoke(node, 0);
    }

    private MethodCallNode invoke(MethodCallNode node, int depth) {
        if (node == null) {
            return node;
        }

        // --- Literal node --- (no recursion, safe to resolve regardless of depth)
        if (node.isLiteral()) {
            Object resolved = resolveLiteral(node.getValue(), node.getValueType());
            node.resolvedValue = resolved;
            node.setReturnValue(node.getValue());
            node.setReturnType(node.getValueType());
            node.setRealReturnType(resolved != null ? resolved.getClass().getName() : "");
            node.setVoid(false);
            node.setDuration(0);
            node.setException(null);
            return node;
        }

        // --- className-only node (Class.forName) --- (no recursion, safe to resolve regardless of depth)
        // When className is provided but methodName is empty/null,
        // resolve the class directly and return the Class<?> object.
        // This is useful when a parameter needs a Class object (e.g. getBean(Class)).
        if (node.getClassName() != null && !node.getClassName().isEmpty()
            && (node.getMethodName() == null || node.getMethodName().isEmpty())) {
            Class<?> clazz = findClass(node.getClassName(), node.getLoaderId());
            if (clazz == null) {
                node.setException("class not found: " + node.getClassName());
                node.setDuration(0);
                return node;
            }
            node.resolvedValue = clazz;
            node.setReturnType("Class");
            node.setRealReturnType(clazz.getName());
            node.setReturnValue(clazz.getName());
            node.setVoid(false);
            node.setDuration(0);
            node.setException(null);
            return node;
        }

        // --- Non-terminal nodes: method calls and field accesses ---
        // These involve recursive evaluation of target and params, so depth limit applies.
        // Depth limit to prevent stack overflow
        if (depth > MAX_DEPTH) {
            node.setException("max recursion depth exceeded (" + MAX_DEPTH + ")");
            node.setDuration(0);
            return node;
        }

        // --- Field access node ---
        if (node.isFieldAccess()) {
            // Step 1: Evaluate target (receiver) first
            Object receiver = null;
            if (node.getTarget() != null) {
                invoke(node.getTarget(), depth + 1);
                if (node.getTarget().getException() != null) {
                    node.setException("target invocation failed: " + node.getTarget().getException());
                    node.setDuration(0);
                    return node;
                }
                receiver = node.getTarget().resolvedValue;
            }

            // Step 2: Find class — use receiver's runtime type as fallback when className is empty
            Class<?> clazz;
            if (node.getClassName() != null && !node.getClassName().isEmpty()) {
                clazz = findClass(node.getClassName(), node.getLoaderId());
            } else if (receiver != null) {
                clazz = receiver.getClass();
            } else {
                node.setException("className is required when no target is provided");
                node.setDuration(0);
                return node;
            }
            if (clazz == null) {
                node.setException("class not found: " + node.getClassName());
                node.setDuration(0);
                return node;
            }

            java.lang.reflect.Field field = findField(clazz, node.getMethodName());
            if (field == null) {
                // Build available fields info for the error message — walk superclass chain
                StringBuilder avail = new StringBuilder();
                Class<?> availCurrent = clazz;
                while (availCurrent != null) {
                    for (java.lang.reflect.Field f : availCurrent.getDeclaredFields()) {
                        avail.append(f.getName()).append("(").append(f.getType().getSimpleName())
                            .append(") [from ").append(availCurrent.getSimpleName()).append("]; ");
                    }
                    availCurrent = availCurrent.getSuperclass();
                }
                // Show runtime class name when className is empty
                String classInfo = (node.getClassName() != null && !node.getClassName().isEmpty())
                    ? node.getClassName() : clazz.getName();
                node.setException("field not found: " + node.getMethodName() + " in " + classInfo
                    + (avail.length() > 0 ? " — available: " + avail.toString() : ""));
                node.setDuration(0);
                return node;
            }

            // Step 3: Get field value
            long start = System.currentTimeMillis();
            try {
                field.setAccessible(true);
                Object result = field.get(receiver);
                long elapsed = System.currentTimeMillis() - start;

                node.resolvedValue = result;
                node.setReturnType(field.getType().getSimpleName());
                node.setRealReturnType(result != null ? result.getClass().getName() : "");
                node.setReturnValue(formatResult(result));
                node.setVoid(false);
                node.setDuration(elapsed);
                node.setException(null);

            } catch (Throwable e) {
                long elapsed = System.currentTimeMillis() - start;
                node.setException(e.getClass().getName() + ": " + e.getMessage());
                node.setDuration(elapsed);
                node.resolvedValue = null;
            }

            return node;
        }

        // --- Array/List index access node ---
        if (node.isArrayGet()) {
            // Step 1: Evaluate target (the array or list object)
            Object receiver = null;
            if (node.getTarget() != null) {
                invoke(node.getTarget(), depth + 1);
                if (node.getTarget().getException() != null) {
                    node.setException("target invocation failed: " + node.getTarget().getException());
                    node.setDuration(0);
                    return node;
                }
                receiver = node.getTarget().resolvedValue;
            }
            if (receiver == null) {
                node.setException("cannot access index on null target");
                node.setDuration(0);
                return node;
            }

            int idx = node.getIndex();

            // Step 2: Get value by index
            long start = System.currentTimeMillis();
            try {
                Object result;
                Class<?> componentType;

                if (receiver.getClass().isArray()) {
                    // Native array access
                    result = java.lang.reflect.Array.get(receiver, idx);
                    componentType = receiver.getClass().getComponentType();
                } else if (receiver instanceof java.util.List) {
                    // List access
                    java.util.List<?> list = (java.util.List<?>) receiver;
                    if (idx < 0 || idx >= list.size()) {
                        long elapsed = System.currentTimeMillis() - start;
                        node.setException("index out of bounds: " + idx + " (size=" + list.size() + ")");
                        node.setDuration(elapsed);
                        return node;
                    }
                    result = list.get(idx);
                    // For List, returnType is the generic element type hint (Object if unknown)
                    componentType = Object.class; // List.get() returns Object
                    // Try to get a more specific type from the field/method that returned this list
                    String targetReturnHint = node.getTarget().getReturnType();
                    if (targetReturnHint != null && !targetReturnHint.isEmpty()
                        && targetReturnHint.endsWith("[]")) {
                        // e.g. "String[]" → element type is "String"
                        componentType = null; // will use targetReturnHint for display below
                    }
                } else {
                    long elapsed = System.currentTimeMillis() - start;
                    node.setException("target is not an array or List: " + receiver.getClass().getName());
                    node.setDuration(elapsed);
                    return node;
                }

                long elapsed = System.currentTimeMillis() - start;
                node.resolvedValue = result;

                // Determine return type for display
                if (result != null) {
                    node.setReturnType(result.getClass().getSimpleName());
                    node.setRealReturnType(result.getClass().getName());
                } else if (componentType != null) {
                    node.setReturnType(componentType.getSimpleName());
                } else {
                    // For List with generic type hint from target
                    String targetReturnHint = node.getTarget().getReturnType();
                    if (targetReturnHint != null && targetReturnHint.endsWith("[]")) {
                        node.setReturnType(targetReturnHint.substring(0, targetReturnHint.length() - 2));
                    } else {
                        node.setReturnType("Object");
                    }
                }

                node.setReturnValue(formatResult(result));
                node.setVoid(false);
                node.setDuration(elapsed);
                node.setException(null);

            } catch (ArrayIndexOutOfBoundsException e) {
                long elapsed = System.currentTimeMillis() - start;
                int arrayLen = receiver.getClass().isArray() ? java.lang.reflect.Array.getLength(receiver) : -1;
                node.setException("index out of bounds: " + idx + " (length=" + arrayLen + ")");
                node.setDuration(elapsed);
                node.resolvedValue = null;
            } catch (Throwable e) {
                long elapsed = System.currentTimeMillis() - start;
                node.setException(e.getClass().getName() + ": " + e.getMessage());
                node.setDuration(elapsed);
                node.resolvedValue = null;
            }

            return node;
        }

        // --- Method call node ---
        if (!node.isMethodCall()) {
            node.setException("invalid node: neither method call, field access, array/list access, class reference, nor literal");
            node.setDuration(0);
            return node;
        }

        // Step 1: Evaluate target (instance method receiver)
        Object receiver = null;
        if (node.getTarget() != null) {
            invoke(node.getTarget(), depth + 1);
            if (node.getTarget().getException() != null) {
                node.setException("target invocation failed: " + node.getTarget().getException());
                node.setDuration(0);
                return node;
            }
            receiver = node.getTarget().resolvedValue;
            if (receiver == null) {
                node.setException("target returned null; cannot invoke instance method on null receiver");
                node.setDuration(0);
                return node;
            }
        }

        // Step 2: Evaluate params recursively
        Object[] args = new Object[node.getParams().size()];
        for (int i = 0; i < node.getParams().size(); i++) {
            MethodCallNode param = node.getParams().get(i);
            invoke(param, depth + 1);
            if (param.getException() != null) {
                node.setException("param[" + i + "] invocation failed: " + param.getException());
                node.setDuration(0);
                return node;
            }
            args[i] = param.resolvedValue;
        }

        // Step 3: Find class across classloaders — use receiver's runtime type as fallback when className is empty
        Class<?> clazz;
        if (node.getClassName() != null && !node.getClassName().isEmpty()) {
            clazz = findClass(node.getClassName(), node.getLoaderId());
        } else if (receiver != null) {
            clazz = receiver.getClass();
        } else {
            node.setException("className is required when no target is provided");
            node.setDuration(0);
            return node;
        }
        if (clazz == null) {
            node.setException("class not found: " + node.getClassName());
            node.setDuration(0);
            return node;
        }

        // Step 4: Resolve method
        Method method = resolveMethod(clazz, node.getMethodName(), node.getParamTypes(), args);
        if (method == null) {
            // Build available methods info for the error message
            // Walk the entire class hierarchy to show all available methods with this name
            StringBuilder avail = new StringBuilder();
            Class<?> availCurrent = clazz;
            while (availCurrent != null) {
                for (Method m : availCurrent.getDeclaredMethods()) {
                    if (m.getName().equals(node.getMethodName())) {
                        avail.append(m.getName()).append("(");
                        Class<?>[] pts = m.getParameterTypes();
                        for (int i = 0; i < pts.length; i++) {
                            avail.append(pts[i].getSimpleName());
                            if (i < pts.length - 1) avail.append(",");
                        }
                        avail.append(")").append(Modifier.isStatic(m.getModifiers()) ? " [static]" : "")
                            .append(" [from ").append(availCurrent.getSimpleName()).append("]; ");
                    }
                }
                availCurrent = availCurrent.getSuperclass();
            }
            // Also check interfaces
            for (Class<?> iface : getAllInterfaces(clazz)) {
                for (Method m : iface.getDeclaredMethods()) {
                    if (m.getName().equals(node.getMethodName())) {
                        avail.append(m.getName()).append("(");
                        Class<?>[] pts = m.getParameterTypes();
                        for (int i = 0; i < pts.length; i++) {
                            avail.append(pts[i].getSimpleName());
                            if (i < pts.length - 1) avail.append(",");
                        }
                        avail.append(")").append(Modifier.isStatic(m.getModifiers()) ? " [static]" : "")
                            .append(" [from ").append(iface.getSimpleName()).append("]; ");
                    }
                }
            }
            // Show runtime class name when className is empty
            String classInfo = (node.getClassName() != null && !node.getClassName().isEmpty())
                ? node.getClassName() : clazz.getName();
            node.setException("method not found: " + node.getMethodName() + " in " + classInfo
                + (avail.length() > 0 ? " — available: " + avail.toString() : ""));
            node.setDuration(0);
            return node;
        }

        // Step 5: Invoke via reflection
        long start = System.currentTimeMillis();
        try {
            method.setAccessible(true);
            Object result = method.invoke(receiver, args);
            long elapsed = System.currentTimeMillis() - start;

            node.setReturnType(method.getReturnType().getSimpleName());
            node.setRealReturnType(result != null ? result.getClass().getName() : "");
            node.setReturnValue(formatResult(result));
            node.setVoid(method.getReturnType() == void.class);
            node.resolvedValue = result;
            node.setDuration(elapsed);
            node.setException(null);

        } catch (InvocationTargetException e) {
            long elapsed = System.currentTimeMillis() - start;
            Throwable cause = e.getTargetException();
            node.setException(cause.getClass().getName() + ": " + cause.getMessage());
            node.setDuration(elapsed);
            node.resolvedValue = null;

        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            node.setException(e.getClass().getName() + ": " + e.getMessage());
            node.setDuration(elapsed);
            node.resolvedValue = null;
        }

        return node;
    }

    // =============================================
    // Class resolution across classloaders
    // =============================================

    // =============================================
    // Class resolution across classloaders
    // =============================================

    /**
     * Find a field by name in a class.
     * 3-tier lookup: getDeclaredField → getField → scan all fields by name
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        // Strategy 1: getDeclaredField (includes private/protected, no inherited)
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // Not declared in this class, try next
        }

        // Strategy 2: getField (public only, includes inherited)
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            // Not a public field, try next
        }

        // Strategy 3: Scan all fields (declared + inherited) by name
        for (java.lang.reflect.Field f : clazz.getFields()) {
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }

        // Strategy 4: Walk up the superclass chain for declared fields
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field f : current.getDeclaredFields()) {
                if (f.getName().equals(fieldName)) {
                    return f;
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private Class<?> findClass(String className) {
        return findClass(className, 0);
    }

    /**
     * Find a class by name across all loaded ClassLoaders.
     * When loaderId > 0, tries the specified ClassLoader first before general search.
     * Follows the same pattern as ClassHandler.fieldsJson():
     * 1. If loaderId specified, try that ClassLoader first
     * 2. Try each ClassLoader from getAllLoadedClasses()
     * 3. Fallback: Instrumentation.getAllLoadedClasses() direct match
     * 4. Fallback: Class.forName with context ClassLoader
     * 5. Fallback: Class.forName
     */
    private Class<?> findClass(String className, int loaderId) {
        ConfigPool pool = ConfigPool.getPool();

        // Strategy 0: If loaderId is specified, try that ClassLoader first
        if (loaderId > 0) {
            LoaderSet ls = pool.getLoaderSet(loaderId);
            if (ls != null) {
                try {
                    return ls.getLoader().loadClass(className);
                } catch (ClassNotFoundException e) {
                    // Not in this specific ClassLoader, continue with general search
                }
            }
        }

        // Strategy 1: Try all loaded ClassLoaders from getAllLoadedClasses()
        for (LoaderSet ls : pool.getAllLoadedClasses()) {
            try {
                return ls.getLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                // Not in this ClassLoader, try next
            }
        }

        // Strategy 2: Try inst.getAllLoadedClasses() for direct class lookup
        Instrumentation inst = pool.getInst();
        if (inst != null) {
            Class<?>[] allClasses = inst.getAllLoadedClasses();
            for (Class<?> clazz : allClasses) {
                if (clazz.getName().equals(className)) {
                    return clazz;
                }
            }
        }

        // Strategy 3: Try Class.forName with context ClassLoader
        try {
            return Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
        }

        // Strategy 4: Try Class.forName with default classloader
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
        }

        return null;
    }

    // =============================================
    // Method resolution with 3-tier fallback
    // =============================================

    /**
     * Resolve a method by name with 5-tier fallback strategy:
     * 1. Exact resolution (when paramTypes is provided)
     * 2. Runtime type matching (when paramTypes is not provided, use evaluated args types)
     * 3. Name + parameter count fallback (public methods via getMethods)
     * 4. Superclass chain walk (declared methods including protected/package-private)
     * 5. Interface hierarchy walk (interface declared methods including default)
     */
    private Method resolveMethod(Class<?> clazz, String methodName, String[] paramTypes, Object[] args) {
        // Strategy 1: Exact resolution with paramTypes
        if (paramTypes != null && paramTypes.length > 0) {
            Class<?>[] resolvedParamTypes = new Class<?>[paramTypes.length];
            boolean allResolved = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> pc = resolveParamClass(paramTypes[i]);
                if (pc == null) {
                    allResolved = false;
                    break;
                }
                resolvedParamTypes[i] = pc;
            }

            if (allResolved) {
                // Try exact match on the class and its superclass chain
                Class<?> current = clazz;
                while (current != null) {
                    try {
                        return current.getDeclaredMethod(methodName, resolvedParamTypes);
                    } catch (NoSuchMethodException e) {
                        // Not in this class, try next superclass
                    }
                    current = current.getSuperclass();
                }
                // Try getMethod (public only, includes inherited)
                try {
                    return clazz.getMethod(methodName, resolvedParamTypes);
                } catch (NoSuchMethodException e2) {
                    // Fall through to strategy 2
                }
            }
        }

        // Strategy 2: Runtime type matching using evaluated args
        // Get runtime types from args, handle primitive/wrapper compatibility
        Class<?>[] runtimeTypes = new Class<?>[args.length];
        boolean hasNullArg = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                runtimeTypes[i] = null;
                hasNullArg = true;
            } else {
                runtimeTypes[i] = args[i].getClass();
            }
        }

        if (!hasNullArg) {
            // Search declared methods in the class and its superclass chain
            Class<?> current = clazz;
            while (current != null) {
                for (Method m : current.getDeclaredMethods()) {
                    if (!m.getName().equals(methodName)) continue;
                    Class<?>[] mParamTypes = m.getParameterTypes();
                    if (mParamTypes.length != args.length) continue;

                    boolean allCompatible = true;
                    for (int i = 0; i < mParamTypes.length; i++) {
                        if (!isTypeCompatible(mParamTypes[i], runtimeTypes[i])) {
                            allCompatible = false;
                            break;
                        }
                    }
                    if (allCompatible) {
                        return m;
                    }
                }
                current = current.getSuperclass();
            }
        }

        // Strategy 3: Name + parameter count fallback (public methods via getMethods)
        Method bestMatch = null;
        for (Method m : clazz.getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            if (m.getParameterTypes().length != args.length) continue;

            if (bestMatch == null) {
                bestMatch = m;
            }
            // Prefer public methods
            if (Modifier.isPublic(m.getModifiers()) && !Modifier.isPublic(bestMatch.getModifiers())) {
                bestMatch = m;
            }
        }
        if (bestMatch != null) return bestMatch;

        // Strategy 4: Superclass chain walk — find declared methods including protected/package-private
        // (covers methods not visible via getMethods() — e.g. protected methods in superclasses)
        Class<?> current = clazz.getSuperclass();
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterTypes().length != args.length) continue;
                // Skip static methods for instance call chains
                if (Modifier.isStatic(m.getModifiers())) continue;

                if (args.length == 0) return m;  // No-arg method, direct match
                // Check type compatibility for methods with parameters
                boolean allCompatible = true;
                for (int i = 0; i < m.getParameterTypes().length; i++) {
                    if (hasNullArg && runtimeTypes[i] == null) continue;  // null arg is compatible with any type
                    if (!hasNullArg && !isTypeCompatible(m.getParameterTypes()[i], runtimeTypes[i])) {
                        allCompatible = false;
                        break;
                    }
                }
                if (allCompatible) return m;
            }
            current = current.getSuperclass();
        }

        // Strategy 5: Interface hierarchy walk — find methods declared in interfaces
        // (covers interface default methods and abstract methods not found via getMethods)
        Set<Class<?>> allInterfaces = getAllInterfaces(clazz);
        for (Class<?> iface : allInterfaces) {
            for (Method m : iface.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterTypes().length != args.length) continue;
                if (Modifier.isStatic(m.getModifiers())) continue;

                if (args.length == 0) return m;
                boolean allCompatible = true;
                for (int i = 0; i < m.getParameterTypes().length; i++) {
                    if (hasNullArg && runtimeTypes[i] == null) continue;
                    if (!hasNullArg && !isTypeCompatible(m.getParameterTypes()[i], runtimeTypes[i])) {
                        allCompatible = false;
                        break;
                    }
                }
                if (allCompatible) return m;
            }
        }

        return null;
    }

    /**
     * Collect all interfaces that a class implements (including inherited interfaces from superclasses).
     */
    private Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        Class<?> current = clazz;
        while (current != null) {
            collectInterfaces(current, interfaces);
            current = current.getSuperclass();
        }
        return interfaces;
    }

    private void collectInterfaces(Class<?> clazz, Set<Class<?>> collected) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (!collected.contains(iface)) {
                collected.add(iface);
                collectInterfaces(iface, collected);  // Recurse for super-interfaces
            }
        }
    }

    /**
     * Check if two types are compatible, handling primitive/wrapper conversions.
     */
    private boolean isTypeCompatible(Class<?> expected, Class<?> actual) {
        if (expected == actual) return true;
        if (expected == null || actual == null) return false;

        // Primitive-wrapper compatibility
        if (expected == int.class && actual == Integer.class) return true;
        if (expected == long.class && actual == Long.class) return true;
        if (expected == short.class && actual == Short.class) return true;
        if (expected == byte.class && actual == Byte.class) return true;
        if (expected == float.class && actual == Float.class) return true;
        if (expected == double.class && actual == Double.class) return true;
        if (expected == boolean.class && actual == Boolean.class) return true;
        if (expected == char.class && actual == Character.class) return true;

        // Wrapper-primitive reverse
        if (expected == Integer.class && actual == int.class) return true;
        if (expected == Long.class && actual == long.class) return true;
        if (expected == Short.class && actual == short.class) return true;
        if (expected == Byte.class && actual == byte.class) return true;
        if (expected == Float.class && actual == float.class) return true;
        if (expected == Double.class && actual == double.class) return true;
        if (expected == Boolean.class && actual == boolean.class) return true;
        if (expected == Character.class && actual == char.class) return true;

        // Object.class is compatible with any non-primitive
        if (expected == Object.class && !actual.isPrimitive()) return true;

        // Subclass/superclass
        if (expected.isAssignableFrom(actual)) return true;

        return false;
    }

    /**
     * Resolve a parameter type name to a Class<?> object.
     * Handles primitive type names, common wrapper names, and full qualified class names.
     */
    private Class<?> resolveParamClass(String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;

        // Primitive types
        switch (typeName) {
            case "int":     return int.class;
            case "long":    return long.class;
            case "short":   return short.class;
            case "byte":    return byte.class;
            case "float":   return float.class;
            case "double":  return double.class;
            case "boolean": return boolean.class;
            case "char":    return char.class;
            case "void":    return void.class;
        }

        // Common wrapper/class names (simple names)
        switch (typeName) {
            case "String":      return String.class;
            case "Integer":     return Integer.class;
            case "Long":        return Long.class;
            case "Short":       return Short.class;
            case "Byte":        return Byte.class;
            case "Float":       return Float.class;
            case "Double":      return Double.class;
            case "Boolean":     return Boolean.class;
            case "Character":   return Character.class;
            case "Object":      return Object.class;
            case "Class":       return Class.class;
        }

        // Full qualified class name — find across classloaders
        return findClass(typeName);
    }

    // =============================================
    // Literal value resolution
    // =============================================

    /**
     * Convert a string literal to a Java object by type name.
     */
    private Object resolveLiteral(String value, String valueType) {
        if (valueType == null || "null".equals(valueType)) {
            return null;
        }
        if (value == null) {
            return null;
        }

        switch (valueType) {
            case "int":
            case "Integer":
                try { return Integer.valueOf(value); } catch (NumberFormatException e) { return 0; }
            case "long":
            case "Long":
                try { return Long.valueOf(value); } catch (NumberFormatException e) { return 0L; }
            case "short":
            case "Short":
                try { return Short.valueOf(value); } catch (NumberFormatException e) { return (short)0; }
            case "byte":
            case "Byte":
                try { return Byte.valueOf(value); } catch (NumberFormatException e) { return (byte)0; }
            case "float":
            case "Float":
                try { return Float.valueOf(value); } catch (NumberFormatException e) { return 0.0f; }
            case "double":
            case "Double":
                try { return Double.valueOf(value); } catch (NumberFormatException e) { return 0.0; }
            case "boolean":
            case "Boolean":
                return Boolean.valueOf(value);
            case "char":
            case "Character":
                return value.length() > 0 ? value.charAt(0) : '\0';
            case "String":
                return value;
            default:
                // Unknown type — treat as String
                return value;
        }
    }

    // =============================================
    // Return value formatting (same as ClassHandler.formatFieldValue)
    // =============================================

    /**
     * Format a method invocation return value for display.
     * Handles null, String, primitives, arrays, collections, maps, and arbitrary objects.
     * Truncates to MAX_VALUE_LEN characters.
     */
    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        // String — show quoted value, truncated
        if (result instanceof String) {
            String s = (String) result;
            if (s.length() > MAX_VALUE_LEN) {
                s = s.substring(0, MAX_VALUE_LEN) + "...";
            }
            return "\"" + s + "\"";
        }
        // Primitive wrappers — direct value
        if (result instanceof Integer || result instanceof Long || result instanceof Double
            || result instanceof Float || result instanceof Short || result instanceof Byte
            || result instanceof Boolean || result instanceof Character) {
            return String.valueOf(result);
        }
        // Array — truncated toString
        if (result.getClass().isArray()) {
            String s;
            if (result instanceof Object[]) {
                s = Arrays.toString((Object[]) result);
            } else if (result instanceof int[]) {
                s = Arrays.toString((int[]) result);
            } else if (result instanceof long[]) {
                s = Arrays.toString((long[]) result);
            } else if (result instanceof byte[]) {
                s = Arrays.toString((byte[]) result);
            } else if (result instanceof char[]) {
                s = Arrays.toString((char[]) result);
            } else if (result instanceof short[]) {
                s = Arrays.toString((short[]) result);
            } else if (result instanceof float[]) {
                s = Arrays.toString((float[]) result);
            } else if (result instanceof double[]) {
                s = Arrays.toString((double[]) result);
            } else if (result instanceof boolean[]) {
                s = Arrays.toString((boolean[]) result);
            } else {
                s = result.toString();
            }
            if (s.length() > MAX_VALUE_LEN) {
                s = s.substring(0, MAX_VALUE_LEN) + "...";
            }
            return s;
        }
        // Collection — show size + truncated toString
        if (result instanceof java.util.Collection) {
            String s = "size=" + ((java.util.Collection) result).size() + ", " + result.toString();
            if (s.length() > MAX_VALUE_LEN) {
                s = s.substring(0, MAX_VALUE_LEN) + "...";
            }
            return s;
        }
        // Map — show size + truncated toString
        if (result instanceof java.util.Map) {
            String s = "size=" + ((java.util.Map) result).size() + ", " + result.toString();
            if (s.length() > MAX_VALUE_LEN) {
                s = s.substring(0, MAX_VALUE_LEN) + "...";
            }
            return s;
        }
        // Other objects — try toString() first, fallback to className@hashCode
        String s;
        try {
            s = result.toString();
        } catch (Throwable t) {
            // toString() can throw — use safe fallback
            s = result.getClass().getName() + "@" + Integer.toHexString(result.hashCode());
        }
        if (s.length() > MAX_VALUE_LEN) {
            s = s.substring(0, MAX_VALUE_LEN) + "...";
        }
        return s;
    }
}