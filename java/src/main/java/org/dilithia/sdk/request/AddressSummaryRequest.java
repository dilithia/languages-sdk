package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.internal.JsonRpcClient;
import org.dilithia.sdk.model.AddressSummary;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Request builder for fetching an address summary via JSON-RPC {@code qsc_addressSummary}.
 *
 * <pre>{@code
 * AddressSummary summary = client.addressSummary("dili1alice").get();
 * }</pre>
 */
public final class AddressSummaryRequest {

    private static final String METHOD = "qsc_addressSummary";

    private final JsonRpcClient rpcClient;
    private final String address;

    /**
     * Creates a new address summary request.
     *
     * @param rpcClient the JSON-RPC client
     * @param address   the account address
     */
    public AddressSummaryRequest(JsonRpcClient rpcClient, String address) {
        this.rpcClient = rpcClient;
        this.address = address;
    }

    /**
     * Executes the address summary request synchronously.
     *
     * @return the address summary
     * @throws DilithiaException if the request fails
     */
    public AddressSummary get() throws DilithiaException {
        Map<String, Object> result = rpcClient.call(METHOD, Map.of("address", address));
        return Json.deserialize(Json.serialize(result), AddressSummary.class);
    }

    /**
     * Executes the address summary request asynchronously.
     *
     * @return a future resolving to the address summary
     */
    public CompletableFuture<AddressSummary> getAsync() {
        return rpcClient.callAsync(METHOD, Map.of("address", address))
                .thenApply(result -> {
                    try {
                        return Json.deserialize(Json.serialize(result), AddressSummary.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
