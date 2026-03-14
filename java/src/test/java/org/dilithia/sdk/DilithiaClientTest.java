package org.dilithia.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DilithiaClientTest {
    @Test
    void versionsMatchRpcLine() {
        assertEquals("0.3.0", DilithiaClient.SDK_VERSION);
        assertEquals("0.3.0", DilithiaClient.RPC_LINE_VERSION);
    }

    @Test
    void trimsRpcUrlAndBuildsPaths() {
        DilithiaClient client = new DilithiaClient("http://localhost:8000/rpc/");
        assertEquals("http://localhost:8000/rpc", client.rpcUrl());
        assertEquals("http://localhost:8000", client.baseUrl());
        assertEquals("ws://localhost:8000", client.wsUrl());
        assertEquals("http://localhost:8000/rpc/balance/alice", client.balancePath("alice"));
        assertEquals("http://localhost:8000/rpc/nonce/alice", client.noncePath("alice"));
    }

    @Test
    void acceptsConfigurableUrlsAndBuildsChainPaths() {
        DilithiaClient client = new DilithiaClient(
                "http://localhost:8000/rpc/",
                5_000,
                "http://localhost:8000/chain/",
                "http://localhost:8011/api/",
                "http://localhost:8020/",
                null,
                "secret-token",
                Map.of("x-network", "devnet")
        );
        assertEquals("http://localhost:8000/chain", client.baseUrl());
        assertEquals("http://localhost:8011/api", client.indexerUrl());
        assertEquals("http://localhost:8020", client.oracleUrl());
        assertEquals("ws://localhost:8000/chain", client.wsUrl());
        assertEquals(
                Map.of(
                        "Authorization", "Bearer secret-token",
                        "x-network", "devnet",
                        "accept", "application/json"
                ),
                client.buildAuthHeaders(Map.of("accept", "application/json"))
        );
        assertEquals("http://localhost:8000/chain/names/resolve/alice.dili", client.resolveNamePath("alice.dili"));
        assertEquals(
                "http://localhost:8000/chain/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D",
                client.queryContractPath("wasm:amm", "get_reserves", "{}")
        );
    }

    @Test
    void mergesSignedCallPayload() {
        DilithiaClient client = new DilithiaClient("http://localhost:8000/rpc");
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("from", "alice");
        call.put("method", "transfer");

        Map<String, Object> signed = client.sendSignedCallBody(call, payload -> Map.of(
                "alg", "mldsa65",
                "sig", "deadbeef"
        ));

        assertEquals("alice", signed.get("from"));
        assertEquals("transfer", signed.get("method"));
        assertEquals("mldsa65", signed.get("alg"));
        assertEquals("deadbeef", signed.get("sig"));
    }

    @Test
    void buildsGenericRpcAndWsRequests() {
        DilithiaClient client = new DilithiaClient("http://localhost:8000/rpc");
        assertEquals(
                Map.of("jsonrpc", "2.0", "id", 1, "method", "qsc_head", "params", Map.of("full", true)),
                client.buildJsonRpcRequest("qsc_head", Map.of("full", true), 1)
        );
        assertEquals(
                Map.of("jsonrpc", "2.0", "id", 2, "method", "subscribe_heads", "params", Map.of("full", true)),
                client.buildWsRequest("subscribe_heads", Map.of("full", true), 2)
        );
        assertEquals(
                Map.of("url", "ws://localhost:8000", "headers", Map.of()),
                client.wsConnectionInfo()
        );
    }

    @Test
    void nativeCryptoLoaderIsOptional() {
        assertEquals(true, NativeCryptoAdapters.load().isEmpty());
        assertNull(new DilithiaClient("http://localhost:8000/rpc").indexerUrl());
    }

    @Test
    void sponsorAndMessagingConnectorsShapeCalls() {
        DilithiaClient client = new DilithiaClient("http://localhost:8000/rpc");
        DilithiaGasSponsorConnector sponsor = new DilithiaGasSponsorConnector(
                client,
                "wasm:gas_sponsor",
                "gas_sponsor"
        );
        Map<String, Object> applied = sponsor.applyPaymaster(Map.of("contract", "wasm:amm", "method", "swap"));
        assertEquals("gas_sponsor", applied.get("paymaster"));

        DilithiaMessagingConnector messaging = new DilithiaMessagingConnector(
                client,
                "wasm:messaging",
                "gas_sponsor"
        );
        Map<String, Object> outbound = messaging.buildSendMessageCall("ethereum", Map.of("amount", 1));
        assertEquals("send_message", outbound.get("method"));
        assertEquals("gas_sponsor", outbound.get("paymaster"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inboundArgs = (Map<String, Object>) messaging
                .buildReceiveMessageCall("ethereum", "bridge", Map.of("tx", "0xabc"))
                .get("args");
        assertEquals("ethereum", inboundArgs.get("source_chain"));
        assertEquals("bridge", inboundArgs.get("source_contract"));
    }
}
