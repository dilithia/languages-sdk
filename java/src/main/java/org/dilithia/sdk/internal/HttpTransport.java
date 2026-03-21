package org.dilithia.sdk.internal;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.HttpException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Internal HTTP transport layer wrapping {@link HttpClient}.
 *
 * <p>This class is not part of the public API.</p>
 */
public final class HttpTransport implements AutoCloseable {

    private final HttpClient client;
    private final Duration timeout;
    private final Map<String, String> defaultHeaders;

    /**
     * Creates a new transport.
     *
     * @param client         the HTTP client (if null, a default is created)
     * @param timeout        the request timeout
     * @param defaultHeaders headers to include on every request
     */
    public HttpTransport(HttpClient client, Duration timeout, Map<String, String> defaultHeaders) {
        this.client = client != null ? client : HttpClient.newHttpClient();
        this.timeout = timeout;
        this.defaultHeaders = defaultHeaders;
    }

    /**
     * Sends a synchronous GET request and returns the response body as a string.
     *
     * @param url the request URL
     * @return the response body
     * @throws DilithiaException if the request fails or returns a non-2xx status
     */
    public String get(String url) throws DilithiaException {
        HttpRequest request = buildGet(url);
        return execute(request);
    }

    /**
     * Sends an asynchronous GET request.
     *
     * @param url the request URL
     * @return a future resolving to the response body
     */
    public CompletableFuture<String> getAsync(String url) {
        HttpRequest request = buildGet(url);
        return executeAsync(request);
    }

    /**
     * Sends a synchronous POST request with a JSON body.
     *
     * @param url  the request URL
     * @param json the JSON body string
     * @return the response body
     * @throws DilithiaException if the request fails or returns a non-2xx status
     */
    public String post(String url, String json) throws DilithiaException {
        HttpRequest request = buildPost(url, json);
        return execute(request);
    }

    /**
     * Sends an asynchronous POST request with a JSON body.
     *
     * @param url  the request URL
     * @param json the JSON body string
     * @return a future resolving to the response body
     */
    public CompletableFuture<String> postAsync(String url, String json) {
        HttpRequest request = buildPost(url, json);
        return executeAsync(request);
    }

    @Override
    public void close() {
        // HttpClient does not implement Closeable in all JDK versions;
        // this is a no-op placeholder for future resource cleanup.
    }

    private HttpRequest buildGet(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .GET();
        defaultHeaders.forEach(builder::header);
        return builder.build();
    }

    private HttpRequest buildPost(String url, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        defaultHeaders.forEach(builder::header);
        return builder.build();
    }

    private String execute(HttpRequest request) throws DilithiaException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new HttpException(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new DilithiaException("HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DilithiaException("HTTP request interrupted", e);
        }
    }

    private CompletableFuture<String> executeAsync(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    }
                    throw new RuntimeException(
                            new HttpException(response.statusCode(), response.body()));
                });
    }
}
