// Students: CSY23102, CSY23052, CSY23031

import java.util.HashMap;
import java.util.Map;

/**
 * protocol handles all message serialization/deserialization. Uses a simple custom JSON-like
 * format instead of a third-party JSON library to keep dependencies minimal, avoid security
 * vulnerabilities from external code, and have full control over parsing. all messages are strings
 * terminated by newlines for streaming over sockets. intentionally simple to keep code auditable.
 */
public class Protocol {
    
    /**
     * parse a JSON-like message string into a map. handles nested objects for complex
     * data like board state. intentionally simple parser - doesn't support all JSON features,
     * just what we need for game protocol (strings, numbers, nested maps).
     */
    public static Map<String, Object> parseMessage(String message) {
        Map<String, Object> result = new HashMap<>();
        message = message.trim();
        
        if (!message.startsWith("{") || !message.endsWith("}")) {
            return result;
        }
        
        // Remove outer braces
        message = message.substring(1, message.length() - 1).trim();
        
        if (message.isEmpty()) {
            return result;
        }
        
        // Parse key-value pairs, handling nested objects
        int i = 0;
        while (i < message.length()) {
            // Skip whitespace
            while (i < message.length() && Character.isWhitespace(message.charAt(i))) {
                i++;
            }
            if (i >= message.length()) break;
            
            // Parse key
            if (message.charAt(i) != '"') break;
            int keyStart = i + 1;
            int keyEnd = message.indexOf('"', keyStart);
            if (keyEnd == -1) break;
            String key = message.substring(keyStart, keyEnd);
            
            // Skip to colon
            i = keyEnd + 1;
            while (i < message.length() && message.charAt(i) != ':') {
                i++;
            }
            if (i >= message.length()) break;
            i++; // Skip colon
            
            // Skip whitespace
            while (i < message.length() && Character.isWhitespace(message.charAt(i))) {
                i++;
            }
            if (i >= message.length()) break;
            
            // Parse value
            Object value;
            if (message.charAt(i) == '"') {
                // String value
                int valueStart = i + 1;
                int valueEnd = message.indexOf('"', valueStart);
                if (valueEnd == -1) break;
                value = message.substring(valueStart, valueEnd);
                i = valueEnd + 1;
            } else if (message.charAt(i) == '{') {
                // Nested object (for data field)
                int braceCount = 1;
                int objStart = i + 1;
                i++;
                while (i < message.length() && braceCount > 0) {
                    if (message.charAt(i) == '{') braceCount++;
                    else if (message.charAt(i) == '}') braceCount--;
                    i++;
                }
                String objContent = message.substring(objStart, i - 1);
                Map<String, String> dataMap = parseNestedObject(objContent);
                value = dataMap;
            } else {
                // Simple value (shouldn't happen in our protocol, but handle it)
                int valueEnd = i;
                while (valueEnd < message.length() && message.charAt(valueEnd) != ',' && message.charAt(valueEnd) != '}') {
                    valueEnd++;
                }
                value = message.substring(i, valueEnd).trim();
                i = valueEnd;
            }
            
            result.put(key, value);
            
            // Skip to next comma or end
            while (i < message.length() && message.charAt(i) != ',' && message.charAt(i) != '}') {
                i++;
            }
            if (i < message.length() && message.charAt(i) == ',') {
                i++;
            }
        }
        
        return result;
    }
    
    /**
     * parse nested object (for data field). used when board state or other complex
     * data needs to be embedded in the message
     */
    private static Map<String, String> parseNestedObject(String content) {
        Map<String, String> result = new HashMap<>();
        content = content.trim();
        if (content.isEmpty()) {
            return result;
        }
        
        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;
            
            // Parse key
            if (content.charAt(i) != '"') break;
            int keyStart = i + 1;
            int keyEnd = content.indexOf('"', keyStart);
            if (keyEnd == -1) break;
            String key = content.substring(keyStart, keyEnd);
            
            // Skip to colon
            i = keyEnd + 1;
            while (i < content.length() && content.charAt(i) != ':') {
                i++;
            }
            if (i >= content.length()) break;
            i++; // Skip colon
            
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;
            
            // Parse value
            if (content.charAt(i) != '"') break;
            int valueStart = i + 1;
            int valueEnd = content.indexOf('"', valueStart);
            if (valueEnd == -1) break;
            String value = content.substring(valueStart, valueEnd);
            result.put(key, value);
            
            i = valueEnd + 1;
            
            // Skip to next comma
            while (i < content.length() && content.charAt(i) != ',') {
                i++;
            }
            if (i < content.length() && content.charAt(i) == ',') {
                i++;
            }
        }
        
        return result;
    }
    
    /**
     * create a json message string from a map. handles nested maps for complex data
     * by serializing them as nested objects
     */
    public static String createMessage(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            sb.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof Map) {
                sb.append("{");
                boolean firstData = true;
                @SuppressWarnings("unchecked")
                Map<String, String> dataMap = (Map<String, String>) value;
                for (Map.Entry<String, String> dataEntry : dataMap.entrySet()) {
                    if (!firstData) {
                        sb.append(",");
                    }
                    firstData = false;
                    sb.append("\"").append(dataEntry.getKey()).append("\":")
                      .append("\"").append(dataEntry.getValue()).append("\"");
                }
                sb.append("}");
            } else {
                sb.append("\"").append(value.toString()).append("\"");
            }
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * create a message with type and optional key-value fields. convenience method
     * for building simple messages without constructing a map
     */
    public static String createSimpleMessage(String type, String... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length) {
                map.put(keyValues[i], keyValues[i + 1]);
            }
        }
        
        return createMessage(map);
    }
    
    /**
     * create an error message with type=ERROR and message field
     */
    public static String createErrorMessage(String error) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ERROR");
        map.put("message", error);
        return createMessage(map);
    }
    
    /**
     * create a success message with type=SUCCESS and message field
     */
    public static String createSuccessMessage(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "SUCCESS");
        map.put("message", message);
        return createMessage(map);
    }
}
