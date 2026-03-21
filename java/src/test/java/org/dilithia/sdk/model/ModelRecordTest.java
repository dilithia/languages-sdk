package org.dilithia.sdk.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelRecordTest {

    @Nested
    class NetworkInfoTest {

        @Test
        void recordAccessors() {
            var info = new NetworkInfo(100L, "0xhash", "dilithia-1");
            assertEquals(100L, info.blockHeight());
            assertEquals("0xhash", info.blockHash());
            assertEquals("dilithia-1", info.chainId());
        }

        @Test
        void equalityByFields() {
            var a = new NetworkInfo(1L, "h", "c");
            var b = new NetworkInfo(1L, "h", "c");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    @Nested
    class GasEstimateTest {

        @Test
        void recordAccessors() {
            var gas = new GasEstimate(1000000L, 10L);
            assertEquals(1000000L, gas.gasLimit());
            assertEquals(10L, gas.gasPrice());
        }
    }

    @Nested
    class QueryResultTest {

        @Test
        void recordAccessors() {
            var result = new QueryResult(Map.of("total", "1000"));
            assertEquals("1000", result.result().get("total"));
        }

        @Test
        void nullResult() {
            var result = new QueryResult(null);
            assertNull(result.result());
        }
    }

    @Nested
    class ContractAbiTest {

        @Test
        void recordAccessors() {
            var method = Map.<String, Object>of("name", "transfer");
            var abi = new ContractAbi("token", List.of(method));
            assertEquals("token", abi.contract());
            assertEquals(1, abi.methods().size());
            assertEquals("transfer", abi.methods().get(0).get("name"));
        }
    }

    @Nested
    class RpcResponseTest {

        @Test
        void recordAccessors() {
            var resp = new RpcResponse(1, "result_data");
            assertEquals(1, resp.id());
            assertEquals("result_data", resp.result());
        }

        @Test
        void nullResult() {
            var resp = new RpcResponse(0, null);
            assertNull(resp.result());
        }
    }

    @Nested
    class NameRecordTest {

        @Test
        void recordAccessors() {
            var record = new NameRecord("alice.dili", Address.of("dili1alice"));
            assertEquals("alice.dili", record.name());
            assertEquals(Address.of("dili1alice"), record.address());
        }
    }

    @Nested
    class SignedPayloadTest {

        @Test
        void recordAccessors() {
            var signed = new SignedPayload("mldsa65", PublicKey.of("pk_hex"), "sig_hex");
            assertEquals("mldsa65", signed.alg());
            assertEquals(PublicKey.of("pk_hex"), signed.publicKey());
            assertEquals("sig_hex", signed.signature());
        }
    }

    @Nested
    class DeployPayloadTest {

        @Test
        void recordAccessors() {
            var payload = new DeployPayload(
                    "token", "0xdead", Address.of("dili1alice"),
                    "mldsa65", PublicKey.of("pk"), "sig", 1L, "chain-1", 1);
            assertEquals("token", payload.name());
            assertEquals("0xdead", payload.bytecode());
            assertEquals(Address.of("dili1alice"), payload.from());
            assertEquals("mldsa65", payload.alg());
            assertEquals(PublicKey.of("pk"), payload.pk());
            assertEquals("sig", payload.sig());
            assertEquals(1L, payload.nonce());
            assertEquals("chain-1", payload.chainId());
            assertEquals(1, payload.version());
        }
    }

    @Nested
    class AddressSummaryTest {

        @Test
        void recordAccessors() {
            var summary = new AddressSummary(Address.of("dili1alice"), 5000L, 10L);
            assertEquals(Address.of("dili1alice"), summary.address());
            assertEquals(5000L, summary.balance());
            assertEquals(10L, summary.nonce());
        }

        @Test
        void balanceAsTokenAmount() {
            var summary = new AddressSummary(Address.of("dili1alice"),
                    1_000_000_000_000_000_000L, 1L);
            var amt = summary.balanceAsTokenAmount(18);
            assertEquals("1", amt.formatted());
        }

        @Test
        void balanceAsDili() {
            var summary = new AddressSummary(Address.of("dili1alice"),
                    2_000_000_000_000_000_000L, 1L);
            var amt = summary.balanceAsDili();
            assertEquals("2", amt.formatted());
        }
    }

    @Nested
    class CanonicalPayloadTest {

        @Test
        void recordAccessors() {
            var fields = Map.<String, Object>of("a", 1);
            byte[] bytes = "{\"a\":1}".getBytes();
            var cp = new CanonicalPayload(fields, bytes);
            assertEquals(fields, cp.fields());
            assertArrayEquals(bytes, cp.canonicalBytes());
        }
    }

    @Nested
    class NonceTest {

        @Test
        void recordAccessors() {
            var nonce = new Nonce(Address.of("dili1alice"), 42L);
            assertEquals(Address.of("dili1alice"), nonce.address());
            assertEquals(42L, nonce.nonce());
        }
    }

    @Nested
    class ReceiptTest {

        @Test
        void withResult() {
            var receipt = new Receipt(TxHash.of("0x1"), "success", 100L, 500L,
                    Map.of("key", "value"));
            assertEquals("value", receipt.result().get("key"));
        }
    }

    @Nested
    class BalanceTest {

        @Test
        void asTokenAmountWithDecimals() {
            var bal = new Balance(Address.of("dili1test"), 5_000_000L);
            var amt = bal.asTokenAmount(6);
            assertEquals("5", amt.formatted());
        }
    }

    @Nested
    class TokenTest {

        @Test
        void diliConstant() {
            assertEquals("DILI", Token.DILI.symbol());
            assertEquals("Dilithia", Token.DILI.name());
            assertEquals(18, Token.DILI.decimals());
        }

        @Test
        void amountFromString() {
            var amt = Token.DILI.amount("2.5");
            assertEquals(18, amt.decimals());
            assertEquals("2.5", amt.formatted());
        }

        @Test
        void amountRaw() {
            var amt = Token.DILI.amountRaw(1_000_000_000_000_000_000L);
            assertEquals("1", amt.formatted());
        }
    }

    @Nested
    class TokenAmountTest {

        @Test
        void toStringContainsDecimals() {
            var amt = TokenAmount.dili("1.5");
            assertTrue(amt.toString().contains("1.5"));
            assertTrue(amt.toString().contains("18 decimals"));
        }

        @Test
        void fromRawLong() {
            var amt = TokenAmount.fromRaw(1_000_000L, 6);
            assertEquals("1", amt.formatted());
        }
    }

    @Nested
    class CommitmentTest {

        @Test
        void recordAccessors() {
            var c = new Commitment("0xhash", 1000L, "0xsecret", "0xnonce");
            assertEquals("0xhash", c.hash());
            assertEquals(1000L, c.value());
            assertEquals("0xsecret", c.secret());
            assertEquals("0xnonce", c.nonce());
        }
    }

    @Nested
    class NullifierTest {

        @Test
        void recordAccessors() {
            var n = new Nullifier("0xnullhash");
            assertEquals("0xnullhash", n.hash());
        }
    }

    @Nested
    class StarkProofResultTest {

        @Test
        void recordAccessors() {
            var result = new StarkProofResult("0xproof", "{\"vk\":1}", "{\"inputs\":2}");
            assertEquals("0xproof", result.proof());
            assertEquals("{\"vk\":1}", result.vk());
            assertEquals("{\"inputs\":2}", result.inputs());
        }
    }

    @Nested
    class BytecodeValidationTest {

        @Test
        void recordAccessors() {
            var v = new BytecodeValidation(true, List.of(), 100);
            assertTrue(v.valid());
            assertTrue(v.errors().isEmpty());
            assertEquals(100, v.sizeBytes());
        }

        @Test
        void invalidWithErrors() {
            var v = new BytecodeValidation(false, List.of("error1", "error2"), 0);
            assertFalse(v.valid());
            assertEquals(2, v.errors().size());
        }
    }
}
