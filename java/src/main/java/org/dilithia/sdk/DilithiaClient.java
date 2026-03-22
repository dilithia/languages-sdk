package org.dilithia.sdk;

import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.JsonRpcClient;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.DeployPayload;
import org.dilithia.sdk.model.TxHash;
import org.dilithia.sdk.request.AddressSummaryRequest;
import org.dilithia.sdk.request.BalanceRequest;
import org.dilithia.sdk.request.ContractRequest;
import org.dilithia.sdk.request.DeployRequest;
import org.dilithia.sdk.request.CredentialRequest;
import org.dilithia.sdk.request.MultisigRequest;
import org.dilithia.sdk.request.NameServiceRequest;
import org.dilithia.sdk.request.NetworkRequest;
import org.dilithia.sdk.request.NonceRequest;
import org.dilithia.sdk.request.ReceiptRequest;
import org.dilithia.sdk.request.RpcRequest;
import org.dilithia.sdk.request.ShieldedRequest;
import org.dilithia.sdk.request.UpgradeRequest;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable Dilithia SDK client. Obtain an instance via the builder:
 *
 * <pre>{@code
 * var client = Dilithia.client("http://localhost:8000/rpc")
 *     .timeout(Duration.ofSeconds(15))
 *     .build();
 *
 * Balance b = client.balance(Address.of("dili1alice")).get();
 * // or, for convenience:
 * Balance b = client.balance("dili1alice").get();
 * }</pre>
 *
 * <p>All public methods that return request objects are thread-safe.</p>
 */
public final class DilithiaClient implements AutoCloseable {

    /** SDK version identifier. */
    public static final String SDK_VERSION = "0.3.0";

    /** RPC protocol version this SDK targets. */
    public static final String RPC_LINE_VERSION = "0.2.0";

    private final String rpcUrl;
    private final String baseUrl;
    private final String indexerUrl;
    private final String oracleUrl;
    private final String wsUrl;
    private final HttpTransport transport;
    private final JsonRpcClient rpcClient;

    /* package-private */ DilithiaClient(
            String rpcUrl,
            Duration timeout,
            String jwt,
            Map<String, String> headers,
            String chainBaseUrl,
            String indexerUrl,
            String oracleUrl,
            String wsUrl,
            HttpClient httpClient
    ) {
        this.rpcUrl = stripTrailingSlashes(rpcUrl);

        this.baseUrl = (chainBaseUrl == null || chainBaseUrl.isBlank())
                ? this.rpcUrl.replaceFirst("/rpc$", "")
                : stripTrailingSlashes(chainBaseUrl);

        this.indexerUrl = normalizeOptionalUrl(indexerUrl);
        this.oracleUrl = normalizeOptionalUrl(oracleUrl);
        this.wsUrl = (wsUrl == null || wsUrl.isBlank()) ? deriveWsUrl(this.baseUrl) : stripTrailingSlashes(wsUrl);

        Map<String, String> allHeaders = new LinkedHashMap<>();
        if (jwt != null && !jwt.isBlank()) {
            allHeaders.put("Authorization", "Bearer " + jwt);
        }
        allHeaders.putAll(headers);

        this.transport = new HttpTransport(httpClient, timeout, allHeaders);
        this.rpcClient = new JsonRpcClient(transport, this.rpcUrl);
    }

    // ── Fluent request entry points ───────────────────────────────────

    /**
     * Creates a balance request for the given address.
     *
     * @param address the account address
     * @return a balance request builder
     */
    public BalanceRequest balance(Address address) {
        return new BalanceRequest(transport, rpcUrl, address.value());
    }

    /**
     * Creates a balance request for the given address string (convenience overload).
     *
     * @param address the account address as a plain string
     * @return a balance request builder
     */
    public BalanceRequest balance(String address) {
        return balance(Address.of(address));
    }

    /**
     * Creates a nonce request for the given address.
     *
     * @param address the account address
     * @return a nonce request builder
     */
    public NonceRequest nonce(Address address) {
        return new NonceRequest(transport, rpcUrl, address.value());
    }

    /**
     * Creates a nonce request for the given address string (convenience overload).
     *
     * @param address the account address as a plain string
     * @return a nonce request builder
     */
    public NonceRequest nonce(String address) {
        return nonce(Address.of(address));
    }

    /**
     * Creates a receipt request for the given transaction hash.
     *
     * @param txHash the transaction hash
     * @return a receipt request builder
     */
    public ReceiptRequest receipt(TxHash txHash) {
        return new ReceiptRequest(transport, rpcUrl, txHash.value());
    }

    /**
     * Creates a receipt request for the given transaction hash string (convenience overload).
     *
     * @param txHash the transaction hash as a plain string
     * @return a receipt request builder
     */
    public ReceiptRequest receipt(String txHash) {
        return receipt(TxHash.of(txHash));
    }

    /**
     * Creates an address summary request (JSON-RPC).
     *
     * @param address the account address
     * @return an address summary request builder
     */
    public AddressSummaryRequest addressSummary(Address address) {
        return new AddressSummaryRequest(rpcClient, address.value());
    }

    /**
     * Creates an address summary request (JSON-RPC) (convenience overload).
     *
     * @param address the account address as a plain string
     * @return an address summary request builder
     */
    public AddressSummaryRequest addressSummary(String address) {
        return addressSummary(Address.of(address));
    }

    /**
     * Creates a network information request group.
     *
     * @return a network request with sub-methods for head, gas, and base fee
     */
    public NetworkRequest network() {
        return new NetworkRequest(rpcClient);
    }

    /**
     * Creates a contract interaction request for the named contract.
     *
     * @param name the on-chain contract name
     * @return a contract request with call, query, and ABI sub-methods
     */
    public ContractRequest contract(String name) {
        return new ContractRequest(transport, rpcClient, baseUrl, rpcUrl, name);
    }

    /**
     * Creates a name-service request group.
     *
     * @return a name service request with resolve, reverse-resolve, registration, and record methods
     */
    public NameServiceRequest names() {
        return new NameServiceRequest(transport, baseUrl, rpcUrl);
    }

    /**
     * Creates a credential request group.
     *
     * @return a credential request with schema, issuance, revocation, and verification methods
     */
    public CredentialRequest credentials() {
        return new CredentialRequest(transport, baseUrl, rpcUrl);
    }

    /**
     * Creates a multisig request group.
     *
     * @return a multisig request with wallet creation, proposal, approval, execution, and query methods
     */
    public MultisigRequest multisig() {
        return new MultisigRequest(transport, baseUrl, rpcUrl);
    }

    /**
     * Creates a shielded transaction request group.
     *
     * @return a shielded request with deposit, withdraw, and query methods
     */
    public ShieldedRequest shielded() {
        return new ShieldedRequest(transport, rpcClient, rpcUrl, baseUrl);
    }

    /**
     * Creates a deploy request for the given payload.
     *
     * @param payload the deployment payload
     * @return a deploy request builder
     */
    public DeployRequest deploy(DeployPayload payload) {
        return new DeployRequest(transport, baseUrl, payload);
    }

    /**
     * Creates an upgrade request for the given payload.
     *
     * @param payload the upgrade payload
     * @return an upgrade request builder
     */
    public UpgradeRequest upgrade(DeployPayload payload) {
        return new UpgradeRequest(transport, baseUrl, payload);
    }

    /**
     * Creates a generic JSON-RPC request.
     *
     * @param method the RPC method name
     * @param params the parameters (may be a map, list, or null)
     * @return an RPC request builder
     */
    public RpcRequest rpc(String method, Object params) {
        return new RpcRequest(rpcClient, method, params);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /**
     * Returns the RPC endpoint URL.
     *
     * @return the RPC URL
     */
    public String rpcUrl() {
        return rpcUrl;
    }

    /**
     * Returns the chain base URL (REST endpoints).
     *
     * @return the base URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Returns the WebSocket URL.
     *
     * @return the WebSocket URL, or {@code null} if it could not be derived
     */
    public String wsUrl() {
        return wsUrl;
    }

    /**
     * Returns the indexer URL, if configured.
     *
     * @return the indexer URL, or {@code null}
     */
    public String indexerUrl() {
        return indexerUrl;
    }

    /**
     * Returns the oracle URL, if configured.
     *
     * @return the oracle URL, or {@code null}
     */
    public String oracleUrl() {
        return oracleUrl;
    }

    /**
     * Returns the internal HTTP transport (for advanced use by request builders).
     *
     * @return the transport
     */
    /* package-private */ HttpTransport transport() {
        return transport;
    }

    /**
     * Returns the internal JSON-RPC client (for advanced use by request builders).
     *
     * @return the JSON-RPC client
     */
    /* package-private */ JsonRpcClient rpcClient() {
        return rpcClient;
    }

    // ── Static utility methods ────────────────────────────────────────

    /**
     * Builds a contract call payload map.
     *
     * @param contract   the contract name
     * @param method     the method name
     * @param args       the method arguments
     * @param paymaster  optional paymaster address (may be {@code null})
     * @return the call payload as a map
     */
    public static Map<String, Object> buildContractCall(
            String contract, String method, Map<String, Object> args, String paymaster) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("contract", contract);
        call.put("method", method);
        call.put("args", args == null ? Map.of() : args);
        return (paymaster == null || paymaster.isBlank()) ? call : withPaymaster(call, paymaster);
    }

    /**
     * Adds a paymaster field to an existing call payload.
     *
     * @param call      the call payload
     * @param paymaster the paymaster address
     * @return a new map containing the original fields plus the paymaster
     */
    public static Map<String, Object> withPaymaster(Map<String, Object> call, String paymaster) {
        Map<String, Object> merged = new LinkedHashMap<>(call);
        merged.put("paymaster", paymaster);
        return merged;
    }

    /**
     * Builds the canonical payload for a deploy/upgrade operation (fields sorted alphabetically).
     *
     * @param from         the sender address
     * @param name         the contract name
     * @param bytecodeHash the SHA-256 hash of the bytecode
     * @param nonce        the sender's current nonce
     * @param chainId      the target chain identifier
     * @return a sorted map representing the canonical payload
     */
    public static Map<String, Object> buildDeployCanonicalPayload(
            String from, String name, String bytecodeHash, long nonce, String chainId) {
        Map<String, Object> sorted = new TreeMap<>();
        sorted.put("bytecode_hash", bytecodeHash);
        sorted.put("chain_id", chainId);
        sorted.put("from", from);
        sorted.put("name", name);
        sorted.put("nonce", nonce);
        return sorted;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    /**
     * Closes the underlying HTTP transport and releases resources.
     */
    @Override
    public void close() {
        transport.close();
    }

    // ── Internals ─────────────────────────────────────────────────────

    private static String stripTrailingSlashes(String url) {
        return url.replaceAll("/+$", "");
    }

    private static String normalizeOptionalUrl(String url) {
        return (url == null || url.isBlank()) ? null : stripTrailingSlashes(url);
    }

    private static String deriveWsUrl(String baseUrl) {
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length());
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length());
        }
        return null;
    }
}
