package sdk

import (
	"context"
	"net/url"
)

// ---------------------------------------------------------------------------
// Name service queries
// ---------------------------------------------------------------------------

// ResolveName resolves a human-readable name to an on-chain address via
// the name service.
func (c *Client) ResolveName(ctx context.Context, name string) (*NameRecord, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/resolve/"+url.PathEscape(name))
	if err != nil {
		return nil, err
	}
	record := &NameRecord{}
	if v, ok := raw["name"].(string); ok {
		record.Name = v
	}
	if v, ok := raw["address"].(string); ok {
		record.Address = Address(v)
	}
	return record, nil
}

// ReverseResolveName resolves an on-chain address to a human-readable name.
func (c *Client) ReverseResolveName(ctx context.Context, address string) (*NameRecord, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/reverse/"+url.PathEscape(address))
	if err != nil {
		return nil, err
	}
	record := &NameRecord{}
	if v, ok := raw["name"].(string); ok {
		record.Name = v
	}
	if v, ok := raw["address"].(string); ok {
		record.Address = Address(v)
	}
	return record, nil
}

// IsNameAvailable checks whether a name is available for registration.
func (c *Client) IsNameAvailable(ctx context.Context, name string) (bool, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/available/"+url.PathEscape(name))
	if err != nil {
		return false, err
	}
	if v, ok := raw["available"].(bool); ok {
		return v, nil
	}
	return false, nil
}

// LookupName fetches the full name-service entry for a registered name.
func (c *Client) LookupName(ctx context.Context, name string) (*NameEntry, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/lookup/"+url.PathEscape(name))
	if err != nil {
		return nil, err
	}
	entry := &NameEntry{}
	if v, ok := raw["name"].(string); ok {
		entry.Name = v
	}
	if v, ok := raw["address"].(string); ok {
		entry.Address = Address(v)
	}
	if v, ok := raw["target"].(string); ok {
		entry.Target = v
	}
	entry.Expiry = jsonNumberToUint64(raw["expiry"])
	return entry, nil
}

// GetNameRecords returns all key-value records associated with a name.
func (c *Client) GetNameRecords(ctx context.Context, name string) (map[string]string, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/records/"+url.PathEscape(name))
	if err != nil {
		return nil, err
	}
	records := map[string]string{}
	if recs, ok := raw["records"].(map[string]any); ok {
		for k, v := range recs {
			if s, ok := v.(string); ok {
				records[k] = s
			}
		}
	}
	return records, nil
}

// GetNamesByOwner returns all names registered to the given address.
func (c *Client) GetNamesByOwner(ctx context.Context, address string) ([]NameEntry, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/owner/"+url.PathEscape(address))
	if err != nil {
		return nil, err
	}
	var entries []NameEntry
	if arr, ok := raw["names"].([]any); ok {
		for _, item := range arr {
			if m, ok := item.(map[string]any); ok {
				entry := NameEntry{}
				if v, ok := m["name"].(string); ok {
					entry.Name = v
				}
				if v, ok := m["address"].(string); ok {
					entry.Address = Address(v)
				}
				if v, ok := m["target"].(string); ok {
					entry.Target = v
				}
				entry.Expiry = jsonNumberToUint64(m["expiry"])
				entries = append(entries, entry)
			}
		}
	}
	return entries, nil
}

// GetRegistrationCost returns the estimated cost of registering a name.
func (c *Client) GetRegistrationCost(ctx context.Context, name string) (*RegistrationCost, error) {
	raw, err := c.getAbsoluteJSON(ctx, c.baseURL+"/names/cost/"+url.PathEscape(name))
	if err != nil {
		return nil, err
	}
	cost := &RegistrationCost{}
	if v, ok := raw["name"].(string); ok {
		cost.Name = v
	}
	cost.Cost = jsonNumberToUint64(raw["cost"])
	cost.Duration = jsonNumberToUint64(raw["duration"])
	return cost, nil
}

// ---------------------------------------------------------------------------
// Name service mutations (submitted as contract calls via POST /call)
// ---------------------------------------------------------------------------

// nameContract is the well-known contract identifier for the name service.
const nameContract = "name_service"

// RegisterName registers a new name on the name service.
func (c *Client) RegisterName(ctx context.Context, name string) (*SubmitResult, error) {
	call := c.BuildContractCall(nameContract, "register", map[string]any{
		"name": name,
	}, "")
	return c.SendCall(ctx, call)
}

// RenewName extends the registration of an existing name.
func (c *Client) RenewName(ctx context.Context, name string) (*SubmitResult, error) {
	call := c.BuildContractCall(nameContract, "renew", map[string]any{
		"name": name,
	}, "")
	return c.SendCall(ctx, call)
}

// TransferName transfers ownership of a name to a new address.
func (c *Client) TransferName(ctx context.Context, name, newOwner string) (*SubmitResult, error) {
	call := c.BuildContractCall(nameContract, "transfer", map[string]any{
		"name":      name,
		"new_owner": newOwner,
	}, "")
	return c.SendCall(ctx, call)
}

// SetNameTarget sets the resolution target for a name.
func (c *Client) SetNameTarget(ctx context.Context, name, target string) (*SubmitResult, error) {
	call := c.BuildContractCall(nameContract, "set_target", map[string]any{
		"name":   name,
		"target": target,
	}, "")
	return c.SendCall(ctx, call)
}

// SetNameRecord sets a key-value record on a name.
func (c *Client) SetNameRecord(ctx context.Context, name, key, value string) (*SubmitResult, error) {
	call := c.BuildContractCall(nameContract, "set_record", map[string]any{
		"name":  name,
		"key":   key,
		"value": value,
	}, "")
	return c.SendCall(ctx, call)
}

// ReleaseName releases (unregisters) a previously registered name.
func (c *Client) ReleaseName(ctx context.Context, name string) (*SubmitResult, error) {
	call := c.BuildContractCall(nameContract, "release", map[string]any{
		"name": name,
	}, "")
	return c.SendCall(ctx, call)
}

// parseNameEntry converts a raw JSON map to a NameEntry. This is a
// convenience used internally; exported only for symmetry with other parsers.
func parseNameEntry(raw map[string]any) NameEntry {
	entry := NameEntry{}
	if v, ok := raw["name"].(string); ok {
		entry.Name = v
	}
	if v, ok := raw["address"].(string); ok {
		entry.Address = Address(v)
	}
	if v, ok := raw["target"].(string); ok {
		entry.Target = v
	}
	entry.Expiry = jsonNumberToUint64(raw["expiry"])
	return entry
}

