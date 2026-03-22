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

from dilithia_sdk import (
    DilithiaClient,
    Balance,
    Receipt,
    DilithiaError,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
TOKEN_CONTRACT: str = "dil1_token_main"
DESTINATION: str = "dil1_recipient_address"
THRESHOLD: int = 500_000
SEND_AMOUNT: int = 100_000


def main() -> None:
    # 1. Initialize client with context manager
    with DilithiaClient(RPC_URL, timeout=15.0) as client:
        # 2. Recover wallet (crypto adapter loaded separately)
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

        mnemonic: str | None = os.environ.get("BOT_MNEMONIC")
        if not mnemonic:
            print("Set BOT_MNEMONIC env var", file=sys.stderr)
            sys.exit(1)

        account = crypto.recover_hd_wallet(mnemonic)
        print(f"Bot address: {account.address}")

        # 3. Check current balance — returns typed Balance with .balance as TokenAmount
        balance_result: Balance = client.balance(account.address)
        raw_balance: int = balance_result.balance.to_raw()
        print(f"Current balance: {balance_result.balance.formatted()}")

        if raw_balance < THRESHOLD:
            print(f"Balance {raw_balance} below threshold {THRESHOLD}. Nothing to do.")
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

        # 7. Poll for receipt — returns typed Receipt
        try:
            receipt: Receipt = client.wait_for_receipt(
                tx_hash, max_attempts=20, delay_seconds=2.0
            )
            print(f"Confirmed in block {receipt.block_height}, status: {receipt.status}")
        except DilithiaError as exc:
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

from dilithia_sdk import (
    AsyncDilithiaClient,
    Balance,
    Receipt,
    DilithiaError,
    load_async_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
TOKEN_CONTRACT: str = "dil1_token_main"
DESTINATION: str = "dil1_recipient_address"
THRESHOLD: int = 500_000
SEND_AMOUNT: int = 100_000


async def main() -> None:
    # 1. Initialize async client with context manager
    async with AsyncDilithiaClient(RPC_URL, timeout=15.0) as client:
        # 2. Load async crypto adapter and recover wallet
        crypto = load_async_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

        mnemonic: str | None = os.environ.get("BOT_MNEMONIC")
        if not mnemonic:
            print("Set BOT_MNEMONIC env var", file=sys.stderr)
            sys.exit(1)

        account = await crypto.recover_hd_wallet(mnemonic)
        print(f"Bot address: {account.address}")

        # 3. Check current balance — returns typed Balance
        balance_result: Balance = await client.balance(account.address)
        raw_balance: int = balance_result.balance.to_raw()
        print(f"Current balance: {balance_result.balance.formatted()}")

        if raw_balance < THRESHOLD:
            print(f"Balance {raw_balance} below threshold {THRESHOLD}. Nothing to do.")
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

        # 6. Poll for receipt — returns typed Receipt
        try:
            receipt: Receipt = await client.wait_for_receipt(
                tx_hash, max_attempts=20, delay_seconds=2.0
            )
            print(f"Confirmed in block {receipt.block_height}, status: {receipt.status}")
        except DilithiaError as exc:
            print(f"Receipt polling failed: {exc}", file=sys.stderr)
            sys.exit(1)


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
    Balance,
    Receipt,
    DilithiaError,
    RpcError,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
TOKEN_CONTRACT: str = "dil1_token_main"
NUM_ACCOUNTS: int = 5


def main() -> None:
    with DilithiaClient(RPC_URL) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

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

        # 2. Check balances — balance() returns typed Balance
        balances: list[int] = []
        for acct in accounts:
            result: Balance = client.balance(acct.address)
            bal: int = result.balance.to_raw()
            balances.append(bal)
            print(f"  Account {acct.account_index}: {acct.address} -> {result.balance.formatted()}")

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
                receipt: Receipt = client.wait_for_receipt(submitted["tx_hash"])
                print(f"  Done. Block: {receipt.block_height}, status: {receipt.status}")
            except RpcError as exc:
                print(f"  RPC error from account {i}: {exc}", file=sys.stderr)
            except DilithiaError as exc:
                print(f"  Transfer from account {i} failed: {exc}", file=sys.stderr)

        # 4. Final balance check
        final: Balance = client.balance(treasury_address)
        print(f"\nTreasury final balance: {final.balance.formatted()}")


if __name__ == "__main__":
    main()
```

---

## Scenario 3: Signature Verification Service

An API endpoint that receives a signed message and verifies the signature against the claimed public key and address. Covers: address validation, public key validation, signature verification, and structured error handling.

```python
from typing import Any

from dilithia_sdk import DilithiaError, load_native_crypto_adapter


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
    except DilithiaError:
        return {"valid": False, "error": "Invalid public key format"}

    # 2. Validate the signature format
    try:
        crypto.validate_signature(signature)
    except DilithiaError:
        return {"valid": False, "error": "Invalid signature format"}

    # 3. Validate the claimed address format
    try:
        crypto.validate_address(address)
    except DilithiaError:
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

from dilithia_sdk import DilithiaAccount, DilithiaError, load_native_crypto_adapter

WALLET_PATH: Path = Path("./my-wallet.json")
PASSWORD: str = "my-secure-passphrase"


def main() -> None:
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise DilithiaError("Native crypto adapter unavailable")

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
            raise DilithiaError("Wallet file not generated")
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
            raise DilithiaError("Set WALLET_MNEMONIC env var to recover")

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
    Receipt,
    DilithiaError,
    RpcError,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
SPONSOR_CONTRACT: str = "dil1_gas_sponsor_v1"
PAYMASTER_ADDRESS: str = "dil1_paymaster_addr"
TARGET_CONTRACT: str = "dil1_nft_mint"


def main() -> None:
    with DilithiaClient(RPC_URL) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Crypto adapter unavailable")

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

            # 8. Wait for confirmation — returns typed Receipt
            receipt: Receipt = client.wait_for_receipt(tx_hash)
            print(f"Confirmed in block {receipt.block_height}, status: {receipt.status}")
        except RpcError as exc:
            print(f"RPC error: {exc}", file=sys.stderr)
            sys.exit(1)
        except DilithiaError as exc:
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
    Receipt,
    DilithiaError,
    RpcError,
    load_async_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
SPONSOR_CONTRACT: str = "dil1_gas_sponsor_v1"
PAYMASTER_ADDRESS: str = "dil1_paymaster_addr"
TARGET_CONTRACT: str = "dil1_nft_mint"


async def main() -> None:
    async with AsyncDilithiaClient(RPC_URL) as client:
        crypto = load_async_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Crypto adapter unavailable")

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

            receipt: Receipt = await client.wait_for_receipt(tx_hash)
            print(f"Confirmed in block {receipt.block_height}, status: {receipt.status}")
        except RpcError as exc:
            print(f"RPC error: {exc}", file=sys.stderr)
            sys.exit(1)
        except DilithiaError as exc:
            print(f"Sponsored transaction failed: {exc}", file=sys.stderr)
            sys.exit(1)


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
    Receipt,
    DilithiaError,
    HttpError,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
MESSAGING_CONTRACT: str = "dil1_bridge_v1"
PAYMASTER: str = "dil1_bridge_paymaster"
DEST_CHAIN: str = "dilithia-testnet-2"


def main() -> None:
    with DilithiaClient(RPC_URL) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Crypto adapter unavailable")

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

            # 6. Wait for confirmation — returns typed Receipt
            receipt: Receipt = client.wait_for_receipt(tx_hash)
            print(f"Message confirmed in block {receipt.block_height}, status: {receipt.status}")

            # 7. Verify message appears in outbox
            updated_outbox: dict = messaging.query_outbox()
            print("Updated outbox:", updated_outbox)
        except HttpError as exc:
            print(f"HTTP error: {exc}", file=sys.stderr)
            sys.exit(1)
        except DilithiaError as exc:
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

from dilithia_sdk import (
    DilithiaClient,
    Nonce,
    Receipt,
    DilithiaError,
    RpcError,
    load_native_crypto_adapter,
    read_wasm_file_hex,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
CONTRACT_NAME: str = "my_contract"
WASM_PATH: str = "./my_contract.wasm"
CHAIN_ID: str = "dilithia-mainnet"


def main() -> None:
    # 1. Initialize client and crypto adapter
    with DilithiaClient(RPC_URL, timeout=30.0) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

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

        # 4. Get the current nonce — returns typed Nonce with .next_nonce
        nonce_result: Nonce = client.nonce(account.address)
        nonce: int = nonce_result.next_nonce
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

        # 10. Wait for the receipt — returns typed Receipt
        try:
            receipt: Receipt = client.wait_for_receipt(
                tx_hash, max_attempts=30, delay_seconds=3.0
            )
            print(f"Contract deployed in block {receipt.block_height}, status: {receipt.status}")
        except RpcError as exc:
            print(f"RPC error: {exc}", file=sys.stderr)
            sys.exit(1)
        except DilithiaError as exc:
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

from dilithia_sdk import (
    AsyncDilithiaClient,
    Nonce,
    Receipt,
    DilithiaError,
    RpcError,
    TimeoutError,
    load_async_native_crypto_adapter,
    read_wasm_file_hex,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
CONTRACT_NAME: str = "my_contract"
WASM_PATH: str = "./my_contract.wasm"
CHAIN_ID: str = "dilithia-mainnet"


async def main() -> None:
    # 1. Initialize async client and crypto adapter with context manager
    async with AsyncDilithiaClient(RPC_URL, timeout=30.0) as client:
        crypto = load_async_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

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

        # 4. Get the current nonce — returns typed Nonce with .next_nonce
        nonce_result: Nonce = await client.nonce(account.address)
        nonce: int = nonce_result.next_nonce
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

            # 10. Wait for the receipt — returns typed Receipt
            receipt: Receipt = await client.wait_for_receipt(
                tx_hash, max_attempts=30, delay_seconds=3.0
            )
            print(f"Contract deployed in block {receipt.block_height}, status: {receipt.status}")
        except TimeoutError as exc:
            print(f"Deployment timed out: {exc}", file=sys.stderr)
            sys.exit(1)
        except RpcError as exc:
            print(f"RPC error: {exc}", file=sys.stderr)
            sys.exit(1)
        except DilithiaError as exc:
            print(f"Deployment failed: {exc}", file=sys.stderr)
            sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
```

---

## Scenario 8: Shielded Pool Deposit & Withdraw

Deposit tokens into a shielded pool using a ZK commitment, then withdraw them later by proving knowledge of the secret and spending the nullifier. Covers: ZK adapter setup, commitment computation, shielded deposit, commitment root verification, nullifier computation, shielded withdraw, and receipt polling.

```python
import os
import sys

from dilithia_sdk import (
    DilithiaClient,
    Receipt,
    DilithiaError,
    load_native_crypto_adapter,
)
from dilithia_sdk.zk import load_native_zk_adapter

RPC_URL: str = "https://rpc.dilithia.network/rpc"
SHIELDED_POOL: str = "dil1_shielded_pool_v1"
DEPOSIT_AMOUNT: int = 100_000


def main() -> None:
    with DilithiaClient(RPC_URL) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

        zk = load_native_zk_adapter()
        if zk is None:
            raise DilithiaError("Native ZK adapter unavailable")

        # 1. Recover wallet
        mnemonic: str = os.environ.get("SHIELDED_MNEMONIC", "")
        if not mnemonic:
            print("Set SHIELDED_MNEMONIC env var", file=sys.stderr)
            sys.exit(1)

        account = crypto.recover_hd_wallet(mnemonic)
        print(f"Address: {account.address}")

        # 2. Generate a secret and nonce for the commitment
        secret_hex: str = crypto.hash_hex("my_deposit_secret_entropy")
        nonce_hex: str = crypto.hash_hex("my_deposit_nonce_entropy")

        # 3. Compute the Poseidon commitment: H(value, secret, nonce)
        commitment: str = zk.compute_commitment(DEPOSIT_AMOUNT, secret_hex, nonce_hex)
        print(f"Commitment: {commitment}")

        # 4. Deposit into the shielded pool
        try:
            deposit_result: dict = client.shielded_deposit(
                SHIELDED_POOL,
                DEPOSIT_AMOUNT,
                commitment,
                crypto.signer_for(account),
            )
            deposit_tx: str = deposit_result["tx_hash"]
            print(f"Deposit tx submitted: {deposit_tx}")

            receipt: Receipt = client.wait_for_receipt(
                deposit_tx, max_attempts=20, delay_seconds=2.0
            )
            print(f"Deposit confirmed in block {receipt.block_height}, status: {receipt.status}")
        except DilithiaError as exc:
            print(f"Deposit failed: {exc}", file=sys.stderr)
            sys.exit(1)

        # 5. Verify the commitment is included in the Merkle root
        root: str = client.get_commitment_root(SHIELDED_POOL)
        print(f"Current commitment root: {root}")

        # 6. Compute the nullifier to spend this commitment
        nullifier: str = zk.compute_nullifier(secret_hex, nonce_hex)
        print(f"Nullifier: {nullifier}")

        # 7. Check that the nullifier has not been spent yet
        already_spent: bool = client.is_nullifier_spent(SHIELDED_POOL, nullifier)
        if already_spent:
            print("Nullifier already spent. Cannot withdraw.", file=sys.stderr)
            sys.exit(1)
        print("Nullifier is unspent. Proceeding with withdraw.")

        # 8. Generate a preimage proof for the withdrawal
        proof = zk.generate_preimage_proof([DEPOSIT_AMOUNT, secret_hex, nonce_hex])
        print(f"Proof generated: {len(proof.proof_bytes)} bytes")

        # 9. Withdraw from the shielded pool
        try:
            withdraw_result: dict = client.shielded_withdraw(
                SHIELDED_POOL,
                DEPOSIT_AMOUNT,
                nullifier,
                proof.proof_bytes,
                account.address,
                crypto.signer_for(account),
            )
            withdraw_tx: str = withdraw_result["tx_hash"]
            print(f"Withdraw tx submitted: {withdraw_tx}")

            receipt = client.wait_for_receipt(
                withdraw_tx, max_attempts=20, delay_seconds=2.0
            )
            print(f"Withdraw confirmed in block {receipt.block_height}, status: {receipt.status}")
        except DilithiaError as exc:
            print(f"Withdraw failed: {exc}", file=sys.stderr)
            sys.exit(1)

        # 10. Confirm the nullifier is now spent
        spent_after: bool = client.is_nullifier_spent(SHIELDED_POOL, nullifier)
        print(f"Nullifier spent after withdraw: {spent_after}")


if __name__ == "__main__":
    main()
```

---

## Scenario 9: ZK Proof Generation & Verification

Standalone zero-knowledge proof operations without any chain interaction. Demonstrates Poseidon hashing, commitment computation, nullifier derivation, preimage proof generation and verification, and range proof generation and verification. Covers: ZK adapter setup, Poseidon hash, commitment and nullifier helpers, preimage proofs, and range proofs.

```python
from dilithia_sdk import DilithiaError, load_native_crypto_adapter
from dilithia_sdk.zk import load_native_zk_adapter


def main() -> None:
    crypto = load_native_crypto_adapter()
    if crypto is None:
        raise DilithiaError("Native crypto adapter unavailable")

    zk = load_native_zk_adapter()
    if zk is None:
        raise DilithiaError("Native ZK adapter unavailable")

    # ---- Poseidon Hashing ----

    # 1. Hash a list of field elements using the Poseidon hash function
    hash_result: str = zk.poseidon_hash([42, 1337, 99])
    print(f"Poseidon hash of [42, 1337, 99]: {hash_result}")

    # 2. Hash a single value
    single_hash: str = zk.poseidon_hash([12345])
    print(f"Poseidon hash of [12345]: {single_hash}")

    # ---- Commitment & Nullifier ----

    # 3. Generate deterministic secret and nonce via the crypto adapter
    secret_hex: str = crypto.hash_hex("user_secret_entropy_value")
    nonce_hex: str = crypto.hash_hex("user_nonce_entropy_value")
    deposit_value: int = 250_000
    print(f"\nSecret:  {secret_hex}")
    print(f"Nonce:   {nonce_hex}")

    # 4. Compute the commitment: H(value, secret, nonce)
    commitment: str = zk.compute_commitment(deposit_value, secret_hex, nonce_hex)
    print(f"Commitment: {commitment}")

    # 5. Compute the nullifier: H(secret, nonce)
    nullifier: str = zk.compute_nullifier(secret_hex, nonce_hex)
    print(f"Nullifier:  {nullifier}")

    # ---- Preimage Proof Generation & Verification ----

    # 6. Generate a preimage proof proving knowledge of the inputs that hash to a
    #    known output, without revealing those inputs
    print("\n--- Preimage Proof ---")
    preimage_proof = zk.generate_preimage_proof([deposit_value, secret_hex, nonce_hex])
    print(f"Proof size:           {len(preimage_proof.proof_bytes)} bytes")
    print(f"Verification key size: {len(preimage_proof.verification_key)} bytes")
    print(f"Public inputs:        {preimage_proof.public_inputs}")

    # 7. Verify the preimage proof
    preimage_valid: bool = zk.verify_preimage_proof(
        preimage_proof.proof_bytes,
        preimage_proof.verification_key,
        preimage_proof.public_inputs,
    )
    print(f"Preimage proof valid: {preimage_valid}")

    if not preimage_valid:
        raise DilithiaError("Preimage proof verification failed unexpectedly")

    # ---- Range Proof Generation & Verification ----

    # 8. Generate a range proof proving that a value lies within [min, max]
    #    without revealing the exact value
    print("\n--- Range Proof ---")
    secret_value: int = 500
    range_min: int = 100
    range_max: int = 1000

    range_proof = zk.generate_range_proof(secret_value, range_min, range_max)
    print(f"Proof size:           {len(range_proof.proof_bytes)} bytes")
    print(f"Verification key size: {len(range_proof.verification_key)} bytes")
    print(f"Public inputs:        {range_proof.public_inputs}")

    # 9. Verify the range proof
    range_valid: bool = zk.verify_range_proof(
        range_proof.proof_bytes,
        range_proof.verification_key,
        range_proof.public_inputs,
    )
    print(f"Range proof valid: {range_valid}")

    if not range_valid:
        raise DilithiaError("Range proof verification failed unexpectedly")

    # 10. Demonstrate that a value outside the range would fail (conceptual)
    print("\n--- Summary ---")
    print(f"Poseidon hash:      {hash_result}")
    print(f"Commitment:         {commitment}")
    print(f"Nullifier:          {nullifier}")
    print(f"Preimage proof OK:  {preimage_valid}")
    print(f"Range proof OK:     {range_valid}")
    print("All ZK operations completed successfully.")


if __name__ == "__main__":
    main()
```

---

## Scenario 10: Name Service & Identity Profile

A utility that registers a `.dili` name, configures profile records, resolves names, and demonstrates the full name service lifecycle. Covers: `getRegistrationCost`, `isNameAvailable`, `registerName`, `setNameTarget`, `setNameRecord`, `getNameRecords`, `lookupName`, `resolveName`, `reverseResolveName`, `getNamesByOwner`, `renewName`, `transferName`, `releaseName`.

```python
import os
import sys

from dilithia_sdk import (
    DilithiaClient,
    Receipt,
    DilithiaError,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
NAME: str = "alice"
TRANSFER_TO: str = "dil1_bob_address"


def main() -> None:
    with DilithiaClient(RPC_URL) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

        # 1–2. Recover wallet from mnemonic
        mnemonic = os.environ.get("WALLET_MNEMONIC")
        if not mnemonic:
            print("Set WALLET_MNEMONIC env var", file=sys.stderr)
            sys.exit(1)
        account = crypto.recover_hd_wallet(mnemonic)
        print(f"Address: {account.address}")

        # 3. Query registration cost for the name
        cost = client.get_registration_cost(NAME)
        print(f'Registration cost for "{NAME}": {cost.formatted()}')

        # 4. Check if name is available
        available: bool = client.is_name_available(NAME)
        print(f'Name "{NAME}" available: {available}')
        if not available:
            raise DilithiaError(f'Name "{NAME}" is already taken')

        # 5. Register name
        reg_result = client.register_name(NAME, account.address, account.secret_key)
        reg_receipt: Receipt = client.wait_for_receipt(reg_result.tx_hash, retries=20, delay_ms=2000)
        print(f"Name registered in block {reg_receipt.block_height}")

        # 6. Set target address
        target_result = client.set_name_target(NAME, account.address, account.secret_key)
        client.wait_for_receipt(target_result.tx_hash, retries=20, delay_ms=2000)
        print(f"Target address set to {account.address}")

        # 7. Set profile records
        records = [
            ("display_name", "Alice"),
            ("avatar", "https://example.com/alice.png"),
            ("bio", "Builder on Dilithia"),
            ("email", "alice@example.com"),
            ("website", "https://alice.dev"),
        ]
        for key, value in records:
            res = client.set_name_record(NAME, key, value, account.secret_key)
            client.wait_for_receipt(res.tx_hash, retries=20, delay_ms=2000)
            print(f'  Set record "{key}" = "{value}"')

        # 8. Get all records
        all_records = client.get_name_records(NAME)
        print(f"All records: {all_records}")

        # 9. Resolve name to address
        resolved = client.resolve_name(NAME)
        print(f'resolve_name("{NAME}") -> {resolved}')

        # 10. Reverse resolve address to name
        reverse_name = client.reverse_resolve_name(account.address)
        print(f'reverse_resolve_name("{account.address}") -> {reverse_name}')

        # 11. List all names by owner
        owned = client.get_names_by_owner(account.address)
        print(f"Names owned by {account.address}: {owned}")

        # 12. Renew name
        renew_result = client.renew_name(NAME, account.secret_key)
        renew_receipt: Receipt = client.wait_for_receipt(renew_result.tx_hash, retries=20, delay_ms=2000)
        print(f"Name renewed in block {renew_receipt.block_height}")

        # 13. Transfer name to another address
        transfer_result = client.transfer_name(NAME, TRANSFER_TO, account.secret_key)
        transfer_receipt: Receipt = client.wait_for_receipt(transfer_result.tx_hash, retries=20, delay_ms=2000)
        print(f"Name transferred to {TRANSFER_TO} in block {transfer_receipt.block_height}")


if __name__ == "__main__":
    main()
```

---

## Scenario 11: Credential Issuance & Verification

An issuer creates a KYC credential schema, issues a credential to a holder, and a verifier checks it with selective disclosure. Covers: `registerSchema`, `issueCredential`, `getSchema`, `getCredential`, `listCredentialsByHolder`, `listCredentialsByIssuer`, `verifyCredential`, `revokeCredential`.

```python
import os
import sys
import time

from dilithia_sdk import (
    DilithiaClient,
    Receipt,
    DilithiaError,
    load_native_crypto_adapter,
)

RPC_URL: str = "https://rpc.dilithia.network/rpc"
HOLDER_ADDRESS: str = "dil1_holder_address"


def main() -> None:
    with DilithiaClient(RPC_URL) as client:
        crypto = load_native_crypto_adapter()
        if crypto is None:
            raise DilithiaError("Native crypto adapter unavailable")

        # 1–2. Recover issuer wallet
        mnemonic = os.environ.get("ISSUER_MNEMONIC")
        if not mnemonic:
            print("Set ISSUER_MNEMONIC env var", file=sys.stderr)
            sys.exit(1)
        issuer = crypto.recover_hd_wallet(mnemonic)
        print(f"Issuer address: {issuer.address}")

        # 3. Register a KYC schema
        schema = {
            "name": "KYC_Basic_v1",
            "attributes": [
                {"name": "full_name", "type": "string"},
                {"name": "country", "type": "string"},
                {"name": "age", "type": "u64"},
                {"name": "verified", "type": "bool"},
            ],
        }
        schema_result = client.register_schema(schema, issuer.secret_key)
        schema_receipt: Receipt = client.wait_for_receipt(schema_result.tx_hash, retries=20, delay_ms=2000)
        schema_hash: str = schema_receipt.logs[0]["schema_hash"]
        print(f"Schema registered: {schema_hash}")

        # 4. Issue credential to holder with commitment hash
        commitment_hash: str = crypto.hash_hex(f"{HOLDER_ADDRESS}:KYC_Basic_v1:{int(time.time())}")
        issue_result = client.issue_credential(
            schema_hash=schema_hash,
            holder=HOLDER_ADDRESS,
            commitment_hash=commitment_hash,
            attributes={"full_name": "Alice Smith", "country": "CH", "age": 30, "verified": True},
            signer=issuer.secret_key,
        )
        issue_receipt: Receipt = client.wait_for_receipt(issue_result.tx_hash, retries=20, delay_ms=2000)
        print(f"Credential issued in block {issue_receipt.block_height}")

        # 5. Get schema by hash
        fetched_schema = client.get_schema(schema_hash)
        print(f"Schema: {fetched_schema}")

        # 6. Get credential by commitment
        credential = client.get_credential(commitment_hash)
        print(f"Credential: {credential}")

        # 7. List credentials by holder
        holder_creds = client.list_credentials_by_holder(HOLDER_ADDRESS)
        print(f"Holder has {len(holder_creds)} credential(s)")

        # 8. List credentials by issuer
        issuer_creds = client.list_credentials_by_issuer(issuer.address)
        print(f"Issuer has {len(issuer_creds)} credential(s)")

        # 9. Verify selective disclosure — prove age > 18 without revealing exact age
        proof = crypto.generate_selective_disclosure_proof(
            commitment_hash, ["age"], {"age": {"operator": "gt", "threshold": 18}},
        )
        verified: bool = client.verify_credential(commitment_hash, proof)
        print(f"Selective disclosure (age > 18) verified: {verified}")

        # 10. Revoke the credential
        revoke_result = client.revoke_credential(commitment_hash, issuer.secret_key)
        revoke_receipt: Receipt = client.wait_for_receipt(revoke_result.tx_hash, retries=20, delay_ms=2000)
        print(f"Credential revoked in block {revoke_receipt.block_height}")

        # 11. Verify revocation by fetching credential again
        revoked_cred = client.get_credential(commitment_hash)
        print(f"Credential status after revocation: {revoked_cred.status}")


if __name__ == "__main__":
    main()
```
