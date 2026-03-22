package org.dilithia.sdk.request;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dilithia.sdk.Dilithia;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.model.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class CredentialRequestTest {

    private HttpServer server;
    private DilithiaClient client;

    private static final DilithiaSigner MOCK_SIGNER = payload ->
            new SignedPayload("mldsa65", PublicKey.of("pk_hex"), "sig_hex");

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        client = Dilithia.client("http://localhost:" + port + "/rpc")
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop(0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── Model record constructors ───────────────────────────────────

    @Nested
    class ModelTests {

        @Test
        void credentialRecord() {
            var cred = new Credential("commit1", "dili1issuer", "dili1holder",
                    "schema_hash_1", "active", false);
            assertEquals("commit1", cred.commitment());
            assertEquals("dili1issuer", cred.issuer());
            assertEquals("dili1holder", cred.holder());
            assertEquals("schema_hash_1", cred.schemaHash());
            assertEquals("active", cred.status());
            assertFalse(cred.revoked());
        }

        @Test
        void credentialRecordRevoked() {
            var cred = new Credential("commit2", "dili1issuer", "dili1holder",
                    "schema_hash_2", "revoked", true);
            assertTrue(cred.revoked());
            assertEquals("revoked", cred.status());
        }

        @Test
        void credentialSchemaRecord() {
            var attrs = List.of(
                    Map.of("name", "age", "type", "integer"),
                    Map.of("name", "country", "type", "string")
            );
            var schema = new CredentialSchema("KYC", "1.0", attrs);
            assertEquals("KYC", schema.name());
            assertEquals("1.0", schema.version());
            assertEquals(2, schema.attributes().size());
            assertEquals("age", schema.attributes().get(0).get("name"));
        }

        @Test
        void verificationResultValid() {
            var result = new VerificationResult(true, "commit1", null);
            assertTrue(result.valid());
            assertEquals("commit1", result.commitment());
            assertNull(result.reason());
        }

        @Test
        void verificationResultInvalid() {
            var result = new VerificationResult(false, "commit2", "expired credential");
            assertFalse(result.valid());
            assertEquals("expired credential", result.reason());
        }
    }

    // ── Mutation: registerSchema ─────────────────────────────────────

    @Nested
    class RegisterSchemaTests {

        @Test
        void registerSchemaSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"register_schema\""));
                assertTrue(bodyStr.contains("\"contract\":\"credential\""));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xschema1\",\"status\":\"success\",\"blockHeight\":10,\"gasUsed\":600,\"result\":null}");
            });
            server.start();

            var attrs = List.of(
                    Map.of("name", "age", "type", "integer")
            );
            Receipt receipt = client.credentials().registerSchema("KYC", "1.0", attrs).send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xschema1"), receipt.txHash());
        }

        @Test
        void registerSchemaSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xschema2\",\"status\":\"success\",\"blockHeight\":11,\"gasUsed\":500,\"result\":null}");
            });
            server.start();

            var attrs = List.of(
                    Map.of("name", "country", "type", "string")
            );
            Receipt receipt = client.credentials().registerSchema("ID", "2.0", attrs)
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }

        @Test
        void registerSchemaAsyncError() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<Receipt> future = client.credentials()
                    .registerSchema("FAIL", "1.0", List.of())
                    .sendAsync(MOCK_SIGNER);
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Mutation: issueCredential ────────────────────────────────────

    @Nested
    class IssueCredentialTests {

        @Test
        void issueCredentialSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"issue\""));
                assertTrue(bodyStr.contains("\"holder\":\"dili1holder\""));
                assertTrue(bodyStr.contains("\"commitment\":\"commit1\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xissue1\",\"status\":\"success\",\"blockHeight\":20,\"gasUsed\":700,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.credentials()
                    .issueCredential("dili1holder", "schema_hash_1", "commit1")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xissue1"), receipt.txHash());
        }

        @Test
        void issueCredentialWithAttributes() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"attributes\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xissue2\",\"status\":\"success\",\"blockHeight\":21,\"gasUsed\":750,\"result\":null}");
            });
            server.start();

            Map<String, Object> attrs = Map.of("age", 25, "country", "US");
            Receipt receipt = client.credentials()
                    .issueCredential("dili1holder", "schema_hash_1", "commit2", attrs)
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void issueCredentialWithNullAttributes() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertFalse(bodyStr.contains("\"attributes\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xissue3\",\"status\":\"success\",\"blockHeight\":22,\"gasUsed\":680,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.credentials()
                    .issueCredential("dili1holder", "schema_hash_1", "commit3", null)
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void issueCredentialWithEmptyAttributes() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertFalse(bodyStr.contains("\"attributes\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xissue4\",\"status\":\"success\",\"blockHeight\":23,\"gasUsed\":660,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.credentials()
                    .issueCredential("dili1holder", "schema_hash_1", "commit4", Map.of())
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void issueCredentialSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xissue5\",\"status\":\"success\",\"blockHeight\":24,\"gasUsed\":700,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.credentials()
                    .issueCredential("dili1holder", "schema_hash_1", "commit5")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: revokeCredential ───────────────────────────────────

    @Nested
    class RevokeCredentialTests {

        @Test
        void revokeCredentialSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"revoke\""));
                assertTrue(bodyStr.contains("\"commitment\":\"commit1\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xrevoke1\",\"status\":\"success\",\"blockHeight\":30,\"gasUsed\":300,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.credentials().revokeCredential("commit1").send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xrevoke1"), receipt.txHash());
        }

        @Test
        void revokeCredentialSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xrevoke2\",\"status\":\"success\",\"blockHeight\":31,\"gasUsed\":250,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.credentials().revokeCredential("commit2")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Query: verifyCredential ─────────────────────────────────────

    @Nested
    class VerifyCredentialTests {

        @Test
        void verifyCredentialValid() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"verify\""));
                assertTrue(bodyStr.contains("\"commitment\":\"commit1\""));
                respond(exchange, 200, "{\"result\":{\"valid\":true}}");
            });
            server.start();

            VerificationResult result = client.credentials()
                    .verifyCredential("commit1", "schema1", "proof_hex", Map.of("age", 25))
                    .get();
            assertTrue(result.valid());
            assertEquals("commit1", result.commitment());
            assertNull(result.reason());
        }

        @Test
        void verifyCredentialInvalid() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"valid\":false,\"reason\":\"expired\"}}");
            });
            server.start();

            VerificationResult result = client.credentials()
                    .verifyCredential("commit2", "schema1", "proof_hex", Map.of())
                    .get();
            assertFalse(result.valid());
            assertEquals("expired", result.reason());
        }

        @Test
        void verifyCredentialWithPredicates() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"predicates\""));
                respond(exchange, 200, "{\"result\":{\"valid\":true}}");
            });
            server.start();

            var predicates = List.of(
                    Map.<String, Object>of("attribute", "age", "op", ">=", "value", 18)
            );
            VerificationResult result = client.credentials()
                    .verifyCredential("commit3", "schema1", "proof_hex", Map.of(), predicates)
                    .get();
            assertTrue(result.valid());
        }

        @Test
        void verifyCredentialWithNullPredicates() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"valid\":true}}");
            });
            server.start();

            VerificationResult result = client.credentials()
                    .verifyCredential("commit4", "schema1", "proof_hex", Map.of(), null)
                    .get();
            assertTrue(result.valid());
        }

        @Test
        void verifyCredentialAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"valid\":true}}");
            });
            server.start();

            VerificationResult result = client.credentials()
                    .verifyCredential("commit5", "schema1", "proof_hex", Map.of())
                    .getAsync().get();
            assertTrue(result.valid());
        }

        @Test
        void verifyCredentialAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<VerificationResult> future = client.credentials()
                    .verifyCredential("commit_fail", "schema1", "proof_hex", Map.of())
                    .getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Query: getCredential ────────────────────────────────────────

    @Nested
    class GetCredentialTests {

        @Test
        void getCredential() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"get_credential\""));
                respond(exchange, 200,
                        "{\"result\":{\"credential\":{\"issuer\":\"dili1issuer\",\"holder\":\"dili1holder\",\"schema_hash\":\"sh1\",\"status\":\"active\"},\"revoked\":false}}");
            });
            server.start();

            Credential cred = client.credentials().getCredential("commit1").get();
            assertNotNull(cred);
            assertEquals("commit1", cred.commitment());
            assertEquals("dili1issuer", cred.issuer());
            assertEquals("dili1holder", cred.holder());
            assertEquals("sh1", cred.schemaHash());
            assertEquals("active", cred.status());
            assertFalse(cred.revoked());
        }

        @Test
        void getCredentialRevoked() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"credential\":{\"issuer\":\"dili1issuer\",\"holder\":\"dili1holder\",\"schema_hash\":\"sh2\",\"status\":\"revoked\"},\"revoked\":true}}");
            });
            server.start();

            Credential cred = client.credentials().getCredential("commit2").get();
            assertNotNull(cred);
            assertTrue(cred.revoked());
        }

        @Test
        void getCredentialNotFound() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"credential\":null}}");
            });
            server.start();

            Credential cred = client.credentials().getCredential("missing").get();
            assertNull(cred);
        }

        @Test
        void getCredentialAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"credential\":{\"issuer\":\"i\",\"holder\":\"h\",\"schema_hash\":\"s\",\"status\":\"active\"},\"revoked\":false}}");
            });
            server.start();

            Credential cred = client.credentials().getCredential("commit3").getAsync().get();
            assertNotNull(cred);
        }

        @Test
        void getCredentialAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<Credential> future = client.credentials().getCredential("fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Query: getSchema ────────────────────────────────────────────

    @Nested
    class GetSchemaTests {

        @Test
        void getSchema() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"get_schema\""));
                respond(exchange, 200,
                        "{\"result\":{\"schema\":{\"name\":\"KYC\",\"version\":\"1.0\",\"attributes\":[{\"name\":\"age\",\"type\":\"integer\"}]}}}");
            });
            server.start();

            CredentialSchema schema = client.credentials().getSchema("schema_hash_1").get();
            assertNotNull(schema);
            assertEquals("KYC", schema.name());
            assertEquals("1.0", schema.version());
            assertEquals(1, schema.attributes().size());
            assertEquals("age", schema.attributes().get(0).get("name"));
        }

        @Test
        void getSchemaNotFound() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"schema\":null}}");
            });
            server.start();

            CredentialSchema schema = client.credentials().getSchema("missing").get();
            assertNull(schema);
        }

        @Test
        void getSchemaNoAttributes() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"schema\":{\"name\":\"Simple\",\"version\":\"1.0\",\"attributes\":null}}}");
            });
            server.start();

            CredentialSchema schema = client.credentials().getSchema("simple_hash").get();
            assertNotNull(schema);
            assertTrue(schema.attributes().isEmpty());
        }

        @Test
        void getSchemaAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"schema\":{\"name\":\"ID\",\"version\":\"2.0\",\"attributes\":[]}}}");
            });
            server.start();

            CredentialSchema schema = client.credentials().getSchema("sh2").getAsync().get();
            assertNotNull(schema);
            assertEquals("ID", schema.name());
        }

        @Test
        void getSchemaAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<CredentialSchema> future = client.credentials().getSchema("fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Query: listCredentialsByHolder ───────────────────────────────

    @Nested
    class ListByHolderTests {

        @Test
        void listCredentialsByHolder() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"list_by_holder\""));
                respond(exchange, 200,
                        "{\"result\":{\"credentials\":[{\"commitment\":\"c1\",\"issuer\":\"i1\",\"holder\":\"h1\",\"schemaHash\":\"s1\",\"status\":\"active\",\"revoked\":false}]}}");
            });
            server.start();

            List<Credential> creds = client.credentials().listCredentialsByHolder("dili1holder").get();
            assertEquals(1, creds.size());
            assertEquals("c1", creds.get(0).commitment());
        }

        @Test
        void listCredentialsByHolderEmpty() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"credentials\":null}}");
            });
            server.start();

            List<Credential> creds = client.credentials().listCredentialsByHolder("dili1empty").get();
            assertTrue(creds.isEmpty());
        }

        @Test
        void listCredentialsByHolderAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"credentials\":[{\"commitment\":\"c2\",\"issuer\":\"i2\",\"holder\":\"h2\",\"schemaHash\":\"s2\",\"status\":\"active\",\"revoked\":false}]}}");
            });
            server.start();

            List<Credential> creds = client.credentials().listCredentialsByHolder("dili1holder")
                    .getAsync().get();
            assertEquals(1, creds.size());
        }

        @Test
        void listCredentialsByHolderAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<List<Credential>> future =
                    client.credentials().listCredentialsByHolder("fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Query: listCredentialsByIssuer ───────────────────────────────

    @Nested
    class ListByIssuerTests {

        @Test
        void listCredentialsByIssuer() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"list_by_issuer\""));
                respond(exchange, 200,
                        "{\"result\":{\"credentials\":[{\"commitment\":\"c3\",\"issuer\":\"i3\",\"holder\":\"h3\",\"schemaHash\":\"s3\",\"status\":\"active\",\"revoked\":false}]}}");
            });
            server.start();

            List<Credential> creds = client.credentials().listCredentialsByIssuer("dili1issuer").get();
            assertEquals(1, creds.size());
        }

        @Test
        void listCredentialsByIssuerAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"credentials\":[]}}");
            });
            server.start();

            List<Credential> creds = client.credentials().listCredentialsByIssuer("dili1issuer")
                    .getAsync().get();
            assertTrue(creds.isEmpty());
        }
    }
}
