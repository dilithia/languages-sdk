package org.dilithia.sdk.internal;

import org.dilithia.sdk.exception.SerializationException;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.PublicKey;
import org.dilithia.sdk.model.SecretKey;
import org.dilithia.sdk.model.TxHash;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Nested
    class SerializeTests {

        @Test
        void serializesMapToJsonString() throws SerializationException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "alice");
            map.put("age", 30);
            String json = Json.serialize(map);
            assertTrue(json.contains("\"name\":\"alice\""));
            assertTrue(json.contains("\"age\":30"));
        }

        @Test
        void serializesNullFieldsGracefully() throws SerializationException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", null);
            String json = Json.serialize(map);
            // Gson by default omits null values, so we just verify it serializes without error
            assertNotNull(json);
            assertTrue(json.startsWith("{"));
        }

        @Test
        void serializesEmptyMap() throws SerializationException {
            String json = Json.serialize(Map.of());
            assertEquals("{}", json);
        }

        @Test
        void serializesNestedMap() throws SerializationException {
            Map<String, Object> inner = Map.of("x", 1);
            Map<String, Object> outer = Map.of("inner", inner);
            String json = Json.serialize(outer);
            assertTrue(json.contains("\"x\":1"));
        }
    }

    @Nested
    class DeserializeTests {

        @Test
        void deserializesToRecord() throws SerializationException {
            String json = "{\"address\":\"dili1alice\",\"balance\":1000}";
            org.dilithia.sdk.model.Balance bal = Json.deserialize(json, org.dilithia.sdk.model.Balance.class);
            assertEquals(Address.of("dili1alice"), bal.address());
            assertEquals(1000L, bal.balance());
        }

        @Test
        void deserializesNetworkInfo() throws SerializationException {
            String json = "{\"blockHeight\":42,\"blockHash\":\"0xabc\",\"chainId\":\"dilithia-1\"}";
            var info = Json.deserialize(json, org.dilithia.sdk.model.NetworkInfo.class);
            assertEquals(42L, info.blockHeight());
            assertEquals("0xabc", info.blockHash());
            assertEquals("dilithia-1", info.chainId());
        }

        @Test
        void invalidJsonThrowsSerializationException() {
            assertThrows(SerializationException.class,
                    () -> Json.deserialize("not json{{{", org.dilithia.sdk.model.Balance.class));
        }

        @Test
        void deserializeWithGenericType() throws SerializationException {
            String json = "{\"address\":\"dili1bob\",\"nonce\":5}";
            var nonce = Json.deserialize(json, org.dilithia.sdk.model.Nonce.class);
            assertEquals(Address.of("dili1bob"), nonce.address());
            assertEquals(5L, nonce.nonce());
        }
    }

    @Nested
    class DeserializeMapTests {

        @Test
        void deserializesJsonToMap() throws SerializationException {
            String json = "{\"key\":\"value\",\"num\":42}";
            Map<String, Object> map = Json.deserializeMap(json);
            assertEquals("value", map.get("key"));
            // Gson deserializes numbers as Double by default
            assertEquals(42.0, map.get("num"));
        }

        @Test
        void invalidJsonThrowsSerializationException() {
            assertThrows(SerializationException.class,
                    () -> Json.deserializeMap("{bad json"));
        }

        @Test
        void emptyObjectDeserializesToEmptyMap() throws SerializationException {
            Map<String, Object> map = Json.deserializeMap("{}");
            assertTrue(map.isEmpty());
        }
    }

    @Nested
    class CanonicalBytesTests {

        @Test
        void producesUtf8Bytes() throws SerializationException {
            Map<String, Object> map = new TreeMap<>();
            map.put("a", 1);
            map.put("b", "two");
            byte[] bytes = Json.canonicalBytes(map);
            String result = new String(bytes, StandardCharsets.UTF_8);
            assertEquals("{\"a\":1,\"b\":\"two\"}", result);
        }

        @Test
        void sortedKeysProduceDeterministicOutput() throws SerializationException {
            TreeMap<String, Object> sorted = new TreeMap<>();
            sorted.put("z", 1);
            sorted.put("a", 2);
            byte[] bytes = Json.canonicalBytes(sorted);
            String result = new String(bytes, StandardCharsets.UTF_8);
            // 'a' should come before 'z'
            assertTrue(result.indexOf("\"a\"") < result.indexOf("\"z\""));
        }
    }

    @Nested
    class CustomTypeAdapterTests {

        @Test
        void addressSerializesToString() throws SerializationException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("addr", Address.of("dili1test"));
            String json = Json.serialize(map);
            assertTrue(json.contains("\"dili1test\""));
        }

        @Test
        void txHashSerializesToString() throws SerializationException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("hash", TxHash.of("0xabc"));
            String json = Json.serialize(map);
            assertTrue(json.contains("\"0xabc\""));
        }

        @Test
        void publicKeySerializesToHex() throws SerializationException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("pk", PublicKey.of("abcdef"));
            String json = Json.serialize(map);
            assertTrue(json.contains("\"abcdef\""));
        }

        @Test
        void secretKeySerializesToHex() throws SerializationException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sk", SecretKey.of("deadbeef"));
            String json = Json.serialize(map);
            assertTrue(json.contains("\"deadbeef\""));
        }

        @Test
        void gsonInstanceIsNotNull() {
            assertNotNull(Json.gson());
        }

        @Test
        void addressDeserializesFromJson() throws SerializationException {
            String json = "{\"address\":\"dili1test\"}";
            record Wrapper(Address address) {}
            var result = Json.deserialize(json, Wrapper.class);
            assertEquals(Address.of("dili1test"), result.address());
        }

        @Test
        void txHashDeserializesFromJson() throws SerializationException {
            String json = "{\"hash\":\"0xabc\"}";
            record Wrapper(TxHash hash) {}
            var result = Json.deserialize(json, Wrapper.class);
            assertEquals(TxHash.of("0xabc"), result.hash());
        }

        @Test
        void publicKeyDeserializesFromJson() throws SerializationException {
            String json = "{\"pk\":\"abcdef\"}";
            record Wrapper(PublicKey pk) {}
            var result = Json.deserialize(json, Wrapper.class);
            assertEquals(PublicKey.of("abcdef"), result.pk());
        }

        @Test
        void secretKeyDeserializesFromJson() throws SerializationException {
            String json = "{\"sk\":\"deadbeef\"}";
            record Wrapper(SecretKey sk) {}
            var result = Json.deserialize(json, Wrapper.class);
            assertEquals(SecretKey.of("deadbeef"), result.sk());
        }
    }

    @Nested
    class GenericDeserializeTests {

        @Test
        void deserializeWithType() throws SerializationException {
            String json = "{\"blockHeight\":99,\"blockHash\":\"0xh\",\"chainId\":\"c1\"}";
            java.lang.reflect.Type type = org.dilithia.sdk.model.NetworkInfo.class;
            org.dilithia.sdk.model.NetworkInfo info = Json.deserialize(json, type);
            assertEquals(99L, info.blockHeight());
        }

        @Test
        void deserializeWithTypeInvalidJsonThrows() {
            java.lang.reflect.Type type = org.dilithia.sdk.model.NetworkInfo.class;
            assertThrows(SerializationException.class,
                    () -> Json.deserialize("{bad", type));
        }
    }
}
