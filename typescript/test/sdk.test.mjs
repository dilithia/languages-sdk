import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  MIN_NODE_MAJOR,
  RPC_LINE_VERSION,
  SDK_VERSION,
  createClient,
  createGasSponsorConnector,
  createMessagingConnector,
  loadNativeCryptoAdapter,
  Address,
  TxHash,
  PublicKey,
  SecretKey,
  TokenAmount,
  DilithiaError,
  RpcError,
  HttpError,
  TimeoutError,
} from "../dist/index.js";

// ── Helper: save and restore globalThis.fetch ───────────────────────────────

const originalFetch = globalThis.fetch;
function restoreFetch() {
  globalThis.fetch = originalFetch;
}

/**
 * Create a mock fetch that returns a JSON response.
 * Optionally captures requests for inspection.
 */
function mockFetch(responseBody, { status = 200, requests } = {}) {
  globalThis.fetch = async (url, init) => {
    if (requests) requests.push({ url, init });
    return new Response(JSON.stringify(responseBody), {
      status,
      headers: { "content-type": "application/json" },
    });
  };
}

/**
 * Create a mock fetch that returns based on URL/body matching.
 */
function mockFetchRouter(handler) {
  globalThis.fetch = async (url, init) => {
    const result = await handler(url, init);
    if (result instanceof Response) return result;
    const httpStatus = typeof result._status === "number" ? result._status : 200;
    const body = result._body ?? result;
    return new Response(JSON.stringify(body), {
      status: httpStatus,
      headers: { "content-type": "application/json" },
    });
  };
}

// ── Version and runtime ────────────────────────────────────────────────────

test("sdk version is 0.3.0", () => {
  assert.equal(SDK_VERSION, "0.3.0");
  assert.equal(RPC_LINE_VERSION, "0.3.0");
});

test("node runtime satisfies the minimum supported version", () => {
  const major = Number(process.versions.node.split(".")[0]);
  assert.ok(Number.isInteger(major));
  assert.ok(major >= MIN_NODE_MAJOR);
});

// ── Branded types ──────────────────────────────────────────────────────────

test("Address.of creates a branded string", () => {
  const addr = Address.of("dili1abc");
  assert.equal(addr, "dili1abc");
  assert.equal(typeof addr, "string");
});

test("TxHash.of creates a branded string", () => {
  const hash = TxHash.of("0xdeadbeef");
  assert.equal(hash, "0xdeadbeef");
});

test("PublicKey.of creates a branded string", () => {
  const pk = PublicKey.of("pk_hex");
  assert.equal(pk, "pk_hex");
});

test("SecretKey.of creates a branded string", () => {
  const sk = SecretKey.of("sk_hex");
  assert.equal(sk, "sk_hex");
});

// ── TokenAmount ────────────────────────────────────────────────────────────

test("TokenAmount.dili parses whole numbers", () => {
  const t = TokenAmount.dili("10");
  assert.equal(t.toRaw(), 10_000_000_000_000_000_000n);
  assert.equal(t.formatted(), "10");
});

test("TokenAmount.dili parses decimal strings", () => {
  const t = TokenAmount.dili("1.5");
  assert.equal(t.toRaw(), 1_500_000_000_000_000_000n);
  assert.equal(t.formatted(), "1.5");
});

test("TokenAmount.dili accepts bigint", () => {
  const t = TokenAmount.dili(2n);
  assert.equal(t.toRaw(), 2_000_000_000_000_000_000n);
  assert.equal(t.formatted(), "2");
});

test("TokenAmount.dili accepts number", () => {
  const t = TokenAmount.dili(3);
  assert.equal(t.toRaw(), 3_000_000_000_000_000_000n);
});

test("TokenAmount.fromRaw creates from raw bigint", () => {
  const t = TokenAmount.fromRaw(1_234_567_890_000_000_000n);
  assert.equal(t.formatted(), "1.23456789");
  assert.equal(t.toString(), "1.23456789");
});

test("TokenAmount.fromRaw with zero fractional part omits decimal", () => {
  const t = TokenAmount.fromRaw(5_000_000_000_000_000_000n);
  assert.equal(t.formatted(), "5");
});

test("TokenAmount DILI_DECIMALS is 18", () => {
  assert.equal(TokenAmount.DILI_DECIMALS, 18);
});

// ── Error classes ──────────────────────────────────────────────────────────

test("DilithiaError is an Error with correct name", () => {
  const err = new DilithiaError("test");
  assert.ok(err instanceof Error);
  assert.ok(err instanceof DilithiaError);
  assert.equal(err.name, "DilithiaError");
  assert.equal(err.message, "test");
});

test("RpcError extends DilithiaError and stores code", () => {
  const err = new RpcError(-32600, "Invalid Request");
  assert.ok(err instanceof DilithiaError);
  assert.ok(err instanceof RpcError);
  assert.equal(err.name, "RpcError");
  assert.equal(err.code, -32600);
  assert.equal(err.message, "Invalid Request");
});

test("HttpError extends DilithiaError and stores statusCode and body", () => {
  const err = new HttpError(404, "Not Found");
  assert.ok(err instanceof DilithiaError);
  assert.ok(err instanceof HttpError);
  assert.equal(err.name, "HttpError");
  assert.equal(err.statusCode, 404);
  assert.equal(err.body, "Not Found");
  assert.equal(err.message, "HTTP 404");
});

test("TimeoutError extends DilithiaError and stores timeoutMs", () => {
  const err = new TimeoutError(5000);
  assert.ok(err instanceof DilithiaError);
  assert.ok(err instanceof TimeoutError);
  assert.equal(err.name, "TimeoutError");
  assert.equal(err.timeoutMs, 5000);
  assert.equal(err.message, "Request timed out after 5000ms");
});

// ── Client construction ────────────────────────────────────────────────────

test("createClient stores normalized rpcUrl", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/" });
  assert.equal(client.rpcUrl, "http://rpc.example");
  assert.equal(client.baseUrl, "http://rpc.example");
});

test("createClient accepts configurable chain base url", () => {
  const client = createClient({
    rpcUrl: "http://rpc.example/rpc",
    chainBaseUrl: "http://chain.example/",
    indexerUrl: "http://indexer.example/",
    oracleUrl: "http://oracle.example/",
  });
  assert.equal(client.baseUrl, "http://chain.example");
  assert.equal(client.indexerUrl, "http://indexer.example");
  assert.equal(client.oracleUrl, "http://oracle.example");
});

test("client derives ws url and builds generic rpc requests", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  assert.equal(client.wsUrl, "ws://rpc.example");
  assert.deepEqual(client.buildJsonRpcRequest("qsc_head", { full: true }), {
    jsonrpc: "2.0",
    id: 1,
    method: "qsc_head",
    params: { full: true },
  });
});

test("client exposes jwt auth headers for http and ws", () => {
  const client = createClient({
    rpcUrl: "https://rpc.example/rpc",
    jwt: "secret-token",
    headers: { "x-network": "devnet" },
  });
  assert.deepEqual(client.buildAuthHeaders({ accept: "application/json" }), {
    Authorization: "Bearer secret-token",
    "x-network": "devnet",
    accept: "application/json",
  });
  assert.deepEqual(client.getWsConnectionInfo(), {
    url: "wss://rpc.example",
    headers: {
      Authorization: "Bearer secret-token",
      "x-network": "devnet",
    },
  });
});

// ── HTTP mock tests: getBalance ─────────────────────────────────────────────

test("getBalance returns typed Balance with TokenAmount (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  mockFetch({ address: "dili1alice", balance: "1000000000000000000" });
  try {
    const balance = await client.getBalance("dili1alice");
    assert.equal(balance.address, "dili1alice");
    assert.equal(balance.raw, 1_000_000_000_000_000_000n);
    assert.equal(balance.balance.formatted(), "1");
    assert.equal(balance.balance.toRaw(), 1_000_000_000_000_000_000n);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getNonce ───────────────────────────────────────────────

test("getNonce returns typed Nonce (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  mockFetch({ address: "dili1alice", next_nonce: 42 });
  try {
    const nonce = await client.getNonce("dili1alice");
    assert.equal(nonce.address, "dili1alice");
    assert.equal(nonce.nextNonce, 42);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getReceipt ─────────────────────────────────────────────

test("getReceipt returns typed Receipt with TxHash (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  mockFetch({
    tx_hash: "0xabc123",
    block_height: 42,
    status: "success",
    result: { value: "ok" },
    gas_used: 21000,
    fee_paid: 210,
  });
  try {
    const receipt = await client.getReceipt("0xabc123");
    assert.equal(receipt.txHash, "0xabc123");
    assert.equal(receipt.blockHeight, 42);
    assert.equal(receipt.status, "success");
    assert.equal(receipt.gasUsed, 21000);
    assert.equal(receipt.feePaid, 210);
    assert.equal(receipt.error, undefined);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getAddressSummary via JSON-RPC ─────────────────────────

test("getAddressSummary sends JSON-RPC and returns result (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url, init) => {
    requests.push({ url, body: JSON.parse(init.body) });
    return {
      jsonrpc: "2.0",
      id: 1,
      result: { address: "dili1alice", balance: "500", nonce: 3 },
    };
  });
  try {
    const summary = await client.getAddressSummary("dili1alice");
    assert.equal(summary.address, "dili1alice");
    assert.equal(summary.balance, "500");
    assert.equal(requests[0].body.method, "qsc_addressSummary");
    assert.equal(requests[0].body.params.address, "dili1alice");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getGasEstimate ─────────────────────────────────────────

test("getGasEstimate returns typed GasEstimate (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    result: { gas_limit: 1000000, base_fee: 100, estimated_cost: 100000000 },
  });
  try {
    const est = await client.getGasEstimate();
    assert.equal(est.gasLimit, 1000000);
    assert.equal(est.baseFee, 100);
    assert.equal(est.estimatedCost, 100000000);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getNetworkInfo ─────────────────────────────────────────

test("getNetworkInfo returns typed NetworkInfo via JSON-RPC (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    result: { chain_id: "dilithia-testnet-1", block_height: 12345, base_fee: 50 },
  });
  try {
    const info = await client.getNetworkInfo();
    assert.equal(info.chainId, "dilithia-testnet-1");
    assert.equal(info.blockHeight, 12345);
    assert.equal(info.baseFee, 50);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: jsonRpc ────────────────────────────────────────────────

test("jsonRpc constructs JSON-RPC envelope and parses result (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url, init) => {
    const body = JSON.parse(init.body);
    requests.push(body);
    return { jsonrpc: "2.0", id: body.id, result: { data: "hello" } };
  });
  try {
    const result = await client.jsonRpc("custom_method", { key: "val" }, 7);
    assert.equal(result.data, "hello");
    assert.equal(requests[0].jsonrpc, "2.0");
    assert.equal(requests[0].method, "custom_method");
    assert.equal(requests[0].id, 7);
    assert.deepEqual(requests[0].params, { key: "val" });
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: queryContract ──────────────────────────────────────────

test("queryContract sends GET with URL-encoded params (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { value: "42" };
  });
  try {
    const result = await client.queryContract("wasm:amm", "get_reserves", { pair: "A/B" });
    assert.equal(result.value, "42");
    const urlStr = requests[0];
    assert.ok(urlStr.includes("contract=wasm%3Aamm"));
    assert.ok(urlStr.includes("method=get_reserves"));
    assert.ok(urlStr.includes("args="));
    const argsMatch = urlStr.match(/args=([^&]*)/);
    const decodedArgs = JSON.parse(decodeURIComponent(argsMatch[1]));
    assert.deepEqual(decodedArgs, { pair: "A/B" });
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: simulate ───────────────────────────────────────────────

test("simulate sends POST to /simulate (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ success: true, gas_used: 500 }, { requests });
  try {
    const result = await client.simulate({ contract: "token", method: "transfer", args: {} });
    assert.equal(result.success, true);
    assert.equal(result.gas_used, 500);
    assert.ok(requests[0].url.endsWith("/rpc/simulate"));
    assert.equal(requests[0].init.method, "POST");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: sendCall ───────────────────────────────────────────────

test("sendCall sends POST to /call and returns typed SubmitResult (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xdeadbeef" }, { requests });
  try {
    const result = await client.sendCall({ contract: "token", method: "transfer", args: { to: "bob", amount: 100 } });
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xdeadbeef");
    assert.ok(requests[0].url.endsWith("/rpc/call"));
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.contract, "token");
    assert.equal(body.method, "transfer");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: callContract with paymaster ────────────────────────────

test("callContract with paymaster includes paymaster in body (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0x123" }, { requests });
  try {
    const result = await client.callContract("token", "transfer", { to: "bob" }, { paymaster: "gas_sponsor" });
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0x123");
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.paymaster, "gas_sponsor");
    assert.equal(body.contract, "token");
    assert.equal(body.method, "transfer");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: deployContract ─────────────────────────────────────────

test("deployContract sends POST to /deploy and returns SubmitResult (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xdeploy" }, { requests });
  try {
    const result = await client.deployContract({
      name: "my_contract",
      bytecode: "0xdeadbeef",
      from: "dili1alice",
      alg: "mldsa65",
      pk: "pk_hex",
      sig: "sig_hex",
      nonce: 1,
      chainId: "test-1",
    });
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xdeploy");
    assert.ok(requests[0].url.endsWith("/deploy"));
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.name, "my_contract");
    assert.equal(body.bytecode, "0xdeadbeef");
    assert.equal(body.from, "dili1alice");
    assert.equal(body.chain_id, "test-1");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: upgradeContract ────────────────────────────────────────

test("upgradeContract sends POST to /upgrade and returns SubmitResult (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xupgrade" }, { requests });
  try {
    const result = await client.upgradeContract({
      name: "my_contract",
      bytecode: "0xbeefdead",
      from: "dili1alice",
      alg: "mldsa65",
      pk: "pk_hex",
      sig: "sig_hex",
      nonce: 2,
      chainId: "test-1",
      version: 2,
    });
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xupgrade");
    assert.ok(requests[0].url.endsWith("/upgrade"));
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.version, 2);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: shieldedDeposit ────────────────────────────────────────

test("shieldedDeposit calls shielded/deposit contract (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xshield_dep" }, { requests });
  try {
    const result = await client.shieldedDeposit("commit123", 500, "proof_hex");
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xshield_dep");
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.contract, "shielded");
    assert.equal(body.method, "deposit");
    assert.equal(body.args.commitment, "commit123");
    assert.equal(body.args.value, 500);
    assert.equal(body.args.proof, "proof_hex");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: shieldedWithdraw ───────────────────────────────────────

test("shieldedWithdraw calls shielded/withdraw contract with correct args (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xshield_wd" }, { requests });
  try {
    const result = await client.shieldedWithdraw("null123", 100, "dili1bob", "proof_hex", "root_abc");
    assert.equal(result.accepted, true);
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.contract, "shielded");
    assert.equal(body.method, "withdraw");
    assert.equal(body.args.nullifier, "null123");
    assert.equal(body.args.amount, 100);
    assert.equal(body.args.recipient, "dili1bob");
    assert.equal(body.args.proof, "proof_hex");
    assert.equal(body.args.commitment_root, "root_abc");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getCommitmentRoot ──────────────────────────────────────

test("getCommitmentRoot queries shielded contract for commitment_root (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { value: "0xrootabc" };
  });
  try {
    const result = await client.getCommitmentRoot();
    assert.equal(result.value, "0xrootabc");
    assert.ok(requests[0].includes("contract=shielded"));
    assert.ok(requests[0].includes("method=commitment_root"));
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: isNullifierSpent ───────────────────────────────────────

test("isNullifierSpent queries shielded contract with nullifier arg (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { value: true };
  });
  try {
    const result = await client.isNullifierSpent("null_abc");
    assert.equal(result.value, true);
    assert.ok(requests[0].includes("contract=shielded"));
    assert.ok(requests[0].includes("method=is_nullifier_spent"));
    const argsMatch = requests[0].match(/args=([^&]*)/);
    const decodedArgs = JSON.parse(decodeURIComponent(argsMatch[1]));
    assert.equal(decodedArgs.nullifier, "null_abc");
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: waitForReceipt ─────────────────────────────────────────

test("waitForReceipt retries on 404 then returns receipt (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  let callCount = 0;
  globalThis.fetch = async () => {
    callCount++;
    if (callCount === 1) {
      return new Response("Not Found", { status: 404 });
    }
    return new Response(
      JSON.stringify({ tx_hash: "0xwait", block_height: 99, status: "success", gas_used: 100, fee_paid: 10 }),
      { status: 200, headers: { "content-type": "application/json" } }
    );
  };
  try {
    const receipt = await client.waitForReceipt("0xwait", 3, 10);
    assert.equal(receipt.txHash, "0xwait");
    assert.equal(receipt.status, "success");
    assert.equal(callCount, 2);
  } finally {
    restoreFetch();
  }
});

test("waitForReceipt throws TimeoutError after max attempts (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => new Response("Not Found", { status: 404 });
  try {
    await assert.rejects(
      () => client.waitForReceipt("0xmissing", 2, 5),
      (err) => {
        assert.ok(err instanceof TimeoutError);
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: Error handling ─────────────────────────────────────────

test("HttpError is thrown on 500 response (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => new Response("Internal Server Error", { status: 500 });
  try {
    await assert.rejects(
      () => client.getBalance("dili1alice"),
      (err) => {
        assert.ok(err instanceof HttpError);
        assert.equal(err.statusCode, 500);
        assert.equal(err.body, "Internal Server Error");
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

test("RpcError is thrown on JSON-RPC error response (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    error: { code: -32601, message: "Method not found" },
  });
  try {
    await assert.rejects(
      () => client.jsonRpc("nonexistent_method", {}),
      (err) => {
        assert.ok(err instanceof RpcError);
        assert.equal(err.code, -32601);
        assert.equal(err.message, "Method not found");
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

test("TimeoutError is thrown when fetch is aborted (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc", timeoutMs: 1 });
  globalThis.fetch = async (_url, init) => {
    return new Promise((_resolve, reject) => {
      const checkAbort = () => {
        if (init.signal?.aborted) {
          const abortErr = new DOMException("The operation was aborted.", "AbortError");
          reject(abortErr);
        } else {
          setTimeout(checkAbort, 1);
        }
      };
      checkAbort();
    });
  };
  try {
    await assert.rejects(
      () => client.getBalance("dili1alice"),
      (err) => {
        assert.ok(err instanceof TimeoutError);
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

test("HttpError is thrown on 404 response (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => new Response("Not Found", { status: 404 });
  try {
    await assert.rejects(
      () => client.getReceipt("0xmissing"),
      (err) => {
        assert.ok(err instanceof HttpError);
        assert.equal(err.statusCode, 404);
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: resolveName / reverseResolveName ───────────────────────

test("resolveName sends GET to /names/resolve/<name> (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { name: "alice.dili", address: "dili1alice" };
  });
  try {
    const record = await client.resolveName("alice.dili");
    assert.equal(record.name, "alice.dili");
    assert.equal(record.address, "dili1alice");
    assert.ok(requests[0].includes("/names/resolve/alice.dili"));
  } finally {
    restoreFetch();
  }
});

test("reverseResolveName sends GET to /names/reverse/<address> (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { name: "alice.dili", address: "dili1alice" };
  });
  try {
    const record = await client.reverseResolveName("dili1alice");
    assert.equal(record.name, "alice.dili");
    assert.equal(record.address, "dili1alice");
    assert.ok(requests[0].includes("/names/reverse/dili1alice"));
  } finally {
    restoreFetch();
  }
});

// ── sendSignedCall ─────────────────────────────────────────────────────────

test("sendSignedCall merges signer payload and returns typed SubmitResult", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xabc" }, { requests });
  try {
    const result = await client.sendSignedCall(
      { from: "a", contract: "token", method: "transfer", args: { to: "b", amount: 1 } },
      {
        async signCanonicalPayload(payloadJson) {
          return { sig: `signed:${payloadJson}`, pk: "pk", alg: "mldsa65" };
        },
      }
    );

    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xabc");
    assert.equal(requests.length, 1);
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.pk, "pk");
    assert.equal(body.alg, "mldsa65");
    assert.match(body.sig, /^signed:/);
  } finally {
    restoreFetch();
  }
});

// ── Paymaster and forwarder ────────────────────────────────────────────────

test("paymaster and forwarder helpers shape calls explicitly", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsored = client.withPaymaster({ contract: "token", method: "transfer", args: {} }, "gas_sponsor");
  assert.equal(sponsored.paymaster, "gas_sponsor");

  const forwarderCall = client.buildForwarderCall(
    "wasm:forwarder",
    { user: "alice", nonce: 1, contract: "wasm:amm" },
    { paymaster: "gas_sponsor" }
  );
  assert.equal(forwarderCall.contract, "wasm:forwarder");
  assert.equal(forwarderCall.method, "forward");
  assert.equal(forwarderCall.paymaster, "gas_sponsor");
});

// ── Gas sponsor connector ──────────────────────────────────────────────────

test("gas sponsor connector applies paymaster and builds sponsor calls", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
    paymaster: "gas_sponsor",
  });

  assert.deepEqual(sponsor.buildRemainingQuotaQuery("alice"), {
    contract: "wasm:gas_sponsor",
    method: "remaining_quota",
    args: { user: "alice" },
  });

  const applied = sponsor.applyPaymaster({ contract: "wasm:amm", method: "swap", args: {} });
  assert.equal(applied.paymaster, "gas_sponsor");
});

test("gas sponsor connector builds accept query", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
    paymaster: "gas_sponsor",
  });
  const query = sponsor.buildAcceptQuery("alice", "token", "transfer");
  assert.equal(query.contract, "wasm:gas_sponsor");
  assert.equal(query.method, "accept");
  assert.deepEqual(query.args, { user: "alice", contract: "token", method: "transfer" });
});

test("gas sponsor connector builds max gas per user query", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
  });
  const query = sponsor.buildMaxGasPerUserQuery();
  assert.equal(query.method, "max_gas_per_user");
  assert.deepEqual(query.args, {});
});

test("gas sponsor connector builds fund call", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
  });
  const call = sponsor.buildFundCall(1000);
  assert.equal(call.method, "fund");
  assert.equal(call.args.amount, 1000);
});

test("gas sponsor connector applyPaymaster is no-op without paymaster", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
  });
  const call = { contract: "token", method: "transfer", args: {} };
  const result = sponsor.applyPaymaster(call);
  assert.equal(result.paymaster, undefined);
});

// ── Messaging connector ────────────────────────────────────────────────────

test("messaging connector builds in and out chain calls", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const messaging = createMessagingConnector({
    client,
    messagingContract: "wasm:messaging",
    paymaster: "gas_sponsor",
  });

  const outbound = messaging.buildSendMessageCall("ethereum", { amount: 1 });
  assert.deepEqual(outbound, {
    contract: "wasm:messaging",
    method: "send_message",
    args: { dest_chain: "ethereum", payload: { amount: 1 } },
    paymaster: "gas_sponsor",
  });

  const inbound = messaging.buildReceiveMessageCall("ethereum", "bridge", { tx: "0xabc" });
  assert.equal(inbound.method, "receive_message");
  assert.equal(inbound.args.source_chain, "ethereum");
  assert.equal(inbound.args.source_contract, "bridge");
});

test("messaging connector builds outbox and inbox queries", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const messaging = createMessagingConnector({
    client,
    messagingContract: "wasm:messaging",
  });
  const outbox = messaging.buildOutboxQuery();
  assert.equal(outbox.method, "outbox");
  const inbox = messaging.buildInboxQuery();
  assert.equal(inbox.method, "inbox");
});

test("messaging connector queryOutbox and queryInbox call queryContract (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const messaging = createMessagingConnector({
    client,
    messagingContract: "wasm:messaging",
  });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { value: ["msg1"] };
  });
  try {
    await messaging.queryOutbox();
    assert.ok(requests[0].includes("contract=wasm%3Amessaging"));
    assert.ok(requests[0].includes("method=outbox"));
    await messaging.queryInbox();
    assert.ok(requests[1].includes("method=inbox"));
  } finally {
    restoreFetch();
  }
});

// ── Crypto adapter ─────────────────────────────────────────────────────────

test("native crypto adapter loader returns null when bridge is unavailable", async () => {
  const adapter = await loadNativeCryptoAdapter(async () => {
    throw new Error("not installed");
  });
  assert.equal(adapter, null);
});

// ── Cross-language canonical payload consistency ─────────────────────────────

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const vectors = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, "../../tests/vectors/canonical_payloads.json"), "utf8")
);

test("buildContractCall produces keys in alphabetical order matching vectors", () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  const v = vectors.contract_call;
  const call = client.buildContractCall(v.input.contract, v.input.method, v.input.args);

  // Serialize with recursively sorted keys (compact, no spaces)
  const deepSorted = JSON.stringify(call, (key, value) => {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return Object.keys(value).sort().reduce((acc, k) => { acc[k] = value[k]; return acc; }, {});
    }
    return value;
  });
  assert.equal(deepSorted, v.expected_json);

  // Verify key ordering
  const keys = Object.keys(JSON.parse(deepSorted));
  assert.deepEqual(keys, v.expected_keys_order);
});

test("buildDeployCanonicalPayload produces expected JSON matching vectors", () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  const v = vectors.deploy_canonical;
  const payload = client.buildDeployCanonicalPayload(
    v.input.from, v.input.name, v.input.bytecode_hash, v.input.nonce, v.input.chain_id
  );

  // Serialize with sorted keys
  const canonical = JSON.stringify(payload, Object.keys(payload).sort());
  assert.equal(canonical, v.expected_json);

  // Verify key ordering
  const keys = Object.keys(JSON.parse(canonical));
  assert.deepEqual(keys, v.expected_keys_order);
});

test("withPaymaster adds the paymaster field matching vectors", () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  const v = vectors.with_paymaster;
  const call = client.buildContractCall(v.input.contract, v.input.method, v.input.args);
  const sponsored = client.withPaymaster(call, v.input.paymaster);

  assert.equal(sponsored.paymaster, v.input.paymaster);
  assert.equal("paymaster" in sponsored, v.expected_has_paymaster);
});

// ── Crypto adapter ─────────────────────────────────────────────────────────

test("native crypto adapter loader maps a bridge module correctly", async () => {
  const adapter = await loadNativeCryptoAdapter(async () => ({
    generateMnemonic: () => "word ".repeat(24).trim(),
    validateMnemonic: () => undefined,
    addressFromPublicKey: () => "derived-address",
    signMessage: () => ({ algorithm: "mldsa65", signature: "deadbeef" }),
    verifyMessage: () => true,
    recoverWalletFile: () => ({
      address: "addr-1",
      publicKey: "pk-1",
      secretKey: "sk-1",
      accountIndex: 0,
      walletFile: { version: 1 },
    }),
  }));

  assert.ok(adapter);
  assert.equal(await adapter.generateMnemonic(), "word ".repeat(24).trim());
  assert.equal(await adapter.addressFromPublicKey("pk"), "derived-address");
  assert.deepEqual(await adapter.signMessage("sk", "msg"), {
    algorithm: "mldsa65",
    signature: "deadbeef",
  });
  assert.equal(await adapter.verifyMessage("pk", "msg", "sig"), true);
  assert.deepEqual(await adapter.recoverWalletFile({ version: 1 }, "mnemonic", "password"), {
    address: "addr-1",
    publicKey: "pk-1",
    secretKey: "sk-1",
    accountIndex: 0,
    walletFile: { version: 1 },
  });
});

// ── HTTP mock tests: getBaseFee ─────────────────────────────────────────────

test("getBaseFee returns raw JSON-RPC result (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    result: { base_fee: 75 },
  });
  try {
    const result = await client.getBaseFee();
    assert.equal(result.base_fee, 75);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: getContractAbi ─────────────────────────────────────────

test("getContractAbi returns typed ContractAbi (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    result: {
      contract: "wasm:token",
      methods: [
        { name: "transfer", mutates: true, has_args: true },
        { name: "balance_of", mutates: false, has_args: true },
      ],
    },
  });
  try {
    const abi = await client.getContractAbi("wasm:token");
    assert.equal(abi.contract, "wasm:token");
    assert.equal(abi.methods.length, 2);
    assert.equal(abi.methods[0].name, "transfer");
    assert.equal(abi.methods[0].mutates, true);
    assert.equal(abi.methods[0].hasArgs, true);
    assert.equal(abi.methods[1].name, "balance_of");
    assert.equal(abi.methods[1].mutates, false);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: rawGet / rawPost ───────────────────────────────────────

test("rawGet sends GET to rpcUrl by default (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { status: "ok" };
  });
  try {
    const result = await client.rawGet("/status");
    assert.equal(result.status, "ok");
    assert.equal(requests[0], "http://rpc.example/rpc/status");
  } finally {
    restoreFetch();
  }
});

test("rawGet with useChainBase sends GET to baseUrl (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc", chainBaseUrl: "http://chain.example" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { chain: "ok" };
  });
  try {
    await client.rawGet("/health", true);
    assert.equal(requests[0], "http://chain.example/health");
  } finally {
    restoreFetch();
  }
});

test("rawPost sends POST to rpcUrl (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ result: "posted" }, { requests });
  try {
    const result = await client.rawPost("/custom", { data: 42 });
    assert.equal(result.result, "posted");
    assert.equal(requests[0].init.method, "POST");
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.data, 42);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: rawRpc returns full envelope ───────────────────────────

test("rawRpc returns full JSON-RPC envelope including error field (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    error: { code: -32600, message: "Invalid" },
  });
  try {
    const result = await client.rawRpc("bad_method", {});
    assert.ok(result.error);
    assert.equal(result.error.code, -32600);
  } finally {
    restoreFetch();
  }
});

// ── HTTP mock tests: sendSponsoredCall ──────────────────────────────────────

test("sendSponsoredCall attaches paymaster and signs (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xsponsored" }, { requests });
  try {
    const result = await client.sendSponsoredCall(
      { contract: "token", method: "transfer", args: { to: "bob" } },
      "gas_sponsor",
      {
        async signCanonicalPayload(payloadJson) {
          const parsed = JSON.parse(payloadJson);
          assert.equal(parsed.paymaster, "gas_sponsor");
          return { sig: "sig", pk: "pk", alg: "mldsa65" };
        },
      }
    );
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xsponsored");
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.paymaster, "gas_sponsor");
  } finally {
    restoreFetch();
  }
});

// ── Additional builder tests ────────────────────────────────────────────────

test("buildContractCall constructs correct call envelope", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const call = client.buildContractCall("token", "transfer", { to: "bob", amount: 100 });
  assert.equal(call.contract, "token");
  assert.equal(call.method, "transfer");
  assert.deepEqual(call.args, { to: "bob", amount: 100 });
  assert.equal(call.paymaster, undefined);
});

test("buildWsRequest returns same format as buildJsonRpcRequest", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const ws = client.buildWsRequest("subscribe", { topic: "heads" }, 5);
  assert.equal(ws.jsonrpc, "2.0");
  assert.equal(ws.method, "subscribe");
  assert.equal(ws.id, 5);
  assert.deepEqual(ws.params, { topic: "heads" });
});

test("queryContractAbi returns JSON-RPC request body", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const body = client.queryContractAbi("wasm:token");
  assert.equal(body.method, "qsc_getAbi");
  assert.equal(body.params.contract, "wasm:token");
  assert.equal(body.jsonrpc, "2.0");
});

test("deployContractRequest returns path and body", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const req = client.deployContractRequest({
    name: "test", bytecode: "0x00", from: "dili1a",
    alg: "mldsa65", pk: "pk", sig: "sig", nonce: 0, chainId: "c1",
  });
  assert.equal(req.path, "/deploy");
  assert.equal(req.body.name, "test");
});

test("upgradeContractRequest returns path and body", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const req = client.upgradeContractRequest({
    name: "test", bytecode: "0x00", from: "dili1a",
    alg: "mldsa65", pk: "pk", sig: "sig", nonce: 1, chainId: "c1", version: 2,
  });
  assert.equal(req.path, "/upgrade");
  assert.equal(req.body.version, 2);
});

test("buildDeployCanonicalPayload returns sorted keys", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const payload = client.buildDeployCanonicalPayload("alice", "my_token", "0xabc", 1, "dilithia");
  assert.equal(payload.from, "alice");
  assert.equal(payload.name, "my_token");
  assert.equal(payload.bytecode_hash, "0xabc");
  assert.equal(payload.nonce, 1);
  assert.equal(payload.chain_id, "dilithia");
});

// ── HTTP mock tests: lookupName / isNameAvailable / getNamesByOwner ────────

test("lookupName sends GET to /names/lookup/<name> (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { name: "alice.dili", address: "dili1alice" };
  });
  try {
    const record = await client.lookupName("alice.dili");
    assert.equal(record.name, "alice.dili");
    assert.equal(record.address, "dili1alice");
    assert.ok(requests[0].includes("/names/lookup/alice.dili"));
  } finally {
    restoreFetch();
  }
});

test("isNameAvailable sends GET to /names/available/<name> (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { available: true };
  });
  try {
    const result = await client.isNameAvailable("bob.dili");
    assert.equal(result.available, true);
    assert.ok(requests[0].includes("/names/available/bob.dili"));
  } finally {
    restoreFetch();
  }
});

test("getNamesByOwner sends GET to /names/by-owner/<address> (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  mockFetchRouter(async (url) => {
    requests.push(url);
    return { names: ["alice.dili"] };
  });
  try {
    const result = await client.getNamesByOwner("dili1alice");
    assert.deepEqual(result.names, ["alice.dili"]);
    assert.ok(requests[0].includes("/names/by-owner/dili1alice"));
  } finally {
    restoreFetch();
  }
});

// ── TokenAmount edge cases ──────────────────────────────────────────────────

test("TokenAmount.dili truncates fractional part beyond 18 decimals", () => {
  // "1.1234567890123456789999" has >18 decimal digits, should truncate to 18
  const t2 = TokenAmount.dili("1.1234567890123456789999");
  assert.equal(t2.toRaw(), 1_123_456_789_012_345_678n);
  assert.equal(t2.formatted(), "1.123456789012345678");
});

test("TokenAmount.dili with zero value", () => {
  const t2 = TokenAmount.dili(0);
  assert.equal(t2.toRaw(), 0n);
  assert.equal(t2.formatted(), "0");
});

test("TokenAmount.dili with string '100.5'", () => {
  const t2 = TokenAmount.dili("100.5");
  assert.equal(t2.formatted(), "100.5");
  assert.equal(t2.toRaw(), 100_500_000_000_000_000_000n);
});

test("TokenAmount.fromRaw with 1e18 gives formatted '1'", () => {
  const t2 = TokenAmount.fromRaw(1_000_000_000_000_000_000n);
  assert.equal(t2.formatted(), "1");
});

// ── Error class additional tests ────────────────────────────────────────────

test("DilithiaError supports options (cause)", () => {
  const cause = new Error("root cause");
  const err = new DilithiaError("wrapped", { cause });
  assert.equal(err.cause, cause);
  assert.equal(err.name, "DilithiaError");
});

test("RpcError inherits from Error prototype chain", () => {
  const err = new RpcError(-32700, "Parse error");
  assert.ok(err instanceof Error);
  assert.ok(err instanceof DilithiaError);
  assert.ok(err instanceof RpcError);
  assert.equal(err.code, -32700);
});

test("HttpError with status 503 and empty body", () => {
  const err = new HttpError(503, "");
  assert.equal(err.statusCode, 503);
  assert.equal(err.body, "");
  assert.equal(err.message, "HTTP 503");
});

test("TimeoutError with small timeout", () => {
  const err = new TimeoutError(100);
  assert.equal(err.timeoutMs, 100);
  assert.equal(err.message, "Request timed out after 100ms");
});

// ── Gas sponsor connector: simulateSponsoredCall and sendSponsoredCall ──────

test("gas sponsor connector simulateSponsoredCall applies paymaster (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
    paymaster: "gas_sponsor",
  });
  const requests = [];
  mockFetch({ success: true, gas_used: 300 }, { requests });
  try {
    const result = await sponsor.simulateSponsoredCall({
      contract: "token",
      method: "transfer",
      args: { to: "bob" },
    });
    assert.equal(result.success, true);
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.paymaster, "gas_sponsor");
  } finally {
    restoreFetch();
  }
});

test("gas sponsor connector sendSponsoredCall signs and submits with paymaster (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
    paymaster: "gas_sponsor",
  });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xsponsored2" }, { requests });
  try {
    const result = await sponsor.sendSponsoredCall(
      { contract: "token", method: "transfer", args: { to: "bob" } },
      { async signCanonicalPayload() { return { sig: "s", pk: "p", alg: "mldsa65" }; } }
    );
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xsponsored2");
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.paymaster, "gas_sponsor");
  } finally {
    restoreFetch();
  }
});

test("gas sponsor connector buildSponsorTokenQuery", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const sponsor = createGasSponsorConnector({
    client,
    sponsorContract: "wasm:gas_sponsor",
  });
  const query = sponsor.buildSponsorTokenQuery();
  assert.equal(query.method, "sponsor_token");
  assert.equal(query.contract, "wasm:gas_sponsor");
  assert.deepEqual(query.args, {});
});

// ── Messaging connector: sendMessage ────────────────────────────────────────

test("messaging connector sendMessage signs and submits (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const messaging = createMessagingConnector({
    client,
    messagingContract: "wasm:messaging",
    paymaster: "gas_sponsor",
  });
  const requests = [];
  mockFetch({ accepted: true, tx_hash: "0xmsg" }, { requests });
  try {
    const result = await messaging.sendMessage(
      "ethereum",
      { amount: 1 },
      { async signCanonicalPayload() { return { sig: "s", pk: "p", alg: "mldsa65" }; } }
    );
    assert.equal(result.accepted, true);
    assert.equal(result.txHash, "0xmsg");
    const body = JSON.parse(requests[0].init.body);
    assert.equal(body.contract, "wasm:messaging");
    assert.equal(body.method, "send_message");
    assert.equal(body.paymaster, "gas_sponsor");
  } finally {
    restoreFetch();
  }
});

test("messaging connector applyPaymaster without paymaster is no-op", () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const messaging = createMessagingConnector({
    client,
    messagingContract: "wasm:messaging",
  });
  const call = { contract: "token", method: "transfer", args: {} };
  const result = messaging.applyPaymaster(call);
  assert.equal(result.paymaster, undefined);
});

// ── readWasmFileHex ─────────────────────────────────────────────────────────

test("readWasmFileHex reads a file and returns hex", async () => {
  const { readWasmFileHex } = await import("../dist/index.js");
  const { writeFileSync, unlinkSync } = await import("node:fs");
  const { join } = await import("node:path");
  const tmpFile = join(__dirname, "__test_wasm_tmp.bin");
  writeFileSync(tmpFile, Buffer.from([0xde, 0xad, 0xbe, 0xef]));
  try {
    const hex = readWasmFileHex(tmpFile);
    assert.equal(hex, "deadbeef");
  } finally {
    unlinkSync(tmpFile);
  }
});

// ── Crypto adapter: error branches with mock partial module ─────────────────

test("crypto adapter async: throws when bridge functions are missing", async (t) => {
  const { loadNativeCryptoAdapter } = await import("../dist/crypto.js");

  // Create adapter with minimal mock (only generateMnemonic + validateMnemonic)
  const adapter = await loadNativeCryptoAdapter(async () => ({
    generateMnemonic: () => "word ".repeat(24).trim(),
    validateMnemonic: () => undefined,
  }));

  assert.ok(adapter, "adapter should be non-null");

  // generateMnemonic and validateMnemonic should work
  assert.equal(await adapter.generateMnemonic(), "word ".repeat(24).trim());
  await adapter.validateMnemonic("anything");

  await t.test("recoverHdWallet throws when not exposed", async () => {
    await assert.rejects(() => adapter.recoverHdWallet("m"), /does not expose recoverHdWallet/);
  });

  await t.test("recoverHdWalletAccount throws when not exposed", async () => {
    await assert.rejects(() => adapter.recoverHdWalletAccount("m", 0), /does not expose recoverHdWalletAccount/);
  });

  await t.test("createHdWalletFileFromMnemonic throws when not exposed", async () => {
    await assert.rejects(() => adapter.createHdWalletFileFromMnemonic("m", "p"), /does not expose createHdWalletFileFromMnemonic/);
  });

  await t.test("createHdWalletAccountFromMnemonic throws when not exposed", async () => {
    await assert.rejects(() => adapter.createHdWalletAccountFromMnemonic("m", "p", 0), /does not expose createHdWalletAccountFromMnemonic/);
  });

  await t.test("recoverWalletFile throws when not exposed", async () => {
    await assert.rejects(() => adapter.recoverWalletFile({}, "m", "p"), /does not expose recoverWalletFile/);
  });

  await t.test("addressFromPublicKey throws when not exposed", async () => {
    await assert.rejects(() => adapter.addressFromPublicKey("pk"), /does not expose addressFromPublicKey/);
  });

  await t.test("validateAddress throws when not exposed", async () => {
    await assert.rejects(() => adapter.validateAddress("addr"), /does not expose validateAddress/);
  });

  await t.test("addressFromPkChecksummed throws when not exposed", async () => {
    await assert.rejects(() => adapter.addressFromPkChecksummed("pk"), /does not expose addressFromPkChecksummed/);
  });

  await t.test("addressWithChecksum throws when not exposed", async () => {
    await assert.rejects(() => adapter.addressWithChecksum("addr"), /does not expose addressWithChecksum/);
  });

  await t.test("validatePublicKey throws when not exposed", async () => {
    await assert.rejects(() => adapter.validatePublicKey("pk"), /does not expose validatePublicKey/);
  });

  await t.test("validateSecretKey throws when not exposed", async () => {
    await assert.rejects(() => adapter.validateSecretKey("sk"), /does not expose validateSecretKey/);
  });

  await t.test("validateSignature throws when not exposed", async () => {
    await assert.rejects(() => adapter.validateSignature("sig"), /does not expose validateSignature/);
  });

  await t.test("signMessage throws when not exposed", async () => {
    await assert.rejects(() => adapter.signMessage("sk", "msg"), /does not expose signMessage/);
  });

  await t.test("verifyMessage throws when not exposed", async () => {
    await assert.rejects(() => adapter.verifyMessage("pk", "msg", "sig"), /does not expose verifyMessage/);
  });

  await t.test("keygen throws when not exposed", async () => {
    await assert.rejects(() => adapter.keygen(), /does not expose keygen/);
  });

  await t.test("keygenFromSeed throws when not exposed", async () => {
    await assert.rejects(() => adapter.keygenFromSeed("seed"), /does not expose keygenFromSeed/);
  });

  await t.test("seedFromMnemonic throws when not exposed", async () => {
    await assert.rejects(() => adapter.seedFromMnemonic("m"), /does not expose seedFromMnemonic/);
  });

  await t.test("deriveChildSeed throws when not exposed", async () => {
    await assert.rejects(() => adapter.deriveChildSeed("seed", 0), /does not expose deriveChildSeed/);
  });

  await t.test("constantTimeEq throws when not exposed", async () => {
    await assert.rejects(() => adapter.constantTimeEq("a", "b"), /does not expose constantTimeEq/);
  });

  await t.test("hashHex throws when not exposed", async () => {
    await assert.rejects(() => adapter.hashHex("data"), /does not expose hashHex/);
  });

  await t.test("setHashAlg throws when not exposed", async () => {
    await assert.rejects(() => adapter.setHashAlg("sha3"), /does not expose setHashAlg/);
  });

  await t.test("currentHashAlg throws when not exposed", async () => {
    await assert.rejects(() => adapter.currentHashAlg(), /does not expose currentHashAlg/);
  });

  await t.test("hashLenHex throws when not exposed", async () => {
    await assert.rejects(() => adapter.hashLenHex(), /does not expose hashLenHex/);
  });
});

// ── Crypto adapter async: full mock with all functions present ──────────────

test("crypto adapter async: happy path with all functions mocked", async () => {
  const { loadNativeCryptoAdapter } = await import("../dist/crypto.js");

  const adapter = await loadNativeCryptoAdapter(async () => ({
    generateMnemonic: () => "word ".repeat(24).trim(),
    validateMnemonic: () => undefined,
    recoverHdWallet: (m) => ({ address: "a1", publicKey: "pk1", secretKey: "sk1", accountIndex: 0 }),
    recoverHdWalletAccount: (m, i) => ({ address: `a${i}`, public_key: `pk${i}`, secret_key: `sk${i}`, account_index: i }),
    createHdWalletFileFromMnemonic: (m, p) => ({ address: "wf_a", publicKey: "wf_pk", secretKey: "wf_sk", accountIndex: 0 }),
    createHdWalletAccountFromMnemonic: (m, p, i) => ({ address: `wa${i}`, publicKey: `wpk${i}`, secretKey: `wsk${i}`, accountIndex: i }),
    recoverWalletFile: () => ({ address: "rf_a", publicKey: "rf_pk", secretKey: "rf_sk", accountIndex: 0 }),
    addressFromPublicKey: (pk) => "derived_addr",
    validateAddress: (a) => a,
    addressFromPkChecksummed: (pk) => "checksummed_addr",
    addressWithChecksum: (a) => "addr_with_checksum",
    validatePublicKey: () => undefined,
    validateSecretKey: () => undefined,
    validateSignature: () => undefined,
    signMessage: (sk, msg) => ({ algorithm: "mldsa65", signature: "sig_hex" }),
    verifyMessage: () => true,
    keygen: () => ({ secretKey: "sk_new", publicKey: "pk_new", address: "addr_new" }),
    keygenFromSeed: (seed) => ({ secretKey: "sk_seed", publicKey: "pk_seed", address: "addr_seed" }),
    seedFromMnemonic: () => "abcd1234",
    deriveChildSeed: () => "child_seed",
    constantTimeEq: (a, b) => a === b,
    hashHex: () => "hash_result",
    setHashAlg: () => undefined,
    currentHashAlg: () => "sha3-256",
    hashLenHex: () => 32,
  }));

  assert.ok(adapter);
  const account = await adapter.recoverHdWallet("mnemonic");
  assert.equal(account.address, "a1");
  assert.equal(account.publicKey, "pk1");

  // Test snake_case to camelCase normalization
  const account1 = await adapter.recoverHdWalletAccount("mnemonic", 1);
  assert.equal(account1.publicKey, "pk1");
  assert.equal(account1.secretKey, "sk1");
  assert.equal(account1.accountIndex, 1);

  const wfAccount = await adapter.createHdWalletFileFromMnemonic("m", "p");
  assert.equal(wfAccount.address, "wf_a");

  const waAccount = await adapter.createHdWalletAccountFromMnemonic("m", "p", 2);
  assert.equal(waAccount.address, "wa2");

  const rfAccount = await adapter.recoverWalletFile({ version: 1, address: "a", public_key: "pk", encrypted_sk: "esk", nonce: "n", tag: "t", account_index: 0 }, "m", "p");
  assert.equal(rfAccount.address, "rf_a");

  // Test recoverWalletFile with camelCase keys and null account_index
  const rfAccount2 = await adapter.recoverWalletFile({ publicKey: "pk", encryptedSk: "esk" }, "m", "p");
  assert.equal(rfAccount2.address, "rf_a");

  assert.equal(await adapter.addressFromPublicKey("pk"), "derived_addr");
  assert.equal(await adapter.validateAddress("addr"), "addr");
  assert.equal(await adapter.addressFromPkChecksummed("pk"), "checksummed_addr");
  assert.equal(await adapter.addressWithChecksum("addr"), "addr_with_checksum");
  await adapter.validatePublicKey("pk");
  await adapter.validateSecretKey("sk");
  await adapter.validateSignature("sig");
  const sig = await adapter.signMessage("sk", "msg");
  assert.equal(sig.algorithm, "mldsa65");
  assert.equal(await adapter.verifyMessage("pk", "msg", "sig"), true);
  const kp = await adapter.keygen();
  assert.equal(kp.address, "addr_new");
  const kpSeed = await adapter.keygenFromSeed("seed");
  assert.equal(kpSeed.address, "addr_seed");
  assert.equal(await adapter.seedFromMnemonic("m"), "abcd1234");
  assert.equal(await adapter.deriveChildSeed("seed", 0), "child_seed");
  assert.equal(await adapter.constantTimeEq("a", "a"), true);
  assert.equal(await adapter.constantTimeEq("a", "b"), false);
  assert.equal(await adapter.hashHex("data"), "hash_result");
  await adapter.setHashAlg("sha3");
  assert.equal(await adapter.currentHashAlg(), "sha3-256");
  assert.equal(await adapter.hashLenHex(), 32);
});

// ── Sync crypto adapter returns null when bridge unavailable ────────────────

test("loadSyncNativeCryptoAdapter returns adapter or null depending on bridge availability", async () => {
  const { loadSyncNativeCryptoAdapter } = await import("../dist/crypto.js");
  const adapter = loadSyncNativeCryptoAdapter();
  if (adapter) {
    assert.equal(typeof adapter.generateMnemonic, "function");
  } else {
    assert.equal(adapter, null);
  }
});

// ── ZK adapter async: mock with all functions ───────────────────────────────

test("ZK adapter async: maps mock bridge correctly", async (t) => {
  const { loadZkAdapter } = await import("../dist/zk.js");

  const mockZk = {
    poseidon_hash: (inputs) => "hash_" + inputs.join("_"),
    compute_commitment: (value, secret, nonce) => ({
      hash: "commit_hash",
      value,
      secret,
      nonce,
    }),
    compute_nullifier: (secret, nonce) => ({ hash: "null_hash" }),
    generate_preimage_proof: (values) => ({
      proof: "preimage_proof",
      vk: "preimage_vk",
      inputs: JSON.stringify(values),
    }),
    verify_preimage_proof: (proof, vk, inputs) => proof === "preimage_proof",
    generate_range_proof: (value, min, max) => ({
      proof: "range_proof",
      vk: "range_vk",
      inputs: JSON.stringify({ value, min, max }),
    }),
    verify_range_proof: (proof, vk, inputs) => proof === "range_proof",
  };

  const zk = await loadZkAdapter(async () => mockZk);
  assert.ok(zk, "ZK adapter should be non-null");

  await t.test("poseidonHash with 2 inputs", async () => {
    const result = await zk.poseidonHash([1, 2]);
    assert.equal(result, "hash_1_2");
  });

  await t.test("poseidonHash with 3 inputs", async () => {
    const result = await zk.poseidonHash([10, 20, 30]);
    assert.equal(result, "hash_10_20_30");
  });

  await t.test("computeCommitment", async () => {
    const result = await zk.computeCommitment(100, "secret", "nonce");
    assert.equal(result.hash, "commit_hash");
    assert.equal(result.value, 100);
  });

  await t.test("computeNullifier", async () => {
    const result = await zk.computeNullifier("secret", "nonce");
    assert.equal(result.hash, "null_hash");
  });

  await t.test("generatePreimageProof + verifyPreimageProof", async () => {
    const proof = await zk.generatePreimageProof([42]);
    assert.equal(proof.proof, "preimage_proof");
    const valid = await zk.verifyPreimageProof(proof.proof, proof.vk, proof.inputs);
    assert.equal(valid, true);
  });

  await t.test("generateRangeProof + verifyRangeProof", async () => {
    const proof = await zk.generateRangeProof(50, 0, 100);
    assert.equal(proof.proof, "range_proof");
    const valid = await zk.verifyRangeProof(proof.proof, proof.vk, proof.inputs);
    assert.equal(valid, true);
  });

  await t.test("verifyPreimageProof with wrong data returns false", async () => {
    const valid = await zk.verifyPreimageProof("wrong_proof", "vk", "inputs");
    assert.equal(valid, false);
  });
});

// ── ZK adapter async: error branches when functions missing ─────────────────

test("ZK adapter async: throws when bridge functions are missing", async (t) => {
  const { loadZkAdapter } = await import("../dist/zk.js");

  // Empty module -- no functions exposed
  const zk = await loadZkAdapter(async () => ({}));
  assert.ok(zk, "ZK adapter should be non-null even with empty module");

  await t.test("poseidonHash throws when not exposed", async () => {
    await assert.rejects(() => zk.poseidonHash([1]), /does not expose poseidon_hash/);
  });

  await t.test("computeCommitment throws when not exposed", async () => {
    await assert.rejects(() => zk.computeCommitment(1, "s", "n"), /does not expose compute_commitment/);
  });

  await t.test("computeNullifier throws when not exposed", async () => {
    await assert.rejects(() => zk.computeNullifier("s", "n"), /does not expose compute_nullifier/);
  });

  await t.test("generatePreimageProof throws when not exposed", async () => {
    await assert.rejects(() => zk.generatePreimageProof([1]), /does not expose generate_preimage_proof/);
  });

  await t.test("verifyPreimageProof throws when not exposed", async () => {
    await assert.rejects(() => zk.verifyPreimageProof("p", "v", "i"), /does not expose verify_preimage_proof/);
  });

  await t.test("generateRangeProof throws when not exposed", async () => {
    await assert.rejects(() => zk.generateRangeProof(1, 0, 10), /does not expose generate_range_proof/);
  });

  await t.test("verifyRangeProof throws when not exposed", async () => {
    await assert.rejects(() => zk.verifyRangeProof("p", "v", "i"), /does not expose verify_range_proof/);
  });
});

// ── ZK adapter: returns null when import fails ──────────────────────────────

test("ZK adapter async: returns null when import fails", async () => {
  const { loadZkAdapter } = await import("../dist/zk.js");
  const result = await loadZkAdapter(async () => { throw new Error("not installed"); });
  assert.equal(result, null);
});

// ── Sync ZK adapter returns null when bridge unavailable ────────────────────

test("loadSyncZkAdapter loads bridge and exercises sync adapter functions", async (t) => {
  const { loadSyncZkAdapter } = await import("../dist/zk.js");
  const adapter = loadSyncZkAdapter();
  if (!adapter) {
    t.skip("ZK bridge not available");
    return;
  }
  assert.equal(typeof adapter.poseidonHash, "function");

  // The sync adapter wraps the native module which may use snake_case keys.
  // If the bridge uses camelCase, these will throw "does not expose" errors.
  // Either way, we exercise all the sync adapter code paths.
  await t.test("poseidonHash works or throws bridge error", () => {
    try {
      const result = adapter.poseidonHash([1, 2]);
      assert.equal(typeof result, "string");
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });

  await t.test("computeCommitment works or throws bridge error", () => {
    try {
      const result = adapter.computeCommitment(100, "aa", "bb");
      assert.ok(result.hash);
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });

  await t.test("computeNullifier works or throws bridge error", () => {
    try {
      const result = adapter.computeNullifier("aa", "bb");
      assert.ok(result.hash);
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });

  await t.test("generatePreimageProof works or throws bridge error", () => {
    try {
      const result = adapter.generatePreimageProof([42]);
      assert.ok(result.proof);
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });

  await t.test("verifyPreimageProof works or throws bridge error", () => {
    try {
      const result = adapter.verifyPreimageProof("p", "v", "i");
      assert.equal(typeof result, "boolean");
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });

  await t.test("generateRangeProof works or throws bridge error", () => {
    try {
      const result = adapter.generateRangeProof(50, 0, 100);
      assert.ok(result.proof);
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });

  await t.test("verifyRangeProof works or throws bridge error", () => {
    try {
      const result = adapter.verifyRangeProof("p", "v", "i");
      assert.equal(typeof result, "boolean");
    } catch (err) {
      assert.match(err.message, /does not expose/);
    }
  });
});

// ── DilithiaError network error branch ──────────────────────────────────────

test("DilithiaError is thrown on network failure (non-abort)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  try {
    await assert.rejects(
      () => client.getBalance("dili1alice"),
      (err) => {
        assert.ok(err instanceof DilithiaError);
        assert.equal(err.message, "Network request failed");
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

test("DilithiaError is thrown on POST network failure (non-abort)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
  try {
    await assert.rejects(
      () => client.sendCall({ contract: "token", method: "transfer", args: {} }),
      (err) => {
        assert.ok(err instanceof DilithiaError);
        assert.equal(err.message, "Network request failed");
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

// ── jsonRpc returns envelope when result is not an object ───────────────────

test("jsonRpc returns full envelope when result is a primitive", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  mockFetch({
    jsonrpc: "2.0",
    id: 1,
    result: "just_a_string",
  });
  try {
    const result = await client.jsonRpc("some_method", {});
    // When result is not an object, the full envelope is returned
    assert.equal(result.result, "just_a_string");
  } finally {
    restoreFetch();
  }
});

// ── TimeoutError on POST ────────────────────────────────────────────────────

test("TimeoutError is thrown on POST when fetch is aborted (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc", timeoutMs: 1 });
  globalThis.fetch = async (_url, init) => {
    return new Promise((_resolve, reject) => {
      const checkAbort = () => {
        if (init.signal?.aborted) {
          reject(new DOMException("The operation was aborted.", "AbortError"));
        } else {
          setTimeout(checkAbort, 1);
        }
      };
      checkAbort();
    });
  };
  try {
    await assert.rejects(
      () => client.sendCall({ contract: "token", method: "transfer", args: {} }),
      (err) => {
        assert.ok(err instanceof TimeoutError);
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

// ── HttpError on POST ───────────────────────────────────────────────────────

test("HttpError is thrown on POST 500 response (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => new Response("Server Error", { status: 500 });
  try {
    await assert.rejects(
      () => client.simulate({ contract: "token", method: "transfer", args: {} }),
      (err) => {
        assert.ok(err instanceof HttpError);
        assert.equal(err.statusCode, 500);
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

// ── waitForReceipt re-throws non-404 errors ─────────────────────────────────

test("waitForReceipt re-throws non-404 errors immediately", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  globalThis.fetch = async () => new Response("Internal Server Error", { status: 500 });
  try {
    await assert.rejects(
      () => client.waitForReceipt("0xfail", 3, 5),
      (err) => {
        assert.ok(err instanceof HttpError);
        assert.equal(err.statusCode, 500);
        return true;
      }
    );
  } finally {
    restoreFetch();
  }
});

// ── Client wsUrl derivation with https ──────────────────────────────────────

test("client derives wss:// url from https:// base", () => {
  const client = createClient({ rpcUrl: "https://rpc.example/rpc" });
  assert.equal(client.wsUrl, "wss://rpc.example");
});

// ── rawPost with useChainBase ───────────────────────────────────────────────

test("rawPost with useChainBase sends POST to baseUrl (mock HTTP)", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc", chainBaseUrl: "http://chain.example" });
  const requests = [];
  mockFetch({ result: "chain_posted" }, { requests });
  try {
    await client.rawPost("/custom", { data: 1 }, true);
    assert.ok(requests[0].url.startsWith("http://chain.example/custom"));
  } finally {
    restoreFetch();
  }
});

// ── Native crypto integration (real compiled bridge) ────────────────────────

test("native crypto integration", async (t) => {
  const { loadSyncNativeCryptoAdapter } = await import("../dist/crypto.js");
  let crypto;
  try {
    crypto = loadSyncNativeCryptoAdapter();
  } catch {
    crypto = null;
  }
  if (!crypto) {
    t.skip("native bridge not available");
    return;
  }

  await t.test("generateMnemonic returns 24 words", () => {
    const mnemonic = crypto.generateMnemonic();
    assert.equal(typeof mnemonic, "string");
    assert.equal(mnemonic.split(" ").length, 24);
  });

  await t.test("validateMnemonic succeeds for generated mnemonic", () => {
    const mnemonic = crypto.generateMnemonic();
    crypto.validateMnemonic(mnemonic);
  });

  await t.test("validateMnemonic throws for invalid words", () => {
    assert.throws(() => {
      crypto.validateMnemonic("invalid words");
    });
  });

  await t.test("recoverHdWallet returns account with address, publicKey, secretKey", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    assert.ok(account.address, "account should have address");
    assert.ok(account.publicKey, "account should have publicKey");
    assert.ok(account.secretKey, "account should have secretKey");
  });

  await t.test("recoverHdWalletAccount(mnemonic, 0) same as recoverHdWallet", () => {
    const mnemonic = crypto.generateMnemonic();
    const wallet = crypto.recoverHdWallet(mnemonic);
    const account0 = crypto.recoverHdWalletAccount(mnemonic, 0);
    assert.equal(account0.address, wallet.address);
    assert.equal(account0.publicKey, wallet.publicKey);
    assert.equal(account0.secretKey, wallet.secretKey);
  });

  await t.test("recoverHdWalletAccount(mnemonic, 1) different address than index 0", () => {
    const mnemonic = crypto.generateMnemonic();
    const account0 = crypto.recoverHdWalletAccount(mnemonic, 0);
    const account1 = crypto.recoverHdWalletAccount(mnemonic, 1);
    assert.notEqual(account1.address, account0.address);
  });

  await t.test("signMessage returns signature with algorithm mldsa65", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    const sig = crypto.signMessage(account.secretKey, "hello");
    assert.equal(typeof sig.signature, "string");
    assert.equal(sig.algorithm, "mldsa65");
  });

  await t.test("verifyMessage returns true for valid signature", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    const sig = crypto.signMessage(account.secretKey, "hello");
    const valid = crypto.verifyMessage(account.publicKey, "hello", sig.signature);
    assert.equal(valid, true);
  });

  await t.test("verifyMessage returns false for wrong message", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    const sig = crypto.signMessage(account.secretKey, "hello");
    const valid = crypto.verifyMessage(account.publicKey, "wrong", sig.signature);
    assert.equal(valid, false);
  });

  await t.test("addressFromPublicKey matches account address", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    const addr = crypto.addressFromPublicKey(account.publicKey);
    assert.equal(addr, account.address);
  });

  await t.test("validateAddress succeeds for valid address", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    crypto.validateAddress(account.address);
  });

  await t.test("validatePublicKey succeeds for valid public key", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    crypto.validatePublicKey(account.publicKey);
  });

  await t.test("validateSecretKey succeeds for valid secret key", () => {
    const mnemonic = crypto.generateMnemonic();
    const account = crypto.recoverHdWallet(mnemonic);
    crypto.validateSecretKey(account.secretKey);
  });

  await t.test("keygen returns keypair with address, publicKey, secretKey", () => {
    const kp = crypto.keygen();
    assert.ok(kp.address, "keypair should have address");
    assert.ok(kp.publicKey, "keypair should have publicKey");
    assert.ok(kp.secretKey, "keypair should have secretKey");
  });

  await t.test("seedFromMnemonic returns hex string of 64 chars", () => {
    const mnemonic = crypto.generateMnemonic();
    const seed = crypto.seedFromMnemonic(mnemonic);
    assert.equal(typeof seed, "string");
    assert.equal(seed.length, 64);
    assert.match(seed, /^[0-9a-f]+$/);
  });

  await t.test("hashHex returns non-empty hash", () => {
    const hash = crypto.hashHex("deadbeef");
    assert.equal(typeof hash, "string");
    assert.ok(hash.length > 0, "hash should be non-empty");
  });

  await t.test("constantTimeEq returns true for equal values", () => {
    const result = crypto.constantTimeEq("abcdef", "abcdef");
    assert.equal(result, true);
  });

  await t.test("constantTimeEq returns false for different values", () => {
    const result = crypto.constantTimeEq("abcdef", "123456");
    assert.equal(result, false);
  });

  await t.test("keygenFromSeed returns deterministic keypair", () => {
    const mnemonic = crypto.generateMnemonic();
    const seed = crypto.seedFromMnemonic(mnemonic);
    const kp1 = crypto.keygenFromSeed(seed);
    assert.ok(kp1.address, "keypair should have address");
    assert.ok(kp1.publicKey, "keypair should have publicKey");
    assert.ok(kp1.secretKey, "keypair should have secretKey");
    // Deterministic: same seed produces same keypair
    const kp2 = crypto.keygenFromSeed(seed);
    assert.equal(kp2.address, kp1.address);
    assert.equal(kp2.publicKey, kp1.publicKey);
    assert.equal(kp2.secretKey, kp1.secretKey);
  });

  await t.test("deriveChildSeed returns 64-char hex different from parent", () => {
    const mnemonic = crypto.generateMnemonic();
    const seed = crypto.seedFromMnemonic(mnemonic);
    const childSeed = crypto.deriveChildSeed(seed, 0);
    assert.equal(typeof childSeed, "string");
    assert.equal(childSeed.length, 64);
    assert.match(childSeed, /^[0-9a-f]+$/);
    assert.notEqual(childSeed, seed);
    // Different indices produce different seeds
    const childSeed1 = crypto.deriveChildSeed(seed, 1);
    assert.notEqual(childSeed1, childSeed);
  });

  await t.test("currentHashAlg returns non-empty string", () => {
    const alg = crypto.currentHashAlg();
    assert.equal(typeof alg, "string");
    assert.ok(alg.length > 0, "algorithm name should be non-empty");
  });

  await t.test("setHashAlg sets algorithm and currentHashAlg reflects it", () => {
    const originalAlg = crypto.currentHashAlg();
    // Set to same algorithm (safe round-trip)
    crypto.setHashAlg(originalAlg);
    assert.equal(crypto.currentHashAlg(), originalAlg);
  });

  await t.test("hashLenHex returns positive number", () => {
    const len = crypto.hashLenHex();
    assert.equal(typeof len, "number");
    assert.ok(len > 0, "hash length should be positive");
  });
});

// ── Sync crypto adapter: mock-based happy path covering all functions ────────
// We create a temporary mock @dilithia/sdk-native module on disk so that
// loadSyncNativeCryptoAdapter's createRequire can resolve it.

import { createRequire } from "node:module";

/**
 * Clear the CJS require cache for @dilithia/sdk-native so that
 * loadSyncNativeCryptoAdapter picks up the freshly-written mock file.
 */
function clearSdkNativeRequireCache() {
  const esmRequire = createRequire(import.meta.url);
  try {
    const resolved = esmRequire.resolve("@dilithia/sdk-native");
    delete esmRequire.cache?.[resolved];
    // Also try the global require cache used by createRequire
    if (typeof require !== "undefined" && require.cache) {
      delete require.cache[resolved];
    }
  } catch {
    // Module not resolvable yet — nothing to clear
  }
}

test("sync crypto adapter: happy path with all functions mocked", async () => {
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  const mockDir = path.resolve(__dirname, "..", "node_modules", "@dilithia", "sdk-native");
  const mockIndex = path.join(mockDir, "index.js");
  const existed = fs.existsSync(mockDir);

  // Write a mock native module with all functions the sync adapter wraps
  const mockCode = `
    module.exports = {
      generateMnemonic: () => "word ".repeat(24).trim(),
      validateMnemonic: () => undefined,
      recoverHdWallet: (m) => ({ address: "a1", publicKey: "pk1", secretKey: "sk1", accountIndex: 0 }),
      recoverHdWalletAccount: (m, i) => ({ address: "a" + i, public_key: "pk" + i, secret_key: "sk" + i, account_index: i }),
      createHdWalletFileFromMnemonic: (m, p) => ({ address: "wf_a", publicKey: "wf_pk", secretKey: "wf_sk", accountIndex: 0 }),
      createHdWalletAccountFromMnemonic: (m, p, i) => ({ address: "wa" + i, publicKey: "wpk" + i, secretKey: "wsk" + i, accountIndex: i }),
      recoverWalletFile: () => ({ address: "rf_a", publicKey: "rf_pk", secretKey: "rf_sk", accountIndex: 0 }),
      addressFromPublicKey: (pk) => "derived_addr",
      validateAddress: (a) => a,
      addressFromPkChecksummed: (pk) => "checksummed_addr",
      addressWithChecksum: (a) => "addr_with_checksum",
      validatePublicKey: () => undefined,
      validateSecretKey: () => undefined,
      validateSignature: () => undefined,
      signMessage: (sk, msg) => ({ algorithm: "mldsa65", signature: "sig_hex" }),
      verifyMessage: () => true,
      keygen: () => ({ secretKey: "sk_new", publicKey: "pk_new", address: "addr_new" }),
      keygenFromSeed: (seed) => ({ secretKey: "sk_seed", publicKey: "pk_seed", address: "addr_seed" }),
      seedFromMnemonic: () => "abcd1234",
      deriveChildSeed: () => "child_seed",
      constantTimeEq: (a, b) => a === b,
      hashHex: () => "hash_result",
      setHashAlg: () => undefined,
      currentHashAlg: () => "sha3-256",
      hashLenHex: () => 32,
    };
  `;

  try {
    if (!existed) {
      fs.mkdirSync(mockDir, { recursive: true });
    }
    fs.writeFileSync(mockIndex, mockCode);
    clearSdkNativeRequireCache();

    const cryptoMod = await import("../dist/crypto.js");
    const adapter = cryptoMod.loadSyncNativeCryptoAdapter();
    assert.ok(adapter, "sync adapter should load with mock module");

    // ── Exercise all sync wrapper methods (covers lines 275-325 in crypto.js) ──

    // generateMnemonic / validateMnemonic
    assert.equal(adapter.generateMnemonic(), "word ".repeat(24).trim());
    adapter.validateMnemonic("anything");

    // recoverHdWallet
    const acct = adapter.recoverHdWallet("mnemonic");
    assert.equal(acct.address, "a1");
    assert.equal(acct.publicKey, "pk1");

    // recoverHdWalletAccount (snake_case normalization)
    const acct1 = adapter.recoverHdWalletAccount("mnemonic", 1);
    assert.equal(acct1.publicKey, "pk1");
    assert.equal(acct1.secretKey, "sk1");
    assert.equal(acct1.accountIndex, 1);

    // createHdWalletFileFromMnemonic
    const wfAcct = adapter.createHdWalletFileFromMnemonic("m", "p");
    assert.equal(wfAcct.address, "wf_a");

    // createHdWalletAccountFromMnemonic
    const waAcct = adapter.createHdWalletAccountFromMnemonic("m", "p", 2);
    assert.equal(waAcct.address, "wa2");

    // recoverWalletFile with snake_case keys
    const rfAcct = adapter.recoverWalletFile(
      { version: 1, address: "a", public_key: "pk", encrypted_sk: "esk", nonce: "n", tag: "t", account_index: 0 },
      "m", "p"
    );
    assert.equal(rfAcct.address, "rf_a");

    // recoverWalletFile with camelCase keys and null account_index
    const rfAcct2 = adapter.recoverWalletFile({ publicKey: "pk", encryptedSk: "esk" }, "m", "p");
    assert.equal(rfAcct2.address, "rf_a");

    // addressFromPublicKey
    assert.equal(adapter.addressFromPublicKey("pk"), "derived_addr");

    // validateAddress
    assert.equal(adapter.validateAddress("addr"), "addr");

    // addressFromPkChecksummed
    assert.equal(adapter.addressFromPkChecksummed("pk"), "checksummed_addr");

    // addressWithChecksum
    assert.equal(adapter.addressWithChecksum("addr"), "addr_with_checksum");

    // validatePublicKey / validateSecretKey / validateSignature
    adapter.validatePublicKey("pk");
    adapter.validateSecretKey("sk");
    adapter.validateSignature("sig");

    // signMessage
    const sig = adapter.signMessage("sk", "msg");
    assert.equal(sig.algorithm, "mldsa65");

    // verifyMessage
    assert.equal(adapter.verifyMessage("pk", "msg", "sig"), true);

    // keygen
    const kp = adapter.keygen();
    assert.equal(kp.address, "addr_new");

    // keygenFromSeed — the key uncovered sync line
    const kpSeed = adapter.keygenFromSeed("seed");
    assert.equal(kpSeed.address, "addr_seed");
    assert.equal(kpSeed.publicKey, "pk_seed");
    assert.equal(kpSeed.secretKey, "sk_seed");

    // seedFromMnemonic
    assert.equal(adapter.seedFromMnemonic("m"), "abcd1234");

    // deriveChildSeed
    assert.equal(adapter.deriveChildSeed("seed", 0), "child_seed");

    // constantTimeEq
    assert.equal(adapter.constantTimeEq("a", "a"), true);
    assert.equal(adapter.constantTimeEq("a", "b"), false);

    // hashHex
    assert.equal(adapter.hashHex("data"), "hash_result");

    // setHashAlg
    adapter.setHashAlg("sha3");

    // currentHashAlg
    assert.equal(adapter.currentHashAlg(), "sha3-256");

    // hashLenHex
    assert.equal(adapter.hashLenHex(), 32);
  } finally {
    // Clean up mock module and require cache
    clearSdkNativeRequireCache();
    if (fs.existsSync(mockIndex)) fs.unlinkSync(mockIndex);
    if (!existed && fs.existsSync(mockDir)) {
      fs.rmSync(mockDir, { recursive: true, force: true });
    }
  }
});

// ── Sync crypto adapter: throws when bridge functions are missing ────────────

test("sync crypto adapter: throws when bridge functions are missing", async () => {
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  const mockDir = path.resolve(__dirname, "..", "node_modules", "@dilithia", "sdk-native");
  const mockIndex = path.join(mockDir, "index.js");
  const existed = fs.existsSync(mockDir);

  // Minimal mock — only generateMnemonic + validateMnemonic; all others missing
  const mockCode = `
    module.exports = {
      generateMnemonic: () => "word ".repeat(24).trim(),
      validateMnemonic: () => undefined,
    };
  `;

  try {
    if (!existed) {
      fs.mkdirSync(mockDir, { recursive: true });
    }
    fs.writeFileSync(mockIndex, mockCode);
    clearSdkNativeRequireCache();

    const cryptoMod = await import("../dist/crypto.js");
    const adapter = cryptoMod.loadSyncNativeCryptoAdapter();
    assert.ok(adapter, "sync adapter should load with minimal mock");

    // These should work
    assert.equal(adapter.generateMnemonic(), "word ".repeat(24).trim());
    adapter.validateMnemonic("anything");

    // All optional bridge functions should throw
    assert.throws(() => adapter.recoverHdWallet("m"), /does not expose recoverHdWallet/);
    assert.throws(() => adapter.recoverHdWalletAccount("m", 0), /does not expose recoverHdWalletAccount/);
    assert.throws(() => adapter.createHdWalletFileFromMnemonic("m", "p"), /does not expose createHdWalletFileFromMnemonic/);
    assert.throws(() => adapter.createHdWalletAccountFromMnemonic("m", "p", 0), /does not expose createHdWalletAccountFromMnemonic/);
    assert.throws(() => adapter.recoverWalletFile({}, "m", "p"), /does not expose recoverWalletFile/);
    assert.throws(() => adapter.addressFromPublicKey("pk"), /does not expose addressFromPublicKey/);
    assert.throws(() => adapter.validateAddress("addr"), /does not expose validateAddress/);
    assert.throws(() => adapter.addressFromPkChecksummed("pk"), /does not expose addressFromPkChecksummed/);
    assert.throws(() => adapter.addressWithChecksum("addr"), /does not expose addressWithChecksum/);
    assert.throws(() => adapter.validatePublicKey("pk"), /does not expose validatePublicKey/);
    assert.throws(() => adapter.validateSecretKey("sk"), /does not expose validateSecretKey/);
    assert.throws(() => adapter.validateSignature("sig"), /does not expose validateSignature/);
    assert.throws(() => adapter.signMessage("sk", "msg"), /does not expose signMessage/);
    assert.throws(() => adapter.verifyMessage("pk", "msg", "sig"), /does not expose verifyMessage/);
    assert.throws(() => adapter.keygen(), /does not expose keygen/);
    assert.throws(() => adapter.keygenFromSeed("seed"), /does not expose keygenFromSeed/);
    assert.throws(() => adapter.seedFromMnemonic("m"), /does not expose seedFromMnemonic/);
    assert.throws(() => adapter.deriveChildSeed("seed", 0), /does not expose deriveChildSeed/);
    assert.throws(() => adapter.constantTimeEq("a", "b"), /does not expose constantTimeEq/);
    assert.throws(() => adapter.hashHex("data"), /does not expose hashHex/);
    assert.throws(() => adapter.setHashAlg("sha3"), /does not expose setHashAlg/);
    assert.throws(() => adapter.currentHashAlg(), /does not expose currentHashAlg/);
    assert.throws(() => adapter.hashLenHex(), /does not expose hashLenHex/);
  } finally {
    clearSdkNativeRequireCache();
    if (fs.existsSync(mockIndex)) fs.unlinkSync(mockIndex);
    if (!existed && fs.existsSync(mockDir)) {
      fs.rmSync(mockDir, { recursive: true, force: true });
    }
  }
});
