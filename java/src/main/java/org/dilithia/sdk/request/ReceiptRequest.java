package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.HttpException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.Receipt;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Request builder for fetching a transaction receipt.
 *
 * <p>Executes a GET to {@code /rpc/receipt/{txHash}}. Supports polling via
 * {@link #waitFor(int, Duration)}.</p>
 *
 * <pre>{@code
 * Receipt r = client.receipt("0xabc123").get();
 *
 * // Poll until the receipt is available
 * Receipt r = client.receipt("0xabc123").waitFor(30, Duration.ofSeconds(1));
 * }</pre>
 */
public final class ReceiptRequest {

    private final HttpTransport transport;
    private final String url;

    /**
     * Creates a new receipt request.
     *
     * @param transport the HTTP transport
     * @param rpcUrl    the RPC base URL
     * @param txHash    the transaction hash
     */
    public ReceiptRequest(HttpTransport transport, String rpcUrl, String txHash) {
        this.transport = transport;
        this.url = rpcUrl + "/receipt/" + txHash;
    }

    /**
     * Executes the receipt request synchronously.
     *
     * @return the transaction receipt
     * @throws DilithiaException if the request fails
     */
    public Receipt get() throws DilithiaException {
        String body = transport.get(url);
        return Json.deserialize(body, Receipt.class);
    }

    /**
     * Executes the receipt request asynchronously.
     *
     * @return a future resolving to the transaction receipt
     */
    public CompletableFuture<Receipt> getAsync() {
        return transport.getAsync(url)
                .thenApply(body -> {
                    try {
                        return Json.deserialize(body, Receipt.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Polls for the receipt until it is available or the maximum number of attempts is reached.
     *
     * <p>On each attempt, if the receipt endpoint returns a 404 (not yet available),
     * the method waits for {@code delay} before retrying.</p>
     *
     * @param maxAttempts the maximum number of polling attempts
     * @param delay       the delay between attempts
     * @return the transaction receipt
     * @throws DilithiaException if the receipt is not found after all attempts or another error occurs
     */
    public Receipt waitFor(int maxAttempts, Duration delay) throws DilithiaException {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return get();
            } catch (HttpException e) {
                if (e.statusCode() == 404 && i < maxAttempts - 1) {
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DilithiaException("Polling interrupted", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new DilithiaException("Receipt not found after " + maxAttempts + " attempts");
    }
}
