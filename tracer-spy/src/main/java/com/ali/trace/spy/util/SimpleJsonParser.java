package com.ali.trace.spy.util;

import com.ali.trace.spy.jetty.vo.MethodCallNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JSON parser for MethodCallNode structure only.
 * Does not need to be a general-purpose JSON parser — the structure is predictable:
 * known field names, string values, one nested object (target), one nested array (params),
 * one string array (paramTypes).
 *
 * Follows the same manual parsing pattern as NodePool (extractString, extractLong, extractObject, extractArray).
 */
public class SimpleJsonParser {

    /**
     * Parse a JSON string into a MethodCallNode tree.
     */
    public static MethodCallNode parseMethodCallNode(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        json = json.trim();
        return parseNode(json);
    }

    /**
     * Parse one MethodCallNode from a JSON object string.
     */
    private static MethodCallNode parseNode(String json) {
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return null;
        }

        // Strip outer braces
        String content = json.substring(1, json.length() - 1).trim();

        MethodCallNode node = new MethodCallNode();

        // Extract className
        String className = extractStringValue(content, "className");
        if (className != null) {
            node.setClassName(className);
        }

        // Extract methodName
        String methodName = extractStringValue(content, "methodName");
        if (methodName != null) {
            node.setMethodName(methodName);
        }

        // Extract value (for literal nodes)
        String value = extractStringValue(content, "value");
        if (value != null) {
            node.setValue(value);
        }

        // Extract valueType (for literal nodes)
        String valueType = extractStringValue(content, "valueType");
        if (valueType != null) {
            node.setValueType(valueType);
        }

        // Extract returnType (response field, may be present)
        String returnType = extractStringValue(content, "returnType");
        if (returnType != null) {
            node.setReturnType(returnType);
        }

        // Extract returnValue (response field)
        String returnValue = extractStringValue(content, "returnValue");
        if (returnValue != null) {
            node.setReturnValue(returnValue);
        }

        // Extract exception (response field)
        String exception = extractStringValue(content, "exception");
        if (exception != null) {
            node.setException(exception);
        }

        // Extract isVoid (response field)
        String isVoidStr = extractStringValue(content, "isVoid");
        if (isVoidStr != null) {
            node.setVoid(Boolean.parseBoolean(isVoidStr));
        }

        // Extract duration (response field)
        String durationStr = extractStringValue(content, "duration");
        if (durationStr != null) {
            try { node.setDuration(Long.parseLong(durationStr)); } catch (NumberFormatException e) { /* ignore */ }
        }

        // Extract loaderId (optional, 0 = auto-detect)
        int loaderId = extractIntValue(content, "loaderId");
        if (loaderId > 0) {
            node.setLoaderId(loaderId);
        }

        // Extract index (for arrayGet nodes, -1 means not set)
        int index = extractIntValue(content, "index");
        if (index >= 0) {
            node.setIndex(index);
        }

        // Extract callType (optional, default "method")
        String callType = extractStringValue(content, "callType");
        if (callType != null && !callType.isEmpty()) {
            node.setCallType(callType);
        }

        // Extract paramTypes (string array)
        String paramTypesJson = extractObjectOrArray(content, "paramTypes");
        if (paramTypesJson != null && paramTypesJson.startsWith("[") && paramTypesJson.endsWith("]")) {
            String[] paramTypes = parseStringArray(paramTypesJson);
            node.setParamTypes(paramTypes);
        }

        // Extract target (nested object)
        String targetJson = extractObjectOrArray(content, "target");
        if (targetJson != null && targetJson.startsWith("{") && targetJson.endsWith("}")) {
            MethodCallNode target = parseNode(targetJson);
            node.setTarget(target);
        }

        // Extract params (nested array of objects)
        String paramsJson = extractObjectOrArray(content, "params");
        if (paramsJson != null && paramsJson.startsWith("[") && paramsJson.endsWith("]")) {
            List<MethodCallNode> params = parseNodeArray(paramsJson);
            node.setParams(params);
        }

        return node;
    }

    /**
     * Extract an integer value for a given key from JSON object content.
     * Returns 0 if the key is not found or the value is not a valid integer.
     */
    private static int extractIntValue(String content, String key) {
        String strValue = extractStringValue(content, key);
        if (strValue != null) {
            try { return Integer.parseInt(strValue); } catch (NumberFormatException e) { /* ignore */ }
        }
        return 0;
    }

    /**
     * Extract a string value for a given key from JSON object content.
     * Handles: "key":"value" and "key":value (unquoted)
     */
    private static String extractStringValue(String content, String key) {
        // Look for "key":"value" pattern
        String pattern = "\"" + key + "\"";
        int keyStart = findKey(content, pattern);
        if (keyStart < 0) {
            return null;
        }

        int colonPos = content.indexOf(':', keyStart + pattern.length());
        if (colonPos < 0) {
            return null;
        }

        int valueStart = colonPos + 1;
        // Skip whitespace
        while (valueStart < content.length() && content.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= content.length()) {
            return null;
        }

        if (content.charAt(valueStart) == '"') {
            // Quoted string value — find end quote, handle escaped quotes
            int endQuote = findEndQuote(content, valueStart + 1);
            if (endQuote < 0) {
                return null;
            }
            String raw = content.substring(valueStart + 1, endQuote);
            return unescapeJsonString(raw);
        } else if (content.charAt(valueStart) == 'n' && content.startsWith("null", valueStart)) {
            return null;
        } else {
            // Unquoted value (boolean, number) — find end (comma, brace, bracket, or end of content)
            int end = findUnquotedValueEnd(content, valueStart);
            String raw = content.substring(valueStart, end).trim();
            return raw;
        }
    }

    /**
     * Extract a nested JSON object or array for a given key.
     * Uses brace/bracket counting to find the complete nested structure.
     */
    private static String extractObjectOrArray(String content, String key) {
        String pattern = "\"" + key + "\"";
        int keyStart = findKey(content, pattern);
        if (keyStart < 0) {
            return null;
        }

        int colonPos = content.indexOf(':', keyStart + pattern.length());
        if (colonPos < 0) {
            return null;
        }

        int valueStart = colonPos + 1;
        while (valueStart < content.length() && content.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= content.length()) {
            return null;
        }

        char startChar = content.charAt(valueStart);
        if (startChar == '{') {
            int endBrace = findMatchingBrace(content, valueStart, '{', '}');
            if (endBrace < 0) return null;
            return content.substring(valueStart, endBrace + 1);
        } else if (startChar == '[') {
            int endBracket = findMatchingBracket(content, valueStart);
            if (endBracket < 0) return null;
            return content.substring(valueStart, endBracket + 1);
        }

        return null;
    }

    /**
     * Parse a JSON array of strings: ["a","b","c"]
     */
    private static String[] parseStringArray(String json) {
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return new String[0];
        }

        List<String> result = new ArrayList<String>();
        int pos = 0;
        while (pos < content.length()) {
            // Skip whitespace
            while (pos < content.length() && content.charAt(pos) == ' ') pos++;
            if (pos >= content.length()) break;

            if (content.charAt(pos) == '"') {
                int endQuote = findEndQuote(content, pos + 1);
                if (endQuote < 0) break;
                String raw = content.substring(pos + 1, endQuote);
                result.add(unescapeJsonString(raw));
                pos = endQuote + 1;
            } else {
                // Unquoted value
                int end = findUnquotedValueEnd(content, pos);
                result.add(content.substring(pos, end).trim());
                pos = end;
            }

            // Skip comma
            while (pos < content.length() && (content.charAt(pos) == ',' || content.charAt(pos) == ' ')) pos++;
        }

        return result.toArray(new String[result.size()]);
    }

    /**
     * Parse a JSON array of MethodCallNode objects: [{...},{...}]
     */
    private static List<MethodCallNode> parseNodeArray(String json) {
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return new ArrayList<MethodCallNode>();
        }

        List<MethodCallNode> result = new ArrayList<MethodCallNode>();
        int pos = 0;
        while (pos < content.length()) {
            // Skip whitespace
            while (pos < content.length() && content.charAt(pos) == ' ') pos++;
            if (pos >= content.length()) break;

            if (content.charAt(pos) == '{') {
                int endBrace = findMatchingBrace(content, pos, '{', '}');
                if (endBrace < 0) break;
                String nodeJson = content.substring(pos, endBrace + 1);
                MethodCallNode node = parseNode(nodeJson);
                if (node != null) {
                    result.add(node);
                }
                pos = endBrace + 1;
            } else {
                // Skip unexpected characters
                pos++;
            }

            // Skip comma
            while (pos < content.length() && (content.charAt(pos) == ',' || content.charAt(pos) == ' ')) pos++;
        }

        return result;
    }

    /**
     * Find a key pattern in content, skipping nested braces/brackets.
     */
    private static int findKey(String content, String pattern) {
        int depth = 0;
        for (int i = 0; i <= content.length() - pattern.length(); i++) {
            char c = content.charAt(i);
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (depth == 0 && content.substring(i, i + pattern.length()).equals(pattern)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find end quote for a string starting after the opening quote.
     * Handles escaped quotes (\") and escaped backslashes (\\).
     */
    private static int findEndQuote(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\' && i + 1 < content.length()) {
                i++; // skip escaped character
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find end of an unquoted value (stops at comma, brace, bracket, whitespace that is not inside a value).
     */
    private static int findUnquotedValueEnd(String content, int start) {
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r') {
                return i;
            }
        }
        return content.length();
    }

    /**
     * Find matching closing brace/bracket by counting nesting depth.
     */
    private static int findMatchingBrace(String content, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == open) depth++;
            if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findMatchingBracket(String content, int start) {
        return findMatchingBrace(content, start, '[', ']');
    }

    /**
     * Unescape JSON string: handle \\, \", \n, \r, \t
     */
    private static String unescapeJsonString(String s) {
        if (s == null || !s.contains("\\")) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}