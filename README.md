# Dilithia Language SDKs

Server-side and CLI SDKs for the Dilithia network -- RPC, contracts, signing, messaging, and gas sponsorship.

**Version: 0.2.0** | [Documentation](https://dilithia.github.io/languages-sdk/)

## Supported Languages

| Language   | Package                     | Registry                                                                 |
| ---------- | --------------------------- | ------------------------------------------------------------------------ |
| TypeScript | `@dilithia/sdk-node`        | [npm](https://www.npmjs.com/package/@dilithia/sdk-node)                  |
| Python     | `dilithia-sdk`              | [PyPI](https://pypi.org/project/dilithia-sdk/)                           |
| Rust       | `dilithia-sdk-rust`         | [crates.io](https://crates.io/crates/dilithia-sdk-rust)                  |
| Go         | `github.com/dilithia/languages-sdk/go` | [pkg.go.dev](https://pkg.go.dev/github.com/dilithia/languages-sdk/go) |
| Java       | `org.dilithia:dilithia-sdk-java` | [Maven Central](https://central.sonatype.com/artifact/org.dilithia/dilithia-sdk-java) |

Optional native crypto bridges (key derivation and signing backed by `dilithia-core`):

| Package                  | Registry                                                                  |
| ------------------------ | ------------------------------------------------------------------------- |
| `@dilithia/sdk-native`   | [npm](https://www.npmjs.com/package/@dilithia/sdk-native)                 |
| `dilithia-sdk-native`    | [PyPI](https://pypi.org/project/dilithia-sdk-native/)                     |

## Installation

### TypeScript / Node.js

```bash
npm install @dilithia/sdk-node
# optional native crypto bridge
npm install @dilithia/sdk-native
```

### Python

```bash
pip install dilithia-sdk
# optional native crypto bridge
pip install dilithia-sdk-native
```

### Rust

```toml
[dependencies]
dilithia-sdk-rust = "0.2.0"
```

### Go

```bash
go get github.com/dilithia/languages-sdk/go@v0.2.0
```

### Java (Maven)

```xml
<dependency>
  <groupId>org.dilithia</groupId>
  <artifactId>dilithia-sdk-java</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Quick Example (TypeScript)

```typescript
import {
  createClient,
  loadNativeCryptoAdapter,
} from "@dilithia/sdk-node";

const client = createClient({ rpcUrl: "https://rpc.dilithia.network/rpc" });

// Load the native crypto bridge for wallet operations
const crypto = await loadNativeCryptoAdapter();

// Generate a new wallet from a mnemonic
const mnemonic = await crypto.generateMnemonic();
const account = await crypto.recoverHdWallet(mnemonic);
console.log("Address:", account.address);

// Check balance
const balance = await client.getBalance(account.address);
console.log("Balance:", balance);

// Sign and submit a contract call
const call = client.buildContractCall("contract_addr", "transfer", {
  to: "recipient_addr",
  amount: 100,
});
const result = await client.sendSignedCall(call, {
  signCanonicalPayload: (payload) => crypto.signMessage(account.secretKey, payload),
});
const receipt = await client.waitForReceipt(result.tx_hash as string);
console.log("Receipt:", receipt);
```

## Architecture

```
dilithia-core (Rust)
    |
    +-- dilithia-sdk-rust (direct Rust adapter)
    |
    +-- native bridges (napi-rs / pyo3 / C ABI)
    |       |
    |       +-- @dilithia/sdk-native  (Node.js)
    |       +-- dilithia-sdk-native   (Python)
    |       +-- native-core           (Go, Java via JNA)
    |
    +-- language SDKs (RPC client + optional native crypto)
            +-- @dilithia/sdk-node
            +-- dilithia-sdk
            +-- languages-sdk/go
            +-- dilithia-sdk-java
```

All cryptographic operations (key derivation, signing, verification) are implemented
once in `dilithia-core` and exposed to each language through thin native bridges.
The language SDKs provide the RPC client, contract helpers, gas sponsorship, and
cross-chain messaging on top.

## Documentation

Full documentation for each language SDK is available in the `docs/` directory
within each language folder, and on the
[Dilithia documentation site](https://docs.dilithia.network).

## License

Licensed under either of [MIT](https://opensource.org/licenses/MIT) or
[Apache-2.0](https://opensource.org/licenses/Apache-2.0), at your option.
