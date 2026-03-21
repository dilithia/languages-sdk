package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.Nonce;

import java.util.concurrent.CompletableFuture;

/**
 * Request builder for fetching an account nonce.
 *
 * <p>Executes a GET to {@code /rpc/nonce/{address}}.</p>
 *
 * <pre>{@code
 * Nonce n = client.nonce("dili1alice").get();
 * }</pre>
 */
public final class NonceRequest {

    private final HttpTransport transport;
    private final String url;

    /**
     * Creates a new nonce request.
     *
     * @param transport the HTTP transport
     * @param rpcUrl    the RPC base URL
     * @param address   the account address
     */
    public NonceRequest(HttpTransport transport, String rpcUrl, String address) {
        this.transport = transport;
        this.url = rpcUrl + "/nonce/" + address;
    }

    /**
     * Executes the nonce request synchronously.
     *
     * @return the account nonce
     * @throws DilithiaException if the request fails
     */
    public Nonce get() throws DilithiaException {
        String body = transport.get(url);
        return Json.deserialize(body, Nonce.class);
    }

    /**
     * Executes the nonce request asynchronously.
     *
     * @return a future resolving to the account nonce
     */
    public CompletableFuture<Nonce> getAsync() {
        return transport.getAsync(url)
                .thenApply(body -> {
                    try {
                        return Json.deserialize(body, Nonce.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
