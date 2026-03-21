"""Dilithia Python SDK.

Provides typed, httpx-backed clients for interacting with Dilithia nodes,
along with a crypto adapter protocol for the optional native Rust bridge.
"""

from .client import (
    AsyncDilithiaClient,
    DilithiaClient,
    DilithiaGasSponsorConnector,
    DilithiaMessagingConnector,
    read_wasm_file_hex,
)
from .crypto import (
    AsyncDilithiaCryptoAdapter,
    AsyncNativeCryptoAdapter,
    DilithiaAccount,
    DilithiaCryptoAdapter,
    DilithiaKeypair,
    DilithiaSignature,
    NativeCryptoAdapter,
    load_async_native_crypto_adapter,
    load_native_crypto_adapter,
)
from .exceptions import (
    CryptoError,
    DilithiaError,
    HttpError,
    RpcError,
    TimeoutError,
    ValidationError,
)
from .models import (
    Address,
    Balance,
    GasEstimate,
    NameRecord,
    NetworkInfo,
    Nonce,
    QueryResult,
    Receipt,
    TokenAmount,
    TxHash,
)
from .validation import (
    BytecodeValidation,
    estimate_deploy_gas,
    validate_bytecode,
)
from .zk import (
    AsyncDilithiaZkAdapter,
    AsyncNativeZkAdapter,
    Commitment,
    DilithiaZkAdapter,
    NativeZkAdapter,
    Nullifier,
    StarkProofResult,
    load_async_zk_adapter,
    load_zk_adapter,
)

__version__ = "0.3.0"
RPC_LINE_VERSION = "0.3.0"
MIN_PYTHON = (3, 11)

__all__ = [
    # Client
    "AsyncDilithiaClient",
    "DilithiaClient",
    "DilithiaGasSponsorConnector",
    "DilithiaMessagingConnector",
    "read_wasm_file_hex",
    # Models
    "Address",
    "Balance",
    "GasEstimate",
    "NameRecord",
    "NetworkInfo",
    "Nonce",
    "QueryResult",
    "Receipt",
    "TokenAmount",
    "TxHash",
    # Exceptions
    "CryptoError",
    "DilithiaError",
    "HttpError",
    "RpcError",
    "TimeoutError",
    "ValidationError",
    # Crypto
    "AsyncDilithiaCryptoAdapter",
    "AsyncNativeCryptoAdapter",
    "DilithiaAccount",
    "DilithiaCryptoAdapter",
    "DilithiaKeypair",
    "DilithiaSignature",
    "NativeCryptoAdapter",
    "load_async_native_crypto_adapter",
    "load_native_crypto_adapter",
    # Validation
    "BytecodeValidation",
    "validate_bytecode",
    "estimate_deploy_gas",
    # ZK
    "AsyncDilithiaZkAdapter",
    "AsyncNativeZkAdapter",
    "Commitment",
    "DilithiaZkAdapter",
    "NativeZkAdapter",
    "Nullifier",
    "StarkProofResult",
    "load_async_zk_adapter",
    "load_zk_adapter",
]
