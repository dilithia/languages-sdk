import { createRequire } from "module";

export type Commitment = {
  hash: string;
  value: number;
  secret: string;
  nonce: string;
};

export type Nullifier = {
  hash: string;
};

export type ComplianceType = "not_on_sanctions" | "tax_paid" | "balance_range";

export type StarkProof = {
  proof: string;
  vk: string;
  inputs: string;
};

/** Result of a commitment proof with domain tag (0.5.0). */
export type CommitmentProofResult = {
  proof: string;
  publicInputs: string;
  verificationKey: string;
};

/** Result of a predicate proof (age, balance — 0.5.0). */
export type PredicateProofResult = {
  proof: string;
  commitment: string;
  min: number;
  max: number;
  domainTag: number;
};

/** Result of a transfer proof (0.5.0). */
export type TransferProofResult = {
  proof: string;
  senderPre: number;
  receiverPre: number;
  senderPost: number;
  receiverPost: number;
};

/** Result of a Merkle verification proof (0.5.0). */
export type MerkleProofResult = {
  proof: string;
  leafHash: string;
  root: string;
  depth: number;
};

export interface DilithiaZkAdapter {
  poseidonHash(inputs: number[]): Promise<string>;
  computeCommitment(value: number, secretHex: string, nonceHex: string): Promise<Commitment>;
  computeNullifier(secretHex: string, nonceHex: string): Promise<Nullifier>;
  generatePreimageProof(values: number[]): Promise<StarkProof>;
  verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
  generateRangeProof(value: number, min: number, max: number): Promise<StarkProof>;
  verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
  generateCommitmentProof(value: number, blinding: number, domainTag: number): Promise<CommitmentProofResult>;
  verifyCommitmentProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
  provePredicate(value: number, blinding: number, domainTag: number, min: number, max: number): Promise<PredicateProofResult>;
  proveAgeOver(birthYear: number, currentYear: number, minAge: number, blinding: number): Promise<PredicateProofResult>;
  verifyAgeOver(proofHex: string, commitmentHex: string, minAge: number): Promise<boolean>;
  proveBalanceAbove(balance: number, blinding: number, minBalance: number, maxBalance: number): Promise<PredicateProofResult>;
  verifyBalanceAbove(proofHex: string, commitmentHex: string, minBalance: number, maxBalance: number): Promise<boolean>;
  proveTransfer(senderPre: number, receiverPre: number, amount: number): Promise<TransferProofResult>;
  verifyTransfer(proofHex: string, inputsJson: string): Promise<boolean>;
  proveMerkleVerify(leafHashHex: string, pathJson: string): Promise<MerkleProofResult>;
  verifyMerkleProof(proofHex: string, inputsJson: string): Promise<boolean>;
}

export interface SyncDilithiaZkAdapter {
  poseidonHash(inputs: number[]): string;
  computeCommitment(value: number, secretHex: string, nonceHex: string): Commitment;
  computeNullifier(secretHex: string, nonceHex: string): Nullifier;
  generatePreimageProof(values: number[]): StarkProof;
  verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
  generateRangeProof(value: number, min: number, max: number): StarkProof;
  verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
  generateCommitmentProof(value: number, blinding: number, domainTag: number): CommitmentProofResult;
  verifyCommitmentProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
  provePredicate(value: number, blinding: number, domainTag: number, min: number, max: number): PredicateProofResult;
  proveAgeOver(birthYear: number, currentYear: number, minAge: number, blinding: number): PredicateProofResult;
  verifyAgeOver(proofHex: string, commitmentHex: string, minAge: number): boolean;
  proveBalanceAbove(balance: number, blinding: number, minBalance: number, maxBalance: number): PredicateProofResult;
  verifyBalanceAbove(proofHex: string, commitmentHex: string, minBalance: number, maxBalance: number): boolean;
  proveTransfer(senderPre: number, receiverPre: number, amount: number): TransferProofResult;
  verifyTransfer(proofHex: string, inputsJson: string): boolean;
  proveMerkleVerify(leafHashHex: string, pathJson: string): MerkleProofResult;
  verifyMerkleProof(proofHex: string, inputsJson: string): boolean;
}

type ZkNativeModuleShape = {
  poseidon_hash?: (inputs: number[]) => string;
  compute_commitment?: (value: number, secretHex: string, nonceHex: string) => Commitment;
  compute_nullifier?: (secretHex: string, nonceHex: string) => Nullifier;
  generate_preimage_proof?: (values: number[]) => StarkProof;
  verify_preimage_proof?: (proofHex: string, vkJson: string, inputsJson: string) => boolean;
  generate_range_proof?: (value: number, min: number, max: number) => StarkProof;
  verify_range_proof?: (proofHex: string, vkJson: string, inputsJson: string) => boolean;
  generate_commitment_proof?: (value: number, blinding: number, domainTag: number) => CommitmentProofResult;
  verify_commitment_proof?: (proofHex: string, vkJson: string, inputsJson: string) => boolean;
  prove_predicate?: (value: number, blinding: number, domainTag: number, min: number, max: number) => PredicateProofResult;
  prove_age_over?: (birthYear: number, currentYear: number, minAge: number, blinding: number) => PredicateProofResult;
  verify_age_over?: (proofHex: string, commitmentHex: string, minAge: number) => boolean;
  prove_balance_above?: (balance: number, blinding: number, minBalance: number, maxBalance: number) => PredicateProofResult;
  verify_balance_above?: (proofHex: string, commitmentHex: string, minBalance: number, maxBalance: number) => boolean;
  prove_transfer?: (senderPre: number, receiverPre: number, amount: number) => TransferProofResult;
  verify_transfer?: (proofHex: string, inputsJson: string) => boolean;
  prove_merkle_verify?: (leafHashHex: string, pathJson: string) => MerkleProofResult;
  verify_merkle_proof?: (proofHex: string, inputsJson: string) => boolean;
};

export async function loadZkAdapter(
  importer?: () => Promise<unknown>
): Promise<DilithiaZkAdapter | null> {
  const doImport = importer ?? (() => import("@dilithia/sdk-zk" as string) as Promise<unknown>);
  try {
    const module = (await doImport()) as ZkNativeModuleShape;
    return {
      async poseidonHash(inputs: number[]) {
        if (!module.poseidon_hash) {
          throw new Error("Stark bridge does not expose poseidon_hash.");
        }
        return module.poseidon_hash(inputs);
      },
      async computeCommitment(value: number, secretHex: string, nonceHex: string) {
        if (!module.compute_commitment) {
          throw new Error("Stark bridge does not expose compute_commitment.");
        }
        return module.compute_commitment(value, secretHex, nonceHex);
      },
      async computeNullifier(secretHex: string, nonceHex: string) {
        if (!module.compute_nullifier) {
          throw new Error("Stark bridge does not expose compute_nullifier.");
        }
        return module.compute_nullifier(secretHex, nonceHex);
      },
      async generatePreimageProof(values: number[]) {
        if (!module.generate_preimage_proof) {
          throw new Error("Stark bridge does not expose generate_preimage_proof.");
        }
        return module.generate_preimage_proof(values);
      },
      async verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string) {
        if (!module.verify_preimage_proof) {
          throw new Error("Stark bridge does not expose verify_preimage_proof.");
        }
        return module.verify_preimage_proof(proofHex, vkJson, inputsJson);
      },
      async generateRangeProof(value: number, min: number, max: number) {
        if (!module.generate_range_proof) {
          throw new Error("Stark bridge does not expose generate_range_proof.");
        }
        return module.generate_range_proof(value, min, max);
      },
      async verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string) {
        if (!module.verify_range_proof) {
          throw new Error("Stark bridge does not expose verify_range_proof.");
        }
        return module.verify_range_proof(proofHex, vkJson, inputsJson);
      },
      async generateCommitmentProof(value: number, blinding: number, domainTag: number) {
        if (!module.generate_commitment_proof) {
          throw new Error("Stark bridge does not expose generate_commitment_proof.");
        }
        return module.generate_commitment_proof(value, blinding, domainTag);
      },
      async verifyCommitmentProof(proofHex: string, vkJson: string, inputsJson: string) {
        if (!module.verify_commitment_proof) {
          throw new Error("Stark bridge does not expose verify_commitment_proof.");
        }
        return module.verify_commitment_proof(proofHex, vkJson, inputsJson);
      },
      async provePredicate(value: number, blinding: number, domainTag: number, min: number, max: number) {
        if (!module.prove_predicate) {
          throw new Error("Stark bridge does not expose prove_predicate.");
        }
        return module.prove_predicate(value, blinding, domainTag, min, max);
      },
      async proveAgeOver(birthYear: number, currentYear: number, minAge: number, blinding: number) {
        if (!module.prove_age_over) {
          throw new Error("Stark bridge does not expose prove_age_over.");
        }
        return module.prove_age_over(birthYear, currentYear, minAge, blinding);
      },
      async verifyAgeOver(proofHex: string, commitmentHex: string, minAge: number) {
        if (!module.verify_age_over) {
          throw new Error("Stark bridge does not expose verify_age_over.");
        }
        return module.verify_age_over(proofHex, commitmentHex, minAge);
      },
      async proveBalanceAbove(balance: number, blinding: number, minBalance: number, maxBalance: number) {
        if (!module.prove_balance_above) {
          throw new Error("Stark bridge does not expose prove_balance_above.");
        }
        return module.prove_balance_above(balance, blinding, minBalance, maxBalance);
      },
      async verifyBalanceAbove(proofHex: string, commitmentHex: string, minBalance: number, maxBalance: number) {
        if (!module.verify_balance_above) {
          throw new Error("Stark bridge does not expose verify_balance_above.");
        }
        return module.verify_balance_above(proofHex, commitmentHex, minBalance, maxBalance);
      },
      async proveTransfer(senderPre: number, receiverPre: number, amount: number) {
        if (!module.prove_transfer) {
          throw new Error("Stark bridge does not expose prove_transfer.");
        }
        return module.prove_transfer(senderPre, receiverPre, amount);
      },
      async verifyTransfer(proofHex: string, inputsJson: string) {
        if (!module.verify_transfer) {
          throw new Error("Stark bridge does not expose verify_transfer.");
        }
        return module.verify_transfer(proofHex, inputsJson);
      },
      async proveMerkleVerify(leafHashHex: string, pathJson: string) {
        if (!module.prove_merkle_verify) {
          throw new Error("Stark bridge does not expose prove_merkle_verify.");
        }
        return module.prove_merkle_verify(leafHashHex, pathJson);
      },
      async verifyMerkleProof(proofHex: string, inputsJson: string) {
        if (!module.verify_merkle_proof) {
          throw new Error("Stark bridge does not expose verify_merkle_proof.");
        }
        return module.verify_merkle_proof(proofHex, inputsJson);
      },
    };
  } catch {
    return null;
  }
}

export function loadSyncZkAdapter(): SyncDilithiaZkAdapter | null {
  try {
    const esmRequire = createRequire(import.meta.url);
    const module = esmRequire("@dilithia/sdk-zk") as ZkNativeModuleShape;
    return {
      poseidonHash(inputs: number[]) {
        if (!module.poseidon_hash) {
          throw new Error("Stark bridge does not expose poseidon_hash.");
        }
        return module.poseidon_hash(inputs);
      },
      computeCommitment(value: number, secretHex: string, nonceHex: string) {
        if (!module.compute_commitment) {
          throw new Error("Stark bridge does not expose compute_commitment.");
        }
        return module.compute_commitment(value, secretHex, nonceHex);
      },
      computeNullifier(secretHex: string, nonceHex: string) {
        if (!module.compute_nullifier) {
          throw new Error("Stark bridge does not expose compute_nullifier.");
        }
        return module.compute_nullifier(secretHex, nonceHex);
      },
      generatePreimageProof(values: number[]) {
        if (!module.generate_preimage_proof) {
          throw new Error("Stark bridge does not expose generate_preimage_proof.");
        }
        return module.generate_preimage_proof(values);
      },
      verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string) {
        if (!module.verify_preimage_proof) {
          throw new Error("Stark bridge does not expose verify_preimage_proof.");
        }
        return module.verify_preimage_proof(proofHex, vkJson, inputsJson);
      },
      generateRangeProof(value: number, min: number, max: number) {
        if (!module.generate_range_proof) {
          throw new Error("Stark bridge does not expose generate_range_proof.");
        }
        return module.generate_range_proof(value, min, max);
      },
      verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string) {
        if (!module.verify_range_proof) {
          throw new Error("Stark bridge does not expose verify_range_proof.");
        }
        return module.verify_range_proof(proofHex, vkJson, inputsJson);
      },
      generateCommitmentProof(value: number, blinding: number, domainTag: number) {
        if (!module.generate_commitment_proof) {
          throw new Error("Stark bridge does not expose generate_commitment_proof.");
        }
        return module.generate_commitment_proof(value, blinding, domainTag);
      },
      verifyCommitmentProof(proofHex: string, vkJson: string, inputsJson: string) {
        if (!module.verify_commitment_proof) {
          throw new Error("Stark bridge does not expose verify_commitment_proof.");
        }
        return module.verify_commitment_proof(proofHex, vkJson, inputsJson);
      },
      provePredicate(value: number, blinding: number, domainTag: number, min: number, max: number) {
        if (!module.prove_predicate) {
          throw new Error("Stark bridge does not expose prove_predicate.");
        }
        return module.prove_predicate(value, blinding, domainTag, min, max);
      },
      proveAgeOver(birthYear: number, currentYear: number, minAge: number, blinding: number) {
        if (!module.prove_age_over) {
          throw new Error("Stark bridge does not expose prove_age_over.");
        }
        return module.prove_age_over(birthYear, currentYear, minAge, blinding);
      },
      verifyAgeOver(proofHex: string, commitmentHex: string, minAge: number) {
        if (!module.verify_age_over) {
          throw new Error("Stark bridge does not expose verify_age_over.");
        }
        return module.verify_age_over(proofHex, commitmentHex, minAge);
      },
      proveBalanceAbove(balance: number, blinding: number, minBalance: number, maxBalance: number) {
        if (!module.prove_balance_above) {
          throw new Error("Stark bridge does not expose prove_balance_above.");
        }
        return module.prove_balance_above(balance, blinding, minBalance, maxBalance);
      },
      verifyBalanceAbove(proofHex: string, commitmentHex: string, minBalance: number, maxBalance: number) {
        if (!module.verify_balance_above) {
          throw new Error("Stark bridge does not expose verify_balance_above.");
        }
        return module.verify_balance_above(proofHex, commitmentHex, minBalance, maxBalance);
      },
      proveTransfer(senderPre: number, receiverPre: number, amount: number) {
        if (!module.prove_transfer) {
          throw new Error("Stark bridge does not expose prove_transfer.");
        }
        return module.prove_transfer(senderPre, receiverPre, amount);
      },
      verifyTransfer(proofHex: string, inputsJson: string) {
        if (!module.verify_transfer) {
          throw new Error("Stark bridge does not expose verify_transfer.");
        }
        return module.verify_transfer(proofHex, inputsJson);
      },
      proveMerkleVerify(leafHashHex: string, pathJson: string) {
        if (!module.prove_merkle_verify) {
          throw new Error("Stark bridge does not expose prove_merkle_verify.");
        }
        return module.prove_merkle_verify(leafHashHex, pathJson);
      },
      verifyMerkleProof(proofHex: string, inputsJson: string) {
        if (!module.verify_merkle_proof) {
          throw new Error("Stark bridge does not expose verify_merkle_proof.");
        }
        return module.verify_merkle_proof(proofHex, inputsJson);
      },
    };
  } catch {
    return null;
  }
}
