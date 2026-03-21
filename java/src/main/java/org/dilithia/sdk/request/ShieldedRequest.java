package org.dilithia.sdk.request;

import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.internal.JsonRpcClient;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.QueryResult;
import org.dilithia.sdk.model.Receipt;
import org.dilithia.sdk.model.SignedPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Request group for shielded (privacy-preserving) transactions.
 *
 * <pre>{@code
 * // Deposit
 * client.shielded().deposit(commitment, value, proof).send(signer);
 *
 * // Withdraw
 * client.shielded().withdraw(nullifier, amount, recipient, proof, root).send(signer);
 *
 * // Query commitment root
 * QueryResult root = client.shielded().commitmentRoot().get();
 *
 * // Check if nullifier is spent
 * QueryResult spent = client.shielded().isNullifierSpent(nullifier).get();
 * }</pre>
 */
public final class ShieldedRequest {

    private final HttpTransport transport;
    private final JsonRpcClient rpcClient;
    private final String rpcUrl;
    private final String baseUrl;

    /**
     * Creates a new shielded request group.
     *
     * @param transport the HTTP transport
     * @param rpcClient the JSON-RPC client
     * @param rpcUrl    the RPC endpoint URL
     * @param baseUrl   the chain base URL
     */
    public ShieldedRequest(HttpTransport transport, JsonRpcClient rpcClient,
                           String rpcUrl, String baseUrl) {
        this.transport = transport;
        this.rpcClient = rpcClient;
        this.rpcUrl = rpcUrl;
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a shielded deposit call builder.
     *
     * @param commitment the commitment value
     * @param value      the deposit amount
     * @param proof      the zero-knowledge proof in hex
     * @return a shielded call builder
     */
    public ShieldedCallBuilder deposit(String commitment, long value, String proof) {
        Map<String, Object> args = Map.of(
                "commitment", commitment,
                "value", value,
                "proof", proof
        );
        Map<String, Object> call = DilithiaClient.buildContractCall("shielded", "deposit", args, null);
        return new ShieldedCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a shielded withdraw call builder.
     *
     * @param nullifier  the nullifier value
     * @param amount     the withdrawal amount
     * @param recipient  the recipient address
     * @param proof      the zero-knowledge proof in hex
     * @param root       the commitment root
     * @return a shielded call builder
     */
    public ShieldedCallBuilder withdraw(String nullifier, long amount, String recipient,
                                        String proof, String root) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("nullifier", nullifier);
        args.put("amount", amount);
        args.put("recipient", recipient);
        args.put("proof", proof);
        args.put("commitment_root", root);
        Map<String, Object> call = DilithiaClient.buildContractCall("shielded", "withdraw", args, null);
        return new ShieldedCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a request to fetch the current commitment root.
     *
     * @return a typed query request
     */
    public ShieldedQueryRequest commitmentRoot() {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                "shielded", "get_commitment_root", Map.of(), null);
        return new ShieldedQueryRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to check whether a nullifier has been spent.
     *
     * @param nullifier the nullifier to check
     * @return a typed query request
     */
    public ShieldedQueryRequest isNullifierSpent(String nullifier) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                "shielded", "is_nullifier_spent", Map.of("nullifier", nullifier), null);
        return new ShieldedQueryRequest(transport, baseUrl, call);
    }

    /**
     * Builder for shielded mutating calls (deposit/withdraw) that require signing.
     */
    public static final class ShieldedCallBuilder {

        private final HttpTransport transport;
        private final String rpcUrl;
        private final Map<String, Object> call;

        ShieldedCallBuilder(HttpTransport transport, String rpcUrl, Map<String, Object> call) {
            this.transport = transport;
            this.rpcUrl = rpcUrl;
            this.call = call;
        }

        /**
         * Signs and sends the shielded call synchronously.
         *
         * @param signer the signer
         * @return the transaction receipt
         * @throws DilithiaException if the request fails
         * @throws CryptoException   if signing fails
         */
        public Receipt send(DilithiaSigner signer) throws DilithiaException, CryptoException {
            String body = buildSignedBody(signer);
            String response = transport.post(rpcUrl, body);
            return Json.deserialize(response, Receipt.class);
        }

        /**
         * Signs and sends the shielded call asynchronously.
         *
         * @param signer the signer
         * @return a future resolving to the transaction receipt
         */
        public CompletableFuture<Receipt> sendAsync(DilithiaSigner signer) {
            try {
                String body = buildSignedBody(signer);
                return transport.postAsync(rpcUrl, body)
                        .thenApply(response -> {
                            try {
                                return Json.deserialize(response, Receipt.class);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        private String buildSignedBody(DilithiaSigner signer) throws DilithiaException, CryptoException {
            TreeMap<String, Object> sorted = new TreeMap<>(call);
            byte[] canonical = Json.canonicalBytes(sorted);
            CanonicalPayload canonicalPayload = new CanonicalPayload(sorted, canonical);
            SignedPayload signed = signer.sign(canonicalPayload);

            Map<String, Object> body = new LinkedHashMap<>(call);
            body.put("alg", signed.alg());
            body.put("pk", signed.publicKey());
            body.put("sig", signed.signature());
            return Json.serialize(body);
        }
    }

    /**
     * Typed request for read-only shielded queries (commitment root, nullifier check).
     */
    public static final class ShieldedQueryRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        ShieldedQueryRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the shielded query synchronously.
         *
         * @return the query result
         * @throws DilithiaException if the request fails
         */
        public QueryResult get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return Json.deserialize(response, QueryResult.class);
        }

        /**
         * Executes the shielded query asynchronously.
         *
         * @return a future resolving to the query result
         */
        public CompletableFuture<QueryResult> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return Json.deserialize(response, QueryResult.class);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }
}
