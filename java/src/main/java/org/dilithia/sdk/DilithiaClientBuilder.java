package org.dilithia.sdk;

import org.dilithia.sdk.exception.ValidationException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for {@link DilithiaClient}.
 *
 * <p>Obtain an instance via {@link Dilithia#client(String)}, configure it,
 * then call {@link #build()} to produce an immutable client.</p>
 *
 * <pre>{@code
 * DilithiaClient client = Dilithia.client("http://localhost:8000/rpc")
 *     .timeout(Duration.ofSeconds(15))
 *     .jwt("my-token")
 *     .build();
 * }</pre>
 */
public final class DilithiaClientBuilder {

    private final String rpcUrl;
    private Duration timeout = Duration.ofSeconds(10);
    private String jwt;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String chainBaseUrl;
    private String indexerUrl;
    private String oracleUrl;
    private String wsUrl;
    private HttpClient httpClient;

    /**
     * Creates a new builder targeting the given RPC URL.
     *
     * @param rpcUrl the base JSON-RPC endpoint URL
     * @throws NullPointerException if {@code rpcUrl} is null
     */
    DilithiaClientBuilder(String rpcUrl) {
        this.rpcUrl = Objects.requireNonNull(rpcUrl, "rpcUrl must not be null");
    }

    /**
     * Sets the HTTP request timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public DilithiaClientBuilder timeout(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        return this;
    }

    /**
     * Sets the HTTP request timeout in milliseconds.
     *
     * @param ms timeout in milliseconds; must be positive
     * @return this builder
     */
    public DilithiaClientBuilder timeoutMs(long ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + ms);
        }
        this.timeout = Duration.ofMillis(ms);
        return this;
    }

    /**
     * Sets the JWT bearer token for authentication.
     *
     * @param jwt the JWT token
     * @return this builder
     */
    public DilithiaClientBuilder jwt(String jwt) {
        this.jwt = jwt;
        return this;
    }

    /**
     * Adds a single custom HTTP header to every request.
     *
     * @param name  the header name
     * @param value the header value
     * @return this builder
     */
    public DilithiaClientBuilder header(String name, String value) {
        Objects.requireNonNull(name, "header name must not be null");
        Objects.requireNonNull(value, "header value must not be null");
        this.headers.put(name, value);
        return this;
    }

    /**
     * Adds multiple custom HTTP headers to every request.
     *
     * @param headers a map of header names to values
     * @return this builder
     */
    public DilithiaClientBuilder headers(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        this.headers.putAll(headers);
        return this;
    }

    /**
     * Overrides the chain base URL (derived from the RPC URL by default).
     *
     * @param chainBaseUrl the base URL for REST endpoints
     * @return this builder
     */
    public DilithiaClientBuilder chainBaseUrl(String chainBaseUrl) {
        this.chainBaseUrl = chainBaseUrl;
        return this;
    }

    /**
     * Sets the indexer service URL.
     *
     * @param indexerUrl the indexer URL
     * @return this builder
     */
    public DilithiaClientBuilder indexerUrl(String indexerUrl) {
        this.indexerUrl = indexerUrl;
        return this;
    }

    /**
     * Sets the oracle service URL.
     *
     * @param oracleUrl the oracle URL
     * @return this builder
     */
    public DilithiaClientBuilder oracleUrl(String oracleUrl) {
        this.oracleUrl = oracleUrl;
        return this;
    }

    /**
     * Overrides the WebSocket URL (derived from the base URL by default).
     *
     * @param wsUrl the WebSocket URL
     * @return this builder
     */
    public DilithiaClientBuilder wsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
        return this;
    }

    /**
     * Supplies a pre-configured {@link HttpClient} instead of creating one internally.
     *
     * @param httpClient the HTTP client to use for all requests
     * @return this builder
     */
    public DilithiaClientBuilder httpClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        return this;
    }

    /**
     * Validates the configuration and builds an immutable {@link DilithiaClient}.
     *
     * @return a new client instance
     * @throws ValidationException if the configuration is invalid (e.g. blank RPC URL)
     */
    public DilithiaClient build() throws ValidationException {
        if (rpcUrl.isBlank()) {
            throw new ValidationException("RPC URL must not be blank");
        }
        return new DilithiaClient(
                rpcUrl, timeout, jwt, Map.copyOf(headers),
                chainBaseUrl, indexerUrl, oracleUrl, wsUrl,
                httpClient
        );
    }
}
