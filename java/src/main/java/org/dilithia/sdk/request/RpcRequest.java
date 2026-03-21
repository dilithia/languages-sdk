package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.internal.JsonRpcClient;
import org.dilithia.sdk.model.RpcResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Request builder for generic JSON-RPC calls.
 *
 * <pre>{@code
 * RpcResponse resp = client.rpc("custom_method", Map.of("key", "value")).get();
 * }</pre>
 */
public final class RpcRequest {

    private final JsonRpcClient rpcClient;
    private final String method;
    private final Object params;

    /**
     * Creates a new generic RPC request.
     *
     * @param rpcClient the JSON-RPC client
     * @param method    the RPC method name
     * @param params    the parameters (may be a map, list, or null)
     */
    public RpcRequest(JsonRpcClient rpcClient, String method, Object params) {
        this.rpcClient = rpcClient;
        this.method = method;
        this.params = params;
    }

    /**
     * Executes the RPC call synchronously.
     *
     * @return the RPC response
     * @throws DilithiaException if the request fails
     */
    public RpcResponse get() throws DilithiaException {
        String raw = rpcClient.callRaw(method, params);
        Map<String, Object> parsed = Json.deserializeMap(raw);
        int id = parsed.get("id") instanceof Number n ? n.intValue() : 0;
        return new RpcResponse(id, parsed.get("result"));
    }

    /**
     * Executes the RPC call asynchronously.
     *
     * @return a future resolving to the RPC response
     */
    public CompletableFuture<RpcResponse> getAsync() {
        return rpcClient.callRawAsync(method, params)
                .thenApply(raw -> {
                    try {
                        Map<String, Object> parsed = Json.deserializeMap(raw);
                        int id = parsed.get("id") instanceof Number n ? n.intValue() : 0;
                        return new RpcResponse(id, parsed.get("result"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
