package org.dilithia.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DilithiaGasSponsorConnector {
    private final DilithiaClient client;
    private final String sponsorContract;
    private final String paymaster;

    public DilithiaGasSponsorConnector(DilithiaClient client, String sponsorContract, String paymaster) {
        this.client = client;
        this.sponsorContract = sponsorContract;
        this.paymaster = paymaster;
    }

    public Map<String, Object> buildAcceptQuery(String user, String contract, String method) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("user", user);
        args.put("contract", contract);
        args.put("method", method);
        return client.buildContractCall(sponsorContract, "accept", args, null);
    }

    public Map<String, Object> buildRemainingQuotaQuery(String user) {
        return client.buildContractCall(sponsorContract, "remaining_quota", Map.of("user", user), null);
    }

    public Map<String, Object> applyPaymaster(Map<String, Object> call) {
        return paymaster == null || paymaster.isBlank() ? new LinkedHashMap<>(call) : client.withPaymaster(call, paymaster);
    }
}
