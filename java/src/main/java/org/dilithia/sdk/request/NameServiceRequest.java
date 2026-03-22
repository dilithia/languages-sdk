package org.dilithia.sdk.request;

import com.google.gson.reflect.TypeToken;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.NameRecord;
import org.dilithia.sdk.model.QueryResult;
import org.dilithia.sdk.model.Receipt;
import org.dilithia.sdk.model.RegistrationCost;
import org.dilithia.sdk.model.SignedPayload;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Request group for name-service operations: resolve, reverse-resolve,
 * registration, renewal, transfer, and record management.
 *
 * <pre>{@code
 * // Queries
 * NameRecord record = client.names().resolve("alice").get();
 * NameRecord reverse = client.names().reverseResolve("dili1abc").get();
 * boolean avail = client.names().isNameAvailable("alice").get();
 * NameRecord lookup = client.names().lookupName("alice").get();
 * Map<String, String> records = client.names().getNameRecords("alice").get();
 * List<NameRecord> owned = client.names().getNamesByOwner("dili1abc").get();
 * RegistrationCost cost = client.names().getRegistrationCost("alice").get();
 *
 * // Mutations (require signing)
 * Receipt r = client.names().registerName("alice").send(signer);
 * Receipt r = client.names().renewName("alice").send(signer);
 * Receipt r = client.names().transferName("alice", "dili1bob").send(signer);
 * Receipt r = client.names().setNameTarget("alice", "dili1target").send(signer);
 * Receipt r = client.names().setNameRecord("alice", "avatar", "https://...").send(signer);
 * Receipt r = client.names().releaseName("alice").send(signer);
 * }</pre>
 */
public final class NameServiceRequest {

    private static final String NAME_SERVICE_CONTRACT = "name_service";

    private final HttpTransport transport;
    private final String baseUrl;
    private final String rpcUrl;

    /**
     * Creates a new name service request group.
     *
     * @param transport the HTTP transport
     * @param baseUrl   the chain base URL
     * @param rpcUrl    the RPC endpoint URL
     */
    public NameServiceRequest(HttpTransport transport, String baseUrl, String rpcUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
        this.rpcUrl = rpcUrl;
    }

    // ── Read-only queries ────────────────────────────────────────────

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
     * Creates a request to check whether a name is available for registration.
     *
     * @param name the name to check
     * @return a typed request that returns {@code true} if the name is available
     */
    public NameAvailableRequest isNameAvailable(String name) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "available", Map.of("name", name), null);
        return new NameAvailableRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to look up detailed name information via contract query.
     *
     * @param name the name to look up
     * @return a typed request for the name record
     */
    public NameResolveRequest lookupName(String name) {
        return new NameResolveRequest(transport, baseUrl + "/names/resolve/" + name);
    }

    /**
     * Creates a request to fetch all key-value records associated with a name.
     *
     * @param name the name whose records to fetch
     * @return a typed request returning the record map
     */
    public NameRecordsRequest getNameRecords(String name) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "get_records", Map.of("name", name), null);
        return new NameRecordsRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to list all names owned by the given address.
     *
     * @param address the owner address
     * @return a typed request returning the list of name records
     */
    public NamesByOwnerRequest getNamesByOwner(String address) {
        return new NamesByOwnerRequest(transport, baseUrl + "/names/by-owner/" + address);
    }

    /**
     * Creates a request to list all names owned by the given address.
     *
     * @param address the owner address
     * @return a typed request returning the list of name records
     */
    public NamesByOwnerRequest getNamesByOwner(Address address) {
        return getNamesByOwner(address.value());
    }

    /**
     * Creates a request to fetch the registration cost for a name.
     *
     * @param name the name to quote
     * @return a typed request for the registration cost
     */
    public RegistrationCostRequest getRegistrationCost(String name) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "registration_cost", Map.of("name", name), null);
        return new RegistrationCostRequest(transport, baseUrl, call);
    }

    // ── Mutations (require signing) ──────────────────────────────────

    /**
     * Creates a name registration call builder.
     *
     * @param name the name to register
     * @return a name call builder
     */
    public NameCallBuilder registerName(String name) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "register_name", Map.of("name", name), null);
        return new NameCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a name renewal call builder.
     *
     * @param name the name to renew
     * @return a name call builder
     */
    public NameCallBuilder renewName(String name) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "renew", Map.of("name", name), null);
        return new NameCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a name transfer call builder.
     *
     * @param name     the name to transfer
     * @param newOwner the new owner address
     * @return a name call builder
     */
    public NameCallBuilder transferName(String name, String newOwner) {
        Map<String, Object> args = Map.of("name", name, "new_owner", newOwner);
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "transfer_name", args, null);
        return new NameCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a call builder to set the resolution target for a name.
     *
     * @param name   the name whose target to set
     * @param target the target address
     * @return a name call builder
     */
    public NameCallBuilder setNameTarget(String name, String target) {
        Map<String, Object> args = Map.of("name", name, "target", target);
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "set_target", args, null);
        return new NameCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a call builder to set a key-value record on a name.
     *
     * @param name  the name on which to set the record
     * @param key   the record key
     * @param value the record value
     * @return a name call builder
     */
    public NameCallBuilder setNameRecord(String name, String key, String value) {
        Map<String, Object> args = Map.of("name", name, "key", key, "value", value);
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "set_record", args, null);
        return new NameCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a call builder to release a name, making it available for others.
     *
     * @param name the name to release
     * @return a name call builder
     */
    public NameCallBuilder releaseName(String name) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                NAME_SERVICE_CONTRACT, "release", Map.of("name", name), null);
        return new NameCallBuilder(transport, rpcUrl, call);
    }

    // ── Inner request classes ────────────────────────────────────────

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

    /**
     * Typed request that checks name availability and returns a boolean.
     */
    public static final class NameAvailableRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        NameAvailableRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the availability check synchronously.
         *
         * @return {@code true} if the name is available
         * @throws DilithiaException if the request fails
         */
        public boolean get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Object value = result.result().get("value");
            return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
        }

        /**
         * Executes the availability check asynchronously.
         *
         * @return a future resolving to {@code true} if the name is available
         */
        public CompletableFuture<Boolean> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                QueryResult result = Json.deserialize(response, QueryResult.class);
                                Object value = result.result().get("value");
                                return value instanceof Boolean ? (Boolean) value
                                        : Boolean.parseBoolean(String.valueOf(value));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    /**
     * Typed request that returns a map of name records (key-value pairs).
     */
    public static final class NameRecordsRequest {

        private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        NameRecordsRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the records query synchronously.
         *
         * @return the name records map
         * @throws DilithiaException if the request fails
         */
        @SuppressWarnings("unchecked")
        public Map<String, String> get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Object records = result.result().get("records");
            if (records instanceof Map) {
                return (Map<String, String>) records;
            }
            return Map.of();
        }

        /**
         * Executes the records query asynchronously.
         *
         * @return a future resolving to the name records map
         */
        public CompletableFuture<Map<String, String>> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseRecords(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, String> parseRecords(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Object records = result.result().get("records");
            if (records instanceof Map) {
                return (Map<String, String>) records;
            }
            return Map.of();
        }
    }

    /**
     * Typed request that returns a list of name records by owner.
     */
    public static final class NamesByOwnerRequest {

        private static final Type LIST_TYPE = new TypeToken<List<NameRecord>>() {}.getType();

        private final HttpTransport transport;
        private final String url;

        NamesByOwnerRequest(HttpTransport transport, String url) {
            this.transport = transport;
            this.url = url;
        }

        /**
         * Executes the lookup synchronously.
         *
         * @return the list of name records owned by the address
         * @throws DilithiaException if the request fails
         */
        public List<NameRecord> get() throws DilithiaException {
            String body = transport.get(url);
            return Json.deserialize(body, LIST_TYPE);
        }

        /**
         * Executes the lookup asynchronously.
         *
         * @return a future resolving to the list of name records
         */
        public CompletableFuture<List<NameRecord>> getAsync() {
            return transport.getAsync(url)
                    .thenApply(body -> {
                        try {
                            return Json.deserialize(body, LIST_TYPE);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    /**
     * Typed request that returns the registration cost for a name.
     */
    public static final class RegistrationCostRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        RegistrationCostRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the cost query synchronously.
         *
         * @return the registration cost
         * @throws DilithiaException if the request fails
         */
        public RegistrationCost get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return Json.deserialize(response, RegistrationCost.class);
        }

        /**
         * Executes the cost query asynchronously.
         *
         * @return a future resolving to the registration cost
         */
        public CompletableFuture<RegistrationCost> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return Json.deserialize(response, RegistrationCost.class);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    /**
     * Builder for name-service mutating calls that require signing.
     */
    public static final class NameCallBuilder {

        private final HttpTransport transport;
        private final String rpcUrl;
        private final Map<String, Object> call;

        NameCallBuilder(HttpTransport transport, String rpcUrl, Map<String, Object> call) {
            this.transport = transport;
            this.rpcUrl = rpcUrl;
            this.call = call;
        }

        /**
         * Signs and sends the name-service call synchronously.
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
         * Signs and sends the name-service call asynchronously.
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
}
