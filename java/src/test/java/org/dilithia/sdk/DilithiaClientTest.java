package org.dilithia.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.dilithia.sdk.connector.GasSponsorConnector;
import org.dilithia.sdk.connector.MessagingConnector;
import org.dilithia.sdk.crypto.NativeCryptoAdapters;
import org.dilithia.sdk.exception.ValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DilithiaClientTest {

    // ── Builder pattern ──────────────────────────────────────────────

    @Nested
    class BuilderTests {

        @Test
        void buildsClientWithDefaults() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            assertEquals("http://localhost:8000/rpc", client.rpcUrl());
        }

        @Test
        void buildsClientWithAllOptions() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc")
                    .timeout(Duration.ofSeconds(5))
                    .jwt("secret-token")
                    .header("x-network", "devnet")
                    .chainBaseUrl("http://localhost:8000/chain")
                    .indexerUrl("http://localhost:8011/api")
                    .oracleUrl("http://localhost:8020")
                    .wsUrl("ws://custom:9000")
                    .build();

            assertEquals("http://localhost:8000/rpc", client.rpcUrl());
            assertEquals("http://localhost:8000/chain", client.baseUrl());
            assertEquals("http://localhost:8011/api", client.indexerUrl());
            assertEquals("http://localhost:8020", client.oracleUrl());
            assertEquals("ws://custom:9000", client.wsUrl());
        }

        @Test
        void rejectsBlankRpcUrl() {
            assertThrows(ValidationException.class, () ->
                    Dilithia.client("  ").build()
            );
        }

        @Test
        void rejectsNullRpcUrl() {
            assertThrows(NullPointerException.class, () ->
                    Dilithia.client(null)
            );
        }

        @Test
        void builderTimeoutMs() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc")
                    .timeoutMs(5_000)
                    .build();
            assertNotNull(client);
        }

        @Test
        void rejectsNegativeTimeoutMs() {
            assertThrows(IllegalArgumentException.class, () ->
                    Dilithia.client("http://localhost:8000/rpc").timeoutMs(-1)
            );
        }
    }

    // ── Version constants ────────────────────────────────────────────

    @Nested
    class VersionTests {

        @Test
        void sdkVersionIsDefined() {
            assertNotNull(DilithiaClient.SDK_VERSION);
            assertFalse(DilithiaClient.SDK_VERSION.isBlank());
        }

        @Test
        void rpcLineVersionIsDefined() {
            assertNotNull(DilithiaClient.RPC_LINE_VERSION);
            assertFalse(DilithiaClient.RPC_LINE_VERSION.isBlank());
        }
    }

    // ── URL construction ─────────────────────────────────────────────

    @Nested
    class UrlTests {

        @Test
        void trimsTrailingSlashesFromRpcUrl() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc/").build();
            assertEquals("http://localhost:8000/rpc", client.rpcUrl());
        }

        @Test
        void derivesBaseUrlByStrippingRpc() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            assertEquals("http://localhost:8000", client.baseUrl());
        }

        @Test
        void derivesWsUrlFromHttpBaseUrl() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            assertEquals("ws://localhost:8000", client.wsUrl());
        }

        @Test
        void derivesWssUrlFromHttpsBaseUrl() throws ValidationException {
            DilithiaClient client = Dilithia.client("https://chain.example.com/rpc").build();
            assertEquals("wss://chain.example.com", client.wsUrl());
        }

        @Test
        void customWsUrlOverridesDerivation() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc")
                    .wsUrl("ws://custom:9000")
                    .build();
            assertEquals("ws://custom:9000", client.wsUrl());
        }

        @Test
        void indexerAndOracleDefaultToNull() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            assertNull(client.indexerUrl());
            assertNull(client.oracleUrl());
        }

        @Test
        void customChainBaseUrlOverridesDefault() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc")
                    .chainBaseUrl("http://localhost:9000/chain/")
                    .build();
            assertEquals("http://localhost:9000/chain", client.baseUrl());
        }
    }

    // ── Canonical payload construction ───────────────────────────────

    @Nested
    class PayloadTests {

        @Test
        void contractCallPayloadHasRequiredFields() {
            Map<String, Object> call = DilithiaClient.buildContractCall(
                    "token", "transfer", Map.of("to", "bob", "amount", 100), null);

            assertEquals("token", call.get("contract"));
            assertEquals("transfer", call.get("method"));
            assertNotNull(call.get("args"));
        }

        @Test
        void contractCallWithPaymaster() {
            Map<String, Object> call = DilithiaClient.buildContractCall(
                    "token", "transfer", Map.of("to", "bob"), "gas_sponsor");
            assertEquals("gas_sponsor", call.get("paymaster"));
        }

        @Test
        void contractCallWithNullArgsDefaultsToEmptyMap() {
            Map<String, Object> call = DilithiaClient.buildContractCall(
                    "token", "balance", null, null);
            assertEquals(Map.of(), call.get("args"));
        }

        @Test
        void deployCanonicalPayloadSortsKeysAlphabetically() {
            Map<String, Object> payload = DilithiaClient.buildDeployCanonicalPayload(
                    "alice", "my_token", "0xabcdef", 1, "dilithia");

            assertEquals("alice", payload.get("from"));
            assertEquals("my_token", payload.get("name"));
            assertEquals("0xabcdef", payload.get("bytecode_hash"));
            assertEquals(1L, payload.get("nonce"));
            assertEquals("dilithia", payload.get("chain_id"));

            // Keys must be in alphabetical order (TreeMap)
            var keys = payload.keySet().stream().toList();
            assertEquals("bytecode_hash", keys.get(0));
            assertEquals("chain_id", keys.get(1));
            assertEquals("from", keys.get(2));
            assertEquals("name", keys.get(3));
            assertEquals("nonce", keys.get(4));
        }
    }

    // ── Static utility methods ───────────────────────────────────────

    @Nested
    class StaticUtilityTests {

        @Test
        void withPaymasterMergesPaymasterKey() {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("contract", "token");
            call.put("method", "transfer");

            Map<String, Object> result = DilithiaClient.withPaymaster(call, "gas_sponsor");
            assertEquals("gas_sponsor", result.get("paymaster"));
            assertEquals("token", result.get("contract"));
            assertEquals("transfer", result.get("method"));
        }

        @Test
        void withPaymasterDoesNotMutateOriginal() {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("contract", "token");
            call.put("method", "transfer");

            DilithiaClient.withPaymaster(call, "gas_sponsor");
            assertNull(call.get("paymaster"));
        }

        @Test
        void buildContractCallBlankPaymasterIsIgnored() {
            Map<String, Object> call = DilithiaClient.buildContractCall(
                    "token", "transfer", Map.of(), "");
            assertNull(call.get("paymaster"));
        }
    }

    // ── Connectors ───────────────────────────────────────────────────

    @Nested
    class ConnectorTests {

        @Test
        void gasSponsorAppliesPaymaster() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            GasSponsorConnector sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", "gas_sponsor");

            ContractCallBuilder call = new ContractCallBuilder(client, "wasm:amm", "swap", Map.of("amount", 100));
            ContractCallBuilder result = sponsor.applyPaymaster(call);
            assertEquals("gas_sponsor", result.paymaster());
        }

        @Test
        void messagingSendBuildsCall() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", "gas_sponsor");

            ContractCallBuilder send = messaging.sendMessage("ethereum", Map.of("amount", 1));
            assertEquals("send_message", send.method());
            assertEquals("wasm:messaging", send.contract());
            assertEquals("gas_sponsor", send.paymaster());
        }

        @Test
        void messagingReceiveBuildsCall() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", "gas_sponsor");

            ContractCallBuilder receive = messaging.receiveMessage("ethereum", "bridge", Map.of("tx", "0xabc"));
            assertEquals("receive_message", receive.method());
            Map<String, Object> args = receive.args();
            assertEquals("ethereum", args.get("source_chain"));
            assertEquals("bridge", args.get("source_contract"));
        }

        @Test
        void messagingWithoutPaymaster() throws ValidationException {
            DilithiaClient client = Dilithia.client("http://localhost:8000/rpc").build();
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", null);

            ContractCallBuilder send = messaging.sendMessage("ethereum", Map.of("amount", 1));
            assertNull(send.paymaster());
        }
    }

    // ── Crypto loader ────────────────────────────────────────────────

    @Nested
    class CryptoLoaderTests {

        @Test
        void nativeCryptoLoaderIsOptionalWithoutNativeLib() {
            assertTrue(NativeCryptoAdapters.load().isEmpty());
        }
    }

    // ── Address ──────────────────────────────────────────────────────

    @Nested
    class AddressTests {

        @Test
        void ofCreatesAddress() {
            var addr = org.dilithia.sdk.model.Address.of("dili1alice");
            assertEquals("dili1alice", addr.value());
        }

        @Test
        void toStringReturnsValue() {
            var addr = org.dilithia.sdk.model.Address.of("dili1alice");
            assertEquals("dili1alice", addr.toString());
        }

        @Test
        void equalityByValue() {
            var a = org.dilithia.sdk.model.Address.of("dili1alice");
            var b = org.dilithia.sdk.model.Address.of("dili1alice");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void inequalityForDifferentValues() {
            var a = org.dilithia.sdk.model.Address.of("dili1alice");
            var b = org.dilithia.sdk.model.Address.of("dili1bob");
            assertNotEquals(a, b);
        }

        @Test
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    org.dilithia.sdk.model.Address.of("  "));
        }

        @Test
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    org.dilithia.sdk.model.Address.of(null));
        }
    }

    // ── TxHash ───────────────────────────────────────────────────────

    @Nested
    class TxHashTests {

        @Test
        void ofCreatesTxHash() {
            var hash = org.dilithia.sdk.model.TxHash.of("hash123");
            assertEquals("hash123", hash.value());
        }

        @Test
        void toStringReturnsValue() {
            var hash = org.dilithia.sdk.model.TxHash.of("hash123");
            assertEquals("hash123", hash.toString());
        }

        @Test
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    org.dilithia.sdk.model.TxHash.of(""));
        }

        @Test
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                    org.dilithia.sdk.model.TxHash.of(null));
        }
    }

    // ── PublicKey / SecretKey ─────────────────────────────────────────

    @Nested
    class KeyTests {

        @Test
        void publicKeyCreation() {
            var pk = org.dilithia.sdk.model.PublicKey.of("abcdef01");
            assertEquals("abcdef01", pk.hex());
            assertEquals("abcdef01", pk.toString());
        }

        @Test
        void publicKeyRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    org.dilithia.sdk.model.PublicKey.of("  "));
        }

        @Test
        void secretKeyCreation() {
            var sk = org.dilithia.sdk.model.SecretKey.of("deadbeef");
            assertEquals("deadbeef", sk.hex());
        }

        @Test
        void secretKeyToStringIsRedacted() {
            var sk = org.dilithia.sdk.model.SecretKey.of("deadbeef");
            assertEquals("SecretKey[REDACTED]", sk.toString());
            assertFalse(sk.toString().contains("deadbeef"));
        }

        @Test
        void secretKeyRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    org.dilithia.sdk.model.SecretKey.of(""));
        }
    }

    // ── TokenAmount ──────────────────────────────────────────────────

    @Nested
    class TokenAmountTests {

        @Test
        void diliFromString() {
            var amt = org.dilithia.sdk.model.TokenAmount.dili("100.5");
            assertEquals(new java.math.BigDecimal("100.5"), amt.value());
            assertEquals(18, amt.decimals());
        }

        @Test
        void diliFromLong() {
            var amt = org.dilithia.sdk.model.TokenAmount.dili(100);
            assertEquals(new java.math.BigDecimal("100"), amt.value());
            assertEquals(18, amt.decimals());
        }

        @Test
        void fromRawBigInteger() {
            var raw = java.math.BigInteger.valueOf(5_000_000_000_000_000_000L);
            var amt = org.dilithia.sdk.model.TokenAmount.fromRaw(raw, 18);
            assertEquals(18, amt.decimals());
            assertEquals(raw, amt.toRaw());
        }

        @Test
        void toRawRoundTrips() {
            var amt = org.dilithia.sdk.model.TokenAmount.dili("1.0");
            var expected = new java.math.BigInteger("1000000000000000000");
            assertEquals(expected, amt.toRaw());
        }

        @Test
        void formattedStripsTrailingZeros() {
            var amt = org.dilithia.sdk.model.TokenAmount.dili("100.500");
            assertEquals("100.5", amt.formatted());
        }

        @Test
        void diliDecimalsConstant() {
            assertEquals(18, org.dilithia.sdk.model.TokenAmount.DILI_DECIMALS);
        }

        @Test
        void rejectsNullValue() {
            assertThrows(IllegalArgumentException.class, () ->
                    new org.dilithia.sdk.model.TokenAmount(null, 18));
        }

        @Test
        void rejectsNegativeDecimals() {
            assertThrows(IllegalArgumentException.class, () ->
                    new org.dilithia.sdk.model.TokenAmount(java.math.BigDecimal.ONE, -1));
        }
    }

    // ── Balance ──────────────────────────────────────────────────────

    @Nested
    class BalanceTests {

        @Test
        void constructionAndAccessors() {
            var addr = org.dilithia.sdk.model.Address.of("dili1alice");
            var bal = new org.dilithia.sdk.model.Balance(addr, 2_000_000_000_000_000_000L);
            assertEquals(addr, bal.address());
            assertEquals(2_000_000_000_000_000_000L, bal.balance());
        }

        @Test
        void asDiliReturnsTokenAmount() {
            var addr = org.dilithia.sdk.model.Address.of("dili1alice");
            var bal = new org.dilithia.sdk.model.Balance(addr, 1_000_000_000_000_000_000L);
            var amt = bal.asDili();
            assertEquals(18, amt.decimals());
            assertEquals("1", amt.formatted());
        }
    }

    // ── Receipt ──────────────────────────────────────────────────────

    @Nested
    class ReceiptTests {

        @Test
        void constructionAndAccessors() {
            var txHash = org.dilithia.sdk.model.TxHash.of("abc123");
            var receipt = new org.dilithia.sdk.model.Receipt(txHash, "success", 42L, 500L, null);
            assertEquals(txHash, receipt.txHash());
            assertEquals("success", receipt.status());
            assertEquals(42L, receipt.blockHeight());
            assertEquals(500L, receipt.gasUsed());
            assertNull(receipt.result());
        }

        @Test
        void feePaidAmountCalculation() {
            var txHash = org.dilithia.sdk.model.TxHash.of("abc123");
            var receipt = new org.dilithia.sdk.model.Receipt(txHash, "success", 42L, 1000L, null);
            var fee = receipt.feePaidAmount(10, 18);
            assertEquals(java.math.BigInteger.valueOf(10_000L), fee.toRaw());
        }
    }

    // ── DeployPayload construction ───────────────────────────────────

    @Nested
    class DeployPayloadTests {

        @Test
        void recordConstruction() {
            var from = org.dilithia.sdk.model.Address.of("dili1alice");
            var pk = org.dilithia.sdk.model.PublicKey.of("pubhex");
            var payload = new org.dilithia.sdk.model.DeployPayload(
                    "my_token", "0xdead", from, "mldsa65", pk, "sig", 1L, "dilithia-1", 1);
            assertEquals("my_token", payload.name());
            assertEquals("0xdead", payload.bytecode());
            assertEquals(from, payload.from());
            assertEquals("mldsa65", payload.alg());
            assertEquals(pk, payload.pk());
            assertEquals("sig", payload.sig());
            assertEquals(1L, payload.nonce());
            assertEquals("dilithia-1", payload.chainId());
            assertEquals(1, payload.version());
        }
    }

    // ── Exception hierarchy ──────────────────────────────────────────

    @Nested
    class ExceptionHierarchyTests {

        @Test
        void dilithiaExceptionIsBaseClass() {
            var ex = new org.dilithia.sdk.exception.DilithiaException("test");
            assertInstanceOf(Exception.class, ex);
            assertEquals("test", ex.getMessage());
        }

        @Test
        void rpcExceptionExtendsDilithiaException() {
            var ex = new org.dilithia.sdk.exception.RpcException(-32600, "invalid request");
            assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex);
            assertEquals(-32600, ex.code());
            assertEquals("invalid request", ex.rpcMessage());
            assertTrue(ex.getMessage().contains("-32600"));
        }

        @Test
        void httpExceptionExtendsDilithiaException() {
            var ex = new org.dilithia.sdk.exception.HttpException(502, "bad gateway");
            assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex);
            assertEquals(502, ex.statusCode());
            assertEquals("bad gateway", ex.body());
        }

        @Test
        void timeoutExceptionExtendsDilithiaException() {
            var cause = new RuntimeException("timed out");
            var ex = new org.dilithia.sdk.exception.TimeoutException(cause);
            assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex);
            assertEquals(cause, ex.getCause());
        }

        @Test
        void cryptoExceptionExtendsDilithiaException() {
            var ex = new org.dilithia.sdk.exception.CryptoException("key gen failed");
            assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex);
            assertEquals("key gen failed", ex.getMessage());
        }

        @Test
        void serializationExceptionExtendsDilithiaException() {
            var ex = new org.dilithia.sdk.exception.SerializationException("bad json");
            assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex);
        }

        @Test
        void validationExceptionExtendsDilithiaException() {
            var ex = new org.dilithia.sdk.exception.ValidationException("invalid input");
            assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex);
        }

        @Test
        void allExceptionsCaughtByDilithiaException() {
            // Verify a single catch(DilithiaException) covers all subtypes
            org.dilithia.sdk.exception.DilithiaException[] exceptions = {
                    new org.dilithia.sdk.exception.RpcException(-1, "err"),
                    new org.dilithia.sdk.exception.HttpException(500, "err"),
                    new org.dilithia.sdk.exception.TimeoutException(new RuntimeException()),
                    new org.dilithia.sdk.exception.CryptoException("err"),
                    new org.dilithia.sdk.exception.SerializationException("err"),
                    new org.dilithia.sdk.exception.ValidationException("err"),
            };
            for (var ex : exceptions) {
                assertInstanceOf(org.dilithia.sdk.exception.DilithiaException.class, ex,
                        ex.getClass().getSimpleName() + " should extend DilithiaException");
            }
        }
    }

    // ── CanonicalPayload ─────────────────────────────────────────────

    @Nested
    class CanonicalPayloadTests {

        @Test
        void canonicalPayloadStoresFieldsAndBytes() {
            var fields = new java.util.TreeMap<String, Object>();
            fields.put("a", 1);
            fields.put("b", "two");
            fields.put("c", true);

            byte[] canonical = "{\"a\":1,\"b\":\"two\",\"c\":true}".getBytes();
            var payload = new org.dilithia.sdk.model.CanonicalPayload(fields, canonical);

            assertEquals(fields, payload.fields());
            assertArrayEquals(canonical, payload.canonicalBytes());
        }

        @Test
        void deployCanonicalPayloadKeysAreSorted() {
            // Verify via DilithiaClient static method that keys come out alphabetically
            Map<String, Object> payload = DilithiaClient.buildDeployCanonicalPayload(
                    "alice", "token", "0xhash", 5, "chain-1");

            var keys = new java.util.ArrayList<>(payload.keySet());
            var sorted = new java.util.ArrayList<>(keys);
            java.util.Collections.sort(sorted);
            assertEquals(sorted, keys, "canonical payload keys must be alphabetically sorted");
        }
    }

    // ── Cross-language canonical payload consistency (shared vectors) ─

    @Nested
    class CrossLanguageCanonicalPayloadTests {

        private JsonObject loadVectors() throws IOException {
            // Try from the java/ project root (standard Maven test working dir)
            Path vectorsPath = Path.of(System.getProperty("user.dir"))
                    .resolve("../tests/vectors/canonical_payloads.json")
                    .normalize();
            if (!Files.exists(vectorsPath)) {
                // Fallback: deeper nesting
                vectorsPath = Path.of("../../../../tests/vectors/canonical_payloads.json");
            }
            String content = Files.readString(vectorsPath);
            return JsonParser.parseString(content).getAsJsonObject();
        }

        @SuppressWarnings("unchecked")
        private String canonicalJson(Map<String, Object> map) {
            TreeMap<String, Object> sorted = new TreeMap<>(map);
            // Sort nested maps recursively
            for (var entry : sorted.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?>) {
                    Map<String, Object> nestedMap = (Map<String, Object>) entry.getValue();
                    entry.setValue(new TreeMap<>(nestedMap));
                }
            }
            Gson gson = new Gson();
            return gson.toJson(sorted);
        }

        @Test
        void contractCallCanonicalMatchesVectors() throws IOException {
            JsonObject vectors = loadVectors();
            JsonObject v = vectors.getAsJsonObject("contract_call");
            String expectedJson = v.get("expected_json").getAsString();

            JsonObject argsNode = v.getAsJsonObject("input").getAsJsonObject("args");
            Map<String, Object> args = new TreeMap<>();
            for (var entry : argsNode.entrySet()) {
                JsonElement val = entry.getValue();
                if (val.getAsJsonPrimitive().isNumber()) {
                    args.put(entry.getKey(), val.getAsInt());
                } else {
                    args.put(entry.getKey(), val.getAsString());
                }
            }

            Map<String, Object> call = DilithiaClient.buildContractCall(
                    v.getAsJsonObject("input").get("contract").getAsString(),
                    v.getAsJsonObject("input").get("method").getAsString(),
                    args, null);

            String canonical = canonicalJson(call);
            assertEquals(expectedJson, canonical,
                    "contract_call canonical JSON must match cross-language vector");
        }

        @Test
        void deployCanonicalPayloadMatchesVectors() throws IOException {
            JsonObject vectors = loadVectors();
            JsonObject v = vectors.getAsJsonObject("deploy_canonical");
            String expectedJson = v.get("expected_json").getAsString();

            JsonObject input = v.getAsJsonObject("input");
            Map<String, Object> payload = DilithiaClient.buildDeployCanonicalPayload(
                    input.get("from").getAsString(),
                    input.get("name").getAsString(),
                    input.get("bytecode_hash").getAsString(),
                    input.get("nonce").getAsLong(),
                    input.get("chain_id").getAsString());

            String canonical = canonicalJson(payload);
            assertEquals(expectedJson, canonical,
                    "deploy_canonical canonical JSON must match cross-language vector");

            // Verify key ordering
            var keys = new ArrayList<>(payload.keySet());
            var expectedKeys = new ArrayList<String>();
            for (var n : v.getAsJsonArray("expected_keys_order")) {
                expectedKeys.add(n.getAsString());
            }
            assertEquals(expectedKeys, keys, "deploy payload keys must be alphabetically sorted");
        }

        @Test
        void withPaymasterMatchesVectors() throws IOException {
            JsonObject vectors = loadVectors();
            JsonObject v = vectors.getAsJsonObject("with_paymaster");

            JsonObject input = v.getAsJsonObject("input");
            JsonObject argsNode = input.getAsJsonObject("args");
            Map<String, Object> args = new LinkedHashMap<>();
            for (var entry : argsNode.entrySet()) {
                JsonElement val = entry.getValue();
                if (val.getAsJsonPrimitive().isNumber()) {
                    args.put(entry.getKey(), val.getAsInt());
                } else {
                    args.put(entry.getKey(), val.getAsString());
                }
            }

            Map<String, Object> call = DilithiaClient.buildContractCall(
                    input.get("contract").getAsString(),
                    input.get("method").getAsString(),
                    args, null);

            Map<String, Object> sponsored = DilithiaClient.withPaymaster(
                    call, input.get("paymaster").getAsString());

            assertTrue(sponsored.containsKey("paymaster"),
                    "sponsored call must contain paymaster key");
            assertEquals(input.get("paymaster").getAsString(), sponsored.get("paymaster"),
                    "paymaster value must match vector input");
            assertEquals(v.get("expected_has_paymaster").getAsBoolean(),
                    sponsored.containsKey("paymaster"));
        }
    }
}
