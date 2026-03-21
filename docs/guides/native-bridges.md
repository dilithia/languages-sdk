# Native Crypto Bridges

The Dilithia SDKs use `dilithia-core` (a Rust library) as the single source of truth for all cryptographic operations. Rather than reimplementing ML-DSA-65 key derivation, signing, and address logic in each language, native bridges expose the Rust implementation directly. This page explains how each bridge works and how to set it up.

---

## Architecture Overview

```
dilithia-core (Rust)
    |
    +--- NAPI-RS ---------> @dilithia/sdk-native (Node.js)
    |
    +--- PyO3 ------------> dilithia-sdk-native (Python)
    |
    +--- C ABI (FFI) -----> native-core shared library
              |
              +--- cgo -------> Go SDK
              +--- JNI -------> Java SDK
              +--- P/Invoke --> C# SDK
```

All bridges compile `dilithia-core` into a platform-native shared library. The difference is the foreign function interface used to cross the language boundary.

---

## Node.js (NAPI-RS)

The TypeScript SDK uses [NAPI-RS](https://napi.rs) to compile `dilithia-core` into a native Node.js addon (`.node` file).

### Installation

```bash
npm install @dilithia/sdk-native
```

The package ships prebuilt binaries for common platforms. If no prebuilt binary is available, it will attempt to compile from source (requires Rust toolchain).

### Usage

The SDK automatically detects and loads the native bridge:

```typescript
import { loadNativeCryptoAdapter } from "@dilithia/sdk";

const crypto = await loadNativeCryptoAdapter();
// Returns null if the native bridge is not installed
```

For synchronous usage:

```typescript
import { loadSyncNativeCryptoAdapter } from "@dilithia/sdk";

const crypto = loadSyncNativeCryptoAdapter();
```

### How It Works

1. `@dilithia/sdk-native` compiles `dilithia-core` via NAPI-RS into a `.node` addon
2. The addon exports snake_case functions (`generate_mnemonic`, `sign_message`, etc.)
3. The SDK's `loadNativeCryptoAdapter()` dynamically imports the addon and wraps each function in the `DilithiaCryptoAdapter` interface
4. Field names are normalized from snake_case to camelCase automatically

### Building from Source

```bash
cd typescript/native
npm install
npm run build
```

Or using the containerized build:

```bash
./scripts/build-typescript-native-docker.sh
```

---

## Python (PyO3)

The Python SDK uses [PyO3](https://pyo3.rs) and [Maturin](https://maturin.rs) to compile `dilithia-core` into a native Python extension module.

### Installation

```bash
pip install dilithia-sdk-native
```

Prebuilt wheels are available for common platforms. Falls back to source build if needed.

### Usage

```python
from dilithia_sdk.crypto import load_native_crypto_adapter

crypto = load_native_crypto_adapter()
# Returns None if the native bridge is not installed
```

For async usage:

```python
from dilithia_sdk.crypto import load_async_native_crypto_adapter

crypto = load_async_native_crypto_adapter()
mnemonic = await crypto.generate_mnemonic()
```

### How It Works

1. `dilithia-sdk-native` compiles `dilithia-core` via PyO3 into a `.so` / `.pyd` extension module
2. The module is importable as `dilithia_sdk_native`
3. `load_native_crypto_adapter()` calls `importlib.import_module("dilithia_sdk_native")` and wraps the module in a `NativeCryptoAdapter` class
4. The `NativeCryptoAdapter` handles type normalization (dict to `DilithiaAccount`, etc.)

### Building from Source

```bash
cd python/native
pip install maturin
maturin develop
```

Or using the containerized build:

```bash
./scripts/build-python-native-docker.sh
```

---

## Go (C ABI / cgo)

The Go SDK uses the `native-core` shared library, which exposes `dilithia-core` through a C-compatible ABI.

### Setup

1. Build the native-core shared library:

    ```bash
    cd native-core
    cargo build --release
    ```

    This produces `libdilithia_core.so` (Linux), `libdilithia_core.dylib` (macOS), or `dilithia_core.dll` (Windows).

2. Set the environment variable pointing to the library:

    ```bash
    export DILITHIA_NATIVE_LIB=/path/to/libdilithia_core.so
    ```

    Alternatively, place the library in a standard search path (`/usr/local/lib`, etc.) or set `LD_LIBRARY_PATH`.

3. The Go SDK will load the library via cgo at runtime.

### Environment Variables

| Variable              | Description                                          |
| --------------------- | ---------------------------------------------------- |
| `DILITHIA_NATIVE_LIB` | Full path to the `native-core` shared library        |
| `LD_LIBRARY_PATH`     | (Linux) Additional library search paths              |
| `DYLD_LIBRARY_PATH`   | (macOS) Additional library search paths              |

!!! warning
    If the native library is not found at runtime, the Go SDK will return an error when attempting to use crypto operations. The RPC client still works without native crypto.

---

## Java (C ABI / JNI)

The Java SDK also uses the `native-core` shared library, accessed through JNI (Java Native Interface).

### Setup

1. Build the native-core shared library (same as Go):

    ```bash
    cd native-core
    cargo build --release
    ```

2. Set the library path for Java:

    ```bash
    export DILITHIA_NATIVE_LIB=/path/to/libdilithia_core.so
    ```

    Or pass it as a JVM argument:

    ```bash
    java -Djava.library.path=/path/to/native-core/target/release -jar myapp.jar
    ```

3. Add the native SDK dependency:

    ```xml
    <dependency>
      <groupId>org.dilithia</groupId>
      <artifactId>sdk-native</artifactId>
      <version>0.3.0</version>
    </dependency>
    ```

### Environment Variables

| Variable                | Description                                        |
| ----------------------- | -------------------------------------------------- |
| `DILITHIA_NATIVE_LIB`   | Full path to the `native-core` shared library      |
| `java.library.path`     | JVM system property for native library search path |

---

## C# (C ABI / P/Invoke)

The C# SDK uses the `native-core` shared library, accessed through [P/Invoke](https://learn.microsoft.com/en-us/dotnet/standard/native-interop/pinvoke) (Platform Invocation Services).

### Setup

1. Build the native-core shared library (same as Go and Java):

    ```bash
    cd native-core
    cargo build --release
    ```

2. Set the library path for the .NET runtime:

    ```bash
    export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithia_core.so
    ```

    Or place the library alongside your application binary, or in a standard search path.

3. Add the SDK NuGet package:

    ```bash
    dotnet add package Dilithia.Sdk --version 0.3.0
    ```

### Usage

```csharp
using Dilithia.Sdk.Crypto;

var crypto = new NativeCryptoBridge();
var mnemonic = crypto.GenerateMnemonic();
var account = crypto.RecoverHdWallet(mnemonic);
```

### How It Works

1. `NativeCryptoBridge` declares `[DllImport]` attributes pointing to `dilithia_native_core`
2. The .NET runtime resolves the shared library using the `DILITHIUM_NATIVE_CORE_LIB` environment variable or standard OS library search paths
3. Each P/Invoke call marshals arguments to the C ABI and unmarshals return values into .NET types (`DilithiaAccount`, `DilithiaSignature`, etc.)

### Environment Variables

| Variable                    | Description                                        |
| --------------------------- | -------------------------------------------------- |
| `DILITHIUM_NATIVE_CORE_LIB` | Full path to the `native-core` shared library      |
| `LD_LIBRARY_PATH`           | (Linux) Additional library search paths            |
| `DYLD_LIBRARY_PATH`         | (macOS) Additional library search paths            |

---

## Fallback Behavior

All SDKs are designed to work without the native bridge installed. The bridge loading functions return `null` / `None` when the native module is unavailable, rather than throwing an error.

This allows applications to:

- Check for native crypto availability at startup
- Fall back to alternative implementations if needed
- Use the RPC client features without installing native dependencies

```typescript
const crypto = await loadNativeCryptoAdapter();
if (!crypto) {
  console.warn("Native crypto not available, some features will be disabled");
}
```

---

## Supported Platforms

Native bridges are built and tested for these platforms:

| Platform              | Node.js (NAPI-RS) | Python (PyO3) | C ABI (Go/Java/C#) |
| --------------------- | :----------------: | :-----------: | :-----------------: |
| Linux x86_64          | Yes                | Yes           | Yes                 |
| Linux aarch64         | Yes                | Yes           | Yes                 |
| macOS x86_64          | Yes                | Yes           | Yes                 |
| macOS aarch64 (Apple Silicon) | Yes        | Yes           | Yes                 |
| Windows x86_64        | Yes                | Yes           | Yes                 |

!!! tip
    If you need a platform not listed here, you can build from source using the Rust toolchain for your target.
