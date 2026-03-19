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

export interface DilithiaZkAdapter {
  poseidonHash(inputs: number[]): Promise<string>;
  computeCommitment(value: number, secretHex: string, nonceHex: string): Promise<Commitment>;
  computeNullifier(secretHex: string, nonceHex: string): Promise<Nullifier>;
  generatePreimageProof(values: number[]): Promise<StarkProof>;
  verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
  generateRangeProof(value: number, min: number, max: number): Promise<StarkProof>;
  verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string): Promise<boolean>;
}

export interface SyncDilithiaZkAdapter {
  poseidonHash(inputs: number[]): string;
  computeCommitment(value: number, secretHex: string, nonceHex: string): Commitment;
  computeNullifier(secretHex: string, nonceHex: string): Nullifier;
  generatePreimageProof(values: number[]): StarkProof;
  verifyPreimageProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
  generateRangeProof(value: number, min: number, max: number): StarkProof;
  verifyRangeProof(proofHex: string, vkJson: string, inputsJson: string): boolean;
}

type ZkNativeModuleShape = {
  poseidon_hash?: (inputs: number[]) => string;
  compute_commitment?: (value: number, secretHex: string, nonceHex: string) => Commitment;
  compute_nullifier?: (secretHex: string, nonceHex: string) => Nullifier;
  generate_preimage_proof?: (values: number[]) => StarkProof;
  verify_preimage_proof?: (proofHex: string, vkJson: string, inputsJson: string) => boolean;
  generate_range_proof?: (value: number, min: number, max: number) => StarkProof;
  verify_range_proof?: (proofHex: string, vkJson: string, inputsJson: string) => boolean;
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
    };
  } catch {
    return null;
  }
}
