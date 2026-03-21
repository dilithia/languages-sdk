package org.dilithia.sdk.internal;

import com.sun.net.httpserver.HttpServer;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.HttpException;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class HttpTransportTest {

    private HttpServer server;
    private HttpTransport transport;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
        transport = new HttpTransport(null, Duration.ofSeconds(5), Map.of());
    }

    @AfterEach
    void tearDown() {
        transport.close();
        server.stop(0);
    }

    @Test
    void getSyncReturnsBody() throws Exception {
        server.createContext("/ok", exchange -> {
            String resp = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        String body = transport.get(baseUrl + "/ok");
        assertEquals("{\"status\":\"ok\"}", body);
    }

    @Test
    void getAsyncReturnsBody() throws Exception {
        server.createContext("/async-ok", exchange -> {
            String resp = "{\"async\":true}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        CompletableFuture<String> future = transport.getAsync(baseUrl + "/async-ok");
        String body = future.get();
        assertEquals("{\"async\":true}", body);
    }

    @Test
    void postSyncReturnsBody() throws Exception {
        server.createContext("/post", exchange -> {
            // Read request body
            byte[] reqBody = exchange.getRequestBody().readAllBytes();
            assertTrue(reqBody.length > 0);
            String resp = "{\"received\":true}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        String body = transport.post(baseUrl + "/post", "{\"data\":1}");
        assertEquals("{\"received\":true}", body);
    }

    @Test
    void postAsyncReturnsBody() throws Exception {
        server.createContext("/post-async", exchange -> {
            String resp = "{\"async_post\":true}";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        String body = transport.postAsync(baseUrl + "/post-async", "{\"data\":1}").get();
        assertEquals("{\"async_post\":true}", body);
    }

    @Test
    void non2xxThrowsHttpException() throws Exception {
        server.createContext("/notfound", exchange -> {
            String resp = "not found";
            exchange.sendResponseHeaders(404, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        HttpException ex = assertThrows(HttpException.class,
                () -> transport.get(baseUrl + "/notfound"));
        assertEquals(404, ex.statusCode());
        assertEquals("not found", ex.body());
    }

    @Test
    void serverErrorThrowsHttpException() throws Exception {
        server.createContext("/error", exchange -> {
            String resp = "internal error";
            exchange.sendResponseHeaders(500, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        HttpException ex = assertThrows(HttpException.class,
                () -> transport.get(baseUrl + "/error"));
        assertEquals(500, ex.statusCode());
    }

    @Test
    void asyncNon2xxThrowsViaFuture() throws Exception {
        server.createContext("/async-err", exchange -> {
            String resp = "bad request";
            exchange.sendResponseHeaders(400, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        CompletableFuture<String> future = transport.getAsync(baseUrl + "/async-err");
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertInstanceOf(HttpException.class, ex.getCause().getCause());
    }

    @Test
    void defaultHeadersAreIncluded() throws Exception {
        HttpTransport transportWithHeaders = new HttpTransport(
                null, Duration.ofSeconds(5), Map.of("X-Custom", "test-value"));
        server.createContext("/headers", exchange -> {
            String customHeader = exchange.getRequestHeaders().getFirst("X-Custom");
            String resp = customHeader != null ? customHeader : "missing";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        String body = transportWithHeaders.get(baseUrl + "/headers");
        assertEquals("test-value", body);
        transportWithHeaders.close();
    }

    @Test
    void postSetsContentTypeHeader() throws Exception {
        server.createContext("/content-type", exchange -> {
            String ct = exchange.getRequestHeaders().getFirst("Content-Type");
            String resp = ct != null ? ct : "missing";
            exchange.sendResponseHeaders(200, resp.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        server.start();

        String body = transport.post(baseUrl + "/content-type", "{}");
        assertEquals("application/json", body);
    }

    @Test
    void closeIsIdempotent() {
        assertDoesNotThrow(() -> {
            transport.close();
            transport.close();
        });
    }
}
