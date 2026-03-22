package org.dilithia.sdk.model;

import java.util.List;

/**
 * Represents a multisig wallet stored on-chain.
 *
 * @param walletId  the unique wallet identifier
 * @param signers   the list of authorised signer addresses
 * @param threshold the number of approvals required to execute a transaction
 */
public record MultisigWallet(
        String walletId,
        List<String> signers,
        int threshold
) {}
