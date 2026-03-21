package org.dilithia.sdk.request;

import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.NameRecord;

import java.util.concurrent.CompletableFuture;

/**
 * Request group for name-service operations: resolve and reverse-resolve.
 *
 * <pre>{@code
 * NameRecord record = client.names().resolve("alice").get();
 * NameRecord reverse = client.names().reverseResolve("dili1abc").get();
 * }</pre>
 */
public final class NameServiceRequest {

    private final HttpTransport transport;
    private final String baseUrl;

    /**
     * Creates a new name service request group.
     *
     * @param transport the HTTP transport
     * @param baseUrl   the chain base URL
     */
    public NameServiceRequest(HttpTransport transport, String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a request to resolve a name to an address.
     *
     * @param name the name to resolve
     * @return a typed request for the name record
     */
    public NameResolveRequest resolve(String name) {
        return new NameResolveRequest(transport, baseUrl + "/names/resolve/" + name);
    }

    /**
     * Creates a request to reverse-resolve an address to a name.
     *
     * @param address the address to reverse-resolve
     * @return a typed request for the name record
     */
    public NameResolveRequest reverseResolve(Address address) {
        return new NameResolveRequest(transport, baseUrl + "/names/reverse/" + address.value());
    }

    /**
     * Creates a request to reverse-resolve an address string to a name (convenience overload).
     *
     * @param address the address to reverse-resolve as a plain string
     * @return a typed request for the name record
     */
    public NameResolveRequest reverseResolve(String address) {
        return reverseResolve(Address.of(address));
    }

    /**
     * Typed request for name-service lookups that returns {@link NameRecord}.
     */
    public static final class NameResolveRequest {

        private final HttpTransport transport;
        private final String url;

        NameResolveRequest(HttpTransport transport, String url) {
            this.transport = transport;
            this.url = url;
        }

        /**
         * Executes the name lookup synchronously.
         *
         * @return the name record
         * @throws DilithiaException if the request fails
         */
        public NameRecord get() throws DilithiaException {
            String body = transport.get(url);
            return Json.deserialize(body, NameRecord.class);
        }

        /**
         * Executes the name lookup asynchronously.
         *
         * @return a future resolving to the name record
         */
        public CompletableFuture<NameRecord> getAsync() {
            return transport.getAsync(url)
                    .thenApply(body -> {
                        try {
                            return Json.deserialize(body, NameRecord.class);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
