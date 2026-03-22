package sdk

import (
	"encoding/json"
	"fmt"
	"math/big"
	"strings"
)

// Address is a Dilithia account address (e.g. "dili1abc...").
type Address string

// String returns the address as a plain string.
func (a Address) String() string { return string(a) }

// TxHash is a transaction hash.
type TxHash string

// String returns the hash as a plain string.
func (h TxHash) String() string { return string(h) }

// TokenAmount represents a token quantity with decimal precision.
// Raw is the amount in the smallest indivisible unit; Decimals indicates
// how many trailing digits represent the fractional part.
type TokenAmount struct {
	Raw      *big.Int
	Decimals int
}

// DiliAmount creates a TokenAmount using the native token's 18 decimals.
func DiliAmount(raw *big.Int) TokenAmount {
	return TokenAmount{Raw: new(big.Int).Set(raw), Decimals: 18}
}

// ParseDili parses a string representation of DILI (e.g. "1000000000000000000")
// into a TokenAmount with 18 decimals.
func ParseDili(s string) (TokenAmount, error) {
	s = strings.TrimSpace(s)
	v, ok := new(big.Int).SetString(s, 10)
	if !ok {
		return TokenAmount{}, fmt.Errorf("invalid token amount: %q", s)
	}
	return TokenAmount{Raw: v, Decimals: 18}, nil
}

// String returns the raw amount as a decimal string.
func (t TokenAmount) String() string {
	if t.Raw == nil {
		return "0"
	}
	return t.Raw.String()
}

// Balance is the response from GetBalance.
type Balance struct {
	// Address that was queried.
	Address Address `json:"address"`
	// Value is the parsed balance as a TokenAmount (not serialised to JSON).
	Value TokenAmount `json:"-"`
	// RawValue is the raw balance string returned by the node, used for JSON
	// round-tripping.
	RawValue string `json:"balance"`
}

// UnmarshalJSON implements json.Unmarshaler so that Value is populated from
// the "balance" JSON field.
func (b *Balance) UnmarshalJSON(data []byte) error {
	type alias Balance
	var raw alias
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	*b = Balance(raw)
	if b.RawValue != "" {
		v, ok := new(big.Int).SetString(b.RawValue, 10)
		if !ok {
			// Try as float-like integer (JSON number without quotes).
			var num json.Number
			if err := json.Unmarshal([]byte(b.RawValue), &num); err == nil {
				v, ok = new(big.Int).SetString(num.String(), 10)
			}
		}
		if ok {
			b.Value = DiliAmount(v)
		}
	}
	return nil
}

// Nonce is the response from GetNonce.
type Nonce struct {
	// Address that was queried.
	Address Address `json:"address"`
	// NextNonce is the next valid nonce for this address.
	NextNonce uint64 `json:"next_nonce"`
}

// Receipt represents a transaction receipt.
type Receipt struct {
	// TxHash is the hash of the transaction.
	TxHash TxHash `json:"tx_hash"`
	// BlockHeight is the block in which the transaction was included.
	BlockHeight uint64 `json:"block_height"`
	// Status is the execution status (e.g. "success", "revert").
	Status string `json:"status"`
	// Result holds any return data from the transaction.
	Result any `json:"result"`
	// Error is a non-empty string if the transaction reverted.
	Error string `json:"error"`
	// GasUsed is the amount of gas consumed.
	GasUsed uint64 `json:"gas_used"`
	// FeePaid is the fee charged for the transaction.
	FeePaid uint64 `json:"fee_paid"`
}

// NetworkInfo describes the current state of the network.
type NetworkInfo struct {
	// ChainID is the identifier for this chain.
	ChainID string `json:"chain_id"`
	// BlockHeight is the latest block height.
	BlockHeight uint64 `json:"height"`
	// BaseFee is the current base fee per gas unit.
	BaseFee uint64 `json:"base_fee"`
}

// GasEstimate holds the result of a gas estimation query.
type GasEstimate struct {
	// GasLimit is the maximum gas allowed.
	GasLimit uint64 `json:"gas_limit"`
	// BaseFee is the current base fee.
	BaseFee uint64 `json:"base_fee"`
	// EstimatedCost is the estimated total cost.
	EstimatedCost uint64 `json:"estimated_cost"`
}

// NameRecord holds a name-service resolution result.
type NameRecord struct {
	// Name is the human-readable name.
	Name string `json:"name"`
	// Address is the resolved on-chain address.
	Address Address `json:"address"`
}

// QueryResult wraps the return value of a contract query.
type QueryResult struct {
	// Value holds the decoded query result.
	Value any `json:"value"`
}

// SubmitResult is returned when a transaction is submitted to the network.
type SubmitResult struct {
	// Accepted indicates whether the node accepted the transaction.
	Accepted bool `json:"accepted"`
	// TxHash is the hash assigned to the submitted transaction.
	TxHash TxHash `json:"tx_hash"`
}

// ContractAbi describes the public interface of a deployed contract.
type ContractAbi struct {
	// Contract is the contract identifier.
	Contract string `json:"contract"`
	// Methods lists the callable methods.
	Methods []AbiMethod `json:"methods"`
}

// AbiMethod describes a single method in a contract ABI.
type AbiMethod struct {
	// Name is the method name.
	Name string `json:"name"`
	// Mutates is true if the method writes state.
	Mutates bool `json:"mutates"`
	// HasArgs is true if the method accepts arguments.
	HasArgs bool `json:"has_args"`
}

// WalletFile represents an encrypted wallet file as a generic JSON map.
type WalletFile map[string]any

// Account holds the key material and metadata for a Dilithia account.
type Account struct {
	Address      string     `json:"address"`
	PublicKey    string     `json:"public_key"`
	SecretKey    string     `json:"secret_key"`
	AccountIndex int        `json:"account_index"`
	WalletFile   WalletFile `json:"wallet_file"`
}

// Signature holds a cryptographic signature and the algorithm used.
type Signature struct {
	Algorithm string `json:"algorithm"`
	Signature string `json:"signature"`
}

// Keypair holds a freshly generated or derived key pair.
type Keypair struct {
	SecretKey string `json:"secret_key"`
	PublicKey string `json:"public_key"`
	Address   string `json:"address"`
}

// Commitment represents a shielded pool commitment.
type Commitment struct {
	Hash   string `json:"hash"`
	Value  uint64 `json:"value"`
	Secret string `json:"secret"`
	Nonce  string `json:"nonce"`
}

// Nullifier represents a shielded pool nullifier.
type Nullifier struct {
	Hash string `json:"hash"`
}

// StarkProofResult holds the output of a STARK proof generation.
type StarkProofResult struct {
	Proof  string `json:"proof"`
	VK     string `json:"vk"`
	Inputs string `json:"inputs"`
}

// NameEntry holds full name-service entry metadata.
type NameEntry struct {
	// Name is the human-readable name.
	Name string `json:"name"`
	// Address is the resolved on-chain address (owner).
	Address Address `json:"address"`
	// Target is the address or resource the name points to.
	Target string `json:"target"`
	// Expiry is the Unix timestamp when the registration expires.
	Expiry uint64 `json:"expiry"`
}

// RegistrationCost holds the estimated cost of registering a name.
type RegistrationCost struct {
	// Name is the name that was queried.
	Name string `json:"name"`
	// Cost is the registration cost in the smallest token unit.
	Cost uint64 `json:"cost"`
	// Duration is the registration period in seconds.
	Duration uint64 `json:"duration"`
}

// SchemaAttribute describes a single attribute in a credential schema.
type SchemaAttribute struct {
	// Name is the attribute key.
	Name string `json:"name"`
	// Type is the attribute data type (e.g. "string", "uint64").
	Type string `json:"type"`
}

// CredentialSchema is a credential schema registered on-chain.
type CredentialSchema struct {
	// Name is the schema's human-readable name.
	Name string `json:"name"`
	// Version is the schema version string.
	Version string `json:"version"`
	// Attributes lists the schema's attribute definitions.
	Attributes []SchemaAttribute `json:"attributes"`
}

// Credential is an issued credential stored on-chain.
type Credential struct {
	// Commitment is the credential's unique commitment hash.
	Commitment string `json:"commitment"`
	// Issuer is the address of the credential issuer.
	Issuer string `json:"issuer"`
	// Holder is the address of the credential holder.
	Holder string `json:"holder"`
	// SchemaHash identifies the schema this credential was issued under.
	SchemaHash string `json:"schema_hash"`
	// Status is the credential status (e.g. "active", "revoked").
	Status string `json:"status"`
	// Revoked indicates whether the credential has been revoked.
	Revoked bool `json:"revoked"`
}

// VerificationResult holds the outcome of a credential verification.
type VerificationResult struct {
	// Valid is true if the credential proof is valid.
	Valid bool `json:"valid"`
	// Commitment is the credential commitment that was verified.
	Commitment string `json:"commitment"`
	// Reason provides additional detail when verification fails.
	Reason string `json:"reason,omitempty"`
}

// MultisigWallet represents a multisig wallet stored on-chain.
type MultisigWallet struct {
	// WalletID is the unique identifier for this multisig wallet.
	WalletID string `json:"wallet_id"`
	// Signers is the list of authorised signer addresses.
	Signers []string `json:"signers"`
	// Threshold is the number of approvals required to execute a transaction.
	Threshold int `json:"threshold"`
}

// MultisigTx represents a pending multisig transaction.
type MultisigTx struct {
	// TxID is the unique identifier for this pending transaction.
	TxID string `json:"tx_id"`
	// Contract is the target contract for the proposed call.
	Contract string `json:"contract"`
	// Method is the target method for the proposed call.
	Method string `json:"method"`
	// Args holds the call arguments.
	Args map[string]any `json:"args"`
	// Approvals lists the addresses that have approved this transaction.
	Approvals []string `json:"approvals"`
}

// DeployPayload carries the data needed to deploy or upgrade a contract.
type DeployPayload struct {
	Name     string `json:"name"`
	Bytecode string `json:"bytecode"`
	From     string `json:"from"`
	Alg      string `json:"alg"`
	PK       string `json:"pk"`
	Sig      string `json:"sig"`
	Nonce    uint64 `json:"nonce"`
	ChainID  string `json:"chain_id"`
	Version  uint8  `json:"version"`
}
