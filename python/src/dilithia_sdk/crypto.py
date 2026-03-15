from __future__ import annotations

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

    def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature: ...

    def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool: ...


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

    def sign_message(self, secret_key_hex: str, message: str) -> DilithiaSignature:
        payload = self._module.sign_message(secret_key_hex, message)
        return DilithiaSignature(
            algorithm=str(payload["algorithm"]),
            signature=str(payload["signature"]),
        )

    def verify_message(self, public_key_hex: str, message: str, signature_hex: str) -> bool:
        return bool(self._module.verify_message(public_key_hex, message, signature_hex))


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
        module = import_module("dilithia_sdk_python_crypto")
    except ImportError:
        return None
    return NativeCryptoAdapter(module)
