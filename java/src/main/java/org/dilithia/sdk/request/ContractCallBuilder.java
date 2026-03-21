package org.dilithia.sdk.request;

import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.Receipt;
import org.dilithia.sdk.model.SignedPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for a mutating contract call.
 *
 * <p>Builds a canonical payload, signs it, and POSTs the signed transaction to the RPC endpoint.</p>
 *
 * <pre>{@code
 * Receipt r = client.contract("token")
 *     .call("transfer", Map.of("to", "dili1bob", "amount", 100))
 *     .withPaymaster("dili1sponsor")
 *     .send(signer);
 * }</pre>
 */
public final class ContractCallBuilder {

    private final HttpTransport transport;
    private final String rpcUrl;
    private final String contract;
    private final String method;
    private final Map<String, Object> args;
    private String paymaster;

    /**
     * Creates a new contract call builder.
     *
     * @param transport the HTTP transport
     * @param rpcUrl    the RPC endpoint URL
     * @param contract  the contract name
     * @param method    the method name
     * @param args      the method arguments
     */
    ContractCallBuilder(HttpTransport transport, String rpcUrl,
                        String contract, String method, Map<String, Object> args) {
        this.transport = transport;
        this.rpcUrl = rpcUrl;
        this.contract = contract;
        this.method = method;
        this.args = args != null ? args : Map.of();
    }

    /**
     * Adds a gas paymaster to sponsor this transaction.
     *
     * @param paymasterAddress the paymaster's address
     * @return this builder
     */
    public ContractCallBuilder withPaymaster(String paymasterAddress) {
        this.paymaster = paymasterAddress;
        return this;
    }

    /**
     * Signs and sends the contract call synchronously.
     *
     * @param signer the signer to sign the canonical payload
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
     * Signs and sends the contract call asynchronously.
     *
     * @param signer the signer to sign the canonical payload
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
        Map<String, Object> call = DilithiaClient.buildContractCall(contract, method, args, paymaster);

        // Build canonical payload with sorted keys
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
