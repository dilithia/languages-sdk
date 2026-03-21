// ── Branded types ────────────────────────────────────────────────────────────

/**
 * Branded string type for Dilithia addresses.
 *
 * Use `Address.of(raw)` to create an instance from a plain string.
 * Branded types prevent accidental mixing of semantically different strings
 * at compile time while remaining plain strings at runtime.
 */
export type Address = string & { readonly __brand: "Address" };

/** Factory for {@link Address} branded strings. */
export const Address = {
  /** Wrap a plain string as a branded {@link Address}. */
  of(value: string): Address {
    return value as Address;
  },
};

/**
 * Branded string type for transaction hashes.
 *
 * Use `TxHash.of(raw)` to create an instance from a plain string.
 */
export type TxHash = string & { readonly __brand: "TxHash" };

/** Factory for {@link TxHash} branded strings. */
export const TxHash = {
  /** Wrap a plain string as a branded {@link TxHash}. */
  of(value: string): TxHash {
    return value as TxHash;
  },
};

/**
 * Branded string type for public keys.
 *
 * Use `PublicKey.of(raw)` to create an instance from a plain string.
 */
export type PublicKey = string & { readonly __brand: "PublicKey" };

/** Factory for {@link PublicKey} branded strings. */
export const PublicKey = {
  /** Wrap a plain string as a branded {@link PublicKey}. */
  of(value: string): PublicKey {
    return value as PublicKey;
  },
};

/**
 * Branded string type for secret (private) keys.
 *
 * Use `SecretKey.of(raw)` to create an instance from a plain string.
 */
export type SecretKey = string & { readonly __brand: "SecretKey" };

/** Factory for {@link SecretKey} branded strings. */
export const SecretKey = {
  /** Wrap a plain string as a branded {@link SecretKey}. */
  of(value: string): SecretKey {
    return value as SecretKey;
  },
};

// ── TokenAmount ──────────────────────────────────────────────────────────────

/**
 * Arbitrary-precision token amount backed by {@link BigInt}.
 *
 * JavaScript `number` loses precision beyond 2^53, which is far below the
 * 10^18 range used by Dilithia's native token. `TokenAmount` stores the raw
 * integer and provides formatting helpers.
 *
 * @example
 * ```ts
 * const amount = TokenAmount.dili("1.5");   // 1_500_000_000_000_000_000n
 * const raw    = TokenAmount.fromRaw(1500000000000000000n);
 * console.log(raw.formatted()); // "1.5"
 * ```
 */
export class TokenAmount {
  /** Number of decimal places for the native DILI token. */
  static readonly DILI_DECIMALS = 18;

  /** The raw integer amount (smallest unit). */
  readonly value: bigint;

  /** The number of decimal places this amount uses. */
  readonly decimals: number;

  private constructor(value: bigint, decimals: number) {
    this.value = value;
    this.decimals = decimals;
  }

  /**
   * Create a {@link TokenAmount} from a human-readable DILI value.
   *
   * Accepts strings like `"1.5"`, integers, or bigints.
   *
   * @param value - The human-readable token amount.
   * @returns A new {@link TokenAmount} with 18 decimals.
   */
  static dili(value: string | number | bigint): TokenAmount {
    const decimals = TokenAmount.DILI_DECIMALS;
    if (typeof value === "bigint") {
      return new TokenAmount(value * 10n ** BigInt(decimals), decimals);
    }
    const str = String(value);
    const dotIndex = str.indexOf(".");
    if (dotIndex === -1) {
      return new TokenAmount(BigInt(str) * 10n ** BigInt(decimals), decimals);
    }
    const wholePart = str.slice(0, dotIndex);
    let fracPart = str.slice(dotIndex + 1);
    if (fracPart.length > decimals) {
      fracPart = fracPart.slice(0, decimals);
    } else {
      fracPart = fracPart.padEnd(decimals, "0");
    }
    const raw = BigInt(wholePart) * 10n ** BigInt(decimals) + BigInt(fracPart);
    return new TokenAmount(raw, decimals);
  }

  /**
   * Create a {@link TokenAmount} from a raw integer (smallest unit).
   *
   * @param raw      - The raw integer amount.
   * @param decimals - Number of decimal places (defaults to 18).
   * @returns A new {@link TokenAmount}.
   */
  static fromRaw(raw: bigint, decimals = TokenAmount.DILI_DECIMALS): TokenAmount {
    return new TokenAmount(raw, decimals);
  }

  /** Return the raw BigInt value (smallest unit). */
  toRaw(): bigint {
    return this.value;
  }

  /**
   * Format the amount as a human-readable decimal string.
   *
   * Trailing zeros after the decimal point are stripped.
   * If the fractional part is all zeros, only the whole part is returned.
   */
  formatted(): string {
    const divisor = 10n ** BigInt(this.decimals);
    const whole = this.value / divisor;
    const frac = this.value % divisor;
    if (frac === 0n) {
      return whole.toString();
    }
    const fracStr = frac.toString().padStart(this.decimals, "0").replace(/0+$/, "");
    return `${whole}.${fracStr}`;
  }

  /** Equivalent to {@link formatted}. */
  toString(): string {
    return this.formatted();
  }
}

// ── Response types ───────────────────────────────────────────────────────────

/** Balance query result. */
export interface Balance {
  /** The queried address. */
  address: Address;
  /** The balance as a {@link TokenAmount}. */
  balance: TokenAmount;
  /** The raw balance as a BigInt (smallest unit). */
  raw: bigint;
}

/** Nonce query result. */
export interface Nonce {
  /** The queried address. */
  address: Address;
  /** The next available nonce for the address. */
  nextNonce: number;
}

/** Transaction receipt. */
export interface Receipt {
  /** The hash of the transaction. */
  txHash: TxHash;
  /** The block height at which the transaction was included. */
  blockHeight: number;
  /** Execution status (e.g. "success", "revert"). */
  status: string;
  /** Execution result data, if any. */
  result?: unknown;
  /** Error message, if the transaction reverted. */
  error?: string;
  /** Gas consumed by the transaction. */
  gasUsed: number;
  /** Fee paid for execution. */
  feePaid: number;
}

/** Network information. */
export interface NetworkInfo {
  /** The chain identifier. */
  chainId: string;
  /** Current block height. */
  blockHeight: number;
  /** Current base fee. */
  baseFee: number;
}

/** Gas estimate for a transaction. */
export interface GasEstimate {
  /** Maximum gas the transaction may consume. */
  gasLimit: number;
  /** Current base fee per gas unit. */
  baseFee: number;
  /** Estimated total cost (gasLimit * baseFee). */
  estimatedCost: number;
}

/** Result of a read-only contract query. */
export interface QueryResult {
  /** The returned value from the contract query. */
  value: unknown;
}

/** A name-service record. */
export interface NameRecord {
  /** The registered name. */
  name: string;
  /** The address the name resolves to. */
  address: Address;
}

/** ABI metadata for a deployed contract. */
export interface ContractAbi {
  /** The contract identifier. */
  contract: string;
  /** The list of callable methods. */
  methods: { name: string; mutates: boolean; hasArgs: boolean }[];
}

/** Result of submitting a transaction. */
export interface SubmitResult {
  /** Whether the node accepted the transaction. */
  accepted: boolean;
  /** The transaction hash assigned by the node. */
  txHash: TxHash;
}

// ── Signer interface ─────────────────────────────────────────────────────────

/**
 * Interface for signing canonical payloads before submission.
 *
 * Implementations must produce a JSON-compatible signature envelope
 * containing `alg`, `pk`, and `sig` fields.
 */
export interface DilithiaSigner {
  /**
   * Sign a canonical JSON payload.
   *
   * @param payloadJson - The deterministically-serialized JSON string to sign.
   * @returns A signature envelope with algorithm, public key, and signature.
   */
  signCanonicalPayload(payloadJson: string): Promise<{ alg: string; pk: string; sig: string }>;
}
