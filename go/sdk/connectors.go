package sdk

// GasSponsorConnector simplifies interactions with a gas-sponsorship contract.
// It attaches a paymaster to outgoing calls and provides helpers for querying
// sponsorship acceptance and remaining quota.
type GasSponsorConnector struct {
	// Client is the underlying SDK client.
	Client *Client
	// SponsorContract is the address of the gas-sponsor contract.
	SponsorContract string
	// Paymaster is the paymaster identifier attached to sponsored calls.
	Paymaster string
}

// NewGasSponsorConnector creates a GasSponsorConnector bound to the given
// client, sponsor contract, and paymaster identity.
func NewGasSponsorConnector(client *Client, sponsorContract, paymaster string) *GasSponsorConnector {
	return &GasSponsorConnector{Client: client, SponsorContract: sponsorContract, Paymaster: paymaster}
}

// BuildAcceptQuery builds a query map for checking whether a user's call
// to a given contract method is eligible for gas sponsorship.
func (g *GasSponsorConnector) BuildAcceptQuery(user, contract, method string) map[string]any {
	return map[string]any{
		"contract": g.SponsorContract,
		"method":   "accept",
		"args":     map[string]any{"user": user, "contract": contract, "method": method},
	}
}

// BuildRemainingQuotaQuery builds a query map for checking a user's
// remaining gas-sponsorship quota.
func (g *GasSponsorConnector) BuildRemainingQuotaQuery(user string) map[string]any {
	return map[string]any{
		"contract": g.SponsorContract,
		"method":   "remaining_quota",
		"args":     map[string]any{"user": user},
	}
}

// ApplyPaymaster returns a copy of the call map with the paymaster field set.
// If no Paymaster is configured the call is returned as-is (cloned).
func (g *GasSponsorConnector) ApplyPaymaster(call map[string]any) map[string]any {
	if g.Paymaster == "" {
		return cloneMap(call)
	}
	return g.Client.WithPaymaster(call, g.Paymaster)
}

// MessagingConnector simplifies interactions with a cross-chain messaging
// contract. It builds outbound and inbound message calls with optional
// gas sponsorship.
type MessagingConnector struct {
	// Client is the underlying SDK client.
	Client *Client
	// MessagingContract is the address of the messaging contract.
	MessagingContract string
	// Paymaster is the paymaster identifier attached to sponsored calls.
	Paymaster string
}

// NewMessagingConnector creates a MessagingConnector bound to the given
// client, messaging contract, and paymaster identity.
func NewMessagingConnector(client *Client, messagingContract, paymaster string) *MessagingConnector {
	return &MessagingConnector{Client: client, MessagingContract: messagingContract, Paymaster: paymaster}
}

// BuildSendMessageCall constructs a call map for sending a cross-chain message.
func (m *MessagingConnector) BuildSendMessageCall(destChain string, payload any) map[string]any {
	return m.applyPaymaster(map[string]any{
		"contract": m.MessagingContract,
		"method":   "send_message",
		"args":     map[string]any{"dest_chain": destChain, "payload": payload},
	})
}

// BuildReceiveMessageCall constructs a call map for receiving a cross-chain message.
func (m *MessagingConnector) BuildReceiveMessageCall(sourceChain, sourceContract string, payload any) map[string]any {
	return m.applyPaymaster(map[string]any{
		"contract": m.MessagingContract,
		"method":   "receive_message",
		"args": map[string]any{
			"source_chain":    sourceChain,
			"source_contract": sourceContract,
			"payload":         payload,
		},
	})
}

func (m *MessagingConnector) applyPaymaster(call map[string]any) map[string]any {
	if m.Paymaster == "" {
		return cloneMap(call)
	}
	return m.Client.WithPaymaster(call, m.Paymaster)
}
