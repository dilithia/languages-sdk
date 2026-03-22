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

class MultisigRequestTest {

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
        void multisigWalletRecord() {
            var wallet = new MultisigWallet("w1",
                    List.of("dili1alice", "dili1bob", "dili1carol"), 2);
            assertEquals("w1", wallet.walletId());
            assertEquals(3, wallet.signers().size());
            assertEquals("dili1alice", wallet.signers().get(0));
            assertEquals(2, wallet.threshold());
        }

        @Test
        void multisigTxRecord() {
            var tx = new MultisigTx("tx1", "token", "transfer",
                    Map.of("to", "dili1bob", "amount", 100),
                    List.of("dili1alice"));
            assertEquals("tx1", tx.txId());
            assertEquals("token", tx.contract());
            assertEquals("transfer", tx.method());
            assertEquals("dili1bob", tx.args().get("to"));
            assertEquals(1, tx.approvals().size());
            assertEquals("dili1alice", tx.approvals().get(0));
        }

        @Test
        void multisigTxEmptyApprovals() {
            var tx = new MultisigTx("tx2", "token", "pause", Map.of(), List.of());
            assertTrue(tx.approvals().isEmpty());
            assertTrue(tx.args().isEmpty());
        }
    }

    // ── Mutation: createMultisig ─────────────────────────────────────

    @Nested
    class CreateMultisigTests {

        @Test
        void createMultisigSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"create\""));
                assertTrue(bodyStr.contains("\"contract\":\"multisig\""));
                assertTrue(bodyStr.contains("\"wallet_id\":\"w1\""));
                assertTrue(bodyStr.contains("\"threshold\":2"));
                assertTrue(bodyStr.contains("\"sig\":\"sig_hex\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xms1\",\"status\":\"success\",\"blockHeight\":10,\"gasUsed\":800,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .createMultisig("w1", List.of("dili1alice", "dili1bob", "dili1carol"), 2)
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
            assertEquals(TxHash.of("0xms1"), receipt.txHash());
        }

        @Test
        void createMultisigSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xms2\",\"status\":\"success\",\"blockHeight\":11,\"gasUsed\":750,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .createMultisig("w2", List.of("dili1a", "dili1b"), 1)
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }

        @Test
        void createMultisigAsyncError() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<Receipt> future = client.multisig()
                    .createMultisig("w_fail", List.of("s1"), 1)
                    .sendAsync(MOCK_SIGNER);
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Mutation: proposeTx ─────────────────────────────────────────

    @Nested
    class ProposeTxTests {

        @Test
        void proposeTxSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"propose_tx\""));
                assertTrue(bodyStr.contains("\"wallet_id\":\"w1\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xprop1\",\"status\":\"success\",\"blockHeight\":20,\"gasUsed\":600,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .proposeTx("w1", "token", "transfer", Map.of("to", "dili1bob", "amount", 100))
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void proposeTxSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xprop2\",\"status\":\"success\",\"blockHeight\":21,\"gasUsed\":550,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .proposeTx("w1", "token", "pause", Map.of())
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: approveMultisigTx ─────────────────────────────────

    @Nested
    class ApproveTests {

        @Test
        void approveMultisigTxSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"approve\""));
                assertTrue(bodyStr.contains("\"wallet_id\":\"w1\""));
                assertTrue(bodyStr.contains("\"tx_id\":\"tx1\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xappr1\",\"status\":\"success\",\"blockHeight\":30,\"gasUsed\":400,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .approveMultisigTx("w1", "tx1")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void approveMultisigTxSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xappr2\",\"status\":\"success\",\"blockHeight\":31,\"gasUsed\":350,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .approveMultisigTx("w1", "tx2")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: executeMultisigTx ─────────────────────────────────

    @Nested
    class ExecuteTests {

        @Test
        void executeMultisigTxSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"execute\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xexec1\",\"status\":\"success\",\"blockHeight\":40,\"gasUsed\":900,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .executeMultisigTx("w1", "tx1")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void executeMultisigTxSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xexec2\",\"status\":\"success\",\"blockHeight\":41,\"gasUsed\":850,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .executeMultisigTx("w1", "tx2")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: revokeMultisigApproval ─────────────────────────────

    @Nested
    class RevokeApprovalTests {

        @Test
        void revokeMultisigApprovalSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"revoke\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xrev1\",\"status\":\"success\",\"blockHeight\":50,\"gasUsed\":300,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .revokeMultisigApproval("w1", "tx1")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void revokeMultisigApprovalSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xrev2\",\"status\":\"success\",\"blockHeight\":51,\"gasUsed\":280,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .revokeMultisigApproval("w1", "tx2")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: addMultisigSigner ──────────────────────────────────

    @Nested
    class AddSignerTests {

        @Test
        void addMultisigSignerSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"add_signer\""));
                assertTrue(bodyStr.contains("\"signer\":\"dili1dave\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xadd1\",\"status\":\"success\",\"blockHeight\":60,\"gasUsed\":350,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .addMultisigSigner("w1", "dili1dave")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void addMultisigSignerSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xadd2\",\"status\":\"success\",\"blockHeight\":61,\"gasUsed\":320,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .addMultisigSigner("w1", "dili1eve")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Mutation: removeMultisigSigner ───────────────────────────────

    @Nested
    class RemoveSignerTests {

        @Test
        void removeMultisigSignerSend() throws Exception {
            server.createContext("/rpc", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"remove_signer\""));
                assertTrue(bodyStr.contains("\"signer\":\"dili1dave\""));
                respond(exchange, 200,
                        "{\"txHash\":\"0xrm1\",\"status\":\"success\",\"blockHeight\":70,\"gasUsed\":300,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .removeMultisigSigner("w1", "dili1dave")
                    .send(MOCK_SIGNER);
            assertEquals("success", receipt.status());
        }

        @Test
        void removeMultisigSignerSendAsync() throws Exception {
            server.createContext("/rpc", exchange -> {
                respond(exchange, 200,
                        "{\"txHash\":\"0xrm2\",\"status\":\"success\",\"blockHeight\":71,\"gasUsed\":280,\"result\":null}");
            });
            server.start();

            Receipt receipt = client.multisig()
                    .removeMultisigSigner("w1", "dili1eve")
                    .sendAsync(MOCK_SIGNER).get();
            assertEquals("success", receipt.status());
        }
    }

    // ── Query: getMultisigWallet ────────────────────────────────────

    @Nested
    class GetWalletTests {

        @Test
        void getMultisigWallet() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"wallet\""));
                assertTrue(bodyStr.contains("\"wallet_id\":\"w1\""));
                respond(exchange, 200,
                        "{\"result\":{\"wallet_id\":\"w1\",\"signers\":[\"dili1alice\",\"dili1bob\"],\"threshold\":2}}");
            });
            server.start();

            MultisigWallet wallet = client.multisig().getMultisigWallet("w1").get();
            assertNotNull(wallet);
            assertEquals("w1", wallet.walletId());
            assertEquals(2, wallet.signers().size());
            assertEquals("dili1alice", wallet.signers().get(0));
            assertEquals(2, wallet.threshold());
        }

        @Test
        void getMultisigWalletNotFound() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":null}");
            });
            server.start();

            MultisigWallet wallet = client.multisig().getMultisigWallet("missing").get();
            assertNull(wallet);
        }

        @Test
        void getMultisigWalletAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"wallet_id\":\"w2\",\"signers\":[\"s1\"],\"threshold\":1}}");
            });
            server.start();

            MultisigWallet wallet = client.multisig().getMultisigWallet("w2").getAsync().get();
            assertNotNull(wallet);
            assertEquals("w2", wallet.walletId());
        }

        @Test
        void getMultisigWalletAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<MultisigWallet> future =
                    client.multisig().getMultisigWallet("fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Query: getMultisigTx ────────────────────────────────────────

    @Nested
    class GetTxTests {

        @Test
        void getMultisigTx() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"pending_tx\""));
                respond(exchange, 200,
                        "{\"result\":{\"tx_id\":\"tx1\",\"contract\":\"token\",\"method\":\"transfer\",\"args\":{\"to\":\"dili1bob\"},\"approvals\":[\"dili1alice\"]}}");
            });
            server.start();

            MultisigTx tx = client.multisig().getMultisigTx("w1", "tx1").get();
            assertNotNull(tx);
            assertEquals("tx1", tx.txId());
            assertEquals("token", tx.contract());
            assertEquals("transfer", tx.method());
            assertEquals("dili1bob", tx.args().get("to"));
            assertEquals(1, tx.approvals().size());
        }

        @Test
        void getMultisigTxNotFound() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":null}");
            });
            server.start();

            MultisigTx tx = client.multisig().getMultisigTx("w1", "missing").get();
            assertNull(tx);
        }

        @Test
        void getMultisigTxAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"tx_id\":\"tx2\",\"contract\":\"amm\",\"method\":\"swap\",\"args\":{},\"approvals\":[]}}");
            });
            server.start();

            MultisigTx tx = client.multisig().getMultisigTx("w1", "tx2").getAsync().get();
            assertNotNull(tx);
            assertEquals("tx2", tx.txId());
        }

        @Test
        void getMultisigTxAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<MultisigTx> future =
                    client.multisig().getMultisigTx("w1", "fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }

    // ── Query: listMultisigPendingTxs ───────────────────────────────

    @Nested
    class ListPendingTxsTests {

        @Test
        void listMultisigPendingTxs() throws Exception {
            server.createContext("/query", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String bodyStr = new String(body);
                assertTrue(bodyStr.contains("\"method\":\"pending_txs\""));
                respond(exchange, 200,
                        "{\"result\":{\"pending_txs\":[{\"txId\":\"tx1\",\"contract\":\"token\",\"method\":\"transfer\",\"args\":{},\"approvals\":[\"dili1alice\"]}]}}");
            });
            server.start();

            List<MultisigTx> txs = client.multisig().listMultisigPendingTxs("w1").get();
            assertEquals(1, txs.size());
            assertEquals("tx1", txs.get(0).txId());
        }

        @Test
        void listMultisigPendingTxsEmpty() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200, "{\"result\":{\"pending_txs\":null}}");
            });
            server.start();

            List<MultisigTx> txs = client.multisig().listMultisigPendingTxs("w_empty").get();
            assertTrue(txs.isEmpty());
        }

        @Test
        void listMultisigPendingTxsAsync() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 200,
                        "{\"result\":{\"pending_txs\":[{\"txId\":\"tx2\",\"contract\":\"amm\",\"method\":\"swap\",\"args\":{},\"approvals\":[]}]}}");
            });
            server.start();

            List<MultisigTx> txs = client.multisig().listMultisigPendingTxs("w1").getAsync().get();
            assertEquals(1, txs.size());
        }

        @Test
        void listMultisigPendingTxsAsyncError() throws Exception {
            server.createContext("/query", exchange -> {
                respond(exchange, 500, "error");
            });
            server.start();

            CompletableFuture<List<MultisigTx>> future =
                    client.multisig().listMultisigPendingTxs("fail").getAsync();
            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertNotNull(ex.getCause());
        }
    }
}
