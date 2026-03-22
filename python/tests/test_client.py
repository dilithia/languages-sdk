"""Tests for the Dilithia Python SDK v0.5.0 redesign.

Covers version constants, URL construction, typed models, exception hierarchy,
and both sync/async client interfaces.
"""

import asyncio
import json
import sys
import unittest
from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from dilithia_sdk import (
    Address,
    AsyncDilithiaClient,
    Balance,
    CryptoError,
    DilithiaClient,
    DilithiaError,
    DilithiaGasSponsorConnector,
    DilithiaMessagingConnector,
    GasEstimate,
    HttpError,
    MIN_PYTHON,
    NameRecord,
    NetworkInfo,
    Nonce,
    QueryResult,
    RPC_LINE_VERSION,
    Receipt,
    RpcError,
    TimeoutError,
    TokenAmount,
    TxHash,
    ValidationError,
    __version__,
    load_native_crypto_adapter,
    load_async_native_crypto_adapter,
    load_zk_adapter,
    load_async_zk_adapter,
)
from dilithia_sdk.crypto import (
    AsyncNativeCryptoAdapter,
    DilithiaAccount,
    DilithiaKeypair,
    DilithiaSignature,
    NativeCryptoAdapter,
)
from dilithia_sdk.zk import (
    AsyncNativeZkAdapter,
    Commitment,
    CommitmentProofResult,
    MerkleProofResult,
    NativeZkAdapter,
    Nullifier,
    PredicateProofResult,
    StarkProofResult,
    TransferProofResult,
)


# ---------------------------------------------------------------------------
# Shared mock helpers
# ---------------------------------------------------------------------------


def _mock_response(json_data, status_code=200):
    """Build a fake httpx.Response with the given JSON body and status."""
    resp = MagicMock(spec=httpx.Response)
    resp.status_code = status_code
    resp.json.return_value = json_data
    resp.text = json.dumps(json_data) if json_data is not None else ""
    resp.raise_for_status = MagicMock()
    if status_code >= 400:
        request = MagicMock(spec=httpx.Request)
        resp.raise_for_status.side_effect = httpx.HTTPStatusError(
            "error", request=request, response=resp
        )
    return resp


def _async_mock_response(json_data, status_code=200):
    """Return an awaitable that resolves to a mock response."""
    resp = _mock_response(json_data, status_code)
    future = asyncio.Future()
    future.set_result(resp)
    return future


# ---------------------------------------------------------------------------
# Version / metadata
# ---------------------------------------------------------------------------


class VersionTests(unittest.TestCase):
    """SDK version and minimum-Python checks."""

    def test_sdk_version_is_0_3_0(self) -> None:
        self.assertEqual(__version__, "0.5.0")

    def test_rpc_line_version_matches_sdk(self) -> None:
        self.assertEqual(RPC_LINE_VERSION, "0.5.0")

    def test_python_runtime_satisfies_minimum(self) -> None:
        self.assertGreaterEqual(sys.version_info[:2], MIN_PYTHON)


# ---------------------------------------------------------------------------
# URL construction
# ---------------------------------------------------------------------------


class UrlConstructionTests(unittest.TestCase):
    """Verify URL normalisation, WebSocket derivation, and URL builders."""

    def test_normalizes_rpc_url(self) -> None:
        client = DilithiaClient("http://rpc.example/")
        self.assertEqual(client.rpc_url, "http://rpc.example")
        self.assertEqual(client.base_url, "http://rpc.example")
        self.assertEqual(client.ws_url, "ws://rpc.example")

    def test_derives_wss_from_https(self) -> None:
        client = DilithiaClient("https://rpc.example/rpc")
        self.assertEqual(client.ws_url, "wss://rpc.example")

    def test_accepts_configurable_urls(self) -> None:
        client = DilithiaClient(
            "http://rpc.example/rpc",
            chain_base_url="http://chain.example/",
            indexer_url="http://indexer.example/",
            oracle_url="http://oracle.example/",
        )
        self.assertEqual(client.base_url, "http://chain.example")
        self.assertEqual(client.indexer_url, "http://indexer.example")
        self.assertEqual(client.oracle_url, "http://oracle.example")

    def test_name_service_url(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        url = client.build_name_service_url("resolve", "alice.dili")
        self.assertEqual(url, "http://rpc.example/names/resolve/alice.dili")

    def test_contract_query_url(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        url = client.build_contract_query_url("wasm:amm", "get_reserves", {})
        self.assertEqual(
            url,
            "http://rpc.example/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D",
        )


# ---------------------------------------------------------------------------
# Typed models
# ---------------------------------------------------------------------------


class AddressTests(unittest.TestCase):
    """Tests for the Address model."""

    def test_address_of_creates_address(self) -> None:
        addr = Address.of("alice")
        self.assertEqual(addr.value, "alice")
        self.assertEqual(str(addr), "alice")

    def test_address_of_rejects_empty(self) -> None:
        with self.assertRaises(ValidationError):
            Address.of("")


class TokenAmountTests(unittest.TestCase):
    """Tests for the TokenAmount model."""

    def test_dili_from_string(self) -> None:
        t = TokenAmount.dili("1.5")
        self.assertEqual(t.value, Decimal("1.5"))
        self.assertEqual(t.decimals, 18)

    def test_dili_from_int(self) -> None:
        t = TokenAmount.dili(10)
        self.assertEqual(t.value, Decimal("10"))

    def test_from_raw_and_to_raw_roundtrip(self) -> None:
        raw = 1_500_000_000_000_000_000
        t = TokenAmount.from_raw(raw)
        self.assertEqual(t.to_raw(), raw)

    def test_formatted(self) -> None:
        t = TokenAmount.dili("1.5")
        formatted = t.formatted()
        self.assertTrue(formatted.startswith("1.5"))
        self.assertEqual(len(formatted), 20)  # "1." + 18 decimals

    def test_from_raw_small_decimals(self) -> None:
        t = TokenAmount.from_raw(150, decimals=2)
        self.assertEqual(t.value, Decimal("1.5"))
        self.assertEqual(t.to_raw(), 150)


class BalanceTests(unittest.TestCase):
    """Tests for the Balance model."""

    def test_balance_is_frozen(self) -> None:
        bal = Balance(
            address=Address(value="alice"),
            balance=TokenAmount.dili("100"),
        )
        with self.assertRaises(AttributeError):
            bal.address = Address(value="bob")  # type: ignore[misc]


class ReceiptTests(unittest.TestCase):
    """Tests for the Receipt model."""

    def test_receipt_defaults(self) -> None:
        r = Receipt(
            tx_hash=TxHash(value="0xabc"),
            block_height=42,
            status="success",
        )
        self.assertIsNone(r.error)
        self.assertEqual(r.gas_used, 0)
        self.assertEqual(r.fee_paid, 0)


# ---------------------------------------------------------------------------
# Exception hierarchy
# ---------------------------------------------------------------------------


class ExceptionTests(unittest.TestCase):
    """Verify the exception class hierarchy."""

    def test_all_exceptions_inherit_from_dilithia_error(self) -> None:
        self.assertTrue(issubclass(RpcError, DilithiaError))
        self.assertTrue(issubclass(HttpError, DilithiaError))
        self.assertTrue(issubclass(TimeoutError, DilithiaError))
        self.assertTrue(issubclass(CryptoError, DilithiaError))
        self.assertTrue(issubclass(ValidationError, DilithiaError))

    def test_rpc_error_carries_code_and_message(self) -> None:
        err = RpcError(code=-32600, message="Invalid request")
        self.assertEqual(err.code, -32600)
        self.assertEqual(err.message, "Invalid request")
        self.assertIn("-32600", str(err))

    def test_http_error_carries_status_and_body(self) -> None:
        err = HttpError(status_code=404, body="not found")
        self.assertEqual(err.status_code, 404)
        self.assertEqual(err.body, "not found")


# ---------------------------------------------------------------------------
# Sync client
# ---------------------------------------------------------------------------


class SyncClientTests(unittest.TestCase):
    """Tests for DilithiaClient (sync)."""

    def test_builds_json_rpc_request(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        self.assertEqual(
            client.build_json_rpc_request("qsc_head", {"full": True}),
            {"jsonrpc": "2.0", "id": 1, "method": "qsc_head", "params": {"full": True}},
        )

    def test_builds_json_rpc_batch(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        batch = client.build_json_rpc_batch([("qsc_head", {}), ("qsc_tps", {"window": 10})])
        self.assertEqual(len(batch), 2)
        self.assertEqual(batch[0]["method"], "qsc_head")
        self.assertEqual(batch[1]["method"], "qsc_tps")
        self.assertEqual(batch[0]["id"], 1)
        self.assertEqual(batch[1]["id"], 2)

    def test_build_network_overview_batch(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        batch = client.build_network_overview_batch()
        methods = [r["method"] for r in batch]
        self.assertEqual(
            methods,
            ["qsc_chain", "qsc_head", "qsc_stateRoot", "qsc_tps", "qsc_gasEstimate"],
        )

    def test_build_transaction_details_batch(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        batch = client.build_transaction_details_batch("deadbeef")
        self.assertEqual(len(batch), 3)
        self.assertEqual(batch[0]["params"]["tx_hash"], "deadbeef")

    def test_build_address_details_batch(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        batch = client.build_address_details_batch("alice")
        self.assertEqual(len(batch), 2)
        self.assertEqual(batch[0]["params"]["address"], "alice")

    def test_jwt_auth_headers(self) -> None:
        client = DilithiaClient(
            "https://rpc.example/rpc",
            jwt="secret-token",
            headers={"x-network": "devnet"},
        )
        h = client.build_auth_headers({"accept": "application/json"})
        self.assertEqual(h["Authorization"], "Bearer secret-token")
        self.assertEqual(h["x-network"], "devnet")
        self.assertEqual(h["accept"], "application/json")

    def test_ws_connection_info(self) -> None:
        client = DilithiaClient(
            "https://rpc.example/rpc",
            jwt="token",
            headers={"x-network": "devnet"},
        )
        info = client.get_ws_connection_info()
        self.assertEqual(info["url"], "wss://rpc.example")
        self.assertEqual(info["headers"]["Authorization"], "Bearer token")

    def test_paymaster_and_forwarder(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        sponsored = client.with_paymaster(
            {"contract": "wasm:amm", "method": "swap"}, "gas_sponsor"
        )
        self.assertEqual(sponsored["paymaster"], "gas_sponsor")

        forwarder = client.build_forwarder_call(
            "wasm:forwarder",
            {"user": "alice", "nonce": 1},
            paymaster="gas_sponsor",
        )
        self.assertEqual(forwarder["contract"], "wasm:forwarder")
        self.assertEqual(forwarder["method"], "forward")
        self.assertEqual(forwarder["paymaster"], "gas_sponsor")

    def test_send_signed_call_merges_signer_payload(self) -> None:
        client = DilithiaClient("http://rpc.example")

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": f"signed:{payload_json}", "pk": "pk", "alg": "mldsa65"}

        sent: dict = {}

        def fake_send_call(call: dict) -> dict:
            sent.update(call)
            return {"tx_hash": "0xabc"}

        client.send_call = fake_send_call  # type: ignore[method-assign]
        result = client.send_signed_call(
            {"from": "a", "contract": "token"}, FakeSigner()
        )
        self.assertEqual(result, {"tx_hash": "0xabc"})
        self.assertEqual(sent["pk"], "pk")
        self.assertEqual(sent["alg"], "mldsa65")
        self.assertTrue(sent["sig"].startswith("signed:"))

    def test_explorer_rpc_helpers_delegate_to_json_rpc(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        calls: list[tuple[str, dict]] = []

        def fake_json_rpc(method: str, params: dict | None = None) -> dict:
            calls.append((method, params or {}))
            return {"ok": True}

        client.json_rpc = fake_json_rpc  # type: ignore[method-assign]
        client.get_head()
        client.get_chain()
        client.get_state_root()
        client.get_tps()
        client.get_block(42)
        client.get_blocks(10, 12)
        client.get_tx_block("abc")
        client.get_internal_txs("abc")
        client.get_address_txs("alice")
        client.search_hash("deadbeef")

        self.assertEqual(
            calls,
            [
                ("qsc_head", {}),
                ("qsc_chain", {}),
                ("qsc_stateRoot", {}),
                ("qsc_tps", {}),
                ("qsc_getBlock", {"height": 42}),
                ("qsc_getBlocks", {"from": 10, "to": 12}),
                ("qsc_getTxBlock", {"tx_hash": "abc"}),
                ("qsc_internalTxs", {"tx_hash": "abc"}),
                ("qsc_getAddressTxs", {"address": "alice"}),
                ("qsc_search", {"hash": "deadbeef"}),
            ],
        )

    def test_json_rpc_batch_parses_results(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")

        def fake_post_json(_pathname: str, body):
            self.assertIsInstance(body, list)
            return [
                {"jsonrpc": "2.0", "id": 1, "result": {"chain_id": "dili-devnet"}},
                {"jsonrpc": "2.0", "id": 2, "result": {"height": 42}},
            ]

        client._post_json = fake_post_json  # type: ignore[method-assign]
        result = client.json_rpc_batch([("qsc_chain", {}), ("qsc_head", {})])
        self.assertEqual(result, [{"chain_id": "dili-devnet"}, {"height": 42}])

    def test_gas_sponsor_connector(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        sponsor = DilithiaGasSponsorConnector(
            client, "wasm:gas_sponsor", paymaster="gas_sponsor"
        )
        self.assertEqual(
            sponsor.build_remaining_quota_query("alice"),
            {
                "contract": "wasm:gas_sponsor",
                "method": "remaining_quota",
                "args": {"user": "alice"},
            },
        )
        applied = sponsor.apply_paymaster(
            {"contract": "wasm:amm", "method": "swap", "args": {}}
        )
        self.assertEqual(applied["paymaster"], "gas_sponsor")

    def test_messaging_connector(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        messaging = DilithiaMessagingConnector(
            client, "wasm:messaging", paymaster="gas_sponsor"
        )
        outbound = messaging.build_send_message_call("ethereum", {"amount": 1})
        self.assertEqual(outbound["method"], "send_message")
        self.assertEqual(outbound["paymaster"], "gas_sponsor")
        inbound = messaging.build_receive_message_call(
            "ethereum", "bridge", {"tx": "0xabc"}
        )
        self.assertEqual(inbound["method"], "receive_message")
        self.assertEqual(inbound["args"]["source_chain"], "ethereum")


# ---------------------------------------------------------------------------
# Async client
# ---------------------------------------------------------------------------


class AsyncClientTests(unittest.IsolatedAsyncioTestCase):
    """Tests for AsyncDilithiaClient."""

    async def test_async_client_ws_info(self) -> None:
        client = AsyncDilithiaClient(
            "https://rpc.example/rpc", jwt="token", headers={"x-network": "devnet"}
        )
        info = client.get_ws_connection_info()
        self.assertEqual(info["url"], "wss://rpc.example")
        self.assertEqual(info["headers"]["Authorization"], "Bearer token")
        await client.aclose()

    async def test_async_json_rpc_batch_parses_results(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example/rpc")

        async def fake_post_json(_pathname: str, body):
            self.assertIsInstance(body, list)
            return [
                {"jsonrpc": "2.0", "id": 1, "result": {"chain_id": "dili-devnet"}},
                {"jsonrpc": "2.0", "id": 2, "result": {"height": 42}},
            ]

        client._post_json = fake_post_json  # type: ignore[method-assign]
        result = await client.json_rpc_batch([("qsc_chain", {}), ("qsc_head", {})])
        self.assertEqual(result, [{"chain_id": "dili-devnet"}, {"height": 42}])
        await client.aclose()

    async def test_async_context_manager(self) -> None:
        async with AsyncDilithiaClient("http://rpc.example/rpc") as client:
            self.assertIsNotNone(client.rpc_url)


# ---------------------------------------------------------------------------
# Crypto adapter loader
# ---------------------------------------------------------------------------


class CryptoTests(unittest.TestCase):
    """Tests for the native crypto adapter loading."""

    def test_loader_returns_none_when_bridge_unavailable(self) -> None:
        self.assertIsNone(load_native_crypto_adapter())

    def test_loader_returns_adapter_when_module_available(self) -> None:
        class FakeModule:
            pass

        with patch("dilithia_sdk.crypto.import_module", return_value=FakeModule):
            adapter = load_native_crypto_adapter()
        self.assertIsNotNone(adapter)

    def test_native_crypto_adapter_delegates(self) -> None:
        class FakeModule:
            @staticmethod
            def generate_mnemonic() -> str:
                return "word " * 24

            @staticmethod
            def validate_mnemonic(_mnemonic: str) -> None:
                return None

            @staticmethod
            def address_from_public_key(_pk: str) -> str:
                return "derived-address"

            @staticmethod
            def sign_message(_sk: str, _msg: str) -> dict:
                return {"algorithm": "mldsa65", "signature": "deadbeef"}

            @staticmethod
            def verify_message(_pk: str, _msg: str, _sig: str) -> bool:
                return True

            @staticmethod
            def recover_wallet_file(*_args) -> dict:
                return {
                    "address": "addr-1",
                    "public_key": "pk-1",
                    "secret_key": "sk-1",
                    "account_index": 0,
                    "wallet_file": {"version": 1},
                }

        adapter = NativeCryptoAdapter(FakeModule)
        self.assertEqual(adapter.address_from_public_key("pk"), "derived-address")
        self.assertEqual(adapter.sign_message("sk", "msg").signature, "deadbeef")
        self.assertTrue(adapter.verify_message("pk", "msg", "sig"))
        recovered = adapter.recover_wallet_file({"version": 1}, "mnemonic", "password")
        self.assertEqual(recovered.address, "addr-1")


# ---------------------------------------------------------------------------
# Cross-language canonical payload consistency (shared test vectors)
# ---------------------------------------------------------------------------


import json
from pathlib import Path


def _load_vectors() -> dict:
    vectors_path = Path(__file__).resolve().parent.parent.parent / "tests" / "vectors" / "canonical_payloads.json"
    with open(vectors_path) as f:
        return json.load(f)


class CrossLanguageCanonicalPayloadTests(unittest.TestCase):
    """Verify canonical payloads match shared cross-language test vectors."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.vectors = _load_vectors()

    def test_contract_call_canonical_matches_vectors(self) -> None:
        v = self.vectors["contract_call"]
        client = DilithiaClient("http://rpc.example")
        call = client.build_contract_call(
            v["input"]["contract"], v["input"]["method"], v["input"]["args"]
        )
        canonical = json.dumps(call, sort_keys=True, separators=(",", ":"))
        self.assertEqual(
            canonical,
            v["expected_json"],
            "contract_call canonical JSON must match cross-language vector",
        )

        # Verify key ordering
        parsed = json.loads(canonical)
        keys = list(parsed.keys())
        self.assertEqual(keys, v["expected_keys_order"])

    def test_deploy_canonical_payload_matches_vectors(self) -> None:
        v = self.vectors["deploy_canonical"]
        client = DilithiaClient("http://rpc.example")
        payload = client.build_deploy_canonical_payload(
            v["input"]["from"],
            v["input"]["name"],
            v["input"]["bytecode_hash"],
            v["input"]["nonce"],
            v["input"]["chain_id"],
        )
        canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        self.assertEqual(
            canonical,
            v["expected_json"],
            "deploy_canonical canonical JSON must match cross-language vector",
        )

        # Verify key ordering
        keys = list(json.loads(canonical).keys())
        self.assertEqual(keys, v["expected_keys_order"])

    def test_with_paymaster_matches_vectors(self) -> None:
        v = self.vectors["with_paymaster"]
        client = DilithiaClient("http://rpc.example")
        call = client.build_contract_call(
            v["input"]["contract"], v["input"]["method"], v["input"]["args"]
        )
        sponsored = client.with_paymaster(call, v["input"]["paymaster"])
        self.assertIn("paymaster", sponsored)
        self.assertEqual(sponsored["paymaster"], v["input"]["paymaster"])
        self.assertEqual("paymaster" in sponsored, v["expected_has_paymaster"])


# ---------------------------------------------------------------------------
# Mock HTTP tests for DilithiaClient (sync)
# ---------------------------------------------------------------------------


class SyncClientHttpTests(unittest.TestCase):
    """Test all sync client HTTP methods with mocked httpx."""

    def _client(self):
        return DilithiaClient("http://rpc.example")

    # -- balance --

    def test_balance_returns_typed_balance(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"address": "dili1alice", "balance": "1000000000000000000"})
        bal = client.balance("dili1alice")
        self.assertIsInstance(bal, Balance)
        self.assertEqual(bal.address.value, "dili1alice")
        self.assertEqual(bal.balance.to_raw(), 1000000000000000000)

    # -- nonce --

    def test_nonce_returns_typed_nonce(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"nonce": 5})
        n = client.nonce("dili1alice")
        self.assertIsInstance(n, Nonce)
        self.assertEqual(n.address.value, "dili1alice")
        self.assertEqual(n.next_nonce, 5)

    # -- receipt --

    def test_receipt_returns_typed_receipt(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={
            "tx_hash": "0xabc", "block_height": 42, "status": "success",
            "gas_used": 100, "fee_paid": 50,
        })
        r = client.receipt(TxHash(value="0xabc"))
        self.assertIsInstance(r, Receipt)
        self.assertEqual(r.tx_hash.value, "0xabc")
        self.assertEqual(r.block_height, 42)
        self.assertEqual(r.status, "success")
        self.assertEqual(r.gas_used, 100)
        self.assertEqual(r.fee_paid, 50)

    def test_receipt_accepts_string(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={
            "tx_hash": "0xdef", "block_height": 1, "status": "success",
        })
        r = client.receipt("0xdef")
        self.assertEqual(r.tx_hash.value, "0xdef")

    # -- network_info --

    def test_network_info_returns_typed(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1,
            "result": {"chain_id": "dili-devnet", "block_height": 100, "base_fee": 10},
        })
        info = client.network_info()
        self.assertIsInstance(info, NetworkInfo)
        self.assertEqual(info.chain_id, "dili-devnet")
        self.assertEqual(info.block_height, 100)
        self.assertEqual(info.base_fee, 10)

    # -- gas_estimate --

    def test_gas_estimate_returns_typed(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1,
            "result": {"gas_limit": 21000, "base_fee": 5, "estimated_cost": 105000},
        })
        est = client.gas_estimate()
        self.assertIsInstance(est, GasEstimate)
        self.assertEqual(est.gas_limit, 21000)
        self.assertEqual(est.base_fee, 5)
        self.assertEqual(est.estimated_cost, 105000)

    # -- json_rpc --

    def test_json_rpc_returns_result(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"ok": True},
        })
        result = client.json_rpc("qsc_method", {})
        self.assertEqual(result, {"ok": True})

    def test_json_rpc_raises_rpc_error(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1,
            "error": {"code": -32600, "message": "Invalid request"},
        })
        with self.assertRaises(RpcError) as ctx:
            client.json_rpc("bad_method", {})
        self.assertEqual(ctx.exception.code, -32600)

    # -- query_contract --

    def test_query_contract_returns_query_result(self) -> None:
        client = self._client()
        client._get_absolute_json = MagicMock(return_value={"totalSupply": "1000000"})
        qr = client.query_contract("token", "totalSupply")
        self.assertIsInstance(qr, QueryResult)
        self.assertEqual(qr.value["totalSupply"], "1000000")

    # -- simulate --

    def test_simulate_returns_dict(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"gas_used": 100, "result": "ok"})
        result = client.simulate({"contract": "token", "method": "transfer"})
        self.assertEqual(result["gas_used"], 100)

    # -- send_call --

    def test_send_call_returns_dict(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"tx_hash": "0xabc"})
        result = client.send_call({"contract": "token", "method": "transfer"})
        self.assertEqual(result["tx_hash"], "0xabc")

    # -- send_signed_call --

    def test_send_signed_call_rejects_non_dict_signer(self) -> None:
        client = self._client()
        client.send_call = MagicMock(return_value={"tx_hash": "0x1"})

        class BadSigner:
            @staticmethod
            def sign_canonical_payload(_json: str):
                return "not-a-dict"

        with self.assertRaises(ValidationError):
            client.send_signed_call({"from": "a"}, BadSigner())

    # -- call_contract --

    def test_call_contract_delegates(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"tx_hash": "0x1"})
        result = client.call_contract("token", "transfer", {"to": "bob", "amount": 1})
        self.assertEqual(result["tx_hash"], "0x1")

    def test_call_contract_with_paymaster(self) -> None:
        client = self._client()
        sent = {}

        def capture_send(call):
            sent.update(call)
            return {"tx_hash": "0x2"}

        client.send_call = capture_send  # type: ignore[method-assign]
        client.call_contract("token", "transfer", {"to": "bob"}, paymaster="sponsor")
        self.assertEqual(sent["paymaster"], "sponsor")

    # -- deploy_contract / upgrade_contract --

    def test_deploy_contract(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"tx_hash": "0xdep"})
        result = client.deploy_contract({"name": "mycontract", "bytecode": "00"})
        self.assertEqual(result["tx_hash"], "0xdep")

    def test_upgrade_contract(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"tx_hash": "0xupg"})
        result = client.upgrade_contract({"name": "mycontract", "bytecode": "01"})
        self.assertEqual(result["tx_hash"], "0xupg")

    # -- shielded --

    def test_shielded_deposit(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"tx_hash": "0xsd"})
        result = client.shielded_deposit("commitment_hash", 1000, "proof_hex")
        self.assertEqual(result["tx_hash"], "0xsd")

    def test_shielded_withdraw(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={"tx_hash": "0xsw"})
        result = client.shielded_withdraw("null", 500, "recipient", "proof", "root")
        self.assertEqual(result["tx_hash"], "0xsw")

    def test_commitment_root(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"commitment_root": "0xroot"})
        self.assertEqual(client.commitment_root(), "0xroot")

    def test_commitment_root_fallback_key(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"root": "0xfallback"})
        self.assertEqual(client.commitment_root(), "0xfallback")

    def test_is_nullifier_spent_true(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"spent": True})
        self.assertTrue(client.is_nullifier_spent("null1"))

    def test_is_nullifier_spent_false(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"spent": False})
        self.assertFalse(client.is_nullifier_spent("null2"))

    # -- resolve_name / reverse_resolve --

    def test_resolve_name(self) -> None:
        client = self._client()
        client._get_absolute_json = MagicMock(return_value={"name": "alice.dili", "address": "dili1alice"})
        nr = client.resolve_name("alice.dili")
        self.assertIsInstance(nr, NameRecord)
        self.assertEqual(nr.name, "alice.dili")
        self.assertEqual(nr.address.value, "dili1alice")

    def test_reverse_resolve(self) -> None:
        client = self._client()
        client._get_absolute_json = MagicMock(return_value={"name": "alice.dili", "address": "dili1alice"})
        nr = client.reverse_resolve("dili1alice")
        self.assertEqual(nr.name, "alice.dili")

    def test_reverse_resolve_accepts_address_obj(self) -> None:
        client = self._client()
        client._get_absolute_json = MagicMock(return_value={"name": "bob.dili", "address": "dili1bob"})
        nr = client.reverse_resolve(Address.of("dili1bob"))
        self.assertEqual(nr.name, "bob.dili")

    # -- wait_for_receipt --

    def test_wait_for_receipt_succeeds_on_second_try(self) -> None:
        client = self._client()
        call_count = 0

        def fake_receipt(tx_hash):
            nonlocal call_count
            call_count += 1
            if call_count < 2:
                raise HttpError(status_code=404, body="not found")
            return Receipt(
                tx_hash=TxHash(value="0xtx"), block_height=10, status="success"
            )

        client.receipt = fake_receipt  # type: ignore[method-assign]
        with patch("time.sleep"):
            r = client.wait_for_receipt("0xtx", max_attempts=5, delay=0.0)
        self.assertEqual(r.status, "success")

    def test_wait_for_receipt_timeout(self) -> None:
        client = self._client()

        def always_404(_tx_hash):
            raise HttpError(status_code=404, body="not found")

        client.receipt = always_404  # type: ignore[method-assign]
        with patch("time.sleep"):
            with self.assertRaises(TimeoutError):
                client.wait_for_receipt("0xtx", max_attempts=2, delay=0.0)

    def test_wait_for_receipt_reraises_non_404(self) -> None:
        client = self._client()

        def server_error(_tx_hash):
            raise HttpError(status_code=500, body="internal error")

        client.receipt = server_error  # type: ignore[method-assign]
        with self.assertRaises(HttpError) as ctx:
            client.wait_for_receipt("0xtx", max_attempts=2, delay=0.0)
        self.assertEqual(ctx.exception.status_code, 500)

    # -- name service extra methods --

    def test_lookup_name(self) -> None:
        client = self._client()
        client.query_contract = MagicMock(return_value=QueryResult(value={"name": "alice.dili", "owner": "dili1alice"}))
        result = client.lookup_name("alice.dili")
        self.assertIsNotNone(result)

    def test_is_name_available(self) -> None:
        client = self._client()
        client.query_contract = MagicMock(return_value=QueryResult(value={"available": True}))
        result = client.is_name_available("newname.dili")
        self.assertIsNotNone(result)

    def test_get_names_by_owner(self) -> None:
        client = self._client()
        client._get_absolute_json = MagicMock(return_value={"names": ["alice.dili"]})
        result = client.get_names_by_owner("dili1alice")
        self.assertEqual(result["names"], ["alice.dili"])

    # -- send_sponsored_call --

    def test_send_sponsored_call(self) -> None:
        client = self._client()
        sent = {}

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": "s", "pk": "pk", "alg": "mldsa65"}

        def fake_send_call(call):
            sent.update(call)
            return {"tx_hash": "0xsponsored"}

        client.send_call = fake_send_call  # type: ignore[method-assign]
        result = client.send_sponsored_call(
            {"contract": "token", "method": "transfer"}, "gas_sponsor", FakeSigner()
        )
        self.assertEqual(result["tx_hash"], "0xsponsored")
        self.assertEqual(sent["paymaster"], "gas_sponsor")

    # -- raw helpers --

    def test_raw_rpc_delegates_to_json_rpc(self) -> None:
        client = self._client()
        calls = []

        def fake_json_rpc(method, params=None):
            calls.append((method, params))
            return {"ok": True}

        client.json_rpc = fake_json_rpc  # type: ignore[method-assign]
        client.raw_rpc("qsc_head", {})
        self.assertEqual(calls, [("qsc_head", {})])

    def test_raw_get(self) -> None:
        client = self._client()
        client._get_absolute_json = MagicMock(return_value={"data": "ok"})
        result = client.raw_get("/custom/path")
        self.assertEqual(result["data"], "ok")

    def test_raw_get_with_chain_base(self) -> None:
        client = DilithiaClient("http://rpc.example", chain_base_url="http://chain.example")
        client._get_absolute_json = MagicMock(return_value={"data": "chain"})
        client.raw_get("/info", use_chain_base=True)
        # Verify the URL starts with chain base
        called_url = client._get_absolute_json.call_args[0][0]
        self.assertTrue(called_url.startswith("http://chain.example"))

    def test_raw_post(self) -> None:
        client = self._client()
        client._post_absolute_json = MagicMock(return_value={"status": "ok"})
        result = client.raw_post("/custom", {"key": "val"})
        self.assertEqual(result["status"], "ok")

    # -- explorer convenience extras --

    def test_get_address_summary(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"address": "alice", "balance": 100}
        })
        result = client.get_address_summary("alice")
        self.assertEqual(result["address"], "alice")

    def test_get_gas_estimate_rpc(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"gas": 100}
        })
        result = client.get_gas_estimate()
        self.assertEqual(result["gas"], 100)

    def test_get_base_fee(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"base_fee": 5}
        })
        result = client.get_base_fee()
        self.assertEqual(result["base_fee"], 5)

    def test_query_contract_abi(self) -> None:
        client = self._client()
        client._post_json = MagicMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"methods": ["transfer"]}
        })
        result = client.query_contract_abi("token")
        self.assertEqual(result["methods"], ["transfer"])

    # -- legacy REST reads --

    def test_get_balance_raw(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"address": "a", "balance": "100"})
        result = client.get_balance("a")
        self.assertEqual(result["balance"], "100")

    def test_get_nonce_raw(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"nonce": 3})
        result = client.get_nonce("a")
        self.assertEqual(result["nonce"], 3)

    def test_get_receipt_raw(self) -> None:
        client = self._client()
        client._get_json = MagicMock(return_value={"tx_hash": "0x1", "status": "success"})
        result = client.get_receipt("0x1")
        self.assertEqual(result["status"], "success")


# ---------------------------------------------------------------------------
# HTTP error handling (sync)
# ---------------------------------------------------------------------------


class SyncClientErrorTests(unittest.TestCase):
    """Test HTTP error → SDK exception translation for sync client."""

    def test_http_404_raises_http_error(self) -> None:
        client = DilithiaClient("http://rpc.example")
        resp = _mock_response({"error": "not found"}, status_code=404)
        with patch.object(client._http, "get", return_value=resp):
            with self.assertRaises(HttpError) as ctx:
                client.balance("dili1alice")
            self.assertEqual(ctx.exception.status_code, 404)

    def test_http_500_raises_http_error(self) -> None:
        client = DilithiaClient("http://rpc.example")
        resp = _mock_response({"error": "server error"}, status_code=500)
        with patch.object(client._http, "get", return_value=resp):
            with self.assertRaises(HttpError) as ctx:
                client.balance("dili1alice")
            self.assertEqual(ctx.exception.status_code, 500)

    def test_timeout_raises_timeout_error(self) -> None:
        client = DilithiaClient("http://rpc.example")
        with patch.object(
            client._http, "get", side_effect=httpx.TimeoutException("timed out")
        ):
            with self.assertRaises(TimeoutError):
                client.balance("dili1alice")

    def test_post_http_error(self) -> None:
        client = DilithiaClient("http://rpc.example")
        resp = _mock_response({"error": "bad"}, status_code=400)
        with patch.object(client._http, "post", return_value=resp):
            with self.assertRaises(HttpError) as ctx:
                client.send_call({"contract": "token", "method": "transfer"})
            self.assertEqual(ctx.exception.status_code, 400)

    def test_post_timeout_error(self) -> None:
        client = DilithiaClient("http://rpc.example")
        with patch.object(
            client._http, "post", side_effect=httpx.TimeoutException("timed out")
        ):
            with self.assertRaises(TimeoutError):
                client.send_call({"contract": "token", "method": "transfer"})

    def test_rpc_error_response(self) -> None:
        client = DilithiaClient("http://rpc.example")
        rpc_resp = {
            "jsonrpc": "2.0", "id": 1,
            "error": {"code": -32601, "message": "Method not found"},
        }
        resp = _mock_response(rpc_resp)
        with patch.object(client._http, "post", return_value=resp):
            with self.assertRaises(RpcError) as ctx:
                client.json_rpc("nonexistent_method", {})
            self.assertEqual(ctx.exception.code, -32601)
            self.assertEqual(ctx.exception.message, "Method not found")


# ---------------------------------------------------------------------------
# Context manager tests (sync)
# ---------------------------------------------------------------------------


class SyncContextManagerTests(unittest.TestCase):
    """Test sync client as context manager."""

    def test_client_context_manager(self) -> None:
        with DilithiaClient("http://rpc.example") as client:
            self.assertIsNotNone(client)
            self.assertEqual(client.rpc_url, "http://rpc.example")

    def test_close_called_on_exit(self) -> None:
        client = DilithiaClient("http://rpc.example")
        with patch.object(client, "close") as mock_close:
            with client:
                pass
            mock_close.assert_called_once()


# ---------------------------------------------------------------------------
# Batch JSON-RPC error handling
# ---------------------------------------------------------------------------


class BatchRpcTests(unittest.TestCase):
    """Test batch JSON-RPC error handling."""

    def test_batch_error_raises_rpc_error(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value=[
            {"jsonrpc": "2.0", "id": 1, "result": {"ok": True}},
            {"jsonrpc": "2.0", "id": 2, "error": {"code": -32600, "message": "bad"}},
        ])
        with self.assertRaises(RpcError):
            client.json_rpc_batch([("method1", {}), ("method2", {})])


# ---------------------------------------------------------------------------
# Response parser edge cases
# ---------------------------------------------------------------------------


class ResponseParserTests(unittest.TestCase):
    """Test _ClientBase static response parsers."""

    def test_parse_balance_uses_value_key(self) -> None:
        from dilithia_sdk.client import _ClientBase
        bal = _ClientBase._parse_balance({"value": 2000}, Address.of("addr"))
        self.assertEqual(bal.balance.to_raw(), 2000)

    def test_parse_nonce_default_zero(self) -> None:
        from dilithia_sdk.client import _ClientBase
        n = _ClientBase._parse_nonce({}, Address.of("addr"))
        self.assertEqual(n.next_nonce, 0)

    def test_parse_receipt_with_error(self) -> None:
        from dilithia_sdk.client import _ClientBase
        r = _ClientBase._parse_receipt({
            "tx_hash": "0x1", "block_height": 5, "status": "failed", "error": "revert"
        })
        self.assertEqual(r.error, "revert")
        self.assertEqual(r.status, "failed")

    def test_parse_network_info_height_fallback(self) -> None:
        from dilithia_sdk.client import _ClientBase
        info = _ClientBase._parse_network_info({"chain_id": "x", "height": 99})
        self.assertEqual(info.block_height, 99)

    def test_parse_gas_estimate_defaults(self) -> None:
        from dilithia_sdk.client import _ClientBase
        est = _ClientBase._parse_gas_estimate({})
        self.assertEqual(est.gas_limit, 0)
        self.assertEqual(est.base_fee, 0)
        self.assertEqual(est.estimated_cost, 0)

    def test_parse_name_record_defaults(self) -> None:
        from dilithia_sdk.client import _ClientBase
        nr = _ClientBase._parse_name_record({})
        self.assertEqual(nr.name, "")
        self.assertEqual(nr.address.value, "")

    def test_to_address_passthrough(self) -> None:
        from dilithia_sdk.client import _ClientBase
        addr = Address.of("test")
        self.assertIs(_ClientBase._to_address(addr), addr)

    def test_to_tx_hash_passthrough(self) -> None:
        from dilithia_sdk.client import _ClientBase
        h = TxHash(value="0xabc")
        self.assertIs(_ClientBase._to_tx_hash(h), h)

    def test_parse_json_rpc_response_empty_result(self) -> None:
        from dilithia_sdk.client import _ClientBase
        result = _ClientBase._parse_json_rpc_response({"jsonrpc": "2.0", "id": 1}, "m")
        self.assertEqual(result, {})


# ---------------------------------------------------------------------------
# Mock HTTP tests for AsyncDilithiaClient
# ---------------------------------------------------------------------------


class AsyncClientHttpTests(unittest.IsolatedAsyncioTestCase):
    """Test async client HTTP methods with mocked internals."""

    async def test_async_balance(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"address": "dili1alice", "balance": "5000"})
        bal = await client.balance("dili1alice")
        self.assertIsInstance(bal, Balance)
        self.assertEqual(bal.address.value, "dili1alice")
        self.assertEqual(bal.balance.to_raw(), 5000)
        await client.aclose()

    async def test_async_nonce(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"nonce": 7})
        n = await client.nonce("dili1alice")
        self.assertEqual(n.next_nonce, 7)
        await client.aclose()

    async def test_async_receipt(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={
            "tx_hash": "0xabc", "block_height": 10, "status": "success"
        })
        r = await client.receipt("0xabc")
        self.assertEqual(r.tx_hash.value, "0xabc")
        await client.aclose()

    async def test_async_network_info(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={
            "jsonrpc": "2.0", "id": 1,
            "result": {"chain_id": "dili-devnet", "block_height": 50, "base_fee": 1},
        })
        info = await client.network_info()
        self.assertEqual(info.chain_id, "dili-devnet")
        await client.aclose()

    async def test_async_gas_estimate(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={
            "jsonrpc": "2.0", "id": 1,
            "result": {"gas_limit": 21000, "base_fee": 5, "estimated_cost": 105000},
        })
        est = await client.gas_estimate()
        self.assertEqual(est.gas_limit, 21000)
        await client.aclose()

    async def test_async_json_rpc(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"data": 123}
        })
        result = await client.json_rpc("some_method", {})
        self.assertEqual(result["data"], 123)
        await client.aclose()

    async def test_async_query_contract(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"supply": "999"})
        qr = await client.query_contract("token", "totalSupply")
        self.assertEqual(qr.value["supply"], "999")
        await client.aclose()

    async def test_async_send_call(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xtx"})
        result = await client.send_call({"contract": "token", "method": "transfer"})
        self.assertEqual(result["tx_hash"], "0xtx")
        await client.aclose()

    async def test_async_send_signed_call(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xsigned"})

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": "s", "pk": "pk", "alg": "mldsa65"}

        result = await client.send_signed_call({"from": "a"}, FakeSigner())
        self.assertEqual(result["tx_hash"], "0xsigned")
        await client.aclose()

    async def test_async_send_signed_call_rejects_non_dict(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")

        class BadSigner:
            @staticmethod
            def sign_canonical_payload(_json: str):
                return "not-a-dict"

        with self.assertRaises(ValidationError):
            await client.send_signed_call({"from": "a"}, BadSigner())
        await client.aclose()

    async def test_async_send_sponsored_call(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xsp"})

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": "s", "pk": "pk", "alg": "mldsa65"}

        result = await client.send_sponsored_call(
            {"contract": "t", "method": "m"}, "sponsor", FakeSigner()
        )
        self.assertEqual(result["tx_hash"], "0xsp")
        await client.aclose()

    async def test_async_call_contract(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xcc"})
        result = await client.call_contract("token", "transfer", {"to": "bob"})
        self.assertEqual(result["tx_hash"], "0xcc")
        await client.aclose()

    async def test_async_simulate(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"gas_used": 50})
        result = await client.simulate({"contract": "t", "method": "m"})
        self.assertEqual(result["gas_used"], 50)
        await client.aclose()

    async def test_async_deploy_contract(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xdep"})
        result = await client.deploy_contract({"name": "c", "bytecode": "00"})
        self.assertEqual(result["tx_hash"], "0xdep")
        await client.aclose()

    async def test_async_upgrade_contract(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xupg"})
        result = await client.upgrade_contract({"name": "c", "bytecode": "01"})
        self.assertEqual(result["tx_hash"], "0xupg")
        await client.aclose()

    async def test_async_shielded_deposit(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xsd"})
        result = await client.shielded_deposit("cm", 1000, "proof")
        self.assertEqual(result["tx_hash"], "0xsd")
        await client.aclose()

    async def test_async_shielded_withdraw(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xsw"})
        result = await client.shielded_withdraw("n", 500, "r", "p", "root")
        self.assertEqual(result["tx_hash"], "0xsw")
        await client.aclose()

    async def test_async_commitment_root(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"commitment_root": "0xcr"})
        self.assertEqual(await client.commitment_root(), "0xcr")
        await client.aclose()

    async def test_async_is_nullifier_spent(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"spent": True})
        self.assertTrue(await client.is_nullifier_spent("null"))
        await client.aclose()

    async def test_async_resolve_name(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"name": "a.dili", "address": "dili1a"})
        nr = await client.resolve_name("a.dili")
        self.assertEqual(nr.name, "a.dili")
        await client.aclose()

    async def test_async_reverse_resolve(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"name": "a.dili", "address": "dili1a"})
        nr = await client.reverse_resolve("dili1a")
        self.assertEqual(nr.name, "a.dili")
        await client.aclose()

    async def test_async_lookup_name(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client.query_contract = AsyncMock(return_value=QueryResult(value={"name": "a.dili", "owner": "x"}))
        result = await client.lookup_name("a.dili")
        self.assertIsNotNone(result)
        await client.aclose()

    async def test_async_is_name_available(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client.query_contract = AsyncMock(return_value=QueryResult(value={"available": True}))
        result = await client.is_name_available("new.dili")
        self.assertIsNotNone(result)
        await client.aclose()

    async def test_async_get_names_by_owner(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"names": ["a.dili"]})
        result = await client.get_names_by_owner("dili1a")
        self.assertEqual(result["names"], ["a.dili"])
        await client.aclose()

    async def test_async_wait_for_receipt_success(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        call_count = 0

        async def fake_receipt(tx_hash):
            nonlocal call_count
            call_count += 1
            if call_count < 2:
                raise HttpError(status_code=404, body="not found")
            return Receipt(
                tx_hash=TxHash(value="0xtx"), block_height=10, status="success"
            )

        client.receipt = fake_receipt  # type: ignore[method-assign]
        with patch("asyncio.sleep", new_callable=AsyncMock):
            r = await client.wait_for_receipt("0xtx", max_attempts=5, delay=0.0)
        self.assertEqual(r.status, "success")
        await client.aclose()

    async def test_async_wait_for_receipt_timeout(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")

        async def always_404(_tx_hash):
            raise HttpError(status_code=404, body="not found")

        client.receipt = always_404  # type: ignore[method-assign]
        with patch("asyncio.sleep", new_callable=AsyncMock):
            with self.assertRaises(TimeoutError):
                await client.wait_for_receipt("0xtx", max_attempts=2, delay=0.0)
        await client.aclose()

    async def test_async_wait_for_receipt_reraises_non_404(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")

        async def server_error(_tx_hash):
            raise HttpError(status_code=500, body="internal error")

        client.receipt = server_error  # type: ignore[method-assign]
        with self.assertRaises(HttpError) as ctx:
            await client.wait_for_receipt("0xtx", max_attempts=2, delay=0.0)
        self.assertEqual(ctx.exception.status_code, 500)
        await client.aclose()

    # -- raw helpers (async) --

    async def test_async_raw_rpc(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={
            "jsonrpc": "2.0", "id": 1, "result": {"ok": True}
        })
        result = await client.raw_rpc("qsc_head", {})
        self.assertEqual(result["ok"], True)
        await client.aclose()

    async def test_async_raw_get(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"data": "ok"})
        result = await client.raw_get("/custom")
        self.assertEqual(result["data"], "ok")
        await client.aclose()

    async def test_async_raw_post(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_absolute_json = AsyncMock(return_value={"status": "ok"})
        result = await client.raw_post("/custom", {"key": "val"})
        self.assertEqual(result["status"], "ok")
        await client.aclose()

    # -- explorer methods (async) --

    async def test_async_explorer_methods(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        calls = []

        async def fake_json_rpc(method, params=None):
            calls.append((method, params or {}))
            return {"ok": True}

        client.json_rpc = fake_json_rpc  # type: ignore[method-assign]
        await client.get_head()
        await client.get_chain()
        await client.get_state_root()
        await client.get_tps()
        await client.get_gas_estimate()
        await client.get_base_fee()
        await client.get_block(42)
        await client.get_blocks(10, 20)
        await client.get_tx_block("0xabc")
        await client.get_internal_txs("0xabc")
        await client.get_address_txs("alice")
        await client.search_hash("0xdef")
        await client.query_contract_abi("token")
        await client.get_address_summary("alice")

        self.assertEqual(len(calls), 14)
        self.assertEqual(calls[0], ("qsc_head", {}))
        self.assertEqual(calls[6], ("qsc_getBlock", {"height": 42}))
        await client.aclose()

    # -- legacy REST reads (async) --

    async def test_async_get_balance_raw(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"balance": "100"})
        result = await client.get_balance("a")
        self.assertEqual(result["balance"], "100")
        await client.aclose()

    async def test_async_get_nonce_raw(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"nonce": 3})
        result = await client.get_nonce("a")
        self.assertEqual(result["nonce"], 3)
        await client.aclose()

    async def test_async_get_receipt_raw(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_json = AsyncMock(return_value={"tx_hash": "0x1", "status": "ok"})
        result = await client.get_receipt("0x1")
        self.assertEqual(result["status"], "ok")
        await client.aclose()


# ---------------------------------------------------------------------------
# Async HTTP error handling
# ---------------------------------------------------------------------------


class AsyncClientErrorTests(unittest.IsolatedAsyncioTestCase):
    """Test error handling for the async client at the httpx layer."""

    async def test_async_http_error(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        resp = _mock_response({"error": "not found"}, status_code=404)
        with patch.object(client._http, "get", new_callable=AsyncMock, return_value=resp):
            with self.assertRaises(HttpError) as ctx:
                await client.balance("dili1alice")
            self.assertEqual(ctx.exception.status_code, 404)
        await client.aclose()

    async def test_async_timeout_error(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        with patch.object(
            client._http, "get",
            new_callable=AsyncMock,
            side_effect=httpx.TimeoutException("timed out"),
        ):
            with self.assertRaises(TimeoutError):
                await client.balance("dili1alice")
        await client.aclose()

    async def test_async_post_http_error(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        resp = _mock_response({"error": "bad"}, status_code=400)
        with patch.object(client._http, "post", new_callable=AsyncMock, return_value=resp):
            with self.assertRaises(HttpError):
                await client.send_call({"contract": "t", "method": "m"})
        await client.aclose()

    async def test_async_post_timeout_error(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        with patch.object(
            client._http, "post",
            new_callable=AsyncMock,
            side_effect=httpx.TimeoutException("timed out"),
        ):
            with self.assertRaises(TimeoutError):
                await client.send_call({"contract": "t", "method": "m"})
        await client.aclose()


# ---------------------------------------------------------------------------
# GasSponsorConnector comprehensive tests
# ---------------------------------------------------------------------------


class GasSponsorConnectorTests(unittest.TestCase):
    """Comprehensive tests for DilithiaGasSponsorConnector."""

    def test_build_accept_query(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor", paymaster="sponsor")
        q = sponsor.build_accept_query("alice", "wasm:amm", "swap")
        self.assertEqual(q["contract"], "wasm:gas_sponsor")
        self.assertEqual(q["method"], "accept")
        self.assertEqual(q["args"]["user"], "alice")
        self.assertEqual(q["args"]["contract"], "wasm:amm")
        self.assertEqual(q["args"]["method"], "swap")

    def test_build_max_gas_per_user_query(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor")
        q = sponsor.build_max_gas_per_user_query()
        self.assertEqual(q["method"], "max_gas_per_user")

    def test_build_sponsor_token_query(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor")
        q = sponsor.build_sponsor_token_query()
        self.assertEqual(q["method"], "sponsor_token")

    def test_build_fund_call(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor")
        call = sponsor.build_fund_call(1000)
        self.assertEqual(call["method"], "fund")
        self.assertEqual(call["args"]["amount"], 1000)

    def test_apply_paymaster_without_paymaster(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor", paymaster=None)
        call = {"contract": "wasm:amm", "method": "swap"}
        result = sponsor.apply_paymaster(call)
        self.assertNotIn("paymaster", result)

    def test_apply_paymaster_with_paymaster(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor", paymaster="sponsor")
        call = {"contract": "wasm:amm", "method": "swap"}
        result = sponsor.apply_paymaster(call)
        self.assertEqual(result["paymaster"], "sponsor")

    def test_send_sponsored_call(self) -> None:
        client = DilithiaClient("http://rpc.example")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor", paymaster="sponsor")

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": "s", "pk": "pk", "alg": "mldsa65"}

        sent = {}

        def capture_send(call):
            sent.update(call)
            return {"tx_hash": "0xsp"}

        client.send_call = capture_send  # type: ignore[method-assign]
        result = sponsor.send_sponsored_call({"contract": "token"}, FakeSigner())
        self.assertEqual(result["tx_hash"], "0xsp")
        self.assertEqual(sent["paymaster"], "sponsor")


# ---------------------------------------------------------------------------
# MessagingConnector comprehensive tests
# ---------------------------------------------------------------------------


class MessagingConnectorTests(unittest.TestCase):
    """Comprehensive tests for DilithiaMessagingConnector."""

    def test_build_send_message_call_without_paymaster(self) -> None:
        client = DilithiaClient("http://rpc.example")
        msg = DilithiaMessagingConnector(client, "wasm:messaging", paymaster=None)
        call = msg.build_send_message_call("ethereum", {"amount": 1})
        self.assertEqual(call["method"], "send_message")
        self.assertNotIn("paymaster", call)

    def test_build_receive_message_call_without_paymaster(self) -> None:
        client = DilithiaClient("http://rpc.example")
        msg = DilithiaMessagingConnector(client, "wasm:messaging", paymaster=None)
        call = msg.build_receive_message_call("ethereum", "bridge", {"tx": "0xabc"})
        self.assertEqual(call["method"], "receive_message")
        self.assertEqual(call["args"]["source_contract"], "bridge")
        self.assertNotIn("paymaster", call)

    def test_build_send_message_call_with_string_payload(self) -> None:
        client = DilithiaClient("http://rpc.example")
        msg = DilithiaMessagingConnector(client, "wasm:messaging", paymaster="sponsor")
        call = msg.build_send_message_call("polygon", "raw_payload_string")
        self.assertEqual(call["args"]["payload"], "raw_payload_string")
        self.assertEqual(call["paymaster"], "sponsor")

    def test_query_outbox(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={"outbox": []})
        msg = DilithiaMessagingConnector(client, "wasm:messaging")
        result = msg.query_outbox()
        self.assertIsInstance(result, QueryResult)

    def test_query_inbox(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={"inbox": []})
        msg = DilithiaMessagingConnector(client, "wasm:messaging")
        result = msg.query_inbox()
        self.assertIsInstance(result, QueryResult)

    def test_send_message(self) -> None:
        client = DilithiaClient("http://rpc.example")
        msg = DilithiaMessagingConnector(client, "wasm:messaging", paymaster="sponsor")

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": "s", "pk": "pk", "alg": "mldsa65"}

        sent = {}

        def capture_send(call):
            sent.update(call)
            return {"tx_hash": "0xmsg"}

        client.send_call = capture_send  # type: ignore[method-assign]
        result = msg.send_message("ethereum", {"amount": 1}, FakeSigner())
        self.assertEqual(result["tx_hash"], "0xmsg")

    def test_apply_paymaster_without(self) -> None:
        client = DilithiaClient("http://rpc.example")
        msg = DilithiaMessagingConnector(client, "wasm:messaging", paymaster=None)
        call = {"contract": "x"}
        self.assertNotIn("paymaster", msg.apply_paymaster(call))


# ---------------------------------------------------------------------------
# NativeCryptoAdapter comprehensive tests
# ---------------------------------------------------------------------------


class _FakeCryptoModule:
    """Fake native module implementing all functions expected by NativeCryptoAdapter."""

    @staticmethod
    def generate_mnemonic() -> str:
        return "word " * 24

    @staticmethod
    def validate_mnemonic(mnemonic: str) -> None:
        return None

    @staticmethod
    def recover_hd_wallet(mnemonic: str) -> dict:
        return {"address": "a", "public_key": "pk", "secret_key": "sk", "account_index": 0}

    @staticmethod
    def recover_hd_wallet_account(mnemonic: str, account_index: int) -> dict:
        return {"address": "a", "public_key": "pk", "secret_key": "sk", "account_index": account_index}

    @staticmethod
    def create_hd_wallet_file_from_mnemonic(mnemonic: str, password: str) -> dict:
        return {
            "address": "a", "public_key": "pk", "secret_key": "sk",
            "account_index": 0, "wallet_file": {"version": 1},
        }

    @staticmethod
    def create_hd_wallet_account_from_mnemonic(mnemonic: str, password: str, account_index: int) -> dict:
        return {
            "address": "a", "public_key": "pk", "secret_key": "sk",
            "account_index": account_index, "wallet_file": {"version": 1},
        }

    @staticmethod
    def recover_wallet_file(*_args) -> dict:
        return {
            "address": "a", "public_key": "pk", "secret_key": "sk",
            "account_index": 0, "wallet_file": {"version": 1},
        }

    @staticmethod
    def address_from_public_key(pk: str) -> str:
        return f"addr-{pk}"

    @staticmethod
    def validate_address(addr: str) -> str:
        return addr

    @staticmethod
    def address_from_pk_checksummed(pk: str) -> str:
        return f"cs-addr-{pk}"

    @staticmethod
    def address_with_checksum(raw_addr: str) -> str:
        return f"ck-{raw_addr}"

    @staticmethod
    def validate_public_key(pk: str) -> None:
        return None

    @staticmethod
    def validate_secret_key(sk: str) -> None:
        return None

    @staticmethod
    def validate_signature(sig: str) -> None:
        return None

    @staticmethod
    def sign_message(sk: str, msg: str) -> dict:
        return {"algorithm": "mldsa65", "signature": f"sig-{sk}-{msg}"}

    @staticmethod
    def verify_message(pk: str, msg: str, sig: str) -> bool:
        return True

    @staticmethod
    def keygen() -> dict:
        return {"secret_key": "sk1", "public_key": "pk1", "address": "addr1"}

    @staticmethod
    def keygen_from_seed(seed: str) -> dict:
        return {"secret_key": f"sk-{seed}", "public_key": f"pk-{seed}", "address": f"addr-{seed}"}

    @staticmethod
    def seed_from_mnemonic(mnemonic: str) -> str:
        return "seed0123"

    @staticmethod
    def derive_child_seed(parent_seed: str, index: int) -> str:
        return f"child-{parent_seed}-{index}"

    @staticmethod
    def constant_time_eq(a: str, b: str) -> bool:
        return a == b

    @staticmethod
    def hash_hex(data: str) -> str:
        return f"hash-{data}"

    @staticmethod
    def set_hash_alg(alg: str) -> None:
        return None

    @staticmethod
    def current_hash_alg() -> str:
        return "sha3-256"

    @staticmethod
    def hash_len_hex() -> int:
        return 64


class NativeCryptoAdapterTests(unittest.TestCase):
    """Comprehensive tests for NativeCryptoAdapter with fake module."""

    def setUp(self) -> None:
        self.adapter = NativeCryptoAdapter(_FakeCryptoModule)

    def test_generate_mnemonic(self) -> None:
        m = self.adapter.generate_mnemonic()
        self.assertIn("word", m)

    def test_validate_mnemonic(self) -> None:
        self.assertIsNone(self.adapter.validate_mnemonic("test mnemonic"))

    def test_recover_hd_wallet(self) -> None:
        acct = self.adapter.recover_hd_wallet("mnemonic")
        self.assertIsInstance(acct, DilithiaAccount)
        self.assertEqual(acct.address, "a")
        self.assertEqual(acct.public_key, "pk")
        self.assertEqual(acct.secret_key, "sk")

    def test_recover_hd_wallet_account(self) -> None:
        acct = self.adapter.recover_hd_wallet_account("mnemonic", 3)
        self.assertEqual(acct.account_index, 3)

    def test_create_hd_wallet_file_from_mnemonic(self) -> None:
        acct = self.adapter.create_hd_wallet_file_from_mnemonic("mnemonic", "pass")
        self.assertIsInstance(acct, DilithiaAccount)
        self.assertIsNotNone(acct.wallet_file)

    def test_create_hd_wallet_account_from_mnemonic(self) -> None:
        acct = self.adapter.create_hd_wallet_account_from_mnemonic("mnemonic", "pass", 2)
        self.assertEqual(acct.account_index, 2)

    def test_recover_wallet_file(self) -> None:
        wf = {"version": 1, "address": "a", "public_key": "pk", "encrypted_sk": "enc", "nonce": "n", "tag": "t"}
        acct = self.adapter.recover_wallet_file(wf, "mnemonic", "pass")
        self.assertEqual(acct.address, "a")

    def test_recover_wallet_file_camel_case_keys(self) -> None:
        wf = {"version": 1, "address": "a", "publicKey": "pk", "encryptedSk": "enc", "nonce": "n", "tag": "t", "account_index": 5}
        acct = self.adapter.recover_wallet_file(wf, "mnemonic", "pass")
        self.assertIsInstance(acct, DilithiaAccount)

    def test_address_from_public_key(self) -> None:
        self.assertEqual(self.adapter.address_from_public_key("pk1"), "addr-pk1")

    def test_validate_address(self) -> None:
        self.assertEqual(self.adapter.validate_address("dili1x"), "dili1x")

    def test_address_from_pk_checksummed(self) -> None:
        self.assertEqual(self.adapter.address_from_pk_checksummed("pk1"), "cs-addr-pk1")

    def test_address_with_checksum(self) -> None:
        self.assertEqual(self.adapter.address_with_checksum("raw"), "ck-raw")

    def test_validate_public_key(self) -> None:
        self.assertIsNone(self.adapter.validate_public_key("pk"))

    def test_validate_secret_key(self) -> None:
        self.assertIsNone(self.adapter.validate_secret_key("sk"))

    def test_validate_signature(self) -> None:
        self.assertIsNone(self.adapter.validate_signature("sig"))

    def test_sign_message(self) -> None:
        sig = self.adapter.sign_message("sk1", "hello")
        self.assertIsInstance(sig, DilithiaSignature)
        self.assertEqual(sig.algorithm, "mldsa65")
        self.assertEqual(sig.signature, "sig-sk1-hello")

    def test_verify_message(self) -> None:
        self.assertTrue(self.adapter.verify_message("pk", "msg", "sig"))

    def test_keygen(self) -> None:
        kp = self.adapter.keygen()
        self.assertIsInstance(kp, DilithiaKeypair)
        self.assertEqual(kp.secret_key, "sk1")
        self.assertEqual(kp.public_key, "pk1")
        self.assertEqual(kp.address, "addr1")

    def test_keygen_from_seed(self) -> None:
        kp = self.adapter.keygen_from_seed("abcdef")
        self.assertEqual(kp.secret_key, "sk-abcdef")
        self.assertEqual(kp.public_key, "pk-abcdef")

    def test_seed_from_mnemonic(self) -> None:
        self.assertEqual(self.adapter.seed_from_mnemonic("m"), "seed0123")

    def test_derive_child_seed(self) -> None:
        self.assertEqual(self.adapter.derive_child_seed("parent", 3), "child-parent-3")

    def test_constant_time_eq_true(self) -> None:
        self.assertTrue(self.adapter.constant_time_eq("aa", "aa"))

    def test_constant_time_eq_false(self) -> None:
        self.assertFalse(self.adapter.constant_time_eq("aa", "bb"))

    def test_hash_hex(self) -> None:
        self.assertEqual(self.adapter.hash_hex("data"), "hash-data")

    def test_set_hash_alg(self) -> None:
        self.assertIsNone(self.adapter.set_hash_alg("sha3-256"))

    def test_current_hash_alg(self) -> None:
        self.assertEqual(self.adapter.current_hash_alg(), "sha3-256")

    def test_hash_len_hex(self) -> None:
        self.assertEqual(self.adapter.hash_len_hex(), 64)


# ---------------------------------------------------------------------------
# AsyncNativeCryptoAdapter tests
# ---------------------------------------------------------------------------


class AsyncNativeCryptoAdapterTests(unittest.IsolatedAsyncioTestCase):
    """Test AsyncNativeCryptoAdapter wrapping a sync adapter."""

    def setUp(self) -> None:
        self.sync_adapter = NativeCryptoAdapter(_FakeCryptoModule)
        self.adapter = AsyncNativeCryptoAdapter(self.sync_adapter)

    async def test_generate_mnemonic(self) -> None:
        m = await self.adapter.generate_mnemonic()
        self.assertIn("word", m)

    async def test_validate_mnemonic(self) -> None:
        result = await self.adapter.validate_mnemonic("test")
        self.assertIsNone(result)

    async def test_recover_hd_wallet(self) -> None:
        acct = await self.adapter.recover_hd_wallet("mnemonic")
        self.assertIsInstance(acct, DilithiaAccount)

    async def test_recover_hd_wallet_account(self) -> None:
        acct = await self.adapter.recover_hd_wallet_account("mnemonic", 2)
        self.assertEqual(acct.account_index, 2)

    async def test_create_hd_wallet_file_from_mnemonic(self) -> None:
        acct = await self.adapter.create_hd_wallet_file_from_mnemonic("m", "p")
        self.assertIsInstance(acct, DilithiaAccount)

    async def test_create_hd_wallet_account_from_mnemonic(self) -> None:
        acct = await self.adapter.create_hd_wallet_account_from_mnemonic("m", "p", 1)
        self.assertEqual(acct.account_index, 1)

    async def test_recover_wallet_file(self) -> None:
        wf = {"version": 1, "address": "a", "public_key": "pk", "encrypted_sk": "e", "nonce": "n", "tag": "t"}
        acct = await self.adapter.recover_wallet_file(wf, "m", "p")
        self.assertIsInstance(acct, DilithiaAccount)

    async def test_address_from_public_key(self) -> None:
        self.assertEqual(await self.adapter.address_from_public_key("pk"), "addr-pk")

    async def test_validate_address(self) -> None:
        self.assertEqual(await self.adapter.validate_address("x"), "x")

    async def test_address_from_pk_checksummed(self) -> None:
        self.assertEqual(await self.adapter.address_from_pk_checksummed("pk"), "cs-addr-pk")

    async def test_address_with_checksum(self) -> None:
        self.assertEqual(await self.adapter.address_with_checksum("raw"), "ck-raw")

    async def test_validate_public_key(self) -> None:
        self.assertIsNone(await self.adapter.validate_public_key("pk"))

    async def test_validate_secret_key(self) -> None:
        self.assertIsNone(await self.adapter.validate_secret_key("sk"))

    async def test_validate_signature(self) -> None:
        self.assertIsNone(await self.adapter.validate_signature("sig"))

    async def test_sign_message(self) -> None:
        sig = await self.adapter.sign_message("sk1", "hello")
        self.assertIsInstance(sig, DilithiaSignature)

    async def test_verify_message(self) -> None:
        self.assertTrue(await self.adapter.verify_message("pk", "msg", "sig"))

    async def test_keygen(self) -> None:
        kp = await self.adapter.keygen()
        self.assertIsInstance(kp, DilithiaKeypair)

    async def test_keygen_from_seed(self) -> None:
        kp = await self.adapter.keygen_from_seed("seed")
        self.assertIsInstance(kp, DilithiaKeypair)

    async def test_seed_from_mnemonic(self) -> None:
        self.assertEqual(await self.adapter.seed_from_mnemonic("m"), "seed0123")

    async def test_derive_child_seed(self) -> None:
        self.assertEqual(await self.adapter.derive_child_seed("p", 1), "child-p-1")

    async def test_constant_time_eq(self) -> None:
        self.assertTrue(await self.adapter.constant_time_eq("a", "a"))

    async def test_hash_hex(self) -> None:
        self.assertEqual(await self.adapter.hash_hex("d"), "hash-d")

    async def test_set_hash_alg(self) -> None:
        self.assertIsNone(await self.adapter.set_hash_alg("sha3-256"))

    async def test_current_hash_alg(self) -> None:
        self.assertEqual(await self.adapter.current_hash_alg(), "sha3-256")

    async def test_hash_len_hex(self) -> None:
        self.assertEqual(await self.adapter.hash_len_hex(), 64)


# ---------------------------------------------------------------------------
# Crypto adapter loaders
# ---------------------------------------------------------------------------


class CryptoLoaderTests(unittest.TestCase):
    """Test crypto adapter loader edge cases."""

    def test_load_native_crypto_adapter_returns_none(self) -> None:
        self.assertIsNone(load_native_crypto_adapter())

    def test_load_async_native_crypto_adapter_returns_none(self) -> None:
        self.assertIsNone(load_async_native_crypto_adapter())

    def test_load_native_crypto_adapter_with_module(self) -> None:
        with patch("dilithia_sdk.crypto.import_module", return_value=_FakeCryptoModule):
            adapter = load_native_crypto_adapter()
        self.assertIsNotNone(adapter)
        self.assertIsInstance(adapter, NativeCryptoAdapter)

    def test_load_async_native_crypto_adapter_with_module(self) -> None:
        with patch("dilithia_sdk.crypto.import_module", return_value=_FakeCryptoModule):
            adapter = load_async_native_crypto_adapter()
        self.assertIsNotNone(adapter)
        self.assertIsInstance(adapter, AsyncNativeCryptoAdapter)


# ---------------------------------------------------------------------------
# NativeZkAdapter comprehensive tests
# ---------------------------------------------------------------------------


class _FakeZkModule:
    """Fake native module implementing all functions expected by NativeZkAdapter."""

    @staticmethod
    def poseidon_hash(inputs: list[int]) -> str:
        return f"ph-{sum(inputs)}"

    @staticmethod
    def compute_commitment(value: int, secret_hex: str, nonce_hex: str) -> dict:
        return {"hash": f"cm-{value}", "value": value, "secret": secret_hex, "nonce": nonce_hex}

    @staticmethod
    def compute_nullifier(secret_hex: str, nonce_hex: str) -> dict:
        return {"hash": f"null-{secret_hex}"}

    @staticmethod
    def generate_preimage_proof(values: list[int]) -> dict:
        return {"proof": "proof1", "vk": "vk1", "inputs": "inputs1"}

    @staticmethod
    def verify_preimage_proof(proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return True

    @staticmethod
    def generate_range_proof(value: int, min_val: int, max_val: int) -> dict:
        return {"proof": "rp1", "vk": "rvk1", "inputs": "ri1"}

    @staticmethod
    def verify_range_proof(proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return True

    @staticmethod
    def generate_commitment_proof(value: int, blinding: int, domain_tag: int) -> dict:
        return {"proof": "cp1", "public_inputs": "pi1", "verification_key": "vk1"}

    @staticmethod
    def verify_commitment_proof(proof_hex: str, vk_json: str, inputs_json: str) -> bool:
        return proof_hex == "cp1"

    @staticmethod
    def prove_predicate(value: int, blinding: int, domain_tag: int, min_val: int, max_val: int) -> dict:
        return {"proof": "pp1", "commitment": "pc1", "min": min_val, "max": max_val, "domain_tag": domain_tag}

    @staticmethod
    def prove_age_over(birth_year: int, current_year: int, min_age: int, blinding: int) -> dict:
        return {"proof": "ap1", "commitment": "ac1", "min": min_age, "max": 200, "domain_tag": 1}

    @staticmethod
    def verify_age_over(proof_hex: str, commitment_hex: str, min_age: int) -> bool:
        return proof_hex == "ap1"

    @staticmethod
    def prove_balance_above(balance: int, blinding: int, min_balance: int, max_balance: int) -> dict:
        return {"proof": "bp1", "commitment": "bc1", "min": min_balance, "max": max_balance, "domain_tag": 2}

    @staticmethod
    def verify_balance_above(proof_hex: str, commitment_hex: str, min_balance: int, max_balance: int) -> bool:
        return proof_hex == "bp1"

    @staticmethod
    def prove_transfer(sender_pre: int, receiver_pre: int, amount: int) -> dict:
        return {
            "proof": "tp1",
            "sender_pre": sender_pre,
            "receiver_pre": receiver_pre,
            "sender_post": sender_pre - amount,
            "receiver_post": receiver_pre + amount,
        }

    @staticmethod
    def verify_transfer(proof_hex: str, inputs_json: str) -> bool:
        return proof_hex == "tp1"

    @staticmethod
    def prove_merkle_verify(leaf_hash_hex: str, path_json: str) -> dict:
        return {"proof": "mp1", "leaf_hash": leaf_hash_hex, "root": "root1", "depth": 3}

    @staticmethod
    def verify_merkle_proof(proof_hex: str, inputs_json: str) -> bool:
        return proof_hex == "mp1"


class NativeZkAdapterTests(unittest.TestCase):
    """Comprehensive tests for NativeZkAdapter with fake module."""

    def setUp(self) -> None:
        self.adapter = NativeZkAdapter(_FakeZkModule)

    def test_poseidon_hash(self) -> None:
        self.assertEqual(self.adapter.poseidon_hash([1, 2, 3]), "ph-6")

    def test_compute_commitment(self) -> None:
        cm = self.adapter.compute_commitment(100, "sec", "nonce")
        self.assertIsInstance(cm, Commitment)
        self.assertEqual(cm.hash, "cm-100")
        self.assertEqual(cm.value, 100)
        self.assertEqual(cm.secret, "sec")
        self.assertEqual(cm.nonce, "nonce")

    def test_compute_nullifier(self) -> None:
        n = self.adapter.compute_nullifier("sec", "nonce")
        self.assertIsInstance(n, Nullifier)
        self.assertEqual(n.hash, "null-sec")

    def test_generate_preimage_proof(self) -> None:
        p = self.adapter.generate_preimage_proof([1, 2])
        self.assertIsInstance(p, StarkProofResult)
        self.assertEqual(p.proof, "proof1")
        self.assertEqual(p.vk, "vk1")
        self.assertEqual(p.inputs, "inputs1")

    def test_verify_preimage_proof(self) -> None:
        self.assertTrue(self.adapter.verify_preimage_proof("p", "vk", "inputs"))

    def test_generate_range_proof(self) -> None:
        p = self.adapter.generate_range_proof(50, 0, 100)
        self.assertIsInstance(p, StarkProofResult)
        self.assertEqual(p.proof, "rp1")

    def test_verify_range_proof(self) -> None:
        self.assertTrue(self.adapter.verify_range_proof("p", "vk", "inputs"))

    # ── 0.5.0 commitment proof ──────────────────────────────────────────

    def test_generate_commitment_proof(self) -> None:
        p = self.adapter.generate_commitment_proof(100, 42, 1)
        self.assertIsInstance(p, CommitmentProofResult)
        self.assertEqual(p.proof, "cp1")
        self.assertEqual(p.public_inputs, "pi1")
        self.assertEqual(p.verification_key, "vk1")

    def test_verify_commitment_proof_valid(self) -> None:
        self.assertTrue(self.adapter.verify_commitment_proof("cp1", "vk", "inputs"))

    def test_verify_commitment_proof_invalid(self) -> None:
        self.assertFalse(self.adapter.verify_commitment_proof("bad", "vk", "inputs"))

    # ── 0.5.0 predicate proofs ─────────────────────────────────────────

    def test_prove_predicate(self) -> None:
        p = self.adapter.prove_predicate(25, 42, 1, 18, 200)
        self.assertIsInstance(p, PredicateProofResult)
        self.assertEqual(p.proof, "pp1")
        self.assertEqual(p.commitment, "pc1")
        self.assertEqual(p.min, 18)
        self.assertEqual(p.max, 200)
        self.assertEqual(p.domain_tag, 1)

    def test_prove_age_over(self) -> None:
        p = self.adapter.prove_age_over(2000, 2026, 18, 99)
        self.assertIsInstance(p, PredicateProofResult)
        self.assertEqual(p.proof, "ap1")
        self.assertEqual(p.commitment, "ac1")
        self.assertEqual(p.min, 18)

    def test_verify_age_over_valid(self) -> None:
        self.assertTrue(self.adapter.verify_age_over("ap1", "ac1", 18))

    def test_verify_age_over_invalid(self) -> None:
        self.assertFalse(self.adapter.verify_age_over("bad", "ac1", 18))

    def test_prove_balance_above(self) -> None:
        p = self.adapter.prove_balance_above(5000, 42, 1000, 100000)
        self.assertIsInstance(p, PredicateProofResult)
        self.assertEqual(p.proof, "bp1")
        self.assertEqual(p.commitment, "bc1")
        self.assertEqual(p.min, 1000)
        self.assertEqual(p.max, 100000)
        self.assertEqual(p.domain_tag, 2)

    def test_verify_balance_above_valid(self) -> None:
        self.assertTrue(self.adapter.verify_balance_above("bp1", "bc1", 1000, 100000))

    def test_verify_balance_above_invalid(self) -> None:
        self.assertFalse(self.adapter.verify_balance_above("bad", "bc1", 1000, 100000))

    # ── 0.5.0 transfer proof ──────────────────────────────────────────

    def test_prove_transfer(self) -> None:
        p = self.adapter.prove_transfer(1000, 500, 200)
        self.assertIsInstance(p, TransferProofResult)
        self.assertEqual(p.proof, "tp1")
        self.assertEqual(p.sender_pre, 1000)
        self.assertEqual(p.receiver_pre, 500)
        self.assertEqual(p.sender_post, 800)
        self.assertEqual(p.receiver_post, 700)

    def test_verify_transfer_valid(self) -> None:
        self.assertTrue(self.adapter.verify_transfer("tp1", "{}"))

    def test_verify_transfer_invalid(self) -> None:
        self.assertFalse(self.adapter.verify_transfer("bad", "{}"))

    # ── 0.5.0 Merkle proof ────────────────────────────────────────────

    def test_prove_merkle_verify(self) -> None:
        p = self.adapter.prove_merkle_verify("0xleaf", "[]")
        self.assertIsInstance(p, MerkleProofResult)
        self.assertEqual(p.proof, "mp1")
        self.assertEqual(p.leaf_hash, "0xleaf")
        self.assertEqual(p.root, "root1")
        self.assertEqual(p.depth, 3)

    def test_verify_merkle_proof_valid(self) -> None:
        self.assertTrue(self.adapter.verify_merkle_proof("mp1", "{}"))

    def test_verify_merkle_proof_invalid(self) -> None:
        self.assertFalse(self.adapter.verify_merkle_proof("bad", "{}"))


# ---------------------------------------------------------------------------
# AsyncNativeZkAdapter tests
# ---------------------------------------------------------------------------


class AsyncNativeZkAdapterTests(unittest.IsolatedAsyncioTestCase):
    """Test AsyncNativeZkAdapter wrapping a sync adapter."""

    def setUp(self) -> None:
        self.sync_adapter = NativeZkAdapter(_FakeZkModule)
        self.adapter = AsyncNativeZkAdapter(self.sync_adapter)

    async def test_poseidon_hash(self) -> None:
        self.assertEqual(await self.adapter.poseidon_hash([1, 2, 3]), "ph-6")

    async def test_compute_commitment(self) -> None:
        cm = await self.adapter.compute_commitment(100, "sec", "nonce")
        self.assertIsInstance(cm, Commitment)

    async def test_compute_nullifier(self) -> None:
        n = await self.adapter.compute_nullifier("sec", "nonce")
        self.assertIsInstance(n, Nullifier)

    async def test_generate_preimage_proof(self) -> None:
        p = await self.adapter.generate_preimage_proof([1, 2])
        self.assertIsInstance(p, StarkProofResult)

    async def test_verify_preimage_proof(self) -> None:
        self.assertTrue(await self.adapter.verify_preimage_proof("p", "vk", "inputs"))

    async def test_generate_range_proof(self) -> None:
        p = await self.adapter.generate_range_proof(50, 0, 100)
        self.assertIsInstance(p, StarkProofResult)

    async def test_verify_range_proof(self) -> None:
        self.assertTrue(await self.adapter.verify_range_proof("p", "vk", "inputs"))

    async def test_async_generate_commitment_proof(self) -> None:
        p = await self.adapter.generate_commitment_proof(100, 42, 1)
        self.assertIsInstance(p, CommitmentProofResult)

    async def test_async_verify_commitment_proof(self) -> None:
        self.assertTrue(await self.adapter.verify_commitment_proof("cp1", "vk", "inputs"))

    async def test_async_prove_predicate(self) -> None:
        p = await self.adapter.prove_predicate(25, 42, 1, 18, 200)
        self.assertIsInstance(p, PredicateProofResult)

    async def test_async_prove_age_over(self) -> None:
        p = await self.adapter.prove_age_over(2000, 2026, 18, 99)
        self.assertIsInstance(p, PredicateProofResult)

    async def test_async_verify_age_over(self) -> None:
        self.assertTrue(await self.adapter.verify_age_over("ap1", "ac1", 18))

    async def test_async_prove_balance_above(self) -> None:
        p = await self.adapter.prove_balance_above(5000, 42, 1000, 100000)
        self.assertIsInstance(p, PredicateProofResult)

    async def test_async_verify_balance_above(self) -> None:
        self.assertTrue(await self.adapter.verify_balance_above("bp1", "bc1", 1000, 100000))

    async def test_async_prove_transfer(self) -> None:
        p = await self.adapter.prove_transfer(1000, 500, 200)
        self.assertIsInstance(p, TransferProofResult)

    async def test_async_verify_transfer(self) -> None:
        self.assertTrue(await self.adapter.verify_transfer("tp1", "{}"))

    async def test_async_prove_merkle_verify(self) -> None:
        p = await self.adapter.prove_merkle_verify("0xleaf", "[]")
        self.assertIsInstance(p, MerkleProofResult)

    async def test_async_verify_merkle_proof(self) -> None:
        self.assertTrue(await self.adapter.verify_merkle_proof("mp1", "{}"))


# ---------------------------------------------------------------------------
# ZK adapter loaders
# ---------------------------------------------------------------------------


class ZkLoaderTests(unittest.TestCase):
    """Test ZK adapter loader edge cases."""

    def test_load_zk_adapter_returns_none(self) -> None:
        self.assertIsNone(load_zk_adapter())

    def test_load_async_zk_adapter_returns_none(self) -> None:
        self.assertIsNone(load_async_zk_adapter())

    def test_load_zk_adapter_with_module(self) -> None:
        with patch("dilithia_sdk.zk.import_module", return_value=_FakeZkModule):
            adapter = load_zk_adapter()
        self.assertIsNotNone(adapter)
        self.assertIsInstance(adapter, NativeZkAdapter)

    def test_load_async_zk_adapter_with_module(self) -> None:
        with patch("dilithia_sdk.zk.import_module", return_value=_FakeZkModule):
            adapter = load_async_zk_adapter()
        self.assertIsNotNone(adapter)
        self.assertIsInstance(adapter, AsyncNativeZkAdapter)


# ---------------------------------------------------------------------------
# read_wasm_file_hex utility
# ---------------------------------------------------------------------------


class WasmFileHexTests(unittest.TestCase):
    """Test the read_wasm_file_hex utility."""

    def test_read_wasm_file_hex(self) -> None:
        import tempfile
        import os
        from dilithia_sdk.client import read_wasm_file_hex

        content = b"\x00\x61\x73\x6d"
        with tempfile.NamedTemporaryFile(suffix=".wasm", delete=False) as f:
            f.write(content)
            f.flush()
            path = f.name
        try:
            hex_str = read_wasm_file_hex(path)
            self.assertEqual(hex_str, "0061736d")
        finally:
            os.unlink(path)


# ---------------------------------------------------------------------------
# CredentialClient tests
# ---------------------------------------------------------------------------


class CredentialClientTests(unittest.IsolatedAsyncioTestCase):
    """Tests for the CredentialClient wrapper in credentials.py."""

    async def test_register_schema(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.call_contract = AsyncMock(return_value={"tx_hash": "0xschema"})
        cc = CredentialClient(mock_client)
        result = await cc.register_schema("identity", "1.0", [{"name": "age", "type": "uint64"}])
        self.assertEqual(result["tx_hash"], "0xschema")
        mock_client.call_contract.assert_called_once_with(
            "credential", "register_schema",
            {"name": "identity", "version": "1.0", "attributes": [{"name": "age", "type": "uint64"}]},
        )

    async def test_issue_with_attributes(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.call_contract = AsyncMock(return_value={"tx_hash": "0xissue"})
        cc = CredentialClient(mock_client)
        result = await cc.issue("holder", "0xschema", "0xcommit", {"name": "Alice"})
        self.assertEqual(result["tx_hash"], "0xissue")
        args = mock_client.call_contract.call_args[0][2]
        self.assertEqual(args["attributes"], {"name": "Alice"})

    async def test_issue_without_attributes(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.call_contract = AsyncMock(return_value={"tx_hash": "0xissue"})
        cc = CredentialClient(mock_client)
        result = await cc.issue("holder", "0xschema", "0xcommit", None)
        self.assertEqual(result["tx_hash"], "0xissue")
        args = mock_client.call_contract.call_args[0][2]
        self.assertNotIn("attributes", args)

    async def test_verify_with_predicates(self) -> None:
        from dilithia_sdk.credentials import CredentialClient, VerificationResult

        mock_client = MagicMock()
        mock_client.call_contract = AsyncMock(return_value={"valid": True})
        cc = CredentialClient(mock_client)
        result = await cc.verify("0xcommit", "0xschema", "proof", {"name": "Alice"}, [{"type": "gt"}])
        self.assertIsInstance(result, VerificationResult)
        self.assertTrue(result.valid)
        self.assertEqual(result.commitment, "0xcommit")

    async def test_verify_without_predicates(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.call_contract = AsyncMock(return_value={"valid": False, "reason": "expired"})
        cc = CredentialClient(mock_client)
        result = await cc.verify("0xcommit", "0xschema", "proof", {})
        self.assertFalse(result.valid)
        self.assertEqual(result.reason, "expired")

    async def test_revoke(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.call_contract = AsyncMock(return_value={"tx_hash": "0xrev"})
        cc = CredentialClient(mock_client)
        result = await cc.revoke("0xcommit")
        self.assertEqual(result["tx_hash"], "0xrev")
        mock_client.call_contract.assert_called_once_with(
            "credential", "revoke", {"commitment": "0xcommit"},
        )

    async def test_get_credential_found(self) -> None:
        from dilithia_sdk.credentials import CredentialClient, Credential

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={
            "credential": {"issuer": "i", "holder": "h", "schema_hash": "s", "status": "active"},
            "revoked": False,
        })
        cc = CredentialClient(mock_client)
        result = await cc.get_credential("0xcommit")
        self.assertIsInstance(result, Credential)
        self.assertEqual(result.commitment, "0xcommit")
        self.assertEqual(result.issuer, "i")
        self.assertEqual(result.holder, "h")
        self.assertFalse(result.revoked)

    async def test_get_credential_not_found(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={})
        cc = CredentialClient(mock_client)
        result = await cc.get_credential("0xmissing")
        self.assertIsNone(result)

    async def test_get_credential_with_result_object(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        qr = MagicMock()
        qr.value = {
            "credential": {"issuer": "i", "holder": "h", "schema_hash": "s"},
            "revoked": True,
        }
        mock_client.query_contract = AsyncMock(return_value=qr)
        cc = CredentialClient(mock_client)
        result = await cc.get_credential("0xcommit")
        self.assertIsNotNone(result)
        self.assertTrue(result.revoked)
        self.assertEqual(result.status, "active")  # default status

    async def test_get_schema_found(self) -> None:
        from dilithia_sdk.credentials import CredentialClient, CredentialSchema

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={
            "schema": {"name": "identity", "version": "1.0", "attributes": [{"name": "age", "type": "uint64"}]},
        })
        cc = CredentialClient(mock_client)
        result = await cc.get_schema("0xschema")
        self.assertIsInstance(result, CredentialSchema)
        self.assertEqual(result.name, "identity")
        self.assertEqual(result.version, "1.0")
        self.assertEqual(len(result.attributes), 1)
        self.assertEqual(result.attributes[0].name, "age")

    async def test_get_schema_not_found(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={})
        cc = CredentialClient(mock_client)
        result = await cc.get_schema("0xmissing")
        self.assertIsNone(result)

    async def test_get_schema_with_result_object(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        qr = MagicMock()
        qr.value = {"schema": {"name": "test", "version": "2.0", "attributes": []}}
        mock_client.query_contract = AsyncMock(return_value=qr)
        cc = CredentialClient(mock_client)
        result = await cc.get_schema("0xschema")
        self.assertIsNotNone(result)
        self.assertEqual(result.name, "test")
        self.assertEqual(len(result.attributes), 0)

    async def test_list_by_holder(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={
            "credentials": [{"commitment": "0xc1"}, {"commitment": "0xc2"}],
        })
        cc = CredentialClient(mock_client)
        result = await cc.list_by_holder("holder1")
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["commitment"], "0xc1")

    async def test_list_by_holder_empty(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={})
        cc = CredentialClient(mock_client)
        result = await cc.list_by_holder("holder1")
        self.assertEqual(result, [])

    async def test_list_by_holder_with_result_object(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        qr = MagicMock()
        qr.value = {"credentials": [{"commitment": "0xc1"}]}
        mock_client.query_contract = AsyncMock(return_value=qr)
        cc = CredentialClient(mock_client)
        result = await cc.list_by_holder("holder1")
        self.assertEqual(len(result), 1)

    async def test_list_by_issuer(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={
            "credentials": [{"commitment": "0xc1"}],
        })
        cc = CredentialClient(mock_client)
        result = await cc.list_by_issuer("issuer1")
        self.assertEqual(len(result), 1)

    async def test_list_by_issuer_empty(self) -> None:
        from dilithia_sdk.credentials import CredentialClient

        mock_client = MagicMock()
        mock_client.query_contract = AsyncMock(return_value={})
        cc = CredentialClient(mock_client)
        result = await cc.list_by_issuer("issuer1")
        self.assertEqual(result, [])


# ---------------------------------------------------------------------------
# Async client: name service mutation methods
# ---------------------------------------------------------------------------


class AsyncNameServiceMutationTests(unittest.IsolatedAsyncioTestCase):
    """Tests for async name service mutation methods on AsyncDilithiaClient."""

    async def test_async_register_name(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xreg"})
        result = await client.register_name("alice.dili")
        self.assertEqual(result["tx_hash"], "0xreg")
        await client.aclose()

    async def test_async_renew_name(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xrenew"})
        result = await client.renew_name("alice.dili")
        self.assertEqual(result["tx_hash"], "0xrenew")
        await client.aclose()

    async def test_async_transfer_name(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xtrans"})
        result = await client.transfer_name("alice.dili", "dili1bob")
        self.assertEqual(result["tx_hash"], "0xtrans")
        await client.aclose()

    async def test_async_set_name_target(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xtarget"})
        result = await client.set_name_target("alice.dili", "dili1target")
        self.assertEqual(result["tx_hash"], "0xtarget")
        await client.aclose()

    async def test_async_set_name_record(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xrec"})
        result = await client.set_name_record("alice.dili", "avatar", "https://example.com/a.png")
        self.assertEqual(result["tx_hash"], "0xrec")
        await client.aclose()

    async def test_async_release_name(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xrel"})
        result = await client.release_name("alice.dili")
        self.assertEqual(result["tx_hash"], "0xrel")
        await client.aclose()

    async def test_async_get_name_records(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"records": {"avatar": "url"}})
        result = await client.get_name_records("alice.dili")
        self.assertIsInstance(result, QueryResult)
        await client.aclose()

    async def test_async_get_registration_cost(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"cost": 5000})
        result = await client.get_registration_cost("alice.dili")
        self.assertIsInstance(result, QueryResult)
        await client.aclose()


# ---------------------------------------------------------------------------
# Sync client: name service mutation methods
# ---------------------------------------------------------------------------


class SyncNameServiceMutationTests(unittest.TestCase):
    """Tests for sync name service mutation methods on DilithiaClient."""

    def test_sync_register_name(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xreg"})
        result = client.register_name("alice.dili")
        self.assertEqual(result["tx_hash"], "0xreg")

    def test_sync_renew_name(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xrenew"})
        result = client.renew_name("alice.dili")
        self.assertEqual(result["tx_hash"], "0xrenew")

    def test_sync_transfer_name(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xtrans"})
        result = client.transfer_name("alice.dili", "dili1bob")
        self.assertEqual(result["tx_hash"], "0xtrans")

    def test_sync_set_name_target(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xtarget"})
        result = client.set_name_target("alice.dili", "dili1target")
        self.assertEqual(result["tx_hash"], "0xtarget")

    def test_sync_set_name_record(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xrec"})
        result = client.set_name_record("alice.dili", "avatar", "https://example.com/a.png")
        self.assertEqual(result["tx_hash"], "0xrec")

    def test_sync_release_name(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xrel"})
        result = client.release_name("alice.dili")
        self.assertEqual(result["tx_hash"], "0xrel")

    def test_sync_get_name_records(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={"records": {"avatar": "url"}})
        result = client.get_name_records("alice.dili")
        self.assertIsInstance(result, QueryResult)

    def test_sync_get_registration_cost(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={"cost": 5000})
        result = client.get_registration_cost("alice.dili")
        self.assertIsInstance(result, QueryResult)


# ---------------------------------------------------------------------------
# Sync client: multisig methods
# ---------------------------------------------------------------------------


class SyncMultisigTests(unittest.TestCase):
    """Tests for sync multisig methods on DilithiaClient."""

    def test_sync_create_multisig(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xcm"})
        result = client.create_multisig("wallet1", ["dili1a", "dili1b"], 2)
        self.assertEqual(result["tx_hash"], "0xcm")

    def test_sync_propose_tx(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xpt"})
        result = client.propose_tx("wallet1", "token", "transfer", {"to": "bob"})
        self.assertEqual(result["tx_hash"], "0xpt")

    def test_sync_approve_multisig_tx(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xap"})
        result = client.approve_multisig_tx("wallet1", "tx1")
        self.assertEqual(result["tx_hash"], "0xap")

    def test_sync_execute_multisig_tx(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xex"})
        result = client.execute_multisig_tx("wallet1", "tx1")
        self.assertEqual(result["tx_hash"], "0xex")

    def test_sync_revoke_multisig_approval(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xrv"})
        result = client.revoke_multisig_approval("wallet1", "tx1")
        self.assertEqual(result["tx_hash"], "0xrv")

    def test_sync_add_multisig_signer(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xas"})
        result = client.add_multisig_signer("wallet1", "dili1new")
        self.assertEqual(result["tx_hash"], "0xas")

    def test_sync_remove_multisig_signer(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._post_json = MagicMock(return_value={"tx_hash": "0xrs"})
        result = client.remove_multisig_signer("wallet1", "dili1old")
        self.assertEqual(result["tx_hash"], "0xrs")

    def test_sync_get_multisig_wallet(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={
            "wallet_id": "wallet1", "signers": ["a", "b"], "threshold": 2,
        })
        result = client.get_multisig_wallet("wallet1")
        self.assertIsInstance(result, QueryResult)

    def test_sync_get_multisig_tx(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={
            "tx_id": "tx1", "contract": "token", "method": "transfer",
        })
        result = client.get_multisig_tx("wallet1", "tx1")
        self.assertIsInstance(result, QueryResult)

    def test_sync_list_multisig_pending_txs(self) -> None:
        client = DilithiaClient("http://rpc.example")
        client._get_absolute_json = MagicMock(return_value={"pending_txs": []})
        result = client.list_multisig_pending_txs("wallet1")
        self.assertIsInstance(result, QueryResult)


# ---------------------------------------------------------------------------
# Async client: multisig methods
# ---------------------------------------------------------------------------


class AsyncMultisigTests(unittest.IsolatedAsyncioTestCase):
    """Tests for async multisig methods on AsyncDilithiaClient."""

    async def test_async_create_multisig(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xcm"})
        result = await client.create_multisig("wallet1", ["dili1a", "dili1b"], 2)
        self.assertEqual(result["tx_hash"], "0xcm")
        await client.aclose()

    async def test_async_propose_tx(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xpt"})
        result = await client.propose_tx("wallet1", "token", "transfer", {"to": "bob"})
        self.assertEqual(result["tx_hash"], "0xpt")
        await client.aclose()

    async def test_async_approve_multisig_tx(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xap"})
        result = await client.approve_multisig_tx("wallet1", "tx1")
        self.assertEqual(result["tx_hash"], "0xap")
        await client.aclose()

    async def test_async_execute_multisig_tx(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xex"})
        result = await client.execute_multisig_tx("wallet1", "tx1")
        self.assertEqual(result["tx_hash"], "0xex")
        await client.aclose()

    async def test_async_revoke_multisig_approval(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xrv"})
        result = await client.revoke_multisig_approval("wallet1", "tx1")
        self.assertEqual(result["tx_hash"], "0xrv")
        await client.aclose()

    async def test_async_add_multisig_signer(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xas"})
        result = await client.add_multisig_signer("wallet1", "dili1new")
        self.assertEqual(result["tx_hash"], "0xas")
        await client.aclose()

    async def test_async_remove_multisig_signer(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._post_json = AsyncMock(return_value={"tx_hash": "0xrs"})
        result = await client.remove_multisig_signer("wallet1", "dili1old")
        self.assertEqual(result["tx_hash"], "0xrs")
        await client.aclose()

    async def test_async_get_multisig_wallet(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={
            "wallet_id": "wallet1", "signers": ["a", "b"], "threshold": 2,
        })
        result = await client.get_multisig_wallet("wallet1")
        self.assertIsInstance(result, QueryResult)
        await client.aclose()

    async def test_async_get_multisig_tx(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={
            "tx_id": "tx1", "contract": "token", "method": "transfer",
        })
        result = await client.get_multisig_tx("wallet1", "tx1")
        self.assertIsInstance(result, QueryResult)
        await client.aclose()

    async def test_async_list_multisig_pending_txs(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example")
        client._get_absolute_json = AsyncMock(return_value={"pending_txs": []})
        result = await client.list_multisig_pending_txs("wallet1")
        self.assertIsInstance(result, QueryResult)
        await client.aclose()


if __name__ == "__main__":
    unittest.main()
