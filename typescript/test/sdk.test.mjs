import test from "node:test";
import assert from "node:assert/strict";
import {
  MIN_NODE_MAJOR,
  RPC_LINE_VERSION,
  SDK_VERSION,
  createClient,
  createGasSponsorConnector,
  createMessagingConnector,
  loadNativeCryptoAdapter
} from "../dist/index.js";

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

test("sdk version stays aligned with rpc line", () => {
  assert.equal(SDK_VERSION, "0.1.0");
  assert.equal(RPC_LINE_VERSION, "0.1.0");
});

test("node runtime satisfies the minimum supported version", () => {
  const major = Number(process.versions.node.split(".")[0]);
  assert.ok(Number.isInteger(major));
  assert.ok(major >= MIN_NODE_MAJOR);
});

test("native crypto adapter loader returns null when bridge is unavailable", async () => {
  const adapter = await loadNativeCryptoAdapter(async () => {
    throw new Error("not installed");
  });
  assert.equal(adapter, null);
});

test("native crypto adapter loader maps a bridge module correctly", async () => {
  const adapter = await loadNativeCryptoAdapter(async () => ({
    generate_mnemonic: () => "word ".repeat(24).trim(),
    validate_mnemonic: () => undefined,
    address_from_public_key: () => "derived-address",
    sign_message: () => ({ algorithm: "mldsa65", signature: "deadbeef" }),
    verify_message: () => true,
    recover_wallet_file: () => ({
      address: "addr-1",
      public_key: "pk-1",
      secret_key: "sk-1",
      account_index: 0,
      wallet_file: { version: 1 },
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

test("sendSignedCall merges signer payload into the submitted call", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example" });
  const requests = [];
  globalThis.fetch = async (url, init) => {
    requests.push({ url, init });
    return {
      ok: true,
      async json() {
        return { tx_hash: "0xabc" };
      },
    };
  };

  const result = await client.sendSignedCall(
    { from: "a", contract: "token", method: "transfer", args: { to: "b", amount: 1 } },
    {
      async signCanonicalPayload(payloadJson) {
        return { sig: `signed:${payloadJson}`, pk: "pk", alg: "mldsa65" };
      },
    }
  );

  assert.deepEqual(result, { tx_hash: "0xabc" });
  assert.equal(requests.length, 1);
  const body = JSON.parse(requests[0].init.body);
  assert.equal(body.pk, "pk");
  assert.equal(body.alg, "mldsa65");
  assert.match(body.sig, /^signed:/);
});

test("name service methods derive explorer-style paths from rpc url", async () => {
  const client = createClient({ rpcUrl: "http://rpc.example/rpc" });
  const requests = [];
  globalThis.fetch = async (url) => {
    requests.push(url);
    return {
      ok: true,
      async json() {
        return { ok: true };
      },
    };
  };

  await client.resolveName("alice.dili");
  await client.reverseResolveName("dili1alice");
  await client.queryContract("wasm:amm", "get_reserves", {});

  assert.equal(requests[0], "http://rpc.example/names/resolve/alice.dili");
  assert.equal(requests[1], "http://rpc.example/names/reverse/dili1alice");
  assert.equal(requests[2], "http://rpc.example/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D");
});

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
