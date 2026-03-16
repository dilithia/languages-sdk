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
  signMessage(secretKeyHex: string, message: string): Promise<DilithiaSignature>;
  verifyMessage(publicKeyHex: string, message: string, signatureHex: string): Promise<boolean>;
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
  sign_message?: (secretKeyHex: string, message: string) => DilithiaSignature;
  verify_message?: (publicKeyHex: string, message: string, signatureHex: string) => boolean;
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
    };
  } catch {
    return null;
  }
}
