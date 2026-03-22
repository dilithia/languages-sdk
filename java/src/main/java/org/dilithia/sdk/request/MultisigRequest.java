package org.dilithia.sdk.request;

import com.google.gson.reflect.TypeToken;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.MultisigTx;
import org.dilithia.sdk.model.MultisigWallet;
import org.dilithia.sdk.model.QueryResult;
import org.dilithia.sdk.model.Receipt;
import org.dilithia.sdk.model.SignedPayload;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Request group for multisig wallet operations: creation, proposal,
 * approval, execution, revocation, signer management, and queries.
 *
 * <pre>{@code
 * // Mutations (require signing)
 * Receipt r = client.multisig().createMultisig("w1", signers, 2).send(signer);
 * Receipt r = client.multisig().proposeTx("w1", "token", "transfer", args).send(signer);
 * Receipt r = client.multisig().approveMultisigTx("w1", "tx1").send(signer);
 * Receipt r = client.multisig().executeMultisigTx("w1", "tx1").send(signer);
 *
 * // Queries
 * MultisigWallet w = client.multisig().getMultisigWallet("w1").get();
 * MultisigTx tx = client.multisig().getMultisigTx("w1", "tx1").get();
 * List<MultisigTx> txs = client.multisig().listMultisigPendingTxs("w1").get();
 * }</pre>
 */
public final class MultisigRequest {

    private static final String MULTISIG_CONTRACT = "multisig";

    private final HttpTransport transport;
    private final String baseUrl;
    private final String rpcUrl;

    /**
     * Creates a new multisig request group.
     *
     * @param transport the HTTP transport
     * @param baseUrl   the chain base URL
     * @param rpcUrl    the RPC endpoint URL
     */
    public MultisigRequest(HttpTransport transport, String baseUrl, String rpcUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
        this.rpcUrl = rpcUrl;
    }

    // ── Mutations (require signing) ──────────────────────────────────

    /**
     * Creates a multisig wallet creation call builder.
     *
     * @param walletId  the wallet identifier
     * @param signers   the list of signer addresses
     * @param threshold the approval threshold
     * @return a multisig call builder
     */
    public MultisigCallBuilder createMultisig(String walletId, List<String> signers, int threshold) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("wallet_id", walletId);
        args.put("signers", signers);
        args.put("threshold", threshold);
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "create", args, null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a transaction proposal call builder.
     *
     * @param walletId the wallet identifier
     * @param contract the target contract
     * @param method   the target method
     * @param args     the call arguments
     * @return a multisig call builder
     */
    public MultisigCallBuilder proposeTx(String walletId, String contract, String method,
                                          Map<String, Object> args) {
        Map<String, Object> callArgs = new LinkedHashMap<>();
        callArgs.put("wallet_id", walletId);
        callArgs.put("contract", contract);
        callArgs.put("method", method);
        callArgs.put("args", args);
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "propose_tx", callArgs, null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates an approval call builder.
     *
     * @param walletId the wallet identifier
     * @param txId     the transaction identifier
     * @return a multisig call builder
     */
    public MultisigCallBuilder approveMultisigTx(String walletId, String txId) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "approve", Map.of("wallet_id", walletId, "tx_id", txId), null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates an execution call builder.
     *
     * @param walletId the wallet identifier
     * @param txId     the transaction identifier
     * @return a multisig call builder
     */
    public MultisigCallBuilder executeMultisigTx(String walletId, String txId) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "execute", Map.of("wallet_id", walletId, "tx_id", txId), null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a revocation call builder.
     *
     * @param walletId the wallet identifier
     * @param txId     the transaction identifier
     * @return a multisig call builder
     */
    public MultisigCallBuilder revokeMultisigApproval(String walletId, String txId) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "revoke", Map.of("wallet_id", walletId, "tx_id", txId), null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates an add-signer call builder.
     *
     * @param walletId the wallet identifier
     * @param signer   the signer address to add
     * @return a multisig call builder
     */
    public MultisigCallBuilder addMultisigSigner(String walletId, String signer) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "add_signer", Map.of("wallet_id", walletId, "signer", signer), null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a remove-signer call builder.
     *
     * @param walletId the wallet identifier
     * @param signer   the signer address to remove
     * @return a multisig call builder
     */
    public MultisigCallBuilder removeMultisigSigner(String walletId, String signer) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "remove_signer", Map.of("wallet_id", walletId, "signer", signer), null);
        return new MultisigCallBuilder(transport, rpcUrl, call);
    }

    // ── Queries ──────────────────────────────────────────────────────

    /**
     * Creates a request to fetch a multisig wallet.
     *
     * @param walletId the wallet identifier
     * @return a typed request for the wallet
     */
    public GetMultisigWalletRequest getMultisigWallet(String walletId) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "wallet", Map.of("wallet_id", walletId), null);
        return new GetMultisigWalletRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to fetch a single pending multisig transaction.
     *
     * @param walletId the wallet identifier
     * @param txId     the transaction identifier
     * @return a typed request for the transaction
     */
    public GetMultisigTxRequest getMultisigTx(String walletId, String txId) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "pending_tx", Map.of("wallet_id", walletId, "tx_id", txId), null);
        return new GetMultisigTxRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to list all pending multisig transactions.
     *
     * @param walletId the wallet identifier
     * @return a typed request for the transaction list
     */
    public ListMultisigPendingTxsRequest listMultisigPendingTxs(String walletId) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                MULTISIG_CONTRACT, "pending_txs", Map.of("wallet_id", walletId), null);
        return new ListMultisigPendingTxsRequest(transport, baseUrl, call);
    }

    // ── Inner request classes ────────────────────────────────────────

    /**
     * Builder for multisig mutating calls that require signing.
     */
    public static final class MultisigCallBuilder {

        private final HttpTransport transport;
        private final String rpcUrl;
        private final Map<String, Object> call;

        MultisigCallBuilder(HttpTransport transport, String rpcUrl, Map<String, Object> call) {
            this.transport = transport;
            this.rpcUrl = rpcUrl;
            this.call = call;
        }

        /**
         * Signs and sends the multisig call synchronously.
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
         * Signs and sends the multisig call asynchronously.
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
     * Typed request that fetches a multisig wallet.
     */
    public static final class GetMultisigWalletRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        GetMultisigWalletRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the wallet lookup synchronously.
         *
         * @return the multisig wallet, or {@code null} if not found
         * @throws DilithiaException if the request fails
         */
        @SuppressWarnings("unchecked")
        public MultisigWallet get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseWallet(response);
        }

        /**
         * Executes the wallet lookup asynchronously.
         *
         * @return a future resolving to the multisig wallet (or {@code null})
         */
        public CompletableFuture<MultisigWallet> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseWallet(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private MultisigWallet parseWallet(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            if (data == null) return null;
            String walletId = data.get("wallet_id") instanceof String ? (String) data.get("wallet_id") : "";
            List<String> signers = data.get("signers") instanceof List ? (List<String>) data.get("signers") : List.of();
            int threshold = data.get("threshold") instanceof Number ? ((Number) data.get("threshold")).intValue() : 0;
            return new MultisigWallet(walletId, signers, threshold);
        }
    }

    /**
     * Typed request that fetches a single pending multisig transaction.
     */
    public static final class GetMultisigTxRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        GetMultisigTxRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the transaction lookup synchronously.
         *
         * @return the multisig transaction, or {@code null} if not found
         * @throws DilithiaException if the request fails
         */
        @SuppressWarnings("unchecked")
        public MultisigTx get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseTx(response);
        }

        /**
         * Executes the transaction lookup asynchronously.
         *
         * @return a future resolving to the multisig transaction (or {@code null})
         */
        public CompletableFuture<MultisigTx> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseTx(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private MultisigTx parseTx(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            if (data == null) return null;
            String txId = data.get("tx_id") instanceof String ? (String) data.get("tx_id") : "";
            String contract = data.get("contract") instanceof String ? (String) data.get("contract") : "";
            String method = data.get("method") instanceof String ? (String) data.get("method") : "";
            Map<String, Object> args = data.get("args") instanceof Map ? (Map<String, Object>) data.get("args") : Map.of();
            List<String> approvals = data.get("approvals") instanceof List ? (List<String>) data.get("approvals") : List.of();
            return new MultisigTx(txId, contract, method, args, approvals);
        }
    }

    /**
     * Typed request that lists pending multisig transactions.
     */
    public static final class ListMultisigPendingTxsRequest {

        private static final Type LIST_TYPE = new TypeToken<List<MultisigTx>>() {}.getType();

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        ListMultisigPendingTxsRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the pending transactions list query synchronously.
         *
         * @return the list of pending transactions
         * @throws DilithiaException if the request fails
         */
        @SuppressWarnings("unchecked")
        public List<MultisigTx> get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseList(response);
        }

        /**
         * Executes the pending transactions list query asynchronously.
         *
         * @return a future resolving to the list of pending transactions
         */
        public CompletableFuture<List<MultisigTx>> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseList(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private List<MultisigTx> parseList(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            Object pendingTxs = data.get("pending_txs");
            if (pendingTxs instanceof List) {
                return Json.deserialize(Json.serialize(pendingTxs), LIST_TYPE);
            }
            return List.of();
        }
    }
}
