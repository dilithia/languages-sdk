package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.internal.JsonRpcClient;
import org.dilithia.sdk.model.ContractAbi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Request builder for fetching a contract's ABI via JSON-RPC {@code qsc_getAbi}.
 *
 * <pre>{@code
 * ContractAbi abi = client.contract("token").abi().get();
 * }</pre>
 */
public final class AbiRequest {

    private static final String METHOD = "qsc_getAbi";

    private final JsonRpcClient rpcClient;
    private final String contractName;

    /**
     * Creates a new ABI request.
     *
     * @param rpcClient    the JSON-RPC client
     * @param contractName the contract name
     */
    AbiRequest(JsonRpcClient rpcClient, String contractName) {
        this.rpcClient = rpcClient;
        this.contractName = contractName;
    }

    /**
     * Fetches the contract ABI synchronously.
     *
     * @return the contract ABI
     * @throws DilithiaException if the request fails
     */
    public ContractAbi get() throws DilithiaException {
        Map<String, Object> result = rpcClient.call(METHOD, Map.of("contract", contractName));
        return Json.deserialize(Json.serialize(result), ContractAbi.class);
    }

    /**
     * Fetches the contract ABI asynchronously.
     *
     * @return a future resolving to the contract ABI
     */
    public CompletableFuture<ContractAbi> getAsync() {
        return rpcClient.callAsync(METHOD, Map.of("contract", contractName))
                .thenApply(result -> {
                    try {
                        return Json.deserialize(Json.serialize(result), ContractAbi.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
