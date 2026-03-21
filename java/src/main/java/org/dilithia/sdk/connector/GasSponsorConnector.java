package org.dilithia.sdk.connector;

import org.dilithia.sdk.ContractCallBuilder;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.exception.DilithiaException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gas sponsorship connector.
 *
 * <p>Wraps the gas sponsor contract to check call acceptance,
 * query remaining quotas, and apply a paymaster to outgoing calls.</p>
 */
public class GasSponsorConnector {

    private final DilithiaClient client;
    private final String sponsorContract;
    private final String paymaster;

    /**
     * Creates a new gas sponsor connector.
     *
     * @param client          the SDK client
     * @param sponsorContract the gas sponsor contract address
     * @param paymaster       the paymaster address
     */
    public GasSponsorConnector(DilithiaClient client, String sponsorContract, String paymaster) {
        this.client = client;
        this.sponsorContract = sponsorContract;
        this.paymaster = paymaster;
    }

    /**
     * Checks whether the sponsor accepts calls from the given user
     * to the specified contract method.
     *
     * @param user     the caller address
     * @param contract the target contract
     * @param method   the target method name
     * @return {@code true} if the call is accepted
     * @throws DilithiaException if the query fails
     */
    public boolean acceptsCall(String user, String contract, String method) throws DilithiaException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("user", user);
        args.put("contract", contract);
        args.put("method", method);
        Map<String, Object> result = DilithiaClient.buildContractCall(sponsorContract, "accept", args, null);
        Object ok = result.get("args");
        return ok != null;
    }

    /**
     * Queries the remaining gas quota for a user.
     *
     * @param user the user address
     * @return the remaining quota
     * @throws DilithiaException if the query fails
     */
    public long remainingQuota(String user) throws DilithiaException {
        Map<String, Object> result = DilithiaClient.buildContractCall(
                sponsorContract, "remaining_quota", Map.of("user", user), null);
        Object quota = result.get("args");
        if (quota instanceof Map<?, ?> argsMap) {
            Object userQuota = argsMap.get("user");
            if (userQuota instanceof Number n) {
                return n.longValue();
            }
        }
        return 0L;
    }

    /**
     * Applies the paymaster to a contract call builder.
     *
     * @param call the contract call builder
     * @return the call builder with the paymaster applied
     */
    public ContractCallBuilder applyPaymaster(ContractCallBuilder call) {
        if (paymaster != null && !paymaster.isBlank()) {
            call.withPaymaster(paymaster);
        }
        return call;
    }
}
