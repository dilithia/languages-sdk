# Sync vs Async Adapters

The TypeScript and Python SDKs each provide both synchronous and asynchronous variants of the crypto adapter and the RPC client. This guide explains when to use each and how they differ.

---

## Overview

| Language       | Sync Adapter                      | Async Adapter                     |
| -------------- | --------------------------------- | --------------------------------- |
| **TypeScript** | `SyncDilithiaCryptoAdapter`       | `DilithiaCryptoAdapter`           |
| **Python**     | `DilithiaCryptoAdapter`           | `AsyncDilithiaCryptoAdapter`      |
| **Python**     | `DilithiaClient`                  | `AsyncDilithiaClient`             |

!!! note
    Rust and Go are inherently synchronous at the adapter level (Rust uses `Result`, Go returns errors). Async I/O in those languages is handled at the HTTP transport layer, not at the SDK interface level.

!!! note "C# / .NET"
    The C# SDK is **async-first**. All client methods use `async`/`await` and return `Task<T>`, following standard .NET conventions. The crypto bridge (`NativeCryptoBridge`) methods are synchronous since they are CPU-bound P/Invoke calls, but client I/O operations (`GetNonceAsync`, `SendSignedCallAsync`, `WaitForReceiptAsync`, etc.) are fully asynchronous.

---

## TypeScript

### Async Adapter (Default)

The primary `DilithiaCryptoAdapter` interface is async. Every method returns a `Promise`. This is the recommended choice for most applications.

```typescript
import { loadNativeCryptoAdapter, DilithiaCryptoAdapter } from "@dilithia/sdk";

const crypto: DilithiaCryptoAdapter | null = await loadNativeCryptoAdapter();
if (!crypto) throw new Error("Native bridge not available");

// All methods are async
const mnemonic: string = await crypto.generateMnemonic();
const account = await crypto.recoverHdWallet(mnemonic);
const sig = await crypto.signMessage(account.secretKey, "hello");
```

**When to use:** Web servers, any code already running in an async context, applications where you do not want to block the main thread.

### Sync Adapter

The `SyncDilithiaCryptoAdapter` provides the same methods but returns values directly (no `Promise`). It uses `createRequire` to load the native module synchronously.

```typescript
import { loadSyncNativeCryptoAdapter, SyncDilithiaCryptoAdapter } from "@dilithia/sdk";

const crypto: SyncDilithiaCryptoAdapter | null = loadSyncNativeCryptoAdapter();
if (!crypto) throw new Error("Native bridge not available");

// All methods return directly
const mnemonic: string = crypto.generateMnemonic();
const account = crypto.recoverHdWallet(mnemonic);
const sig = crypto.signMessage(account.secretKey, "hello");
```

**When to use:** CLI tools, scripts, build steps, or any context where blocking is acceptable and simpler code is preferred.

!!! warning
    The sync adapter blocks the Node.js event loop during cryptographic operations. Do not use it in request handlers of web servers or any latency-sensitive async code path.

### How It Works

Both adapters delegate to the same native module (`@dilithia/sdk-native`). The difference is purely in the loading mechanism and return types:

- **Async**: Uses dynamic `import()` to load the native module, wraps each call in an `async` function
- **Sync**: Uses `createRequire` to load the native module via CommonJS `require()`, returns values directly

---

## Python

### Sync Client and Adapter

The default Python classes are synchronous, using `httpx` (synchronous mode) for HTTP and direct function calls for crypto.

```python
from dilithia_sdk import DilithiaClient
from dilithia_sdk.crypto import load_native_crypto_adapter

# Sync client
client = DilithiaClient("https://rpc.dilithia.network/rpc")
balance = client.get_balance("dili1abc...")

# Sync crypto adapter
crypto = load_native_crypto_adapter()
mnemonic = crypto.generate_mnemonic()
account = crypto.recover_hd_wallet(mnemonic)
```

**When to use:** Scripts, CLI tools, Django views, or any synchronous codebase.

### Async Client

The `AsyncDilithiaClient` provides the same methods as `DilithiaClient`, but every I/O method is `async`. In v0.3.0, the async client uses **httpx** for real async HTTP -- no longer delegating to `asyncio.to_thread` on urllib. This means HTTP requests are truly non-blocking and run on the event loop directly.

```python
from dilithia_sdk import AsyncDilithiaClient

client = AsyncDilithiaClient("https://rpc.dilithia.network/rpc")
balance = await client.get_balance("dili1abc...")
receipt = await client.wait_for_receipt("0xabc...")

# Clean up when done
await client.aclose()
```

**When to use:** FastAPI, aiohttp, or any `asyncio`-based application.

### Async Crypto Adapter

The `AsyncNativeCryptoAdapter` wraps the sync adapter and delegates every call to `asyncio.to_thread`, so CPU-intensive native crypto work never blocks the event loop.

```python
from dilithia_sdk.crypto import load_async_native_crypto_adapter

crypto = load_async_native_crypto_adapter()
mnemonic = await crypto.generate_mnemonic()
account = await crypto.recover_hd_wallet(mnemonic)
sig = await crypto.sign_message(account.secret_key, "hello")
```

**When to use:** When your application is async and you want to keep the event loop responsive during key generation or signing.

### How It Works

```
AsyncNativeCryptoAdapter
    |
    +-- asyncio.to_thread() -->  NativeCryptoAdapter
                                      |
                                      +-- dilithia_sdk_native (PyO3 module)
                                              |
                                              +-- dilithia-core (Rust)
```

The async wrapper adds no overhead beyond the thread dispatch. The actual cryptographic work runs in the same native code regardless of sync or async usage.

---

## Decision Guide

| Scenario                                | Recommended Approach              |
| --------------------------------------- | --------------------------------- |
| Node.js web server (Express, Fastify)   | TypeScript async adapter          |
| Node.js CLI tool or script              | TypeScript sync adapter           |
| Python FastAPI / aiohttp service        | `AsyncDilithiaClient` + `AsyncNativeCryptoAdapter` |
| Python script or Django view            | `DilithiaClient` + sync adapter   |
| Rust service                            | Direct `NativeCryptoAdapter`      |
| Go service                              | Direct adapter (inherently sync)  |
| C# / .NET service (ASP.NET, etc.)      | Async client + sync `NativeCryptoBridge` |

!!! tip
    If you are unsure, start with the async variant. It is always safe to `await` in an async context, and most modern frameworks are async-first.

---

## Exception Hierarchy (v0.3.0)

Both the sync and async Python clients raise structured exceptions from `dilithia_sdk.errors`:

```python
from dilithia_sdk.errors import DilithiaError, RpcError, HttpError, TimeoutError

try:
    balance = client.get_balance("dili1abc...")
except RpcError as e:
    print(f"RPC error {e.code}: {e}")
except HttpError as e:
    print(f"HTTP {e.status}: {e}")
except TimeoutError:
    print("Request timed out")
except DilithiaError as e:
    print(f"SDK error: {e}")
```

All SDK errors inherit from `DilithiaError`, so you can catch the base class to handle any SDK error generically. The same hierarchy applies to both `DilithiaClient` and `AsyncDilithiaClient`.
