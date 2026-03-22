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

class NameServiceRequestTest {

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
        void nameRecordFullConstructor() {
            var record = new NameRecord("alice.dili", Address.of("dili1alice"),
                    Address.of("dili1owner"), "dili1target");
            assertEquals("alice.dili", record.name());
            assertEquals(Address.of("dili1alice"), record.address());
            assertEquals(Address.of("dili1owner"), record.owner());
            assertEquals("dili1target", record.target());
        }

        @Test
        void nameRecordCompactConstructor() {
            var record = new NameRecord("alice.dili", Address.of("dili1alice"));
            assertEquals("alice.dili", record.name());
            assertEquals(Address.of("dili1alice"), record.address());
            assertNull(record.owner());
            assertNull(record.target());
        }

        @Test
        void registrationCostRecord() {
            var cost = new RegistrationCost("alice.dili", 5_000_000_000_000_000_000L, "DILI");
            assertEquals("alice.dili", cost.name());
            assertEquals(5_000_000_000_000_000_000L, cost.cost());
            assertEquals("DILI", cost.currency());
        }

        @Test
        void registrationCostAsDili() {
            var cost = new RegistrationCost("alice.dili", 1_000_000_000_000_000_000L, "DILI");
            TokenAmount amt = cost.asDili();
            assertEquals(18, amt.decimals());
            assertEquals("1", amt.formatted());
        }

        @Test
        void registrationCostAsTokenAmount() {
            var cost = new RegistrationCost("alice.dili", 1_000_000L, "TOKEN");
            TokenAmount amt = cost.asTokenAmount(6);
            assertEquals(6, amt.decimals());
            assertEquals("1", amt.formatted());
        }
    }

    // ── Resolve queries ─────────────────────────────────────────────

    @Nested
    class ResolveTests {

        @Test
        void resolve() throws Exception {
            server.createContext("/names/resolve/alice.dili", exchange -> {
                respond(exchange, 200, "{\"name\":\"alice.dili\",\"address\":\"dili1alice\"}");
            });
            server.start();

            NameRecord record = client.names().resolve("alice.dili").get();
            assertEquals("alice.dili", record.name());
            assertEquals(Address.of("dili1alice"), record.address());
        }

        @Test
        void resolveAsync() throws Exception {
            server.createContext("/names/resolve/bob.dili", exchange -> {
                respond(exchange, 200, "{\"name\":\"bob.dili\",\"address\":\"dili1bob\"}");
            });
            server.start();

            NameRecord record = client.names().resolve("bob.dili").getAsync().get();
            assertEquals("bob.dili", record.name());
            assertEquals(Address.of("dili1bob"), record.address());
        }

        @Test
        void reverseResolveWithAddress() throws Exception {
            server.createContext("/names/reverse/dili1alice", exchange -> {
                respond(exchange, 200, "{\"name\":\"alice.dili\",\"address\":\"dili1alice\"}");
            });
            server.start();

            NameRecord record = client.names().reverseResolve(Address.of("dili1alice")).get();
            assertEquals("alice.dili", record.name());
        }

        @Test
        void reverseResolveWithString() throws Exception {
            server.createContext("/names/reverse/dili1bob", exchange -> {
                respond(exchange, 200, "{\"name\":\"bob.dili\",\"address\":\"dili1bob\"}");
            });
            server.start();

            NameRecord record = client.names().reverseResolve("dili1bob").get();
            assertEquals("bob.dili", record.name());
        }

        @Test
        void reverseResolveAsync() throws Exception {
            server.createContext("/names/reverse/dili1alice", exchange -> {
                respond(exchange, 200, "{\"name\":\"alice.dili\",\"address\":\"dili1alice\"}");
            });
            server.start();

            NameRecord record = client.names().reverseResolve(Address.of("dili1alice")).getAsync().get();
            assertEquals("alice.dili", record.name());
        }

        @Test
        void lookupName() throws Exception {
            server.createContext("/names/resolve/carol.dili", exchange -> {
                respond(exchange, 200, "{\"name\":\"carol.dili\",\"address\":\"dili1carol\"}");
            });
            server.start();

            NameRecord record = client.names().lookupName("carol.dili").get();
            assertEquals("carol.dili", record.name());
            assertEquals(Address.of("dili1carol"), record.address());
        }
    }

    // ── Availability check ──────────────────────────────────────────

    @Nested
    class AvailabilityTests {

        @Test
        void isNameAvailableTrue() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"available\""));
                assertTrue(bodyStr.contains("\"contract\":\"name_service\""));
                respond(exchange, 200, "{\"result\":{\"value\":true}}");
            });
            server.start();

            boolean available = client.names().isNameAvailable("new.dili").get();
            assertTrue(available);
        }

        @Test
        void isNameAvailableFalse() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"value\":false}}");
            });
            server.start();

            boolean available = client.names().isNameAvailable("taken.dili").get();
            assertFalse(available);
        }

        @Test
        void isNameAvailableAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"value\":true}}");
            });
            server.start();

            boolean available = client.names().isNameAvailable("async.dili").getAsync().get();
            assertTrue(available);
        }

        @Test
        void isNameAvailableAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "server error");
            });
            server.start();

            CompletableFuture<Boolean> future = client.names().isNameAvailable("fail.dili").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Name records query ──────────────────────────────────────────

    @Nested
    class NameRecordsTests {

        @Test
        void getNameRecords() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"get_records\""));
                respond(exchange, 200,
                        "{\"result\":{\"records\":{\"avatar\":\"https://img.example.com/a.png\",\"email\":\"alice@example.com\"}}}");
            });
            server.start();

            Map<String, String> records = client.names().getNameRecords("alice.dili").get();
            assertEquals("https://img.example.com/a.png", records.get("avatar"));
            assertEquals("alice@example.com", records.get("email"));
        }

        @Test
        void getNameRecordsEmptyWhenNoRecords() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"records\":null}}");
            });
            server.start();

            Map<String, String> records = client.names().getNameRecords("empty.dili").get();
            assertTrue(records.isEmpty());
        }

        @Test
        void getNameRecordsAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"records\":{\"website\":\"https://example.com\"}}}");
            });
            server.start();

            Map<String, String> records = client.names().getNameRecords("alice.dili").getAsync().get();
            assertEquals("https://example.com", records.get("website"));
        }

        @Test
        void getNameRecordsAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<Map<String, String>> future =
                    client.names().getNameRecords("fail.dili").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Names by owner ──────────────────────────────────────────────

    @Nested
    class NamesByOwnerTests {

        @Test
        void getNamesByOwnerWithString() throws Exception {
            server.createContext("/names/by-owner/dili1alice", exchange -> {
                respond(exchange, 200,
                        "[{\"name\":\"alice.dili\",\"address\":\"dili1alice\"},{\"name\":\"alice2.dili\",\"address\":\"dili1alice\"}]");
            });
            server.start();

            List<NameRecord> records = client.names().getNamesByOwner("dili1alice").get();
            assertEquals(2, records.size());
            assertEquals("alice.dili", records.get(0).name());
            assertEquals("alice2.dili", records.get(1).name());
        }

        @Test
        void getNamesByOwnerWithAddress() throws Exception {
            server.createContext("/names/by-owner/dili1bob", exchange -> {
                respond(exchange, 200, "[{\"name\":\"bob.dili\",\"address\":\"dili1bob\"}]");
            });
            server.start();

            List<NameRecord> records = client.names().getNamesByOwner(Address.of("dili1bob")).get();
            assertEquals(1, records.size());
            assertEquals("bob.dili", records.get(0).name());
        }

        @Test
        void getNamesByOwnerAsync() throws Exception {
            server.createContext("/names/by-owner/dili1carol", exchange -> {
                respond(exchange, 200, "[]");
            });
            server.start();

            List<NameRecord> records = client.names().getNamesByOwner("dili1carol").getAsync().get();
            assertTrue(records.isEmpty());
        }

        @Test
        void getNamesByOwnerAsyncError() throws Exception {
            server.createContext("/names/by-owner/dili1fail", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<List<NameRecord>> future =
                    client.names().getNamesByOwner("dili1fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Registration cost ───────────────────────────────────────────

    @Nested
    class RegistrationCostTests {

        @Test
        void getRegistrationCost() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"registration_cost\""));
                respond(exchange, 200,
                        "{\"name\":\"alice.dili\",\"cost\":1000000000000000000,\"currency\":\"DILI\"}");
            });
            server.start();

            RegistrationCost cost = client.names().getRegistrationCost("alice.dili").get();
            assertEquals("alice.dili", cost.name());
            assertEquals(1_000_000_000_000_000_000L, cost.cost());
            assertEquals("DILI", cost.currency());
        }

        @Test
        void getRegistrationCostAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"name\":\"bob.dili\",\"cost\":500000000000000000,\"currency\":\"DILI\"}");
            });
            server.start();

            RegistrationCost cost = client.names().getRegistrationCost("bob.dili").getAsync().get();
            assertEquals("bob.dili", cost.name());
        }

        @Test
        void getRegistrationCostAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<RegistrationCost> future =
                    client.names().getRegistrationCost("fail.dili").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Mutation: registerName ───────────────────────────────────────

    @Nested
    class RegisterNameTests {

        @Test
        void registerNameSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"register_name\""));
                assertTrue(bodyStr.contains("\"contract\":\"name_service\""));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xreg1\",\"status\":\"success\",\"blockHeight\":100,\"gasUsed\":500,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().registerName("alice.dili").send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xreg1"), receipt.txHash());
        }

        @Test
        void registerNameSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xreg2\",\"status\":\"success\",\"blockHeight\":101,\"gasUsed\":400,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().registerName("bob.dili").sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }

        @Test
        void registerNameAsyncError() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<Receipt> future = client.names().registerName("fail.dili").sendAsync(MOCK_SIGNER);
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Mutation: renewName ─────────────────────────────────────────

    @Nested
    class RenewNameTests {

        @Test
        void renewNameSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"renew\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xrenew1\",\"status\":\"success\",\"blockHeight\":110,\"gasUsed\":300,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().renewName("alice.dili").send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void renewNameSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xrenew2\",\"status\":\"success\",\"blockHeight\":111,\"gasUsed\":250,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().renewName("bob.dili").sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: transferName ───────────────────────────────────────

    @Nested
    class TransferNameTests {

        @Test
        void transferNameSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"transfer_name\""));
                assertTrue(bodyStr.contains("\"new_owner\":\"dili1bob\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xtransfer1\",\"status\":\"success\",\"blockHeight\":120,\"gasUsed\":400,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().transferName("alice.dili", "dili1bob").send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void transferNameSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xtransfer2\",\"status\":\"success\",\"blockHeight\":121,\"gasUsed\":350,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().transferName("alice.dili", "dili1carol").sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: setNameTarget ──────────────────────────────────────

    @Nested
    class SetNameTargetTests {

        @Test
        void setNameTargetSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"set_target\""));
                assertTrue(bodyStr.contains("\"target\":\"dili1target\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xset1\",\"status\":\"success\",\"blockHeight\":130,\"gasUsed\":200,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().setNameTarget("alice.dili", "dili1target").send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void setNameTargetSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xset2\",\"status\":\"success\",\"blockHeight\":131,\"gasUsed\":180,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().setNameTarget("alice.dili", "dili1other").sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: setNameRecord ──────────────────────────────────────

    @Nested
    class SetNameRecordTests {

        @Test
        void setNameRecordSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"set_record\""));
                assertTrue(bodyStr.contains("\"key\":\"avatar\""));
                assertTrue(bodyStr.contains("\"value\":\"https://img.example.com/a.png\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xrec1\",\"status\":\"success\",\"blockHeight\":140,\"gasUsed\":150,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names()
                    .setNameRecord("alice.dili", "avatar", "https://img.example.com/a.png")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void setNameRecordSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xrec2\",\"status\":\"success\",\"blockHeight\":141,\"gasUsed\":120,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names()
                    .setNameRecord("alice.dili", "email", "alice@example.com")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: releaseName ────────────────────────────────────────

    @Nested
    class ReleaseNameTests {

        @Test
        void releaseNameSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"release\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xrel1\",\"status\":\"success\",\"blockHeight\":150,\"gasUsed\":100,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().releaseName("alice.dili").send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void releaseNameSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xrel2\",\"status\":\"success\",\"blockHeight\":151,\"gasUsed\":90,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.names().releaseName("bob.dili").sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }
}
