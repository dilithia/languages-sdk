package org.dilithia.sdk.internal;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.RpcException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Internal JSON-RPC 2.0 client built on top of {@link HttpTransport}.
 *
 * <p>This class is not part of the public API.</p>
 */
public final class JsonRpcClient {

    private final HttpTransport transport;
    private final String rpcUrl;
    private final AtomicInteger idCounter = new AtomicInteger(1);

    /**
     * Creates a new JSON-RPC client.
     *
     * @param transport the HTTP transport
     * @param rpcUrl    the RPC endpoint URL
     */
    public JsonRpcClient(HttpTransport transport, String rpcUrl) {
        this.transport = transport;
        this.rpcUrl = rpcUrl;
    }

    /**
     * Sends a synchronous JSON-RPC call and returns the result as a map.
     *
     * @param method the RPC method name
     * @param params the parameters (may be null)
     * @return the result portion of the JSON-RPC response
     * @throws DilithiaException if the call fails
     */
    public Map<String, Object> call(String method, Object params) throws DilithiaException {
        String body = Json.serialize(buildRequest(method, params));
        String responseBody = transport.post(rpcUrl, body);
        return parseResponse(responseBody);
    }

    /**
     * Sends an asynchronous JSON-RPC call.
     *
     * @param method the RPC method name
     * @param params the parameters (may be null)
     * @return a future resolving to the result portion of the response
     */
    public CompletableFuture<Map<String, Object>> callAsync(String method, Object params) {
        try {
            String body = Json.serialize(buildRequest(method, params));
            return transport.postAsync(rpcUrl, body)
                    .thenApply(responseBody -> {
                        try {
                            return parseResponse(responseBody);
                        } catch (DilithiaException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Sends a synchronous JSON-RPC call and returns the raw response string.
     *
     * @param method the RPC method name
     * @param params the parameters (may be null)
     * @return the raw JSON response string
     * @throws DilithiaException if the call fails
     */
    public String callRaw(String method, Object params) throws DilithiaException {
        String body = Json.serialize(buildRequest(method, params));
        return transport.post(rpcUrl, body);
    }

    /**
     * Sends an async JSON-RPC call and returns the raw response string.
     *
     * @param method the RPC method name
     * @param params the parameters
     * @return a future resolving to the raw JSON response
     */
    public CompletableFuture<String> callRawAsync(String method, Object params) {
        try {
            String body = Json.serialize(buildRequest(method, params));
            return transport.postAsync(rpcUrl, body);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> buildRequest(String method, Object params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", idCounter.getAndIncrement());
        request.put("method", method);
        request.put("params", params == null ? Map.of() : params);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(String body) throws DilithiaException {
        Map<String, Object> response = Json.deserializeMap(body);
        if (response.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            int code = error.get("code") instanceof Number n ? n.intValue() : -1;
            String message = String.valueOf(error.getOrDefault("message", "Unknown RPC error"));
            throw new RpcException(code, message);
        }
        Object result = response.get("result");
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        // Wrap scalar results
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("value", result);
        return wrapper;
    }
}
