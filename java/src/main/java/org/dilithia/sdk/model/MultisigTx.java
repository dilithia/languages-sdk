package org.dilithia.sdk.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a pending multisig transaction.
 *
 * @param txId      the unique transaction identifier
 * @param contract  the target contract for the proposed call
 * @param method    the target method for the proposed call
 * @param args      the call arguments
 * @param approvals the addresses that have approved this transaction
 */
public record MultisigTx(
        String txId,
        String contract,
        String method,
        Map<String, Object> args,
        List<String> approvals
) {}
