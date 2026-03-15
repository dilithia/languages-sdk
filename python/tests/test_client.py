import json
import sys
import unittest
from unittest.mock import patch

from dilithia_sdk import (
    AsyncDilithiaClient,
    DilithiaClient,
    DilithiaGasSponsorConnector,
    DilithiaMessagingConnector,
    MIN_PYTHON,
    RPC_LINE_VERSION,
    __version__,
    load_native_crypto_adapter,
)
from dilithia_sdk.crypto import NativeCryptoAdapter


class ClientTests(unittest.TestCase):
    def test_normalizes_rpc_url(self) -> None:
        client = DilithiaClient("http://rpc.example/")
        self.assertEqual(client.rpc_url, "http://rpc.example")
        self.assertEqual(client.base_url, "http://rpc.example")
        self.assertEqual(client.ws_url, "ws://rpc.example")

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

    def test_builds_generic_rpc_request(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        self.assertEqual(
            client.build_json_rpc_request("qsc_head", {"full": True}),
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "qsc_head",
                "params": {"full": True},
            },
        )
        self.assertEqual(
            client.build_json_rpc_batch([("qsc_head", {}), ("qsc_tps", {"window": 10})]),
            [
                {"jsonrpc": "2.0", "id": 1, "method": "qsc_head", "params": {}},
                {"jsonrpc": "2.0", "id": 2, "method": "qsc_tps", "params": {"window": 10}},
            ],
        )
        self.assertEqual(
            client.build_network_overview_batch(),
            [
                {"jsonrpc": "2.0", "id": 1, "method": "qsc_chain", "params": {}},
                {"jsonrpc": "2.0", "id": 2, "method": "qsc_head", "params": {}},
                {"jsonrpc": "2.0", "id": 3, "method": "qsc_stateRoot", "params": {}},
                {"jsonrpc": "2.0", "id": 4, "method": "qsc_tps", "params": {}},
                {"jsonrpc": "2.0", "id": 5, "method": "qsc_gasEstimate", "params": {}},
            ],
        )
        self.assertEqual(
            client.build_transaction_details_batch("deadbeef"),
            [
                {"jsonrpc": "2.0", "id": 1, "method": "qsc_getReceipt", "params": {"tx_hash": "deadbeef"}},
                {"jsonrpc": "2.0", "id": 2, "method": "qsc_getTxBlock", "params": {"tx_hash": "deadbeef"}},
                {"jsonrpc": "2.0", "id": 3, "method": "qsc_internalTxs", "params": {"tx_hash": "deadbeef"}},
            ],
        )
        self.assertEqual(
            client.build_address_details_batch("alice"),
            [
                {"jsonrpc": "2.0", "id": 1, "method": "qsc_addressSummary", "params": {"address": "alice"}},
                {"jsonrpc": "2.0", "id": 2, "method": "qsc_getAddressTxs", "params": {"address": "alice"}},
            ],
        )

    def test_explorer_rpc_helpers_delegate_to_json_rpc(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        calls: list[tuple[str, dict]] = []

        def fake_json_rpc(method: str, params: dict | None = None, request_id: int = 1) -> dict:
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

    def test_sync_json_rpc_batch_parses_results(self) -> None:
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

    def test_jwt_auth_headers_are_exposed_for_http_and_ws(self) -> None:
        client = DilithiaClient(
            "https://rpc.example/rpc",
            jwt="secret-token",
            headers={"x-network": "devnet"},
        )
        self.assertEqual(
            client.build_auth_headers({"accept": "application/json"}),
            {
                "Authorization": "Bearer secret-token",
                "x-network": "devnet",
                "accept": "application/json",
            },
        )
        self.assertEqual(
            client.get_ws_connection_info(),
            {
                "url": "wss://rpc.example",
                "headers": {
                    "Authorization": "Bearer secret-token",
                    "x-network": "devnet",
                },
            },
        )

    def test_sdk_version_stays_aligned_with_rpc_line(self) -> None:
        self.assertEqual(__version__, "0.3.0")
        self.assertEqual(RPC_LINE_VERSION, "0.3.0")

    def test_python_runtime_satisfies_minimum_supported_version(self) -> None:
        self.assertGreaterEqual(sys.version_info[:2], MIN_PYTHON)

    def test_native_crypto_adapter_loader_returns_none_when_bridge_is_unavailable(self) -> None:
        self.assertIsNone(load_native_crypto_adapter())

    def test_native_crypto_adapter_maps_bridge_module_correctly(self) -> None:
        class FakeModule:
            @staticmethod
            def generate_mnemonic() -> str:
                return "word " * 24

            @staticmethod
            def validate_mnemonic(_mnemonic: str) -> None:
                return None

            @staticmethod
            def address_from_public_key(_public_key_hex: str) -> str:
                return "derived-address"

            @staticmethod
            def sign_message(_secret_key_hex: str, _message: str) -> dict:
                return {"algorithm": "mldsa65", "signature": "deadbeef"}

            @staticmethod
            def verify_message(_public_key_hex: str, _message: str, _signature_hex: str) -> bool:
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
        self.assertEqual(recovered.public_key, "pk-1")
        self.assertEqual(recovered.secret_key, "sk-1")

    def test_native_crypto_adapter_loader_uses_imported_module_when_available(self) -> None:
        class FakeModule:
            pass

        with patch("dilithia_sdk.crypto.import_module", return_value=FakeModule):
            adapter = load_native_crypto_adapter()
        self.assertIsNotNone(adapter)

    def test_send_signed_call_merges_signer_payload(self) -> None:
        client = DilithiaClient("http://rpc.example")

        class FakeSigner:
            @staticmethod
            def sign_canonical_payload(payload_json: str) -> dict:
                return {"sig": f"signed:{payload_json}", "pk": "pk", "alg": "mldsa65"}
        

        sent = {}

        def fake_send_call(call: dict) -> dict:
            sent.update(call)
            return {"tx_hash": "0xabc"}

        client.send_call = fake_send_call  # type: ignore[method-assign]
        result = client.send_signed_call({"from": "a", "contract": "token"}, FakeSigner())
        self.assertEqual(result, {"tx_hash": "0xabc"})
        self.assertEqual(sent["pk"], "pk")
        self.assertEqual(sent["alg"], "mldsa65")
        self.assertTrue(sent["sig"].startswith("signed:"))

    def test_paymaster_and_forwarder_helpers(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        sponsored = client.with_paymaster({"contract": "wasm:amm", "method": "swap"}, "gas_sponsor")
        self.assertEqual(sponsored["paymaster"], "gas_sponsor")

        forwarder = client.build_forwarder_call(
            "wasm:forwarder",
            {"user": "alice", "nonce": 1},
            paymaster="gas_sponsor",
        )
        self.assertEqual(forwarder["contract"], "wasm:forwarder")
        self.assertEqual(forwarder["method"], "forward")
        self.assertEqual(forwarder["paymaster"], "gas_sponsor")

    def test_name_service_and_contract_query_use_chain_base_url(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        called_urls: list[str] = []

        def fake_read_json(_request):
            called_urls.append(_request.full_url)
            return {"ok": True}

        client._read_sync_json = fake_read_json  # type: ignore[method-assign]
        client.resolve_name("alice.dili")
        client.query_contract("wasm:amm", "get_reserves", {})
        self.assertEqual(called_urls[0], "http://rpc.example/names/resolve/alice.dili")
        self.assertEqual(
            called_urls[1],
            "http://rpc.example/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D",
        )

    def test_gas_sponsor_connector_builds_expected_queries(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        sponsor = DilithiaGasSponsorConnector(client, "wasm:gas_sponsor", paymaster="gas_sponsor")
        self.assertEqual(
            sponsor.build_remaining_quota_query("alice"),
            {"contract": "wasm:gas_sponsor", "method": "remaining_quota", "args": {"user": "alice"}},
        )
        applied = sponsor.apply_paymaster({"contract": "wasm:amm", "method": "swap", "args": {}})
        self.assertEqual(applied["paymaster"], "gas_sponsor")

    def test_messaging_connector_builds_in_and_out_calls(self) -> None:
        client = DilithiaClient("http://rpc.example/rpc")
        messaging = DilithiaMessagingConnector(client, "wasm:messaging", paymaster="gas_sponsor")
        outbound = messaging.build_send_message_call("ethereum", {"amount": 1})
        self.assertEqual(outbound["method"], "send_message")
        self.assertEqual(outbound["paymaster"], "gas_sponsor")
        inbound = messaging.build_receive_message_call("ethereum", "bridge", {"tx": "0xabc"})
        self.assertEqual(inbound["method"], "receive_message")
        self.assertEqual(inbound["args"]["source_chain"], "ethereum")


class AsyncClientTests(unittest.IsolatedAsyncioTestCase):
    async def test_async_client_exposes_jwt_for_ws(self) -> None:
        client = AsyncDilithiaClient("https://rpc.example/rpc", jwt="token", headers={"x-network": "devnet"})
        self.assertEqual(
            client.get_ws_connection_info(),
            {
                "url": "wss://rpc.example",
                "headers": {
                    "Authorization": "Bearer token",
                    "x-network": "devnet",
                },
            },
        )
        await client.aclose()

    async def test_async_json_rpc_and_batch_paths(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example/rpc", jwt="token")
        calls: list[tuple[str, dict | list[dict], str]] = []

        async def fake_post_json(pathname: str, body, *, method: str):
            calls.append((pathname, body, method))
            if isinstance(body, list):
                return [
                    {"jsonrpc": "2.0", "id": 1, "result": {"chain_id": "dili-devnet"}},
                    {"jsonrpc": "2.0", "id": 2, "result": {"height": 42}},
                ]
            return {"height": 42}

        async def fake_post_absolute_json(url: str, body, *, method: str = "request"):
            return await fake_post_json(url, body, method=method)

        client._post_json = fake_post_json  # type: ignore[method-assign]
        client._post_absolute_json = fake_post_absolute_json  # type: ignore[method-assign]
        head = await client.get_head()
        batch = await client.raw_post("", client.build_json_rpc_batch([("qsc_chain", {}), ("qsc_head", {})]))

        self.assertEqual(head, {"height": 42})
        self.assertEqual(batch[0]["result"], {"chain_id": "dili-devnet"})
        self.assertEqual(calls[0][2], "qsc_head")

    async def test_async_json_rpc_batch_parses_results(self) -> None:
        client = AsyncDilithiaClient("http://rpc.example/rpc")

        async def fake_post_json(_pathname: str, body, *, method: str):
            self.assertEqual(method, "batch")
            self.assertIsInstance(body, list)
            return [
                {"jsonrpc": "2.0", "id": 1, "result": {"chain_id": "dili-devnet"}},
                {"jsonrpc": "2.0", "id": 2, "result": {"height": 42}},
            ]

        client._post_json = fake_post_json  # type: ignore[method-assign]
        result = await client.json_rpc_batch([("qsc_chain", {}), ("qsc_head", {})])
        self.assertEqual(result, [{"chain_id": "dili-devnet"}, {"height": 42}])


if __name__ == "__main__":
    unittest.main()
