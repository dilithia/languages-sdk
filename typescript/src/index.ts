export type DilithiumClientConfig = {
  rpcUrl: string;
  timeoutMs?: number;
  chainBaseUrl?: string;
  indexerUrl?: string;
  oracleUrl?: string;
  wsUrl?: string;
  jwt?: string;
  headers?: Record<string, string>;
};

export const SDK_VERSION = "0.3.0";
export const RPC_LINE_VERSION = "0.3.0";
export const MIN_NODE_MAJOR = 22;

export type AddressSummary = Record<string, unknown>;
export type Receipt = Record<string, unknown>;
export type SimulationResult = Record<string, unknown>;
export type SubmittedCall = Record<string, unknown>;
export type CanonicalCall = Record<string, unknown>;
export type NameServiceRecord = Record<string, unknown>;
export type GasEstimate = Record<string, unknown>;
export type GasSponsorConnectorConfig = {
  client: DilithiumClient;
  sponsorContract: string;
  paymaster?: string;
};
export type MessagingConnectorConfig = {
  client: DilithiumClient;
  messagingContract: string;
  paymaster?: string;
};

export {
  type DilithiumAccount,
  type DilithiumCryptoAdapter,
  type DilithiumSignature,
  type WalletFile,
  loadNativeCryptoAdapter,
} from "./crypto.js";

export class DilithiumClient {
  readonly rpcUrl: string;
  readonly baseUrl: string;
  readonly timeoutMs: number;
  readonly indexerUrl?: string;
  readonly oracleUrl?: string;
  readonly wsUrl?: string;
  readonly jwt?: string;
  readonly headers: Record<string, string>;

  constructor(config: DilithiumClientConfig) {
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

  async getBalance(address: string): Promise<Record<string, unknown>> {
    return this.getJson(`/balance/${encodeURIComponent(address)}`);
  }

  async getNonce(address: string): Promise<Record<string, unknown>> {
    return this.getJson(`/nonce/${encodeURIComponent(address)}`);
  }

  async getReceipt(txHash: string): Promise<Receipt> {
    return this.getJson(`/receipt/${encodeURIComponent(txHash)}`);
  }

  async getAddressSummary(address: string): Promise<AddressSummary> {
    return this.jsonRpc("qsc_addressSummary", { address });
  }

  async getGasEstimate(): Promise<GasEstimate> {
    return this.jsonRpc("qsc_gasEstimate", {});
  }

  async getBaseFee(): Promise<Record<string, unknown>> {
    return this.jsonRpc("qsc_baseFee", {});
  }

  buildJsonRpcRequest(method: string, params: Record<string, unknown> = {}, id = 1): Record<string, unknown> {
    return {
      jsonrpc: "2.0",
      id,
      method,
      params,
    };
  }

  async jsonRpc(method: string, params: Record<string, unknown> = {}, id = 1): Promise<Record<string, unknown>> {
    return this.postJson("", this.buildJsonRpcRequest(method, params, id));
  }

  async rawRpc(method: string, params: Record<string, unknown> = {}, id = 1): Promise<Record<string, unknown>> {
    return this.jsonRpc(method, params, id);
  }

  async rawGet(pathname: string, useChainBase = false): Promise<Record<string, unknown>> {
    const root = useChainBase ? this.baseUrl : this.rpcUrl;
    return this.getAbsoluteJson(`${root}${pathname.startsWith("/") ? pathname : `/${pathname}`}`);
  }

  async rawPost(pathname: string, body: Record<string, unknown>, useChainBase = false): Promise<Record<string, unknown>> {
    const root = useChainBase ? this.baseUrl : this.rpcUrl;
    return this.postAbsoluteJson(`${root}${pathname.startsWith("/") ? pathname : `/${pathname}`}`, body);
  }

  buildWsRequest(method: string, params: Record<string, unknown> = {}, id = 1): Record<string, unknown> {
    return this.buildJsonRpcRequest(method, params, id);
  }

  buildAuthHeaders(extra: Record<string, string> = {}): Record<string, string> {
    return {
      ...(this.jwt ? { Authorization: `Bearer ${this.jwt}` } : {}),
      ...this.headers,
      ...extra,
    };
  }

  getWsConnectionInfo(): { url?: string; headers: Record<string, string> } {
    return {
      url: this.wsUrl,
      headers: this.buildAuthHeaders(),
    };
  }

  async resolveName(name: string): Promise<NameServiceRecord> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/resolve/${encodeURIComponent(name)}`);
  }

  async lookupName(name: string): Promise<NameServiceRecord> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/lookup/${encodeURIComponent(name)}`);
  }

  async isNameAvailable(name: string): Promise<NameServiceRecord> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/available/${encodeURIComponent(name)}`);
  }

  async getNamesByOwner(address: string): Promise<NameServiceRecord> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/by-owner/${encodeURIComponent(address)}`);
  }

  async reverseResolveName(address: string): Promise<NameServiceRecord> {
    return this.getAbsoluteJson(`${this.baseUrl}/names/reverse/${encodeURIComponent(address)}`);
  }

  async queryContract(contract: string, method: string, args: Record<string, unknown> = {}): Promise<Record<string, unknown>> {
    const encodedArgs = encodeURIComponent(JSON.stringify(args));
    return this.getAbsoluteJson(
      `${this.baseUrl}/query?contract=${encodeURIComponent(contract)}&method=${encodeURIComponent(method)}&args=${encodedArgs}`
    );
  }

  async simulate(call: Record<string, unknown>): Promise<SimulationResult> {
    return this.postJson("/simulate", call);
  }

  async sendCall(call: Record<string, unknown>): Promise<SubmittedCall> {
    return this.postJson("/call", call);
  }

  withPaymaster(call: CanonicalCall, paymaster: string): CanonicalCall {
    return {
      ...call,
      paymaster,
    };
  }

  async sendSignedCall(call: CanonicalCall, signer: { signCanonicalPayload(payloadJson: string): Promise<Record<string, unknown>> }): Promise<SubmittedCall> {
    const payloadJson = JSON.stringify(call);
    const signature = await signer.signCanonicalPayload(payloadJson);
    return this.sendCall({
      ...call,
      ...signature,
    });
  }

  async sendSponsoredCall(
    call: CanonicalCall,
    paymaster: string,
    signer: { signCanonicalPayload(payloadJson: string): Promise<Record<string, unknown>> }
  ): Promise<SubmittedCall> {
    return this.sendSignedCall(this.withPaymaster(call, paymaster), signer);
  }

  buildForwarderCall(
    forwarderContract: string,
    args: Record<string, unknown>,
    options: { paymaster?: string } = {}
  ): CanonicalCall {
    const call: CanonicalCall = {
      contract: forwarderContract,
      method: "forward",
      args,
    };
    return options.paymaster ? this.withPaymaster(call, options.paymaster) : call;
  }

  buildContractCall(
    contract: string,
    method: string,
    args: Record<string, unknown> = {},
    options: { paymaster?: string } = {}
  ): CanonicalCall {
    const call: CanonicalCall = { contract, method, args };
    return options.paymaster ? this.withPaymaster(call, options.paymaster) : call;
  }

  async callContract(
    contract: string,
    method: string,
    args: Record<string, unknown> = {},
    options: { paymaster?: string } = {}
  ): Promise<SubmittedCall> {
    return this.sendCall(this.buildContractCall(contract, method, args, options));
  }

  async waitForReceipt(txHash: string, maxAttempts = 12, delayMs = 1_000): Promise<Receipt> {
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      try {
        return await this.getReceipt(txHash);
      } catch (error) {
        if (!(error instanceof Error) || !error.message.includes("404")) {
          throw error;
        }
      }
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
    throw new Error("Receipt not available yet.");
  }

  private async getJson(pathname: string): Promise<Record<string, unknown>> {
    return this.getAbsoluteJson(`${this.rpcUrl}${pathname}`);
  }

  private async getAbsoluteJson(url: string): Promise<Record<string, unknown>> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(url, {
        method: "GET",
        headers: this.buildAuthHeaders({ accept: "application/json" }),
        signal: controller.signal,
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return (await response.json()) as Record<string, unknown>;
    } finally {
      clearTimeout(timer);
    }
  }

  private async postJson(pathname: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    return this.postAbsoluteJson(`${this.rpcUrl}${pathname}`, body);
  }

  private async postAbsoluteJson(url: string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(url, {
        method: "POST",
        headers: this.buildAuthHeaders({
          "content-type": "application/json",
          accept: "application/json",
        }),
        body: JSON.stringify(body),
        signal: controller.signal,
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return (await response.json()) as Record<string, unknown>;
    } finally {
      clearTimeout(timer);
    }
  }
}

export function createClient(config: DilithiumClientConfig): DilithiumClient {
  return new DilithiumClient(config);
}

export class DilithiumGasSponsorConnector {
  readonly client: DilithiumClient;
  readonly sponsorContract: string;
  readonly paymaster?: string;

  constructor(config: GasSponsorConnectorConfig) {
    this.client = config.client;
    this.sponsorContract = config.sponsorContract;
    this.paymaster = config.paymaster;
  }

  buildAcceptQuery(user: string, contract: string, method: string): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "accept",
      args: { user, contract, method },
    };
  }

  buildMaxGasPerUserQuery(): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "max_gas_per_user",
      args: {},
    };
  }

  buildRemainingQuotaQuery(user: string): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "remaining_quota",
      args: { user },
    };
  }

  buildSponsorTokenQuery(): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "sponsor_token",
      args: {},
    };
  }

  buildFundCall(amount: number): CanonicalCall {
    return {
      contract: this.sponsorContract,
      method: "fund",
      args: { amount },
    };
  }

  applyPaymaster(call: CanonicalCall): CanonicalCall {
    return this.paymaster ? this.client.withPaymaster(call, this.paymaster) : call;
  }

  async simulateSponsoredCall(call: CanonicalCall): Promise<SimulationResult> {
    return this.client.simulate(this.applyPaymaster(call));
  }

  async sendSponsoredCall(
    call: CanonicalCall,
    signer: { signCanonicalPayload(payloadJson: string): Promise<Record<string, unknown>> }
  ): Promise<SubmittedCall> {
    return this.client.sendSignedCall(this.applyPaymaster(call), signer);
  }
}

export function createGasSponsorConnector(config: GasSponsorConnectorConfig): DilithiumGasSponsorConnector {
  return new DilithiumGasSponsorConnector(config);
}

export class DilithiumMessagingConnector {
  readonly client: DilithiumClient;
  readonly messagingContract: string;
  readonly paymaster?: string;

  constructor(config: MessagingConnectorConfig) {
    this.client = config.client;
    this.messagingContract = config.messagingContract;
    this.paymaster = config.paymaster;
  }

  buildSendMessageCall(destChain: string, payload: Record<string, unknown> | string): CanonicalCall {
    return this.applyPaymaster({
      contract: this.messagingContract,
      method: "send_message",
      args: { dest_chain: destChain, payload },
    });
  }

  buildReceiveMessageCall(
    sourceChain: string,
    sourceContract: string,
    payload: Record<string, unknown> | string
  ): CanonicalCall {
    return this.applyPaymaster({
      contract: this.messagingContract,
      method: "receive_message",
      args: { source_chain: sourceChain, source_contract: sourceContract, payload },
    });
  }

  buildOutboxQuery(): CanonicalCall {
    return {
      contract: this.messagingContract,
      method: "outbox",
      args: {},
    };
  }

  buildInboxQuery(): CanonicalCall {
    return {
      contract: this.messagingContract,
      method: "inbox",
      args: {},
    };
  }

  async queryOutbox(): Promise<Record<string, unknown>> {
    return this.client.queryContract(this.messagingContract, "outbox", {});
  }

  async queryInbox(): Promise<Record<string, unknown>> {
    return this.client.queryContract(this.messagingContract, "inbox", {});
  }

  async sendMessage(
    destChain: string,
    payload: Record<string, unknown> | string,
    signer: { signCanonicalPayload(payloadJson: string): Promise<Record<string, unknown>> }
  ): Promise<SubmittedCall> {
    return this.client.sendSignedCall(this.buildSendMessageCall(destChain, payload), signer);
  }

  applyPaymaster(call: CanonicalCall): CanonicalCall {
    return this.paymaster ? this.client.withPaymaster(call, this.paymaster) : call;
  }
}

export function createMessagingConnector(config: MessagingConnectorConfig): DilithiumMessagingConnector {
  return new DilithiumMessagingConnector(config);
}
