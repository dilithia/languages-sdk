//! STARK native bridge — requires dilithia-core with feature "stark".
//! This package will not compile until dilithia-core publishes the stark feature.
//! See: https://github.com/dilithia/languages-sdk/issues/TBD

use napi::bindgen_prelude::*;
use napi_derive::napi;

#[napi(object)]
pub struct CommitmentResult {
    pub commitment: String,
    pub nonce: String,
}

#[napi(object)]
pub struct ProofResult {
    pub proof: String,
    pub public_inputs: String,
    pub verification_key: String,
}

#[napi]
pub fn poseidon_hash(inputs: Vec<u64>) -> Result<String> {
    todo!("waiting for dilithia-core stark feature")
}

#[napi]
pub fn compute_commitment(value: u64, secret_hex: String, nonce_hex: String) -> Result<CommitmentResult> {
    todo!("waiting for dilithia-core stark feature")
}

#[napi]
pub fn compute_nullifier(secret_hex: String, nonce_hex: String) -> Result<String> {
    todo!("waiting for dilithia-core stark feature")
}

#[napi]
pub fn generate_preimage_proof(values: Vec<u64>) -> Result<ProofResult> {
    todo!("waiting for dilithia-core stark feature")
}

#[napi]
pub fn verify_preimage_proof(proof_hex: String, vk_json: String, inputs_json: String) -> Result<bool> {
    todo!("waiting for dilithia-core stark feature")
}

#[napi]
pub fn generate_range_proof(value: u64, min_val: u64, max_val: u64) -> Result<ProofResult> {
    todo!("waiting for dilithia-core stark feature")
}

#[napi]
pub fn verify_range_proof(proof_hex: String, vk_json: String, inputs_json: String) -> Result<bool> {
    todo!("waiting for dilithia-core stark feature")
}
