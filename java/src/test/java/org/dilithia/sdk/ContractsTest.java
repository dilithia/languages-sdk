package org.dilithia.sdk;

import org.dilithia.sdk.model.BytecodeValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractsTest {

    private static byte[] makeValidWasm(int extra) {
        byte[] header = {0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00};
        byte[] result = new byte[8 + extra];
        System.arraycopy(header, 0, result, 0, 8);
        return result;
    }

    @Test
    void validateBytecode_valid() {
        BytecodeValidation result = Contracts.validateBytecode(makeValidWasm(100));
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(108, result.sizeBytes());
    }

    @Test
    void validateBytecode_empty() {
        BytecodeValidation result = Contracts.validateBytecode(new byte[0]);
        assertFalse(result.valid());
        assertTrue(result.errors().get(0).contains("empty"));
        assertEquals(0, result.sizeBytes());
    }

    @Test
    void validateBytecode_tooSmall() {
        BytecodeValidation result = Contracts.validateBytecode(new byte[]{0x00, 0x61, 0x73});
        assertFalse(result.valid());
        assertTrue(result.errors().get(0).contains("too small"));
    }

    @Test
    void validateBytecode_tooLarge() {
        BytecodeValidation result = Contracts.validateBytecode(makeValidWasm(512 * 1024));
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("too large")));
    }

    @Test
    void validateBytecode_invalidMagic() {
        byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                        0x01, 0x00, 0x00, 0x00, 0x00};
        BytecodeValidation result = Contracts.validateBytecode(data);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("magic")));
    }

    @Test
    void validateBytecode_invalidVersion() {
        byte[] data = {0x00, 0x61, 0x73, 0x6D, 0x02, 0x00, 0x00, 0x00, 0x00};
        BytecodeValidation result = Contracts.validateBytecode(data);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("version")));
    }

    @Test
    void estimateDeployGas_knownSize() {
        byte[] wasm = makeValidWasm(0); // 8 bytes
        assertEquals(500_000L + 8L * 50L, Contracts.estimateDeployGas(wasm));
    }

    @Test
    void estimateDeployGas_scalesWithSize() {
        byte[] wasm = makeValidWasm(992); // 1000 bytes
        assertEquals(500_000L + 1000L * 50L, Contracts.estimateDeployGas(wasm));
    }
}
