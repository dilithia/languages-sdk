package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.internal.JsonRpcClient;
import org.dilithia.sdk.model.GasEstimate;
import org.dilithia.sdk.model.NetworkInfo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Request group for network-level queries.
 *
 * <p>Provides sub-methods for retrieving chain head, gas estimates, and base fee.</p>
 *
 * <pre>{@code
 * NetworkInfo info = client.network().head();
 * GasEstimate gas = client.network().gasEstimate();
 * long fee = client.network().baseFee();
 * }</pre>
 */
public final class NetworkRequest {

    private final JsonRpcClient rpcClient;

    /**
     * Creates a new network request group.
     *
     * @param rpcClient the JSON-RPC client
     */
    public NetworkRequest(JsonRpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    /**
     * Fetches the current chain head information synchronously.
     *
     * @return the network head info
     * @throws DilithiaException if the request fails
     */
    public NetworkInfo head() throws DilithiaException {
        Map<String, Object> result = rpcClient.call("qsc_head", null);
        return Json.deserialize(Json.serialize(result), NetworkInfo.class);
    }

    /**
     * Fetches the current chain head information asynchronously.
     *
     * @return a future resolving to the network head info
     */
    public CompletableFuture<NetworkInfo> headAsync() {
        return rpcClient.callAsync("qsc_head", null)
                .thenApply(result -> {
                    try {
                        return Json.deserialize(Json.serialize(result), NetworkInfo.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Fetches the current gas estimate synchronously.
     *
     * @return the gas estimate
     * @throws DilithiaException if the request fails
     */
    public GasEstimate gasEstimate() throws DilithiaException {
        Map<String, Object> result = rpcClient.call("qsc_gasEstimate", null);
        return Json.deserialize(Json.serialize(result), GasEstimate.class);
    }

    /**
     * Fetches the current gas estimate asynchronously.
     *
     * @return a future resolving to the gas estimate
     */
    public CompletableFuture<GasEstimate> gasEstimateAsync() {
        return rpcClient.callAsync("qsc_gasEstimate", null)
                .thenApply(result -> {
                    try {
                        return Json.deserialize(Json.serialize(result), GasEstimate.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Fetches the current base fee synchronously.
     *
     * @return the base fee value
     * @throws DilithiaException if the request fails
     */
    public long baseFee() throws DilithiaException {
        Map<String, Object> result = rpcClient.call("qsc_baseFee", null);
        Object value = result.get("value");
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new DilithiaException("Unexpected baseFee result type: " + value);
    }

    /**
     * Fetches the current base fee asynchronously.
     *
     * @return a future resolving to the base fee value
     */
    public CompletableFuture<Long> baseFeeAsync() {
        return rpcClient.callAsync("qsc_baseFee", null)
                .thenApply(result -> {
                    Object value = result.get("value");
                    if (value instanceof Number n) {
                        return n.longValue();
                    }
                    throw new RuntimeException("Unexpected baseFee result type: " + value);
                });
    }
}
