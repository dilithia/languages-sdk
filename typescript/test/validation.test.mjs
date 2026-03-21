import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { validateBytecode, estimateDeployGas } from "../dist/validation.js";

/** Build a valid WASM header with optional extra padding bytes. */
function makeValidWasm(extra = 0) {
  const header = new Uint8Array([0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00]);
  const buf = new Uint8Array(8 + extra);
  buf.set(header);
  return buf;
}

describe("validateBytecode", () => {
  it("accepts valid WASM", () => {
    const result = validateBytecode(makeValidWasm(100));
    assert.equal(result.valid, true);
    assert.deepEqual(result.errors, []);
    assert.equal(result.sizeBytes, 108);
  });

  it("rejects empty bytes", () => {
    const result = validateBytecode(new Uint8Array(0));
    assert.equal(result.valid, false);
    assert.ok(result.errors[0].includes("empty"));
    assert.equal(result.sizeBytes, 0);
  });

  it("rejects too-small bytes", () => {
    const result = validateBytecode(new Uint8Array([0x00, 0x61, 0x73]));
    assert.equal(result.valid, false);
    assert.ok(result.errors[0].includes("too small"));
  });

  it("rejects too-large bytes", () => {
    const result = validateBytecode(makeValidWasm(512 * 1024));
    assert.equal(result.valid, false);
    assert.ok(result.errors.some((e) => e.includes("too large")));
  });

  it("rejects invalid magic bytes", () => {
    const bytes = new Uint8Array([0xff, 0xff, 0xff, 0xff, 0x01, 0x00, 0x00, 0x00, 0x00]);
    const result = validateBytecode(bytes);
    assert.equal(result.valid, false);
    assert.ok(result.errors.some((e) => e.includes("magic")));
  });

  it("rejects invalid version", () => {
    const bytes = new Uint8Array([0x00, 0x61, 0x73, 0x6d, 0x02, 0x00, 0x00, 0x00, 0x00]);
    const result = validateBytecode(bytes);
    assert.equal(result.valid, false);
    assert.ok(result.errors.some((e) => e.includes("version")));
  });
});

describe("estimateDeployGas", () => {
  it("returns correct estimate for known size", () => {
    const wasm = makeValidWasm(0); // 8 bytes
    assert.equal(estimateDeployGas(wasm), 500_000n + 8n * 50n);
  });

  it("scales with bytecode size", () => {
    const wasm = makeValidWasm(992); // 1000 bytes
    assert.equal(estimateDeployGas(wasm), 500_000n + 1000n * 50n);
  });
});
