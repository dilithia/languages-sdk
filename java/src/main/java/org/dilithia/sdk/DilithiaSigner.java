package org.dilithia.sdk;

import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.model.CanonicalPayload;
import org.dilithia.sdk.model.SignedPayload;

/**
 * Functional interface for signing canonical payloads.
 *
 * <p>Implementations wrap a specific cryptographic backend (e.g. native Dilithium,
 * HSM, remote signer) and produce a {@link SignedPayload} containing the
 * algorithm identifier, public key, and signature bytes.</p>
 *
 * <pre>{@code
 * DilithiaSigner signer = payload -> {
 *     byte[] sig = myKeyPair.sign(payload.canonicalBytes());
 *     return new SignedPayload("dilithium", myKeyPair.publicKeyHex(), sig);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface DilithiaSigner {

    /**
     * Signs the given canonical payload.
     *
     * @param payload the canonical payload to sign
     * @return the signed payload containing algorithm, public key, and signature
     * @throws CryptoException if signing fails
     */
    SignedPayload sign(CanonicalPayload payload) throws CryptoException;
}
