package org.dilithia.sdk;

import java.util.ArrayList;
import java.util.List;

import org.dilithia.sdk.model.BytecodeValidation;

/**
 * Client-side utilities for WASM contract bytecode validation and gas estimation.
 *
 * <p>These are pure functions — no RPC calls or native bridges required.
 */
public final class Contracts {

    /** WASM magic bytes: \0asm */
    private static final byte[] WASM_MAGIC = {0x00, 0x61, 0x73, 0x6D};

    /** WASM version 1 */
    private static final byte[] WASM_VERSION_1 = {0x01, 0x00, 0x00, 0x00};

    /** Maximum bytecode size (512 KB). */
    public static final int MAX_BYTECODE_SIZE = 512 * 1024;

    /** Base gas cost for deploying a contract. */
    public static final long BASE_DEPLOY_GAS = 500_000L;

    /** Gas cost per byte of bytecode. */
    public static final long PER_BYTE_GAS = 50L;

    private Contracts() {
        // utility class
    }

    /**
     * Validate raw WASM bytecode.
     *
     * <p>Checks magic bytes, version header, and size constraints.
     * This is a lightweight client-side check — no WASM parsing or RPC required.
     *
     * @param wasmBytes raw WASM binary data
     * @return a {@link BytecodeValidation} result
     */
    public static BytecodeValidation validateBytecode(byte[] wasmBytes) {
        List<String> errors = new ArrayList<>();
        int sizeBytes = wasmBytes.length;

        if (sizeBytes == 0) {
            errors.add("bytecode is empty");
            return new BytecodeValidation(false, errors, sizeBytes);
        }

        if (sizeBytes < 8) {
            errors.add("bytecode too small: must be at least 8 bytes");
            return new BytecodeValidation(false, errors, sizeBytes);
        }

        if (sizeBytes > MAX_BYTECODE_SIZE) {
            errors.add(String.format(
                "bytecode too large: %d bytes exceeds maximum of %d bytes",
                sizeBytes, MAX_BYTECODE_SIZE
            ));
        }

        boolean magicOk = true;
        for (int i = 0; i < 4; i++) {
            if (wasmBytes[i] != WASM_MAGIC[i]) {
                magicOk = false;
                break;
            }
        }
        if (!magicOk) {
            errors.add("invalid WASM magic bytes: expected \\0asm");
        }

        boolean versionOk = true;
        for (int i = 0; i < 4; i++) {
            if (wasmBytes[4 + i] != WASM_VERSION_1[i]) {
                versionOk = false;
                break;
            }
        }
        if (!versionOk) {
            errors.add("unsupported WASM version: expected version 1");
        }

        return new BytecodeValidation(errors.isEmpty(), errors, sizeBytes);
    }

    /**
     * Estimate the gas cost for deploying WASM bytecode.
     *
     * <p>Uses a simple heuristic: {@code BASE_DEPLOY_GAS + len(wasmBytes) * PER_BYTE_GAS}.
     *
     * @param wasmBytes raw WASM binary data
     * @return estimated gas cost
     */
    public static long estimateDeployGas(byte[] wasmBytes) {
        return BASE_DEPLOY_GAS + (long) wasmBytes.length * PER_BYTE_GAS;
    }
}
