package org.dilithia.sdk.request;

import com.google.gson.reflect.TypeToken;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.internal.HttpTransport;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.Credential;
import org.dilithia.sdk.model.CredentialSchema;
import org.dilithia.sdk.model.QueryResult;
import org.dilithia.sdk.model.Receipt;
import org.dilithia.sdk.model.SignedPayload;
import org.dilithia.sdk.model.VerificationResult;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Request group for credential and identity operations: schema registration,
 * credential issuance, revocation, verification, and queries.
 *
 * <pre>{@code
 * // Mutations (require signing)
 * Receipt r = client.credentials().registerSchema("KYC", "1.0", attrs).send(signer);
 * Receipt r = client.credentials().issueCredential(holder, schemaHash, commitment).send(signer);
 * Receipt r = client.credentials().revokeCredential(commitment).send(signer);
 *
 * // Queries
 * VerificationResult vr = client.credentials().verifyCredential(commitment, schemaHash, proof, revealed).get();
 * Credential c = client.credentials().getCredential(commitment).get();
 * CredentialSchema s = client.credentials().getSchema(schemaHash).get();
 * List<Credential> held = client.credentials().listCredentialsByHolder(holder).get();
 * List<Credential> issued = client.credentials().listCredentialsByIssuer(issuer).get();
 * }</pre>
 */
public final class CredentialRequest {

    private static final String CREDENTIAL_CONTRACT = "credential";

    private final HttpTransport transport;
    private final String baseUrl;
    private final String rpcUrl;

    /**
     * Creates a new credential request group.
     *
     * @param transport the HTTP transport
     * @param baseUrl   the chain base URL
     * @param rpcUrl    the RPC endpoint URL
     */
    public CredentialRequest(HttpTransport transport, String baseUrl, String rpcUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
        this.rpcUrl = rpcUrl;
    }

    // ── Mutations (require signing) ──────────────────────────────────

    /**
     * Creates a schema registration call builder.
     *
     * @param name       the schema name
     * @param version    the schema version
     * @param attributes the attribute definitions (list of maps with "name" and "type" keys)
     * @return a credential call builder
     */
    public CredentialCallBuilder registerSchema(String name, String version,
                                                 List<Map<String, String>> attributes) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("name", name);
        args.put("version", version);
        args.put("attributes", attributes);
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "register_schema", args, null);
        return new CredentialCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a credential issuance call builder.
     *
     * @param holder     the holder address
     * @param schemaHash the schema hash
     * @param commitment the credential commitment
     * @return a credential call builder
     */
    public CredentialCallBuilder issueCredential(String holder, String schemaHash,
                                                  String commitment) {
        Map<String, Object> args = Map.of(
                "holder", holder,
                "schema_hash", schemaHash,
                "commitment", commitment
        );
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "issue", args, null);
        return new CredentialCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a credential issuance call builder with optional attributes.
     *
     * @param holder     the holder address
     * @param schemaHash the schema hash
     * @param commitment the credential commitment
     * @param attributes optional attribute values
     * @return a credential call builder
     */
    public CredentialCallBuilder issueCredential(String holder, String schemaHash,
                                                  String commitment,
                                                  Map<String, Object> attributes) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("holder", holder);
        args.put("schema_hash", schemaHash);
        args.put("commitment", commitment);
        if (attributes != null && !attributes.isEmpty()) {
            args.put("attributes", attributes);
        }
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "issue", args, null);
        return new CredentialCallBuilder(transport, rpcUrl, call);
    }

    /**
     * Creates a credential revocation call builder.
     *
     * @param commitment the credential commitment to revoke
     * @return a credential call builder
     */
    public CredentialCallBuilder revokeCredential(String commitment) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "revoke", Map.of("commitment", commitment), null);
        return new CredentialCallBuilder(transport, rpcUrl, call);
    }

    // ── Queries ──────────────────────────────────────────────────────

    /**
     * Creates a request to verify a credential proof.
     *
     * @param commitment         the credential commitment
     * @param schemaHash         the schema hash
     * @param proof              the zero-knowledge proof
     * @param revealedAttributes the revealed attribute values
     * @return a typed request for the verification result
     */
    public VerifyCredentialRequest verifyCredential(String commitment, String schemaHash,
                                                     String proof,
                                                     Map<String, Object> revealedAttributes) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("commitment", commitment);
        args.put("schema_hash", schemaHash);
        args.put("proof", proof);
        args.put("revealed_attributes", revealedAttributes);
        args.put("predicates", List.of());
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "verify", args, null);
        return new VerifyCredentialRequest(transport, baseUrl, call, commitment);
    }

    /**
     * Creates a request to verify a credential proof with predicate checks.
     *
     * @param commitment         the credential commitment
     * @param schemaHash         the schema hash
     * @param proof              the zero-knowledge proof
     * @param revealedAttributes the revealed attribute values
     * @param predicates         the predicate checks
     * @return a typed request for the verification result
     */
    public VerifyCredentialRequest verifyCredential(String commitment, String schemaHash,
                                                     String proof,
                                                     Map<String, Object> revealedAttributes,
                                                     List<Map<String, Object>> predicates) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("commitment", commitment);
        args.put("schema_hash", schemaHash);
        args.put("proof", proof);
        args.put("revealed_attributes", revealedAttributes);
        args.put("predicates", predicates != null ? predicates : List.of());
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "verify", args, null);
        return new VerifyCredentialRequest(transport, baseUrl, call, commitment);
    }

    /**
     * Creates a request to fetch a credential by its commitment.
     *
     * @param commitment the credential commitment
     * @return a typed request for the credential
     */
    public GetCredentialRequest getCredential(String commitment) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "get_credential", Map.of("commitment", commitment), null);
        return new GetCredentialRequest(transport, baseUrl, call, commitment);
    }

    /**
     * Creates a request to fetch a credential schema by its hash.
     *
     * @param schemaHash the schema hash
     * @return a typed request for the schema
     */
    public GetSchemaRequest getSchema(String schemaHash) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "get_schema", Map.of("schema_hash", schemaHash), null);
        return new GetSchemaRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to list all credentials held by the given address.
     *
     * @param holder the holder address
     * @return a typed request for the credential list
     */
    public ListCredentialsRequest listCredentialsByHolder(String holder) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "list_by_holder", Map.of("holder", holder), null);
        return new ListCredentialsRequest(transport, baseUrl, call);
    }

    /**
     * Creates a request to list all credentials issued by the given address.
     *
     * @param issuer the issuer address
     * @return a typed request for the credential list
     */
    public ListCredentialsRequest listCredentialsByIssuer(String issuer) {
        Map<String, Object> call = DilithiaClient.buildContractCall(
                CREDENTIAL_CONTRACT, "list_by_issuer", Map.of("issuer", issuer), null);
        return new ListCredentialsRequest(transport, baseUrl, call);
    }

    // ── Inner request classes ────────────────────────────────────────

    /**
     * Builder for credential mutating calls that require signing.
     */
    public static final class CredentialCallBuilder {

        private final HttpTransport transport;
        private final String rpcUrl;
        private final Map<String, Object> call;

        CredentialCallBuilder(HttpTransport transport, String rpcUrl, Map<String, Object> call) {
            this.transport = transport;
            this.rpcUrl = rpcUrl;
            this.call = call;
        }

        /**
         * Signs and sends the credential call synchronously.
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
         * Signs and sends the credential call asynchronously.
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

    /**
     * Typed request for credential verification that returns {@link VerificationResult}.
     */
    public static final class VerifyCredentialRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;
        private final String commitment;

        VerifyCredentialRequest(HttpTransport transport, String baseUrl,
                                Map<String, Object> call, String commitment) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
            this.commitment = commitment;
        }

        /**
         * Executes the verification synchronously.
         *
         * @return the verification result
         * @throws DilithiaException if the request fails
         */
        public VerificationResult get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseResult(response);
        }

        /**
         * Executes the verification asynchronously.
         *
         * @return a future resolving to the verification result
         */
        public CompletableFuture<VerificationResult> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseResult(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private VerificationResult parseResult(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            boolean valid = data.get("valid") instanceof Boolean ? (Boolean) data.get("valid") : false;
            String reason = data.get("reason") instanceof String ? (String) data.get("reason") : null;
            return new VerificationResult(valid, commitment, reason);
        }
    }

    /**
     * Typed request that fetches a single credential by commitment.
     */
    public static final class GetCredentialRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;
        private final String commitment;

        GetCredentialRequest(HttpTransport transport, String baseUrl,
                             Map<String, Object> call, String commitment) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
            this.commitment = commitment;
        }

        /**
         * Executes the credential lookup synchronously.
         *
         * @return the credential, or {@code null} if not found
         * @throws DilithiaException if the request fails
         */
        public Credential get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseCredential(response);
        }

        /**
         * Executes the credential lookup asynchronously.
         *
         * @return a future resolving to the credential (or {@code null})
         */
        public CompletableFuture<Credential> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseCredential(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private Credential parseCredential(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            Map<String, Object> cred = (Map<String, Object>) data.get("credential");
            if (cred == null) {
                return null;
            }
            boolean revoked = data.get("revoked") instanceof Boolean ? (Boolean) data.get("revoked") : false;
            return new Credential(
                    commitment,
                    (String) cred.get("issuer"),
                    (String) cred.get("holder"),
                    (String) cred.get("schema_hash"),
                    cred.getOrDefault("status", "active").toString(),
                    revoked
            );
        }
    }

    /**
     * Typed request that fetches a credential schema by its hash.
     */
    public static final class GetSchemaRequest {

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        GetSchemaRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the schema lookup synchronously.
         *
         * @return the credential schema, or {@code null} if not found
         * @throws DilithiaException if the request fails
         */
        public CredentialSchema get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseSchema(response);
        }

        /**
         * Executes the schema lookup asynchronously.
         *
         * @return a future resolving to the credential schema (or {@code null})
         */
        public CompletableFuture<CredentialSchema> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseSchema(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private CredentialSchema parseSchema(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            Map<String, Object> schema = (Map<String, Object>) data.get("schema");
            if (schema == null) {
                return null;
            }
            List<Map<String, String>> attrs = (List<Map<String, String>>) schema.get("attributes");
            return new CredentialSchema(
                    (String) schema.get("name"),
                    (String) schema.get("version"),
                    attrs != null ? attrs : List.of()
            );
        }
    }

    /**
     * Typed request that lists credentials (by holder or issuer).
     */
    public static final class ListCredentialsRequest {

        private static final Type LIST_TYPE = new TypeToken<List<Credential>>() {}.getType();

        private final HttpTransport transport;
        private final String baseUrl;
        private final Map<String, Object> call;

        ListCredentialsRequest(HttpTransport transport, String baseUrl, Map<String, Object> call) {
            this.transport = transport;
            this.baseUrl = baseUrl;
            this.call = call;
        }

        /**
         * Executes the credential list query synchronously.
         *
         * @return the list of credentials
         * @throws DilithiaException if the request fails
         */
        @SuppressWarnings("unchecked")
        public List<Credential> get() throws DilithiaException {
            String body = Json.serialize(call);
            String response = transport.post(baseUrl + "/query", body);
            return parseList(response);
        }

        /**
         * Executes the credential list query asynchronously.
         *
         * @return a future resolving to the list of credentials
         */
        public CompletableFuture<List<Credential>> getAsync() {
            try {
                String body = Json.serialize(call);
                return transport.postAsync(baseUrl + "/query", body)
                        .thenApply(response -> {
                            try {
                                return parseList(response);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @SuppressWarnings("unchecked")
        private List<Credential> parseList(String response) throws DilithiaException {
            QueryResult result = Json.deserialize(response, QueryResult.class);
            Map<String, Object> data = result.result();
            Object credentials = data.get("credentials");
            if (credentials instanceof List) {
                return Json.deserialize(Json.serialize(credentials), LIST_TYPE);
            }
            return List.of();
        }
    }
}
