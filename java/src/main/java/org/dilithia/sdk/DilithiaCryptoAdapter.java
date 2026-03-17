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

    String validateAddress(String addr);

    String addressFromPkChecksummed(String publicKeyHex);

    String addressWithChecksum(String rawAddr);

    void validatePublicKey(String publicKeyHex);

    void validateSecretKey(String secretKeyHex);

    void validateSignature(String signatureHex);

    DilithiaKeypair keygen();

    DilithiaKeypair keygenFromSeed(String seedHex);

    String seedFromMnemonic(String mnemonic);

    String deriveChildSeed(String parentSeedHex, int index);

    boolean constantTimeEq(String aHex, String bHex);

    String hashHex(String dataHex);

    void setHashAlg(String alg);

    String currentHashAlg();

    int hashLenHex();
}
