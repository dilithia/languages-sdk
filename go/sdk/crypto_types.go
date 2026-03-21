package sdk

import (
	"context"
	"errors"
)

// ErrNativeCryptoUnavailable is returned when the native crypto adapter
// cannot be loaded (e.g. CGo is disabled or the shared library is missing).
var ErrNativeCryptoUnavailable = errors.New("native crypto adapter is not available")

// NativeCryptoAdapter is the interface for a platform-native crypto backend
// loaded via CGo from a shared library.
type NativeCryptoAdapter interface {
	CryptoAdapter
}

// ZkAdapter defines zero-knowledge proof operations (Poseidon hashing,
// commitment/nullifier computation, and STARK proof generation/verification).
type ZkAdapter interface {
	PoseidonHash(ctx context.Context, inputs []uint64) (string, error)
	ComputeCommitment(ctx context.Context, value uint64, secretHex, nonceHex string) (Commitment, error)
	ComputeNullifier(ctx context.Context, secretHex, nonceHex string) (Nullifier, error)
	GeneratePreimageProof(ctx context.Context, values []uint64) (StarkProofResult, error)
	VerifyPreimageProof(ctx context.Context, proofHex, vkJSON, inputsJSON string) (bool, error)
	GenerateRangeProof(ctx context.Context, value, min, max uint64) (StarkProofResult, error)
	VerifyRangeProof(ctx context.Context, proofHex, vkJSON, inputsJSON string) (bool, error)
}

// CryptoAdapter defines the full suite of cryptographic operations:
// key generation, signing, verification, address derivation, HD wallet
// management, and hashing.
type CryptoAdapter interface {
	GenerateMnemonic(ctx context.Context) (string, error)
	ValidateMnemonic(ctx context.Context, mnemonic string) error
	RecoverHDWallet(ctx context.Context, mnemonic string) (Account, error)
	RecoverHDWalletAccount(ctx context.Context, mnemonic string, accountIndex int) (Account, error)
	CreateHDWalletFileFromMnemonic(ctx context.Context, mnemonic, password string) (Account, error)
	CreateHDWalletAccountFromMnemonic(ctx context.Context, mnemonic, password string, accountIndex int) (Account, error)
	RecoverWalletFile(ctx context.Context, walletFile WalletFile, mnemonic, password string) (Account, error)
	AddressFromPublicKey(ctx context.Context, publicKeyHex string) (string, error)
	SignMessage(ctx context.Context, secretKeyHex, message string) (Signature, error)
	VerifyMessage(ctx context.Context, publicKeyHex, message, signatureHex string) (bool, error)
	ValidateAddress(ctx context.Context, addr string) (string, error)
	AddressFromPKChecksummed(ctx context.Context, publicKeyHex string) (string, error)
	AddressWithChecksum(ctx context.Context, rawAddr string) (string, error)
	ValidatePublicKey(ctx context.Context, publicKeyHex string) error
	ValidateSecretKey(ctx context.Context, secretKeyHex string) error
	ValidateSignature(ctx context.Context, signatureHex string) error
	Keygen(ctx context.Context) (Keypair, error)
	KeygenFromSeed(ctx context.Context, seedHex string) (Keypair, error)
	SeedFromMnemonic(ctx context.Context, mnemonic string) (string, error)
	DeriveChildSeed(ctx context.Context, parentSeedHex string, index int) (string, error)
	ConstantTimeEq(ctx context.Context, aHex string, bHex string) (bool, error)
	HashHex(ctx context.Context, dataHex string) (string, error)
	SetHashAlg(ctx context.Context, alg string) error
	CurrentHashAlg(ctx context.Context) (string, error)
	HashLenHex(ctx context.Context) (int, error)
}
