# Dilithia Python SDK

Python SDK for the Dilithia RPC surface.

Current SDK line: `0.3.0`

## Install

```bash
pip install dilithia-sdk
```

`httpx` is installed automatically. For the optional Rust-backed crypto bridge:

```bash
pip install dilithia-sdk[native]
```

## Quick start (sync)

```python
from dilithia_sdk import DilithiaClient

with DilithiaClient("https://rpc.example/rpc") as client:
    bal = client.balance("alice")
    print(f"{bal.address}: {bal.balance.formatted()} DILI")

    info = client.network_info()
    print(f"chain={info.chain_id} height={info.block_height}")
```

## Quick start (async)

```python
import asyncio
from dilithia_sdk import AsyncDilithiaClient

async def main():
    async with AsyncDilithiaClient("https://rpc.example/rpc") as client:
        bal = await client.balance("alice")
        print(f"{bal.address}: {bal.balance.formatted()} DILI")

asyncio.run(main())
```

## Typed responses

All read methods return frozen dataclasses:

- `Balance` with `address: Address` and `balance: TokenAmount`
- `Nonce` with `address: Address` and `next_nonce: int`
- `Receipt` with `tx_hash: TxHash`, `block_height`, `status`, etc.
- `NetworkInfo` with `chain_id`, `block_height`, `base_fee`
- `GasEstimate` with `gas_limit`, `base_fee`, `estimated_cost`
- `NameRecord` with `name` and `address: Address`
- `QueryResult` wrapping any contract query return value

Token amounts use `Decimal` to avoid floating-point issues:

```python
from dilithia_sdk import TokenAmount

t = TokenAmount.dili("1.5")
raw = t.to_raw()        # 1500000000000000000
back = TokenAmount.from_raw(raw)
print(back.formatted())  # 1.500000000000000000
```

## Exception hierarchy

All exceptions inherit from `DilithiaError`:

- `RpcError(code, message)` -- JSON-RPC error
- `HttpError(status_code, body)` -- non-2xx HTTP response
- `TimeoutError` -- request or polling timeout
- `CryptoError` -- native crypto failures
- `ValidationError` -- invalid input

## Gas sponsor

```python
from dilithia_sdk import DilithiaClient, DilithiaGasSponsorConnector

client = DilithiaClient("https://rpc.example/rpc")
sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor", paymaster="gas_sponsor")
call = client.build_contract_call("wasm:amm", "swap", {"amount": 100})
sponsor.send_sponsored_call(call, signer)
```

## Crypto adapter

```python
from dilithia_sdk import load_native_crypto_adapter

crypto = load_native_crypto_adapter()
if crypto:
    mnemonic = crypto.generate_mnemonic()
    account = crypto.recover_hd_wallet(mnemonic)
```

## Commands

```bash
python -m pip install -e .
python -m unittest discover -s tests
python -m build
```

Requires Python 3.11--3.14.
