"""WASM bytecode validation utilities.

Client-side validation functions for checking WASM bytecode before
submitting deploy/upgrade transactions. No RPC calls required.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# WASM magic bytes: \0asm
_WASM_MAGIC = bytes([0x00, 0x61, 0x73, 0x6D])

# WASM version 1
_WASM_VERSION_1 = bytes([0x01, 0x00, 0x00, 0x00])

# Maximum bytecode size (512 KB)
_MAX_BYTECODE_SIZE = 512 * 1024

# Gas constants
_BASE_DEPLOY_GAS = 500_000
_PER_BYTE_GAS = 50


@dataclass
class BytecodeValidation:
    """Result of validating WASM bytecode."""

    valid: bool
    errors: list[str] = field(default_factory=list)
    size_bytes: int = 0


def validate_bytecode(wasm_bytes: bytes) -> BytecodeValidation:
    """Validate raw WASM bytecode.

    Checks magic bytes, version header, and size constraints.
    This is a lightweight client-side check — no WASM parsing or RPC required.

    Args:
        wasm_bytes: Raw WASM binary data.

    Returns:
        A BytecodeValidation result.
    """
    errors: list[str] = []
    size_bytes = len(wasm_bytes)

    if size_bytes == 0:
        errors.append("bytecode is empty")
        return BytecodeValidation(valid=False, errors=errors, size_bytes=size_bytes)

    if size_bytes < 8:
        errors.append("bytecode too small: must be at least 8 bytes")
        return BytecodeValidation(valid=False, errors=errors, size_bytes=size_bytes)

    if size_bytes > _MAX_BYTECODE_SIZE:
        errors.append(
            f"bytecode too large: {size_bytes} bytes exceeds maximum of {_MAX_BYTECODE_SIZE} bytes"
        )

    if wasm_bytes[:4] != _WASM_MAGIC:
        errors.append("invalid WASM magic bytes: expected \\0asm")

    if wasm_bytes[4:8] != _WASM_VERSION_1:
        errors.append("unsupported WASM version: expected version 1")

    return BytecodeValidation(
        valid=len(errors) == 0, errors=errors, size_bytes=size_bytes
    )


def estimate_deploy_gas(wasm_bytes: bytes) -> int:
    """Estimate the gas cost for deploying WASM bytecode.

    Uses a simple heuristic: BASE_DEPLOY_GAS + len(wasm_bytes) * PER_BYTE_GAS.

    Args:
        wasm_bytes: Raw WASM binary data.

    Returns:
        Estimated gas cost.
    """
    return _BASE_DEPLOY_GAS + len(wasm_bytes) * _PER_BYTE_GAS
