package org.dilithia.sdk.crypto;

import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.ValidationException;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.PublicKey;
import org.dilithia.sdk.model.SecretKey;

import java.util.Map;

/**
 * Adapter interface for Dilithia post-quantum cryptographic operations.
 *
 * <p>Implementations bridge to the native Dilithia crypto library via JNA
 * or provide alternative (e.g. pure-Java) implementations.</p>
 */
public interface DilithiaCryptoAdapter {
    String generateMnemonic() throws CryptoException;

    void validateMnemonic(String mnemonic) throws ValidationException;

    DilithiaAccount recoverHdWallet(String mnemonic) throws CryptoException;

    DilithiaAccount recoverHdWalletAccount(String mnemonic, int accountIndex) throws CryptoException;

    DilithiaAccount createHdWalletFileFromMnemonic(String mnemonic, String password) throws CryptoException;

    DilithiaAccount createHdWalletAccountFromMnemonic(String mnemonic, String password, int accountIndex) throws CryptoException;

    DilithiaAccount recoverWalletFile(Map<String, Object> walletFile, String mnemonic, String password) throws CryptoException;

    /**
     * Derives an address from the given public key.
     *
     * @param publicKey the public key
     * @return the derived address
     * @throws CryptoException if derivation fails
     */
    Address addressFromPublicKey(PublicKey publicKey) throws CryptoException;

    /**
     * Signs a message with the given secret key.
     *
     * @param secretKey the secret key to sign with
     * @param message   the message to sign
     * @return the resulting signature
     * @throws CryptoException if signing fails
     */
    DilithiaSignature signMessage(SecretKey secretKey, String message) throws CryptoException;

    /**
     * Verifies a message signature against the given public key.
     *
     * @param publicKey    the public key to verify against
     * @param message      the original message
     * @param signatureHex the signature in hex
     * @return {@code true} if the signature is valid
     * @throws CryptoException if verification fails
     */
    boolean verifyMessage(PublicKey publicKey, String message, String signatureHex) throws CryptoException;

    /**
     * Validates an address string and returns the canonical form.
     *
     * @param addr the address to validate
     * @return the validated address
     * @throws ValidationException if the address is invalid
     */
    Address validateAddress(String addr) throws ValidationException;

    /**
     * Derives a checksummed address from the given public key.
     *
     * @param publicKey the public key
     * @return the checksummed address
     * @throws CryptoException if derivation fails
     */
    Address addressFromPkChecksummed(PublicKey publicKey) throws CryptoException;

    /**
     * Adds a checksum to a raw address.
     *
     * @param rawAddr the raw address string
     * @return the checksummed address
     * @throws CryptoException if checksumming fails
     */
    Address addressWithChecksum(String rawAddr) throws CryptoException;

    /**
     * Validates a public key hex string.
     *
     * @param publicKey the public key to validate
     * @throws ValidationException if the public key is invalid
     */
    void validatePublicKey(PublicKey publicKey) throws ValidationException;

    /**
     * Validates a secret key hex string.
     *
     * @param secretKey the secret key to validate
     * @throws ValidationException if the secret key is invalid
     */
    void validateSecretKey(SecretKey secretKey) throws ValidationException;

    void validateSignature(String signatureHex) throws ValidationException;

    DilithiaKeypair keygen() throws CryptoException;

    DilithiaKeypair keygenFromSeed(String seedHex) throws CryptoException;

    String seedFromMnemonic(String mnemonic) throws CryptoException;

    String deriveChildSeed(String parentSeedHex, int index) throws CryptoException;

    boolean constantTimeEq(String aHex, String bHex) throws CryptoException;

    String hashHex(String dataHex) throws CryptoException;

    void setHashAlg(String alg) throws CryptoException;

    String currentHashAlg() throws CryptoException;

    int hashLenHex() throws CryptoException;
}
