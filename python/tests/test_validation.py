"""Tests for WASM bytecode validation utilities."""

from dilithia_sdk.validation import BytecodeValidation, estimate_deploy_gas, validate_bytecode


def _make_valid_wasm(extra: int = 0) -> bytes:
    """Build a valid WASM header with optional extra padding bytes."""
    return bytes([0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00]) + b"\x00" * extra


class TestValidateBytecode:
    def test_valid_wasm(self) -> None:
        result = validate_bytecode(_make_valid_wasm(100))
        assert result.valid is True
        assert result.errors == []
        assert result.size_bytes == 108

    def test_empty_bytes(self) -> None:
        result = validate_bytecode(b"")
        assert result.valid is False
        assert "empty" in result.errors[0]
        assert result.size_bytes == 0

    def test_too_small(self) -> None:
        result = validate_bytecode(b"\x00\x61\x73")
        assert result.valid is False
        assert "too small" in result.errors[0]

    def test_too_large(self) -> None:
        result = validate_bytecode(_make_valid_wasm(512 * 1024))
        assert result.valid is False
        assert any("too large" in e for e in result.errors)

    def test_invalid_magic(self) -> None:
        data = bytes([0xFF, 0xFF, 0xFF, 0xFF, 0x01, 0x00, 0x00, 0x00, 0x00])
        result = validate_bytecode(data)
        assert result.valid is False
        assert any("magic" in e for e in result.errors)

    def test_invalid_version(self) -> None:
        data = bytes([0x00, 0x61, 0x73, 0x6D, 0x02, 0x00, 0x00, 0x00, 0x00])
        result = validate_bytecode(data)
        assert result.valid is False
        assert any("version" in e for e in result.errors)


class TestEstimateDeployGas:
    def test_known_size(self) -> None:
        wasm = _make_valid_wasm(0)  # 8 bytes
        assert estimate_deploy_gas(wasm) == 500_000 + 8 * 50

    def test_scales_with_size(self) -> None:
        wasm = _make_valid_wasm(992)  # 1000 bytes
        assert estimate_deploy_gas(wasm) == 500_000 + 1000 * 50
