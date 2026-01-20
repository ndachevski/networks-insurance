import java.util.HashMap;
import java.util.Map;

/**
 * Protocol.java - define message format and how to parse/create json message for game protocol
 * we use simple json-like format to send message between client and server. this class handle converting
 * between string message and map so we can easily access message field
 */
public class Protocol {
    
    /**
     * parse json-like message string into map. message come like: {"type":"LOGIN","username":"bob","password":"secret"}
     * we extract each field and put it in map so code can access by name instead of parsing each time
     */
    public static Map<String, Object> parseMessage(String message) {
        Map<String, Object> result = new HashMap<>();
        message = message.trim();
        
        // message must start with { and end with } to be valid json
        if (!message.startsWith("{") || !message.endsWith("}")) {
            return result;
        }
        
        // remove the outer curly braces so we just have content inside
        message = message.substring(1, message.length() - 1).trim();
        
        // if message empty after removing braces, return empty result
        if (message.isEmpty()) {
            return result;
        }
        
        // parse key-value pair from message. some value might be nested object (like data field)
        int i = 0;
        while (i < message.length()) {
            // skip any whitespace
            while (i < message.length() && Character.isWhitespace(message.charAt(i))) {
                i++;
            }
            if (i >= message.length()) break;
            
            // parse key - key come between double quote mark
            if (message.charAt(i) != '"') break;
            int keyStart = i + 1;
            int keyEnd = message.indexOf('"', keyStart);
            if (keyEnd == -1) break;
            String key = message.substring(keyStart, keyEnd);
            
            // skip until we find the colon
            i = keyEnd + 1;
            while (i < message.length() && message.charAt(i) != ':') {
                i++;
            }
            if (i >= message.length()) break;
            i++; // skip the colon
            
            // skip whitespace after colon
            while (i < message.length() && Character.isWhitespace(message.charAt(i))) {
                i++;
            }
            if (i >= message.length()) break;
            
            // parse value - can be string or nested object
            Object value;
            if (message.charAt(i) == '"') {
                // string value come between quote
                int valueStart = i + 1;
                int valueEnd = message.indexOf('"', valueStart);
                if (valueEnd == -1) break;
                value = message.substring(valueStart, valueEnd);
                i = valueEnd + 1;
            } else if (message.charAt(i) == '{') {
                // nested object - like data field with x,y coordinate for move
                // we need to find where nested object end by counting opening and closing brace
                int braceCount = 1; // start with 1 because we already seen opening brace
                int objStart = i + 1; // position right after opening brace
                i++; // move past the opening brace
                // go through content until we find matching closing brace
                // we increment braceCount when see {, decrement when see }, stop when count reach 0
                while (i < message.length() && braceCount > 0) {
                    if (message.charAt(i) == '{') braceCount++; // found nested opening brace
                    else if (message.charAt(i) == '}') braceCount--; // found closing brace
                    i++;
                }
                // extract nested object content (everything between the braces)
                String objContent = message.substring(objStart, i - 1);
                // recursively parse nested object
                Map<String, String> dataMap = parseNestedObject(objContent);
                value = dataMap;
            } else {
                // simple value (shouldn't happen but handle it)
                int valueEnd = i;
                while (valueEnd < message.length() && message.charAt(valueEnd) != ',' && message.charAt(valueEnd) != '}') {
                    valueEnd++;
                }
                value = message.substring(i, valueEnd).trim();
                i = valueEnd;
            }
            
            // add to result map
            result.put(key, value);
            
            // skip to next comma or end
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
     * parse nested object inside message like data field. this have x,y coordinate for move
     */
    private static Map<String, String> parseNestedObject(String content) {
        Map<String, String> result = new HashMap<>();
        content = content.trim();
        if (content.isEmpty()) {
            return result;
        }
        
        int i = 0;
        while (i < content.length()) {
            // skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;
            
            // parse key
            if (content.charAt(i) != '"') break;
            int keyStart = i + 1;
            int keyEnd = content.indexOf('"', keyStart);
            if (keyEnd == -1) break;
            String key = content.substring(keyStart, keyEnd);
            
            // skip to colon
            i = keyEnd + 1;
            while (i < content.length() && content.charAt(i) != ':') {
                i++;
            }
            if (i >= content.length()) break;
            i++; // skip colon
            
            // skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;
            
            // parse value (between quote)
            if (content.charAt(i) != '"') break;
            int valueStart = i + 1;
            int valueEnd = content.indexOf('"', valueStart);
            if (valueEnd == -1) break;
            String value = content.substring(valueStart, valueEnd);
            result.put(key, value);
            
            i = valueEnd + 1;
            
            // skip to next comma
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
     * convert map to json message string so it can be send over network.
     * reverse operation of parseMessage
     */
    public static String createMessage(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        
        // go through each field in map
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            // add key
            sb.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof Map) {
                // if value is nested map (like data field with x,y), convert it too
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
                // string value - put between quote
                sb.append("\"").append(value.toString()).append("\"");
            }
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * create simple message quickly with type and optional field.
     * convenience method so don't have to create map manually
     */
    public static String createSimpleMessage(String type, String... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        
        // add optional key-value pair (come in pair so i += 2)
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length) {
                map.put(keyValues[i], keyValues[i + 1]);
            }
        }
        
        return createMessage(map);
    }
    
    /**
     * create error message to send back to client when something wrong happen
     */
    public static String createErrorMessage(String error) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ERROR");
        map.put("message", error);
        return createMessage(map);
    }
    
    /**
     * create success message to send back to client when operation success
     */
    public static String createSuccessMessage(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "SUCCESS");
        map.put("message", message);
        return createMessage(map);
    }
}

