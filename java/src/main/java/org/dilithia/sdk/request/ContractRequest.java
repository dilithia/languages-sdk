package org.dilithia.sdk.request;

import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.JsonRpcClient;

import java.util.Map;

/**
 * Entry point for contract interactions: calls, queries, and ABI retrieval.
 *
 * <pre>{@code
 * // Mutating call
 * Receipt r = client.contract("token")
 *     .call("transfer", Map.of("to", "dili1bob", "amount", 100))
 *     .send(signer);
 *
 * // Read-only query
 * QueryResult q = client.contract("token").query("totalSupply").get();
 *
 * // ABI
 * ContractAbi abi = client.contract("token").abi().get();
 * }</pre>
 */
public final class ContractRequest {

    private final HttpTransport transport;
    private final JsonRpcClient rpcClient;
    private final String baseUrl;
    private final String rpcUrl;
    private final String contractName;

    /**
     * Creates a new contract request for the named contract.
     *
     * @param transport    the HTTP transport
     * @param rpcClient    the JSON-RPC client
     * @param baseUrl      the chain base URL
     * @param rpcUrl       the RPC URL
     * @param contractName the on-chain contract name
     */
    public ContractRequest(HttpTransport transport, JsonRpcClient rpcClient,
                           String baseUrl, String rpcUrl, String contractName) {
        this.transport = transport;
        this.rpcClient = rpcClient;
        this.baseUrl = baseUrl;
        this.rpcUrl = rpcUrl;
        this.contractName = contractName;
    }

    /**
     * Begins building a mutating contract call.
     *
     * @param method the contract method name
     * @param args   the method arguments as a map
     * @return a contract call builder
     */
    public ContractCallBuilder call(String method, Map<String, Object> args) {
        return new ContractCallBuilder(transport, rpcUrl, contractName, method, args);
    }

    /**
     * Begins building a mutating contract call with no arguments.
     *
     * @param method the contract method name
     * @return a contract call builder
     */
    public ContractCallBuilder call(String method) {
        return call(method, Map.of());
    }

    /**
     * Begins building a read-only contract query.
     *
     * @param method the contract method name
     * @param args   the method arguments as a map
     * @return a contract query builder
     */
    public ContractQueryBuilder query(String method, Map<String, Object> args) {
        return new ContractQueryBuilder(transport, baseUrl, contractName, method, args);
    }

    /**
     * Begins building a read-only contract query with no arguments.
     *
     * @param method the contract method name
     * @return a contract query builder
     */
    public ContractQueryBuilder query(String method) {
        return query(method, Map.of());
    }

    /**
     * Creates a request to fetch this contract's ABI.
     *
     * @return an ABI request
     */
    public AbiRequest abi() {
        return new AbiRequest(rpcClient, contractName);
    }
}
