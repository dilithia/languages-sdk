from __future__ import annotations

import asyncio
from dataclasses import dataclass
from importlib import import_module
from typing import Any, Protocol


WalletFile = dict[str, Any]


@dataclass(slots=True)
class DilithiaAccount:
    address: str
    public_key: str
    secret_key: str
    account_index: int
    wallet_file: WalletFile | None = None


@dataclass(slots=True)
class DilithiaSignature:
    algorithm: str
    signature: str


@dataclass(slots=True)
class DilithiaKeypair:
    secret_key: str
    public_key: str
    address: str


class DilithiaCryptoAdapter(Protocol):
    def generate_mnemonic(self) -> str: ...

    def validate_mnemonic(self, mnemonic: str) -> None: ...

    def recover_hd_wallet(self, mnemonic: str) -> DilithiaAccount: ...

    def recover_hd_wallet_account(self, mnemonic: str, account_index: int) -> DilithiaAccount: ...

    def create_hd_wallet_file_from_mnemonic(self, mnemonic: str, password: str) -> DilithiaAccount: ...

    def create_hd_wallet_account_from_mnemonic(
        self, mnemonic: str, password: str, account_index: int
    ) -> DilithiaAccount: ...

    def recover_wallet_file(self, wallet_file: WalletFile, mnemonic: str, password: str) -> DilithiaAccount: ...

    def address_from_public_key(self, public_key_hex: str) -> str: ...

    def validate_address(self, addr: str) -> str: ...

    def address_from_pk_checksummed(self, public_key_hex: str) -> str: ...

    def address_with_checksum(self, raw_addr: str) -> str: ...

    def validate_public_key(self, public_key_hex: str) -> None: ...

    def validate_secret_key(self, secret_key_hex: str) -> None: ...

    def validate_signature(self, signature_hex: str) -> None: ...

    def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature: ...

    def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool: ...

    def keygen(self) -> DilithiaKeypair: ...

    def keygen_from_seed(self, seed_hex: str) -> DilithiaKeypair: ...

    def seed_from_mnemonic(self, mnemonic: str) -> str: ...

    def derive_child_seed(self, parent_seed_hex: str, index: int) -> str: ...

    def constant_time_eq(self, a_hex: str, b_hex: str) -> bool: ...

    def hash_hex(self, data_hex: str) -> str: ...

    def set_hash_alg(self, alg: str) -> None: ...

    def current_hash_alg(self) -> str: ...

    def hash_len_hex(self) -> int: ...


class NativeCryptoAdapter:
    def __init__(self, module: Any) -> None:
        self._module = module

    def generate_mnemonic(self) -> str:
        return self._module.generate_mnemonic()

    def validate_mnemonic(self, mnemonic: str) -> None:
        self._module.validate_mnemonic(mnemonic)

    def recover_hd_wallet(self, mnemonic: str) -> DilithiaAccount:
        payload = self._module.recover_hd_wallet(mnemonic)
        return _normalize_account(payload)

    def recover_hd_wallet_account(self, mnemonic: str, account_index: int) -> DilithiaAccount:
        payload = self._module.recover_hd_wallet_account(mnemonic, account_index)
        return _normalize_account(payload)

    def create_hd_wallet_file_from_mnemonic(self, mnemonic: str, password: str) -> DilithiaAccount:
        payload = self._module.create_hd_wallet_file_from_mnemonic(mnemonic, password)
        return _normalize_account(payload)

    def create_hd_wallet_account_from_mnemonic(
        self, mnemonic: str, password: str, account_index: int
    ) -> DilithiaAccount:
        payload = self._module.create_hd_wallet_account_from_mnemonic(mnemonic, password, account_index)
        return _normalize_account(payload)

    def recover_wallet_file(self, wallet_file: WalletFile, mnemonic: str, password: str) -> DilithiaAccount:
        payload = self._module.recover_wallet_file(
            int(wallet_file.get("version", 1)),
            str(wallet_file.get("address", "")),
            str(wallet_file.get("public_key", wallet_file.get("publicKey", ""))),
            str(wallet_file.get("encrypted_sk", wallet_file.get("encryptedSk", ""))),
            str(wallet_file.get("nonce", "")),
            str(wallet_file.get("tag", "")),
            mnemonic,
            password,
            (
                int(wallet_file["account_index"])
                if wallet_file.get("account_index") is not None
                else None
            ),
        )
        return _normalize_account(payload)

    def address_from_public_key(self, public_key_hex: str) -> str:
        return self._module.address_from_public_key(public_key_hex)

    def validate_address(self, addr: str) -> str:
        return self._module.validate_address(addr)

    def address_from_pk_checksummed(self, public_key_hex: str) -> str:
        return self._module.address_from_pk_checksummed(public_key_hex)

    def address_with_checksum(self, raw_addr: str) -> str:
        return self._module.address_with_checksum(raw_addr)

    def validate_public_key(self, public_key_hex: str) -> None:
        self._module.validate_public_key(public_key_hex)

    def validate_secret_key(self, secret_key_hex: str) -> None:
        self._module.validate_secret_key(secret_key_hex)

    def validate_signature(self, signature_hex: str) -> None:
        self._module.validate_signature(signature_hex)

    def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature:
        payload = self._module.sign_message(secret_key_hex, message)
        return DilithiaSignature(
            algorithm=str(payload["algorithm"]),
            signature=str(payload["signature"]),
        )

    def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool:
        return bool(self._module.verify_message(public_key_hex, message, signature_hex))

    def keygen(self) -> DilithiaKeypair:
        payload = self._module.keygen()
        return DilithiaKeypair(
            secret_key=str(payload["secret_key"]),
            public_key=str(payload["public_key"]),
            address=str(payload["address"]),
        )

    def keygen_from_seed(self, seed_hex: str) -> DilithiaKeypair:
        payload = self._module.keygen_from_seed(seed_hex)
        return DilithiaKeypair(
            secret_key=str(payload["secret_key"]),
            public_key=str(payload["public_key"]),
            address=str(payload["address"]),
        )

    def seed_from_mnemonic(self, mnemonic: str) -> str:
        return self._module.seed_from_mnemonic(mnemonic)

    def derive_child_seed(self, parent_seed_hex: str, index: int) -> str:
        return self._module.derive_child_seed(parent_seed_hex, index)

    def constant_time_eq(self, a_hex: str, b_hex: str) -> bool:
        return bool(self._module.constant_time_eq(a_hex, b_hex))

    def hash_hex(self, data_hex: str) -> str:
        return self._module.hash_hex(data_hex)

    def set_hash_alg(self, alg: str) -> None:
        self._module.set_hash_alg(alg)

    def current_hash_alg(self) -> str:
        return self._module.current_hash_alg()

    def hash_len_hex(self) -> int:
        return int(self._module.hash_len_hex())


def _normalize_account(payload: Any) -> DilithiaAccount:
    return DilithiaAccount(
        address=str(payload["address"]),
        public_key=str(payload["public_key"]),
        secret_key=str(payload["secret_key"]),
        account_index=int(payload.get("account_index", 0)),
        wallet_file=payload.get("wallet_file"),
    )


def load_native_crypto_adapter() -> DilithiaCryptoAdapter | None:
    try:
        module = import_module("dilithia_sdk_native")
    except ImportError:
        return None
    return NativeCryptoAdapter(module)


# ---------------------------------------------------------------------------
# Async variants
# ---------------------------------------------------------------------------


class AsyncDilithiaCryptoAdapter(Protocol):
    async def generate_mnemonic(self) -> str: ...

    async def validate_mnemonic(self, mnemonic: str) -> None: ...

    async def recover_hd_wallet(self, mnemonic: str) -> DilithiaAccount: ...

    async def recover_hd_wallet_account(self, mnemonic: str, account_index: int) -> DilithiaAccount: ...

    async def create_hd_wallet_file_from_mnemonic(self, mnemonic: str, password: str) -> DilithiaAccount: ...

    async def create_hd_wallet_account_from_mnemonic(
        self, mnemonic: str, password: str, account_index: int
    ) -> DilithiaAccount: ...

    async def recover_wallet_file(self, wallet_file: WalletFile, mnemonic: str, password: str) -> DilithiaAccount: ...

    async def address_from_public_key(self, public_key_hex: str) -> str: ...

    async def validate_address(self, addr: str) -> str: ...

    async def address_from_pk_checksummed(self, public_key_hex: str) -> str: ...

    async def address_with_checksum(self, raw_addr: str) -> str: ...

    async def validate_public_key(self, public_key_hex: str) -> None: ...

    async def validate_secret_key(self, secret_key_hex: str) -> None: ...

    async def validate_signature(self, signature_hex: str) -> None: ...

    async def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature: ...

    async def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool: ...

    async def keygen(self) -> DilithiaKeypair: ...

    async def keygen_from_seed(self, seed_hex: str) -> DilithiaKeypair: ...

    async def seed_from_mnemonic(self, mnemonic: str) -> str: ...

    async def derive_child_seed(self, parent_seed_hex: str, index: int) -> str: ...

    async def constant_time_eq(self, a_hex: str, b_hex: str) -> bool: ...

    async def hash_hex(self, data_hex: str) -> str: ...

    async def set_hash_alg(self, alg: str) -> None: ...

    async def current_hash_alg(self) -> str: ...

    async def hash_len_hex(self) -> int: ...


class AsyncNativeCryptoAdapter:
    """Wraps a synchronous :class:`NativeCryptoAdapter` and delegates every
    call to a thread executor via :func:`asyncio.to_thread` so that
    CPU-intensive native crypto work never blocks the event loop.
    """

    def __init__(self, sync_adapter: NativeCryptoAdapter) -> None:
        self._sync = sync_adapter

    async def generate_mnemonic(self) -> str:
        return await asyncio.to_thread(self._sync.generate_mnemonic)

    async def validate_mnemonic(self, mnemonic: str) -> None:
        await asyncio.to_thread(self._sync.validate_mnemonic, mnemonic)

    async def recover_hd_wallet(self, mnemonic: str) -> DilithiaAccount:
        return await asyncio.to_thread(self._sync.recover_hd_wallet, mnemonic)

    async def recover_hd_wallet_account(self, mnemonic: str, account_index: int) -> DilithiaAccount:
        return await asyncio.to_thread(self._sync.recover_hd_wallet_account, mnemonic, account_index)

    async def create_hd_wallet_file_from_mnemonic(self, mnemonic: str, password: str) -> DilithiaAccount:
        return await asyncio.to_thread(self._sync.create_hd_wallet_file_from_mnemonic, mnemonic, password)

    async def create_hd_wallet_account_from_mnemonic(
        self, mnemonic: str, password: str, account_index: int
    ) -> DilithiaAccount:
        return await asyncio.to_thread(
            self._sync.create_hd_wallet_account_from_mnemonic, mnemonic, password, account_index
        )

    async def recover_wallet_file(self, wallet_file: WalletFile, mnemonic: str, password: str) -> DilithiaAccount:
        return await asyncio.to_thread(self._sync.recover_wallet_file, wallet_file, mnemonic, password)

    async def address_from_public_key(self, public_key_hex: str) -> str:
        return await asyncio.to_thread(self._sync.address_from_public_key, public_key_hex)

    async def validate_address(self, addr: str) -> str:
        return await asyncio.to_thread(self._sync.validate_address, addr)

    async def address_from_pk_checksummed(self, public_key_hex: str) -> str:
        return await asyncio.to_thread(self._sync.address_from_pk_checksummed, public_key_hex)

    async def address_with_checksum(self, raw_addr: str) -> str:
        return await asyncio.to_thread(self._sync.address_with_checksum, raw_addr)

    async def validate_public_key(self, public_key_hex: str) -> None:
        await asyncio.to_thread(self._sync.validate_public_key, public_key_hex)

    async def validate_secret_key(self, secret_key_hex: str) -> None:
        await asyncio.to_thread(self._sync.validate_secret_key, secret_key_hex)

    async def validate_signature(self, signature_hex: str) -> None:
        await asyncio.to_thread(self._sync.validate_signature, signature_hex)

    async def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature:
        return await asyncio.to_thread(self._sync.sign_message, secret_key_hex, message)

    async def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool:
        return await asyncio.to_thread(self._sync.verify_message, public_key_hex, message, signature_hex)

    async def keygen(self) -> DilithiaKeypair:
        return await asyncio.to_thread(self._sync.keygen)

    async def keygen_from_seed(self, seed_hex: str) -> DilithiaKeypair:
        return await asyncio.to_thread(self._sync.keygen_from_seed, seed_hex)

    async def seed_from_mnemonic(self, mnemonic: str) -> str:
        return await asyncio.to_thread(self._sync.seed_from_mnemonic, mnemonic)

    async def derive_child_seed(self, parent_seed_hex: str, index: int) -> str:
        return await asyncio.to_thread(self._sync.derive_child_seed, parent_seed_hex, index)

    async def constant_time_eq(self, a_hex: str, b_hex: str) -> bool:
        return await asyncio.to_thread(self._sync.constant_time_eq, a_hex, b_hex)

    async def hash_hex(self, data_hex: str) -> str:
        return await asyncio.to_thread(self._sync.hash_hex, data_hex)

    async def set_hash_alg(self, alg: str) -> None:
        await asyncio.to_thread(self._sync.set_hash_alg, alg)

    async def current_hash_alg(self) -> str:
        return await asyncio.to_thread(self._sync.current_hash_alg)

    async def hash_len_hex(self) -> int:
        return await asyncio.to_thread(self._sync.hash_len_hex)


def load_async_native_crypto_adapter() -> AsyncDilithiaCryptoAdapter | None:
    try:
        module = import_module("dilithia_sdk_native")
    except ImportError:
        return None
    return AsyncNativeCryptoAdapter(NativeCryptoAdapter(module))
