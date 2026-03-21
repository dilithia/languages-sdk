package org.dilithia.sdk.request;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dilithia.sdk.Dilithia;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.exception.HttpException;
import org.dilithia.sdk.exception.RpcException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RequestIntegrationTest {

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

    // ── Balance ──────────────────────────────────────────────────────

    @Nested
    class BalanceTests {

        @Test
        void getBalance() throws Exception {
            server.createContext("/rpc/balance/dili1alice", exchange -> {
                respond(exchange, 200, "{\"address\":\"dili1alice\",\"balance\":1000000}");
            });
            server.start();

            Balance bal = client.balance("dili1alice").get();
            assertEquals(Address.of("dili1alice"), bal.address());
            assertEquals(1000000L, bal.balance());
        }

        @Test
        void getBalanceWithAddressType() throws Exception {
            server.createContext("/rpc/balance/dili1bob", exchange -> {
                respond(exchange, 200, "{\"address\":\"dili1bob\",\"balance\":500}");
            });
            server.start();

            Balance bal = client.balance(Address.of("dili1bob")).get();
            assertEquals(Address.of("dili1bob"), bal.address());
            assertEquals(500L, bal.balance());
        }

        @Test
        void getBalanceAsync() throws Exception {
            server.createContext("/rpc/balance/dili1alice", exchange -> {
                respond(exchange, 200, "{\"address\":\"dili1alice\",\"balance\":2000}");
            });
            server.start();

            CompletableFuture<Balance> future = client.balance("dili1alice").getAsync();
            Balance bal = future.get();
            assertEquals(2000L, bal.balance());
        }
    }

    // ── Nonce ────────────────────────────────────────────────────────

    @Nested
    class NonceTests {

        @Test
        void getNonce() throws Exception {
            server.createContext("/rpc/nonce/dili1alice", exchange -> {
                respond(exchange, 200, "{\"address\":\"dili1alice\",\"nonce\":42}");
            });
            server.start();

            Nonce nonce = client.nonce("dili1alice").get();
            assertEquals(Address.of("dili1alice"), nonce.address());
            assertEquals(42L, nonce.nonce());
        }

        @Test
        void getNonceWithAddressType() throws Exception {
            server.createContext("/rpc/nonce/dili1bob", exchange -> {
                respond(exchange, 200, "{\"address\":\"dili1bob\",\"nonce\":7}");
            });
            server.start();

            Nonce nonce = client.nonce(Address.of("dili1bob")).get();
            assertEquals(7L, nonce.nonce());
        }

        @Test
        void getNonceAsync() throws Exception {
            server.createContext("/rpc/nonce/dili1alice", exchange -> {
                respond(exchange, 200, "{\"address\":\"dili1alice\",\"nonce\":10}");
            });
            server.start();

            Nonce nonce = client.nonce("dili1alice").getAsync().get();
            assertEquals(10L, nonce.nonce());
        }
    }

    // ── Receipt ──────────────────────────────────────────────────────

    @Nested
    class ReceiptTests {

        @Test
        void getReceipt() throws Exception {
            server.createContext("/rpc/receipt/0xabc123", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xabc123\",\"status\":\"success\",\"blockHeight\":100,\"gasUsed\":500,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.receipt("0xabc123").get();
            assertEquals(TxHash.of("0xabc123"), receipt.txHash());
            assertEquals("success", receipt.status());
            assertEquals(100L, receipt.blockHeight());
            assertEquals(500L, receipt.gasUsed());
        }

        @Test
        void getReceiptWithTxHashType() throws Exception {
            server.createContext("/rpc/receipt/0xdef", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xdef\",\"status\":\"success\",\"blockHeight\":50,\"gasUsed\":100,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.receipt(TxHash.of("0xdef")).get();
            assertEquals("success", receipt.status());
        }

        @Test
        void getReceiptAsync() throws Exception {
            server.createContext("/rpc/receipt/0xabc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xabc\",\"status\":\"success\",\"blockHeight\":1,\"gasUsed\":10,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.receipt("0xabc").getAsync().get();
            assertEquals("success", receipt.status());
        }

        @Test
        void waitForPollsUntilFound() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);
            server.createContext("/rpc/receipt/0xpoll", exchange -> {
                int count = callCount.incrementAndGet();
                if (count < 3) {
                    respond(exchange, 404, "not found");
                } else {
                    respond(exchange, 200,
                            "{\"txHash\":\"0xpoll\",\"status\":\"success\",\"blockHeight\":5,\"gasUsed\":50,\"result\":null}");
                }
            });
            server.start();

            Receipt receipt = client.receipt("0xpoll").waitFor(5, Duration.ofMillis(50));
            assertEquals("success", receipt.status());
            assertTrue(callCount.get() >= 3);
        }

        @Test
        void waitForThrowsAfterMaxAttempts() throws Exception {
            server.createContext("/rpc/receipt/0xmissing", exchange -> {
                respond(exchange, 404, "not found");
            });
            server.start();

            HttpException ex = assertThrows(HttpException.class,
                    () -> client.receipt("0xmissing").waitFor(3, Duration.ofMillis(50)));
            assertEquals(404, ex.statusCode());
        }
    }

    // ── Network ──────────────────────────────────────────────────────

    @Nested
    class NetworkTests {

        @Test
        void head() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"blockHeight\":500,\"blockHash\":\"0xhash\",\"chainId\":\"dilithia-1\"}}");
            });
            server.start();

            NetworkInfo info = client.network().head();
            assertEquals(500L, info.blockHeight());
            assertEquals("0xhash", info.blockHash());
            assertEquals("dilithia-1", info.chainId());
        }

        @Test
        void headAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"blockHeight\":501,\"blockHash\":\"0xh2\",\"chainId\":\"d1\"}}");
            });
            server.start();

            NetworkInfo info = client.network().headAsync().get();
            assertEquals(501L, info.blockHeight());
        }

        @Test
        void gasEstimate() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"gasLimit\":1000000,\"gasPrice\":10}}");
            });
            server.start();

            GasEstimate gas = client.network().gasEstimate();
            assertEquals(1000000L, gas.gasLimit());
            assertEquals(10L, gas.gasPrice());
        }

        @Test
        void gasEstimateAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"gasLimit\":500000,\"gasPrice\":5}}");
            });
            server.start();

            GasEstimate gas = client.network().gasEstimateAsync().get();
            assertEquals(500000L, gas.gasLimit());
        }

        @Test
        void baseFee() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":100}");
            });
            server.start();

            long fee = client.network().baseFee();
            assertEquals(100L, fee);
        }

        @Test
        void baseFeeAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":200}");
            });
            server.start();

            long fee = client.network().baseFeeAsync().get();
            assertEquals(200L, fee);
        }
    }

    // ── Contract Query ───────────────────────────────────────────────

    @Nested
    class ContractQueryTests {

        @Test
        void queryGet() throws Exception {
            server.createContext("/query", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                assertTrue(query.contains("contract=token"));
                assertTrue(query.contains("method=totalSupply"));
                respond(exchange, 200, "{\"result\":{\"total\":\"1000000\"}}");
            });
            server.start();

            QueryResult result = client.contract("token").query("totalSupply").get();
            assertNotNull(result.result());
            assertEquals("1000000", result.result().get("total"));
        }

        @Test
        void queryWithArgs() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"balance\":\"500\"}}");
            });
            server.start();

            QueryResult result = client.contract("token")
                    .query("balanceOf", Map.of("owner", "dili1alice"))
                    .get();
            assertEquals("500", result.result().get("balance"));
        }

        @Test
        void queryAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"value\":\"42\"}}");
            });
            server.start();

            QueryResult result = client.contract("token").query("totalSupply").getAsync().get();
            assertEquals("42", result.result().get("value"));
        }
    }

    // ── Contract Call ────────────────────────────────────────────────

    @Nested
    class ContractCallTests {

        @Test
        void callSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"contract\":\"token\""));
                assertTrue(bodyStr.contains("\"method\":\"transfer\""));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xtx1\",\"status\":\"success\",\"blockHeight\":10,\"gasUsed\":200,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.contract("token")
                    .call("transfer", Map.of("to", "dili1bob", "amount", 100))
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xtx1"), receipt.txHash());
        }

        @Test
        void callSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xtx2\",\"status\":\"success\",\"blockHeight\":11,\"gasUsed\":150,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.contract("token")
                    .call("transfer", Map.of("to", "dili1bob"))
                    .sendAsync(MOCK_SIGNER)
                    .get();
            assertEquals("success", receipt.status());
        }

        @Test
        void callWithPaymaster() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"paymaster\":\"dili1sponsor\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xtx3\",\"status\":\"success\",\"blockHeight\":12,\"gasUsed\":100,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.contract("token")
                    .call("transfer", Map.of("to", "dili1bob"))
                    .withPaymaster("dili1sponsor")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void callNoArgs() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xtx4\",\"status\":\"success\",\"blockHeight\":13,\"gasUsed\":50,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.contract("token")
                    .call("pause")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }
    }

    // ── Contract ABI ─────────────────────────────────────────────────

    @Nested
    class AbiTests {

        @Test
        void getAbi() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"contract\":\"token\",\"methods\":[{\"name\":\"transfer\"}]}}");
            });
            server.start();

            ContractAbi abi = client.contract("token").abi().get();
            assertEquals("token", abi.contract());
            assertFalse(abi.methods().isEmpty());
        }

        @Test
        void getAbiAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"contract\":\"token\",\"methods\":[]}}");
            });
            server.start();

            ContractAbi abi = client.contract("token").abi().getAsync().get();
            assertEquals("token", abi.contract());
        }
    }

    // ── Name Service ─────────────────────────────────────────────────

    @Nested
    class NameServiceTests {

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
    }

    // ── Deploy ───────────────────────────────────────────────────────

    @Nested
    class DeployTests {

        @Test
        void deploySend() throws Exception {
            server.createContext("/deploy", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"name\":\"my_token\""));
                assertTrue(bodyStr.contains("\"bytecode\":\"0xdead\""));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xdeploy1\",\"status\":\"success\",\"blockHeight\":20,\"gasUsed\":1000,\"result\":null}");
            });
            server.start();

            DeployPayload payload = new DeployPayload(
                    "my_token", "0xdead", Address.of("dili1alice"),
                    "mldsa65", PublicKey.of("pk"), "oldsig", 1L, "dilithia-1", 1);

            Receipt receipt = client.deploy(payload).send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xdeploy1"), receipt.txHash());
        }

        @Test
        void deploySendAsync() throws Exception {
            server.createContext("/deploy", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xdeploy2\",\"status\":\"success\",\"blockHeight\":21,\"gasUsed\":900,\"result\":null}");
            });
            server.start();

            DeployPayload payload = new DeployPayload(
                    "my_token", "0xbeef", Address.of("dili1bob"),
                    "mldsa65", PublicKey.of("pk2"), "sig2", 2L, "dilithia-1", 1);

            Receipt receipt = client.deploy(payload).sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Upgrade ──────────────────────────────────────────────────────

    @Nested
    class UpgradeTests {

        @Test
        void upgradeSend() throws Exception {
            server.createContext("/upgrade", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"name\":\"my_token\""));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xupgrade1\",\"status\":\"success\",\"blockHeight\":30,\"gasUsed\":800,\"result\":null}");
            });
            server.start();

            DeployPayload payload = new DeployPayload(
                    "my_token", "0xnewcode", Address.of("dili1alice"),
                    "mldsa65", PublicKey.of("pk"), "sig", 2L, "dilithia-1", 2);

            Receipt receipt = client.upgrade(payload).send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void upgradeSendAsync() throws Exception {
            server.createContext("/upgrade", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xupgrade2\",\"status\":\"success\",\"blockHeight\":31,\"gasUsed\":700,\"result\":null}");
            });
            server.start();

            DeployPayload payload = new DeployPayload(
                    "my_token", "0xnewcode2", Address.of("dili1bob"),
                    "mldsa65", PublicKey.of("pk2"), "sig2", 3L, "dilithia-1", 2);

            Receipt receipt = client.upgrade(payload).sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Shielded ─────────────────────────────────────────────────────

    @Nested
    class ShieldedTests {

        @Test
        void commitmentRoot() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"root\":\"0xcommitroot\"}}");
            });
            server.start();

            QueryResult result = client.shielded().commitmentRoot().get();
            assertNotNull(result.result());
            assertEquals("0xcommitroot", result.result().get("root"));
        }

        @Test
        void commitmentRootAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"root\":\"0xroot2\"}}");
            });
            server.start();

            QueryResult result = client.shielded().commitmentRoot().getAsync().get();
            assertEquals("0xroot2", result.result().get("root"));
        }

        @Test
        void isNullifierSpent() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"nullifier\":\"null123\""));
                respond(exchange, 200, "{\"result\":{\"spent\":true}}");
            });
            server.start();

            QueryResult result = client.shielded().isNullifierSpent("null123").get();
            assertNotNull(result.result());
        }

        @Test
        void isNullifierSpentAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"spent\":false}}");
            });
            server.start();

            QueryResult result = client.shielded().isNullifierSpent("null456").getAsync().get();
            assertNotNull(result.result());
        }

        @Test
        void depositSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"deposit\""));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xdep1\",\"status\":\"success\",\"blockHeight\":40,\"gasUsed\":300,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.shielded()
                    .deposit("commit1", 1000L, "proof_hex")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void depositSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xdep2\",\"status\":\"success\",\"blockHeight\":41,\"gasUsed\":250,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.shielded()
                    .deposit("commit2", 2000L, "proof2")
                    .sendAsync(MOCK_SIGNER)
                    .get();
            assertEquals("success", receipt.status());
        }

        @Test
        void withdrawSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"withdraw\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xwith1\",\"status\":\"success\",\"blockHeight\":50,\"gasUsed\":400,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.shielded()
                    .withdraw("nullifier1", 500L, "dili1bob", "proof3", "root1")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void withdrawSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xwith2\",\"status\":\"success\",\"blockHeight\":51,\"gasUsed\":350,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.shielded()
                    .withdraw("nullifier2", 600L, "dili1carol", "proof4", "root2")
                    .sendAsync(MOCK_SIGNER)
                    .get();
            assertEquals("success", receipt.status());
        }
    }

    // ── RPC ──────────────────────────────────────────────────────────

    @Nested
    class RpcTests {

        @Test
        void genericRpc() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"custom\":\"data\"}}");
            });
            server.start();

            RpcResponse resp = client.rpc("custom_method", Map.of("key", "value")).get();
            assertEquals(1, resp.id());
            assertNotNull(resp.result());
        }

        @Test
        void genericRpcAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"async_custom\":true}}");
            });
            server.start();

            RpcResponse resp = client.rpc("async_method", null).getAsync().get();
            assertNotNull(resp.result());
        }

        @Test
        void rpcWithNullParams() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"scalar\"}");
            });
            server.start();

            RpcResponse resp = client.rpc("method", null).get();
            assertNotNull(resp.result());
        }
    }

    // ── Address Summary ──────────────────────────────────────────────

    @Nested
    class AddressSummaryTests {

        @Test
        void getAddressSummary() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"address\":\"dili1alice\",\"balance\":5000,\"nonce\":10}}");
            });
            server.start();

            AddressSummary summary = client.addressSummary("dili1alice").get();
            assertEquals(Address.of("dili1alice"), summary.address());
            assertEquals(5000L, summary.balance());
            assertEquals(10L, summary.nonce());
        }

        @Test
        void getAddressSummaryWithAddressType() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"address\":\"dili1bob\",\"balance\":3000,\"nonce\":5}}");
            });
            server.start();

            AddressSummary summary = client.addressSummary(Address.of("dili1bob")).get();
            assertEquals(3000L, summary.balance());
        }

        @Test
        void getAddressSummaryAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"address\":\"dili1alice\",\"balance\":1000,\"nonce\":1}}");
            });
            server.start();

            AddressSummary summary = client.addressSummary("dili1alice").getAsync().get();
            assertEquals(1000L, summary.balance());
        }
    }

    // ── Error handling ───────────────────────────────────────────────

    @Nested
    class ErrorTests {

        @Test
        void httpException404() throws Exception {
            server.createContext("/rpc/balance/dili1missing", exchange -> {
                respond(exchange, 404, "not found");
            });
            server.start();

            HttpException ex = assertThrows(HttpException.class,
                    () -> client.balance("dili1missing").get());
            assertEquals(404, ex.statusCode());
        }

        @Test
        void httpException500() throws Exception {
            server.createContext("/rpc/balance/dili1error", exchange -> {
                respond(exchange, 500, "internal server error");
            });
            server.start();

            HttpException ex = assertThrows(HttpException.class,
                    () -> client.balance("dili1error").get());
            assertEquals(500, ex.statusCode());
        }

        @Test
        void rpcErrorThrowsRpcException() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}");
            });
            server.start();

            RpcException ex = assertThrows(RpcException.class,
                    () -> client.network().head());
            assertEquals(-32601, ex.code());
            assertEquals("Method not found", ex.rpcMessage());
        }

        @Test
        void asyncErrorPropagated() throws Exception {
            server.createContext("/rpc/balance/dili1fail", exchange -> {
                respond(exchange, 502, "bad gateway");
            });
            server.start();

            CompletableFuture<Balance> future = client.balance("dili1fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(RuntimeException.class, ex.getCause());
        }
    }
}
