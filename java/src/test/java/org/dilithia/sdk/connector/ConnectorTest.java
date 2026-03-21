package org.dilithia.sdk.connector;

import org.dilithia.sdk.ContractCallBuilder;
import org.dilithia.sdk.Dilithia;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.exception.DilithiaException;
import org.dilithia.sdk.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorTest {

    private DilithiaClient client;

    @BeforeEach
    void setUp() throws ValidationException {
        client = Dilithia.client("http://localhost:8000/rpc").build();
    }

    @Nested
    class GasSponsorConnectorTests {

        @Test
        void acceptsCallReturnsTrue() throws DilithiaException {
            GasSponsorConnector sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", "gas_sponsor");
            // acceptsCall builds a contract call and checks args field is not null
            boolean accepts = sponsor.acceptsCall("dili1user", "token", "transfer");
            assertTrue(accepts);
        }

        @Test
        void remainingQuotaReturnsZeroForNonNumeric() throws DilithiaException {
            GasSponsorConnector sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", "gas_sponsor");
            // remainingQuota builds a call and checks nested user value as Number
            // Since buildContractCall returns args as a map of {user: "dili1user"}, not a Number,
            // it will return 0L
            long quota = sponsor.remainingQuota("dili1user");
            assertEquals(0L, quota);
        }

        @Test
        void applyPaymasterSetsPaymaster() {
            GasSponsorConnector sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", "my_paymaster");
            ContractCallBuilder call = new ContractCallBuilder(client, "token", "transfer", Map.of("amount", 100));
            assertNull(call.paymaster());

            ContractCallBuilder result = sponsor.applyPaymaster(call);
            assertEquals("my_paymaster", result.paymaster());
            assertSame(call, result); // returns same builder
        }

        @Test
        void applyPaymasterWithNullPaymasterIsNoOp() {
            GasSponsorConnector sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", null);
            ContractCallBuilder call = new ContractCallBuilder(client, "token", "transfer", Map.of());
            sponsor.applyPaymaster(call);
            assertNull(call.paymaster());
        }

        @Test
        void applyPaymasterWithBlankPaymasterIsNoOp() {
            GasSponsorConnector sponsor = new GasSponsorConnector(client, "wasm:gas_sponsor", "   ");
            ContractCallBuilder call = new ContractCallBuilder(client, "token", "transfer", Map.of());
            sponsor.applyPaymaster(call);
            assertNull(call.paymaster());
        }
    }

    @Nested
    class MessagingConnectorTests {

        @Test
        void sendMessageBuildsCorrectCall() {
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", "paymaster1");
            ContractCallBuilder send = messaging.sendMessage("ethereum", Map.of("amount", 1));

            assertEquals("send_message", send.method());
            assertEquals("wasm:messaging", send.contract());
            assertEquals("paymaster1", send.paymaster());

            Map<String, Object> args = send.args();
            assertEquals("ethereum", args.get("dest_chain"));
            assertNotNull(args.get("payload"));
        }

        @Test
        void sendMessageWithoutPaymaster() {
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", null);
            ContractCallBuilder send = messaging.sendMessage("polygon", Map.of("data", "test"));

            assertEquals("send_message", send.method());
            assertNull(send.paymaster());
        }

        @Test
        void sendMessageWithBlankPaymaster() {
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", "  ");
            ContractCallBuilder send = messaging.sendMessage("polygon", Map.of("data", "test"));

            assertNull(send.paymaster());
        }

        @Test
        void receiveMessageBuildsCorrectCall() {
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", "paymaster1");
            ContractCallBuilder receive = messaging.receiveMessage("ethereum", "bridge_contract", Map.of("tx", "0xabc"));

            assertEquals("receive_message", receive.method());
            assertEquals("wasm:messaging", receive.contract());
            assertEquals("paymaster1", receive.paymaster());

            Map<String, Object> args = receive.args();
            assertEquals("ethereum", args.get("source_chain"));
            assertEquals("bridge_contract", args.get("source_contract"));
        }

        @Test
        void receiveMessageWithoutPaymaster() {
            MessagingConnector messaging = new MessagingConnector(client, "wasm:messaging", null);
            ContractCallBuilder receive = messaging.receiveMessage("eth", "bridge", Map.of("tx", "0x1"));

            assertNull(receive.paymaster());
        }
    }
}
