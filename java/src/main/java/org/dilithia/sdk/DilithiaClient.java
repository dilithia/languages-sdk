package org.dilithia.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DilithiaClient {
    public static final String SDK_VERSION = "0.3.0";
    public static final String RPC_LINE_VERSION = "0.3.0";

    private final String rpcUrl;
    private final String baseUrl;
    private final String indexerUrl;
    private final String oracleUrl;
    private final String wsUrl;
    private final String jwt;
    private final Map<String, String> headers;
    private final int timeoutMs;

    public DilithiaClient(String rpcUrl) {
        this(rpcUrl, 10_000);
    }

    public DilithiaClient(String rpcUrl, int timeoutMs) {
        this(rpcUrl, timeoutMs, null, null, null);
    }

    public DilithiaClient(
            String rpcUrl,
            int timeoutMs,
            String chainBaseUrl,
            String indexerUrl,
            String oracleUrl
    ) {
        this(rpcUrl, timeoutMs, chainBaseUrl, indexerUrl, oracleUrl, null);
    }

    public DilithiaClient(
            String rpcUrl,
            int timeoutMs,
            String chainBaseUrl,
            String indexerUrl,
            String oracleUrl,
            String wsUrl
    ) {
        this(rpcUrl, timeoutMs, chainBaseUrl, indexerUrl, oracleUrl, wsUrl, null, Map.of());
    }

    public DilithiaClient(
            String rpcUrl,
            int timeoutMs,
            String chainBaseUrl,
            String indexerUrl,
            String oracleUrl,
            String wsUrl,
            String jwt,
            Map<String, String> headers
    ) {
        if (rpcUrl == null || rpcUrl.isBlank()) {
            throw new IllegalArgumentException("RPC URL is required");
        }
        this.rpcUrl = rpcUrl.replaceAll("/+$", "");
        this.baseUrl = (chainBaseUrl == null || chainBaseUrl.isBlank()
                ? this.rpcUrl.replaceFirst("/rpc$", "")
                : chainBaseUrl).replaceAll("/+$", "");
        this.indexerUrl = indexerUrl == null || indexerUrl.isBlank() ? null : indexerUrl.replaceAll("/+$", "");
        this.oracleUrl = oracleUrl == null || oracleUrl.isBlank() ? null : oracleUrl.replaceAll("/+$", "");
        this.wsUrl = wsUrl == null || wsUrl.isBlank() ? deriveWsUrl(this.baseUrl) : wsUrl.replaceAll("/+$", "");
        this.jwt = jwt;
        this.headers = headers == null ? Map.of() : new LinkedHashMap<>(headers);
        this.timeoutMs = timeoutMs;
    }

    public String rpcUrl() {
        return rpcUrl;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String indexerUrl() {
        return indexerUrl;
    }

    public String oracleUrl() {
        return oracleUrl;
    }

    public String wsUrl() {
        return wsUrl;
    }

    public Map<String, String> buildAuthHeaders(Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (jwt != null && !jwt.isBlank()) {
            merged.put("Authorization", "Bearer " + jwt);
        }
        merged.putAll(headers);
        if (extra != null) {
            merged.putAll(extra);
        }
        return merged;
    }

    public Map<String, Object> wsConnectionInfo() {
        return Map.of(
                "url", wsUrl,
                "headers", buildAuthHeaders(Map.of())
        );
    }

    public String balancePath(String address) {
        return rpcUrl + "/balance/" + address;
    }

    public String noncePath(String address) {
        return rpcUrl + "/nonce/" + address;
    }

    public String receiptPath(String txHash) {
        return rpcUrl + "/receipt/" + txHash;
    }

    public Map<String, Object> addressSummaryBody(String address) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", "qsc_addressSummary");
        body.put("params", Map.of("address", address));
        return body;
    }

    public Map<String, Object> gasEstimateBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", "qsc_gasEstimate");
        body.put("params", Map.of());
        return body;
    }

    public Map<String, Object> baseFeeBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", "qsc_baseFee");
        body.put("params", Map.of());
        return body;
    }

    public String resolveNamePath(String name) {
        return baseUrl + "/names/resolve/" + name;
    }

    public String reverseResolveNamePath(String address) {
        return baseUrl + "/names/reverse/" + address;
    }

    public String queryContractPath(String contract, String method, String argsJson) {
        return baseUrl
                + "/query?contract=" + urlEncode(contract)
                + "&method=" + urlEncode(method)
                + "&args=" + urlEncode(argsJson);
    }

    public Map<String, Object> buildJsonRpcRequest(String method, Map<String, Object> params, int id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id == 0 ? 1 : id);
        body.put("method", method);
        body.put("params", params == null ? Map.of() : params);
        return body;
    }

    public Map<String, Object> buildWsRequest(String method, Map<String, Object> params, int id) {
        return buildJsonRpcRequest(method, params, id);
    }

    public Map<String, Object> buildContractCall(
            String contract,
            String method,
            Map<String, Object> args,
            String paymaster
    ) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("contract", contract);
        call.put("method", method);
        call.put("args", args == null ? Map.of() : args);
        return paymaster == null || paymaster.isBlank() ? call : withPaymaster(call, paymaster);
    }

    public Map<String, Object> withPaymaster(Map<String, Object> call, String paymaster) {
        Map<String, Object> merged = new LinkedHashMap<>(call);
        merged.put("paymaster", paymaster);
        return merged;
    }

    public Map<String, Object> buildForwarderCall(String forwarderContract, Map<String, Object> args, String paymaster) {
        return buildContractCall(forwarderContract, "forward", args, paymaster);
    }

    public Map<String, Object> sendSignedCallBody(
            Map<String, Object> call,
            DilithiaSigner signer
    ) {
        Map<String, Object> merged = new LinkedHashMap<>(call);
        merged.putAll(signer.signCanonicalPayload(call));
        return merged;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String deriveWsUrl(String baseUrl) {
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length());
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length());
        }
        return null;
    }
}
