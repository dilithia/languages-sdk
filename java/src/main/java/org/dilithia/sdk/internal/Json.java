package org.dilithia.sdk.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.dilithia.sdk.exception.SerializationException;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.PublicKey;
import org.dilithia.sdk.model.SecretKey;
import org.dilithia.sdk.model.TxHash;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Internal JSON serialization utilities backed by Gson.
 *
 * <p>This class is not part of the public API.</p>
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Address.class, new TypeAdapter<Address>() {
                @Override public void write(JsonWriter out, Address value) throws IOException {
                    out.value(value == null ? null : value.value());
                }
                @Override public Address read(JsonReader in) throws IOException {
                    String s = in.nextString();
                    return s == null ? null : Address.of(s);
                }
            })
            .registerTypeAdapter(TxHash.class, new TypeAdapter<TxHash>() {
                @Override public void write(JsonWriter out, TxHash value) throws IOException {
                    out.value(value == null ? null : value.value());
                }
                @Override public TxHash read(JsonReader in) throws IOException {
                    String s = in.nextString();
                    return s == null ? null : TxHash.of(s);
                }
            })
            .registerTypeAdapter(PublicKey.class, new TypeAdapter<PublicKey>() {
                @Override public void write(JsonWriter out, PublicKey value) throws IOException {
                    out.value(value == null ? null : value.hex());
                }
                @Override public PublicKey read(JsonReader in) throws IOException {
                    String s = in.nextString();
                    return s == null ? null : PublicKey.of(s);
                }
            })
            .registerTypeAdapter(SecretKey.class, new TypeAdapter<SecretKey>() {
                @Override public void write(JsonWriter out, SecretKey value) throws IOException {
                    out.value(value == null ? null : value.hex());
                }
                @Override public SecretKey read(JsonReader in) throws IOException {
                    String s = in.nextString();
                    return s == null ? null : SecretKey.of(s);
                }
            })
            .create();

    private Json() {}

    /**
     * Serializes an object to a JSON string.
     *
     * @param value the object to serialize
     * @return the JSON string
     * @throws SerializationException if serialization fails
     */
    public static String serialize(Object value) throws SerializationException {
        try {
            return GSON.toJson(value);
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Serializes an object to canonical (sorted-key) JSON bytes for signing.
     *
     * @param value the object to serialize
     * @return the canonical JSON bytes
     * @throws SerializationException if serialization fails
     */
    public static byte[] canonicalBytes(Object value) throws SerializationException {
        return serialize(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserializes a JSON string to the given type.
     *
     * @param json  the JSON string
     * @param clazz the target type
     * @param <T>   the target type
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    public static <T> T deserialize(String json, Class<T> clazz) throws SerializationException {
        try {
            return GSON.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            throw new SerializationException("Failed to deserialize JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to the given generic type.
     *
     * @param json the JSON string
     * @param type the target type
     * @param <T>  the target type
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    public static <T> T deserialize(String json, Type type) throws SerializationException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new SerializationException("Failed to deserialize JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to a Map.
     *
     * @param json the JSON string
     * @return the deserialized map
     * @throws SerializationException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deserializeMap(String json) throws SerializationException {
        try {
            return GSON.fromJson(json, Map.class);
        } catch (JsonSyntaxException e) {
            throw new SerializationException("Failed to deserialize JSON to map", e);
        }
    }

    /**
     * Returns the shared Gson instance.
     *
     * @return the Gson instance
     */
    public static Gson gson() { return GSON; }
}
