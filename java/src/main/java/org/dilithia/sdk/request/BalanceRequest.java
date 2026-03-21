package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.Balance;

import java.util.concurrent.CompletableFuture;

/**
 * Request builder for fetching an account balance.
 *
 * <p>Executes a GET to {@code /rpc/balance/{address}}.</p>
 *
 * <pre>{@code
 * Balance b = client.balance("dili1alice").get();
 * }</pre>
 */
public final class BalanceRequest {

    private final HttpTransport transport;
    private final String url;

    /**
     * Creates a new balance request.
     *
     * @param transport the HTTP transport
     * @param rpcUrl    the RPC base URL
     * @param address   the account address
     */
    public BalanceRequest(HttpTransport transport, String rpcUrl, String address) {
        this.transport = transport;
        this.url = rpcUrl + "/balance/" + address;
    }

    /**
     * Executes the balance request synchronously.
     *
     * @return the account balance
     * @throws DilithiaException if the request fails
     */
    public Balance get() throws DilithiaException {
        String body = transport.get(url);
        return Json.deserialize(body, Balance.class);
    }

    /**
     * Executes the balance request asynchronously.
     *
     * @return a future resolving to the account balance
     */
    public CompletableFuture<Balance> getAsync() {
        return transport.getAsync(url)
                .thenApply(body -> {
                    try {
                        return Json.deserialize(body, Balance.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
