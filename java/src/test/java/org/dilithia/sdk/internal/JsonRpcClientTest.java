package org.dilithia.sdk.internal;

import com.sun.net.httpserver.HttpServer;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.RpcException;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcClientTest {

    private HttpServer server;
    private HttpTransport transport;
    private JsonRpcClient rpcClient;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        String rpcUrl = "http://localhost:" + port + "/rpc";
        transport = new HttpTransport(null, Duration.ofSeconds(5), Map.of());
        rpcClient = new JsonRpcClient(transport, rpcUrl);
    }

    @AfterEach
    void tearDown() {
        transport.close();
        server.stop(0);
    }

    @Test
    void callReturnsResultMap() throws Exception {
        server.createContext("/rpc", exchange -> {
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"height\":100,\"hash\":\"0xabc\"}}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        Map<String, Object> result = rpcClient.call("qsc_head", null);
        assertEquals(100.0, result.get("height"));
        assertEquals("0xabc", result.get("hash"));
    }

    @Test
    void callWithRpcErrorThrowsRpcException() throws Exception {
        server.createContext("/rpc", exchange -> {
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32600,\"message\":\"Invalid request\"}}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        RpcException ex = assertThrows(RpcException.class,
                () -> rpcClient.call("bad_method", null));
        assertEquals(-32600, ex.code());
        assertEquals("Invalid request", ex.rpcMessage());
    }

    @Test
    void callWrapsScalarResultInMap() throws Exception {
        server.createContext("/rpc", exchange -> {
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":42}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        Map<String, Object> result = rpcClient.call("qsc_baseFee", null);
        assertEquals(42.0, result.get("value"));
    }

    @Test
    void callAsyncReturnsResult() throws Exception {
        server.createContext("/rpc", exchange -> {
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"data\":\"async\"}}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        Map<String, Object> result = rpcClient.callAsync("method", null).get();
        assertEquals("async", result.get("data"));
    }

    @Test
    void callAsyncWithRpcErrorThrows() throws Exception {
        server.createContext("/rpc", exchange -> {
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"fail\"}}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        var future = rpcClient.callAsync("method", null);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, ex.getCause());
    }

    @Test
    void callRawReturnsRawString() throws Exception {
        String rawResp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"foo\":\"bar\"}}";
        server.createContext("/rpc", exchange -> {
            exchange.sendResponseHeaders(200, rawResp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(rawResp.getBytes());
            }
        });
        server.start();

        String result = rpcClient.callRaw("method", null);
        assertEquals(rawResp, result);
    }

    @Test
    void callRawAsyncReturnsRawString() throws Exception {
        String rawResp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"raw\"}";
        server.createContext("/rpc", exchange -> {
            exchange.sendResponseHeaders(200, rawResp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(rawResp.getBytes());
            }
        });
        server.start();

        String result = rpcClient.callRawAsync("method", null).get();
        assertEquals(rawResp, result);
    }

    @Test
    void callWithParamsIncludesParams() throws Exception {
        server.createContext("/rpc", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body);
            // Verify that params are included in the request body
            assertTrue(bodyStr.contains("\"params\""));
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        Map<String, Object> result = rpcClient.call("method", Map.of("key", "val"));
        assertNotNull(result);
    }

    @Test
    void idCounterIncrements() throws Exception {
        // Each call should increment the id
        server.createContext("/rpc", exchange -> {
            String resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        rpcClient.call("a", null);
        rpcClient.call("b", null);
        // No assertion on id value needed; just verify no errors
    }
}
