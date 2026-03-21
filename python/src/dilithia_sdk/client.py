"""Dilithia SDK clients for sync and async usage.

Both :class:`DilithiaClient` (synchronous) and :class:`AsyncDilithiaClient`
(asynchronous) are backed by `httpx <https://www.python-httpx.org/>`_ and
return typed dataclass models from :mod:`dilithia_sdk.models`.
"""

from __future__ import annotations

import asyncio
import json
import time
import urllib.parse
from typing import Any

import httpx

from .exceptions import HttpError, RpcError, TimeoutError, ValidationError
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


def read_wasm_file_hex(path: str) -> str:
    """Read a ``.wasm`` binary file and return its contents as a hex-encoded string."""
    with open(path, "rb") as f:
        return f.read().hex()


# ---------------------------------------------------------------------------
# Shared helpers (URL building, payload construction)
# ---------------------------------------------------------------------------


class _ClientBase:
    """Shared configuration and URL/payload builders used by both clients."""

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
        self.rpc_url: str = rpc_url.rstrip("/")
        self.base_url: str = (
            chain_base_url.rstrip("/")
            if chain_base_url
            else self.rpc_url.removesuffix("/rpc")
        )
        self.timeout: float = timeout
        self.indexer_url: str | None = indexer_url.rstrip("/") if indexer_url else None
        self.oracle_url: str | None = oracle_url.rstrip("/") if oracle_url else None
        self.ws_url: str | None = ws_url.rstrip("/") if ws_url else self._derive_ws_url()
        self.jwt: str | None = jwt
        self.headers: dict[str, str] = headers or {}

    # -- URL helpers --------------------------------------------------------

    def _derive_ws_url(self) -> str | None:
        """Derive a WebSocket URL from the base HTTP URL."""
        if self.base_url.startswith("https://"):
            return "wss://" + self.base_url.removeprefix("https://")
        if self.base_url.startswith("http://"):
            return "ws://" + self.base_url.removeprefix("http://")
        return None

    def build_auth_headers(self, extra: dict[str, str] | None = None) -> dict[str, str]:
        """Build merged headers including JWT authorisation and user-supplied extras."""
        merged: dict[str, str] = {}
        if self.jwt:
            merged["Authorization"] = f"Bearer {self.jwt}"
        merged.update(self.headers)
        if extra:
            merged.update(extra)
        return merged

    def get_ws_connection_info(self) -> dict[str, Any]:
        """Return connection parameters suitable for a WebSocket library."""
        return {"url": self.ws_url, "headers": self.build_auth_headers()}

    def build_absolute_url(self, root: str, path: str) -> str:
        """Join *root* and *path* ensuring exactly one ``/`` separator."""
        return f"{root}{path if path.startswith('/') else '/' + path}"

    def build_name_service_url(self, action: str, value: str) -> str:
        """Build a name-service REST URL."""
        return f"{self.base_url}/names/{action}/{urllib.parse.quote(value, safe='')}"

    def build_contract_query_url(
        self, contract: str, method: str, args: dict[str, Any] | None = None
    ) -> str:
        """Build a read-only contract query URL."""
        encoded_args = urllib.parse.quote(
            json.dumps(args or {}, separators=(",", ":")), safe=""
        )
        return (
            f"{self.base_url}/query?contract={urllib.parse.quote(contract, safe='')}"
            f"&method={urllib.parse.quote(method, safe='')}&args={encoded_args}"
        )

    # -- JSON-RPC payload builders -----------------------------------------

    def build_json_rpc_request(
        self, method: str, params: dict[str, Any] | None = None, request_id: int = 1
    ) -> dict[str, Any]:
        """Build a single JSON-RPC 2.0 request envelope."""
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params or {},
        }

    def build_json_rpc_batch(
        self, calls: list[tuple[str, dict[str, Any]]], start_id: int = 1
    ) -> list[dict[str, Any]]:
        """Build a JSON-RPC batch request from a list of ``(method, params)`` tuples."""
        return [
            self.build_json_rpc_request(method, params, request_id=index)
            for index, (method, params) in enumerate(calls, start=start_id)
        ]

    def build_network_overview_batch(self) -> list[dict[str, Any]]:
        """Build a batch request for common network-overview RPCs."""
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
        """Build a batch request for transaction detail RPCs."""
        return self.build_json_rpc_batch(
            [
                ("qsc_getReceipt", {"tx_hash": tx_hash}),
                ("qsc_getTxBlock", {"tx_hash": tx_hash}),
                ("qsc_internalTxs", {"tx_hash": tx_hash}),
            ]
        )

    def build_address_details_batch(self, address: str) -> list[dict[str, Any]]:
        """Build a batch request for address detail RPCs."""
        return self.build_json_rpc_batch(
            [
                ("qsc_addressSummary", {"address": address}),
                ("qsc_getAddressTxs", {"address": address}),
            ]
        )

    # -- Call / contract payload builders -----------------------------------

    def build_contract_call(
        self,
        contract: str,
        method: str,
        args: dict[str, Any] | None = None,
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        """Build a contract call payload, optionally adding a paymaster."""
        call: dict[str, Any] = {"contract": contract, "method": method, "args": args or {}}
        return self.with_paymaster(call, paymaster) if paymaster else call

    def build_forwarder_call(
        self,
        forwarder_contract: str,
        args: dict[str, Any],
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        """Build a meta-transaction forwarder call."""
        call: dict[str, Any] = {
            "contract": forwarder_contract,
            "method": "forward",
            "args": args,
        }
        return self.with_paymaster(call, paymaster) if paymaster else call

    @staticmethod
    def with_paymaster(call: dict[str, Any], paymaster: str) -> dict[str, Any]:
        """Attach a paymaster to an existing call payload."""
        return {**call, "paymaster": paymaster}

    def build_deploy_canonical_payload(
        self,
        from_addr: str,
        name: str,
        bytecode_hash: str,
        nonce: int,
        chain_id: str,
    ) -> dict[str, Any]:
        """Return the canonical deploy payload with keys in alphabetical order."""
        return {
            "bytecode_hash": bytecode_hash,
            "chain_id": chain_id,
            "from": from_addr,
            "name": name,
            "nonce": nonce,
        }

    def deploy_contract_body(
        self,
        name: str,
        bytecode: str,
        from_addr: str,
        alg: str,
        pk: str,
        sig: str,
        nonce: int,
        chain_id: str,
        version: int = 1,
    ) -> dict[str, Any]:
        """Build the full deploy/upgrade request body."""
        return {
            "name": name,
            "bytecode": bytecode,
            "from": from_addr,
            "alg": alg,
            "pk": pk,
            "sig": sig,
            "nonce": nonce,
            "chain_id": chain_id,
            "version": version,
        }

    def deploy_contract_path(self) -> str:
        """Return the URL path for the deploy endpoint."""
        return "/deploy"

    def upgrade_contract_path(self) -> str:
        """Return the URL path for the upgrade endpoint."""
        return "/upgrade"

    def query_contract_abi_body(self, contract: str) -> dict[str, Any]:
        """Return a JSON-RPC body for ``qsc_getAbi``."""
        return self.build_json_rpc_request("qsc_getAbi", {"contract": contract})

    def build_ws_request(
        self, method: str, params: dict[str, Any] | None = None, request_id: int = 1
    ) -> dict[str, Any]:
        """Build a WebSocket JSON-RPC request (same shape as HTTP)."""
        return self.build_json_rpc_request(method, params, request_id)

    # -- Internal response parsers ------------------------------------------

    @staticmethod
    def _parse_json_rpc_response(body: dict[str, Any], method: str) -> Any:
        """Extract the ``result`` from a JSON-RPC response or raise :class:`RpcError`."""
        if "error" in body:
            err = body["error"]
            raise RpcError(
                code=int(err.get("code", -1)),
                message=str(err.get("message", "unknown rpc error")),
            )
        return body.get("result", {})

    @staticmethod
    def _parse_json_rpc_batch_response(
        body: list[dict[str, Any]], payload: list[dict[str, Any]]
    ) -> list[Any]:
        """Parse a JSON-RPC batch response, matching results to requests by ``id``."""
        items_by_id = {item["id"]: item for item in body}
        results: list[Any] = []
        for index, request in enumerate(payload, start=1):
            item = items_by_id[index]
            if "error" in item:
                err = item["error"]
                raise RpcError(
                    code=int(err.get("code", -1)),
                    message=str(err.get("message", "unknown rpc error")),
                )
            results.append(item.get("result", {}))
        return results

    # -- Typed response constructors ----------------------------------------

    @staticmethod
    def _to_address(value: str | Address) -> Address:
        """Normalise a string or Address into an Address."""
        if isinstance(value, Address):
            return value
        return Address.of(value)

    @staticmethod
    def _to_tx_hash(value: str | TxHash) -> TxHash:
        """Normalise a string or TxHash into a TxHash."""
        if isinstance(value, TxHash):
            return value
        return TxHash(value=value)

    @staticmethod
    def _parse_balance(data: dict[str, Any], address: Address) -> Balance:
        """Build a :class:`Balance` from a raw REST response."""
        raw = int(data.get("balance", data.get("value", 0)))
        return Balance(address=address, balance=TokenAmount.from_raw(raw))

    @staticmethod
    def _parse_nonce(data: dict[str, Any], address: Address) -> Nonce:
        """Build a :class:`Nonce` from a raw REST response."""
        return Nonce(address=address, next_nonce=int(data.get("nonce", 0)))

    @staticmethod
    def _parse_receipt(data: dict[str, Any]) -> Receipt:
        """Build a :class:`Receipt` from a raw REST response."""
        return Receipt(
            tx_hash=TxHash(value=str(data.get("tx_hash", ""))),
            block_height=int(data.get("block_height", 0)),
            status=str(data.get("status", "")),
            result=data.get("result"),
            error=data.get("error"),
            gas_used=int(data.get("gas_used", 0)),
            fee_paid=int(data.get("fee_paid", 0)),
        )

    @staticmethod
    def _parse_network_info(data: dict[str, Any]) -> NetworkInfo:
        """Build a :class:`NetworkInfo` from a raw RPC response."""
        return NetworkInfo(
            chain_id=str(data.get("chain_id", "")),
            block_height=int(data.get("block_height", data.get("height", 0))),
            base_fee=int(data.get("base_fee", 0)),
        )

    @staticmethod
    def _parse_gas_estimate(data: dict[str, Any]) -> GasEstimate:
        """Build a :class:`GasEstimate` from a raw RPC response."""
        return GasEstimate(
            gas_limit=int(data.get("gas_limit", 0)),
            base_fee=int(data.get("base_fee", 0)),
            estimated_cost=int(data.get("estimated_cost", 0)),
        )

    @staticmethod
    def _parse_name_record(data: dict[str, Any]) -> NameRecord:
        """Build a :class:`NameRecord` from a raw REST response."""
        return NameRecord(
            name=str(data.get("name", "")),
            address=Address(value=str(data.get("address", ""))),
        )

    @staticmethod
    def _handle_httpx_error(exc: httpx.HTTPStatusError) -> None:
        """Translate an httpx status error into :class:`HttpError`."""
        raise HttpError(
            status_code=exc.response.status_code,
            body=exc.response.text,
        ) from exc

    @staticmethod
    def _handle_httpx_timeout(exc: httpx.TimeoutException) -> None:
        """Translate an httpx timeout into :class:`TimeoutError`."""
        raise TimeoutError(str(exc)) from exc


# ---------------------------------------------------------------------------
# Synchronous client
# ---------------------------------------------------------------------------


class DilithiaClient(_ClientBase):
    """Synchronous Dilithia client backed by :class:`httpx.Client`."""

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
        self._http = httpx.Client(timeout=timeout)

    def close(self) -> None:
        """Close the underlying HTTP client."""
        self._http.close()

    def __enter__(self) -> DilithiaClient:
        return self

    def __exit__(self, *_: Any) -> None:
        self.close()

    # -- Low-level HTTP -----------------------------------------------------

    def _get_json(self, pathname: str) -> Any:
        """GET ``rpc_url + pathname`` and return parsed JSON."""
        return self._get_absolute_json(f"{self.rpc_url}{pathname}")

    def _get_absolute_json(self, url: str) -> Any:
        """GET an absolute URL and return parsed JSON."""
        try:
            resp = self._http.get(
                url, headers=self.build_auth_headers({"accept": "application/json"})
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPStatusError as exc:
            self._handle_httpx_error(exc)
        except httpx.TimeoutException as exc:
            self._handle_httpx_timeout(exc)

    def _post_json(self, pathname: str, body: Any) -> Any:
        """POST JSON to ``rpc_url + pathname`` and return parsed JSON."""
        return self._post_absolute_json(f"{self.rpc_url}{pathname}", body)

    def _post_absolute_json(self, url: str, body: Any) -> Any:
        """POST JSON to an absolute URL and return parsed JSON."""
        try:
            resp = self._http.post(
                url,
                headers=self.build_auth_headers(
                    {"accept": "application/json", "content-type": "application/json"}
                ),
                content=json.dumps(body).encode("utf-8"),
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPStatusError as exc:
            self._handle_httpx_error(exc)
        except httpx.TimeoutException as exc:
            self._handle_httpx_timeout(exc)

    # -- Typed reads --------------------------------------------------------

    def balance(self, address: str | Address) -> Balance:
        """Fetch the balance for *address*."""
        addr = self._to_address(address)
        data = self._get_json(f"/balance/{urllib.parse.quote(str(addr), safe='')}")
        return self._parse_balance(data, addr)

    def nonce(self, address: str | Address) -> Nonce:
        """Fetch the next nonce for *address*."""
        addr = self._to_address(address)
        data = self._get_json(f"/nonce/{urllib.parse.quote(str(addr), safe='')}")
        return self._parse_nonce(data, addr)

    def receipt(self, tx_hash: str | TxHash) -> Receipt:
        """Fetch the receipt for *tx_hash*."""
        h = self._to_tx_hash(tx_hash)
        data = self._get_json(f"/receipt/{urllib.parse.quote(str(h), safe='')}")
        return self._parse_receipt(data)

    def wait_for_receipt(
        self, tx_hash: str | TxHash, max_attempts: int = 30, delay: float = 2.0
    ) -> Receipt:
        """Poll for a receipt up to *max_attempts* times with *delay* seconds between tries."""
        for _ in range(max_attempts):
            try:
                return self.receipt(tx_hash)
            except HttpError as exc:
                if exc.status_code != 404:
                    raise
            time.sleep(delay)
        raise TimeoutError("Receipt not available after polling.")

    def network_info(self) -> NetworkInfo:
        """Fetch high-level network information via JSON-RPC."""
        data = self.json_rpc("qsc_head", {})
        return self._parse_network_info(data)

    def gas_estimate(self) -> GasEstimate:
        """Fetch the current gas estimate."""
        data = self.json_rpc("qsc_gasEstimate", {})
        return self._parse_gas_estimate(data)

    def query_contract(
        self, contract: str, method: str, args: dict[str, Any] | None = None
    ) -> QueryResult:
        """Execute a read-only contract query."""
        data = self._get_absolute_json(
            self.build_contract_query_url(contract, method, args)
        )
        return QueryResult(value=data)

    # -- Names --------------------------------------------------------------

    def resolve_name(self, name: str) -> NameRecord:
        """Resolve a human-readable name to an address."""
        data = self._get_absolute_json(self.build_name_service_url("resolve", name))
        return self._parse_name_record(data)

    def reverse_resolve(self, address: str | Address) -> NameRecord:
        """Reverse-resolve an address to a name."""
        addr = self._to_address(address)
        data = self._get_absolute_json(
            self.build_name_service_url("reverse", str(addr))
        )
        return self._parse_name_record(data)

    def lookup_name(self, name: str) -> dict[str, Any]:
        """Look up detailed name information."""
        return self._get_absolute_json(self.build_name_service_url("lookup", name))

    def is_name_available(self, name: str) -> dict[str, Any]:
        """Check whether a name is available for registration."""
        return self._get_absolute_json(self.build_name_service_url("available", name))

    def get_names_by_owner(self, address: str) -> dict[str, Any]:
        """Return all names owned by *address*."""
        return self._get_absolute_json(self.build_name_service_url("by-owner", address))

    # -- Transactions -------------------------------------------------------

    def send_call(self, call: dict[str, Any]) -> dict[str, Any]:
        """Submit a transaction call to the chain."""
        return self._post_json("/call", call)

    def send_signed_call(self, call: dict[str, Any], signer: Any) -> dict[str, Any]:
        """Sign *call* with *signer* and submit it."""
        payload_json = json.dumps(call, separators=(",", ":"), sort_keys=False)
        signature = signer.sign_canonical_payload(payload_json)
        if not isinstance(signature, dict):
            raise ValidationError("Signer must return a dictionary payload.")
        return self.send_call({**call, **signature})

    def send_sponsored_call(
        self, call: dict[str, Any], paymaster: str, signer: Any
    ) -> dict[str, Any]:
        """Sign and submit *call* with an attached paymaster."""
        return self.send_signed_call(self.with_paymaster(call, paymaster), signer)

    def call_contract(
        self,
        contract: str,
        method: str,
        args: dict[str, Any] | None = None,
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        """Build a contract call and submit it."""
        return self.send_call(
            self.build_contract_call(contract, method, args, paymaster=paymaster)
        )

    def simulate(self, call: dict[str, Any]) -> dict[str, Any]:
        """Simulate a call without committing state."""
        return self._post_json("/simulate", call)

    # -- Deploy / upgrade ---------------------------------------------------

    def deploy_contract(self, body: dict[str, Any]) -> dict[str, Any]:
        """POST a deploy request body to the deploy endpoint."""
        return self._post_json(self.deploy_contract_path(), body)

    def upgrade_contract(self, body: dict[str, Any]) -> dict[str, Any]:
        """POST an upgrade request body to the upgrade endpoint."""
        return self._post_json(self.upgrade_contract_path(), body)

    # -- Shielded -----------------------------------------------------------

    def shielded_deposit(
        self, commitment: str, value: int, proof_hex: str
    ) -> dict[str, Any]:
        """Submit a shielded deposit."""
        return self._post_json(
            "/shielded/deposit",
            {"commitment": commitment, "value": value, "proof": proof_hex},
        )

    def shielded_withdraw(
        self,
        nullifier: str,
        amount: int,
        recipient: str,
        proof_hex: str,
        commitment_root: str,
    ) -> dict[str, Any]:
        """Submit a shielded withdrawal."""
        return self._post_json(
            "/shielded/withdraw",
            {
                "nullifier": nullifier,
                "amount": amount,
                "recipient": recipient,
                "proof": proof_hex,
                "commitment_root": commitment_root,
            },
        )

    def commitment_root(self) -> str:
        """Fetch the current shielded commitment root."""
        data = self._get_json("/shielded/commitment-root")
        return str(data.get("commitment_root", data.get("root", "")))

    def is_nullifier_spent(self, nullifier: str) -> bool:
        """Check whether a nullifier has already been spent."""
        data = self._get_json(
            f"/shielded/nullifier/{urllib.parse.quote(nullifier, safe='')}"
        )
        return bool(data.get("spent", False))

    # -- JSON-RPC -----------------------------------------------------------

    def json_rpc(self, method: str, params: dict[str, Any] | None = None) -> Any:
        """Execute a single JSON-RPC call and return the ``result``."""
        body = self._post_json("", self.build_json_rpc_request(method, params))
        return self._parse_json_rpc_response(body, method)

    def json_rpc_batch(
        self, calls: list[tuple[str, dict[str, Any]]], start_id: int = 1
    ) -> list[Any]:
        """Execute a JSON-RPC batch and return ordered results."""
        payload = self.build_json_rpc_batch(calls, start_id=start_id)
        body = self._post_json("", payload)
        return self._parse_json_rpc_batch_response(body, payload)

    # -- Raw helpers (for explorer RPC methods, etc.) -----------------------

    def raw_rpc(
        self, method: str, params: dict[str, Any] | None = None
    ) -> Any:
        """Alias for :meth:`json_rpc`."""
        return self.json_rpc(method, params)

    def raw_get(self, path: str, *, use_chain_base: bool = False) -> Any:
        """GET an arbitrary path on the RPC or chain-base URL."""
        root = self.base_url if use_chain_base else self.rpc_url
        return self._get_absolute_json(self.build_absolute_url(root, path))

    def raw_post(self, path: str, body: Any, *, use_chain_base: bool = False) -> Any:
        """POST an arbitrary payload to the RPC or chain-base URL."""
        root = self.base_url if use_chain_base else self.rpc_url
        return self._post_absolute_json(self.build_absolute_url(root, path), body)

    # -- Explorer convenience methods (delegate to json_rpc) ----------------

    def get_address_summary(self, address: str) -> dict[str, Any]:
        """Fetch the address summary via ``qsc_addressSummary``."""
        return self.json_rpc("qsc_addressSummary", {"address": address})

    def get_head(self) -> dict[str, Any]:
        """Fetch the latest head via ``qsc_head``."""
        return self.json_rpc("qsc_head", {})

    def get_chain(self) -> dict[str, Any]:
        """Fetch chain metadata via ``qsc_chain``."""
        return self.json_rpc("qsc_chain", {})

    def get_state_root(self) -> dict[str, Any]:
        """Fetch the state root via ``qsc_stateRoot``."""
        return self.json_rpc("qsc_stateRoot", {})

    def get_tps(self) -> dict[str, Any]:
        """Fetch the current TPS via ``qsc_tps``."""
        return self.json_rpc("qsc_tps", {})

    def get_gas_estimate(self) -> dict[str, Any]:
        """Fetch the gas estimate via ``qsc_gasEstimate``."""
        return self.json_rpc("qsc_gasEstimate", {})

    def get_base_fee(self) -> dict[str, Any]:
        """Fetch the base fee via ``qsc_baseFee``."""
        return self.json_rpc("qsc_baseFee", {})

    def get_block(self, height: int) -> dict[str, Any]:
        """Fetch a single block by height."""
        return self.json_rpc("qsc_getBlock", {"height": height})

    def get_blocks(self, from_height: int, to_height: int) -> dict[str, Any]:
        """Fetch a range of blocks."""
        return self.json_rpc("qsc_getBlocks", {"from": from_height, "to": to_height})

    def get_tx_block(self, tx_hash: str) -> dict[str, Any]:
        """Fetch the block that contains *tx_hash*."""
        return self.json_rpc("qsc_getTxBlock", {"tx_hash": tx_hash})

    def get_internal_txs(self, tx_hash: str) -> dict[str, Any]:
        """Fetch internal transactions for *tx_hash*."""
        return self.json_rpc("qsc_internalTxs", {"tx_hash": tx_hash})

    def get_address_txs(self, address: str) -> dict[str, Any]:
        """Fetch transactions involving *address*."""
        return self.json_rpc("qsc_getAddressTxs", {"address": address})

    def search_hash(self, hash_value: str) -> dict[str, Any]:
        """Search by hash."""
        return self.json_rpc("qsc_search", {"hash": hash_value})

    def query_contract_abi(self, contract: str) -> dict[str, Any]:
        """Fetch the ABI for a contract via JSON-RPC."""
        return self.json_rpc("qsc_getAbi", {"contract": contract})

    # -- Legacy REST reads (untyped, kept for backward compat) --------------

    def get_balance(self, address: str) -> dict[str, Any]:
        """Fetch raw balance JSON (prefer :meth:`balance` for typed result)."""
        return self._get_json(f"/balance/{urllib.parse.quote(address, safe='')}")

    def get_nonce(self, address: str) -> dict[str, Any]:
        """Fetch raw nonce JSON (prefer :meth:`nonce` for typed result)."""
        return self._get_json(f"/nonce/{urllib.parse.quote(address, safe='')}")

    def get_receipt(self, tx_hash: str) -> dict[str, Any]:
        """Fetch raw receipt JSON (prefer :meth:`receipt` for typed result)."""
        return self._get_json(f"/receipt/{urllib.parse.quote(tx_hash, safe='')}")


# ---------------------------------------------------------------------------
# Asynchronous client
# ---------------------------------------------------------------------------


class AsyncDilithiaClient(_ClientBase):
    """Asynchronous Dilithia client backed by :class:`httpx.AsyncClient`."""

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
        self._http = httpx.AsyncClient(timeout=timeout)

    async def aclose(self) -> None:
        """Close the underlying async HTTP client."""
        await self._http.aclose()

    async def __aenter__(self) -> AsyncDilithiaClient:
        return self

    async def __aexit__(self, *_: Any) -> None:
        await self.aclose()

    # -- Low-level HTTP -----------------------------------------------------

    async def _get_json(self, pathname: str) -> Any:
        """GET ``rpc_url + pathname`` and return parsed JSON."""
        return await self._get_absolute_json(f"{self.rpc_url}{pathname}")

    async def _get_absolute_json(self, url: str) -> Any:
        """GET an absolute URL and return parsed JSON."""
        try:
            resp = await self._http.get(
                url, headers=self.build_auth_headers({"accept": "application/json"})
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPStatusError as exc:
            self._handle_httpx_error(exc)
        except httpx.TimeoutException as exc:
            self._handle_httpx_timeout(exc)

    async def _post_json(self, pathname: str, body: Any) -> Any:
        """POST JSON to ``rpc_url + pathname`` and return parsed JSON."""
        return await self._post_absolute_json(f"{self.rpc_url}{pathname}", body)

    async def _post_absolute_json(self, url: str, body: Any) -> Any:
        """POST JSON to an absolute URL and return parsed JSON."""
        try:
            resp = await self._http.post(
                url,
                headers=self.build_auth_headers(
                    {"accept": "application/json", "content-type": "application/json"}
                ),
                content=json.dumps(body).encode("utf-8"),
            )
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPStatusError as exc:
            self._handle_httpx_error(exc)
        except httpx.TimeoutException as exc:
            self._handle_httpx_timeout(exc)

    # -- Typed reads --------------------------------------------------------

    async def balance(self, address: str | Address) -> Balance:
        """Fetch the balance for *address*."""
        addr = self._to_address(address)
        data = await self._get_json(f"/balance/{urllib.parse.quote(str(addr), safe='')}")
        return self._parse_balance(data, addr)

    async def nonce(self, address: str | Address) -> Nonce:
        """Fetch the next nonce for *address*."""
        addr = self._to_address(address)
        data = await self._get_json(f"/nonce/{urllib.parse.quote(str(addr), safe='')}")
        return self._parse_nonce(data, addr)

    async def receipt(self, tx_hash: str | TxHash) -> Receipt:
        """Fetch the receipt for *tx_hash*."""
        h = self._to_tx_hash(tx_hash)
        data = await self._get_json(f"/receipt/{urllib.parse.quote(str(h), safe='')}")
        return self._parse_receipt(data)

    async def wait_for_receipt(
        self, tx_hash: str | TxHash, max_attempts: int = 30, delay: float = 2.0
    ) -> Receipt:
        """Poll for a receipt up to *max_attempts* times with *delay* seconds between tries."""
        for _ in range(max_attempts):
            try:
                return await self.receipt(tx_hash)
            except HttpError as exc:
                if exc.status_code != 404:
                    raise
            await asyncio.sleep(delay)
        raise TimeoutError("Receipt not available after polling.")

    async def network_info(self) -> NetworkInfo:
        """Fetch high-level network information via JSON-RPC."""
        data = await self.json_rpc("qsc_head", {})
        return self._parse_network_info(data)

    async def gas_estimate(self) -> GasEstimate:
        """Fetch the current gas estimate."""
        data = await self.json_rpc("qsc_gasEstimate", {})
        return self._parse_gas_estimate(data)

    async def query_contract(
        self, contract: str, method: str, args: dict[str, Any] | None = None
    ) -> QueryResult:
        """Execute a read-only contract query."""
        data = await self._get_absolute_json(
            self.build_contract_query_url(contract, method, args)
        )
        return QueryResult(value=data)

    # -- Names --------------------------------------------------------------

    async def resolve_name(self, name: str) -> NameRecord:
        """Resolve a human-readable name to an address."""
        data = await self._get_absolute_json(
            self.build_name_service_url("resolve", name)
        )
        return self._parse_name_record(data)

    async def reverse_resolve(self, address: str | Address) -> NameRecord:
        """Reverse-resolve an address to a name."""
        addr = self._to_address(address)
        data = await self._get_absolute_json(
            self.build_name_service_url("reverse", str(addr))
        )
        return self._parse_name_record(data)

    async def lookup_name(self, name: str) -> dict[str, Any]:
        """Look up detailed name information."""
        return await self._get_absolute_json(
            self.build_name_service_url("lookup", name)
        )

    async def is_name_available(self, name: str) -> dict[str, Any]:
        """Check whether a name is available for registration."""
        return await self._get_absolute_json(
            self.build_name_service_url("available", name)
        )

    async def get_names_by_owner(self, address: str) -> dict[str, Any]:
        """Return all names owned by *address*."""
        return await self._get_absolute_json(
            self.build_name_service_url("by-owner", address)
        )

    # -- Transactions -------------------------------------------------------

    async def send_call(self, call: dict[str, Any]) -> dict[str, Any]:
        """Submit a transaction call to the chain."""
        return await self._post_json("/call", call)

    async def send_signed_call(self, call: dict[str, Any], signer: Any) -> dict[str, Any]:
        """Sign *call* with *signer* and submit it."""
        payload_json = json.dumps(call, separators=(",", ":"), sort_keys=False)
        signature = signer.sign_canonical_payload(payload_json)
        if not isinstance(signature, dict):
            raise ValidationError("Signer must return a dictionary payload.")
        return await self.send_call({**call, **signature})

    async def send_sponsored_call(
        self, call: dict[str, Any], paymaster: str, signer: Any
    ) -> dict[str, Any]:
        """Sign and submit *call* with an attached paymaster."""
        return await self.send_signed_call(
            self.with_paymaster(call, paymaster), signer
        )

    async def call_contract(
        self,
        contract: str,
        method: str,
        args: dict[str, Any] | None = None,
        *,
        paymaster: str | None = None,
    ) -> dict[str, Any]:
        """Build a contract call and submit it."""
        return await self.send_call(
            self.build_contract_call(contract, method, args, paymaster=paymaster)
        )

    async def simulate(self, call: dict[str, Any]) -> dict[str, Any]:
        """Simulate a call without committing state."""
        return await self._post_json("/simulate", call)

    # -- Deploy / upgrade ---------------------------------------------------

    async def deploy_contract(self, body: dict[str, Any]) -> dict[str, Any]:
        """POST a deploy request body to the deploy endpoint."""
        return await self._post_json(self.deploy_contract_path(), body)

    async def upgrade_contract(self, body: dict[str, Any]) -> dict[str, Any]:
        """POST an upgrade request body to the upgrade endpoint."""
        return await self._post_json(self.upgrade_contract_path(), body)

    # -- Shielded -----------------------------------------------------------

    async def shielded_deposit(
        self, commitment: str, value: int, proof_hex: str
    ) -> dict[str, Any]:
        """Submit a shielded deposit."""
        return await self._post_json(
            "/shielded/deposit",
            {"commitment": commitment, "value": value, "proof": proof_hex},
        )

    async def shielded_withdraw(
        self,
        nullifier: str,
        amount: int,
        recipient: str,
        proof_hex: str,
        commitment_root: str,
    ) -> dict[str, Any]:
        """Submit a shielded withdrawal."""
        return await self._post_json(
            "/shielded/withdraw",
            {
                "nullifier": nullifier,
                "amount": amount,
                "recipient": recipient,
                "proof": proof_hex,
                "commitment_root": commitment_root,
            },
        )

    async def commitment_root(self) -> str:
        """Fetch the current shielded commitment root."""
        data = await self._get_json("/shielded/commitment-root")
        return str(data.get("commitment_root", data.get("root", "")))

    async def is_nullifier_spent(self, nullifier: str) -> bool:
        """Check whether a nullifier has already been spent."""
        data = await self._get_json(
            f"/shielded/nullifier/{urllib.parse.quote(nullifier, safe='')}"
        )
        return bool(data.get("spent", False))

    # -- JSON-RPC -----------------------------------------------------------

    async def json_rpc(self, method: str, params: dict[str, Any] | None = None) -> Any:
        """Execute a single JSON-RPC call and return the ``result``."""
        body = await self._post_json("", self.build_json_rpc_request(method, params))
        return self._parse_json_rpc_response(body, method)

    async def json_rpc_batch(
        self, calls: list[tuple[str, dict[str, Any]]], start_id: int = 1
    ) -> list[Any]:
        """Execute a JSON-RPC batch and return ordered results."""
        payload = self.build_json_rpc_batch(calls, start_id=start_id)
        body = await self._post_json("", payload)
        return self._parse_json_rpc_batch_response(body, payload)

    # -- Raw helpers --------------------------------------------------------

    async def raw_rpc(
        self, method: str, params: dict[str, Any] | None = None
    ) -> Any:
        """Alias for :meth:`json_rpc`."""
        return await self.json_rpc(method, params)

    async def raw_get(self, path: str, *, use_chain_base: bool = False) -> Any:
        """GET an arbitrary path on the RPC or chain-base URL."""
        root = self.base_url if use_chain_base else self.rpc_url
        return await self._get_absolute_json(self.build_absolute_url(root, path))

    async def raw_post(
        self, path: str, body: Any, *, use_chain_base: bool = False
    ) -> Any:
        """POST an arbitrary payload to the RPC or chain-base URL."""
        root = self.base_url if use_chain_base else self.rpc_url
        return await self._post_absolute_json(self.build_absolute_url(root, path), body)

    # -- Explorer convenience methods (delegate to json_rpc) ----------------

    async def get_address_summary(self, address: str) -> dict[str, Any]:
        """Fetch the address summary via ``qsc_addressSummary``."""
        return await self.json_rpc("qsc_addressSummary", {"address": address})

    async def get_head(self) -> dict[str, Any]:
        """Fetch the latest head via ``qsc_head``."""
        return await self.json_rpc("qsc_head", {})

    async def get_chain(self) -> dict[str, Any]:
        """Fetch chain metadata via ``qsc_chain``."""
        return await self.json_rpc("qsc_chain", {})

    async def get_state_root(self) -> dict[str, Any]:
        """Fetch the state root via ``qsc_stateRoot``."""
        return await self.json_rpc("qsc_stateRoot", {})

    async def get_tps(self) -> dict[str, Any]:
        """Fetch the current TPS via ``qsc_tps``."""
        return await self.json_rpc("qsc_tps", {})

    async def get_gas_estimate(self) -> dict[str, Any]:
        """Fetch the gas estimate via ``qsc_gasEstimate``."""
        return await self.json_rpc("qsc_gasEstimate", {})

    async def get_base_fee(self) -> dict[str, Any]:
        """Fetch the base fee via ``qsc_baseFee``."""
        return await self.json_rpc("qsc_baseFee", {})

    async def get_block(self, height: int) -> dict[str, Any]:
        """Fetch a single block by height."""
        return await self.json_rpc("qsc_getBlock", {"height": height})

    async def get_blocks(self, from_height: int, to_height: int) -> dict[str, Any]:
        """Fetch a range of blocks."""
        return await self.json_rpc("qsc_getBlocks", {"from": from_height, "to": to_height})

    async def get_tx_block(self, tx_hash: str) -> dict[str, Any]:
        """Fetch the block that contains *tx_hash*."""
        return await self.json_rpc("qsc_getTxBlock", {"tx_hash": tx_hash})

    async def get_internal_txs(self, tx_hash: str) -> dict[str, Any]:
        """Fetch internal transactions for *tx_hash*."""
        return await self.json_rpc("qsc_internalTxs", {"tx_hash": tx_hash})

    async def get_address_txs(self, address: str) -> dict[str, Any]:
        """Fetch transactions involving *address*."""
        return await self.json_rpc("qsc_getAddressTxs", {"address": address})

    async def search_hash(self, hash_value: str) -> dict[str, Any]:
        """Search by hash."""
        return await self.json_rpc("qsc_search", {"hash": hash_value})

    async def query_contract_abi(self, contract: str) -> dict[str, Any]:
        """Fetch the ABI for a contract via JSON-RPC."""
        return await self.json_rpc("qsc_getAbi", {"contract": contract})

    # -- Legacy REST reads (untyped, kept for backward compat) --------------

    async def get_balance(self, address: str) -> dict[str, Any]:
        """Fetch raw balance JSON (prefer :meth:`balance` for typed result)."""
        return await self._get_json(f"/balance/{urllib.parse.quote(address, safe='')}")

    async def get_nonce(self, address: str) -> dict[str, Any]:
        """Fetch raw nonce JSON (prefer :meth:`nonce` for typed result)."""
        return await self._get_json(f"/nonce/{urllib.parse.quote(address, safe='')}")

    async def get_receipt(self, tx_hash: str) -> dict[str, Any]:
        """Fetch raw receipt JSON (prefer :meth:`receipt` for typed result)."""
        return await self._get_json(f"/receipt/{urllib.parse.quote(tx_hash, safe='')}")


# ---------------------------------------------------------------------------
# Connectors
# ---------------------------------------------------------------------------


class DilithiaGasSponsorConnector:
    """Helper for interacting with an on-chain gas-sponsor contract.

    Works with both :class:`DilithiaClient` and :class:`AsyncDilithiaClient`.
    """

    def __init__(
        self,
        client: DilithiaClient | AsyncDilithiaClient,
        sponsor_contract: str,
        paymaster: str | None = None,
    ) -> None:
        self.client = client
        self.sponsor_contract = sponsor_contract
        self.paymaster = paymaster

    def build_accept_query(
        self, user: str, contract: str, method: str
    ) -> dict[str, Any]:
        """Build a query to check whether the sponsor accepts a call."""
        return {
            "contract": self.sponsor_contract,
            "method": "accept",
            "args": {"user": user, "contract": contract, "method": method},
        }

    def build_max_gas_per_user_query(self) -> dict[str, Any]:
        """Build a query for the per-user gas cap."""
        return {
            "contract": self.sponsor_contract,
            "method": "max_gas_per_user",
            "args": {},
        }

    def build_remaining_quota_query(self, user: str) -> dict[str, Any]:
        """Build a query for the remaining gas quota of *user*."""
        return {
            "contract": self.sponsor_contract,
            "method": "remaining_quota",
            "args": {"user": user},
        }

    def build_sponsor_token_query(self) -> dict[str, Any]:
        """Build a query for the sponsor token address."""
        return {
            "contract": self.sponsor_contract,
            "method": "sponsor_token",
            "args": {},
        }

    def build_fund_call(self, amount: int) -> dict[str, Any]:
        """Build a call to fund the sponsor with *amount*."""
        return {
            "contract": self.sponsor_contract,
            "method": "fund",
            "args": {"amount": amount},
        }

    def apply_paymaster(self, call: dict[str, Any]) -> dict[str, Any]:
        """Attach the configured paymaster to *call* if one is set."""
        return (
            self.client.with_paymaster(call, self.paymaster) if self.paymaster else call
        )

    def send_sponsored_call(self, call: dict[str, Any], signer: Any) -> Any:
        """Sign and submit a sponsored call."""
        return self.client.send_signed_call(self.apply_paymaster(call), signer)


class DilithiaMessagingConnector:
    """Helper for cross-chain messaging via the messaging contract.

    Works with both :class:`DilithiaClient` and :class:`AsyncDilithiaClient`.
    """

    def __init__(
        self,
        client: DilithiaClient | AsyncDilithiaClient,
        messaging_contract: str,
        paymaster: str | None = None,
    ) -> None:
        self.client = client
        self.messaging_contract = messaging_contract
        self.paymaster = paymaster

    def build_send_message_call(
        self, dest_chain: str, payload: dict[str, Any] | str
    ) -> dict[str, Any]:
        """Build a call to send a cross-chain message."""
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
        """Build a call to receive a cross-chain message."""
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
        """Query the messaging outbox."""
        return self.client.query_contract(self.messaging_contract, "outbox", {})

    def query_inbox(self) -> Any:
        """Query the messaging inbox."""
        return self.client.query_contract(self.messaging_contract, "inbox", {})

    def send_message(
        self, dest_chain: str, payload: dict[str, Any] | str, signer: Any
    ) -> Any:
        """Sign and submit a cross-chain message."""
        return self.client.send_signed_call(
            self.build_send_message_call(dest_chain, payload), signer
        )

    def apply_paymaster(self, call: dict[str, Any]) -> dict[str, Any]:
        """Attach the configured paymaster to *call* if one is set."""
        return (
            self.client.with_paymaster(call, self.paymaster) if self.paymaster else call
        )
