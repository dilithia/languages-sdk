package org.dilithia.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DilithiaMessagingConnector {
    private final DilithiaClient client;
    private final String messagingContract;
    private final String paymaster;

    public DilithiaMessagingConnector(DilithiaClient client, String messagingContract, String paymaster) {
        this.client = client;
        this.messagingContract = messagingContract;
        this.paymaster = paymaster;
    }

    public Map<String, Object> buildSendMessageCall(String destChain, Object payload) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("dest_chain", destChain);
        args.put("payload", payload);
        return client.buildContractCall(messagingContract, "send_message", args, paymaster);
    }

    public Map<String, Object> buildReceiveMessageCall(String sourceChain, String sourceContract, Object payload) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("source_chain", sourceChain);
        args.put("source_contract", sourceContract);
        args.put("payload", payload);
        return client.buildContractCall(messagingContract, "receive_message", args, paymaster);
    }
}
