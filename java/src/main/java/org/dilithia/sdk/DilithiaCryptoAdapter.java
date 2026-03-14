package org.dilithia.sdk;

import java.util.Map;

public interface DilithiaCryptoAdapter {
    String generateMnemonic();

    void validateMnemonic(String mnemonic);

    DilithiaAccount recoverHdWallet(String mnemonic);

    DilithiaAccount recoverHdWalletAccount(String mnemonic, int accountIndex);

    DilithiaAccount createHdWalletFileFromMnemonic(String mnemonic, String password);

    DilithiaAccount createHdWalletAccountFromMnemonic(String mnemonic, String password, int accountIndex);

    DilithiaAccount recoverWalletFile(Map<String, Object> walletFile, String mnemonic, String password);

    String addressFromPublicKey(String publicKeyHex);

    DilithiaSignature signMessage(String secretKeyHex, String message);

    boolean verifyMessage(String publicKeyHex, String message, String signatureHex);
}
