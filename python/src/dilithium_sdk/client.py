from __future__ import annotations

import json
import time
import asyncio
import urllib.error
import urllib.parse
import urllib.request
from typing import Any


class _DilithiumClientBase:
    def __init__(
        self,
        rpc_url: str,
        timeout: float = 10.0,
        *,
        chain_base_url: str | None = None,
        indexer_url: str | None = None,
        oracle_url: str | None = None,
        ws_url: str | None = None,
        jwt: str | None = None,
        headers: dict[str, str] | None = None,
    ) -> None:
        self.rpc_url = rpc_url.rstrip("/")
        self.base_url = (chain_base_url.rstrip("/") if chain_base_url else self.rpc_url.removesuffix("/rpc"))
        self.timeout = timeout
        self.indexer_url = indexer_url.rstrip("/") if indexer_url else None
        self.oracle_url = oracle_url.rstrip("/") if oracle_url else None
        self.ws_url = ws_url.rstrip("/") if ws_url else self._derive_ws_url()
        self.jwt = jwt
        self.headers = headers or {}

    def _derive_ws_url(self) -> str | None:
        if self.base_url.startswith("https://"):
            return "wss://" + self.base_url.removeprefix("https://")
        if self.base_url.startswith("http://"):
            return "ws://" + self.base_url.removeprefix("http://")
        return None

    def get_address_summary(self, address: str) -> dict[str, Any]:
        return self.json_rpc("qsc_addressSummary", {"address": address})

    def get_head(self) -> dict[str, Any]:
        return self.json_rpc("qsc_head", {})

    def get_chain(self) -> dict[str, Any]:
        return self.json_rpc("qsc_chain", {})

    def get_state_root(self) -> dict[str, Any]:
        return self.json_rpc("qsc_stateRoot", {})

    def get_tps(self) -> dict[str, Any]:
        return self.json_rpc("qsc_tps", {})

    def get_gas_estimate(self) -> dict[str, Any]:
        return self.json_rpc("qsc_gasEstimate", {})

    def get_base_fee(self) -> dict[str, Any]:
        return self.json_rpc("qsc_baseFee", {})

    def get_block(self, height: int) -> dict[str, Any]:
        return self.json_rpc("qsc_getBlock", {"height": height})

    def get_blocks(self, from_height: int, to_height: int) -> dict[str, Any]:
        return self.json_rpc("qsc_getBlocks", {"from": from_height, "to": to_height})

    def get_tx_block(self, tx_hash: str) -> dict[str, Any]:
        return self.json_rpc("qsc_getTxBlock", {"tx_hash": tx_hash})

    def get_internal_txs(self, tx_hash: str) -> dict[str, Any]:
        return self.json_rpc("qsc_internalTxs", {"tx_hash": tx_hash})

    def get_address_txs(self, address: str) -> dict[str, Any]:
        return self.json_rpc("qsc_getAddressTxs", {"address": address})

    def search_hash(self, hash_value: str) -> dict[str, Any]:
        return self.json_rpc("qsc_search", {"hash": hash_value})

    def build_json_rpc_request(self, method: str, params: dict[str, Any] | None = None, request_id: int = 1) -> dict[str, Any]:
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params or {},
        }

    def build_json_rpc_batch(self, calls: list[tuple[str, dict[str, Any]]], start_id: int = 1) -> list[dict[str, Any]]:
        return [
            self.build_json_rpc_request(method, params, request_id=index)
            for index, (method, params) in enumerate(calls, start=start_id)
        ]

    def build_network_overview_batch(self) -> list[dict[str, Any]]:
        return self.build_json_rpc_batch(
            [
                ("qsc_chain", {}),
                ("qsc_head", {}),
                ("qsc_stateRoot", {}),
                ("qsc_tps", {}),
                ("qsc_gasEstimate", {}),
            ]
        )

    def build_transaction_details_batch(self, tx_hash: str) -> list[dict[str, Any]]:
        return self.build_json_rpc_batch(
            [
                ("qsc_getReceipt", {"tx_hash": tx_hash}),
                ("qsc_getTxBlock", {"tx_hash": tx_hash}),
                ("qsc_internalTxs", {"tx_hash": tx_hash}),
            ]
        )

    def build_address_details_batch(self, address: str) -> list[dict[str, Any]]:
        return self.build_json_rpc_batch(
            [
                ("qsc_addressSummary", {"address": address}),
                ("qsc_getAddressTxs", {"address": address}),
            ]
        )

    def _parse_json_rpc_batch_response(self, body: list[dict[str, Any]], payload: list[dict[str, Any]]) -> list[dict[str, Any]]:
        items_by_id = {item["id"]: item for item in body}
        results: list[dict[str, Any]] = []
        for index, request in enumerate(payload, start=1):
            item = items_by_id[index]
            if "error" in item:
                message = item["error"].get("message", "unknown rpc error")
                raise RuntimeError(f"{request.get('method', 'rpc')} failed: {message}")
            results.append(item.get("result", {}))
        return results

    def build_ws_request(self, method: str, params: dict[str, Any] | None = None, request_id: int = 1) -> dict[str, Any]:
        return self.build_json_rpc_request(method, params, request_id)

    def build_auth_headers(self, extra: dict[str, str] | None = None) -> dict[str, str]:
        merged = {}
        if self.jwt:
            merged["Authorization"] = f"Bearer {self.jwt}"
        merged.update(self.headers)
        if extra:
            merged.update(extra)
        return merged

    def get_ws_connection_info(self) -> dict[str, Any]:
        return {"url": self.ws_url, "headers": self.build_auth_headers()}

    def build_absolute_url(self, root: str, path: str) -> str:
        return f"{root}{path if path.startswith('/') else '/' + path}"

    def build_name_service_url(self, action: str, value: str) -> str:
        return f"{self.base_url}/names/{action}/{urllib.parse.quote(value, safe='')}"

    def build_contract_query_url(self, contract: str, method: str, args: dict[str, Any] | None = None) -> str:
        encoded_args = urllib.parse.quote(json.dumps(args or {}, separators=(",", ":")), safe="")
        return (
            f"{self.base_url}/query?contract={urllib.parse.quote(contract, safe='')}"
            f"&method={urllib.parse.quote(method, safe='')}&args={encoded_args}"
        )

    def resolve_name(self, name: str) -> dict[str, Any]:
        return self._get_absolute_json(self.build_name_service_url("resolve", name))

    def lookup_name(self, name: str) -> dict[str, Any]:
        return self._get_absolute_json(self.build_name_service_url("lookup", name))

    def is_name_available(self, name: str) -> dict[str, Any]:
        return self._get_absolute_json(self.build_name_service_url("available", name))

    def get_names_by_owner(self, address: str) -> dict[str, Any]:
        return self._get_absolute_json(self.build_name_service_url("by-owner", address))

    def reverse_resolve_name(self, address: str) -> dict[str, Any]:
        return self._get_absolute_json(self.build_name_service_url("reverse", address))

    def query_contract(self, contract: str, method: str, args: dict[str, Any] | None = None) -> dict[str, Any]:
        return self._get_absolute_json(self.build_contract_query_url(contract, method, args))

    def build_forwarder_call(
        self,
        forwarder_contract: str,
        args: dict[str, Any],
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        call: dict[str, Any] = {
            "contract": forwarder_contract,
            "method": "forward",
            "args": args,
        }
        return self.with_paymaster(call, paymaster) if paymaster else call

    def build_contract_call(
        self,
        contract: str,
        method: str,
        args: dict[str, Any] | None = None,
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        call: dict[str, Any] = {"contract": contract, "method": method, "args": args or {}}
        return self.with_paymaster(call, paymaster) if paymaster else call

    def with_paymaster(self, call: dict[str, Any], paymaster: str) -> dict[str, Any]:
        return {**call, "paymaster": paymaster}

    def _parse_json_rpc_response(self, body: dict[str, Any], method: str) -> dict[str, Any]:
        if "error" in body:
            message = body["error"].get("message", "unknown rpc error")
            raise RuntimeError(f"{method} failed: {message}")
        return body.get("result", {})

    def _read_sync_json(self, request: urllib.request.Request) -> dict[str, Any]:
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                payload = response.read().decode("utf-8")
                return json.loads(payload)
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"HTTP {exc.code}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(str(exc.reason)) from exc


class DilithiumClient(_DilithiumClientBase):
    def get_balance(self, address: str) -> dict[str, Any]:
        return self._get_json(f"/balance/{urllib.parse.quote(address, safe='')}")

    def get_nonce(self, address: str) -> dict[str, Any]:
        return self._get_json(f"/nonce/{urllib.parse.quote(address, safe='')}")

    def get_receipt(self, tx_hash: str) -> dict[str, Any]:
        return self._get_json(f"/receipt/{urllib.parse.quote(tx_hash, safe='')}")

    def json_rpc(self, method: str, params: dict[str, Any] | None = None, request_id: int = 1) -> dict[str, Any]:
        return self._post_json("", self.build_json_rpc_request(method, params, request_id))

    def raw_rpc(self, method: str, params: dict[str, Any] | None = None, request_id: int = 1) -> dict[str, Any]:
        return self.json_rpc(method, params, request_id)

    def json_rpc_batch(self, calls: list[tuple[str, dict[str, Any]]], start_id: int = 1) -> list[dict[str, Any]]:
        payload = self.build_json_rpc_batch(calls, start_id=start_id)
        body = self._post_json("", payload)
        return self._parse_json_rpc_batch_response(body, payload)

    def raw_get(self, path: str, *, use_chain_base: bool = False) -> dict[str, Any]:
        root = self.base_url if use_chain_base else self.rpc_url
        return self._get_absolute_json(self.build_absolute_url(root, path))

    def raw_post(self, path: str, body: Any, *, use_chain_base: bool = False) -> Any:
        root = self.base_url if use_chain_base else self.rpc_url
        return self._post_absolute_json(self.build_absolute_url(root, path), body)

    def simulate(self, call: dict[str, Any]) -> dict[str, Any]:
        return self._post_json("/simulate", call)

    def send_call(self, call: dict[str, Any]) -> dict[str, Any]:
        return self._post_json("/call", call)

    def send_signed_call(self, call: dict[str, Any], signer: Any) -> dict[str, Any]:
        payload_json = json.dumps(call, separators=(",", ":"), sort_keys=False)
        signature = signer.sign_canonical_payload(payload_json)
        if not isinstance(signature, dict):
            raise TypeError("Signer must return a dictionary payload.")
        return self.send_call({**call, **signature})

    def send_sponsored_call(self, call: dict[str, Any], paymaster: str, signer: Any) -> dict[str, Any]:
        return self.send_signed_call(self.with_paymaster(call, paymaster), signer)

    def call_contract(
        self,
        contract: str,
        method: str,
        args: dict[str, Any] | None = None,
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        return self.send_call(self.build_contract_call(contract, method, args, paymaster=paymaster))

    def wait_for_receipt(self, tx_hash: str, max_attempts: int = 12, delay_seconds: float = 1.0) -> dict[str, Any]:
        for _ in range(max_attempts):
            try:
                return self.get_receipt(tx_hash)
            except RuntimeError as exc:
                if "HTTP 404" not in str(exc):
                    raise
            time.sleep(delay_seconds)
        raise RuntimeError("Receipt not available yet.")

    def _get_json(self, pathname: str) -> dict[str, Any]:
        request = urllib.request.Request(
            f"{self.rpc_url}{pathname}",
            method="GET",
            headers=self.build_auth_headers({"accept": "application/json"}),
        )
        return self._read_sync_json(request)

    def _get_absolute_json(self, url: str) -> dict[str, Any]:
        request = urllib.request.Request(
            url,
            method="GET",
            headers=self.build_auth_headers({"accept": "application/json"}),
        )
        return self._read_sync_json(request)

    def _post_json(self, pathname: str, body: Any) -> Any:
        return self._post_absolute_json(f"{self.rpc_url}{pathname}", body)

    def _post_absolute_json(self, url: str, body: Any) -> Any:
        request = urllib.request.Request(
            url,
            method="POST",
            headers=self.build_auth_headers({
                "accept": "application/json",
                "content-type": "application/json",
            }),
            data=json.dumps(body).encode("utf-8"),
        )
        return self._read_sync_json(request)


class AsyncDilithiumClient(_DilithiumClientBase):
    def __init__(
        self,
        rpc_url: str,
        timeout: float = 10.0,
        *,
        chain_base_url: str | None = None,
        indexer_url: str | None = None,
        oracle_url: str | None = None,
        ws_url: str | None = None,
        jwt: str | None = None,
        headers: dict[str, str] | None = None,
    ) -> None:
        super().__init__(
            rpc_url,
            timeout,
            chain_base_url=chain_base_url,
            indexer_url=indexer_url,
            oracle_url=oracle_url,
            ws_url=ws_url,
            jwt=jwt,
            headers=headers,
        )

    async def aclose(self) -> None:
        return None

    async def get_balance(self, address: str) -> dict[str, Any]:
        return await self._get_json(f"/balance/{urllib.parse.quote(address, safe='')}")

    async def get_nonce(self, address: str) -> dict[str, Any]:
        return await self._get_json(f"/nonce/{urllib.parse.quote(address, safe='')}")

    async def get_receipt(self, tx_hash: str) -> dict[str, Any]:
        return await self._get_json(f"/receipt/{urllib.parse.quote(tx_hash, safe='')}")

    async def get_address_summary(self, address: str) -> dict[str, Any]:
        return await self.json_rpc("qsc_addressSummary", {"address": address})

    async def get_head(self) -> dict[str, Any]:
        return await self.json_rpc("qsc_head", {})

    async def get_chain(self) -> dict[str, Any]:
        return await self.json_rpc("qsc_chain", {})

    async def get_state_root(self) -> dict[str, Any]:
        return await self.json_rpc("qsc_stateRoot", {})

    async def get_tps(self) -> dict[str, Any]:
        return await self.json_rpc("qsc_tps", {})

    async def get_gas_estimate(self) -> dict[str, Any]:
        return await self.json_rpc("qsc_gasEstimate", {})

    async def get_base_fee(self) -> dict[str, Any]:
        return await self.json_rpc("qsc_baseFee", {})

    async def get_block(self, height: int) -> dict[str, Any]:
        return await self.json_rpc("qsc_getBlock", {"height": height})

    async def get_blocks(self, from_height: int, to_height: int) -> dict[str, Any]:
        return await self.json_rpc("qsc_getBlocks", {"from": from_height, "to": to_height})

    async def get_tx_block(self, tx_hash: str) -> dict[str, Any]:
        return await self.json_rpc("qsc_getTxBlock", {"tx_hash": tx_hash})

    async def get_internal_txs(self, tx_hash: str) -> dict[str, Any]:
        return await self.json_rpc("qsc_internalTxs", {"tx_hash": tx_hash})

    async def get_address_txs(self, address: str) -> dict[str, Any]:
        return await self.json_rpc("qsc_getAddressTxs", {"address": address})

    async def search_hash(self, hash_value: str) -> dict[str, Any]:
        return await self.json_rpc("qsc_search", {"hash": hash_value})

    async def json_rpc(self, method: str, params: dict[str, Any] | None = None, request_id: int = 1) -> dict[str, Any]:
        return await self._post_json("", self.build_json_rpc_request(method, params, request_id), method=method)

    async def raw_rpc(self, method: str, params: dict[str, Any] | None = None, request_id: int = 1) -> dict[str, Any]:
        return await self.json_rpc(method, params, request_id)

    async def json_rpc_batch(self, calls: list[tuple[str, dict[str, Any]]], start_id: int = 1) -> list[dict[str, Any]]:
        payload = self.build_json_rpc_batch(calls, start_id=start_id)
        body = await self._post_json("", payload, method="batch")
        return self._parse_json_rpc_batch_response(body, payload)

    async def raw_get(self, path: str, *, use_chain_base: bool = False) -> dict[str, Any]:
        root = self.base_url if use_chain_base else self.rpc_url
        return await self._get_absolute_json(self.build_absolute_url(root, path))

    async def raw_post(self, path: str, body: Any, *, use_chain_base: bool = False) -> Any:
        root = self.base_url if use_chain_base else self.rpc_url
        return await self._post_absolute_json(self.build_absolute_url(root, path), body)

    async def resolve_name(self, name: str) -> dict[str, Any]:
        return await self._get_absolute_json(self.build_name_service_url("resolve", name))

    async def lookup_name(self, name: str) -> dict[str, Any]:
        return await self._get_absolute_json(self.build_name_service_url("lookup", name))

    async def is_name_available(self, name: str) -> dict[str, Any]:
        return await self._get_absolute_json(self.build_name_service_url("available", name))

    async def get_names_by_owner(self, address: str) -> dict[str, Any]:
        return await self._get_absolute_json(self.build_name_service_url("by-owner", address))

    async def reverse_resolve_name(self, address: str) -> dict[str, Any]:
        return await self._get_absolute_json(self.build_name_service_url("reverse", address))

    async def query_contract(self, contract: str, method: str, args: dict[str, Any] | None = None) -> dict[str, Any]:
        return await self._get_absolute_json(self.build_contract_query_url(contract, method, args))

    async def simulate(self, call: dict[str, Any]) -> dict[str, Any]:
        return await self._post_json("/simulate", call, method="simulate")

    async def send_call(self, call: dict[str, Any]) -> dict[str, Any]:
        return await self._post_json("/call", call, method="call")

    async def send_signed_call(self, call: dict[str, Any], signer: Any) -> dict[str, Any]:
        payload_json = json.dumps(call, separators=(",", ":"), sort_keys=False)
        signature = signer.sign_canonical_payload(payload_json)
        if not isinstance(signature, dict):
            raise TypeError("Signer must return a dictionary payload.")
        return await self.send_call({**call, **signature})

    async def send_sponsored_call(self, call: dict[str, Any], paymaster: str, signer: Any) -> dict[str, Any]:
        return await self.send_signed_call(self.with_paymaster(call, paymaster), signer)

    async def call_contract(
        self,
        contract: str,
        method: str,
        args: dict[str, Any] | None = None,
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        return await self.send_call(self.build_contract_call(contract, method, args, paymaster=paymaster))

    async def wait_for_receipt(self, tx_hash: str, max_attempts: int = 12, delay_seconds: float = 1.0) -> dict[str, Any]:
        for _ in range(max_attempts):
            try:
                return await self.get_receipt(tx_hash)
            except RuntimeError as exc:
                if "HTTP 404" not in str(exc):
                    raise
            await self._sleep(delay_seconds)
        raise RuntimeError("Receipt not available yet.")

    async def _sleep(self, delay_seconds: float) -> None:
        await asyncio.sleep(delay_seconds)

    async def _get_json(self, pathname: str) -> dict[str, Any]:
        return await self._get_absolute_json(f"{self.rpc_url}{pathname}")

    async def _get_absolute_json(self, url: str) -> dict[str, Any]:
        request = urllib.request.Request(
            url,
            method="GET",
            headers=self.build_auth_headers({"accept": "application/json"}),
        )
        return await asyncio.to_thread(self._read_sync_json, request)

    async def _post_json(self, pathname: str, body: Any, *, method: str) -> Any:
        return await self._post_absolute_json(f"{self.rpc_url}{pathname}", body, method=method)

    async def _post_absolute_json(self, url: str, body: Any, *, method: str = "request") -> Any:
        request = urllib.request.Request(
            url,
            method="POST",
            headers=self.build_auth_headers({
                "accept": "application/json",
                "content-type": "application/json",
            }),
            data=json.dumps(body).encode("utf-8"),
        )
        parsed = await asyncio.to_thread(self._read_sync_json, request)
        if url == self.rpc_url and isinstance(body, dict):
            return self._parse_json_rpc_response(parsed, method)
        return parsed


class DilithiumGasSponsorConnector:
    def __init__(self, client: DilithiumClient | AsyncDilithiumClient, sponsor_contract: str, paymaster: str | None = None) -> None:
        self.client = client
        self.sponsor_contract = sponsor_contract
        self.paymaster = paymaster

    def build_accept_query(self, user: str, contract: str, method: str) -> dict[str, Any]:
        return {
            "contract": self.sponsor_contract,
            "method": "accept",
            "args": {"user": user, "contract": contract, "method": method},
        }

    def build_max_gas_per_user_query(self) -> dict[str, Any]:
        return {"contract": self.sponsor_contract, "method": "max_gas_per_user", "args": {}}

    def build_remaining_quota_query(self, user: str) -> dict[str, Any]:
        return {"contract": self.sponsor_contract, "method": "remaining_quota", "args": {"user": user}}

    def build_sponsor_token_query(self) -> dict[str, Any]:
        return {"contract": self.sponsor_contract, "method": "sponsor_token", "args": {}}

    def build_fund_call(self, amount: int) -> dict[str, Any]:
        return {"contract": self.sponsor_contract, "method": "fund", "args": {"amount": amount}}

    def apply_paymaster(self, call: dict[str, Any]) -> dict[str, Any]:
        return self.client.with_paymaster(call, self.paymaster) if self.paymaster else call

    def send_sponsored_call(self, call: dict[str, Any], signer: Any) -> Any:
        return self.client.send_signed_call(self.apply_paymaster(call), signer)


class DilithiumMessagingConnector:
    def __init__(self, client: DilithiumClient | AsyncDilithiumClient, messaging_contract: str, paymaster: str | None = None) -> None:
        self.client = client
        self.messaging_contract = messaging_contract
        self.paymaster = paymaster

    def build_send_message_call(self, dest_chain: str, payload: dict[str, Any] | str) -> dict[str, Any]:
        return self.apply_paymaster(
            {
                "contract": self.messaging_contract,
                "method": "send_message",
                "args": {"dest_chain": dest_chain, "payload": payload},
            }
        )

    def build_receive_message_call(
        self,
        source_chain: str,
        source_contract: str,
        payload: dict[str, Any] | str,
    ) -> dict[str, Any]:
        return self.apply_paymaster(
            {
                "contract": self.messaging_contract,
                "method": "receive_message",
                "args": {
                    "source_chain": source_chain,
                    "source_contract": source_contract,
                    "payload": payload,
                },
            }
        )

    def query_outbox(self) -> Any:
        return self.client.query_contract(self.messaging_contract, "outbox", {})

    def query_inbox(self) -> Any:
        return self.client.query_contract(self.messaging_contract, "inbox", {})

    def send_message(self, dest_chain: str, payload: dict[str, Any] | str, signer: Any) -> Any:
        return self.client.send_signed_call(self.build_send_message_call(dest_chain, payload), signer)

    def apply_paymaster(self, call: dict[str, Any]) -> dict[str, Any]:
        return self.client.with_paymaster(call, self.paymaster) if self.paymaster else call
