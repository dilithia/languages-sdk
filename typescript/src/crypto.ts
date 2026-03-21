import { createRequire } from "module";

export type WalletFile = Record<string, unknown>;

export type DilithiaAccount = {
  address: string;
  publicKey: string;
  secretKey: string;
  accountIndex: number;
  walletFile?: WalletFile | null;
};

export type DilithiaSignature = {
  algorithm: string;
  signature: string;
};

export type DilithiaKeypair = {
  secretKey: string;
  publicKey: string;
  address: string;
};

export interface DilithiaCryptoAdapter {
  generateMnemonic(): Promise<string>;
  validateMnemonic(mnemonic: string): Promise<void>;
  recoverHdWallet(mnemonic: string): Promise<DilithiaAccount>;
  recoverHdWalletAccount(mnemonic: string, accountIndex: number): Promise<DilithiaAccount>;
  createHdWalletFileFromMnemonic(mnemonic: string, password: string): Promise<DilithiaAccount>;
  createHdWalletAccountFromMnemonic(
    mnemonic: string,
    password: string,
    accountIndex: number
  ): Promise<DilithiaAccount>;
  recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string): Promise<DilithiaAccount>;
  addressFromPublicKey(publicKeyHex: string): Promise<string>;
  validateAddress(addr: string): Promise<string>;
  addressFromPkChecksummed(publicKeyHex: string): Promise<string>;
  addressWithChecksum(rawAddr: string): Promise<string>;
  validatePublicKey(publicKeyHex: string): Promise<void>;
  validateSecretKey(secretKeyHex: string): Promise<void>;
  validateSignature(signatureHex: string): Promise<void>;
  signMessage(secretKeyHex: string, message: string): Promise<DilithiaSignature>;
  verifyMessage(publicKeyHex: string, message: string, signatureHex: string): Promise<boolean>;
  keygen(): Promise<DilithiaKeypair>;
  keygenFromSeed(seedHex: string): Promise<DilithiaKeypair>;
  seedFromMnemonic(mnemonic: string): Promise<string>;
  deriveChildSeed(parentSeedHex: string, index: number): Promise<string>;
  constantTimeEq(aHex: string, bHex: string): Promise<boolean>;
  hashHex(dataHex: string): Promise<string>;
  setHashAlg(alg: string): Promise<void>;
  currentHashAlg(): Promise<string>;
  hashLenHex(): Promise<number>;
}

export interface SyncDilithiaCryptoAdapter {
  generateMnemonic(): string;
  validateMnemonic(mnemonic: string): void;
  recoverHdWallet(mnemonic: string): DilithiaAccount;
  recoverHdWalletAccount(mnemonic: string, accountIndex: number): DilithiaAccount;
  createHdWalletFileFromMnemonic(mnemonic: string, password: string): DilithiaAccount;
  createHdWalletAccountFromMnemonic(
    mnemonic: string,
    password: string,
    accountIndex: number
  ): DilithiaAccount;
  recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string): DilithiaAccount;
  addressFromPublicKey(publicKeyHex: string): string;
  validateAddress(addr: string): string;
  addressFromPkChecksummed(publicKeyHex: string): string;
  addressWithChecksum(rawAddr: string): string;
  validatePublicKey(publicKeyHex: string): void;
  validateSecretKey(secretKeyHex: string): void;
  validateSignature(signatureHex: string): void;
  signMessage(secretKeyHex: string, message: string): DilithiaSignature;
  verifyMessage(publicKeyHex: string, message: string, signatureHex: string): boolean;
  keygen(): DilithiaKeypair;
  keygenFromSeed(seedHex: string): DilithiaKeypair;
  seedFromMnemonic(mnemonic: string): string;
  deriveChildSeed(parentSeedHex: string, index: number): string;
  constantTimeEq(aHex: string, bHex: string): boolean;
  hashHex(dataHex: string): string;
  setHashAlg(alg: string): void;
  currentHashAlg(): string;
  hashLenHex(): number;
}

type NativeModuleShape = {
  generateMnemonic: () => string;
  validateMnemonic: (mnemonic: string) => void;
  recoverHdWallet?: (mnemonic: string) => DilithiaAccount;
  recoverHdWalletAccount?: (mnemonic: string, accountIndex: number) => DilithiaAccount;
  createWalletFile?: (password: string) => { mnemonic: string; walletFile: WalletFile };
  createHdWalletFileFromMnemonic?: (mnemonic: string, password: string) => DilithiaAccount;
  createHdWalletAccountFromMnemonic?: (
    mnemonic: string,
    password: string,
    accountIndex: number
  ) => DilithiaAccount;
  recoverWalletFile?: (
    version: number,
    address: string,
    publicKey: string,
    encryptedSk: string,
    nonce: string,
    tag: string,
    accountIndex: number | null,
    mnemonic: string,
    password: string
  ) => DilithiaAccount;
  addressFromPublicKey?: (publicKeyHex: string) => string;
  validateAddress?: (addr: string) => string;
  addressFromPkChecksummed?: (publicKeyHex: string) => string;
  addressWithChecksum?: (rawAddr: string) => string;
  validatePublicKey?: (publicKeyHex: string) => void;
  validateSecretKey?: (secretKeyHex: string) => void;
  validateSignature?: (signatureHex: string) => void;
  signMessage?: (secretKeyHex: string, message: string) => DilithiaSignature;
  verifyMessage?: (publicKeyHex: string, message: string, signatureHex: string) => boolean;
  keygen?: () => { secretKey: string; publicKey: string; address: string };
  keygenFromSeed?: (seedHex: string) => { secretKey: string; publicKey: string; address: string };
  seedFromMnemonic?: (mnemonic: string) => string;
  deriveChildSeed?: (parentSeedHex: string, index: number) => string;
  constantTimeEq?: (aHex: string, bHex: string) => boolean;
  hashHex?: (dataHex: string) => string;
  setHashAlg?: (alg: string) => void;
  currentHashAlg?: () => string;
  hashLenHex?: () => number;
};

function normalizeNativeAccount(account: DilithiaAccount | Record<string, unknown>): DilithiaAccount {
  const source = account as Record<string, unknown>;
  return {
    address: String(source.address ?? ""),
    publicKey: String(source.publicKey ?? source.public_key ?? ""),
    secretKey: String(source.secretKey ?? source.secret_key ?? ""),
    accountIndex: Number(source.accountIndex ?? source.account_index ?? 0),
    walletFile: (source.walletFile ?? source.wallet_file ?? null) as WalletFile | null,
  };
}

export async function loadNativeCryptoAdapter(
  importer: () => Promise<unknown> = () => import("@dilithia/sdk-native")
): Promise<DilithiaCryptoAdapter | null> {
  try {
    const module = (await importer()) as NativeModuleShape;
    return {
      async generateMnemonic() {
        return module.generateMnemonic();
      },
      async validateMnemonic(mnemonic: string) {
        module.validateMnemonic(mnemonic);
      },
      async recoverHdWallet(mnemonic: string) {
        if (!module.recoverHdWallet) {
          throw new Error("Native crypto bridge does not expose recoverHdWallet.");
        }
        return normalizeNativeAccount(module.recoverHdWallet(mnemonic));
      },
      async recoverHdWalletAccount(mnemonic: string, accountIndex: number) {
        if (!module.recoverHdWalletAccount) {
          throw new Error("Native crypto bridge does not expose recoverHdWalletAccount.");
        }
        return normalizeNativeAccount(module.recoverHdWalletAccount(mnemonic, accountIndex));
      },
      async createHdWalletFileFromMnemonic(mnemonic: string, password: string) {
        if (!module.createHdWalletFileFromMnemonic) {
          throw new Error("Native crypto bridge does not expose createHdWalletFileFromMnemonic.");
        }
        return normalizeNativeAccount(module.createHdWalletFileFromMnemonic(mnemonic, password));
      },
      async createHdWalletAccountFromMnemonic(mnemonic: string, password: string, accountIndex: number) {
        if (!module.createHdWalletAccountFromMnemonic) {
          throw new Error("Native crypto bridge does not expose createHdWalletAccountFromMnemonic.");
        }
        return normalizeNativeAccount(
          module.createHdWalletAccountFromMnemonic(mnemonic, password, accountIndex)
        );
      },
      async recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string) {
        if (!module.recoverWalletFile) {
          throw new Error("Native crypto bridge does not expose recoverWalletFile.");
        }
        return normalizeNativeAccount(
          module.recoverWalletFile(
            Number(walletFile.version ?? 1),
            String(walletFile.address ?? ""),
            String(walletFile.public_key ?? walletFile.publicKey ?? ""),
            String(walletFile.encrypted_sk ?? walletFile.encryptedSk ?? ""),
            String(walletFile.nonce ?? ""),
            String(walletFile.tag ?? ""),
            walletFile.account_index !== undefined && walletFile.account_index !== null
              ? Number(walletFile.account_index)
              : null,
            mnemonic,
            password
          )
        );
      },
      async addressFromPublicKey(publicKeyHex: string) {
        if (!module.addressFromPublicKey) {
          throw new Error("Native crypto bridge does not expose addressFromPublicKey.");
        }
        return module.addressFromPublicKey(publicKeyHex);
      },
      async validateAddress(addr: string) {
        if (!module.validateAddress) {
          throw new Error("Native crypto bridge does not expose validateAddress.");
        }
        return module.validateAddress(addr);
      },
      async addressFromPkChecksummed(publicKeyHex: string) {
        if (!module.addressFromPkChecksummed) {
          throw new Error("Native crypto bridge does not expose addressFromPkChecksummed.");
        }
        return module.addressFromPkChecksummed(publicKeyHex);
      },
      async addressWithChecksum(rawAddr: string) {
        if (!module.addressWithChecksum) {
          throw new Error("Native crypto bridge does not expose addressWithChecksum.");
        }
        return module.addressWithChecksum(rawAddr);
      },
      async validatePublicKey(publicKeyHex: string) {
        if (!module.validatePublicKey) {
          throw new Error("Native crypto bridge does not expose validatePublicKey.");
        }
        module.validatePublicKey(publicKeyHex);
      },
      async validateSecretKey(secretKeyHex: string) {
        if (!module.validateSecretKey) {
          throw new Error("Native crypto bridge does not expose validateSecretKey.");
        }
        module.validateSecretKey(secretKeyHex);
      },
      async validateSignature(signatureHex: string) {
        if (!module.validateSignature) {
          throw new Error("Native crypto bridge does not expose validateSignature.");
        }
        module.validateSignature(signatureHex);
      },
      async signMessage(secretKeyHex: string, message: string) {
        if (!module.signMessage) {
          throw new Error("Native crypto bridge does not expose signMessage.");
        }
        return module.signMessage(secretKeyHex, message);
      },
      async verifyMessage(publicKeyHex: string, message: string, signatureHex: string) {
        if (!module.verifyMessage) {
          throw new Error("Native crypto bridge does not expose verifyMessage.");
        }
        return module.verifyMessage(publicKeyHex, message, signatureHex);
      },
      async keygen() {
        if (!module.keygen) {
          throw new Error("Native crypto bridge does not expose keygen.");
        }
        const raw = module.keygen();
        return { secretKey: raw.secretKey, publicKey: raw.publicKey, address: raw.address };
      },
      async keygenFromSeed(seedHex: string) {
        if (!module.keygenFromSeed) {
          throw new Error("Native crypto bridge does not expose keygenFromSeed.");
        }
        const raw = module.keygenFromSeed(seedHex);
        return { secretKey: raw.secretKey, publicKey: raw.publicKey, address: raw.address };
      },
      async seedFromMnemonic(mnemonic: string) {
        if (!module.seedFromMnemonic) {
          throw new Error("Native crypto bridge does not expose seedFromMnemonic.");
        }
        return module.seedFromMnemonic(mnemonic);
      },
      async deriveChildSeed(parentSeedHex: string, index: number) {
        if (!module.deriveChildSeed) {
          throw new Error("Native crypto bridge does not expose deriveChildSeed.");
        }
        return module.deriveChildSeed(parentSeedHex, index);
      },
      async constantTimeEq(aHex: string, bHex: string) {
        if (!module.constantTimeEq) {
          throw new Error("Native crypto bridge does not expose constantTimeEq.");
        }
        return module.constantTimeEq(aHex, bHex);
      },
      async hashHex(dataHex: string) {
        if (!module.hashHex) {
          throw new Error("Native crypto bridge does not expose hashHex.");
        }
        return module.hashHex(dataHex);
      },
      async setHashAlg(alg: string) {
        if (!module.setHashAlg) {
          throw new Error("Native crypto bridge does not expose setHashAlg.");
        }
        module.setHashAlg(alg);
      },
      async currentHashAlg() {
        if (!module.currentHashAlg) {
          throw new Error("Native crypto bridge does not expose currentHashAlg.");
        }
        return module.currentHashAlg();
      },
      async hashLenHex() {
        if (!module.hashLenHex) {
          throw new Error("Native crypto bridge does not expose hashLenHex.");
        }
        return module.hashLenHex();
      },
    };
  } catch {
    return null;
  }
}

export function loadSyncNativeCryptoAdapter(): SyncDilithiaCryptoAdapter | null {
  try {
    const esmRequire = createRequire(import.meta.url);
    const module = esmRequire("@dilithia/sdk-native") as NativeModuleShape;
    return {
      generateMnemonic() {
        return module.generateMnemonic();
      },
      validateMnemonic(mnemonic: string) {
        module.validateMnemonic(mnemonic);
      },
      recoverHdWallet(mnemonic: string) {
        if (!module.recoverHdWallet) {
          throw new Error("Native crypto bridge does not expose recoverHdWallet.");
        }
        return normalizeNativeAccount(module.recoverHdWallet(mnemonic));
      },
      recoverHdWalletAccount(mnemonic: string, accountIndex: number) {
        if (!module.recoverHdWalletAccount) {
          throw new Error("Native crypto bridge does not expose recoverHdWalletAccount.");
        }
        return normalizeNativeAccount(module.recoverHdWalletAccount(mnemonic, accountIndex));
      },
      createHdWalletFileFromMnemonic(mnemonic: string, password: string) {
        if (!module.createHdWalletFileFromMnemonic) {
          throw new Error("Native crypto bridge does not expose createHdWalletFileFromMnemonic.");
        }
        return normalizeNativeAccount(module.createHdWalletFileFromMnemonic(mnemonic, password));
      },
      createHdWalletAccountFromMnemonic(mnemonic: string, password: string, accountIndex: number) {
        if (!module.createHdWalletAccountFromMnemonic) {
          throw new Error("Native crypto bridge does not expose createHdWalletAccountFromMnemonic.");
        }
        return normalizeNativeAccount(
          module.createHdWalletAccountFromMnemonic(mnemonic, password, accountIndex)
        );
      },
      recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string) {
        if (!module.recoverWalletFile) {
          throw new Error("Native crypto bridge does not expose recoverWalletFile.");
        }
        return normalizeNativeAccount(
          module.recoverWalletFile(
            Number(walletFile.version ?? 1),
            String(walletFile.address ?? ""),
            String(walletFile.public_key ?? walletFile.publicKey ?? ""),
            String(walletFile.encrypted_sk ?? walletFile.encryptedSk ?? ""),
            String(walletFile.nonce ?? ""),
            String(walletFile.tag ?? ""),
            walletFile.account_index !== undefined && walletFile.account_index !== null
              ? Number(walletFile.account_index)
              : null,
            mnemonic,
            password
          )
        );
      },
      addressFromPublicKey(publicKeyHex: string) {
        if (!module.addressFromPublicKey) {
          throw new Error("Native crypto bridge does not expose addressFromPublicKey.");
        }
        return module.addressFromPublicKey(publicKeyHex);
      },
      validateAddress(addr: string) {
        if (!module.validateAddress) {
          throw new Error("Native crypto bridge does not expose validateAddress.");
        }
        return module.validateAddress(addr);
      },
      addressFromPkChecksummed(publicKeyHex: string) {
        if (!module.addressFromPkChecksummed) {
          throw new Error("Native crypto bridge does not expose addressFromPkChecksummed.");
        }
        return module.addressFromPkChecksummed(publicKeyHex);
      },
      addressWithChecksum(rawAddr: string) {
        if (!module.addressWithChecksum) {
          throw new Error("Native crypto bridge does not expose addressWithChecksum.");
        }
        return module.addressWithChecksum(rawAddr);
      },
      validatePublicKey(publicKeyHex: string) {
        if (!module.validatePublicKey) {
          throw new Error("Native crypto bridge does not expose validatePublicKey.");
        }
        module.validatePublicKey(publicKeyHex);
      },
      validateSecretKey(secretKeyHex: string) {
        if (!module.validateSecretKey) {
          throw new Error("Native crypto bridge does not expose validateSecretKey.");
        }
        module.validateSecretKey(secretKeyHex);
      },
      validateSignature(signatureHex: string) {
        if (!module.validateSignature) {
          throw new Error("Native crypto bridge does not expose validateSignature.");
        }
        module.validateSignature(signatureHex);
      },
      signMessage(secretKeyHex: string, message: string) {
        if (!module.signMessage) {
          throw new Error("Native crypto bridge does not expose signMessage.");
        }
        return module.signMessage(secretKeyHex, message);
      },
      verifyMessage(publicKeyHex: string, message: string, signatureHex: string) {
        if (!module.verifyMessage) {
          throw new Error("Native crypto bridge does not expose verifyMessage.");
        }
        return module.verifyMessage(publicKeyHex, message, signatureHex);
      },
      keygen() {
        if (!module.keygen) {
          throw new Error("Native crypto bridge does not expose keygen.");
        }
        const raw = module.keygen();
        return { secretKey: raw.secretKey, publicKey: raw.publicKey, address: raw.address };
      },
      keygenFromSeed(seedHex: string) {
        if (!module.keygenFromSeed) {
          throw new Error("Native crypto bridge does not expose keygenFromSeed.");
        }
        const raw = module.keygenFromSeed(seedHex);
        return { secretKey: raw.secretKey, publicKey: raw.publicKey, address: raw.address };
      },
      seedFromMnemonic(mnemonic: string) {
        if (!module.seedFromMnemonic) {
          throw new Error("Native crypto bridge does not expose seedFromMnemonic.");
        }
        return module.seedFromMnemonic(mnemonic);
      },
      deriveChildSeed(parentSeedHex: string, index: number) {
        if (!module.deriveChildSeed) {
          throw new Error("Native crypto bridge does not expose deriveChildSeed.");
        }
        return module.deriveChildSeed(parentSeedHex, index);
      },
      constantTimeEq(aHex: string, bHex: string) {
        if (!module.constantTimeEq) {
          throw new Error("Native crypto bridge does not expose constantTimeEq.");
        }
        return module.constantTimeEq(aHex, bHex);
      },
      hashHex(dataHex: string) {
        if (!module.hashHex) {
          throw new Error("Native crypto bridge does not expose hashHex.");
        }
        return module.hashHex(dataHex);
      },
      setHashAlg(alg: string) {
        if (!module.setHashAlg) {
          throw new Error("Native crypto bridge does not expose setHashAlg.");
        }
        module.setHashAlg(alg);
      },
      currentHashAlg() {
        if (!module.currentHashAlg) {
          throw new Error("Native crypto bridge does not expose currentHashAlg.");
        }
        return module.currentHashAlg();
      },
      hashLenHex() {
        if (!module.hashLenHex) {
          throw new Error("Native crypto bridge does not expose hashLenHex.");
        }
        return module.hashLenHex();
      },
    };
  } catch {
    return null;
  }
}
