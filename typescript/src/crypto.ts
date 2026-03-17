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
  generate_mnemonic: () => string;
  validate_mnemonic: (mnemonic: string) => void;
  recover_hd_wallet?: (mnemonic: string) => DilithiaAccount;
  recover_hd_wallet_account?: (mnemonic: string, accountIndex: number) => DilithiaAccount;
  create_wallet_file?: (password: string) => { mnemonic: string; wallet_file: WalletFile };
  create_hd_wallet_file_from_mnemonic?: (mnemonic: string, password: string) => DilithiaAccount;
  create_hd_wallet_account_from_mnemonic?: (
    mnemonic: string,
    password: string,
    accountIndex: number
  ) => DilithiaAccount;
  recover_wallet_file?: (
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
  address_from_public_key?: (publicKeyHex: string) => string;
  validate_address?: (addr: string) => string;
  address_from_pk_checksummed?: (publicKeyHex: string) => string;
  address_with_checksum?: (rawAddr: string) => string;
  validate_public_key?: (publicKeyHex: string) => void;
  validate_secret_key?: (secretKeyHex: string) => void;
  validate_signature?: (signatureHex: string) => void;
  sign_message?: (secretKeyHex: string, message: string) => DilithiaSignature;
  verify_message?: (publicKeyHex: string, message: string, signatureHex: string) => boolean;
  keygen?: () => { secret_key: string; public_key: string; address: string };
  keygen_from_seed?: (seedHex: string) => { secret_key: string; public_key: string; address: string };
  seed_from_mnemonic?: (mnemonic: string) => string;
  derive_child_seed?: (parentSeedHex: string, index: number) => string;
  constant_time_eq?: (aHex: string, bHex: string) => boolean;
  hash_hex?: (dataHex: string) => string;
  set_hash_alg?: (alg: string) => void;
  current_hash_alg?: () => string;
  hash_len_hex?: () => number;
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
        return module.generate_mnemonic();
      },
      async validateMnemonic(mnemonic: string) {
        module.validate_mnemonic(mnemonic);
      },
      async recoverHdWallet(mnemonic: string) {
        if (!module.recover_hd_wallet) {
          throw new Error("Native crypto bridge does not expose recover_hd_wallet.");
        }
        return normalizeNativeAccount(module.recover_hd_wallet(mnemonic));
      },
      async recoverHdWalletAccount(mnemonic: string, accountIndex: number) {
        if (!module.recover_hd_wallet_account) {
          throw new Error("Native crypto bridge does not expose recover_hd_wallet_account.");
        }
        return normalizeNativeAccount(module.recover_hd_wallet_account(mnemonic, accountIndex));
      },
      async createHdWalletFileFromMnemonic(mnemonic: string, password: string) {
        if (!module.create_hd_wallet_file_from_mnemonic) {
          throw new Error("Native crypto bridge does not expose create_hd_wallet_file_from_mnemonic.");
        }
        return normalizeNativeAccount(module.create_hd_wallet_file_from_mnemonic(mnemonic, password));
      },
      async createHdWalletAccountFromMnemonic(mnemonic: string, password: string, accountIndex: number) {
        if (!module.create_hd_wallet_account_from_mnemonic) {
          throw new Error("Native crypto bridge does not expose create_hd_wallet_account_from_mnemonic.");
        }
        return normalizeNativeAccount(
          module.create_hd_wallet_account_from_mnemonic(mnemonic, password, accountIndex)
        );
      },
      async recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string) {
        if (!module.recover_wallet_file) {
          throw new Error("Native crypto bridge does not expose recover_wallet_file.");
        }
        return normalizeNativeAccount(
          module.recover_wallet_file(
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
        if (!module.address_from_public_key) {
          throw new Error("Native crypto bridge does not expose address_from_public_key.");
        }
        return module.address_from_public_key(publicKeyHex);
      },
      async validateAddress(addr: string) {
        if (!module.validate_address) {
          throw new Error("Native crypto bridge does not expose validate_address.");
        }
        return module.validate_address(addr);
      },
      async addressFromPkChecksummed(publicKeyHex: string) {
        if (!module.address_from_pk_checksummed) {
          throw new Error("Native crypto bridge does not expose address_from_pk_checksummed.");
        }
        return module.address_from_pk_checksummed(publicKeyHex);
      },
      async addressWithChecksum(rawAddr: string) {
        if (!module.address_with_checksum) {
          throw new Error("Native crypto bridge does not expose address_with_checksum.");
        }
        return module.address_with_checksum(rawAddr);
      },
      async validatePublicKey(publicKeyHex: string) {
        if (!module.validate_public_key) {
          throw new Error("Native crypto bridge does not expose validate_public_key.");
        }
        module.validate_public_key(publicKeyHex);
      },
      async validateSecretKey(secretKeyHex: string) {
        if (!module.validate_secret_key) {
          throw new Error("Native crypto bridge does not expose validate_secret_key.");
        }
        module.validate_secret_key(secretKeyHex);
      },
      async validateSignature(signatureHex: string) {
        if (!module.validate_signature) {
          throw new Error("Native crypto bridge does not expose validate_signature.");
        }
        module.validate_signature(signatureHex);
      },
      async signMessage(secretKeyHex: string, message: string) {
        if (!module.sign_message) {
          throw new Error("Native crypto bridge does not expose sign_message.");
        }
        return module.sign_message(secretKeyHex, message);
      },
      async verifyMessage(publicKeyHex: string, message: string, signatureHex: string) {
        if (!module.verify_message) {
          throw new Error("Native crypto bridge does not expose verify_message.");
        }
        return module.verify_message(publicKeyHex, message, signatureHex);
      },
      async keygen() {
        if (!module.keygen) {
          throw new Error("Native crypto bridge does not expose keygen.");
        }
        const raw = module.keygen();
        return { secretKey: raw.secret_key, publicKey: raw.public_key, address: raw.address };
      },
      async keygenFromSeed(seedHex: string) {
        if (!module.keygen_from_seed) {
          throw new Error("Native crypto bridge does not expose keygen_from_seed.");
        }
        const raw = module.keygen_from_seed(seedHex);
        return { secretKey: raw.secret_key, publicKey: raw.public_key, address: raw.address };
      },
      async seedFromMnemonic(mnemonic: string) {
        if (!module.seed_from_mnemonic) {
          throw new Error("Native crypto bridge does not expose seed_from_mnemonic.");
        }
        return module.seed_from_mnemonic(mnemonic);
      },
      async deriveChildSeed(parentSeedHex: string, index: number) {
        if (!module.derive_child_seed) {
          throw new Error("Native crypto bridge does not expose derive_child_seed.");
        }
        return module.derive_child_seed(parentSeedHex, index);
      },
      async constantTimeEq(aHex: string, bHex: string) {
        if (!module.constant_time_eq) {
          throw new Error("Native crypto bridge does not expose constant_time_eq.");
        }
        return module.constant_time_eq(aHex, bHex);
      },
      async hashHex(dataHex: string) {
        if (!module.hash_hex) {
          throw new Error("Native crypto bridge does not expose hash_hex.");
        }
        return module.hash_hex(dataHex);
      },
      async setHashAlg(alg: string) {
        if (!module.set_hash_alg) {
          throw new Error("Native crypto bridge does not expose set_hash_alg.");
        }
        module.set_hash_alg(alg);
      },
      async currentHashAlg() {
        if (!module.current_hash_alg) {
          throw new Error("Native crypto bridge does not expose current_hash_alg.");
        }
        return module.current_hash_alg();
      },
      async hashLenHex() {
        if (!module.hash_len_hex) {
          throw new Error("Native crypto bridge does not expose hash_len_hex.");
        }
        return module.hash_len_hex();
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
        return module.generate_mnemonic();
      },
      validateMnemonic(mnemonic: string) {
        module.validate_mnemonic(mnemonic);
      },
      recoverHdWallet(mnemonic: string) {
        if (!module.recover_hd_wallet) {
          throw new Error("Native crypto bridge does not expose recover_hd_wallet.");
        }
        return normalizeNativeAccount(module.recover_hd_wallet(mnemonic));
      },
      recoverHdWalletAccount(mnemonic: string, accountIndex: number) {
        if (!module.recover_hd_wallet_account) {
          throw new Error("Native crypto bridge does not expose recover_hd_wallet_account.");
        }
        return normalizeNativeAccount(module.recover_hd_wallet_account(mnemonic, accountIndex));
      },
      createHdWalletFileFromMnemonic(mnemonic: string, password: string) {
        if (!module.create_hd_wallet_file_from_mnemonic) {
          throw new Error("Native crypto bridge does not expose create_hd_wallet_file_from_mnemonic.");
        }
        return normalizeNativeAccount(module.create_hd_wallet_file_from_mnemonic(mnemonic, password));
      },
      createHdWalletAccountFromMnemonic(mnemonic: string, password: string, accountIndex: number) {
        if (!module.create_hd_wallet_account_from_mnemonic) {
          throw new Error("Native crypto bridge does not expose create_hd_wallet_account_from_mnemonic.");
        }
        return normalizeNativeAccount(
          module.create_hd_wallet_account_from_mnemonic(mnemonic, password, accountIndex)
        );
      },
      recoverWalletFile(walletFile: WalletFile, mnemonic: string, password: string) {
        if (!module.recover_wallet_file) {
          throw new Error("Native crypto bridge does not expose recover_wallet_file.");
        }
        return normalizeNativeAccount(
          module.recover_wallet_file(
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
        if (!module.address_from_public_key) {
          throw new Error("Native crypto bridge does not expose address_from_public_key.");
        }
        return module.address_from_public_key(publicKeyHex);
      },
      validateAddress(addr: string) {
        if (!module.validate_address) {
          throw new Error("Native crypto bridge does not expose validate_address.");
        }
        return module.validate_address(addr);
      },
      addressFromPkChecksummed(publicKeyHex: string) {
        if (!module.address_from_pk_checksummed) {
          throw new Error("Native crypto bridge does not expose address_from_pk_checksummed.");
        }
        return module.address_from_pk_checksummed(publicKeyHex);
      },
      addressWithChecksum(rawAddr: string) {
        if (!module.address_with_checksum) {
          throw new Error("Native crypto bridge does not expose address_with_checksum.");
        }
        return module.address_with_checksum(rawAddr);
      },
      validatePublicKey(publicKeyHex: string) {
        if (!module.validate_public_key) {
          throw new Error("Native crypto bridge does not expose validate_public_key.");
        }
        module.validate_public_key(publicKeyHex);
      },
      validateSecretKey(secretKeyHex: string) {
        if (!module.validate_secret_key) {
          throw new Error("Native crypto bridge does not expose validate_secret_key.");
        }
        module.validate_secret_key(secretKeyHex);
      },
      validateSignature(signatureHex: string) {
        if (!module.validate_signature) {
          throw new Error("Native crypto bridge does not expose validate_signature.");
        }
        module.validate_signature(signatureHex);
      },
      signMessage(secretKeyHex: string, message: string) {
        if (!module.sign_message) {
          throw new Error("Native crypto bridge does not expose sign_message.");
        }
        return module.sign_message(secretKeyHex, message);
      },
      verifyMessage(publicKeyHex: string, message: string, signatureHex: string) {
        if (!module.verify_message) {
          throw new Error("Native crypto bridge does not expose verify_message.");
        }
        return module.verify_message(publicKeyHex, message, signatureHex);
      },
      keygen() {
        if (!module.keygen) {
          throw new Error("Native crypto bridge does not expose keygen.");
        }
        const raw = module.keygen();
        return { secretKey: raw.secret_key, publicKey: raw.public_key, address: raw.address };
      },
      keygenFromSeed(seedHex: string) {
        if (!module.keygen_from_seed) {
          throw new Error("Native crypto bridge does not expose keygen_from_seed.");
        }
        const raw = module.keygen_from_seed(seedHex);
        return { secretKey: raw.secret_key, publicKey: raw.public_key, address: raw.address };
      },
      seedFromMnemonic(mnemonic: string) {
        if (!module.seed_from_mnemonic) {
          throw new Error("Native crypto bridge does not expose seed_from_mnemonic.");
        }
        return module.seed_from_mnemonic(mnemonic);
      },
      deriveChildSeed(parentSeedHex: string, index: number) {
        if (!module.derive_child_seed) {
          throw new Error("Native crypto bridge does not expose derive_child_seed.");
        }
        return module.derive_child_seed(parentSeedHex, index);
      },
      constantTimeEq(aHex: string, bHex: string) {
        if (!module.constant_time_eq) {
          throw new Error("Native crypto bridge does not expose constant_time_eq.");
        }
        return module.constant_time_eq(aHex, bHex);
      },
      hashHex(dataHex: string) {
        if (!module.hash_hex) {
          throw new Error("Native crypto bridge does not expose hash_hex.");
        }
        return module.hash_hex(dataHex);
      },
      setHashAlg(alg: string) {
        if (!module.set_hash_alg) {
          throw new Error("Native crypto bridge does not expose set_hash_alg.");
        }
        module.set_hash_alg(alg);
      },
      currentHashAlg() {
        if (!module.current_hash_alg) {
          throw new Error("Native crypto bridge does not expose current_hash_alg.");
        }
        return module.current_hash_alg();
      },
      hashLenHex() {
        if (!module.hash_len_hex) {
          throw new Error("Native crypto bridge does not expose hash_len_hex.");
        }
        return module.hash_len_hex();
      },
    };
  } catch {
    return null;
  }
}
