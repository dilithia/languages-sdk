// ── WASM Bytecode Validation ─────────────────────────────────────────────────

/** WASM magic bytes: \0asm */
const WASM_MAGIC = new Uint8Array([0x00, 0x61, 0x73, 0x6d]);

/** WASM version 1 bytes */
const WASM_VERSION_1 = new Uint8Array([0x01, 0x00, 0x00, 0x00]);

/** Maximum bytecode size in bytes (512 KB). */
const MAX_BYTECODE_SIZE = 512 * 1024;

/** Base gas cost for deploying a contract. */
const BASE_DEPLOY_GAS = 500_000n;

/** Gas cost per byte of bytecode. */
const PER_BYTE_GAS = 50n;

/** Result of validating WASM bytecode. */
export interface BytecodeValidation {
  /** Whether the bytecode passed all checks. */
  valid: boolean;
  /** List of validation error messages (empty when valid). */
  errors: string[];
  /** Size of the input bytecode in bytes. */
  sizeBytes: number;
}

/**
 * Validate raw WASM bytecode.
 *
 * Checks magic bytes, version header, and size constraints.
 * This is a lightweight client-side check — no WASM parsing or RPC required.
 *
 * @param wasmBytes - Raw WASM binary data.
 * @returns A {@link BytecodeValidation} result.
 */
export function validateBytecode(wasmBytes: Uint8Array): BytecodeValidation {
  const errors: string[] = [];
  const sizeBytes = wasmBytes.length;

  if (sizeBytes === 0) {
    errors.push("bytecode is empty");
    return { valid: false, errors, sizeBytes };
  }

  if (sizeBytes < 8) {
    errors.push("bytecode too small: must be at least 8 bytes");
    return { valid: false, errors, sizeBytes };
  }

  if (sizeBytes > MAX_BYTECODE_SIZE) {
    errors.push(`bytecode too large: ${sizeBytes} bytes exceeds maximum of ${MAX_BYTECODE_SIZE} bytes`);
  }

  // Check magic bytes
  let magicOk = true;
  for (let i = 0; i < 4; i++) {
    if (wasmBytes[i] !== WASM_MAGIC[i]) {
      magicOk = false;
      break;
    }
  }
  if (!magicOk) {
    errors.push("invalid WASM magic bytes: expected \\0asm");
  }

  // Check version
  let versionOk = true;
  for (let i = 0; i < 4; i++) {
    if (wasmBytes[4 + i] !== WASM_VERSION_1[i]) {
      versionOk = false;
      break;
    }
  }
  if (!versionOk) {
    errors.push("unsupported WASM version: expected version 1");
  }

  return { valid: errors.length === 0, errors, sizeBytes };
}

/**
 * Estimate the gas cost for deploying WASM bytecode.
 *
 * Uses a simple heuristic: `BASE_DEPLOY_GAS + len(wasmBytes) * PER_BYTE_GAS`.
 *
 * @param wasmBytes - Raw WASM binary data.
 * @returns Estimated gas cost as a bigint.
 */
export function estimateDeployGas(wasmBytes: Uint8Array): bigint {
  return BASE_DEPLOY_GAS + BigInt(wasmBytes.length) * PER_BYTE_GAS;
}
