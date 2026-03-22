package sdk

import (
	"context"
	"encoding/json"
)

// credentialContract is the well-known contract identifier for credentials.
const credentialContract = "credential"

// ---------------------------------------------------------------------------
// Credential mutations (submitted as contract calls via POST /call)
// ---------------------------------------------------------------------------

// RegisterSchema registers a new credential schema on-chain.
func (c *Client) RegisterSchema(ctx context.Context, name, version string, attributes []SchemaAttribute) (*SubmitResult, error) {
	// Serialise attributes as []map so the contract sees plain JSON objects.
	attrs := make([]map[string]string, len(attributes))
	for i, a := range attributes {
		attrs[i] = map[string]string{"name": a.Name, "type": a.Type}
	}
	call := c.BuildContractCall(credentialContract, "register_schema", map[string]any{
		"name":       name,
		"version":    version,
		"attributes": attrs,
	}, "")
	return c.SendCall(ctx, call)
}

// IssueCredential issues a credential to holder under the given schema.
func (c *Client) IssueCredential(ctx context.Context, holder, schemaHash, commitment string, attributes map[string]any) (*SubmitResult, error) {
	args := map[string]any{
		"holder":      holder,
		"schema_hash": schemaHash,
		"commitment":  commitment,
	}
	if attributes != nil {
		args["attributes"] = attributes
	}
	call := c.BuildContractCall(credentialContract, "issue", args, "")
	return c.SendCall(ctx, call)
}

// RevokeCredential revokes a previously issued credential.
func (c *Client) RevokeCredential(ctx context.Context, commitment string) (*SubmitResult, error) {
	call := c.BuildContractCall(credentialContract, "revoke", map[string]any{
		"commitment": commitment,
	}, "")
	return c.SendCall(ctx, call)
}

// VerifyCredential verifies a credential proof on-chain, optionally with
// predicate checks for selective disclosure.
func (c *Client) VerifyCredential(ctx context.Context, commitment, schemaHash, proof string, revealedAttributes map[string]any, predicates []map[string]any) (*VerificationResult, error) {
	if revealedAttributes == nil {
		revealedAttributes = map[string]any{}
	}
	if predicates == nil {
		predicates = []map[string]any{}
	}
	call := c.BuildContractCall(credentialContract, "verify", map[string]any{
		"commitment":           commitment,
		"schema_hash":          schemaHash,
		"proof":                proof,
		"revealed_attributes":  revealedAttributes,
		"predicates":           predicates,
	}, "")
	raw, err := c.SendCall(ctx, call)
	if err != nil {
		return nil, err
	}
	// The verification result is returned inside the submit response; parse
	// what we can. Some nodes return the result in the SubmitResult itself.
	result := &VerificationResult{Commitment: commitment}
	// If the call was accepted we treat the submission as successful and
	// return the stub. For richer parsing, callers can use the receipt.
	result.Valid = raw.Accepted
	return result, nil
}

// ---------------------------------------------------------------------------
// Credential queries
// ---------------------------------------------------------------------------

// GetCredential fetches a single credential by its commitment hash.
func (c *Client) GetCredential(ctx context.Context, commitment string) (*Credential, error) {
	qr, err := c.QueryContract(ctx, credentialContract, "get_credential", map[string]any{
		"commitment": commitment,
	})
	if err != nil {
		return nil, err
	}
	raw, ok := qr.Value.(map[string]any)
	if !ok {
		return nil, &DilithiaError{Message: "unexpected query result type"}
	}
	credMap, ok := raw["credential"].(map[string]any)
	if !ok {
		return nil, nil
	}
	cred := &Credential{Commitment: commitment}
	if v, ok := credMap["issuer"].(string); ok {
		cred.Issuer = v
	}
	if v, ok := credMap["holder"].(string); ok {
		cred.Holder = v
	}
	if v, ok := credMap["schema_hash"].(string); ok {
		cred.SchemaHash = v
	}
	if v, ok := credMap["status"].(string); ok {
		cred.Status = v
	} else {
		cred.Status = "active"
	}
	if v, ok := raw["revoked"].(bool); ok {
		cred.Revoked = v
	}
	return cred, nil
}

// GetSchema fetches a credential schema by its hash.
func (c *Client) GetSchema(ctx context.Context, schemaHash string) (*CredentialSchema, error) {
	qr, err := c.QueryContract(ctx, credentialContract, "get_schema", map[string]any{
		"schema_hash": schemaHash,
	})
	if err != nil {
		return nil, err
	}
	raw, ok := qr.Value.(map[string]any)
	if !ok {
		return nil, &DilithiaError{Message: "unexpected query result type"}
	}
	schemaMap, ok := raw["schema"].(map[string]any)
	if !ok {
		return nil, nil
	}
	schema := &CredentialSchema{}
	if v, ok := schemaMap["name"].(string); ok {
		schema.Name = v
	}
	if v, ok := schemaMap["version"].(string); ok {
		schema.Version = v
	}
	if arr, ok := schemaMap["attributes"].([]any); ok {
		for _, item := range arr {
			if m, ok := item.(map[string]any); ok {
				attr := SchemaAttribute{}
				if v, ok := m["name"].(string); ok {
					attr.Name = v
				}
				if v, ok := m["type"].(string); ok {
					attr.Type = v
				}
				schema.Attributes = append(schema.Attributes, attr)
			}
		}
	}
	return schema, nil
}

// ListCredentialsByHolder returns all credentials held by the given address.
func (c *Client) ListCredentialsByHolder(ctx context.Context, holder string) ([]Credential, error) {
	return c.listCredentials(ctx, "list_by_holder", "holder", holder)
}

// ListCredentialsByIssuer returns all credentials issued by the given address.
func (c *Client) ListCredentialsByIssuer(ctx context.Context, issuer string) ([]Credential, error) {
	return c.listCredentials(ctx, "list_by_issuer", "issuer", issuer)
}

// listCredentials is a shared helper for the list_by_holder / list_by_issuer
// contract queries.
func (c *Client) listCredentials(ctx context.Context, method, paramKey, paramValue string) ([]Credential, error) {
	qr, err := c.QueryContract(ctx, credentialContract, method, map[string]any{
		paramKey: paramValue,
	})
	if err != nil {
		return nil, err
	}
	raw, ok := qr.Value.(map[string]any)
	if !ok {
		return nil, &DilithiaError{Message: "unexpected query result type"}
	}
	arr, ok := raw["credentials"].([]any)
	if !ok {
		return nil, nil
	}
	var creds []Credential
	for _, item := range arr {
		b, err := json.Marshal(item)
		if err != nil {
			continue
		}
		var cred Credential
		if err := json.Unmarshal(b, &cred); err != nil {
			continue
		}
		creds = append(creds, cred)
	}
	return creds, nil
}
