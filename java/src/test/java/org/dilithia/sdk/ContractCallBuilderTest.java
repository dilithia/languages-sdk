package org.dilithia.sdk;

import org.dilithia.sdk.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContractCallBuilderTest {

    private DilithiaClient client;

    @BeforeEach
    void setUp() throws ValidationException {
        client = Dilithia.client("http://localhost:8000/rpc").build();
    }

    @Test
    void toPayloadContainsRequiredFields() {
        ContractCallBuilder builder = new ContractCallBuilder(client, "token", "transfer",
                Map.of("to", "bob", "amount", 100));
        Map<String, Object> payload = builder.toPayload();

        assertEquals("token", payload.get("contract"));
        assertEquals("transfer", payload.get("method"));
        assertNotNull(payload.get("args"));
        assertNull(payload.get("paymaster"));
    }

    @Test
    void toPayloadWithPaymaster() {
        ContractCallBuilder builder = new ContractCallBuilder(client, "token", "transfer", Map.of());
        builder.withPaymaster("sponsor");
        Map<String, Object> payload = builder.toPayload();

        assertEquals("sponsor", payload.get("paymaster"));
    }

    @Test
    void toPayloadWithBlankPaymasterExcluded() {
        ContractCallBuilder builder = new ContractCallBuilder(client, "token", "transfer", Map.of());
        builder.withPaymaster("  ");
        Map<String, Object> payload = builder.toPayload();

        assertNull(payload.get("paymaster"));
    }

    @Test
    void toPayloadSortsKeysAlphabetically() {
        ContractCallBuilder builder = new ContractCallBuilder(client, "token", "transfer",
                Map.of("z", 1, "a", 2));
        Map<String, Object> payload = builder.toPayload();
        var keys = payload.keySet().stream().toList();
        // TreeMap sorts: args, contract, method
        assertEquals("args", keys.get(0));
        assertEquals("contract", keys.get(1));
        assertEquals("method", keys.get(2));
    }

    @Test
    void nullArgsDefaultsToEmptyMap() {
        ContractCallBuilder builder = new ContractCallBuilder(client, "token", "transfer", null);
        assertTrue(builder.args().isEmpty());
    }

    @Test
    void argsReturnsUnmodifiableCopy() {
        ContractCallBuilder builder = new ContractCallBuilder(client, "token", "transfer",
                Map.of("key", "value"));
        Map<String, Object> args = builder.args();
        assertThrows(UnsupportedOperationException.class, () -> args.put("new", "val"));
    }
}
