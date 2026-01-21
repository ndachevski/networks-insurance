// Students: CSY23102, CSY23052, CSY23031

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * JUnit 4 tests for Protocol (parseMessage, createMessage, round-trip, edge cases).
 * Tests the custom JSON-like message serialization/deserialization used throughout the system.
 * Ensures messages can be correctly serialized and deserialized without data loss
 */
public class ProtocolTest {

    @Test
    public void parseMessage_simpleValid() {
        // test parsing a simple message with basic fields
        Map<String, Object> got = Protocol.parseMessage("{\"type\":\"MOVE\",\"x\":\"1\",\"y\":\"2\"}");
        Assert.assertEquals("MOVE", got.get("type"));
        Assert.assertEquals("1", got.get("x"));
        Assert.assertEquals("2", got.get("y"));
    }

    @Test
    public void parseMessage_withNestedData() {
        // test parsing messages with nested objects (used for board state)
        String raw = "{\"type\":\"MOVE\",\"data\":{\"x\":\"0\",\"y\":\"2\"}}";
        Map<String, Object> got = Protocol.parseMessage(raw);
        Assert.assertEquals("MOVE", got.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) got.get("data");
        Assert.assertNotNull(data);
        Assert.assertEquals("0", data.get("x"));
        Assert.assertEquals("2", data.get("y"));
    }

    @Test
    public void parseMessage_emptyObject() {
        // test parsing empty objects returns empty map
        Map<String, Object> got = Protocol.parseMessage("{}");
        Assert.assertTrue(got.isEmpty());
    }

    @Test
    public void parseMessage_emptyString() {
        // test parsing empty string returns empty map
        Map<String, Object> got = Protocol.parseMessage("");
        Assert.assertTrue(got.isEmpty());
    }

    @Test
    public void parseMessage_missingBraces() {
        // test that malformed messages without proper braces return empty maps
        Assert.assertTrue(Protocol.parseMessage("{\"a\":\"b\"").isEmpty());
        Assert.assertTrue(Protocol.parseMessage("\"a\":\"b\"}").isEmpty());
        Assert.assertTrue(Protocol.parseMessage("no braces").isEmpty());
    }

    @Test
    public void parseMessage_whitespaceTrimmed() {
        // test that extra whitespace in messages is handled correctly
        Map<String, Object> got = Protocol.parseMessage("  { \"type\" : \"LOGIN\" }  ");
        Assert.assertEquals("LOGIN", got.get("type"));
    }

    @Test
    public void createMessage_simple() {
        // test that creating a message and parsing it back preserves data (round-trip test)
        Map<String, Object> m = new HashMap<>();
        m.put("type", "LOGIN");
        m.put("username", "alice");
        String s = Protocol.createMessage(m);
        Assert.assertTrue(s.startsWith("{"));
        Assert.assertTrue(s.endsWith("}"));
        Map<String, Object> back = Protocol.parseMessage(s);
        Assert.assertEquals("LOGIN", back.get("type"));
        Assert.assertEquals("alice", back.get("username"));
    }

    @Test
    public void createMessage_withNestedMap() {
        // test round-trip with nested objects to verify complex data survives serialization
        Map<String, Object> outer = new HashMap<>();
        outer.put("type", "MOVE");
        Map<String, String> data = new HashMap<>();
        data.put("x", "1");
        data.put("y", "2");
        outer.put("data", data);
        String s = Protocol.createMessage(outer);
        Map<String, Object> back = Protocol.parseMessage(s);
        Assert.assertEquals("MOVE", back.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, String> d = (Map<String, String>) back.get("data");
        Assert.assertNotNull(d);
        Assert.assertEquals("1", d.get("x"));
        Assert.assertEquals("2", d.get("y"));
    }

    @Test
    public void createSimpleMessage() {
        // test the helper method for creating simple key-value messages
        String s = Protocol.createSimpleMessage("CHALLENGE", "opponent", "bob");
        Map<String, Object> m = Protocol.parseMessage(s);
        Assert.assertEquals("CHALLENGE", m.get("type"));
        Assert.assertEquals("bob", m.get("opponent"));
    }

    @Test
    public void createErrorMessage() {
        String s = Protocol.createErrorMessage("Not your turn");
        Map<String, Object> m = Protocol.parseMessage(s);
        Assert.assertEquals("ERROR", m.get("type"));
        Assert.assertEquals("Not your turn", m.get("message"));
    }

    @Test
    public void createSuccessMessage() {
        String s = Protocol.createSuccessMessage("OK");
        Map<String, Object> m = Protocol.parseMessage(s);
        Assert.assertEquals("SUCCESS", m.get("type"));
        Assert.assertEquals("OK", m.get("message"));
    }

    @Test
    public void roundTrip_createThenParse() {
        Map<String, Object> in = new HashMap<>();
        in.put("type", "UPDATE");
        in.put("gameId", "abc-123");
        Map<String, String> board = new HashMap<>();
        board.put("0,0", "X");
        board.put("1,1", "O");
        in.put("board", board);
        String encoded = Protocol.createMessage(in);
        Map<String, Object> out = Protocol.parseMessage(encoded);
        Assert.assertEquals("UPDATE", out.get("type"));
        Assert.assertEquals("abc-123", out.get("gameId"));
        @SuppressWarnings("unchecked")
        Map<String, String> b = (Map<String, String>) out.get("board");
        Assert.assertNotNull(b);
        Assert.assertEquals("X", b.get("0,0"));
        Assert.assertEquals("O", b.get("1,1"));
    }
}
