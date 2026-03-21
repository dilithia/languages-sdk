package org.dilithia.sdk.request;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dilithia.sdk.Dilithia;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.HttpException;
import org.dilithia.sdk.model.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering edge cases and async error paths to push coverage higher.
 */
class RequestEdgeCaseTest {

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

    // ── Nonce async error ────────────────────────────────────────────

    @Test
    void nonceAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc/nonce/dili1fail", exchange -> {
            respond(exchange, 500, "server error");
        });
        server.start();

        CompletableFuture<Nonce> future = client.nonce("dili1fail").getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Receipt async error ──────────────────────────────────────────

    @Test
    void receiptAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc/receipt/0xfail", exchange -> {
            respond(exchange, 404, "not found");
        });
        server.start();

        CompletableFuture<Receipt> future = client.receipt("0xfail").getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Contract query async error ───────────────────────────────────

    @Test
    void contractQueryAsyncErrorPropagated() throws Exception {
        server.createContext("/query", exchange -> {
            respond(exchange, 500, "internal error");
        });
        server.start();

        CompletableFuture<QueryResult> future = client.contract("token").query("fail").getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Contract call async error ────────────────────────────────────

    @Test
    void contractCallAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 500, "internal error");
        });
        server.start();

        CompletableFuture<Receipt> future = client.contract("token")
                .call("transfer", Map.of("to", "bob"))
                .sendAsync(MOCK_SIGNER);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Deploy async error ───────────────────────────────────────────

    @Test
    void deployAsyncErrorPropagated() throws Exception {
        server.createContext("/deploy", exchange -> {
            respond(exchange, 500, "deploy failed");
        });
        server.start();

        DeployPayload payload = new DeployPayload(
                "token", "0xdead", Address.of("dili1alice"),
                "mldsa65", PublicKey.of("pk"), "sig", 1L, "chain-1", 1);
        CompletableFuture<Receipt> future = client.deploy(payload).sendAsync(MOCK_SIGNER);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Upgrade async error ──────────────────────────────────────────

    @Test
    void upgradeAsyncErrorPropagated() throws Exception {
        server.createContext("/upgrade", exchange -> {
            respond(exchange, 500, "upgrade failed");
        });
        server.start();

        DeployPayload payload = new DeployPayload(
                "token", "0xdead", Address.of("dili1alice"),
                "mldsa65", PublicKey.of("pk"), "sig", 1L, "chain-1", 1);
        CompletableFuture<Receipt> future = client.upgrade(payload).sendAsync(MOCK_SIGNER);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Network head async error ─────────────────────────────────────

    @Test
    void networkHeadAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"fail\"}}");
        });
        server.start();

        CompletableFuture<NetworkInfo> future = client.network().headAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Network gas estimate async error ─────────────────────────────

    @Test
    void gasEstimateAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"fail\"}}");
        });
        server.start();

        CompletableFuture<GasEstimate> future = client.network().gasEstimateAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Network baseFee non-number error ─────────────────────────────

    @Test
    void baseFeeNonNumberThrowsException() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"not a number\"}");
        });
        server.start();

        assertThrows(DilithiaException.class, () -> client.network().baseFee());
    }

    // ── Network baseFee async non-number ─────────────────────────────

    @Test
    void baseFeeAsyncNonNumberThrows() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"not a number\"}");
        });
        server.start();

        CompletableFuture<Long> future = client.network().baseFeeAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Name resolve async error ─────────────────────────────────────

    @Test
    void nameResolveAsyncErrorPropagated() throws Exception {
        server.createContext("/names/resolve/fail.dili", exchange -> {
            respond(exchange, 404, "not found");
        });
        server.start();

        CompletableFuture<NameRecord> future = client.names().resolve("fail.dili").getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── ABI async error ──────────────────────────────────────────────

    @Test
    void abiAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"fail\"}}");
        });
        server.start();

        CompletableFuture<ContractAbi> future = client.contract("token").abi().getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Address summary async error ──────────────────────────────────

    @Test
    void addressSummaryAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 200,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"fail\"}}");
        });
        server.start();

        CompletableFuture<AddressSummary> future = client.addressSummary("dili1fail").getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Shielded query async error ───────────────────────────────────

    @Test
    void shieldedQueryAsyncErrorPropagated() throws Exception {
        server.createContext("/query", exchange -> {
            respond(exchange, 500, "error");
        });
        server.start();

        CompletableFuture<QueryResult> future = client.shielded().commitmentRoot().getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Shielded call async error ────────────────────────────────────

    @Test
    void shieldedDepositAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 500, "error");
        });
        server.start();

        CompletableFuture<Receipt> future = client.shielded()
                .deposit("commit", 100L, "proof")
                .sendAsync(MOCK_SIGNER);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── RPC async error ──────────────────────────────────────────────

    @Test
    void rpcAsyncErrorPropagated() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 500, "error");
        });
        server.start();

        CompletableFuture<RpcResponse> future = client.rpc("fail_method", null).getAsync();
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Receipt waitFor non-404 error ────────────────────────────────

    @Test
    void waitForNon404ErrorThrowsImmediately() throws Exception {
        server.createContext("/rpc/receipt/0xerr", exchange -> {
            respond(exchange, 500, "server error");
        });
        server.start();

        HttpException ex = assertThrows(HttpException.class,
                () -> client.receipt("0xerr").waitFor(5, Duration.ofMillis(50)));
        assertEquals(500, ex.statusCode());
    }

    // ── HttpTransport async POST error ───────────────────────────────

    @Test
    void asyncPostNon2xxThrows() throws Exception {
        server.createContext("/rpc", exchange -> {
            respond(exchange, 502, "bad gateway");
        });
        server.start();

        // Test through JsonRpcClient callAsync path
        CompletableFuture<Map<String, Object>> future =
                new org.dilithia.sdk.internal.JsonRpcClient(
                        new org.dilithia.sdk.internal.HttpTransport(null, Duration.ofSeconds(5), Map.of()),
                        "http://localhost:" + server.getAddress().getPort() + "/rpc"
                ).callAsync("method", null);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    // ── Builder extra coverage ───────────────────────────────────────

    @Test
    void builderWithHeaders() throws Exception {
        DilithiaClient c = Dilithia.client("http://localhost:8000/rpc")
                .headers(Map.of("X-A", "1", "X-B", "2"))
                .build();
        assertNotNull(c);
        c.close();
    }

    @Test
    void builderWithHttpClient() throws Exception {
        DilithiaClient c = Dilithia.client("http://localhost:8000/rpc")
                .httpClient(java.net.http.HttpClient.newHttpClient())
                .build();
        assertNotNull(c);
        c.close();
    }

    @Test
    void builderRejectsNullHttpClient() {
        assertThrows(NullPointerException.class,
                () -> Dilithia.client("http://localhost:8000/rpc").httpClient(null));
    }

    @Test
    void builderRejectsNullTimeout() {
        assertThrows(NullPointerException.class,
                () -> Dilithia.client("http://localhost:8000/rpc").timeout(null));
    }

    @Test
    void builderRejectsNullHeaderName() {
        assertThrows(NullPointerException.class,
                () -> Dilithia.client("http://localhost:8000/rpc").header(null, "val"));
    }

    @Test
    void builderRejectsNullHeaderValue() {
        assertThrows(NullPointerException.class,
                () -> Dilithia.client("http://localhost:8000/rpc").header("key", null));
    }

    @Test
    void builderRejectsNullHeaders() {
        assertThrows(NullPointerException.class,
                () -> Dilithia.client("http://localhost:8000/rpc").headers(null));
    }

    @Test
    void builderRejectsZeroTimeoutMs() {
        assertThrows(IllegalArgumentException.class,
                () -> Dilithia.client("http://localhost:8000/rpc").timeoutMs(0));
    }

    // ── DilithiaClient wsUrl derivation edge case ────────────────────

    @Test
    void wsUrlNullForNonHttpUrl() throws Exception {
        // chainBaseUrl that doesn't start with http or https
        DilithiaClient c = Dilithia.client("ftp://localhost/rpc")
                .chainBaseUrl("ftp://localhost")
                .build();
        // wsUrl derivation returns null for non-http/https
        assertNull(c.wsUrl());
        c.close();
    }
}
