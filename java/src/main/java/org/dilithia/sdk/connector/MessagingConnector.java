package org.dilithia.sdk.connector;

import org.dilithia.sdk.ContractCallBuilder;
import org.dilithia.sdk.DilithiaClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cross-chain messaging connector.
 *
 * <p>Builds contract call payloads for sending and receiving
 * cross-chain messages via the messaging contract.</p>
 */
public class MessagingConnector {

    private final DilithiaClient client;
    private final String messagingContract;
    private final String paymaster;

    /**
     * Creates a new messaging connector.
     *
     * @param client            the SDK client
     * @param messagingContract the messaging contract address
     * @param paymaster         the paymaster address (may be null)
     */
    public MessagingConnector(DilithiaClient client, String messagingContract, String paymaster) {
        this.client = client;
        this.messagingContract = messagingContract;
        this.paymaster = paymaster;
    }

    /**
     * Builds a contract call to send a cross-chain message.
     *
     * @param destChain the destination chain identifier
     * @param payload   the message payload
     * @return a contract call builder for the send operation
     */
    public ContractCallBuilder sendMessage(String destChain, Object payload) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("dest_chain", destChain);
        args.put("payload", payload);
        ContractCallBuilder builder = new ContractCallBuilder(client, messagingContract, "send_message", args);
        if (paymaster != null && !paymaster.isBlank()) {
            builder.withPaymaster(paymaster);
        }
        return builder;
    }

    /**
     * Builds a contract call to receive a cross-chain message.
     *
     * @param sourceChain    the source chain identifier
     * @param sourceContract the source contract address
     * @param payload        the message payload
     * @return a contract call builder for the receive operation
     */
    public ContractCallBuilder receiveMessage(String sourceChain, String sourceContract, Object payload) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("source_chain", sourceChain);
        args.put("source_contract", sourceContract);
        args.put("payload", payload);
        ContractCallBuilder builder = new ContractCallBuilder(client, messagingContract, "receive_message", args);
        if (paymaster != null && !paymaster.isBlank()) {
            builder.withPaymaster(paymaster);
        }
        return builder;
    }
}
