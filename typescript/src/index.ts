import { readFileSync } from "node:fs";

import {
  type Address,
  Address as AddressFactory,
  type TxHash,
  TxHash as TxHashFactory,
  TokenAmount,
  type Balance,
  type Nonce,
  type Receipt,
  type NetworkInfo,
  type GasEstimate,
  type QueryResult,
  type NameRecord,
  type ContractAbi,
  type SubmitResult,
  type DilithiaSigner,
} from "./types.js";

import {
  DilithiaError,
  RpcError,
  HttpError,
  TimeoutError,
} from "./errors.js";

// ── Re-exports ───────────────────────────────────────────────────────────────

export * from "./types.js";
export * from "./errors.js";
export * from "./validation.js";

export {
  type DilithiaAccount,
  type DilithiaCryptoAdapter,
  type DilithiaKeypair,
  type DilithiaSignature,
  type SyncDilithiaCryptoAdapter,
  type WalletFile,
  loadNativeCryptoAdapter,
  loadSyncNativeCryptoAdapter,
} from "./crypto.js";

export {
  type Commitment,
  type Nullifier,
  type ComplianceType,
  type StarkProof,
  type DilithiaZkAdapter,
  type SyncDilithiaZkAdapter,
  loadZkAdapter,
  loadSyncZkAdapter,
} from "./zk.js";

// ── Constants ────────────────────────────────────────────────────────────────

/** Current SDK version. */
export const SDK_VERSION = "0.3.0";

/** RPC protocol line version this SDK targets. */
export const RPC_LINE_VERSION = "0.3.0";

/** Minimum Node.js major version required at runtime. */
export const MIN_NODE_MAJOR = 22;

// ── Configuration ────────────────────────────────────────────────────────────

/** Options for constructing a {@link DilithiaClient}. */
export type DilithiaClientConfig = {
  /** The JSON-RPC endpoint URL (e.g. `"http://localhost:9070/rpc"`). */
  rpcUrl: string;
  /** Request timeout in milliseconds. Defaults to 10 000. */
  timeoutMs?: number;
  /** Base URL for REST endpoints (balance, nonce, receipt, etc.). Derived from rpcUrl if omitted. */
  chainBaseUrl?: string;
  /** Indexer service URL. */
  indexerUrl?: string;
  /** Oracle service URL. */
  oracleUrl?: string;
  /** WebSocket URL. Derived from chainBaseUrl if omitted. */
  wsUrl?: string;
  /** JWT bearer token for authenticated endpoints. */
  jwt?: string;
  /** Additional HTTP headers sent with every request. */
  headers?: Record<string, string>;
};

/** Canonical call envelope used for signing and submission. */
export type CanonicalCall = {
  contract: string;
  method: string;
  args: Record<string, unknown>;
  from?: string;
  nonce?: number;
  paymaster?: string;
  [key: string]: unknown;
};

/** Payload for contract deploy/upgrade requests. */
export type DeployPayload = {
  /** Contract name. */
  name: string;
  /** Hex-encoded contract bytecode. */
  bytecode: string;
  /** Deployer address. */
  from: string;
  /** Signature algorithm identifier. */
  alg: string;
  /** Public key (hex). */
  pk: string;
  /** Signature (hex). */
  sig: string;
  /** Account nonce. */
  nonce: number;
  /** Target chain identifier. */
  chainId: string;
  /** Optional contract version for upgrades. */
  version?: number;
};

/** Configuration for the {@link DilithiaGasSponsorConnector}. */
export type GasSponsorConnectorConfig = {
  /** The {@link DilithiaClient} instance to use. */
  client: DilithiaClient;
  /** The gas-sponsor contract identifier. */
  sponsorContract: string;
  /** Optional default paymaster address. */
  paymaster?: string;
};

/** Configuration for the {@link DilithiaMessagingConnector}. */
export type MessagingConnectorConfig = {
  /** The {@link DilithiaClient} instance to use. */
  client: DilithiaClient;
  /** The messaging contract identifier. */
  messagingContract: string;
  /** Optional default paymaster address. */
  paymaster?: string;
};

// ── Internal fetch helpers ───────────────────────────────────────────────────

/**
 * Internal: execute a GET request and return parsed JSON.
 * Throws typed errors for HTTP failures, timeouts, and RPC errors.
 */
async function fetchGetJson(
  url: string,
  headers: Record<string, string>,
  timeoutMs: number,
): Promise<Record<string, unknown>> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    let response: Response;
    try {
      response = await fetch(url, {
        method: "GET",
        headers: { ...headers, accept: "application/json" },
        signal: controller.signal,
      });
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") {
        throw new TimeoutError(timeoutMs);
      }
      throw new DilithiaError("Network request failed", { cause: err instanceof Error ? err : undefined });
    }
    if (!response.ok) {
      const body = await response.text().catch(() => "");
      throw new HttpError(response.status, body);
    }
    return (await response.json()) as Record<string, unknown>;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Internal: execute a POST request and return parsed JSON.
 * Throws typed errors for HTTP failures, timeouts, and RPC errors.
 */
async function fetchPostJson(
  url: string,
  body: Record<string, unknown>,
  headers: Record<string, string>,
  timeoutMs: number,
): Promise<Record<string, unknown>> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    let response: Response;
    try {
      response = await fetch(url, {
        method: "POST",
        headers: { ...headers, "content-type": "application/json", accept: "application/json" },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") {
        throw new TimeoutError(timeoutMs);
      }
      throw new DilithiaError("Network request failed", { cause: err instanceof Error ? err : undefined });
    }
    if (!response.ok) {
      const responseBody = await response.text().catch(() => "");
      throw new HttpError(response.status, responseBody);
    }
    return (await response.json()) as Record<string, unknown>;
  } finally {
    clearTimeout(timer);
  }
}

// ── Client ───────────────────────────────────────────────────────────────────

/**
 * Primary client for interacting with a Dilithia node.
 *
 * Provides typed methods for balance queries, nonce lookups, transaction
 * submission, contract calls, name-service resolution, deploy/upgrade,
 * and shielded-pool operations.
 *
 * @example
 * ```ts
 * const client = createClient({ rpcUrl: "http://localhost:9070/rpc" });
 * const balance = await client.getBalance("dili1abc...");
 * console.log(balance.balance.formatted()); // "42.5"
 * ```
 */
export class DilithiaClient {
  /** The JSON-RPC endpoint URL. */
  readonly rpcUrl: string;

  /** The chain base URL for REST endpoints. */
  readonly baseUrl: string;

  /** Request timeout in milliseconds. */
  readonly timeoutMs: number;

  /** Indexer service URL, if configured. */
  readonly indexerUrl?: string;

  /** Oracle service URL, if configured. */
  readonly oracleUrl?: string;

  /** WebSocket URL, if configured or derived. */
  readonly wsUrl?: string;

  /** JWT bearer token, if configured. */
  readonly jwt?: string;

  /** Additional HTTP headers. */
  readonly headers: Record<string, string>;

  /**
   * Create a new Dilithia client.
   *
   * @param config - Client configuration options.
   */
  constructor(config: DilithiaClientConfig) {
    this.rpcUrl = config.rpcUrl.replace(/\/+$/, "");
    this.baseUrl = (config.chainBaseUrl ?? this.rpcUrl.replace(/\/rpc$/, "")).replace(/\/+$/, "");
    this.timeoutMs = config.timeoutMs ?? 10_000;
    this.indexerUrl = config.indexerUrl?.replace(/\/+$/, "");
    this.oracleUrl = config.oracleUrl?.replace(/\/+$/, "");
    this.wsUrl = config.wsUrl?.replace(/\/+$/, "") ?? this.deriveWsUrl();
    this.jwt = config.jwt;
    this.headers = { ...(config.headers ?? {}) };
  }

  private deriveWsUrl(): string | undefined {
    if (this.baseUrl.startsWith("https://")) {
      return this.baseUrl.replace(/^https:\/\//, "wss://");
    }
    if (this.baseUrl.startsWith("http://")) {
      return this.baseUrl.replace(/^http:\/\//, "ws://");
    }
    return undefined;
  }

  // ── Typed query methods ──────────────────────────────────────────────

  /**
   * Fetch the token balance for an address.
   *
   * @param address - A Dilithia address (plain string or branded {@link Address}).
   * @returns A typed {@link Balance} with {@link TokenAmount}.
   */
  async getBalance(address: string | Address): Promise<Balance> {
    const raw = await this.getJson(`/balance/${encodeURIComponent(address)}`);
    const rawBalance = BigInt(String(raw.balance ?? raw.raw ?? "0"));
    return {
      address: AddressFactory.of(String(raw.address ?? address)),
      balance: TokenAmount.fromRaw(rawBalance),
      raw: rawBalance,
    };
  }

  /**
   * Fetch the next nonce for an address.
   *
   * @param address - A Dilithia address (plain string or branded {@link Address}).
   * @returns A typed {@link Nonce}.
   */
  async getNonce(address: string | Address): Promise<Nonce> {
    const raw = await this.getJson(`/nonce/${encodeURIComponent(address)}`);
    return {
      address: AddressFactory.of(String(raw.address ?? address)),
      nextNonce: Number(raw.nonce ?? raw.next_nonce ?? raw.nextNonce ?? 0),
    };
  }

  /**
   * Fetch a transaction receipt by hash.
   *
   * @param txHash - A transaction hash (plain string or branded {@link TxHash}).
   * @returns A typed {@link Receipt}.
   */
  async getReceipt(txHash: string | TxHash): Promise<Receipt> {
    const raw = await this.getJson(`/receipt/${encodeURIComponent(txHash)}`);
    return {
      txHash: TxHashFactory.of(String(raw.tx_hash ?? raw.txHash ?? txHash)),
      blockHeight: Number(raw.block_height ?? raw.blockHeight ?? 0),
      status: String(raw.status ?? "unknown"),
      result: raw.result,
      error: raw.error != null ? String(raw.error) : undefined,
      gasUsed: Number(raw.gas_used ?? raw.gasUsed ?? 0),
      feePaid: Number(raw.fee_paid ?? raw.feePaid ?? 0),
    };
  }

  /**
   * Fetch a summary for an address (via JSON-RPC).
   *
   * @param address - A Dilithia address.
   * @returns Raw RPC response data.
   */
  async getAddressSummary(address: string | Address): Promise<Record<string, unknown>> {
    return this.jsonRpc("qsc_addressSummary", { address: String(address) });
  }

  /**
   * Fetch the current gas estimate from the node.
   *
   * @returns A typed {@link GasEstimate}.
   */
  async getGasEstimate(): Promise<GasEstimate> {
    const raw = await this.jsonRpc("qsc_gasEstimate", {});
    return {
      gasLimit: Number(raw.gas_limit ?? raw.gasLimit ?? 0),
      baseFee: Number(raw.base_fee ?? raw.baseFee ?? 0),
      estimatedCost: Number(raw.estimated_cost ?? raw.estimatedCost ?? 0),
    };
  }

  /**
   * Fetch the current base fee from the node.
   *
   * @returns Raw RPC response data.
   */
  async getBaseFee(): Promise<Record<string, unknown>> {
    return this.jsonRpc("qsc_baseFee", {});
  }

  /**
   * Fetch network information (chain ID, block height, base fee).
   *
   * @returns A typed {@link NetworkInfo}.
   */
  async getNetworkInfo(): Promise<NetworkInfo> {
    const raw = await this.jsonRpc("qsc_networkInfo", {});
    return {
      chainId: String(raw.chain_id ?? raw.chainId ?? ""),
      blockHeight: Number(raw.block_height ?? raw.blockHeight ?? 0),
      baseFee: Number(raw.base_fee ?? raw.baseFee ?? 0),
    };
  }

  // ── JSON-RPC helpers ─────────────────────────────────────────────────

  /**
   * Build a JSON-RPC 2.0 request envelope.
   *
   * @param method - The RPC method name.
   * @param params - The method parameters.
   * @param id     - The request identifier (defaults to 1).
   * @returns A plain object ready to be serialized.
   */
  buildJsonRpcRequest(method: string, params: Record<string, unknown> = {}, id = 1): Record<string, unknown> {
    return {
      jsonrpc: "2.0",
      id,
      method,
      params,
    };
  }

  /**
   * Send a JSON-RPC request and return the `result` field.
   *
   * @param method - The RPC method name.
   * @param params - The method parameters.
   * @param id     - The request identifier (defaults to 1).
   * @returns The `result` field from the JSON-RPC response.
   * @throws {@link RpcError} if the response contains an `error` field.
   */
  async jsonRpc(method: string, params: Record<string, unknown> = {}, id = 1): Promise<Record<string, unknown>> {
    const envelope = await this.postJson("", this.buildJsonRpcRequest(method, params, id));
    if (envelope.error != null) {
      const err = envelope.error as Record<string, unknown>;
      throw new RpcError(Number(err.code ?? -1), String(err.message ?? "Unknown RPC error"));
    }
    if (envelope.result != null && typeof envelope.result === "object") {
      return envelope.result as Record<string, unknown>;
    }
    return envelope;
  }

  /**
   * Send a raw JSON-RPC request and return the full response envelope.
   *
   * @param method - The RPC method name.
   * @param params - The method parameters.
   * @param id     - The request identifier.
   * @returns The full JSON-RPC response envelope.
   */
  async rawRpc(method: string, params: Record<string, unknown> = {}, id = 1): Promise<Record<string, unknown>> {
    return this.postJson("", this.buildJsonRpcRequest(method, params, id));
  }

  /**
   * Send a raw GET request to a custom pathname.
   *
   * @param pathname    - URL path (e.g. `/status`).
   * @param useChainBase - If true, use {@link baseUrl} instead of {@link rpcUrl}.
   * @returns Parsed JSON response.
   */
  async rawGet(pathname: string, useChainBase = false): Promise<Record<string, unknown>> {
    const root = useChainBase ? this.baseUrl : this.rpcUrl;
    return this.getAbsoluteJson(`${root}${pathname.startsWith("/") ? pathname : `/${pathname}`}`);
  }

  /**
   * Send a raw POST request to a custom pathname.
   *
   * @param pathname    - URL path.
   * @param body        - JSON body.
   * @param useChainBase - If true, use {@link baseUrl} instead of {@link rpcUrl}.
   * @returns Parsed JSON response.
   */
  async rawPost(pathname: string, body: Record<string, unknown>, useChainBase = false): Promise<Record<string, unknown>> {
    const root = useChainBase ? this.baseUrl : this.rpcUrl;
    return this.postAbsoluteJson(`${root}${pathname.startsWith("/") ? pathname : `/${pathname}`}`, body);
  }

  // ── WebSocket helpers ────────────────────────────────────────────────

  /**
   * Build a JSON-RPC request envelope suitable for sending over WebSocket.
   *
   * @param method - The RPC method name.
   * @param params - The method parameters.
   * @param id     - The request identifier.
   * @returns A plain object ready to be serialized.
   */
  buildWsRequest(method: string, params: Record<string, unknown> = {}, id = 1): Record<string, unknown> {
    return this.buildJsonRpcRequest(method, params, id);
  }

  /**
   * Build HTTP headers including JWT authorization and custom headers.
   *
   * @param extra - Additional headers to merge.
   * @returns Combined headers object.
   */
  buildAuthHeaders(extra: Record<string, string> = {}): Record<string, string> {
    return {
      ...(this.jwt ? { Authorization: `Bearer ${this.jwt}` } : {}),
      ...this.headers,
      ...extra,
    };
  }

  /**
   * Get WebSocket connection info (URL and headers).
   *
   * @returns An object with `url` and `headers` for establishing a WebSocket connection.
   */
  getWsConnectionInfo(): { url?: string; headers: Record<string, string> } {
    return {
      url: this.wsUrl,
      headers: this.buildAuthHeaders(),
    };
  }

  // ── Name service ─────────────────────────────────────────────────────

  /**
   * Resolve a name to an address via the name service.
   *
   * @param name - The name to resolve (e.g. `"alice.dili"`).
   * @returns A typed {@link NameRecord}.
   */
  async resolveName(name: string): Promise<NameRecord> {
    const raw = await this.getAbsoluteJson(`${this.baseUrl}/names/resolve/${encodeURIComponent(name)}`);
    return {
      name: String(raw.name ?? name),
      address: AddressFactory.of(String(raw.address ?? "")),
    };
  }

  /**
   * Look up a name in the name service.
   *
   * @param name - The name to look up.
   * @returns A typed {@link NameRecord}.
   */
  async lookupName(name: string): Promise<NameRecord> {
    const raw = await this.getAbsoluteJson(`${this.baseUrl}/names/lookup/${encodeURIComponent(name)}`);
    return {
      name: String(raw.name ?? name),
      address: AddressFactory.of(String(raw.address ?? "")),
    };
  }

  /**
   * Check if a name is available for registration.
   *
   * @param name - The name to check.
   * @returns Raw response data.
   */
  async isNameAvailable(name: string): Promise<Record<string, unknown>> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/available/${encodeURIComponent(name)}`);
  }

  /**
   * Get all names owned by an address.
   *
   * @param address - The owner address.
   * @returns Raw response data.
   */
  async getNamesByOwner(address: string | Address): Promise<Record<string, unknown>> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/by-owner/${encodeURIComponent(address)}`);
  }

  /**
   * Reverse-resolve an address to a name.
   *
   * @param address - The address to look up.
   * @returns A typed {@link NameRecord}.
   */
  async reverseResolveName(address: string | Address): Promise<NameRecord> {
    const raw = await this.getAbsoluteJson(`${this.baseUrl}/names/reverse/${encodeURIComponent(address)}`);
    return {
      name: String(raw.name ?? ""),
      address: AddressFactory.of(String(raw.address ?? address)),
    };
  }

  // ── Contract queries and calls ───────────────────────────────────────

  /**
   * Execute a read-only contract query.
   *
   * @param contract - The contract identifier.
   * @param method   - The method to call.
   * @param args     - Method arguments.
   * @returns A typed {@link QueryResult}.
   */
  async queryContract(contract: string, method: string, args: Record<string, unknown> = {}): Promise<QueryResult> {
    const encodedArgs = encodeURIComponent(JSON.stringify(args));
    const raw = await this.getAbsoluteJson(
      `${this.baseUrl}/query?contract=${encodeURIComponent(contract)}&method=${encodeURIComponent(method)}&args=${encodedArgs}`
    );
    return { value: raw.value !== undefined ? raw.value : raw };
  }

  /**
   * Fetch the ABI for a deployed contract.
   *
   * @param contract - The contract identifier.
   * @returns A typed {@link ContractAbi}.
   */
  async getContractAbi(contract: string): Promise<ContractAbi> {
    const raw = await this.jsonRpc("qsc_getAbi", { contract });
    const methods = Array.isArray(raw.methods)
      ? (raw.methods as Record<string, unknown>[]).map((m) => ({
          name: String(m.name ?? ""),
          mutates: Boolean(m.mutates),
          hasArgs: Boolean(m.has_args ?? m.hasArgs),
        }))
      : [];
    return { contract: String(raw.contract ?? contract), methods };
  }

  /**
   * Simulate a call without submitting it.
   *
   * @param call - The call envelope to simulate.
   * @returns Raw simulation result.
   */
  async simulate(call: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.postJson("/simulate", call);
  }

  /**
   * Submit a call to the node.
   *
   * @param call - The call envelope to submit.
   * @returns A typed {@link SubmitResult}.
   */
  async sendCall(call: Record<string, unknown>): Promise<SubmitResult> {
    const raw = await this.postJson("/call", call);
    return {
      accepted: Boolean(raw.accepted ?? true),
      txHash: TxHashFactory.of(String(raw.tx_hash ?? raw.txHash ?? "")),
    };
  }

  /**
   * Attach a paymaster to a canonical call.
   *
   * @param call      - The original call envelope.
   * @param paymaster - The paymaster contract address.
   * @returns A new call envelope with the paymaster field set.
   */
  withPaymaster(call: CanonicalCall, paymaster: string): CanonicalCall {
    return {
      ...call,
      paymaster,
    };
  }

  /**
   * Sign and submit a canonical call.
   *
   * @param call   - The call envelope to sign.
   * @param signer - A {@link DilithiaSigner} implementation.
   * @returns A typed {@link SubmitResult}.
   */
  async sendSignedCall(call: CanonicalCall, signer: DilithiaSigner): Promise<SubmitResult> {
    const payloadJson = JSON.stringify(call);
    const signature = await signer.signCanonicalPayload(payloadJson);
    return this.sendCall({
      ...call,
      ...signature,
    });
  }

  /**
   * Sign and submit a call with a paymaster attached.
   *
   * @param call      - The call envelope.
   * @param paymaster - The paymaster contract address.
   * @param signer    - A {@link DilithiaSigner} implementation.
   * @returns A typed {@link SubmitResult}.
   */
  async sendSponsoredCall(
    call: CanonicalCall,
    paymaster: string,
    signer: DilithiaSigner,
  ): Promise<SubmitResult> {
    return this.sendSignedCall(this.withPaymaster(call, paymaster), signer);
  }

  /**
   * Build a meta-transaction forwarder call.
   *
   * @param forwarderContract - The forwarder contract identifier.
   * @param args              - The forwarded call arguments.
   * @param options           - Optional paymaster.
   * @returns A {@link CanonicalCall} envelope.
   */
  buildForwarderCall(
    forwarderContract: string,
    args: Record<string, unknown>,
    options: { paymaster?: string } = {},
  ): CanonicalCall {
    const call: CanonicalCall = {
      contract: forwarderContract,
      method: "forward",
      args,
    };
    return options.paymaster ? this.withPaymaster(call, options.paymaster) : call;
  }

  /**
   * Build a contract call envelope.
   *
   * @param contract - The contract identifier.
   * @param method   - The method name.
   * @param args     - Method arguments.
   * @param options  - Optional paymaster.
   * @returns A {@link CanonicalCall} envelope.
   */
  buildContractCall(
    contract: string,
    method: string,
    args: Record<string, unknown> = {},
    options: { paymaster?: string } = {},
  ): CanonicalCall {
    const call: CanonicalCall = { contract, method, args };
    return options.paymaster ? this.withPaymaster(call, options.paymaster) : call;
  }

  /**
   * Build and submit a contract call in one step.
   *
   * @param contract - The contract identifier.
   * @param method   - The method name.
   * @param args     - Method arguments.
   * @param options  - Optional paymaster.
   * @returns A typed {@link SubmitResult}.
   */
  async callContract(
    contract: string,
    method: string,
    args: Record<string, unknown> = {},
    options: { paymaster?: string } = {},
  ): Promise<SubmitResult> {
    return this.sendCall(this.buildContractCall(contract, method, args, options));
  }

  // ── Deploy / Upgrade ─────────────────────────────────────────────────

  /**
   * Build the canonical payload for a deploy or upgrade request.
   * Keys are sorted alphabetically for deterministic signing.
   *
   * @param from         - Deployer address.
   * @param name         - Contract name.
   * @param bytecodeHash - Pre-computed hash of the bytecode hex.
   * @param nonce        - Account nonce.
   * @param chainId      - Target chain identifier.
   * @returns A canonical payload object.
   */
  buildDeployCanonicalPayload(
    from: string,
    name: string,
    bytecodeHash: string,
    nonce: number,
    chainId: string,
  ): Record<string, unknown> {
    return {
      bytecode_hash: bytecodeHash,
      chain_id: chainId,
      from,
      name,
      nonce,
    };
  }

  /**
   * Build the HTTP request descriptor for deploying a contract.
   *
   * @param payload - The deploy payload.
   * @returns An object with `path` and `body` for the POST request.
   */
  deployContractRequest(payload: DeployPayload): { path: string; body: Record<string, unknown> } {
    return {
      path: "/deploy",
      body: this.buildDeployBody(payload),
    };
  }

  /**
   * Deploy a contract by POSTing directly to the chain base URL.
   *
   * @param payload - The deploy payload.
   * @returns A typed {@link SubmitResult}.
   */
  async deployContract(payload: DeployPayload): Promise<SubmitResult> {
    const body = this.buildDeployBody(payload);
    const raw = await this.postAbsoluteJson(`${this.baseUrl}/deploy`, body);
    return {
      accepted: Boolean(raw.accepted ?? true),
      txHash: TxHashFactory.of(String(raw.tx_hash ?? raw.txHash ?? "")),
    };
  }

  /**
   * Build the HTTP request descriptor for upgrading a contract.
   *
   * @param payload - The upgrade payload.
   * @returns An object with `path` and `body` for the POST request.
   */
  upgradeContractRequest(payload: DeployPayload): { path: string; body: Record<string, unknown> } {
    return {
      path: "/upgrade",
      body: this.buildDeployBody(payload),
    };
  }

  /**
   * Upgrade a contract by POSTing directly to the chain base URL.
   *
   * @param payload - The upgrade payload.
   * @returns A typed {@link SubmitResult}.
   */
  async upgradeContract(payload: DeployPayload): Promise<SubmitResult> {
    const body = this.buildDeployBody(payload);
    const raw = await this.postAbsoluteJson(`${this.baseUrl}/upgrade`, body);
    return {
      accepted: Boolean(raw.accepted ?? true),
      txHash: TxHashFactory.of(String(raw.tx_hash ?? raw.txHash ?? "")),
    };
  }

  private buildDeployBody(payload: DeployPayload): Record<string, unknown> {
    return {
      name: payload.name,
      bytecode: payload.bytecode,
      from: payload.from,
      alg: payload.alg,
      pk: payload.pk,
      sig: payload.sig,
      nonce: payload.nonce,
      chain_id: payload.chainId,
      ...(payload.version !== undefined ? { version: payload.version } : {}),
    };
  }

  /**
   * Build a JSON-RPC request body for querying a contract's ABI.
   *
   * @param contract - The contract identifier.
   * @returns A JSON-RPC request object.
   * @deprecated Use {@link getContractAbi} for a typed result.
   */
  queryContractAbi(contract: string): Record<string, unknown> {
    return this.buildJsonRpcRequest("qsc_getAbi", { contract });
  }

  // ── Receipt polling ──────────────────────────────────────────────────

  /**
   * Poll for a transaction receipt until it becomes available.
   *
   * @param txHash      - The transaction hash to poll for.
   * @param maxAttempts - Maximum number of polling attempts (defaults to 12).
   * @param delayMs     - Delay between attempts in milliseconds (defaults to 1000).
   * @returns A typed {@link Receipt}.
   * @throws {@link TimeoutError} if the receipt is not available after all attempts.
   */
  async waitForReceipt(txHash: string | TxHash, maxAttempts = 12, delayMs = 1_000): Promise<Receipt> {
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      try {
        return await this.getReceipt(txHash);
      } catch (error) {
        if (error instanceof HttpError && error.statusCode === 404) {
          // Not available yet, keep polling
        } else if (error instanceof Error && error.message.includes("404")) {
          // Backward-compatible check
        } else {
          throw error;
        }
      }
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
    throw new TimeoutError(maxAttempts * delayMs);
  }

  // ── Shielded pool methods ────────────────────────────────────────────

  /**
   * Deposit into the shielded pool.
   *
   * @param commitment - The deposit commitment hash.
   * @param value      - The deposit value.
   * @param proofHex   - The zero-knowledge proof (hex).
   * @returns A typed {@link SubmitResult}.
   */
  async shieldedDeposit(
    commitment: string,
    value: number,
    proofHex: string,
  ): Promise<SubmitResult> {
    return this.callContract("shielded", "deposit", {
      commitment,
      value,
      proof: proofHex,
    });
  }

  /**
   * Withdraw from the shielded pool.
   *
   * @param nullifier      - The nullifier hash.
   * @param amount         - The withdrawal amount.
   * @param recipient      - The recipient address.
   * @param proofHex       - The zero-knowledge proof (hex).
   * @param commitmentRoot - The Merkle root of commitments.
   * @returns A typed {@link SubmitResult}.
   */
  async shieldedWithdraw(
    nullifier: string,
    amount: number,
    recipient: string,
    proofHex: string,
    commitmentRoot: string,
  ): Promise<SubmitResult> {
    return this.callContract("shielded", "withdraw", {
      nullifier,
      amount,
      recipient,
      proof: proofHex,
      commitment_root: commitmentRoot,
    });
  }

  /**
   * Query the current commitment Merkle root from the shielded pool.
   *
   * @returns A typed {@link QueryResult}.
   */
  async getCommitmentRoot(): Promise<QueryResult> {
    return this.queryContract("shielded", "commitment_root");
  }

  /**
   * Check whether a nullifier has already been spent.
   *
   * @param nullifier - The nullifier hash.
   * @returns A typed {@link QueryResult}.
   */
  async isNullifierSpent(nullifier: string): Promise<QueryResult> {
    return this.queryContract("shielded", "is_nullifier_spent", { nullifier });
  }

  // ── Internal HTTP plumbing ───────────────────────────────────────────

  private async getJson(pathname: string): Promise<Record<string, unknown>> {
    return this.getAbsoluteJson(`${this.rpcUrl}${pathname}`);
  }

  private async getAbsoluteJson(url: string): Promise<Record<string, unknown>> {
    return fetchGetJson(url, this.buildAuthHeaders(), this.timeoutMs);
  }

  private async postJson(pathname: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.postAbsoluteJson(`${this.rpcUrl}${pathname}`, body);
  }

  private async postAbsoluteJson(url: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return fetchPostJson(url, body, this.buildAuthHeaders(), this.timeoutMs);
  }
}

// ── Factory functions ────────────────────────────────────────────────────────

/**
 * Create a new {@link DilithiaClient}.
 *
 * @param config - Client configuration options.
 * @returns A configured client instance.
 */
export function createClient(config: DilithiaClientConfig): DilithiaClient {
  return new DilithiaClient(config);
}

// ── Gas Sponsor Connector ────────────────────────────────────────────────────

/**
 * Helper for interacting with a Dilithia gas-sponsor contract.
 *
 * Wraps common gas-sponsor queries and applies the paymaster to calls
 * before submission.
 */
export class DilithiaGasSponsorConnector {
  /** The underlying client. */
  readonly client: DilithiaClient;

  /** The gas-sponsor contract identifier. */
  readonly sponsorContract: string;

  /** The default paymaster address (if any). */
  readonly paymaster?: string;

  /**
   * Create a new gas-sponsor connector.
   *
   * @param config - Connector configuration.
   */
  constructor(config: GasSponsorConnectorConfig) {
    this.client = config.client;
    this.sponsorContract = config.sponsorContract;
    this.paymaster = config.paymaster;
  }

  /**
   * Build a query to check if the sponsor accepts a given call.
   *
   * @param user     - The user address.
   * @param contract - The target contract.
   * @param method   - The target method.
   * @returns A {@link CanonicalCall} for the accept query.
   */
  buildAcceptQuery(user: string, contract: string, method: string): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "accept",
      args: { user, contract, method },
    };
  }

  /**
   * Build a query to get the maximum gas allowance per user.
   *
   * @returns A {@link CanonicalCall} for the max-gas query.
   */
  buildMaxGasPerUserQuery(): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "max_gas_per_user",
      args: {},
    };
  }

  /**
   * Build a query to check a user's remaining gas quota.
   *
   * @param user - The user address.
   * @returns A {@link CanonicalCall} for the remaining-quota query.
   */
  buildRemainingQuotaQuery(user: string): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "remaining_quota",
      args: { user },
    };
  }

  /**
   * Build a query to get the sponsor token.
   *
   * @returns A {@link CanonicalCall} for the sponsor-token query.
   */
  buildSponsorTokenQuery(): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "sponsor_token",
      args: {},
    };
  }

  /**
   * Build a call to fund the gas-sponsor contract.
   *
   * @param amount - The funding amount.
   * @returns A {@link CanonicalCall} for the fund call.
   */
  buildFundCall(amount: number): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "fund",
      args: { amount },
    };
  }

  /**
   * Apply the default paymaster to a call (if configured).
   *
   * @param call - The call to decorate.
   * @returns The call with the paymaster applied (or unchanged if none configured).
   */
  applyPaymaster(call: CanonicalCall): CanonicalCall {
    return this.paymaster ? this.client.withPaymaster(call, this.paymaster) : call;
  }

  /**
   * Simulate a call with the paymaster applied.
   *
   * @param call - The call to simulate.
   * @returns Raw simulation result.
   */
  async simulateSponsoredCall(call: CanonicalCall): Promise<Record<string, unknown>> {
    return this.client.simulate(this.applyPaymaster(call));
  }

  /**
   * Sign and submit a call with the paymaster applied.
   *
   * @param call   - The call to submit.
   * @param signer - A {@link DilithiaSigner} implementation.
   * @returns A typed {@link SubmitResult}.
   */
  async sendSponsoredCall(
    call: CanonicalCall,
    signer: DilithiaSigner,
  ): Promise<SubmitResult> {
    return this.client.sendSignedCall(this.applyPaymaster(call), signer);
  }
}

/**
 * Create a new {@link DilithiaGasSponsorConnector}.
 *
 * @param config - Connector configuration.
 * @returns A configured gas-sponsor connector.
 */
export function createGasSponsorConnector(config: GasSponsorConnectorConfig): DilithiaGasSponsorConnector {
  return new DilithiaGasSponsorConnector(config);
}

// ── Messaging Connector ──────────────────────────────────────────────────────

/**
 * Helper for interacting with a Dilithia cross-chain messaging contract.
 */
export class DilithiaMessagingConnector {
  /** The underlying client. */
  readonly client: DilithiaClient;

  /** The messaging contract identifier. */
  readonly messagingContract: string;

  /** The default paymaster address (if any). */
  readonly paymaster?: string;

  /**
   * Create a new messaging connector.
   *
   * @param config - Connector configuration.
   */
  constructor(config: MessagingConnectorConfig) {
    this.client = config.client;
    this.messagingContract = config.messagingContract;
    this.paymaster = config.paymaster;
  }

  /**
   * Build a call to send a cross-chain message.
   *
   * @param destChain - The destination chain identifier.
   * @param payload   - The message payload.
   * @returns A {@link CanonicalCall} with paymaster applied.
   */
  buildSendMessageCall(destChain: string, payload: Record<string, unknown> | string): CanonicalCall {
    return this.applyPaymaster({
      contract: this.messagingContract,
      method: "send_message",
      args: { dest_chain: destChain, payload },
    });
  }

  /**
   * Build a call to receive a cross-chain message.
   *
   * @param sourceChain    - The source chain identifier.
   * @param sourceContract - The source contract on the originating chain.
   * @param payload        - The message payload.
   * @returns A {@link CanonicalCall} with paymaster applied.
   */
  buildReceiveMessageCall(
    sourceChain: string,
    sourceContract: string,
    payload: Record<string, unknown> | string,
  ): CanonicalCall {
    return this.applyPaymaster({
      contract: this.messagingContract,
      method: "receive_message",
      args: { source_chain: sourceChain, source_contract: sourceContract, payload },
    });
  }

  /**
   * Build a query for the messaging outbox.
   *
   * @returns A {@link CanonicalCall}.
   */
  buildOutboxQuery(): CanonicalCall {
    return {
      contract: this.messagingContract,
      method: "outbox",
      args: {},
    };
  }

  /**
   * Build a query for the messaging inbox.
   *
   * @returns A {@link CanonicalCall}.
   */
  buildInboxQuery(): CanonicalCall {
    return {
      contract: this.messagingContract,
      method: "inbox",
      args: {},
    };
  }

  /**
   * Query the messaging outbox.
   *
   * @returns A typed {@link QueryResult}.
   */
  async queryOutbox(): Promise<QueryResult> {
    return this.client.queryContract(this.messagingContract, "outbox", {});
  }

  /**
   * Query the messaging inbox.
   *
   * @returns A typed {@link QueryResult}.
   */
  async queryInbox(): Promise<QueryResult> {
    return this.client.queryContract(this.messagingContract, "inbox", {});
  }

  /**
   * Sign and send a cross-chain message.
   *
   * @param destChain - The destination chain identifier.
   * @param payload   - The message payload.
   * @param signer    - A {@link DilithiaSigner} implementation.
   * @returns A typed {@link SubmitResult}.
   */
  async sendMessage(
    destChain: string,
    payload: Record<string, unknown> | string,
    signer: DilithiaSigner,
  ): Promise<SubmitResult> {
    return this.client.sendSignedCall(this.buildSendMessageCall(destChain, payload), signer);
  }

  /**
   * Apply the default paymaster to a call (if configured).
   *
   * @param call - The call to decorate.
   * @returns The call with the paymaster applied.
   */
  applyPaymaster(call: CanonicalCall): CanonicalCall {
    return this.paymaster ? this.client.withPaymaster(call, this.paymaster) : call;
  }
}

/**
 * Create a new {@link DilithiaMessagingConnector}.
 *
 * @param config - Connector configuration.
 * @returns A configured messaging connector.
 */
export function createMessagingConnector(config: MessagingConnectorConfig): DilithiaMessagingConnector {
  return new DilithiaMessagingConnector(config);
}

// ── Utilities ────────────────────────────────────────────────────────────────

/**
 * Read a WASM file from disk and return its contents as a hex string.
 *
 * @param filePath - Absolute or relative path to the `.wasm` file.
 * @returns Hex-encoded file contents.
 */
export function readWasmFileHex(filePath: string): string {
  return readFileSync(filePath).toString("hex");
}
