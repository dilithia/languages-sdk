from __future__ import annotations

import asyncio
from dataclasses import dataclass
from importlib import import_module
from typing import Any, Protocol


@dataclass(slots=True)
class Commitment:
    hash: str
    value: int
    secret: str
    nonce: str


@dataclass(slots=True)
class Nullifier:
    hash: str


@dataclass(slots=True)
class StarkProofResult:
    proof: str
    vk: str
    inputs: str


class DilithiaZkAdapter(Protocol):
    def poseidon_hash(self, inputs: list[int]) -> str: ...

    def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment: ...

    def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier: ...

    def generate_preimage_proof(self, values: list[int]) -> StarkProofResult: ...

    def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...

    def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult: ...

    def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...


class NativeZkAdapter:
    def __init__(self, module: Any) -> None:
        self._module = module

    def poseidon_hash(self, inputs: list[int]) -> str:
        return self._module.poseidon_hash(inputs)

    def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment:
        payload = self._module.compute_commitment(value, secret_hex, nonce_hex)
        return Commitment(
            hash=str(payload["hash"]),
            value=int(payload["value"]),
            secret=str(payload["secret"]),
            nonce=str(payload["nonce"]),
        )

    def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier:
        payload = self._module.compute_nullifier(secret_hex, nonce_hex)
        return Nullifier(hash=str(payload["hash"]))

    def generate_preimage_proof(self, values: list[int]) -> StarkProofResult:
        payload = self._module.generate_preimage_proof(values)
        return _normalize_proof_result(payload)

    def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return bool(self._module.verify_preimage_proof(proof_hex, vk_json, inputs_json))

    def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult:
        payload = self._module.generate_range_proof(value, min_val, max_val)
        return _normalize_proof_result(payload)

    def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return bool(self._module.verify_range_proof(proof_hex, vk_json, inputs_json))


# ---------------------------------------------------------------------------
# Async variants
# ---------------------------------------------------------------------------


class AsyncDilithiaZkAdapter(Protocol):
    async def poseidon_hash(self, inputs: list[int]) -> str: ...

    async def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment: ...

    async def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier: ...

    async def generate_preimage_proof(self, values: list[int]) -> StarkProofResult: ...

    async def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...

    async def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult: ...

    async def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...


class AsyncNativeZkAdapter:
    """Wraps a synchronous :class:`NativeZkAdapter` and delegates every
    call to a thread executor via :func:`asyncio.to_thread` so that
    CPU-intensive STARK proof work never blocks the event loop.
    """

    def __init__(self, sync_adapter: NativeZkAdapter) -> None:
        self._sync = sync_adapter

    async def poseidon_hash(self, inputs: list[int]) -> str:
        return await asyncio.to_thread(self._sync.poseidon_hash, inputs)

    async def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment:
        return await asyncio.to_thread(self._sync.compute_commitment, value, secret_hex, nonce_hex)

    async def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier:
        return await asyncio.to_thread(self._sync.compute_nullifier, secret_hex, nonce_hex)

    async def generate_preimage_proof(self, values: list[int]) -> StarkProofResult:
        return await asyncio.to_thread(self._sync.generate_preimage_proof, values)

    async def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return await asyncio.to_thread(self._sync.verify_preimage_proof, proof_hex, vk_json, inputs_json)

    async def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult:
        return await asyncio.to_thread(self._sync.generate_range_proof, value, min_val, max_val)

    async def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return await asyncio.to_thread(self._sync.verify_range_proof, proof_hex, vk_json, inputs_json)


def _normalize_proof_result(payload: Any) -> StarkProofResult:
    return StarkProofResult(
        proof=str(payload["proof"]),
        vk=str(payload["vk"]),
        inputs=str(payload["inputs"]),
    )


def load_zk_adapter() -> DilithiaZkAdapter | None:
    try:
        module = import_module("dilithia_sdk_zk")
    except ImportError:
        return None
    return NativeZkAdapter(module)


def load_async_zk_adapter() -> AsyncDilithiaZkAdapter | None:
    try:
        module = import_module("dilithia_sdk_zk")
    except ImportError:
        return None
    return AsyncNativeZkAdapter(NativeZkAdapter(module))
