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


@dataclass(slots=True)
class CommitmentProofResult:
    proof: str
    public_inputs: str
    verification_key: str


@dataclass(slots=True)
class PredicateProofResult:
    proof: str
    commitment: str
    min: int
    max: int
    domain_tag: int


@dataclass(slots=True)
class TransferProofResult:
    proof: str
    sender_pre: int
    receiver_pre: int
    sender_post: int
    receiver_post: int


@dataclass(slots=True)
class MerkleProofResult:
    proof: str
    leaf_hash: str
    root: str
    depth: int


class DilithiaZkAdapter(Protocol):
    def poseidon_hash(self, inputs: list[int]) -> str: ...

    def compute_commitment(self, value: int, secret_hex: str, nonce_hex: str) -> Commitment: ...

    def compute_nullifier(self, secret_hex: str, nonce_hex: str) -> Nullifier: ...

    def generate_preimage_proof(self, values: list[int]) -> StarkProofResult: ...

    def verify_preimage_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...

    def generate_range_proof(self, value: int, min_val: int, max_val: int) -> StarkProofResult: ...

    def verify_range_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...

    def generate_commitment_proof(self, value: int, blinding: int, domain_tag: int) -> CommitmentProofResult: ...

    def verify_commitment_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...

    def prove_predicate(self, value: int, blinding: int, domain_tag: int, min_val: int, max_val: int) -> PredicateProofResult: ...

    def prove_age_over(self, birth_year: int, current_year: int, min_age: int, blinding: int) -> PredicateProofResult: ...

    def verify_age_over(self, proof_hex: str, commitment_hex: str, min_age: int) -> bool: ...

    def prove_balance_above(self, balance: int, blinding: int, min_balance: int, max_balance: int) -> PredicateProofResult: ...

    def verify_balance_above(self, proof_hex: str, commitment_hex: str, min_balance: int, max_balance: int) -> bool: ...

    def prove_transfer(self, sender_pre: int, receiver_pre: int, amount: int) -> TransferProofResult: ...

    def verify_transfer(self, proof_hex: str, inputs_json: str) -> bool: ...

    def prove_merkle_verify(self, leaf_hash_hex: str, path_json: str) -> MerkleProofResult: ...

    def verify_merkle_proof(self, proof_hex: str, inputs_json: str) -> bool: ...


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

    def generate_commitment_proof(self, value: int, blinding: int, domain_tag: int) -> CommitmentProofResult:
        payload = self._module.generate_commitment_proof(value, blinding, domain_tag)
        return CommitmentProofResult(
            proof=str(payload["proof"]),
            public_inputs=str(payload["public_inputs"]),
            verification_key=str(payload["verification_key"]),
        )

    def verify_commitment_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return bool(self._module.verify_commitment_proof(proof_hex, vk_json, inputs_json))

    def prove_predicate(self, value: int, blinding: int, domain_tag: int, min_val: int, max_val: int) -> PredicateProofResult:
        payload = self._module.prove_predicate(value, blinding, domain_tag, min_val, max_val)
        return _normalize_predicate_result(payload)

    def prove_age_over(self, birth_year: int, current_year: int, min_age: int, blinding: int) -> PredicateProofResult:
        payload = self._module.prove_age_over(birth_year, current_year, min_age, blinding)
        return _normalize_predicate_result(payload)

    def verify_age_over(self, proof_hex: str, commitment_hex: str, min_age: int) -> bool:
        return bool(self._module.verify_age_over(proof_hex, commitment_hex, min_age))

    def prove_balance_above(self, balance: int, blinding: int, min_balance: int, max_balance: int) -> PredicateProofResult:
        payload = self._module.prove_balance_above(balance, blinding, min_balance, max_balance)
        return _normalize_predicate_result(payload)

    def verify_balance_above(self, proof_hex: str, commitment_hex: str, min_balance: int, max_balance: int) -> bool:
        return bool(self._module.verify_balance_above(proof_hex, commitment_hex, min_balance, max_balance))

    def prove_transfer(self, sender_pre: int, receiver_pre: int, amount: int) -> TransferProofResult:
        payload = self._module.prove_transfer(sender_pre, receiver_pre, amount)
        return TransferProofResult(
            proof=str(payload["proof"]),
            sender_pre=int(payload["sender_pre"]),
            receiver_pre=int(payload["receiver_pre"]),
            sender_post=int(payload["sender_post"]),
            receiver_post=int(payload["receiver_post"]),
        )

    def verify_transfer(self, proof_hex: str, inputs_json: str) -> bool:
        return bool(self._module.verify_transfer(proof_hex, inputs_json))

    def prove_merkle_verify(self, leaf_hash_hex: str, path_json: str) -> MerkleProofResult:
        payload = self._module.prove_merkle_verify(leaf_hash_hex, path_json)
        return MerkleProofResult(
            proof=str(payload["proof"]),
            leaf_hash=str(payload["leaf_hash"]),
            root=str(payload["root"]),
            depth=int(payload["depth"]),
        )

    def verify_merkle_proof(self, proof_hex: str, inputs_json: str) -> bool:
        return bool(self._module.verify_merkle_proof(proof_hex, inputs_json))


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

    async def generate_commitment_proof(self, value: int, blinding: int, domain_tag: int) -> CommitmentProofResult: ...

    async def verify_commitment_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool: ...

    async def prove_predicate(self, value: int, blinding: int, domain_tag: int, min_val: int, max_val: int) -> PredicateProofResult: ...

    async def prove_age_over(self, birth_year: int, current_year: int, min_age: int, blinding: int) -> PredicateProofResult: ...

    async def verify_age_over(self, proof_hex: str, commitment_hex: str, min_age: int) -> bool: ...

    async def prove_balance_above(self, balance: int, blinding: int, min_balance: int, max_balance: int) -> PredicateProofResult: ...

    async def verify_balance_above(self, proof_hex: str, commitment_hex: str, min_balance: int, max_balance: int) -> bool: ...

    async def prove_transfer(self, sender_pre: int, receiver_pre: int, amount: int) -> TransferProofResult: ...

    async def verify_transfer(self, proof_hex: str, inputs_json: str) -> bool: ...

    async def prove_merkle_verify(self, leaf_hash_hex: str, path_json: str) -> MerkleProofResult: ...

    async def verify_merkle_proof(self, proof_hex: str, inputs_json: str) -> bool: ...


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

    async def generate_commitment_proof(self, value: int, blinding: int, domain_tag: int) -> CommitmentProofResult:
        return await asyncio.to_thread(self._sync.generate_commitment_proof, value, blinding, domain_tag)

    async def verify_commitment_proof(self, proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return await asyncio.to_thread(self._sync.verify_commitment_proof, proof_hex, vk_json, inputs_json)

    async def prove_predicate(self, value: int, blinding: int, domain_tag: int, min_val: int, max_val: int) -> PredicateProofResult:
        return await asyncio.to_thread(self._sync.prove_predicate, value, blinding, domain_tag, min_val, max_val)

    async def prove_age_over(self, birth_year: int, current_year: int, min_age: int, blinding: int) -> PredicateProofResult:
        return await asyncio.to_thread(self._sync.prove_age_over, birth_year, current_year, min_age, blinding)

    async def verify_age_over(self, proof_hex: str, commitment_hex: str, min_age: int) -> bool:
        return await asyncio.to_thread(self._sync.verify_age_over, proof_hex, commitment_hex, min_age)

    async def prove_balance_above(self, balance: int, blinding: int, min_balance: int, max_balance: int) -> PredicateProofResult:
        return await asyncio.to_thread(self._sync.prove_balance_above, balance, blinding, min_balance, max_balance)

    async def verify_balance_above(self, proof_hex: str, commitment_hex: str, min_balance: int, max_balance: int) -> bool:
        return await asyncio.to_thread(self._sync.verify_balance_above, proof_hex, commitment_hex, min_balance, max_balance)

    async def prove_transfer(self, sender_pre: int, receiver_pre: int, amount: int) -> TransferProofResult:
        return await asyncio.to_thread(self._sync.prove_transfer, sender_pre, receiver_pre, amount)

    async def verify_transfer(self, proof_hex: str, inputs_json: str) -> bool:
        return await asyncio.to_thread(self._sync.verify_transfer, proof_hex, inputs_json)

    async def prove_merkle_verify(self, leaf_hash_hex: str, path_json: str) -> MerkleProofResult:
        return await asyncio.to_thread(self._sync.prove_merkle_verify, leaf_hash_hex, path_json)

    async def verify_merkle_proof(self, proof_hex: str, inputs_json: str) -> bool:
        return await asyncio.to_thread(self._sync.verify_merkle_proof, proof_hex, inputs_json)


def _normalize_proof_result(payload: Any) -> StarkProofResult:
    return StarkProofResult(
        proof=str(payload["proof"]),
        vk=str(payload["vk"]),
        inputs=str(payload["inputs"]),
    )


def _normalize_predicate_result(payload: Any) -> PredicateProofResult:
    return PredicateProofResult(
        proof=str(payload["proof"]),
        commitment=str(payload["commitment"]),
        min=int(payload["min"]),
        max=int(payload["max"]),
        domain_tag=int(payload["domain_tag"]),
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
