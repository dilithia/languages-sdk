package org.dilithia.sdk.crypto;

import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.internal.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NativeCryptoBridgeTest {

    @Nested
    class ConstructorTests {

        @Test
        void throwsWhenEnvVarNotSet() {
            // DILITHIUM_NATIVE_CORE_LIB is not set in CI/test environments
            assertThrows(CryptoException.class, NativeCryptoBridge::new);
        }
    }

    @Nested
    class NativeCryptoAdaptersTests {

        @Test
        void loadReturnsEmptyWhenNativeLibUnavailable() {
            Optional<DilithiaCryptoAdapter> adapter = NativeCryptoAdapters.load();
            assertTrue(adapter.isEmpty());
        }
    }

    @Nested
    class EnvelopeParsingTests {

        @Test
        void successEnvelopeStructure() throws Exception {
            // Test that the JSON envelope format works
            String payload = "{\"ok\":true,\"value\":\"mnemonic words here\"}";
            Map<String, Object> envelope = Json.deserializeMap(payload);
            assertTrue((Boolean) envelope.get("ok"));
            assertEquals("mnemonic words here", envelope.get("value"));
        }

        @Test
        void errorEnvelopeStructure() throws Exception {
            String payload = "{\"ok\":false,\"error\":\"invalid mnemonic\"}";
            Map<String, Object> envelope = Json.deserializeMap(payload);
            assertFalse((Boolean) envelope.get("ok"));
            assertEquals("invalid mnemonic", envelope.get("error"));
        }

        @Test
        void mapValueEnvelopeStructure() throws Exception {
            String payload = "{\"ok\":true,\"value\":{\"address\":\"dili1test\",\"public_key\":\"pk_hex\",\"secret_key\":\"sk_hex\"}}";
            Map<String, Object> envelope = Json.deserializeMap(payload);
            assertTrue((Boolean) envelope.get("ok"));
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) envelope.get("value");
            assertEquals("dili1test", value.get("address"));
            assertEquals("pk_hex", value.get("public_key"));
            assertEquals("sk_hex", value.get("secret_key"));
        }
    }

    @Nested
    class RecordTests {

        @Test
        void dilithiaKeypairRecord() {
            var kp = new DilithiaKeypair(
                    org.dilithia.sdk.model.SecretKey.of("sk"),
                    org.dilithia.sdk.model.PublicKey.of("pk"),
                    org.dilithia.sdk.model.Address.of("dili1addr"));
            assertEquals("sk", kp.secretKey().hex());
            assertEquals("pk", kp.publicKey().hex());
            assertEquals("dili1addr", kp.address().value());
        }

        @Test
        void dilithiaAccountRecord() {
            var account = new DilithiaAccount(
                    org.dilithia.sdk.model.Address.of("dili1addr"),
                    org.dilithia.sdk.model.PublicKey.of("pk"),
                    org.dilithia.sdk.model.SecretKey.of("sk"),
                    0,
                    Map.of("version", 1));
            assertEquals("dili1addr", account.address().value());
            assertEquals(0, account.accountIndex());
            assertNotNull(account.walletFile());
        }

        @Test
        void dilithiaAccountWithNullWalletFile() {
            var account = new DilithiaAccount(
                    org.dilithia.sdk.model.Address.of("dili1addr"),
                    org.dilithia.sdk.model.PublicKey.of("pk"),
                    org.dilithia.sdk.model.SecretKey.of("sk"),
                    1,
                    null);
            assertNull(account.walletFile());
        }

        @Test
        void dilithiaSignatureRecord() {
            var sig = new DilithiaSignature("mldsa65", "deadbeef");
            assertEquals("mldsa65", sig.algorithm());
            assertEquals("deadbeef", sig.signature());
        }
    }
}
