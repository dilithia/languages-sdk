package org.dilithia.sdk.request;

import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.DeployPayload;
import org.dilithia.sdk.model.Receipt;
import org.dilithia.sdk.model.SignedPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Request builder for deploying a contract.
 *
 * <p>Builds a canonical deploy payload, signs it, and POSTs to {@code /deploy}.</p>
 *
 * <pre>{@code
 * Receipt r = client.deploy(payload).send(signer);
 * }</pre>
 */
public final class DeployRequest {

    private final HttpTransport transport;
    private final String url;
    private final DeployPayload payload;

    /**
     * Creates a new deploy request.
     *
     * @param transport the HTTP transport
     * @param baseUrl   the chain base URL
     * @param payload   the deployment payload
     */
    public DeployRequest(HttpTransport transport, String baseUrl, DeployPayload payload) {
        this.transport = transport;
        this.url = baseUrl + "/deploy";
        this.payload = payload;
    }

    /**
     * Signs and sends the deploy request synchronously.
     *
     * @param signer the signer to sign the canonical payload
     * @return the transaction receipt
     * @throws DilithiaException if the request fails
     * @throws CryptoException   if signing fails
     */
    public Receipt send(DilithiaSigner signer) throws DilithiaException, CryptoException {
        String body = buildSignedBody(signer);
        String response = transport.post(url, body);
        return Json.deserialize(response, Receipt.class);
    }

    /**
     * Signs and sends the deploy request asynchronously.
     *
     * @param signer the signer to sign the canonical payload
     * @return a future resolving to the transaction receipt
     */
    public CompletableFuture<Receipt> sendAsync(DilithiaSigner signer) {
        try {
            String body = buildSignedBody(signer);
            return transport.postAsync(url, body)
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
        TreeMap<String, Object> canonical = new TreeMap<>();
        canonical.put("bytecode", payload.bytecode());
        canonical.put("chain_id", payload.chainId());
        canonical.put("from", payload.from());
        canonical.put("name", payload.name());
        canonical.put("nonce", payload.nonce());
        canonical.put("version", payload.version());

        byte[] canonicalBytes = Json.canonicalBytes(canonical);
        CanonicalPayload canonicalPayload = new CanonicalPayload(canonical, canonicalBytes);
        SignedPayload signed = signer.sign(canonicalPayload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", payload.name());
        body.put("bytecode", payload.bytecode());
        body.put("from", payload.from());
        body.put("alg", signed.alg());
        body.put("pk", signed.publicKey());
        body.put("sig", signed.signature());
        body.put("nonce", payload.nonce());
        body.put("chain_id", payload.chainId());
        body.put("version", payload.version());

        return Json.serialize(body);
    }
}
