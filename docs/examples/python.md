# Python Examples

Complete, runnable Python scripts demonstrating common Dilithia SDK workflows. Each scenario is self-contained and covers a realistic use case from start to finish.

## Prerequisites

```bash
pip install dilithia-sdk dilithia-sdk-native
```

Requires Python 3.11 or later.

---

## Scenario 1: Balance Monitor Bot

A bot that connects to a Dilithia node, recovers its wallet from a saved mnemonic, checks its balance, and if the balance exceeds a threshold, sends tokens to a destination address via a contract call. Covers: client setup, wallet recovery, balance query, contract call construction, signing, submission, and receipt polling.

### Sync version

```python
import os
import sys

from dilithia_sdk import DilithiaClient, load_native_crypto_adapter

RPC_URL: str = "https://rpc.dilithia.network/rpc"
TOKEN_CONTRACT: str = "dil1_token_main"
DESTINATION: str = "dil1_recipient_address"
THRESHOLD: int = 500_000
SEND_AMOUNT: int = 100_000


def main() -> None:
    # 1. Initialize client
    client = DilithiaClient(RPC_URL, timeout=15.0)

    # 2. Recover wallet (crypto adapter loaded separately)
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Native crypto adapter unavailable")

    mnemonic: str | None = os.environ.get("BOT_MNEMONIC")
    if not mnemonic:
        print("Set BOT_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = crypto.recover_hd_wallet(mnemonic)
    print(f"Bot address: {account.address}")

    # 3. Check current balance
    balance_result: dict = client.get_balance(account.address)
    balance: int = int(balance_result.get("balance", 0))
    print(f"Current balance: {balance}")

    if balance < THRESHOLD:
        print(f"Balance {balance} below threshold {THRESHOLD}. Nothing to do.")
        return

    # 4. Build a contract call to transfer tokens
    call: dict = client.build_contract_call(
        TOKEN_CONTRACT,
        "transfer",
        {"to": DESTINATION, "amount": SEND_AMOUNT},
    )

    # 5. Simulate to verify it would succeed
    sim_result: dict = client.simulate(call)
    print("Simulation result:", sim_result)

    # 6. Sign and submit
    submitted: dict = client.send_signed_call(call, crypto.signer_for(account))
    tx_hash: str = submitted["tx_hash"]
    print(f"Transaction submitted: {tx_hash}")

    # 7. Poll for receipt
    try:
        receipt: dict = client.wait_for_receipt(
            tx_hash, max_attempts=20, delay_seconds=2.0
        )
        print("Transaction confirmed:", receipt)
    except RuntimeError as exc:
        print(f"Receipt polling failed: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
```

### Async version

```python
import asyncio
import os
import sys

from dilithia_sdk import AsyncDilithiaClient, load_async_native_crypto_adapter

RPC_URL: str = "https://rpc.dilithia.network/rpc"
TOKEN_CONTRACT: str = "dil1_token_main"
DESTINATION: str = "dil1_recipient_address"
THRESHOLD: int = 500_000
SEND_AMOUNT: int = 100_000


async def main() -> None:
    # 1. Initialize async client
    client = AsyncDilithiaClient(RPC_URL, timeout=15.0)

    # 2. Load async crypto adapter and recover wallet
    crypto = load_async_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Native crypto adapter unavailable")

    mnemonic: str | None = os.environ.get("BOT_MNEMONIC")
    if not mnemonic:
        print("Set BOT_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = await crypto.recover_hd_wallet(mnemonic)
    print(f"Bot address: {account.address}")

    # 3. Check current balance
    balance_result: dict = await client.get_balance(account.address)
    balance: int = int(balance_result.get("balance", 0))
    print(f"Current balance: {balance}")

    if balance < THRESHOLD:
        print(f"Balance {balance} below threshold {THRESHOLD}. Nothing to do.")
        return

    # 4. Build and simulate a contract call
    call: dict = client.build_contract_call(
        TOKEN_CONTRACT,
        "transfer",
        {"to": DESTINATION, "amount": SEND_AMOUNT},
    )
    sim_result: dict = await client.simulate(call)
    print("Simulation result:", sim_result)

    # 5. Sign and submit
    submitted: dict = await client.send_signed_call(
        call, crypto.signer_for(account)
    )
    tx_hash: str = submitted["tx_hash"]
    print(f"Transaction submitted: {tx_hash}")

    # 6. Poll for receipt
    try:
        receipt: dict = await client.wait_for_receipt(
            tx_hash, max_attempts=20, delay_seconds=2.0
        )
        print("Transaction confirmed:", receipt)
    except RuntimeError as exc:
        print(f"Receipt polling failed: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        await client.aclose()


if __name__ == "__main__":
    asyncio.run(main())
```

---

## Scenario 2: Multi-Account Treasury Manager

A service that manages multiple HD wallet accounts derived from a single mnemonic. It derives accounts 0 through 4, checks each balance, and consolidates all funds into account 0. Covers: HD derivation loop, multiple balance queries, and batch transaction construction.

```python
import os
import sys

from dilithia_sdk import (
    DilithiaAccount,
    DilithiaClient,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
TOKEN_CONTRACT: str = "dil1_token_main"
NUM_ACCOUNTS: int = 5


def main() -> None:
    client = DilithiaClient(RPC_URL)
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Native crypto adapter unavailable")

    mnemonic: str = os.environ.get("TREASURY_MNEMONIC", "")
    if not mnemonic:
        print("Set TREASURY_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    # 1. Derive all accounts from the same mnemonic
    accounts: list[DilithiaAccount] = []
    for i in range(NUM_ACCOUNTS):
        acct: DilithiaAccount = crypto.recover_hd_wallet_account(mnemonic, i)
        accounts.append(acct)

    treasury_address: str = accounts[0].address
    print(f"Treasury address (account 0): {treasury_address}")

    # 2. Check balances
    balances: list[int] = []
    for acct in accounts:
        result: dict = client.get_balance(acct.address)
        bal: int = int(result.get("balance", 0))
        balances.append(bal)
        print(f"  Account {acct.account_index}: {acct.address} -> {bal}")

    # 3. Consolidate: transfer from accounts 1-4 to account 0
    for i in range(1, NUM_ACCOUNTS):
        if balances[i] <= 0:
            print(f"  Account {i}: zero balance, skipping.")
            continue

        call: dict = client.build_contract_call(
            TOKEN_CONTRACT,
            "transfer",
            {"to": treasury_address, "amount": balances[i]},
        )

        print(f"  Consolidating {balances[i]} from account {i}...")
        try:
            submitted: dict = client.send_signed_call(
                call, crypto.signer_for(accounts[i])
            )
            receipt: dict = client.wait_for_receipt(submitted["tx_hash"])
            print(f"  Done. Receipt: {str(receipt)[:80]}...")
        except RuntimeError as exc:
            print(f"  Transfer from account {i} failed: {exc}", file=sys.stderr)

    # 4. Final balance check
    final: dict = client.get_balance(treasury_address)
    print(f"\nTreasury final balance: {final.get('balance')}")


if __name__ == "__main__":
    main()
```

---

## Scenario 3: Signature Verification Service

An API endpoint that receives a signed message and verifies the signature against the claimed public key and address. Covers: address validation, public key validation, signature verification, and structured error handling.

```python
from typing import Any

from dilithia_sdk import load_native_crypto_adapter


def verify_signed_message(
    public_key: str,
    address: str,
    message: str,
    signature: str,
) -> dict[str, Any]:
    """Verify a signed message against a claimed public key and address.

    Returns a dict with ``"valid": True`` on success, or
    ``"valid": False`` and an ``"error"`` description on failure.
    """
    crypto = load_native_crypto_adapter()
    if crypto is None:
        return {"valid": False, "error": "Crypto adapter unavailable"}

    # 1. Validate the public key format
    try:
        crypto.validate_public_key(public_key)
    except Exception:
        return {"valid": False, "error": "Invalid public key format"}

    # 2. Validate the signature format
    try:
        crypto.validate_signature(signature)
    except Exception:
        return {"valid": False, "error": "Invalid signature format"}

    # 3. Validate the claimed address format
    try:
        crypto.validate_address(address)
    except Exception:
        return {"valid": False, "error": "Invalid address format"}

    # 4. Verify that the public key maps to the claimed address
    derived_address: str = crypto.address_from_public_key(public_key)
    if derived_address != address:
        return {"valid": False, "error": "Address does not match public key"}

    # 5. Verify the cryptographic signature
    is_valid: bool = crypto.verify_message(public_key, message, signature)
    if not is_valid:
        return {"valid": False, "error": "Signature verification failed"}

    return {"valid": True}


if __name__ == "__main__":
    result: dict[str, Any] = verify_signed_message(
        public_key="abcd1234...",
        address="dil1_abc123",
        message="Login nonce: 98765",
        signature="deadbeef...",
    )
    if result["valid"]:
        print("Signature is valid. User authenticated.")
    else:
        print(f"Verification failed: {result['error']}")
```

---

## Scenario 4: Wallet Backup and Recovery

Create a new wallet, save the encrypted wallet file to disk, then recover it later from the saved file. Covers the full wallet lifecycle: generate mnemonic, create encrypted wallet file, serialize, write to disk, read from disk, deserialize, and recover.

```python
import json
import os
from pathlib import Path

from dilithia_sdk import DilithiaAccount, load_native_crypto_adapter

WALLET_PATH: Path = Path("./my-wallet.json")
PASSWORD: str = "my-secure-passphrase"


def main() -> None:
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Native crypto adapter unavailable")

    if not WALLET_PATH.exists():
        # ---- CREATE NEW WALLET ----
        print("No wallet found. Creating a new one...")

        # 1. Generate a fresh mnemonic
        mnemonic: str = crypto.generate_mnemonic()
        print("SAVE THIS MNEMONIC SECURELY:")
        print(mnemonic)
        print()

        # 2. Create an encrypted wallet file from the mnemonic
        account: DilithiaAccount = crypto.create_hd_wallet_file_from_mnemonic(
            mnemonic, PASSWORD
        )
        print(f"Address: {account.address}")
        print(f"Public key: {account.public_key}")

        # 3. Serialize and save to disk
        if account.wallet_file is None:
            raise RuntimeError("Wallet file not generated")
        WALLET_PATH.write_text(
            json.dumps(account.wallet_file, indent=2), encoding="utf-8"
        )
        print(f"Wallet saved to {WALLET_PATH}")

    else:
        # ---- RECOVER EXISTING WALLET ----
        print("Wallet file found. Recovering...")

        # 4. Read the wallet file from disk
        wallet_file: dict = json.loads(WALLET_PATH.read_text(encoding="utf-8"))

        # 5. Recover using mnemonic + password
        mnemonic_env: str | None = os.environ.get("WALLET_MNEMONIC")
        if not mnemonic_env:
            raise RuntimeError("Set WALLET_MNEMONIC env var to recover")

        account = crypto.recover_wallet_file(wallet_file, mnemonic_env, PASSWORD)
        print(f"Recovered address: {account.address}")
        print(f"Public key: {account.public_key}")
        print("Wallet recovered successfully. Ready to sign transactions.")


if __name__ == "__main__":
    main()
```

---

## Scenario 5: Gas-Sponsored Meta-Transaction

Submit a transaction where the gas fee is paid by a sponsor contract instead of the sender. This is useful for onboarding new users who have no tokens to pay for gas. Covers: gas sponsor connector setup, checking remaining quota, building a paymaster-attached call, signing, and submission.

### Sync version

```python
import os
import sys

from dilithia_sdk import (
    DilithiaClient,
    DilithiaGasSponsorConnector,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
SPONSOR_CONTRACT: str = "dil1_gas_sponsor_v1"
PAYMASTER_ADDRESS: str = "dil1_paymaster_addr"
TARGET_CONTRACT: str = "dil1_nft_mint"


def main() -> None:
    client = DilithiaClient(RPC_URL)
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Crypto adapter unavailable")

    # 1. Recover the user's wallet (new user with zero balance)
    mnemonic: str = os.environ.get("USER_MNEMONIC", "")
    if not mnemonic:
        print("Set USER_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = crypto.recover_hd_wallet(mnemonic)
    print(f"User address: {account.address}")

    # 2. Set up the gas sponsor connector
    sponsor = DilithiaGasSponsorConnector(
        client, SPONSOR_CONTRACT, PAYMASTER_ADDRESS
    )

    # 3. Check if the sponsor will accept this call
    accept_query: dict = sponsor.build_accept_query(
        account.address, TARGET_CONTRACT, "mint"
    )
    accept_result: dict = client.query_contract(
        SPONSOR_CONTRACT, "accept", accept_query["args"]
    )
    print("Sponsor accepts:", accept_result)

    # 4. Check remaining gas quota for this user
    quota_query: dict = sponsor.build_remaining_quota_query(account.address)
    quota_result: dict = client.query_contract(
        SPONSOR_CONTRACT, "remaining_quota", quota_query["args"]
    )
    print("Remaining quota:", quota_result)

    # 5. Build the actual contract call
    call: dict = client.build_contract_call(
        TARGET_CONTRACT,
        "mint",
        {"token_id": "nft_001", "metadata": "ipfs://QmSomeHash"},
    )

    # 6. Apply the paymaster (sponsor pays the gas)
    sponsored_call: dict = sponsor.apply_paymaster(call)
    print("Sponsored call:", sponsored_call)

    # 7. Sign and submit the sponsored call
    try:
        submitted: dict = sponsor.send_sponsored_call(
            call, crypto.signer_for(account)
        )
        tx_hash: str = submitted["tx_hash"]
        print(f"Sponsored tx submitted: {tx_hash}")

        # 8. Wait for confirmation
        receipt: dict = client.wait_for_receipt(tx_hash)
        print("Confirmed:", receipt)
    except RuntimeError as exc:
        print(f"Sponsored transaction failed: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
```

### Async version

```python
import asyncio
import os
import sys

from dilithia_sdk import (
    AsyncDilithiaClient,
    DilithiaGasSponsorConnector,
    load_async_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
SPONSOR_CONTRACT: str = "dil1_gas_sponsor_v1"
PAYMASTER_ADDRESS: str = "dil1_paymaster_addr"
TARGET_CONTRACT: str = "dil1_nft_mint"


async def main() -> None:
    client = AsyncDilithiaClient(RPC_URL)
    crypto = load_async_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Crypto adapter unavailable")

    mnemonic: str = os.environ.get("USER_MNEMONIC", "")
    if not mnemonic:
        print("Set USER_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = await crypto.recover_hd_wallet(mnemonic)
    print(f"User address: {account.address}")

    # Set up the gas sponsor connector (works with both sync and async clients)
    sponsor = DilithiaGasSponsorConnector(
        client, SPONSOR_CONTRACT, PAYMASTER_ADDRESS
    )

    # Check sponsor acceptance and quota concurrently
    accept_query: dict = sponsor.build_accept_query(
        account.address, TARGET_CONTRACT, "mint"
    )
    quota_query: dict = sponsor.build_remaining_quota_query(account.address)

    accept_result, quota_result = await asyncio.gather(
        client.query_contract(
            SPONSOR_CONTRACT, "accept", accept_query["args"]
        ),
        client.query_contract(
            SPONSOR_CONTRACT, "remaining_quota", quota_query["args"]
        ),
    )
    print("Sponsor accepts:", accept_result)
    print("Remaining quota:", quota_result)

    # Build, sponsor, sign, and submit
    call: dict = client.build_contract_call(
        TARGET_CONTRACT,
        "mint",
        {"token_id": "nft_001", "metadata": "ipfs://QmSomeHash"},
    )

    try:
        submitted: dict = sponsor.send_sponsored_call(
            call, crypto.signer_for(account)
        )
        tx_hash: str = submitted["tx_hash"]
        print(f"Sponsored tx submitted: {tx_hash}")

        receipt: dict = await client.wait_for_receipt(tx_hash)
        print("Confirmed:", receipt)
    except RuntimeError as exc:
        print(f"Sponsored transaction failed: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        await client.aclose()


if __name__ == "__main__":
    asyncio.run(main())
```

---

## Scenario 6: Cross-Chain Message Sender

Send a message to another Dilithia chain via the messaging connector. This is useful for bridging data or triggering actions on a remote chain. Covers: messaging connector setup, building outbound messages, querying the outbox, signing, and submission.

```python
import os
import sys
import time

from dilithia_sdk import (
    DilithiaClient,
    DilithiaMessagingConnector,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
MESSAGING_CONTRACT: str = "dil1_bridge_v1"
PAYMASTER: str = "dil1_bridge_paymaster"
DEST_CHAIN: str = "dilithia-testnet-2"


def main() -> None:
    client = DilithiaClient(RPC_URL)
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Crypto adapter unavailable")

    mnemonic: str = os.environ.get("BRIDGE_MNEMONIC", "")
    if not mnemonic:
        print("Set BRIDGE_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = crypto.recover_hd_wallet(mnemonic)
    print(f"Sender address: {account.address}")

    # 1. Set up the messaging connector
    messaging = DilithiaMessagingConnector(
        client, MESSAGING_CONTRACT, PAYMASTER
    )

    # 2. Check current outbox state
    outbox: dict = messaging.query_outbox()
    print("Current outbox:", outbox)

    # 3. Build the cross-chain message
    payload: dict = {
        "action": "lock_tokens",
        "sender": account.address,
        "amount": 50_000,
        "recipient": "dil1_remote_recipient",
        "timestamp": int(time.time() * 1000),
    }

    message_call: dict = messaging.build_send_message_call(DEST_CHAIN, payload)
    print("Message call:", message_call)

    # 4. Simulate the message send
    sim_result: dict = client.simulate(message_call)
    print("Simulation:", sim_result)

    # 5. Sign and send the message
    try:
        submitted: dict = messaging.send_message(
            DEST_CHAIN, payload, crypto.signer_for(account)
        )
        tx_hash: str = submitted["tx_hash"]
        print(f"Message tx submitted: {tx_hash}")

        # 6. Wait for confirmation
        receipt: dict = client.wait_for_receipt(tx_hash)
        print("Message confirmed:", receipt)

        # 7. Verify message appears in outbox
        updated_outbox: dict = messaging.query_outbox()
        print("Updated outbox:", updated_outbox)
    except RuntimeError as exc:
        print(f"Cross-chain message failed: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
```

---

## Scenario 7: Contract Deployment

Deploy a WASM smart contract to the Dilithia chain. Reads the WASM binary, hashes the bytecode, builds and signs a canonical deploy payload, assembles the full deploy body, sends the deploy request, and waits for confirmation.

### Sync version

```python
import json
import os
import sys

from dilithia_sdk import DilithiaClient, load_native_crypto_adapter, read_wasm_file_hex

RPC_URL: str = "https://rpc.dilithia.network/rpc"
CONTRACT_NAME: str = "my_contract"
WASM_PATH: str = "./my_contract.wasm"
CHAIN_ID: str = "dilithia-mainnet"


def main() -> None:
    # 1. Initialize client and crypto adapter
    client = DilithiaClient(RPC_URL, timeout=30.0)
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Native crypto adapter unavailable")

    # 2. Recover wallet from mnemonic
    mnemonic: str | None = os.environ.get("DEPLOYER_MNEMONIC")
    if not mnemonic:
        print("Set DEPLOYER_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = crypto.recover_hd_wallet(mnemonic)
    print(f"Deployer address: {account.address}")

    # 3. Read the WASM file as hex
    bytecode_hex: str = read_wasm_file_hex(WASM_PATH)
    print(f"Bytecode size: {len(bytecode_hex) // 2} bytes")

    # 4. Get the current nonce from the node
    nonce_result: dict = client.get_nonce(account.address)
    nonce: int = int(nonce_result.get("nonce", 0))
    print(f"Current nonce: {nonce}")

    # 5. Hash the bytecode hex for the canonical payload
    bytecode_hash: str = crypto.hash_hex(bytecode_hex)
    print(f"Bytecode hash: {bytecode_hash}")

    # 6. Build the canonical deploy payload (keys sorted for deterministic signing)
    canonical: dict = client.build_deploy_canonical_payload(
        account.address, CONTRACT_NAME, bytecode_hash, nonce, CHAIN_ID
    )
    print("Canonical payload:", canonical)

    # 7. Sign the canonical payload
    canonical_json: str = json.dumps(canonical, sort_keys=True)
    sig = crypto.sign_message(account.secret_key, canonical_json)
    print(f"Signed with algorithm: {sig.algorithm}")

    # 8. Assemble the full deploy body
    body: dict = client.deploy_contract_body(
        name=CONTRACT_NAME,
        bytecode=bytecode_hex,
        from_addr=account.address,
        alg=sig.algorithm,
        pk=account.public_key,
        sig=sig.signature,
        nonce=nonce,
        chain_id=CHAIN_ID,
        version=1,
    )

    # 9. Send the deploy request
    result: dict = client.deploy_contract(body)
    tx_hash: str = result["tx_hash"]
    print(f"Deploy tx submitted: {tx_hash}")

    # 10. Wait for the receipt
    try:
        receipt: dict = client.wait_for_receipt(
            tx_hash, max_attempts=30, delay_seconds=3.0
        )
        print("Contract deployed successfully:", receipt)
    except RuntimeError as exc:
        print(f"Receipt polling failed: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
```

### Async version

```python
import asyncio
import json
import os
import sys

from dilithia_sdk import AsyncDilithiaClient, load_async_native_crypto_adapter, read_wasm_file_hex

RPC_URL: str = "https://rpc.dilithia.network/rpc"
CONTRACT_NAME: str = "my_contract"
WASM_PATH: str = "./my_contract.wasm"
CHAIN_ID: str = "dilithia-mainnet"


async def main() -> None:
    # 1. Initialize async client and crypto adapter
    client = AsyncDilithiaClient(RPC_URL, timeout=30.0)
    crypto = load_async_native_crypto_adapter()
    if crypto is None:
        raise RuntimeError("Native crypto adapter unavailable")

    # 2. Recover wallet from mnemonic
    mnemonic: str | None = os.environ.get("DEPLOYER_MNEMONIC")
    if not mnemonic:
        print("Set DEPLOYER_MNEMONIC env var", file=sys.stderr)
        sys.exit(1)

    account = await crypto.recover_hd_wallet(mnemonic)
    print(f"Deployer address: {account.address}")

    # 3. Read the WASM file as hex (sync I/O is fine for a single file)
    bytecode_hex: str = read_wasm_file_hex(WASM_PATH)
    print(f"Bytecode size: {len(bytecode_hex) // 2} bytes")

    # 4. Get the current nonce from the node
    nonce_result: dict = await client.get_nonce(account.address)
    nonce: int = int(nonce_result.get("nonce", 0))
    print(f"Current nonce: {nonce}")

    # 5. Hash the bytecode hex for the canonical payload
    bytecode_hash: str = await crypto.hash_hex(bytecode_hex)
    print(f"Bytecode hash: {bytecode_hash}")

    # 6. Build the canonical deploy payload
    canonical: dict = client.build_deploy_canonical_payload(
        account.address, CONTRACT_NAME, bytecode_hash, nonce, CHAIN_ID
    )

    # 7. Sign the canonical payload
    canonical_json: str = json.dumps(canonical, sort_keys=True)
    sig = await crypto.sign_message(account.secret_key, canonical_json)

    # 8. Assemble the full deploy body
    body: dict = client.deploy_contract_body(
        name=CONTRACT_NAME,
        bytecode=bytecode_hex,
        from_addr=account.address,
        alg=sig.algorithm,
        pk=account.public_key,
        sig=sig.signature,
        nonce=nonce,
        chain_id=CHAIN_ID,
        version=1,
    )

    # 9. Send the deploy request
    try:
        result: dict = await client.deploy_contract(body)
        tx_hash: str = result["tx_hash"]
        print(f"Deploy tx submitted: {tx_hash}")

        # 10. Wait for the receipt
        receipt: dict = await client.wait_for_receipt(
            tx_hash, max_attempts=30, delay_seconds=3.0
        )
        print("Contract deployed successfully:", receipt)
    except RuntimeError as exc:
        print(f"Deployment failed: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        await client.aclose()


if __name__ == "__main__":
    asyncio.run(main())
```
