package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.QueryResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builder for read-only contract queries.
 *
 * <p>Executes a GET to {@code /query?contract={name}&method={method}&args={json}}.</p>
 *
 * <pre>{@code
 * QueryResult result = client.contract("token").query("totalSupply").get();
 * }</pre>
 */
public final class ContractQueryBuilder {

    private final HttpTransport transport;
    private final String url;

    /**
     * Creates a new contract query builder.
     *
     * @param transport    the HTTP transport
     * @param baseUrl      the chain base URL
     * @param contractName the contract name
     * @param method       the query method name
     * @param args         the query arguments
     */
    ContractQueryBuilder(HttpTransport transport, String baseUrl,
                         String contractName, String method, Map<String, Object> args) {
        this.transport = transport;
        String argsJson;
        try {
            argsJson = Json.serialize(args != null ? args : Map.of());
        } catch (Exception e) {
            argsJson = "{}";
        }
        this.url = baseUrl + "/query"
                + "?contract=" + urlEncode(contractName)
                + "&method=" + urlEncode(method)
                + "&args=" + urlEncode(argsJson);
    }

    /**
     * Executes the query synchronously.
     *
     * @return the query result
     * @throws DilithiaException if the request fails
     */
    public QueryResult get() throws DilithiaException {
        String body = transport.get(url);
        return Json.deserialize(body, QueryResult.class);
    }

    /**
     * Executes the query asynchronously.
     *
     * @return a future resolving to the query result
     */
    public CompletableFuture<QueryResult> getAsync() {
        return transport.getAsync(url)
                .thenApply(body -> {
                    try {
                        return Json.deserialize(body, QueryResult.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
